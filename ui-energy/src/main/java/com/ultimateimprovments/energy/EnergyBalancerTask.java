package com.ultimateimprovments.energy;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.energy.transfer.cable.NodeType;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * The single battery-balancing pass of the network.
 * <p>
 * Once per run it walks the connected components of the cable graph
 * ({@link EnergyFlow#collectBalanceGroup}) and equalizes the charge of every
 * battery group toward the average, capped by {@code energy.balancer.max_transfer}
 * per battery per run. This task replaced two competing systems: the old
 * balancer and {@code BatteryDrainTask} (which was a second balancer that
 * pushed energy into the first battery with free space, ignoring charge
 * modes — the two fought each other and kept the whole network churning).
 */
public class EnergyBalancerTask extends BukkitRunnable {

    /** The task runs every 40 ticks — the transfer cap is scaled by this, so the per-second balancing rate is unchanged. */
    private static final int TICKS_PER_RUN = 40;

    @Override
    public void run() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("energy.balancer.enabled", true)) return;

        int maxTransfer = cfg.getInt("energy.balancer.max_transfer", 25) * TICKS_PER_RUN;
        boolean log = cfg.getBoolean("energy.balancer.log", false);

        // Connected-component walk: every battery belongs to exactly one group.
        List<CableNode> starts = new ArrayList<>();
        CableNetwork.forEachNode(node -> {
            if (node != null && node.getType() == NodeType.BATTERY) {
                starts.add(node);
            }
        });

        Set<Long> visited = new HashSet<>();
        for (CableNode start : starts) {
            if (visited.contains(start.getKey())) continue;

            List<CableNode> group = EnergyFlow.collectBalanceGroup(start, visited);
            if (group.size() > 1) {
                balanceGroup(group, maxTransfer, log);
            }
        }
    }

    // =========================
    // GROUP BALANCING
    // =========================
    /**
     * Equalizes the CHARGE PERCENTAGE of the storage units of one connected
     * group. Percentages (not absolute values) are the only correct target
     * now that a unit is a whole multiblock battery of arbitrary size: with
     * the old absolute average a 100-block battery (10 000 000 ⚡) and a
     * single-block one (100 000 ⚡) would "equalize" to 50 000 ⚡ each, i.e.
     * drain the large battery almost to zero.
     * <p>
     * {@code group} is already deduplicated by pool (see
     * {@link EnergyFlow#collectBalanceGroup}), so every entry is one
     * independent storage unit.
     */
    private void balanceGroup(List<CableNode> group, int maxTransfer, boolean log) {
        long totalEnergy = 0;
        long totalCapacity = 0;
        for (CableNode b : group) {
            totalEnergy += b.getEnergy();
            totalCapacity += Math.max(0, b.getMaxEnergy());
        }
        if (totalCapacity <= 0) return;
        double averageShare = (double) totalEnergy / (double) totalCapacity;

        Map<CableNode, Integer> targets = new LinkedHashMap<>();
        Map<CableNode, Integer> moved = new LinkedHashMap<>();
        Map<CableNode, Integer> donated = new LinkedHashMap<>();
        List<CableNode> poor = new ArrayList<>();
        int collected = 0;

        for (CableNode b : group) {
            int capacity = b.getMaxEnergy();
            if (capacity <= 0) continue;

            int current = b.getEnergy();
            int target = (int) Math.round(capacity * averageShare);
            targets.put(b, target);

            if (current > target) {
                int take = Math.min(Math.min(current - target, maxTransfer), current);
                if (take > 0) {
                    b.removeEnergy(take);
                    collected += take;
                    donated.merge(b, take, Integer::sum);
                    moved.merge(b, take, Integer::sum);
                }
            } else if (current < target) {
                poor.add(b);
            }
        }

        if (collected <= 0) return;

        // Distribute to the batteries below average.
        int remaining = collected;
        for (CableNode b : poor) {
            if (remaining <= 0) break;
            int deficit = targets.getOrDefault(b, b.getEnergy()) - b.getEnergy();
            if (deficit <= 0) continue;
            int add = Math.min(Math.min(deficit, maxTransfer), remaining);
            if (add > 0) {
                b.addEnergy(add);
                remaining -= add;
                moved.merge(b, add, Integer::sum);
            }
        }

        // Return whatever the poor batteries could not take to the donors
        // that gave it (never above their own cap).
        if (remaining > 0) {
            for (Map.Entry<CableNode, Integer> entry : donated.entrySet()) {
                if (remaining <= 0) break;
                CableNode b = entry.getKey();
                int space = b.getMaxEnergy() - b.getEnergy();
                int back = Math.min(Math.min(space, remaining), entry.getValue());
                if (back > 0) {
                    b.addEnergy(back);
                    remaining -= back;
                }
            }
            if (log) {
                ConsoleLogger.info("[Balancer] " + remaining + " energy left undistributed (no free room)");
            }
        }

        // Activity trail on the cables adjacent to the moved batteries
        // (blink visuals + multimeter readings).
        for (Map.Entry<CableNode, Integer> entry : moved.entrySet()) {
            CableNode b = entry.getKey();
            String worldUid = b.getWorld().getUID().toString();
            for (long connKey : b.getConnectionKeys()) {
                CableNode cable = CableNetwork.getNodeByKey(worldUid, connKey);
                if (cable == null || cable.getType() != NodeType.CABLE) continue;
                CableNetwork.markFlowingKey(worldUid, cable.getKey());
                cable.addTransferred(entry.getValue());
            }
        }
    }
}
