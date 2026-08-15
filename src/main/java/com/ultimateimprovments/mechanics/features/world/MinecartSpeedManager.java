package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
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
 * Manages minecart speed with exponential acceleration on powered rails.
 * <p>
 * <b>All internal math is in blocks/tick.</b>
 * The config stores values in blocks/sec for readability, converted on load (÷20).
 * <p>
 * Features:
 * <ul>
 *   <li>Exponential acceleration on active POWERED_RAIL (×N per tick)</li>
 *   <li>Exponential deceleration off rails</li>
 *   <li>Collision: hitting an entity deals damage = speed × 20 (blocks/sec ↔ damage)</li>
 *   <li>Speed display in actionbar (blocks/tick) via /ui togglespeed</li>
 *   <li>Acceleration applied directly each tick (no double scheduling)</li>
 *   <li>Dead minecart cleanup via VehicleDestroyEvent (no scanning)</li>
 *   <li>speedDisplayPlayers cleanup when a player quits</li>
 * </ul>
 */
public class MinecartSpeedManager implements Listener {

    private static boolean enabled;
    /** Whether smelting in HopperMinecart at high speed is enabled. */
    private static boolean hopperSmeltEnabled;
    /** Minimum speed for smelting (blocks/tick). Config: blocks/sec, converted ÷20. */
    private static double hopperSmeltMinSpeed;
    /** Rate-limit counter for hopper smelting (ticks since last smelt, max 20 = 1 sec). */
    private static final Map<UUID, Integer> smeltTickCounter = new ConcurrentHashMap<>();
    /** Next inventory slot to check for smeltable items (round-robin). */
    private static final Map<UUID, Integer> nextSmeltSlot = new ConcurrentHashMap<>();
    /** Cache of all CookingRecipe (FURNACE, BLASTING, SMOKER, CAMPFIRE). */
    private static final List<CookingRecipe<?>> cookingRecipes = new ArrayList<>();
    /** Base max speed (blocks/tick). Config: blocks/sec, converted ÷20. */
    private static double baseMaxSpeed;
    /** Absolute speed cap (blocks/tick). Config: blocks/sec, converted ÷20. */
    private static double maxSpeedLimit;
    /** Per-tick acceleration multiplier (dimensionless, ×1.015 = +1.5%/tick). */
    private static double accelerationFactor;
    /** Per-tick deceleration multiplier (dimensionless). */
    private static double decelerationFactor;
    /** Additive per-tick speed boost (blocks/tick) — added to velocity on powered rails. */
    private static double thrustPerTick;
    /** Min speed for collision (blocks/tick). Config: blocks/sec, converted ÷20. */
    private static double collisionMinSpeed;
    private static int intervalTicks;

    private static Main plugin;
    private static BukkitRunnable speedTask;
    private static BukkitRunnable displayTask;
    private static BukkitRunnable particleTask;
    /** Current speed of each minecart in blocks/tick. */
    private static final Map<UUID, Double> cartSpeeds = new ConcurrentHashMap<>();
    private static final Set<UUID> speedDisplayPlayers = ConcurrentHashMap.newKeySet();
    /** Previous player positions for computing speed via position delta.
     *  We use the player's own position (not the vehicle), because the player
     *  moves together with the minecart — the player position delta = minecart speed.
     *  This eliminates sensor flicker from mount/dismount detection. */
    private static final Map<UUID, Location> prevPositions = new ConcurrentHashMap<>();

    private MinecartSpeedManager() {}

