package com.mcplugin.energy;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.util.LocationUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.*;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Electric Furnace — consumes 100 energy from cable network
 * to lightning-smelt items dropped on top of a BLAST_FURNACE.
 * Cable must be connected to the block BELOW (Y-1) the furnace.
 */
public class ElectricFurnaceManager implements Listener {

    private static ElectricFurnaceManager instance;

    // Cooldown per furnace location (prevents spam-cooking)
    private static final Map<Location, Long> cookingCooldowns = new ConcurrentHashMap<>();
    private static long cooldownMs = 1000L;

    // Periodic scan
    private static BukkitRunnable periodicScanTask = null;

    // Cooking recipe cache
    private static final List<CookingRecipe<?>> cookingRecipes = new ArrayList<>();

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new ElectricFurnaceManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        cacheCookingRecipes();
        loadConfig();
        startPeriodicItemScan();
        Main.getInstance().getLogger().info("[ElectricFurnace] Manager initialized.");
    }

    public static ElectricFurnaceManager getInstance() {
        return instance;
    }

    private static void loadConfig() {
        cooldownMs = Main.getInstance().getConfig()
                .getLong("energy.electric_furnace.cooldown_ms", 1000L);
    }

    // =========================
    // COOKING RECIPE CACHE
    // =========================
    private static void cacheCookingRecipes() {
        cookingRecipes.clear();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof CookingRecipe<?> cr) {
                cookingRecipes.add(cr);
            }
        }
    }

    private static Recipe findCookingRecipe(ItemStack input) {
        if (input == null || input.getType() == Material.AIR) return null;
        for (CookingRecipe<?> cr : cookingRecipes) {
            if (cr.getInputChoice().test(input)) return cr;
        }
        return null;
    }

    // =========================
    // ITEM SPAWN → DEFERRED CHECK
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!isEnabled()) return;

        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        // Defer check by 5 ticks so the item lands
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!item.isValid() || item.isDead()) return;
                Location itemLoc = item.getLocation();

                // Scan for blast furnaces nearby
                World world = itemLoc.getWorld();
                if (world == null) return;

                int bx = itemLoc.getBlockX();
                int bz = itemLoc.getBlockZ();
                int by = itemLoc.getBlockY();

                // Check blocks below the item
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Location blockLoc = new Location(world, bx + dx, by - 1, bz + dz);
                        if (blockLoc.getBlock().getType() != Material.BLAST_FURNACE) continue;

                        // Check if item is on top of this furnace
                        Location furnaceTop = blockLoc.clone().add(0.5, 1.2, 0.5);
                        if (itemLoc.distance(furnaceTop) > 1.5) continue;

                        if (tryCookItem(blockLoc, item, stack)) return;
                    }
                }
            }
        }.runTaskLater(Main.getInstance(), 5L);
    }

    // =========================
    // PERIODIC ITEM SCAN
    // =========================
    private static void startPeriodicItemScan() {
        if (periodicScanTask != null) {
            periodicScanTask.cancel();
        }
        periodicScanTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEnabled()) return;

                // Scan all cable nodes' nearby blast furnaces
                for (CableNode node : CableNetwork.getAllNodes()) {
                    Location nodeLoc = node.getLocation();

                    for (Location nearby : LocationUtil.getNeighbors(nodeLoc)) {
                        Location blockLoc = LocationUtil.normalize(nearby);
                        if (blockLoc == null) continue;
                        if (blockLoc.getBlock().getType() != Material.BLAST_FURNACE) continue;

                        // Cooldown check (cable-below check is done in tryCookItem)
                        long now = System.currentTimeMillis();
                        Long lastCook = cookingCooldowns.get(blockLoc);
                        if (lastCook != null && (now - lastCook) < cooldownMs) continue;

                        // Scan for items on top
                        World world = blockLoc.getWorld();
                        if (world == null) continue;
                        Location furnaceTop = blockLoc.clone().add(0.5, 1.2, 0.5);

                        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(
                                furnaceTop, 1.5, 0.6, 1.5,
                                e -> e instanceof Item && e.isValid())) {
                            Item item = (Item) entity;
                            ItemStack stack = item.getItemStack();
                            if (stack == null || stack.getType() == Material.AIR) continue;

                            if (tryCookItem(blockLoc, item, stack)) break;
                        }
                    }
                }
            }
        };
        periodicScanTask.runTaskTimer(Main.getInstance(), 20L, 20L);
    }

    // =========================
    // PROTECT ITEMS FROM LIGHTNING
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.LIGHTNING) return;

        // Protect items near our electric furnaces from being destroyed by lightning
        org.bukkit.entity.Entity entity = event.getEntity();
        if (!(entity instanceof Item)) return;

        Location itemLoc = entity.getLocation();
        World world = itemLoc.getWorld();
        if (world == null) return;

        // Check if item is on top of any blast furnace
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location blockLoc = new Location(world, itemLoc.getBlockX() + dx,
                        itemLoc.getBlockY() - 1, itemLoc.getBlockZ() + dz);
                if (blockLoc.getBlock().getType() != Material.BLAST_FURNACE) continue;

                Location furnaceTop = blockLoc.clone().add(0.5, 1.2, 0.5);
                if (entity.getLocation().distance(furnaceTop) < 2.0) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // =========================
    // SHARED COOKING LOGIC
    // =========================
    private static boolean tryCookItem(Location furnaceLoc, Item item, ItemStack stack) {
        if (!isEnabled()) return false;

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastCook = cookingCooldowns.get(furnaceLoc);
        if (lastCook != null && (now - lastCook) < cooldownMs) return false;

        // Check cable connected BELOW the furnace
        Location belowFurnace = furnaceLoc.clone().add(0, -1, 0);
        if (!isCableBelow(belowFurnace)) return false;

        // Check cooking recipe
        Recipe cooked = findCookingRecipe(stack);
        if (cooked == null) return false;

        // Check and consume energy
        int energyCost = getEnergyCost();
        if (!hasNetworkEnergy(belowFurnace, energyCost)) return false;
        if (!takeNetworkEnergy(belowFurnace, energyCost)) return false;

        cookingCooldowns.put(furnaceLoc, now);

        World world = furnaceLoc.getWorld();
        if (world == null) return false;

        // Lightning effect
        world.strikeLightningEffect(furnaceLoc.clone().add(0.5, 1.5, 0.5));
        world.spawnParticle(Particle.ELECTRIC_SPARK,
                furnaceLoc.clone().add(0.5, 1.0, 0.5), 30, 0.5, 0.5, 0.5, 0);
        world.playSound(furnaceLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);

        // Replace item with cooked result
        ItemStack result = cooked.getResult().clone();
        result.setAmount(stack.getAmount());

        if (item.isValid() && !item.isDead()) {
            item.getWorld().dropItemNaturally(item.getLocation(), result);
            item.remove();
        }

        return true;
    }

    // =========================
    // CABLE BELOW CHECK
    // =========================
    private static boolean isCableBelow(Location below) {
        below = LocationUtil.normalize(below);
        if (below == null) return false;
        return CableNetwork.getNode(below) != null;
    }

    // =========================
    // NETWORK ENERGY CHECK (BFS)
    // =========================
    private static boolean hasNetworkEnergy(Location cableLoc, int amount) {
        CableNode start = CableNetwork.getNode(LocationUtil.normalize(cableLoc));
        if (start == null) return false;

        Set<Location> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getLocation());

        int total = 0;
        while (!queue.isEmpty()) {
            CableNode node = queue.poll();
            if (node == null) continue;
            total += node.getEnergy();
            if (total >= amount) return true;

            for (Location conn : node.getConnections()) {
                if (visited.contains(conn)) continue;
                CableNode next = CableNetwork.getNode(conn);
                if (next == null) continue;
                visited.add(conn);
                queue.add(next);
            }
        }
        return total >= amount;
    }

    // =========================
    // NETWORK ENERGY CONSUME (BFS)
    // =========================
    private static boolean takeNetworkEnergy(Location cableLoc, int amount) {
        CableNode start = CableNetwork.getNode(LocationUtil.normalize(cableLoc));
        if (start == null) return false;

        Set<Location> visited = new HashSet<>();
        Queue<CableNode> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start.getLocation());

        int remaining = amount;
        while (!queue.isEmpty() && remaining > 0) {
            CableNode node = queue.poll();
            if (node == null) continue;

            int energy = node.getEnergy();
            if (energy > 0) {
                int take = Math.min(energy, remaining);
                node.setEnergy(energy - take);
                remaining -= take;
            }

            for (Location conn : node.getConnections()) {
                if (visited.contains(conn)) continue;
                CableNode next = CableNetwork.getNode(conn);
                if (next == null) continue;
                visited.add(conn);
                queue.add(next);
            }
        }
        return remaining <= 0;
    }

    // =========================
    // CONFIG
    // =========================
    private static boolean isEnabled() {
        return Main.getInstance().getConfig()
                .getBoolean("energy.electric_furnace.enabled", true);
    }

    private static int getEnergyCost() {
        return Main.getInstance().getConfig()
                .getInt("energy.electric_furnace.energy_per_smelt", 100);
    }

    // =========================
    // CLEANUP
    // =========================
    public static void clearAll() {
        cookingCooldowns.clear();
    }
}
