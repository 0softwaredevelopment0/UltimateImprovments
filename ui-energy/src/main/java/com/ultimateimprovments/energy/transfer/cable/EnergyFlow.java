package com.ultimateimprovments.energy.transfer.cable;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.storage.battery.BatteryPool;
import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Unified energy flow service — the single BFS implementation of the cable
 * network and THE contract every machine uses to talk to it. The model:
 * <ul>
 *   <li>any machine <b>pushes</b> energy into the network at its entry node
 *       or <b>pulls</b> from it at its access node — the network routes the
 *       energy through {@code CABLE} routers and settles it at storage
 *       nodes ({@code BATTERY} clusters, {@code GENERATOR} leftover
 *       buffers);</li>
 *   <li>sources are the storage nodes: a pull drains batteries and
 *       generator buffers alike (battery mode restrictions apply); a push
 *       charges batteries only;</li>
 *   <li>the reactor is not a special node — it pulls through its case
 *       junction cells and pushes through an entry node under its base
 *       (see ReactorManager.tickEnergyBuffer / tickEnergyGeneration);</li>
 *   <li>wire losses: every pull delivers 1 ⚡ less per network block the
 *       traversal had to touch — the sources lose the drained amount in
 *       full, the distance eats the difference (push is lossless).</li>
 * </ul>
 * Every traversal is node-capped, so a huge network can't stall a tick.
 * <p>
 * Threading: every method runs on the main server thread only — the BFS
 * touches Bukkit worlds, live blocks and battery clusters, none of which
 * are thread-safe, so there is nothing to move async. The node cap bounds
 * the worst-case cost of a single pass.
 * <p>
 * Activity trail: when energy actually moves, every CABLE node visited by
 * that traversal is marked flowing (blink visuals) and gets
 * {@link CableNode#addTransferred(int)} (multimeter readings).
 */
public final class EnergyFlow {

    /** Hard cap on visited nodes per operation (safety, not gameplay). */
    private static final int MAX_NODES = 4096;

    private EnergyFlow() {}

    // =========================
    // CONFIG
    // =========================

    /** Per-block battery capacity (config: {@code energy.battery.max_energy}). */
    public static int batteryCapacity() {
        return Math.max(1, Main.getInstance().getConfig()
                .getInt("energy.battery.max_energy", 100000));
    }

    // =========================
    // API
    // =========================

    /**
     * Pushes energy into the network: distributes {@code amount} over the
     * chargeable batteries reachable from {@code origin}. The origin may be
     * a network node itself (e.g. the cable under a machine) or a machine
     * block adjacent to the network (its neighbor nodes are used as entry
     * points). Returns the accepted amount — the energy the network can't
     * take (full / not connected) is lost.
     */
    public static int push(Location origin, int amount) {
        if (amount <= 0) return 0;
        return flow(origin, amount, true).moved();
    }

    /**
     * Pulls energy from the network: takes up to {@code amount} from the
     * dischargeable batteries (and legacy generator buffers) reachable from
     * {@code origin}. Returns what was actually DELIVERED to the caller —
     * wire losses subtract 1 ⚡ per network block the traversal had to touch
     * (cables, junctions, storage nodes), so a far-away source delivers less
     * than it loses. A value below {@code amount} means losses ate into the
     * request or the network is low on energy.
     */
    public static int pull(Location origin, int amount) {
        if (amount <= 0) return 0;
        FlowResult result = flow(origin, amount, false);
        int loss = Math.min(result.moved(), result.hops());
        return result.moved() - loss;
    }

    /**
     * Drains up to {@code amount} energy from the network reachable from
     * {@code origin} and returns the <b>gross</b> amount the sources gave up.
     * <p>
     * Wire losses still apply to every plain {@link #pull(Location, int)}: they
     * are subtracted there because the caller receives usable energy. A consumer
     * that stores the drained energy in its OWN buffer (the multiblock lamp)
     * needs the raw drained value instead — otherwise a tiny per-second request
     * vanishes into the distance-based loss entirely, and a distant consumer
     * could never light up. Traversal and side effects (flow marks for the
     * visuals, multimeter counters) are exactly the same as for a pull.
     *
     * @param origin the network node (or machine block) the consumer pulls at
     * @param amount requested gross amount
     * @return gross energy taken from the sources (0 when nothing was available)
     */
    public static int pullGross(Location origin, int amount) {
        if (amount <= 0) return 0;
        return flow(origin, amount, false).moved();
    }

    /** One traversal outcome: energy settled at storage nodes + hops touched. */
    private record FlowResult(int moved, int hops) {}

    /**
     * Availability check without mutation: is at least {@code amount} of
     * energy reachable from {@code origin}? Call this before {@link #pull}
     * when a partial take would lose energy (check-then-take is safe — the
     * server runs these on the main thread). Raw availability: the actual
     * {@link #pull} delivery is reduced by wire losses (1 ⚡ per network
     * block the traversal touches).
     */
    public static boolean hasEnergy(Location origin, int amount) {
        if (amount <= 0) return true;

        Location norm = LocationUtil.normalize(origin);
        if (norm == null) return false;
        String worldUid = norm.getWorld().getUID().toString();

        Set<Long> visited = new HashSet<>();
        Deque<CableNode> queue = new ArrayDeque<>();
        seed(norm, queue, visited);

        int total = 0;
        Set<BatteryPool> seenPools = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!queue.isEmpty() && visited.size() <= MAX_NODES) {
            CableNode node = queue.poll();
            if (node == null) continue;

            total += availableEnergy(node, seenPools);
            if (total >= amount) return true;

            enqueueNeighbors(node, worldUid, queue, visited);
        }
        return total >= amount;
    }

    /**
     * Space check without mutation: can the network accept at least
     * {@code amount} energy right now? Mirrors {@link #push} routing exactly
     * (chargeable battery blocks). Producers use it as a gate: a full network
     * should switch the machine off instead of letting it run and produce
     * nothing.
     */
    public static boolean hasSpace(Location origin, int amount) {
        if (amount <= 0) return true;

        Location norm = LocationUtil.normalize(origin);
        if (norm == null) return false;
        String worldUid = norm.getWorld().getUID().toString();

        Set<Long> visited = new HashSet<>();
        Deque<CableNode> queue = new ArrayDeque<>();
        seed(norm, queue, visited);

        int total = 0;
        Set<BatteryPool> seenPools = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!queue.isEmpty() && visited.size() <= MAX_NODES) {
            CableNode node = queue.poll();
            if (node == null) continue;

            total += freeSpace(node, seenPools);
            if (total >= amount) return true;

            enqueueNeighbors(node, worldUid, queue, visited);
        }
        return total >= amount;
    }

    /**
     * Collects the batteries of one connected component (for the balancer).
     * Marks every visited node key in {@code visited} so the caller never
     * processes the same component twice. Only batteries allowed to both
     * charge and discharge (CHARGE_DISCHARGE clusters / unclustered nodes)
     * take part in balancing — the same rule as before the rework.
     * <p>
     * A multiblock battery is ONE storage unit: only the first reached block
     * of a cluster is returned (deduplicated by its shared
     * {@link BatteryPool}), so balancing works on pools, not on blocks.
     */
    public static List<CableNode> collectBalanceGroup(CableNode start, Set<Long> visited) {
        List<CableNode> batteries = new ArrayList<>();
        if (start == null || start.getWorld() == null) return batteries;
        String worldUid = start.getWorld().getUID().toString();

        Deque<CableNode> queue = new ArrayDeque<>();
        Set<BatteryPool> seenPools = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!visited.add(start.getKey())) return batteries;
        queue.add(start);

        while (!queue.isEmpty() && visited.size() <= MAX_NODES) {
            CableNode node = queue.poll();
            if (node == null) continue;

            if (node.getType() == NodeType.BATTERY) {
                BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
                if (bc == null || bc.mode == BatteryManager.BatteryMode.CHARGE_DISCHARGE) {
                    BatteryPool pool = node.getBatteryPool();
                    if (pool == null || seenPools.add(pool)) {
                        batteries.add(node);
                    }
                }
            }

            enqueueNeighbors(node, worldUid, queue, visited);
        }
        return batteries;
    }

    // =========================
    // CORE TRAVERSAL
    // =========================

    /** One pass over the network — charges (push) or drains (pull) batteries. */
    private static FlowResult flow(Location origin, int amount, boolean charging) {
        Location norm = LocationUtil.normalize(origin);
        if (norm == null) return new FlowResult(0, 0);
        String worldUid = norm.getWorld().getUID().toString();

        Set<Long> visited = new HashSet<>();
        List<CableNode> cables = new ArrayList<>();
        Deque<CableNode> queue = new ArrayDeque<>();
        Set<BatteryPool> seenPools = Collections.newSetFromMap(new IdentityHashMap<>());
        seed(norm, queue, visited);

        int remaining = amount;
        int hops = 0;
        while (!queue.isEmpty() && remaining > 0 && visited.size() <= MAX_NODES) {
            CableNode node = queue.poll();
            if (node == null) continue;

            if (node.getType() == NodeType.CABLE) {
                hops++;
                cables.add(node);
            } else {
                // A multiblock battery is one storage: only the first block
                // of a pool settles energy and counts as a wire hop. The
                // remaining blocks are still walked (neighbours are
                // enqueued below) so cables on the far side stay reachable —
                // they just don't duplicate the same reserve.
                BatteryPool pool = node.getBatteryPool();
                if (pool == null || seenPools.add(pool)) {
                    hops++;
                    int moved = charging ? chargeNode(node, remaining) : drainNode(node, remaining);
                    if (moved > 0) {
                        if (charging) node.addEnergy(moved); else node.removeEnergy(moved);
                        node.addTransferred(moved);
                        remaining -= moved;
                    }
                }
            }

            if (remaining <= 0) break;

            enqueueNeighbors(node, worldUid, queue, visited);
        }

        int transferred = amount - remaining;
        if (transferred > 0) {
            // Activity trail: visuals + multimeter on the cables that carried it.
            for (CableNode cable : cables) {
                CableNetwork.markFlowingKey(worldUid, cable.getKey());
                cable.addTransferred(transferred);
            }
        }
        return new FlowResult(transferred, hops);
    }

    /** Energy this node can accept right now (charging side). */
    private static int chargeNode(CableNode node, int maxAmount) {
        if (node.getType() != NodeType.BATTERY) return 0;
        BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
        if (bc != null && !bc.canCharge()) return 0;

        // Self-heal legacy nodes created before the capacity was set at creation.
        node.setMaxEnergy(Math.max(node.getMaxEnergy(), batteryCapacity()));
        int space = node.getMaxEnergy() - node.getEnergy();
        return Math.min(space, maxAmount);
    }

    /** Energy this node can give right now (discharging side). */
    private static int drainNode(CableNode node, int maxAmount) {
        if (node.getType() == NodeType.GENERATOR) {
            return Math.min(node.getEnergy(), maxAmount);
        }
        if (node.getType() != NodeType.BATTERY) return 0;
        BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
        if (bc != null && !bc.canDischarge()) return 0;
        return Math.min(node.getEnergy(), maxAmount);
    }

    /** Reachable energy at this node (no mutation) — for {@link #hasEnergy}. */
    private static int availableEnergy(CableNode node, Set<BatteryPool> seenPools) {
        BatteryPool pool = node.getBatteryPool();
        if (pool != null && !seenPools.add(pool)) return 0;
        return drainNode(node, Integer.MAX_VALUE);
    }

    /** Free room at this node (no mutation) — for {@link #hasSpace}. */
    private static int freeSpace(CableNode node, Set<BatteryPool> seenPools) {
        if (node.getType() != NodeType.BATTERY) return 0;
        BatteryPool pool = node.getBatteryPool();
        if (pool != null && !seenPools.add(pool)) return 0;
        BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
        if (bc != null && !bc.canCharge()) return 0;
        int max = Math.max(node.getMaxEnergy(), batteryCapacity());
        return Math.max(0, max - node.getEnergy());
    }

    /** Seeds the traversal with the origin node (if any) + its neighbor nodes. */
    private static void seed(Location norm, Deque<CableNode> queue, Set<Long> visited) {
        CableNode self = CableNetwork.getNode(norm);
        if (self != null && visited.add(self.getKey())) {
            queue.add(self);
        }
        for (Location near : LocationUtil.getNeighbors(norm)) {
            CableNode neighbor = CableNetwork.getNode(near);
            if (neighbor != null && visited.add(neighbor.getKey())) {
                queue.add(neighbor);
            }
        }
    }

    private static void enqueueNeighbors(CableNode node, String worldUid,
                                        Deque<CableNode> queue, Set<Long> visited) {
        for (long connKey : node.getConnectionKeys()) {
            if (visited.contains(connKey)) continue;
            CableNode next = CableNetwork.getNodeByKey(worldUid, connKey);
            if (next == null) continue;
            visited.add(connKey);
            queue.add(next);
        }
    }
}
