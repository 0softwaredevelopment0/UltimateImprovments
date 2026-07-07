package com.mcplugin.mechanics.particle;

import com.mcplugin.core.Main;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.transfer.cable.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ParticleAcceleratorManager implements Listener {

    // =========================
    // BLOCK TYPES
    // =========================
    public static final Material RING = Material.CHISELED_TUFF;
    public static final Material ENGINE = Material.TUFF_BRICKS;
    public static final Material SENSOR = Material.POLISHED_DIORITE;
    public static final Material INJECTOR = Material.REINFORCED_DEEPSLATE;

    public static final Set<Material> ACCELERATOR_BLOCKS = Set.of(RING, ENGINE, SENSOR, INJECTOR);

    // =========================
    // PDC KEYS
    // =========================
    public static final NamespacedKey PARTICLE_ID_KEY = new NamespacedKey("mcplugin", "particle_id");

    // =========================
    // ENGINE CONFIG
    // =========================
    private static final int ENGINE_MAX_ENERGY = 500;
    private static final int ENGINE_COST_PER_USE = 50;
    private static final int ENGINE_CHARGE_RATE = 10;
    public static final double SPEED_INCREMENT = 0.1;
    public static final double MAX_SPEED = 5.0;
    public static final double INITIAL_SPEED = 0.1;

    // Engine energy buffers: block location → energy amount
    private static final Map<Location, Integer> engineEnergy = new ConcurrentHashMap<>();

    // Active particles
    private static final Map<UUID, ParticleData> activeParticles = new ConcurrentHashMap<>();

    private static boolean enabled = true;

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        enabled = plugin.getConfig().getBoolean("particle_accelerator.enabled", true);
        Bukkit.getPluginManager().registerEvents(new ParticleAcceleratorManager(), plugin);
        scanExistingAccelerators();

        ConsoleLogger.info("[ParticleAccelerator] Manager initialized.");
    }

    // =========================
    // SCAN EXISTING — rebuild from Marker entities
    // =========================
    private static void scanExistingAccelerators() {
        for (var entry : StructureMarker.getAllEntries()) {
            String type = entry.getValue().type();
            if (!"accelerator".equals(type)) continue;

            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);

            for (World world : Bukkit.getWorlds()) {
                if (!world.getUID().toString().equals(worldUid)) continue;
                Location loc = new Location(world, x, y, z);
                Material mat = loc.getBlock().getType();
                if (!ACCELERATOR_BLOCKS.contains(mat)) {
                    // Block was replaced — remove marker
                    StructureMarker.removeAt(loc);
                    continue;
                }
                // If it's an engine, initialize energy buffer
                if (mat == ENGINE) {
                    engineEnergy.putIfAbsent(LocationUtil.normalize(loc), 0);
                }
                break;
            }
        }
        ConsoleLogger.info("[ParticleAccelerator] Scanned existing accelerators.");
    }

    // =========================
    // PARTICLE DATA
    // =========================
    public static class ParticleData {
        public final UUID id;
        public Marker entity;
        public Location location;
        public final String itemName; // Material name of source item
        public final Material sourceMaterial;
        public List<Location> path;
        public int pathIndex;
        public double speed;
        public boolean dead = false;

        public ParticleData(UUID id, Location start, Material source, List<Location> path) {
            this.id = id;
            this.location = start.clone();
            this.sourceMaterial = source;
            this.itemName = source.name();
            this.path = path;
            this.pathIndex = 0;
            this.speed = INITIAL_SPEED;
        }
    }

    // =========================
    // PUBLIC API
    // =========================
    public static Collection<ParticleData> getActiveParticles() {
        return activeParticles.values();
    }

    public static void removeParticle(UUID id) {
        ParticleData data = activeParticles.remove(id);
        if (data != null && data.entity != null && !data.entity.isDead()) {
            data.entity.remove();
        }
    }

    public static Map<Location, Integer> getEngineEnergy() {
        return engineEnergy;
    }

    public static int getEngineEnergy(Location loc) {
        Location norm = LocationUtil.normalize(loc);
        if (norm == null) return 0;
        return engineEnergy.getOrDefault(norm, 0);
    }

    // =========================
    // ADD PARTICLE from injector
    // =========================
    public static ParticleData createParticle(Location injectorLoc, ItemStack item) {
        if (!enabled) return null;
        Location norm = LocationUtil.normalize(injectorLoc);
        if (norm == null) return null;

        // Find path from injector
        List<Location> path = findPath(norm);
        if (path.isEmpty()) return null;

        return createParticleWithPath(norm, item, path);
    }

    // =========================
    // CREATE PARTICLE WITH PRE-COMPUTED PATH
    // =========================
    public static ParticleData createParticleWithPath(Location normLoc, ItemStack item, List<Location> path) {
        World world = normLoc.getWorld();
        if (world == null || path.isEmpty()) return null;

        UUID id = UUID.randomUUID();
        Material sourceMat = item.getType();

        // Spawn Marker entity at injector center
        Location spawnLoc = normLoc.clone().add(0.5, 0.5, 0.5);
        Marker marker = world.spawn(spawnLoc, Marker.class);
        marker.setPersistent(false);

        PersistentDataContainer pdc = marker.getPersistentDataContainer();
        pdc.set(PARTICLE_ID_KEY, PersistentDataType.STRING, id.toString());

        ParticleData data = new ParticleData(id, spawnLoc, sourceMat, path);
        data.entity = marker;
        activeParticles.put(id, data);

        ConsoleLogger.info("[ParticleAccelerator] Created particle " + id.toString().substring(0, 8)
                + " from " + sourceMat.name() + " at " + normLoc.getBlockX() + " " + normLoc.getBlockY() + " " + normLoc.getBlockZ());

        return data;
    }

    // =========================
    // PATH FINDING
    // =========================
    private static List<Location> findPath(Location start) {
        List<Location> path = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Location current = LocationUtil.normalize(start);
        if (current == null) return path;

        visited.add(current);

        // Scan for first neighbor
        Location next = findNextAcceleratorBlock(current, null);
        while (next != null && !visited.contains(next)) {
            path.add(next);
            visited.add(next);
            Location prev = current;
            current = next;
            next = findNextAcceleratorBlock(current, prev);
        }

        return path;
    }

    /**
     * Check if two paths share at least one block location.
     */
    private static boolean pathsOverlap(List<Location> pathA, List<Location> pathB) {
        Set<Location> setA = new HashSet<>(pathA);
        for (Location loc : pathB) {
            if (setA.contains(loc)) return true;
        }
        return false;
    }

    private static Location findNextAcceleratorBlock(Location from, Location exclude) {
        if (from == null || from.getWorld() == null) return null;
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : dirs) {
            int nx = from.getBlockX() + d[0];
            int ny = from.getBlockY() + d[1];
            int nz = from.getBlockZ() + d[2];
            Location neighbor = new Location(from.getWorld(), nx, ny, nz);
            if (exclude != null && neighbor.equals(exclude)) continue;
            if (ACCELERATOR_BLOCKS.contains(neighbor.getBlock().getType())) {
                return neighbor;
            }
        }
        return null;
    }

    // =========================
    // CHARGE ENGINES — called from ParticleMovementTask each tick
    // =========================
    public static void chargeEngines() {
        for (Map.Entry<Location, Integer> entry : engineEnergy.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) continue; // skip unloaded worlds
            int current = entry.getValue();
            if (current >= ENGINE_MAX_ENERGY) continue;

            // Try to pull energy from adjacent cables
            int pulled = pullFromCables(loc, ENGINE_CHARGE_RATE);
            int newEnergy = Math.min(ENGINE_MAX_ENERGY, current + pulled);
            entry.setValue(newEnergy);
        }
    }

    private static int pullFromCables(Location engineLoc, int maxAmount) {
        if (maxAmount <= 0) return 0;

        // Scan adjacent blocks for cable nodes
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        int totalPulled = 0;

        for (int[] d : dirs) {
            if (totalPulled >= maxAmount) break;
            Location neighbor = new Location(engineLoc.getWorld(),
                    engineLoc.getBlockX() + d[0],
                    engineLoc.getBlockY() + d[1],
                    engineLoc.getBlockZ() + d[2]);

            CableNode node = CableNetwork.getNode(neighbor);
            if (node == null) continue;
            if (node.getType() != NodeType.CABLE) continue;

            int available = node.getEnergy();
            if (available <= 0) continue;

            int toTake = Math.min(available, maxAmount - totalPulled);
            node.removeEnergy(toTake);
            totalPulled += toTake;
            CableNetwork.markFlowing(neighbor);
        }

        return totalPulled;
    }

    // =========================
    // CONSUME ENGINE ENERGY — called when particle passes through engine
    // =========================
    public static boolean consumeEngineEnergy(Location engineLoc) {
        Location norm = LocationUtil.normalize(engineLoc);
        if (norm == null) return false;
        int current = engineEnergy.getOrDefault(norm, 0);
        if (current < ENGINE_COST_PER_USE) return false;
        engineEnergy.put(norm, current - ENGINE_COST_PER_USE);
        return true;
    }

    // =========================
    // BLOCK PLACE
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        Material type = block.getType();
        if (!ACCELERATOR_BLOCKS.contains(type)) return;

        Location loc = LocationUtil.normalize(block.getLocation());
        if (loc == null) return;

        // Create Marker entity
        StructureMarker.place(loc, "accelerator", UUID.randomUUID());

        // Initialize engine energy buffer
        if (type == ENGINE) {
            engineEnergy.putIfAbsent(loc, 0);
        }

        ConsoleLogger.info("[ParticleAccelerator] Placed " + type.name() + " at "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    // =========================
    // BLOCK BREAK
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Material type = block.getType();
        if (!ACCELERATOR_BLOCKS.contains(type)) return;

        Location loc = LocationUtil.normalize(block.getLocation());
        if (loc == null) return;

        // Remove Marker
        StructureMarker.removeAt(loc);

        // Clean up engine energy buffer
        if (type == ENGINE) {
            engineEnergy.remove(loc);
        }

        // Clean up any particles that are passing through this block
        activeParticles.values().removeIf(p -> {
            if (p.dead) return true;
            Location pLoc = LocationUtil.normalize(p.location);
            if (pLoc != null && pLoc.equals(loc)) {
                p.dead = true;
                if (p.entity != null && !p.entity.isDead()) p.entity.remove();
                return true;
            }
            return false;
        });

        ConsoleLogger.info("[ParticleAccelerator] Broken " + type.name() + " at "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    // =========================
    // INJECTOR INTERACTION — create particle from item
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onInjectorInteract(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null || clicked.getType() != INJECTOR) return;

        // Only work on blocks that have been placed as accelerator blocks (have Marker)
        Location clickLoc = LocationUtil.normalize(clicked.getLocation());
        if (clickLoc == null || !StructureMarker.existsAt(clickLoc)) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage("§7[§b⚠§7] §7Hold an item to inject it as a particle!");
            return;
        }

        e.setCancelled(true);
        Location loc = clicked.getLocation();

        // Compute path first (before checking if already running)
        Location normLoc = LocationUtil.normalize(loc);
        List<Location> newPath = findPath(normLoc);
        if (newPath.isEmpty()) {
            player.sendMessage("§7[§c✗§7] §cNo accelerator path found! Place rings/engines/sensors in a line.");
            return;
        }

        // Don't inject if there's already a particle in this accelerator
        boolean alreadyRunning = activeParticles.values().stream()
                .anyMatch(p -> !p.dead && pathsOverlap(p.path, newPath));
        if (alreadyRunning) {
            player.sendMessage("§7[§b⚠§7] §7A particle is already running in this accelerator!");
            return;
        }

        // Create the particle with pre-computed path
        ParticleData data = createParticleWithPath(normLoc, hand, newPath);
        if (data == null) {
            player.sendMessage("§7[§c✗§7] §cNo accelerator path found! Place rings/engines/sensors in a line.");
            return;
        }

        // Consume one item from hand
        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        // Visual feedback
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        loc.getWorld().spawnParticle(Particle.END_ROD,
                loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.01);

        player.sendMessage("§7[§a✓§7] §f" + formatMaterialName(data.sourceMaterial.name())
                + " §7particle injected! Speed: §b" + String.format("%.1f", data.speed)
                + " §7(" + String.format("%.3f", data.speed / MAX_SPEED * 100.0) + "% light speed)");
    }

    // =========================
    // SHUTDOWN — clean up on disable
    // =========================
    public static void shutdown() {
        // Remove all particle Marker entities
        for (ParticleData p : activeParticles.values()) {
            if (p.entity != null && !p.entity.isDead()) {
                p.entity.remove();
            }
        }
        activeParticles.clear();
        engineEnergy.clear();
        ConsoleLogger.info("[ParticleAccelerator] Shutdown complete.");
    }

    // =========================
    // UTILITY
    // =========================
    private static String formatMaterialName(String name) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (c == '_') { nextUpper = true; continue; }
            result.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c));
            nextUpper = false;
        }
        return result.toString();
    }

    public static boolean isEnabled() { return enabled; }
}
