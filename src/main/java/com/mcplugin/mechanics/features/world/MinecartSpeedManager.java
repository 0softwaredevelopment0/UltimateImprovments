package com.mcplugin.mechanics.features.world;

import com.mcplugin.infrastructure.core.Main;
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
 *   <li>Экспоненциальный разгон на активных POWERED_RAIL (×N за тик)</li>
 *   <li>Экспоненциальное замедление вне рельсов</li>
 *   <li>Коллизия: при ударе об entity наносит урон = скорость × 20 (блоки/сек ↔ урон)</li>
 *   <li>Отображение скорости в actionbar (блоки/тик) через /mp togglespeed</li>
 *   <li>Ускорение применяется напрямую в каждом тике (без двойного шедулинга)</li>
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
    /** Множитель ускорения за тик (безразмерный, ×1.015 = +1.5%/тик). */
    private static double accelerationFactor;
    /** Множитель замедления за тик (безразмерный). */
    private static double decelerationFactor;
    /** Аддитивный буст скорости за тик (блоки/тик) — добавляется к velocity на энергорельсах. */
    private static double thrustPerTick;
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
    /** Предыдущие позиции игрока для вычисления скорости через дельту позиции.
     *  Используем позицию самого игрока (а не транспорта), потому что игрок
     *  движется вместе с вагонеткой — дельта позиции игрока = скорость вагонетки.
     *  Это исключает моргание датчика из-за mount/dismount детекции. */
    private static final Map<UUID, Location> prevPositions = new ConcurrentHashMap<>();

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

        // Прямое обновление скорости в каждом тике — БЕЗ двойного шедулинга.
        // Раньше был inner runTask в конце тика, из-за чего вагонетка
        // "буксовала" — физика успевала обработать тик, а скорость обновлялась
        // только в конце, уже после того как вагонетка съезжала с рельсов.
        speedTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                        if (!cart.isValid()) {
                            cartSpeeds.remove(cart.getUniqueId());
                            continue;
                        }
                        updateCart(cart);
                    }
                }
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

        // Speed display task — показывает скорость в actionbar.
        // Всегда использует дельту позиции ИГРОКА (не транспорта).
        // Без mount/dismount детекции — позиция игрока движется вместе с вагонеткой,
        // поэтому дельта = скорость вагонетки. Это исключает моргание датчика.
        displayTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!speedDisplayPlayers.contains(uuid)) {
                        prevPositions.remove(uuid);
                        continue;
                    }

                    Location currentLoc = player.getLocation();
                    Location prevLoc = prevPositions.get(uuid);
                    prevPositions.put(uuid, currentLoc.clone());

                    if (prevLoc == null || !currentLoc.getWorld().equals(prevLoc.getWorld())) {
                        player.sendActionBar("\u00a76\u26a1 \u00a7e0.000 \u00a77\u0431\u043b\u043e\u043a/\u0442\u0438\u043a");
                        continue;
                    }

                    double blocksPerTick = currentLoc.distance(prevLoc);
                    String msg = "\u00a76\u26a1 \u00a7e" + String.format("%.3f", blocksPerTick) + " \u00a77\u0431\u043b\u043e\u043a/\u0442\u0438\u043a";

                    // Если игрок в вагонетке на скорости переплавки — добавляем индикатор [⚡]
                    if (player.getVehicle() instanceof Minecart && blocksPerTick >= hopperSmeltMinSpeed) {
                        msg += " \u00a78[\u00a7e\u26a1\u00a78]";
                    }

                    player.sendActionBar(msg);
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
                + " collision_min=" + String.format("%.3f", collisionMinSpeed) + " blk/tick"
                + " [Boost: additive " + String.format("%.3f", thrustPerTick) + "/tick]");
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
    //
    // 🛠 ЛОГИКА РАБОТЫ:
    //   1. Если вагонетка на POWERED_RAIL → скорость растёт экспоненциально
    //      (currentSpeed × accelerationFactor).
    //   2. Если НЕ на POWERED_RAIL → скорость НЕ сбрасывается, а сохраняется.
    //      Это критически важно: на стыках рельсов вагонетка на 1 тик может
    //      не определиться как "на рельсах", и раньше decelerationFactor
    //      (0.995) съедал весь прирост (1.002 × 0.995 = 0.997 < 1 — скорость
    //      ПАДАЛА, а не росла).
    //   3. setMaxSpeed() поднимает лимит вагонетки.
    //   4. Вместо полной перезаписи velocity (setVelocity) — аддитивный буст:
    //      добавляем thrust к текущей скорости по направлению рельсов.
    //      Это не ломает физику рельсов (повороты, подъёмы) и не сбивает
    //      вагонетку с путей на высокой скорости.
    // =========================
    private static void updateCart(Minecart cart) {
        UUID uuid = cart.getUniqueId();
        double currentSpeed = cartSpeeds.getOrDefault(uuid, baseMaxSpeed);

        boolean onPoweredRail = isOnPoweredRail(cart);

        if (onPoweredRail) {
            // Экспоненциальный разгон: ×N за тик на активных рельсах.
            currentSpeed = Math.min(currentSpeed * accelerationFactor, maxSpeedLimit);
        }
        // else: скорость НЕ уменьшается вне рельсов.
        // На высокой скорости isOnPoweredRail() может ложно возвращать false,
        // т.к. вагонетка пролетает несколько блоков за тик — проверка одного
        // блока в getLocation().getBlock() не успевает поймать энергорельс.
        // Раньше decelerationFactor (0.997) съедал скорость при каждом
        // таком ложном срабатывании, и вагонетка не могла разогнаться выше
        // определённого порога — цикл "разгон → ложный off-rail → замедление".

        cartSpeeds.put(uuid, currentSpeed);

        // Raise the speed cap so vanilla powered rails don't artificially limit us.
        try {
            cart.setMaxSpeed(currentSpeed * 20.0);
        } catch (Exception e) {
            plugin.getLogger().warning("[MinecartSpeed] setMaxSpeed failed: " + e.getMessage());
        }

        // Применяем буст скорости:
        // - на рельсах: разгон до целевой скорости
        // - вне рельсов (если скорость > базовой): поддержание скорости,
        //   компенсация ложных false от isOnPoweredRail на высокой скорости.
        //   Если вагонетка реально сошла с рельсов — ванильное трение
        //   замедлит её сильнее, чем буст +0.04/тик может компенсировать.
        if (onPoweredRail || currentSpeed > baseMaxSpeed) {
            applyVelocityBoost(cart, currentSpeed);
        }
    }

    // =========================
    // APPLY VELOCITY BOOST — аддитивный буст вместо перезаписи velocity
    // =========================
    /**
     * Добавляет ускорение к текущей скорости вагонетки, а не перезаписывает её.
     * <p>
     * Раньше код делал cart.setVelocity(dir.multiply(currentSpeed)) — полная
     * перезапись velocity каждый тик. Это ломало физику рельсов: вагонетка
     * не чувствовала повороты, подъёмы и слетала с путей на высокой скорости.
     * <p>
     * Теперь мы только добавляем thrust в направлении движения, а всё
     * остальное (гравитация, рельсы, повороты) обрабатывает Minecraft Physics.
     */
    private static void applyVelocityBoost(Minecart cart, double targetSpeed) {
        // Определяем направление движения по рельсам
        Vector dir = getRailMovementDirection(cart);

        Vector currentVel = cart.getVelocity();
        // Проецируем текущую скорость на направление рельсов
        double dot = currentVel.dot(dir);

        // Добавляем буст только если текущая скорость ВДОЛЬ РЕЛЬСОВ меньше целевой
        if (dot < targetSpeed) {
            // thrust = насколько не хватает, но не более thrustPerTick за тик (чтобы не ломать физику)
            double thrust = Math.min(targetSpeed - dot, thrustPerTick);
            // Добавляем к velocity, а НЕ перезаписываем
            currentVel.add(dir.multiply(thrust));
            cart.setVelocity(currentVel);
        }
    }

    // =========================
    // GET RAIL MOVEMENT DIRECTION — определяет направление движения по рельсам
    // =========================
    /**
     * Определяет направление рельсов под вагонеткой.
     * <p>
     * Пытается получить направление из:
     * 1. Блока POWERED_RAIL (блок под вагонеткой)
     * 2. Направления движения вагонетки (fallback)
     * 3. Направления facing вагонетки (последний fallback)
     * <p>
     * Всегда возвращает горизонтальное направление (Y = 0).
     */
    private static Vector getRailMovementDirection(Minecart cart) {
        // Направление из текущей скорости вагонетки (предпочтительно — отражает реальное движение)
        Vector vel = cart.getVelocity().clone();
        vel.setY(0);
        if (vel.lengthSquared() > 0.0001) {
            return vel.normalize();
        }

        // Fallback: facing вагонетки
        Vector facing = cart.getFacing().getDirection();
        facing.setY(0);
        if (facing.lengthSquared() > 0.01) {
            return facing.normalize();
        }

        // Последний fallback: север (negative Z)
        return new Vector(0, 0, -1);
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
            accelerationFactor = cfg.getDouble("acceleration_factor", 1.015);
            decelerationFactor = cfg.getDouble("deceleration_factor", 0.997);
            thrustPerTick = cfg.getDouble("thrust_per_tick", 0.04);
            collisionMinSpeed = cfg.getDouble("collision_min_speed", 15.0) / 20.0;
            intervalTicks = cfg.getInt("interval_ticks", 1);
            // Hopper smelt config
            hopperSmeltEnabled = cfg.getBoolean("hopper_smelt.enabled", true);
            hopperSmeltMinSpeed = cfg.getDouble("hopper_smelt.min_speed", 100.0) / 20.0;
        } else {
            enabled = true;
            baseMaxSpeed = 8.0 / 20.0;          // 0.4 блок/тик
            maxSpeedLimit = 999999999.0 / 20.0;  // ~50M блок/тик
            accelerationFactor = 1.015;
            decelerationFactor = 0.997;
            thrustPerTick = 0.04;
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
        if (thrustPerTick < 0.0) thrustPerTick = 0.0;
        if (hopperSmeltMinSpeed < 0.0) hopperSmeltMinSpeed = 0.0;            // ⚠ coal_heat section полностью удалён: механика угля→алмаз убрана.
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
