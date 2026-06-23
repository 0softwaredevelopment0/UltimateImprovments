package com.mcplugin.mechanics.environment.lightning;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.LocationUtil;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;

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

    // Periodic scan task — catches items that were already on the rod
    // (chunk loads, structure assembly with existing items, etc.)
    private static BukkitRunnable periodicScanTask = null;

    // =========================
    // INIT
    // =========================
    public static void init() {
        instance = new LightningManager();
        Bukkit.getPluginManager().registerEvents(instance, Main.getInstance());
        cacheCookingRecipes();
        startPeriodicItemScan();
        Main.getInstance().getLogger().info("[Lightning] Manager initialized.");
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
        loc = LocationUtil.normalize(loc);
        if (loc == null) return false;
        // Check if loc is part of any active structure
        for (Location center : activeStructures.keySet()) {
            if (LightningStructure.isPartOfStructure(center, loc)) {
                return activeStructures.get(center);
            }
        }
        return false;
    }

    public static void setEnabled(Location center, boolean enabled) {
        center = LocationUtil.normalize(center);
        if (activeStructures.containsKey(center)) {
            activeStructures.put(center, enabled);
            Main.getInstance().getLogger().info(
                "[Lightning] Structure at " + center + " " + (enabled ? "enabled" : "disabled"));
        }
    }

    // =========================
    // ASSEMBLE
    // =========================
    public static void assemble(Location center, ItemFrame frame, Player player) {
        center = LocationUtil.normalize(center);
        if (center == null || center.getWorld() == null) {
            if (player != null) player.sendMessage("§4❌ §cНекорректная позиция!");
            return;
        }

        if (activeStructures.containsKey(center)) {
            if (player != null) player.sendMessage("§e⚡ Структура молний уже собрана на этом месте!");
            return;
        }

        if (player != null) {
            player.sendMessage("§8[§e⚡ Молнии§8] §7Проверка структуры...");
        }

        final World world = center.getWorld();

        // Validate on main thread (block reads require Bukkit API on main thread)
        try {
            List<String> errors = LightningStructure.getValidationErrors(center);
            if (!errors.isEmpty()) {
                if (player != null) {
                    player.sendMessage("§4❌ §cСтруктура молний повреждена! §7Ошибки:");
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

            // Activate
            activeStructures.put(center, true);

            // Effects
            world.strikeLightningEffect(center.clone().add(0.5, 1.5, 0.5));
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    center.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0);
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);

            if (player != null) {
                player.sendMessage("§a✔ §fСтруктура молний собрана!");
                player.sendMessage("§8┃ §7Бросайте предметы на громоотвод — молния переплавит их!");
                player.sendMessage("§8┃ §7Команды: §f/mp str lightning enable§7/§cdisable §7/ §fstats");
            }

            Main.getInstance().getLogger().info(
                "[Lightning] Structure assembled at " + center
                + " by " + (player != null ? player.getName() : "unknown"));
        } catch (Exception e) {
            if (player != null) {
                player.sendMessage("§4❌ §cОшибка при проверке структуры!");
            }
            Main.getInstance().getLogger().severe("[Lightning] Assembly error: " + e.getMessage());
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
            center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    center.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0);
            Main.getInstance().getLogger().info("[Lightning] Structure disassembled at " + center);
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
        Main.getInstance().getLogger().info("[Lightning] Cached " + cookingRecipes.size() + " cooking recipes.");
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
        return "§8┃ §e⚡ Структура молний §8» §f"
                + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()
                + " §8[" + (enabled ? "§a✔ Вкл" : "§c❌ Выкл") + "§8]";
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

        // Check if item is cookable
        Recipe cooked = findCookingRecipe(stack);
        if (cooked == null) return false;

        // ⚡ ЭНЕРГИЯ: проверяем кабель у WAXED_CHISELED_COPPER (0, -3, 0)
        if (!hasEnergyForOperation(center)) {
            return false;
        }

        cookingCooldowns.put(center, now);

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
