package com.ultimateimprovements.mechanics.environment.lightning;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.structure.StructureMarker;
import com.ultimateimprovements.util.LocationUtil;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.energy.storage.battery.BatteryManager;
import com.ultimateimprovements.energy.transfer.cable.CableNetwork;
import com.ultimateimprovements.energy.transfer.cable.CableNode;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
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
 * Manages active lightning structures.
 * Handles assembly, disassembly, persistence, and lightning cooking.
 */
public class LightningManager implements Listener {

    private static LightningManager instance;

    // Active structures: center location → enabled flag
    private static final Map<Location, Boolean> activeStructures = new ConcurrentHashMap<>();

    // Cooldown per structure (prevents spam-cooking)
    private static final Map<Location, Long> cookingCooldowns = new ConcurrentHashMap<>();
    private static final long COOKING_COOLDOWN_MS = 1000L; // 1 second

    // Защита от дублирования крафта: UUID предметов, которые уже были обработаны
    private static final Set<UUID> cookedItemIds = ConcurrentHashMap.newKeySet();
    // Периодическая очистка cookedItemIds от старых записей (каждые 100 тиков)
    private static int cookedItemCleanupTick = 0;

    // Periodic scan task — catches items that were already on the rod
    // (chunk loads, structure assembly with existing items, etc.)
    private static BukkitRunnable periodicScanTask = null;

    // =========================
    // INIT — rebuild from Marker entities
    // =========================
    public static void init() {
        instance = new LightningManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        rebuildFromMarkers();
        cacheCookingRecipes();
        startPeriodicItemScan();
        ConsoleLogger.info("[Lightning] Manager initialized.");
    }

