package com.ultimateimprovments.energy.consumption.light;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.energy.transfer.cable.NodeType;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 💡 Multiblock electric lamp — an adaptive structure (same idea as the battery
 * multiblock) built from waxed copper bulbs and waxed chiseled copper junctions.
 * <p>
 * <b>Membership.</b> A one-shot item frame on a waxed copper bulb assembles the
 * whole connected component of bulbs and chiseled copper blocks into ONE lamp
 * structure (flood fill over both materials, chunks are never force-loaded).
 * The structure needs more than one block in total and at least one bulb plus
 * one junction. Chiseled copper is the {@code CABLE} node of the energy network
 * — energy reaches the structure only through its junction blocks. Beware: a
 * fixture attached to a longer chiseled-copper run absorbs that whole run as
 * junctions (every one of them must then be redstone-powered), so connect the
 * grid to the fixture with lightning-rod cable, not with more junctions.
 * <p>
 * <b>Energy.</b> Every lamp consumes {@value #ENERGY_PER_LAMP_PER_SECOND} ⚡/s,
 * so the whole structure consumes {@code lamps} ⚡/s. The local buffer holds
 * {@code lamps × }{@value #BUFFER_SECONDS} ⚡ (five seconds of autonomy). While
 * the lamps burn, the structure refills the buffer from the cable network
 * through its junction ports and goes dark when the buffer cannot cover the
 * next second. Nothing is consumed while the lamps are off.
 * <p>
 * <b>Redstone.</b> Every junction block of the structure must be powered by
 * redstone (direct or indirect). One unpowered junction switches all lamps off;
 * the buffer is kept, so restoring the signal relights them immediately. A
 * redstone component event next to a structure block is applied at once where
 * the server reports one (dust/plate circuits); a lever or torch attached to a
 * junction is picked up by the next one-second tick, which also runs the energy
 * accounting.
 * <p>
 * <b>Persistence.</b> Structure membership is stored in the SQLite structure
 * registry ({@code structure_markers}). Only the BULB blocks get a row of type
 * {@code "light"} — the junction blocks keep their own {@code "cable"} rows
 * (and get one if they were placed without our events), otherwise the energy
 * network would lose those nodes on the next restart. The registry is the
 * source of truth; the in-memory clusters are rebuilt from it on startup, world
 * load and delayed rebuild passes. Breaking any block dismantles the structure.
 * <p>
 * All state is main-thread only (Bukkit events + the scheduler tick).
 */
public final class LightManager {

    // ════════════════════════════════════════
    // SPEC CONSTANTS
    // ════════════════════════════════════════
    /** Energy (⚡) one lamp consumes per second. */
    public static final int ENERGY_PER_LAMP_PER_SECOND = 1;
    /** Local buffer size, expressed in seconds of the structure's consumption. */
    public static final int BUFFER_SECONDS = 5;

    /** Safety cap for one adaptive structure (main-thread flood fill). */
    private static final int MAX_STRUCTURE_BLOCKS = 8192;
    /** How many junction ports one refill pass may try (network BFS each). */
    private static final int MAX_PORT_ATTEMPTS = 8;

    // ════════════════════════════════════════
    // CACHES — world-aware: the same x,y,z in two worlds is two structures
    // ════════════════════════════════════════
    private static final Map<String, Map<Long, LightCluster>> clustersByWorld = new ConcurrentHashMap<>();
    private static final Map<Integer, LightCluster> clustersById = new ConcurrentHashMap<>();
    private static int nextId = 1;
    private static Listener redstoneListener;

    private static final int[][] DIR = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private LightManager() {}

    // ════════════════════════════════════════
    // COORDINATE KEY (via StructureMarker)
    // ════════════════════════════════════════
    public static long toKey(int x, int y, int z) { return StructureMarker.toKey(x, y, z); }
    public static long toKey(Location loc) { return StructureMarker.toKey(loc); }
    public static int getX(long key) { return StructureMarker.getX(key); }
    public static int getY(long key) { return StructureMarker.getY(key); }
    public static int getZ(long key) { return StructureMarker.getZ(key); }

    /** Six neighbour keys in the SAME encoding as {@link #toKey} (StructureMarker). */
    private static long[] neighborKeys(long key) {
        int x = getX(key), y = getY(key), z = getZ(key);
        return new long[]{
                toKey(x + 1, y, z), toKey(x - 1, y, z),
                toKey(x, y + 1, z), toKey(x, y - 1, z),
                toKey(x, y, z + 1), toKey(x, y, z - 1)
        };
    }

    private static String worldUid(World world) { return world.getUID().toString(); }

    private static Map<Long, LightCluster> worldMap(World world) {
        return clustersByWorld.computeIfAbsent(worldUid(world), k -> new ConcurrentHashMap<>());
    }

    private static LightCluster clusterAt(World world, long key) {
        if (world == null) return null;
        Map<Long, LightCluster> map = clustersByWorld.get(worldUid(world));
        return map != null ? map.get(key) : null;
    }

    private static void bindCluster(World world, long key, LightCluster cluster) {
        worldMap(world).put(key, cluster);
    }

    // ════════════════════════════════════════
    // LIGHT CLUSTER
    // ════════════════════════════════════════
    public static class LightCluster {
        public int id;
        public UUID uuid;
        public World world;

        /** Every block of the structure: bulbs AND junctions. */
        public final Set<Long> blockKeys = new HashSet<>();
        /** The bulb blocks of {@link #blockKeys} — only they are lit. */
        public final Set<Long> lampKeys = new HashSet<>();

        public Location center;

        /** Current energy reserve of the whole structure (not persisted). */
        public int buffer;
        /** Whether the lamps are currently burning. */
        public boolean lit;

        /** Rotating cursor over the junction ports (transient, starvation-free). */
        int portCursor;

        private long sumX, sumY, sumZ;

        public int lampCount() { return lampKeys.size(); }
        public int junctionCount() { return blockKeys.size() - lampKeys.size(); }

        /** Buffer capacity — {@value #BUFFER_SECONDS} seconds of consumption. */
        public int bufferCapacity() {
            return BUFFER_SECONDS * ENERGY_PER_LAMP_PER_SECOND * lampCount();
        }

        /** Consumption of the whole structure per second. */
        public int consumptionPerSecond() {
            return ENERGY_PER_LAMP_PER_SECOND * lampCount();
        }

        void addBlock(long key, boolean lamp) {
            if (!blockKeys.add(key)) return;
            sumX += getX(key);
            sumY += getY(key);
            sumZ += getZ(key);
            if (lamp) lampKeys.add(key);
            updateCenterFromSums();
        }

        void recalculateCenter() {
            if (blockKeys.isEmpty()) return;
            sumX = 0;
            sumY = 0;
            sumZ = 0;
            for (long key : blockKeys) {
                sumX += getX(key);
                sumY += getY(key);
                sumZ += getZ(key);
            }
            updateCenterFromSums();
        }

        private void updateCenterFromSums() {
            int size = blockKeys.size();
            if (size == 0) {
                center = null;
                return;
            }
            center = new Location(world,
                    (int) Math.round((double) sumX / size),
                    (int) Math.round((double) sumY / size),
                    (int) Math.round((double) sumZ / size));
        }

        boolean contains(Location loc) { return blockKeys.contains(toKey(loc)); }
    }

    // ════════════════════════════════════════
    // INIT / SHUTDOWN
    // ════════════════════════════════════════
    public static void init() {
        registerRedstoneListener();
        rebuildFromMarkers();
        int lamps = 0;
        for (LightCluster cluster : clustersById.values()) lamps += cluster.lampCount();
        ConsoleLogger.info("[LightMulti] Manager initialized with " + clustersById.size()
                + " light structures (" + lamps + " lamps, chiseled-copper junctions included)");
    }

    private static void registerRedstoneListener() {
        Main main = Main.getInstance();
        if (main == null || redstoneListener != null) return;
        redstoneListener = new LightRedstoneListener();
        Bukkit.getPluginManager().registerEvents(redstoneListener, main);
    }

    /** Turns every burning lamp off and drops the caches (soft reload / disable). */
    public static void shutdown() {
        if (redstoneListener != null) {
            HandlerList.unregisterAll(redstoneListener);
            redstoneListener = null;
        }
        for (LightCluster cluster : clustersById.values()) {
            setBlocksLit(cluster, false);
        }
        clearAll();
    }

    public static void clearAll() {
        clustersByWorld.clear();
        clustersById.clear();
        nextId = 1;
    }

    // ════════════════════════════════════════
    // REBUILD FROM THE STRUCTURE REGISTRY
    // ════════════════════════════════════════
    /**
     * Rebuilds the in-memory clusters from the {@code "light"} markers (bulb
     * positions). Junctions are reconstructed by flood-filling the connected
     * bulb/junction component from those seeds — they are not marked as
     * {@code "light"} precisely because their {@code "cable"} rows must survive.
     * <p>
     * Rows pointing at blocks that no longer exist (explosion/world edit without
     * a break event) are cleaned up. A marker group whose chunks are not fully
     * loaded is left untouched and protected from the orphan purge: a later
     * rebuild pass (delayed startup pass / world load) gets a full view.
     */
    public static void rebuildFromMarkers() {
        // A rebuild can happen while the structures are live (world load, delayed
        // passes). Keep the energy reserves by UUID so the lamps don't lose their
        // five seconds of autonomy on every rebuild pass.
        Map<UUID, Integer> existingBuffers = new HashMap<>();
        for (LightCluster cluster : clustersById.values()) {
            if (cluster.uuid != null) existingBuffers.put(cluster.uuid, cluster.buffer);
        }

        clustersByWorld.clear();
        clustersById.clear();
        nextId = 1;

        Map<UUID, Set<Long>> markerGroups = new HashMap<>();
        Map<UUID, World> foundWorlds = new HashMap<>();

        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            if (!"light".equals(entry.getValue().type())) continue;

            UUID uuid = entry.getValue().uuid();
            String fk = entry.getKey();
            long posKey = toKey(
                    StructureMarker.parseX(fk),
                    StructureMarker.parseY(fk),
                    StructureMarker.parseZ(fk));
            markerGroups.computeIfAbsent(uuid, k -> new HashSet<>()).add(posKey);

            if (!foundWorlds.containsKey(uuid)) {
                String worldUid = entry.getValue().worldUid();
                if (worldUid != null) {
                    for (World w : Bukkit.getServer().getWorlds()) {
                        if (w.getUID().toString().equals(worldUid)) {
                            foundWorlds.put(uuid, w);
                            break;
                        }
                    }
                }
            }
        }

        Map<String, Set<Long>> claimedByWorld = new HashMap<>();
        Set<UUID> usedUuids = new HashSet<>();
        Set<UUID> unverified = new HashSet<>();

        for (Map.Entry<UUID, Set<Long>> group : markerGroups.entrySet()) {
            UUID uuid = group.getKey();
            World world = foundWorlds.get(uuid);
            if (world == null) continue;

            Set<Long> claimed = claimedByWorld.computeIfAbsent(worldUid(world), k -> new HashSet<>());

            // Validate the marked positions: drop rows whose block is gone, find a
            // seed, and detect a partially loaded view (never purge that UUID).
            Long seed = null;
            boolean chunkBlocked = false;
            List<Location> stale = new ArrayList<>();
            for (long key : group.getValue()) {
                int x = getX(key), y = getY(key), z = getZ(key);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    chunkBlocked = true;
                    continue;
                }
                // A "light" row may only describe a bulb. A row over any other
                // material (a junction that replaced the bulb, pasted blocks, ...)
                // is stale, not a valid seed.
                if (world.getType(x, y, z) != Materials.WAXED_COPPER_BULB) {
                    stale.add(new Location(world, x, y, z));
                    continue;
                }
                if (seed == null && !claimed.contains(key)) seed = key;
            }
            for (Location loc : stale) StructureMarker.removeAt(loc);

            if (chunkBlocked) {
                // Partial view: skip the cluster AND protect its rows from the purge.
                unverified.add(uuid);
                continue;
            }
            if (seed == null) continue; // all rows stale or already claimed by another cluster

            Set<Long> component = floodFill(world, getX(seed), getY(seed), getZ(seed));
            if (component == null) {
                unverified.add(uuid); // too large for a safe main-thread walk
                continue;
            }
            component.removeAll(claimed);
            if (component.isEmpty()) continue;

            int lampCount = 0;
            for (long key : component) {
                if (world.getType(getX(key), getY(key), getZ(key)) == Materials.WAXED_COPPER_BULB) {
                    lampCount++;
                }
            }
            if (lampCount < 1 || component.size() - lampCount < 1) {
                // Legacy/malformed structure (e.g. an old single-material lamp):
                // do not destroy the rows of a build we cannot classify.
                unverified.add(uuid);
                continue;
            }

            LightCluster cluster = new LightCluster();
            cluster.id = nextId++;
            cluster.uuid = uuid;
            cluster.world = world;
            cluster.buffer = 0;
            cluster.lit = false;
            for (long key : component) {
                Location blockLoc = new Location(world, getX(key), getY(key), getZ(key));
                boolean lamp = blockLoc.getBlock().getType() == Materials.WAXED_COPPER_BULB;
                cluster.addBlock(key, lamp);
                bindCluster(world, key, cluster);
                if (!lamp) ensureJunctionNode(blockLoc);
            }
            cluster.recalculateCenter();
            Integer preserved = existingBuffers.get(uuid);
            if (preserved != null) cluster.buffer = Math.max(0, Math.min(preserved, cluster.bufferCapacity()));
            clustersById.put(cluster.id, cluster);
            claimed.addAll(component);
            usedUuids.add(uuid);

            // Self-healing: a bulb of this structure may carry a stale UUID (two
            // structures merged through a shared junction) or no row at all
            // (registry purged while its block stayed in a live cluster).
            writeLampMarkers(cluster);
        }

        StructureMarker.purgeOrphaned("light", usedUuids, unverified);
    }

    /** (Re)writes the {@code "light"} rows of the bulbs that miss the right UUID. */
    private static void writeLampMarkers(LightCluster cluster) {
        if (cluster.uuid == null || cluster.world == null) return;
        List<Location> missing = new ArrayList<>();
        for (long key : cluster.lampKeys) {
            Location loc = new Location(cluster.world, getX(key), getY(key), getZ(key));
            UUID current = StructureMarker.getUUID(loc);
            if (cluster.uuid.equals(current)) continue;
            missing.add(loc);
        }
        if (missing.isEmpty()) return;
        StructureMarker.placeAll(missing, "light", cluster.uuid);
    }

    // ════════════════════════════════════════
    // FLOOD FILL — bulbs + chiseled copper junctions
    // ════════════════════════════════════════
    /** Whether the material takes part in a light structure. */
    private static boolean isStructureMaterial(Material material) {
        return material == Materials.WAXED_COPPER_BULB
                || material == Materials.WAXED_CHISELED_COPPER;
    }

    /**
     * Connected bulb/junction component of the seed block.
     *
     * @return the component, an empty set when the seed is not a structure block,
     *         or {@code null} when the component exceeds {@link #MAX_STRUCTURE_BLOCKS}
     */
    private static Set<Long> floodFill(World world, int sx, int sy, int sz) {
        Set<Long> visited = new HashSet<>();
        if (world == null || !world.isChunkLoaded(sx >> 4, sz >> 4)) return visited;
        if (!isStructureMaterial(world.getType(sx, sy, sz))) return visited;

        visited.add(toKey(sx, sy, sz));
        Deque<int[]> queue = new ArrayDeque<>();
        queue.addLast(new int[]{sx, sy, sz});
        while (!queue.isEmpty()) {
            int[] pos = queue.pollFirst();
            int x = pos[0], y = pos[1], z = pos[2];
            for (int[] d : DIR) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long nk = toKey(nx, ny, nz);
                if (visited.contains(nk)) continue;
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                if (!isStructureMaterial(world.getType(nx, ny, nz))) continue;
                if (visited.size() >= MAX_STRUCTURE_BLOCKS) return null;
                visited.add(nk);
                queue.addLast(new int[]{nx, ny, nz});
            }
        }
        return visited;
    }

    /**
     * Chiseled copper is a {@code CABLE} node — make sure the port can route
     * energy, and give it its {@code "cable"} row when the block appeared
     * without our place event (WorldEdit/schematic), so the network and the
     * chunk ticket survive a restart independently of the light rows.
     */
    private static void ensureJunctionNode(Location blockLoc) {
        if (StructureMarker.getType(blockLoc) == null) {
            StructureMarker.place(blockLoc, "cable", UUID.randomUUID());
        }
        if (CableNetwork.getNode(blockLoc) != null) return;
        CableNetwork.ensureNode(blockLoc, NodeType.CABLE);
    }

    // ════════════════════════════════════════
    // ASSEMBLY — one-shot item frame on a waxed copper bulb
    // ════════════════════════════════════════
    /**
     * Assembles the connected bulb/junction component at {@code loc}. The block
     * under the frame must be a waxed copper bulb; an invalid component leaves
     * the frame intact and reports why.
     */
    public static void assemble(Location loc, Player player, ItemFrame frame) {
        loc = LocationUtil.normalize(loc);
        if (loc == null || loc.getWorld() == null) return;

        long seedKey = toKey(loc);
        if (clusterAt(loc.getWorld(), seedKey) != null) {
            if (player != null) {
                player.sendMessage(MessageUtil.parse(MessageUtil.PREFIX
                        + "<yellow>A light structure is already assembled at this place!</yellow>"));
            }
            return;
        }

        if (loc.getBlock().getType() != Materials.WAXED_COPPER_BULB) {
            // Only a lamp starts the assembly; a junction is just an energy port.
            return;
        }

        Set<Long> connected = floodFill(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (connected == null) {
            if (player != null) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>The connected structure is too large ("
                        + MAX_STRUCTURE_BLOCKS + "+ blocks)!</red>"));
            }
            return;
        }
        if (connected.isEmpty()) return;

        int lampCount = 0;
        for (long key : connected) {
            if (loc.getWorld().getType(getX(key), getY(key), getZ(key)) == Materials.WAXED_COPPER_BULB) {
                lampCount++;
            }
        }
        int junctionCount = connected.size() - lampCount;

        boolean overlaps = false;
        for (long key : connected) {
            if (clusterAt(loc.getWorld(), key) != null) {
                overlaps = true;
                break;
            }
        }
        if (overlaps) {
            if (player != null) {
                player.sendMessage(MessageUtil.parse(MessageUtil.PREFIX
                        + "<dark_red>❌ <red>This lamp already belongs to another light structure!</red>"));
            }
            return;
        }

        if (connected.size() <= 1 || lampCount < 1 || junctionCount < 1) {
            if (player != null) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>A light structure needs more than "
                        + "one block: at least <white>1 waxed copper bulb</white> and "
                        + "<white>1 waxed chiseled copper junction</white>!</red>"));
            }
            return;
        }

        UUID uuid = UUID.randomUUID();
        LightCluster cluster = new LightCluster();
        cluster.id = nextId++;
        cluster.uuid = uuid;
        cluster.world = loc.getWorld();
        cluster.buffer = 0;
        cluster.lit = false;

        for (long key : connected) {
            Location blockLoc = new Location(cluster.world, getX(key), getY(key), getZ(key));
            boolean lamp = blockLoc.getBlock().getType() == Materials.WAXED_COPPER_BULB;
            cluster.addBlock(key, lamp);
            bindCluster(cluster.world, key, cluster);
            if (!lamp) ensureJunctionNode(blockLoc);
        }
        cluster.recalculateCenter();
        clustersById.put(cluster.id, cluster);
        writeLampMarkers(cluster);

        consumeTriggerFrame(frame);
        playAssemblyEffects(cluster);

        if (player != null) {
            player.sendMessage(MessageUtil.parse("<green>✔ <white>Light structure assembled!</white>"));
            player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Lamps: <white>" + cluster.lampCount()
                    + " <gray>| Junctions: <white>" + cluster.junctionCount()
                    + " <gray>| Consumption: <white>" + cluster.consumptionPerSecond() + " ⚡/s"));
            player.sendMessage(MessageUtil.parse("<dark_gray>┃ <gray>Buffer: <white>"
                    + cluster.bufferCapacity() + " ⚡ <gray>— all junctions must be redstone-powered to burn"));
        }

        ConsoleLogger.info("[LightMulti] Assembled structure #" + cluster.id + " UUID=" + uuid
                + " with " + cluster.lampCount() + " lamps and " + cluster.junctionCount() + " junctions");
    }

    /** Breaks the trigger item frame and drops it (plus any displayed item). */
    private static void consumeTriggerFrame(ItemFrame frame) {
        if (frame == null || frame.isDead() || !frame.isValid()) return;

        Location loc = frame.getLocation();
        World world = frame.getWorld();
        if (world == null) return;

        ItemStack displayed = frame.getItem();
        if (displayed != null && !displayed.getType().isAir()) {
            world.dropItemNaturally(loc, displayed);
        }
        Material frameItem = frame instanceof GlowItemFrame
                ? Material.GLOW_ITEM_FRAME
                : Material.ITEM_FRAME;
        world.dropItemNaturally(loc, new ItemStack(frameItem));
        frame.remove();
    }

    private static void playAssemblyEffects(LightCluster cluster) {
        if (cluster.center == null || cluster.world == null) return;
        Location center = cluster.center.clone().add(0.5, 0.5, 0.5);
        cluster.world.spawnParticle(Particle.ELECTRIC_SPARK, center, 30, 0.6, 0.6, 0.6, 0.02);
        cluster.world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
    }

    // ════════════════════════════════════════
    // HOT EXPAND — a block placed next to an assembled structure
    // ════════════════════════════════════════
    public static void onBlockPlaced(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null || loc.getWorld() == null) return;

        Material type = loc.getBlock().getType();
        if (!isStructureMaterial(type)) return;

        long key = toKey(loc);
        if (clusterAt(loc.getWorld(), key) != null) return;

        boolean lamp = type == Materials.WAXED_COPPER_BULB;
        Set<LightCluster> adjacent = new LinkedHashSet<>();
        for (long nk : neighborKeys(key)) {
            LightCluster cluster = clusterAt(loc.getWorld(), nk);
            if (cluster != null) adjacent.add(cluster);
        }
        if (adjacent.isEmpty()) return;

        if (adjacent.size() == 1) {
            LightCluster cluster = adjacent.iterator().next();
            if (cluster.blockKeys.size() >= MAX_STRUCTURE_BLOCKS) {
                ConsoleLogger.warn("[LightMulti] Structure #" + cluster.id + " reached the "
                        + MAX_STRUCTURE_BLOCKS + "-block cap — the placed block stays inert");
                return;
            }
            cluster.addBlock(key, lamp);
            bindCluster(cluster.world, key, cluster);
            if (lamp) {
                writeLampMarkers(cluster);
                if (cluster.lit) setBlockLitState(loc.getBlock(), true);
            } else {
                ensureJunctionNode(loc);
            }
            return;
        }

        // Placing the block bridged several structures — merge them into one.
        int mergedSize = 1;
        for (LightCluster cluster : adjacent) mergedSize += cluster.blockKeys.size();
        if (mergedSize > MAX_STRUCTURE_BLOCKS) {
            ConsoleLogger.warn("[LightMulti] Merging these light structures would exceed the "
                    + MAX_STRUCTURE_BLOCKS + "-block cap — the placed block stays inert");
            return;
        }

        Iterator<LightCluster> it = adjacent.iterator();
        LightCluster primary = it.next();
        long mergedBuffer = primary.buffer;

        while (it.hasNext()) {
            LightCluster other = it.next();
            mergedBuffer += other.buffer;
            if (other.lit) setBlocksLit(other, false);

            for (long otherKey : other.blockKeys) {
                boolean otherLamp = other.lampKeys.contains(otherKey);
                bindCluster(primary.world, otherKey, primary);
                primary.addBlock(otherKey, otherLamp);
            }
            clustersById.remove(other.id);
            other.blockKeys.clear();
            other.lampKeys.clear();
            other.lit = false;
            other.buffer = 0;
        }

        primary.addBlock(key, lamp);
        bindCluster(primary.world, key, primary);
        if (!lamp) ensureJunctionNode(loc);
        // One batched pass re-points every lamp row (moved clusters included).
        writeLampMarkers(primary);
        primary.buffer = (int) Math.min(mergedBuffer, primary.bufferCapacity());
        primary.portCursor = 0;

        // The merged structure re-evaluates its state as a whole on the next tick.
        setBlocksLit(primary, false);
        primary.lit = false;

        ConsoleLogger.info("[LightMulti] Merged light structures into #" + primary.id
                + " (" + primary.lampCount() + " lamps, " + primary.junctionCount() + " junctions)");
    }

    // ════════════════════════════════════════
    // REDSTONE CHANGE NEXT TO A STRUCTURE BLOCK
    // ════════════════════════════════════════
    /**
     * Called by {@link LightRedstoneListener} for a redstone power change. When a
     * junction lost its signal the lamps go dark right away; when the structure
     * is powered the manager re-asserts its state on the bulbs near the change
     * (a copper bulb is a redstone toggle block and vanilla may have flipped its
     * {@code lit} behind us). The energy accounting itself stays on the
     * one-second tick.
     */
    public static void onRedstoneUpdate(Location changed) {
        changed = LocationUtil.normalize(changed);
        if (changed == null || changed.getWorld() == null) return;

        Set<LightCluster> touched = new LinkedHashSet<>();
        LightCluster atChanged = clusterAt(changed.getWorld(), toKey(changed));
        if (atChanged != null) touched.add(atChanged);
        for (long nk : neighborKeys(toKey(changed))) {
            LightCluster cluster = clusterAt(changed.getWorld(), nk);
            if (cluster != null) touched.add(cluster);
        }

        for (LightCluster cluster : touched) {
            if (!allJunctionsPowered(cluster)) {
                applyLit(cluster, false);
            } else {
                reassertBulbsNear(cluster, changed);
            }
        }
    }

    /** Re-writes the manager's lit state only for bulbs touching {@code changed}. */
    private static void reassertBulbsNear(LightCluster cluster, Location changed) {
        long changedKey = toKey(changed);
        setLampStateIfMember(cluster, changedKey);
        for (long nk : neighborKeys(changedKey)) setLampStateIfMember(cluster, nk);
    }

    private static void setLampStateIfMember(LightCluster cluster, long key) {
        if (!cluster.lampKeys.contains(key)) return;
        int x = getX(key), y = getY(key), z = getZ(key);
        if (!cluster.world.isChunkLoaded(x >> 4, z >> 4)) return;
        setBlockLitState(cluster.world.getBlockAt(x, y, z), cluster.lit);
    }

    // ════════════════════════════════════════
    // BLOCK BROKEN — the whole structure is dismantled
    // ════════════════════════════════════════
    public static void onBlockBroken(Location loc, Player player) {
        loc = LocationUtil.normalize(loc);
        if (loc == null || loc.getWorld() == null) return;

        LightCluster cluster = clusterAt(loc.getWorld(), toKey(loc));
        if (cluster == null) return;

        int lamps = cluster.lampCount();
        removeCluster(cluster, true);

        ConsoleLogger.info("[LightMulti] Disassembled structure #" + cluster.id
                + " due to a block break at " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());

        if (player != null) {
            player.sendMessage(MessageUtil.parse("<yellow>❕ Light structure disassembled ("
                    + lamps + " lamps went dark, block broken)</yellow>"));
        }
    }

    /** Drops the cluster: turns the lamps off, removes every registry row. */
    private static void removeCluster(LightCluster cluster, boolean unlight) {
        if (unlight) setBlocksLit(cluster, false);
        if (cluster.uuid != null && cluster.world != null) {
            StructureMarker.removeAllByUuid(cluster.world, cluster.uuid);
        }
        if (cluster.world != null) {
            Map<Long, LightCluster> map = clustersByWorld.get(worldUid(cluster.world));
            if (map != null) {
                for (long key : cluster.blockKeys) map.remove(key);
            }
        }
        clustersById.remove(cluster.id);
        cluster.blockKeys.clear();
        cluster.lampKeys.clear();
        cluster.lit = false;
        cluster.buffer = 0;
        cluster.portCursor = 0;
    }

    // ════════════════════════════════════════
    // TICK — once per second (UIEnergy schedules it)
    // ════════════════════════════════════════
    public static void tick() {
        List<Integer> toRemove = new ArrayList<>();

        for (LightCluster cluster : clustersById.values()) {
            try {
                if (cluster.world == null || cluster.blockKeys.isEmpty() || cluster.lampKeys.isEmpty()) {
                    toRemove.add(cluster.id);
                    continue;
                }

                // Anti-phantom: every loaded block of the structure must still be
                // what the structure expects (a world edit / explosion may have
                // removed a block without a BlockBreakEvent).
                if (!membershipIntact(cluster)) {
                    toRemove.add(cluster.id);
                    continue;
                }

                // 🔴 Every junction must carry redstone; otherwise the lamps are
                // dark and no energy is spent (the buffer is kept for later).
                if (!allJunctionsPowered(cluster)) {
                    applyLit(cluster, false);
                    continue;
                }

                int need = cluster.consumptionPerSecond();
                int capacity = cluster.bufferCapacity();
                if (cluster.buffer > capacity) cluster.buffer = capacity;

                if (!cluster.lit) {
                    // Try to start burning: wait for at least one second of energy.
                    refillFromNetwork(cluster, capacity);
                    if (cluster.buffer < need) {
                        applyLit(cluster, false);
                        continue;
                    }
                    cluster.buffer -= need;
                } else {
                    // The reserve covers the current second; pull only when it runs
                    // out (one batched pull per few seconds instead of a request
                    // every second), go dark when the network cannot deliver.
                    if (cluster.buffer < need) {
                        refillFromNetwork(cluster, capacity);
                        if (cluster.buffer < need) {
                            applyLit(cluster, false);
                            continue;
                        }
                    }
                    cluster.buffer -= need;
                }
                applyLit(cluster, true);
            } catch (Exception e) {
                logTickError(e);
            }
        }

        for (int id : toRemove) {
            LightCluster cluster = clustersById.get(id);
            if (cluster == null) continue;
            removeCluster(cluster, true);
            ConsoleLogger.info("[LightMulti] Removed phantom light structure #" + id);
        }
    }

    /** Tick errors are throttled to one line per minute (no console spam). */
    private static long lastTickErrorAt;

    private static void logTickError(Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastTickErrorAt < 60_000L) return;
        lastTickErrorAt = now;
        ConsoleLogger.warn("[LightMulti] Tick error: " + e.getMessage());
    }

    /** True when every loaded block still matches the structure layout. */
    private static boolean membershipIntact(LightCluster cluster) {
        for (long key : cluster.blockKeys) {
            int x = getX(key), y = getY(key), z = getZ(key);
            if (!cluster.world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Material type = cluster.world.getType(x, y, z);
            if (cluster.lampKeys.contains(key)) {
                if (type != Materials.WAXED_COPPER_BULB) return false;
            } else if (type != Materials.WAXED_CHISELED_COPPER) {
                return false;
            }
        }
        return true;
    }

    /** True when every junction block of the structure has a redstone signal. */
    private static boolean allJunctionsPowered(LightCluster cluster) {
        for (long key : cluster.blockKeys) {
            if (cluster.lampKeys.contains(key)) continue;
            int x = getX(key), y = getY(key), z = getZ(key);
            // Never force-load a chunk just to read a power state; an unloaded
            // junction means "not confirmed powered" → lamps stay off.
            if (!cluster.world.isChunkLoaded(x >> 4, z >> 4)) return false;
            Block block = cluster.world.getBlockAt(x, y, z);
            if (!block.isBlockPowered() && !block.isBlockIndirectlyPowered()) return false;
        }
        return true;
    }

    /**
     * Refills the local buffer to {@code capacity} through the junction ports of
     * the structure. A fixture may have several separate port nodes (bulbs
     * between two junctions split the cable graph) and only the one wired to a
     * source can deliver, so the ports are scanned with a persistent rotating
     * cursor: at most {@link #MAX_PORT_ATTEMPTS} per pass, continuing where the
     * last pass stopped. A failed scan therefore never starves a port that
     * happens to sit later in the set.
     * <p>
     * Energy is accounted gross ({@link EnergyFlow#pullGross}): the structure
     * stores exactly the energy the network gave up, so its 1 ⚡/s-per-lamp rate
     * does not depend on the distance to the battery.
     */
    private static void refillFromNetwork(LightCluster cluster, int capacity) {
        int deficit = capacity - cluster.buffer;
        if (deficit <= 0) return;

        List<Long> ports = new ArrayList<>();
        for (long key : cluster.blockKeys) {
            if (cluster.lampKeys.contains(key)) continue;
            Location port = new Location(cluster.world, getX(key), getY(key), getZ(key));
            if (isEnergyPort(port)) ports.add(key);
        }
        if (ports.isEmpty()) return;

        int size = ports.size();
        int start = cluster.portCursor % size;
        for (int i = 0; i < size && i < MAX_PORT_ATTEMPTS; i++) {
            int idx = (start + i) % size;
            long key = ports.get(idx);
            cluster.portCursor = (idx + 1) % size;

            Location port = new Location(cluster.world, getX(key), getY(key), getZ(key));
            int pulled = EnergyFlow.pullGross(port, deficit);
            if (pulled <= 0) continue;
            cluster.buffer = Math.min(capacity, cluster.buffer + pulled);
            deficit = capacity - cluster.buffer;
            if (deficit <= 0) return;
        }
    }

    /** A junction feeds the structure when it is a network node itself or touches one. */
    private static boolean isEnergyPort(Location junctionLoc) {
        if (CableNetwork.getNode(junctionLoc) != null) return true;
        for (Location near : LocationUtil.getNeighbors(junctionLoc)) {
            if (CableNetwork.getNode(near) != null) return true;
        }
        return false;
    }

    // ════════════════════════════════════════
    // BULB LIT STATE
    // ════════════════════════════════════════
    /** Sets the cluster state and re-asserts it on the blocks. */
    private static void applyLit(LightCluster cluster, boolean lit) {
        cluster.lit = lit;
        setBlocksLit(cluster, lit);
    }

    private static void setBlocksLit(LightCluster cluster, boolean lit) {
        if (cluster.world == null) return;
        for (long key : cluster.lampKeys) {
            int x = getX(key), y = getY(key), z = getZ(key);
            if (!cluster.world.isChunkLoaded(x >> 4, z >> 4)) continue;
            setBlockLitState(cluster.world.getBlockAt(x, y, z), lit);
        }
    }

    /**
     * Drives a copper bulb's visual state. The block is not a passive lamp: it is
     * a redstone component whose {@code lit} toggles on rising {@code powered}
     * edges, so the {@code powered} property is normalised to the real signal at
     * the same time. {@code applyPhysics=false} keeps the write from triggering
     * neighbour updates that would immediately fight it back.
     */
    private static void setBlockLitState(Block block, boolean lit) {
        if (block == null || block.getType() != Materials.WAXED_COPPER_BULB) return;
        try {
            BlockData data = block.getBlockData();
            if (!(data instanceof Lightable lightable)) return;

            boolean actualPowered = block.isBlockIndirectlyPowered();
            boolean litWrong = lightable.isLit() != lit;
            boolean poweredWrong = data instanceof Powerable powerable && powerable.isPowered() != actualPowered;
            if (!litWrong && !poweredWrong) return;

            if (litWrong) lightable.setLit(lit);
            if (data instanceof Powerable powerable) powerable.setPowered(actualPowered);
            block.setBlockData(data, false);
        } catch (Exception e) {
            ConsoleLogger.warn("[LightMulti] Could not toggle a copper bulb: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // QUERIES
    // ════════════════════════════════════════
    public static boolean isActive(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null && clusterAt(loc.getWorld(), toKey(loc)) != null;
    }

    public static LightCluster getCluster(Location loc) {
        loc = LocationUtil.normalize(loc);
        return loc != null ? clusterAt(loc.getWorld(), toKey(loc)) : null;
    }

    public static boolean isLit(Location loc) {
        LightCluster cluster = getCluster(loc);
        return cluster != null && cluster.lit;
    }

    public static Collection<LightCluster> getAllClusters() { return clustersById.values(); }
    public static int getClusterCount() { return clustersById.size(); }

    // ════════════════════════════════════════
    // SAVE — no-op (structure rows are written immediately)
    // ════════════════════════════════════════
    public static void saveAll() { /* no-op */ }
}
