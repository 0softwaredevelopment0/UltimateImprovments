package com.mcplugin.features.minecartspeed;

import com.mcplugin.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управляет скоростью вагонеток с экспоненциальным разгоном на энергорельсах.
 * <p>
 * <b>Все внутренние вычисления — в блоках/тик (blocks/tick).</b>
 * Конфиг хранит значения в блоках/сек для читаемости, конвертация при загрузке (÷20).
 * <p>
 * Возможности:
 * <ul>
 *   <li>Экспоненциальный разгон/замедление на energy-рельсах (блоки/тик)</li>
 *   <li>Коллизия: при ударе об entity наносит урон = скорость × 20 (блоки/сек ↔ урон)</li>
 *   <li>Отображение скорости в actionbar (блоки/тик) через /mp togglespeed</li>
 *   <li>setMaxSpeed обновляется последним в тике (двойной шедулинг)</li>
 *   <li>Очистка мёртвых вагонеток через VehicleDestroyEvent (без сканирования)</li>
 *   <li>Очистка speedDisplayPlayers при выходе игрока</li>
 * </ul>
 */
public class MinecartSpeedManager implements Listener {

    private static boolean enabled;
    /** Включена ли переплавка в HopperMinecart при высокой скорости. */
    private static boolean hopperSmeltEnabled;
    /** Минимальная скорость для переплавки (блоки/тик). Конфиг: блоки/сек, конвертируется ÷20. */
    private static double hopperSmeltMinSpeed;
    /** Rate-limit counter for hopper smelting (ticks since last smelt, max 20 = 1 sec). */
    private static final Map<UUID, Integer> smeltTickCounter = new ConcurrentHashMap<>();
    /** Next inventory slot to check for smeltable items (round-robin). */
    private static final Map<UUID, Integer> nextSmeltSlot = new ConcurrentHashMap<>();
    /** Кэш всех CookingRecipe (FURNACE, BLASTING, SMOKER, CAMPFIRE). */
    private static final List<CookingRecipe<?>> cookingRecipes = new ArrayList<>();
    /** Базовая макс. скорость (блоки/тик). Конфиг: блоки/сек, конвертируется ÷20. */
    private static double baseMaxSpeed;
    /** Абсолютный потолок (блоки/тик). Конфиг: блоки/сек, конвертируется ÷20. */
    private static double maxSpeedLimit;
    /** Множитель ускорения за тик (безразмерный). */
    private static double accelerationFactor;
    /** Множитель замедления за тик (безразмерный). */
    private static double decelerationFactor;
    /** Мин. скорость для коллизии (блоки/тик). Конфиг: блоки/сек, конвертируется ÷20. */
    private static double collisionMinSpeed;
    private static int intervalTicks;

    private static Main plugin;
    private static BukkitRunnable speedTask;
    private static BukkitRunnable displayTask;
    private static BukkitRunnable particleTask;
    /** Текущая скорость каждой вагонетки в блоках/тик. */
    private static final Map<UUID, Double> cartSpeeds = new ConcurrentHashMap<>();
    private static final Set<UUID> speedDisplayPlayers = ConcurrentHashMap.newKeySet();
    /** Предыдущие позиции для вычисления скорости через дельту позиции (не getVelocity!). */
    private static final Map<UUID, Location> prevPositions = new ConcurrentHashMap<>();
    /** Отслеживание смены отслеживаемой сущности (маунт/дисмаунт). */
    private static final Map<UUID, UUID> prevTrackedEntities = new ConcurrentHashMap<>();

    private MinecartSpeedManager() {}

    public static void init(Main plugin) {
        MinecartSpeedManager.plugin = plugin;

        // 🛡 Гасим существующие задачи перед созданием новых.
        // Это защита от дублирования при повторном init (например, при
        // /mp modules disable Core → enable Core, или если CoreModule
        // упал на другой фиче и был перевключён).
        cancelTasks();

        reloadConfig();

        if (!enabled) {
            plugin.getLogger().info("[MinecartSpeed] Disabled in config.");
            return;
        }

        // Register listeners (collision, invisible on create, cleanup)
        Bukkit.getPluginManager().registerEvents(new MinecartSpeedManager(), plugin);

        // Cache cooking recipes for hopper smelting
        cacheCookingRecipes();

        // Make all existing minecarts visible on init
        for (World world : Bukkit.getWorlds()) {
            for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                cart.setInvisible(false);
            }
        }