    public static void init(Main plugin) {
        MinecartSpeedManager.plugin = plugin;

        // 🛡 Cancel existing tasks before creating new ones.
        // This prevents duplication on repeated init (e.g. after
        // /ui modules disable Core → enable Core, or if CoreModule
        // failed on another feature and was toggled).
        cancelTasks();

        reloadConfig();

        if (!enabled) {
            ConsoleLogger.info("[MinecartSpeed] Disabled in config.");
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

        // Direct speed update every tick — WITHOUT double scheduling.
        // There used to be an inner runTask at the end of the tick, which made
        // the minecart "spin its wheels" — physics processed the tick while
        // speed only updated at the end, after the cart had left the rails.
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

        // Speed display task — shows speed in the actionbar.
        // Always uses the PLAYER's position delta (not the vehicle).
        // No mount/dismount detection — the player position moves with the minecart,
        // so the delta = minecart speed. This eliminates sensor flicker.
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

                    // If the player is in a minecart at smelting speed — add a [⚡] indicator
                    if (player.getVehicle() instanceof Minecart && blocksPerTick >= hopperSmeltMinSpeed) {
                        msg += " \u00a78[\u00a7e\u26a1\u00a78]";
                    }

                    player.sendActionBar(msg);
                }
            }
        };
        displayTask.runTaskTimer(plugin, 0L, 1L); // every 1 tick

        ConsoleLogger.info("[MinecartSpeed] Hopper smelt: " + (hopperSmeltEnabled
                ? "ON (min=" + String.format("%.1f", hopperSmeltMinSpeed * 20) + " blk/sec)"
                : "OFF"));

        ConsoleLogger.info("[MinecartSpeed] Initialized."
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
    @EventHandler(ignoreCancelled = true)
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
        ConsoleLogger.info("[MinecartSpeed] Cached " + cookingRecipes.size() + " cooking recipes.");
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

            // If the inventory has no space — drop the result outside
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
    // SPEED UPDATE — exponential acceleration (all values in blocks/tick)
    //
    // 🛠 HOW IT WORKS:
    //   1. If the cart is on POWERED_RAIL → speed grows exponentially
    //      (currentSpeed × accelerationFactor).
    //   2. If NOT on POWERED_RAIL → speed is NOT reset, it is preserved.
    //      This is critical: at rail joints the cart may not register as "on rails"
    //      for 1 tick, and previously decelerationFactor (0.995) ate the whole
    //      gain (1.002 × 0.995 = 0.997 < 1 — speed DROPPED instead of growing).
    //   3. setMaxSpeed() raises the cart's speed limit.
    //   4. Instead of fully rewriting velocity (setVelocity) — an additive boost:
    //      add thrust to the current speed along the rail direction.
    //      This doesn't break rail physics (turns, climbs) and doesn't knock
    //      the cart off the track at high speed.
    // =========================
    private static void updateCart(Minecart cart) {
        UUID uuid = cart.getUniqueId();
        double currentSpeed = cartSpeeds.getOrDefault(uuid, baseMaxSpeed);

        boolean onPoweredRail = isOnPoweredRail(cart);

        if (onPoweredRail) {
            // Exponential acceleration: ×N per tick on active rails.
            currentSpeed = Math.min(currentSpeed * accelerationFactor, maxSpeedLimit);
        }
        // else: speed is NOT reduced off rails.
        // At high speed isOnPoweredRail() may falsely return false,
        // because the cart flies several blocks per tick — checking a single
        // block in getLocation().getBlock() can't catch the powered rail.
        // Previously decelerationFactor (0.997) ate speed on every such
        // false positive, and the cart couldn't accelerate past a certain
        // threshold — a "accelerate → false off-rail → decelerate" loop.

        cartSpeeds.put(uuid, currentSpeed);

        // Raise the speed cap so vanilla powered rails don't artificially limit us.
        try {
            cart.setMaxSpeed(currentSpeed * 20.0);
        } catch (Exception e) {
            ConsoleLogger.warn("[MinecartSpeed] setMaxSpeed failed: " + e.getMessage());
        }

        // Apply the speed boost:
        // - on rails: accelerate toward the target speed
        // - off rails (if speed > base): maintain speed,
        //   compensating for false negatives from isOnPoweredRail at high speed.
        //   If the cart truly left the rails — vanilla friction will slow it
        //   down more than a +0.04/tick boost can compensate.
        if (onPoweredRail || currentSpeed > baseMaxSpeed) {
            applyVelocityBoost(cart, currentSpeed);
        }
    }

    // =========================
    // APPLY VELOCITY BOOST — additive boost instead of rewriting velocity
    // =========================
    /**
     * Adds acceleration to the cart's current velocity instead of rewriting it.
     * <p>
     * The old code did cart.setVelocity(dir.multiply(currentSpeed)) — a full
     * velocity rewrite every tick. That broke rail physics: the cart didn't
     * feel turns or climbs and flew off the track at high speed.
     * <p>
     * Now we only add thrust in the direction of motion, and Minecraft Physics
     * handles everything else (gravity, rails, turns).
     */
    private static void applyVelocityBoost(Minecart cart, double targetSpeed) {
        // Determine the movement direction along the rails
        Vector dir = getRailMovementDirection(cart);

        Vector currentVel = cart.getVelocity();
        // Project the current velocity onto the rail direction
        double dot = currentVel.dot(dir);

        // Only add the boost if the current speed ALONG THE RAILS is below target
        if (dot < targetSpeed) {
            // thrust = how much is missing, but no more than thrustPerTick per tick (to not break physics)
            double thrust = Math.min(targetSpeed - dot, thrustPerTick);
            // Add to velocity, NOT rewrite it
            currentVel.add(dir.multiply(thrust));
            cart.setVelocity(currentVel);
        }
    }

    // =========================
    // GET RAIL MOVEMENT DIRECTION — determines the movement direction along rails
    // =========================
    /**
     * Determines the rail direction under the cart.
     * <p>
     * Tries to get the direction from:
     * 1. The POWERED_RAIL block (the block under the cart)
     * 2. The cart's movement direction (fallback)
     * 3. The cart's facing direction (last fallback)
     * <p>
     * Always returns a horizontal direction (Y = 0).
     */
    private static Vector getRailMovementDirection(Minecart cart) {
        // Direction from the cart's current velocity (preferred — reflects real motion)
        Vector vel = cart.getVelocity().clone();
        vel.setY(0);
        if (vel.lengthSquared() > 0.0001) {
            return vel.normalize();
        }

        // Fallback: the cart's facing
        Vector facing = cart.getFacing().getDirection();
        facing.setY(0);
        if (facing.lengthSquared() > 0.01) {
            return facing.normalize();
        }

        // Last fallback: north (negative Z)
        return new Vector(0, 0, -1);
    }

    private static boolean isOnPoweredRail(Minecart cart) {
        Block block = cart.getLocation().getBlock();
        Block below = block.getRelative(BlockFace.DOWN);

        // Check that the block is POWERED_RAIL AND powered by a redstone signal
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
    @EventHandler(ignoreCancelled = true)
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        if (!enabled) return;
        if (!(event.getVehicle() instanceof Minecart cart)) return;

        UUID cartId = cart.getUniqueId();
        double speed = cartSpeeds.getOrDefault(cartId, baseMaxSpeed); // blocks/tick

        // Only deal damage + knockback when going fast enough
        if (speed < collisionMinSpeed) return;

        // Convert blocks/tick → blocks/sec for damage (1 block/tick = 20 damage)
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
    @EventHandler(ignoreCancelled = true)
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
    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!enabled) return;

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
    // CONFIG — values in config are stored in blocks/sec, converted ÷20
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
            baseMaxSpeed = 8.0 / 20.0;          // 0.4 blocks/tick
            maxSpeedLimit = 999999999.0 / 20.0;  // ~50M blocks/tick
            accelerationFactor = 1.015;
            decelerationFactor = 0.997;
            thrustPerTick = 0.04;
            collisionMinSpeed = 15.0 / 20.0;      // 0.75 blocks/tick
            intervalTicks = 1;
            hopperSmeltEnabled = true;
            hopperSmeltMinSpeed = 100.0 / 20.0;   // 5.0 blocks/tick
        }
        if (intervalTicks < 1) intervalTicks = 1;
        if (accelerationFactor < 1.0) accelerationFactor = 1.0;
        if (decelerationFactor > 1.0) decelerationFactor = 1.0;
        if (decelerationFactor < 0.0) decelerationFactor = 0.0;
        if (baseMaxSpeed < 0.05) baseMaxSpeed = 0.05;  // min 1 block/sec
        if (collisionMinSpeed < 0.0) collisionMinSpeed = 0.0;
        if (thrustPerTick < 0.0) thrustPerTick = 0.0;
        if (hopperSmeltMinSpeed < 0.0) hopperSmeltMinSpeed = 0.0;            // ⚠ coal_heat section fully removed: the coal→diamond mechanic is gone.
        // Remove the coal_heat section from config.yml manually.
    }

    // =========================
    // SHUTDOWN
    // =========================
    /**
     * Cancels and nulls all three tasks (speedTask, particleTask, displayTask).
     * Used both in shutdown() and init() to prevent duplication.
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
    // PUBLIC ACCESSORS (for /ui togglespeed command)
    // =========================
    /** @return the speed map (blocks/tick). */
    public static Map<UUID, Double> getCartSpeeds() {
        return cartSpeeds;
    }

    /** @return the base max speed (blocks/tick). */
    public static double getBaseMaxSpeed() {
        return baseMaxSpeed;
    }
}