    public static void rebuildFromMarkers() {
        int count = 0;
        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            if (!"lightning".equals(entry.getValue().type())) continue;
            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);
            for (World w : Bukkit.getWorlds()) {
                if (w.getUID().toString().equals(worldUid)) {
                    Location loc = LocationUtil.normalize(new Location(w, x, y, z));
                    if (LightningStructure.isValid(loc, false)) {
                        activeStructures.put(loc, true);
                        count++;
                    }
                    break;
                }
            }
        }
        // Log suppressed — too spammy on server start
    }

    public static LightningManager getInstance() {
        return instance;
    }

    // =========================
    // STATE
    // =========================
    public static boolean isActive(Location center) {
        center = LocationUtil.normalize(center);
        return activeStructures.getOrDefault(center, false);
    }

    public static boolean isActiveAt(Location loc) {
        return getCenterForBlock(loc) != null;
    }

    /**
     * Находит центр активной структуры молний, в которую входит данный блок.
     */
    public static Location getCenterForBlock(Location loc) {
        loc = LocationUtil.normalize(loc);
        if (loc == null) return null;
        for (Location center : activeStructures.keySet()) {
            if (LightningStructure.isPartOfStructure(center, loc)) {
                return center;
            }
        }
        return null;
    }

    public static void setEnabled(Location center, boolean enabled) {
        center = LocationUtil.normalize(center);
        if (activeStructures.containsKey(center)) {
            activeStructures.put(center, enabled);
            ConsoleLogger.info(
                "[Lightning] Structure at " + center + " " + (enabled ? "enabled" : "disabled"));
        }
    }

    // =========================
    // ASSEMBLE (+ Marker entity)
    // =========================
    public static void assemble(Location center, ItemFrame frame, Player player) {
        center = LocationUtil.normalize(center);
        if (center == null || center.getWorld() == null) {
            if (player != null) player.sendMessage("§4❌ §cInvalid position!");
            return;
        }

        if (activeStructures.containsKey(center)) {
            if (player != null) player.sendMessage("§e⚡ Lightning structure already assembled here!");
            return;
        }

        if (player != null) {
            player.sendMessage("§8[§e⚡ Lightning§8] §7Checking structure...");
        }

        final World world = center.getWorld();

        try {
            List<String> errors = LightningStructure.getValidationErrors(center);
            if (!errors.isEmpty()) {
                if (player != null) {
                    player.sendMessage("§4❌ §cLightning structure damaged! §7Errors:");
                    for (String err : errors) {
                        player.sendMessage("§8 • §f" + err);
                    }
                }
                return;
            }

            // Remove item frame & drop it
            if (frame != null && frame.isValid() && !frame.isDead()) {
                Location frameLoc = frame.getLocation();
                frame.getWorld().dropItemNaturally(frameLoc, new ItemStack(Material.ITEM_FRAME));
                frame.remove();
            }

            // Activate + Marker entity
            activeStructures.put(center, true);
            StructureMarker.place(center, "lightning", UUID.randomUUID());

            // Effects
            world.strikeLightningEffect(center.clone().add(0.5, 1.5, 0.5));
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    center.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0);
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);

            if (player != null) {
                player.sendMessage("§a✔ §fLightning structure assembled!");
                player.sendMessage("§8┃ §7Drop items on the lightning rod — lightning will smelt them!");
                player.sendMessage("§8┃ §7Commands: §f/mp str lightning enable§7/§cdisable §7/ §fstats");
            }

            ConsoleLogger.info(
                "[Lightning] Structure assembled at " + center
                + " by " + (player != null ? player.getName() : "unknown"));
        } catch (Exception e) {
            if (player != null) {
                player.sendMessage("§4❌ §cError checking structure!");
            }
            ConsoleLogger.error("[Lightning] Assembly error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // DISASSEMBLE
    // =========================
    public static void disassemble(Location center) {
        center = LocationUtil.normalize(center);
        if (center != null && activeStructures.containsKey(center)) {
            activeStructures.remove(center);
            cookingCooldowns.remove(center);
            StructureMarker.removeAt(center);
            center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    center.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0);
            ConsoleLogger.info("[Lightning] Structure disassembled at " + center);
        }
    }

    // =========================
    // ⚡ LIGHTNING COOKING — ItemSpawnEvent (any source)
    // When an item appears on/above the lightning rod of an assembled structure,
    // lightning strikes, and the cooked/smelted version replaces the original item.
    // Also works via periodic scan for items already on the rod (chunk loads, etc.).
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        // Defer check by 5 ticks so the item lands
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!item.isValid() || item.isDead()) return;
                Location itemLoc = item.getLocation();

                // Find nearby active lightning structure
                for (Map.Entry<Location, Boolean> entry : activeStructures.entrySet()) {
                    if (!entry.getValue()) continue; // disabled
                    Location center = entry.getKey();

                    // Check if item is on/near the lightning rod (top of structure)
                    Location rodTop = center.clone().add(0.5, 1.2, 0.5);
                    if (itemLoc.getWorld() != rodTop.getWorld()) continue;
                    if (itemLoc.distance(rodTop) > 1.5) continue;

                    if (tryCookItem(center, item, stack)) return;
                }
            }
        }.runTaskLater(Main.getInstance(), 5L);
    }

    // =========================
    // COOKING RECIPE CACHE (populated once at init)
    // =========================
    private static final List<CookingRecipe<?>> cookingRecipes = new ArrayList<>();

    private static void cacheCookingRecipes() {
        cookingRecipes.clear();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof CookingRecipe<?> cr) {
                cookingRecipes.add(cr);
            }
        }
        ConsoleLogger.info("[Lightning] Cached " + cookingRecipes.size() + " cooking recipes.");
    }

    private static Recipe findCookingRecipe(ItemStack input) {
        if (input == null || input.getType() == Material.AIR) return null;
        for (CookingRecipe<?> cr : cookingRecipes) {
            if (cr.getInputChoice().test(input)) return cr;
        }
        return null;
    }

    // =========================
    // PROTECT ITEMS FROM LIGHTNING DAMAGE
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.LIGHTNING) return;
        Entity entity = event.getEntity();

        // Protect items near our structure from being destroyed by lightning
        for (Location center : activeStructures.keySet()) {
            if (!activeStructures.get(center)) continue;
            if (entity.getWorld() != center.getWorld()) continue;
            Location rodTop = center.clone().add(0.5, 1.2, 0.5);
            if (entity.getLocation().distance(rodTop) < 2.0) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // =========================
    // STATS
    // =========================
    public static String getStats(Location center) {
        center = LocationUtil.normalize(center);
        if (center == null || !activeStructures.containsKey(center)) return null;

        boolean enabled = activeStructures.get(center);
        return "§8┃ §e⚡ Lightning structure §8» §f"
                + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()
                + " §8[" + (enabled ? "§a✔ On" : "§c❌ Off") + "§8]";
    }

    public static Collection<Location> getActiveLocations() {
        return new ArrayList<>(activeStructures.keySet());
    }

    // =========================
    // PERIODIC ITEM SCAN (every 20 ticks = 1 second)
    // Catches items already on the rod (chunk loads, assembly with existing items, etc.)
    // =========================
    private static void startPeriodicItemScan() {
        if (periodicScanTask != null) {
            periodicScanTask.cancel();
        }
        periodicScanTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Периодическая очистка cookedItemIds от старых записей (каждые ~5 секунд)
                cookedItemCleanupTick++;
                if (cookedItemCleanupTick >= 100) {
                    cookedItemCleanupTick = 0;
                    cookedItemIds.clear();
                }

                for (Map.Entry<Location, Boolean> entry : activeStructures.entrySet()) {
                    if (!entry.getValue()) continue; // disabled
                    Location center = entry.getKey();
                    World world = center.getWorld();
                    if (world == null) continue;

                    // Cooldown check
                    long now = System.currentTimeMillis();
                    Long lastCook = cookingCooldowns.get(center);
                    if (lastCook != null && (now - lastCook) < COOKING_COOLDOWN_MS) continue;

                    // Scan for items near the rod top
                    Location rodTop = center.clone().add(0.5, 1.2, 0.5);
                    for (Entity entity : world.getNearbyEntities(rodTop, 1.5, 0.6, 1.5, e -> e instanceof Item && e.isValid())) {
                        Item item = (Item) entity;
                        ItemStack stack = item.getItemStack();
                        if (stack == null || stack.getType() == Material.AIR) continue;

                        if (tryCookItem(center, item, stack)) break; // one cook per structure per scan
                    }
                }
            }
        };
        periodicScanTask.runTaskTimer(Main.getInstance(), 20L, 20L);
    }

    // =========================
    // SHARED COOKING LOGIC
    // =========================
    /**
     * Attempts to cook an item using a lightning structure's rod.
     * Handles cooldown, effects, and item replacement.
     * @return true if cooking was performed, false otherwise
     */
    private static final int ENERGY_COST = 100;

    /**
     * Проверяет, есть ли кабель рядом с энергетическим блоком (WAXED_CHISELED_COPPER на Y=-3)
     * и хватает ли энергии (100 за операцию).
     */
    private static boolean hasEnergyForOperation(Location center) {
        Location energyLoc = LightningStructure.getEnergyInputLoc(center);
        if (energyLoc == null) return false;

        for (Location near : LocationUtil.getNeighbors(energyLoc)) {
            Location norm = LocationUtil.normalize(near);
            if (norm == null) continue;
            CableNode node = CableNetwork.getNode(norm);
            if (node != null && node.getEnergy() >= ENERGY_COST
                    && LocationUtil.isFullyConnected(energyLoc, norm)) {
                // Проверяем режим батареи: берём только из DISCHARGE/CHARGE_DISCHARGE
                BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
                if (bc != null && !bc.canDischarge()) continue;
                node.removeEnergy(ENERGY_COST);
                return true;
            }
        }
        return false;
    }

    private static boolean tryCookItem(Location center, Item item, ItemStack stack) {
        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastCook = cookingCooldowns.get(center);
        if (lastCook != null && (now - lastCook) < COOKING_COOLDOWN_MS) return false;

        // Защита от дублирования: проверяем, не был ли уже обработан этот предмет
        if (item.getUniqueId() != null && cookedItemIds.contains(item.getUniqueId())) return false;

        // Check if item is cookable
        Recipe cooked = findCookingRecipe(stack);
        if (cooked == null) return false;

        // ⚡ ЭНЕРГИЯ: проверяем кабель у WAXED_CHISELED_COPPER (0, -3, 0)
        if (!hasEnergyForOperation(center)) {
            return false;
        }

        cookingCooldowns.put(center, now);

        // Помечаем предмет как обработанный
        if (item.getUniqueId() != null) {
            cookedItemIds.add(item.getUniqueId());
        }

        // Strike lightning!
        World world = center.getWorld();
        if (world == null) return false;
        world.strikeLightningEffect(center.clone().add(0.5, 1.5, 0.5));
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);

        // Replace item with cooked result
        final ItemStack result = cooked.getResult().clone();
        result.setAmount(stack.getAmount());

        if (item.isValid() && !item.isDead()) {
            item.getWorld().dropItemNaturally(item.getLocation(), result);
            item.remove();
        }

        return true;
    }

    // =========================
    // CLEANUP
    // =========================
    public static void clearAll() {
        activeStructures.clear();
        cookingCooldowns.clear();
        // Keep periodic scan running — it iterates an empty map harmlessly.
        // Will resume scanning when new structures are assembled.
    }
}