        // Double-scheduling for lowest priority:
        // 1) Outer task fires at start of tick via runTaskTimer
        // 2) Inner task fires at END of tick via runTask — after ALL other plugins
        speedTask = new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (World world : Bukkit.getWorlds()) {
                        for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                            if (!cart.isValid()) {
                                cartSpeeds.remove(cart.getUniqueId());
                                continue;
                            }
                            updateCart(cart);
                        }
                    }
                    // Dead carts are cleaned up via onVehicleDestroy event —
                    // no need for expensive UUID scanning here.
                });
            }
        };
        speedTask.runTaskTimer(plugin, 0L, intervalTicks);

        // Particle task — spawns END_ROD at every minecart (Y+0.5), always every 1 tick
        // Also handles hopper minecart smelting at high speed
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                        if (!cart.isValid()) continue;
                        // END_ROD particle at Y + 0.5
                        Location loc = cart.getLocation();
                        cart.getWorld().spawnParticle(Particle.END_ROD,
                                loc.getX(), loc.getY() + 0.5, loc.getZ(),
                                1, 0, 0, 0, 0);

                        // Hopper minecart smelting at high speed (1 item/sec rate-limited)
                        if (hopperSmeltEnabled && cart instanceof HopperMinecart hopper) {
                            double speed = cartSpeeds.getOrDefault(cart.getUniqueId(), baseMaxSpeed);
                            if (speed >= hopperSmeltMinSpeed) {
                                // Continuous smoke particles while items are being smelted
                                if (hasSmeltableItems(hopper)) {
                                    world.spawnParticle(Particle.SMOKE,
                                            loc.getX(), loc.getY() + 0.8, loc.getZ(),
                                            2, 0.15, 0.05, 0.15, 0.02);
                                }
                                trySmeltOnePerSecond(hopper);
                            }
                        }
                    }
                }
            }
        };
        particleTask.runTaskTimer(plugin, 0L, 1L);

        // Speed display task — shows absolute movement speed (блоки/тик) in actionbar.
        // Uses position delta (not getVelocity!) so walking/sprinting/flying/minecart all register.
        // Detects mount/dismount transitions to avoid false speed spikes.
        // Runs every 1 tick (0.05 sec) for per-tick precision.
        displayTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!speedDisplayPlayers.contains(uuid)) {
                        prevPositions.remove(uuid);
                        prevTrackedEntities.remove(uuid);
                        continue;
                    }

                    // Track the entity that's actually moving (vehicle or player)
                    Entity tracked = (player.getVehicle() != null) ? player.getVehicle() : player;
                    UUID trackedId = tracked.getUniqueId();
                    UUID prevTrackedId = prevTrackedEntities.get(uuid);

                    // Entity changed (mount/dismount/switch vehicle) — set baseline immediately
                    // so the next measurement already has a valid previous position.
                    if (prevTrackedId == null || !prevTrackedId.equals(trackedId)) {
                        prevTrackedEntities.put(uuid, trackedId);
                        prevPositions.put(uuid, tracked.getLocation().clone());
                        player.sendActionBar("\u00a76\u26a1 \u00a7e0.000 \u00a77\u0431\u043b\u043e\u043a/\u0442\u0438\u043a");
                        continue;
                    }

                    Location currentLoc = tracked.getLocation();
                    Location prevLoc = prevPositions.get(uuid);
                    prevPositions.put(uuid, currentLoc.clone());

                    if (prevLoc == null || !currentLoc.getWorld().equals(prevLoc.getWorld())) {
                        // First measurement or world change — show 0
                        player.sendActionBar("\u00a76\u26a1 \u00a7e0.000 \u00a77\u0431\u043b\u043e\u043a/\u0442\u0438\u043a");
                        continue;
                    }

                    // Distance traveled in blocks over 1 tick → blocks/tick
                    double blocksPerTick = currentLoc.distance(prevLoc);

                    player.sendActionBar(
                            "\u00a76\u26a1 \u00a7e" + String.format("%.3f", blocksPerTick) + " \u00a77\u0431\u043b\u043e\u043a/\u0442\u0438\u043a");
                }
            }
        };
        displayTask.runTaskTimer(plugin, 0L, 1L); // every 1 tick

        plugin.getLogger().info("[MinecartSpeed] Hopper smelt: " + (hopperSmeltEnabled
                ? "ON (min=" + String.format("%.1f", hopperSmeltMinSpeed * 20) + " blk/sec)"
                : "OFF"));

        plugin.getLogger().info("[MinecartSpeed] Initialized."
                + " base=" + String.format("%.3f", baseMaxSpeed) + " blk/tick"
                + " (" + String.format("%.1f", baseMaxSpeed * 20) + " blk/sec)"
                + " limit=" + String.format("%.0f", maxSpeedLimit * 20) + " blk/sec"
                + " accel=" + accelerationFactor
                + " decel=" + decelerationFactor
                + " interval=" + intervalTicks + "t"
                + " collision_min=" + String.format("%.3f", collisionMinSpeed) + " blk/tick");
    }

    // =========================
    // MINECART VISIBLE ON CREATE
    // =========================
    @EventHandler
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (!enabled) return;
        if (event.getVehicle() instanceof Minecart cart) {
            cart.setInvisible(false);
        }
    }

    // =========================
    // HOPPER MINECART SMELTING
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
        plugin.getLogger().info("[MinecartSpeed] Cached " + cookingRecipes.size() + " cooking recipes.");
    }

    private static CookingRecipe<?> findCookingRecipe(ItemStack input) {
        if (input == null || input.getType() == Material.AIR) return null;
        for (CookingRecipe<?> cr : cookingRecipes) {
            if (cr.getInputChoice().test(input)) return cr;
        }
        return null;
    }

    /**
     * Smelts ONE item per second in a HopperMinecart (rate-limited).
     * <p>
     * Uses round-robin slot scanning to evenly distribute smelting across all slots.
     * Cooks 1 item at a time: decrements input stack by 1, adds 1 to output stack.
     * Spawns smoke particles and plays beacon power select sound (pitch 0) on each smelt.
     */
    private static void trySmeltOnePerSecond(HopperMinecart hopper) {
        UUID uuid = hopper.getUniqueId();
        int counter = smeltTickCounter.getOrDefault(uuid, 0);
        counter++;

        if (counter < 20) {
            smeltTickCounter.put(uuid, counter);
            return; // Not yet 1 second (20 ticks)
        }

        // Reset counter — 1 second elapsed
        smeltTickCounter.put(uuid, 0);

        // Find next smeltable item
        Inventory inv = hopper.getInventory();
        int size = inv.getSize();
        int startSlot = nextSmeltSlot.getOrDefault(uuid, 0);

        for (int i = 0; i < size; i++) {
            int slot = (startSlot + i) % size;
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;

            CookingRecipe<?> recipe = findCookingRecipe(stack);
            if (recipe == null) continue;

            // Cook 1 item: decrease input by 1, add 1 to output
            ItemStack result = recipe.getResult().clone();
            result.setAmount(1);

            // Decrease input stack by 1
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                inv.setItem(slot, null);
            }

            // Add 1 output to an existing matching stack, or place in empty slot
            boolean added = false;
            for (int j = 0; j < size; j++) {
                ItemStack existing = inv.getItem(j);
                if (existing != null && existing.isSimilar(result)
                        && existing.getAmount() < existing.getMaxStackSize()) {
                    existing.setAmount(existing.getAmount() + 1);
                    added = true;
                    break;
                }
            }
            if (!added) {
                for (int j = 0; j < size; j++) {
                    ItemStack existing = inv.getItem(j);
                    if (existing == null || existing.getType() == Material.AIR) {
                        inv.setItem(j, result);
                        added = true;
                        break;
                    }
                }
            }

            // Если в инвентаре нет места — дропаем результат наружу
            if (!added) {
                Location loc = hopper.getLocation();
                hopper.getWorld().dropItemNaturally(loc, result);
            }

            // Keep checking same slot (it may still have more items)
            nextSmeltSlot.put(uuid, slot);

            // Smoke particles burst on smelt
            Location loc = hopper.getLocation();
            World world = hopper.getWorld();
            world.spawnParticle(Particle.SMOKE,
                    loc.getX(), loc.getY() + 0.8, loc.getZ(),
                    4, 0.2, 0.08, 0.2, 0.03);

            // Beacon power select hum — pitch 0
            world.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 0.0f);

            return; // Only smelt ONE item per second
        }

        // No smeltable items found — reset slot counter
        nextSmeltSlot.put(uuid, 0);
    }

    /**
     * Checks if the hopper minecart has any smeltable items.
     */
    private static boolean hasSmeltableItems(HopperMinecart hopper) {
        for (ItemStack item : hopper.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && findCookingRecipe(item) != null) {
                return true;
            }
        }
        return false;
    }

    // =========================
    // SPEED UPDATE — экспоненциальный разгон (все значения в блоках/тик)
    // =========================
    private static void updateCart(Minecart cart) {
        UUID uuid = cart.getUniqueId();
        double currentSpeed = cartSpeeds.getOrDefault(uuid, baseMaxSpeed);

        boolean onPoweredRail = isOnPoweredRail(cart);

        if (onPoweredRail) {
            // Pure exponential acceleration — no base speed, no floor.
            // All values in blocks/tick.
            currentSpeed = Math.min(currentSpeed * accelerationFactor, maxSpeedLimit);
        } else {
            // Exponential decay toward zero — no base speed floor.
            // 0.05 blocks/tick minimum prevents carts from getting permanently stuck
            // (vanilla carts can still move slowly on flat rails).
            currentSpeed = Math.max(currentSpeed * decelerationFactor, 0.05);
        }

        cartSpeeds.put(uuid, currentSpeed);

        // Raise the speed cap so vanilla powered rails don't artificially limit us.
        // Minecart.setMaxSpeed() expects blocks/sec internally — convert.
        try {
            cart.setMaxSpeed(currentSpeed * 20.0);
        } catch (Exception e) {
            plugin.getLogger().warning("[MinecartSpeed] setMaxSpeed failed: " + e.getMessage());
        }

        // Direct velocity boost — setMaxSpeed only raises the cap, it doesn't provide thrust.
        // Powered rails apply a fixed acceleration that balances with friction at ~0.072 blk/tick.
        // We inject the desired speed directly so the cart actually reaches the target.
        // currentSpeed is already in blocks/tick — pass directly to setVelocity.
        if (onPoweredRail) {
            Vector vel = cart.getVelocity();
            if (vel.lengthSquared() < 0.0001) {
                // Stationary — use cart's rail-facing direction to push off
                vel = cart.getFacing().getDirection();
            }
            cart.setVelocity(vel.normalize().multiply(currentSpeed));
        }
    }

        private static boolean isOnPoweredRail(Minecart cart) {
        Block block = cart.getLocation().getBlock();
        Block below = block.getRelative(BlockFace.DOWN);

        // Проверяем, что блок — POWERED_RAIL И он активирован редстоун-сигналом
        if (block.getType() == Material.POWERED_RAIL) {
            return block.isBlockPowered();
        }
        if (below.getType() == Material.POWERED_RAIL) {
            return below.isBlockPowered();
        }
        return false;
    }

    // =========================
    // COLLISION DAMAGE
    // =========================
    @EventHandler
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        if (!enabled) return;
        if (event.isCancelled()) return;
        if (!(event.getVehicle() instanceof Minecart cart)) return;

        UUID cartId = cart.getUniqueId();
        double speed = cartSpeeds.getOrDefault(cartId, baseMaxSpeed); // блоки/тик

        // Only deal damage + knockback when going fast enough
        if (speed < collisionMinSpeed) return;

        // Convert blocks/tick → blocks/sec for damage (1 блок/тик = 20 урона)
        double damage = speed * 20.0;

        Entity target = event.getEntity();

        // Don't damage entities riding in the minecart (players, villagers, etc.)
        if (cart.getPassengers().contains(target)) return;

        // Deal damage
        if (target instanceof LivingEntity living) {
            living.damage(damage, cart);
        }

        // Push entity off the track (up and sideways from cart's direction)
        Vector cartDir = cart.getVelocity().clone();
        if (cartDir.lengthSquared() < 0.001) {
            cartDir = cart.getLocation().getDirection().setY(0);
        }
        // Normalize horizontal direction, push sideways + up
        cartDir.setY(0).normalize();
        Vector push = new Vector(cartDir.getZ(), 0.8, -cartDir.getX())
                .normalize()
                .multiply(0.6)
                .setY(0.4);
        target.setVelocity(push);

        // Do NOT cancel event — cart continues without speed loss
        // (speed is preserved in cartSpeeds)
    }

    // =========================
    // CLEANUP: Remove player from speed display on quit (prevents memory leak)
    // =========================
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        speedDisplayPlayers.remove(uuid);
        prevPositions.remove(uuid);
        prevTrackedEntities.remove(uuid);
    }

    // =========================
    // CLEANUP: Remove dead carts from tracking (replaces expensive UUID scan)
    // =========================
    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof Minecart cart) {
            UUID uuid = cart.getUniqueId();
            cartSpeeds.remove(uuid);
            smeltTickCounter.remove(uuid);
            nextSmeltSlot.remove(uuid);
        }
    }

    // =========================
    // BLOCK HOPPER MINECART INVENTORY AT SPEED > 1 blk/tick
    // =========================
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!enabled) return;
        if (event.isCancelled()) return;

        if (!(event.getInventory().getHolder() instanceof HopperMinecart hopper)) return;

        double speed = cartSpeeds.getOrDefault(hopper.getUniqueId(), baseMaxSpeed);
        if (speed > 1.0) {
            event.setCancelled(true);

            // Deal 1 fire damage to the player who tried to open
            if (event.getPlayer() instanceof Player player) {
                player.damage(1.0);
                player.setFireTicks(20); // brief fire visual
            }
        }
    }

    // =========================
    // SPEED DISPLAY TOGGLE
    // =========================
    public static boolean isSpeedDisplayEnabled(UUID uuid) {
        return speedDisplayPlayers.contains(uuid);
    }

    public static void toggleSpeedDisplay(UUID uuid) {
        if (speedDisplayPlayers.contains(uuid)) {
            speedDisplayPlayers.remove(uuid);
        } else {
            speedDisplayPlayers.add(uuid);
        }
    }

    // =========================
    // CONFIG — значения в конфиге хранятся в блоках/сек, конвертируются ÷20
    // =========================
    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.minecart_speed");
        if (cfg != null) {
            enabled = cfg.getBoolean("enabled", true);
            // Config stores blocks/sec — convert to blocks/tick (÷20)
            baseMaxSpeed = cfg.getDouble("base_max_speed", 8.0) / 20.0;
            maxSpeedLimit = cfg.getDouble("max_speed_limit", 999999999.0) / 20.0;
            accelerationFactor = cfg.getDouble("acceleration_factor", 1.002);
            decelerationFactor = cfg.getDouble("deceleration_factor", 0.995);
            collisionMinSpeed = cfg.getDouble("collision_min_speed", 15.0) / 20.0;
            intervalTicks = cfg.getInt("interval_ticks", 1);
            // Hopper smelt config
            hopperSmeltEnabled = cfg.getBoolean("hopper_smelt.enabled", true);
            hopperSmeltMinSpeed = cfg.getDouble("hopper_smelt.min_speed", 100.0) / 20.0;
        } else {
            enabled = true;
            baseMaxSpeed = 8.0 / 20.0;          // 0.4 блок/тик
            maxSpeedLimit = 999999999.0 / 20.0;  // ~50M блок/тик
            accelerationFactor = 1.002;
            decelerationFactor = 0.995;
            collisionMinSpeed = 15.0 / 20.0;      // 0.75 блок/тик
            intervalTicks = 1;
            hopperSmeltEnabled = true;
            hopperSmeltMinSpeed = 100.0 / 20.0;   // 5.0 блок/тик
        }
        if (intervalTicks < 1) intervalTicks = 1;
        if (accelerationFactor < 1.0) accelerationFactor = 1.0;
        if (decelerationFactor > 1.0) decelerationFactor = 1.0;
        if (decelerationFactor < 0.0) decelerationFactor = 0.0;
        if (baseMaxSpeed < 0.05) baseMaxSpeed = 0.05;  // min 1 блок/сек
        if (collisionMinSpeed < 0.0) collisionMinSpeed = 0.0;
        if (hopperSmeltMinSpeed < 0.0) hopperSmeltMinSpeed = 0.0;

        // ⚠ coal_heat section полностью удалён: механика угля→алмаз убрана.
        // Удалите раздел coal_heat из config.yml вручную.
    }

    // =========================
    // SHUTDOWN
    // =========================
    /**
     * Отменяет и обнуляет все три задачи (speedTask, particleTask, displayTask).
     * Используется как в shutdown(), так и в init() для предотвращения дублирования.
     */
    private static void cancelTasks() {
        if (speedTask != null) {
            speedTask.cancel();
            speedTask = null;
        }
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (displayTask != null) {
            displayTask.cancel();
            displayTask = null;
        }
    }

    public static void shutdown() {
        cancelTasks();
        cartSpeeds.clear();
        smeltTickCounter.clear();
        nextSmeltSlot.clear();
        speedDisplayPlayers.clear();
        prevPositions.clear();
        prevTrackedEntities.clear();
        cookingRecipes.clear();
        VehicleEntityCollisionEvent.getHandlerList().unregister(plugin);
        PlayerQuitEvent.getHandlerList().unregister(plugin);
        VehicleDestroyEvent.getHandlerList().unregister(plugin);
        VehicleCreateEvent.getHandlerList().unregister(plugin);
        InventoryOpenEvent.getHandlerList().unregister(plugin);
    }

    // =========================
    // PUBLIC ACCESSORS (for /mp togglespeed command)
    // =========================
    /** @return карта скоростей (блоки/тик). */
    public static Map<UUID, Double> getCartSpeeds() {
        return cartSpeeds;
    }

    /** @return базовая макс. скорость (блоки/тик). */
    public static double getBaseMaxSpeed() {
        return baseMaxSpeed;
    }
}
