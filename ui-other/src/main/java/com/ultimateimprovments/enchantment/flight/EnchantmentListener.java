package com.ultimateimprovments.enchantment.flight;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.features.integrity.ItemIntegrityAPI;
import com.ultimateimprovments.mechanics.security.auth.AuthPlayerState;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener: Flight enchantment — fly like in Creative without being Creative.
 * <p>
 * While a player WEARS a chestplate carrying the Flight charm, they get
 * {@code setAllowFlight(true)}: double-tap space and fly, exactly like Creative
 * mode (but the player stays in their real game mode).
 * <p>
 * When the chestplate is taken off / breaks / is removed, flight is revoked —
 * but ONLY if it was granted by this charm (tracked in {@link #GRANTED_FLIGHT}),
 * so we never break Creative/Spectator flight or flight from other plugins.
 * <p>
 * Safety rules:
 * <ul>
 *   <li>players pending auth (frozen by the auth system) are never granted flight;</li>
 *   <li>Creative/Spectator players are never touched (their flight is vanilla);</li>
 *   <li>revoking only touches players this plugin gave flight to.</li>
 * </ul>
 * A periodic sweep ({@value #SWEEP_INTERVAL_TICKS} ticks) is the failsafe for
 * every edge case (death, respawn, gamemode change, item break).
 */
public class EnchantmentListener implements Listener {

    /** Periodic sweep interval: 10 ticks (0.5s) — fast enough to feel instant. */
    static final long SWEEP_INTERVAL_TICKS = 10L;

    /** Integrity drain interval: 20 ticks = 1 second of active flight. */
    static final long INTEGRITY_DRAIN_INTERVAL_TICKS = 20L;

    /** Raw slot of the chestplate in the player's own inventory (36-39 = armor). */
    private static final int CHESTPLATE_RAW_SLOT = 38;

    /** Players this plugin granted flight to via the Flight charm. */
    private static final Set<UUID> GRANTED_FLIGHT = ConcurrentHashMap.newKeySet();

    /**
     * Players currently considered flying: {@code isFlying()} is true now, or
     * they WERE flying and are still airborne. Covers the hover desync: a
     * player hanging motionless in the air can still be in the flight state
     * while {@code isFlying()} reports false — the chestplate must keep
     * draining until they actually land (or lose flight/the charm).
     */
    private static final Set<UUID> FLIGHT_ACTIVE = ConcurrentHashMap.newKeySet();

    // ─────────────────────────────────────────────────────────────
    //  EVENTS
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updatePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        GRANTED_FLIGHT.remove(event.getPlayer().getUniqueId());
        FLIGHT_ACTIVE.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // Re-check after respawn (covers plugins that clear armor on death,
        // and the case where the player respawned without the chestplate).
        updatePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        // Chestplate broke → revoke flight. The item is already gone from the slot
        // by the time this fires on Paper, so a direct check is safe.
        updatePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        // Runs BEFORE the mode actually changes; re-check on the next tick.
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (event.getPlayer().isOnline()) {
                updatePlayer(event.getPlayer());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Only re-check when the click involves the chestplate: raw slot 38 in the
        // player inventory, or a shift-click (which moves items between armor and
        // storage without the exact slot being the raw armor slot).
        boolean touchesArmor = event.getRawSlot() == CHESTPLATE_RAW_SLOT
                || event.isShiftClick()
                || event.getSlotType() == InventoryType.SlotType.ARMOR;
        if (!touchesArmor) return;

        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (player.isOnline()) updatePlayer(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        for (int slot : event.getRawSlots()) {
            if (slot == 38) { // chestplate slot
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (player.isOnline()) updatePlayer(player);
                });
                return;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  FLIGHT LOGIC
    // ─────────────────────────────────────────────────────────────

    /**
     * Grants/revokes creative-style flight based on the equipped chestplate.
     */
    static void updatePlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        // Never grant flight to a frozen (pending auth) player.
        AuthPlayerState auth = AuthPlayerState.getInstance();
        if (auth != null && auth.isPendingAuth(uuid)) return;

        GameMode gm = player.getGameMode();
        boolean creativeLike = gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;

        ItemStack chest = player.getInventory().getChestplate();
        boolean hasCharm = chest != null
                && com.ultimateimprovments.enchantment.flight.Enchantment.getLevel(chest) > 0;

        if (hasCharm) {
            // Equipped → grant flight, but ONLY take credit for flight we actually
            // turned on. If the player already could fly (Creative, another plugin
            // like a donator perk), we must NOT mark them — otherwise on unequip
            // we'd revoke flight we never granted.
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                GRANTED_FLIGHT.add(uuid);
            }
            return;
        }

        // Not equipped → revoke ONLY what we granted.
        if (GRANTED_FLIGHT.remove(uuid)) {
            if (!creativeLike) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  FALL DAMAGE — vanilla skips fall damage while allowFlight is on,
    //  so a player with the Flight chestplate could dive-bomb from build
    //  limit for free. We track the accumulated fall distance ourselves and
    //  apply the vanilla fall-damage formula on landing: 1 HP per block
    //  beyond the 3-block safe distance, halved by Feather Falling IV,
    //  never applied in water/creative-like/slow-falling.
    // ─────────────────────────────────────────────────────────────

    /** Accumulated fall distance (in blocks) per player since they left the ground. */
    private static final java.util.Map<UUID, Float> FALL_DISTANCE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Fall distance (blocks) that vanilla considers safe. */
    private static final float SAFE_FALL_DISTANCE = 3.0f;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        // Vanilla never fires FALL for allowFlight players — this handler only
        // protects against double-dipping if a future version changes that.
        if (event.getEntityType() != EntityType.PLAYER) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Player player = (Player) event.getEntity();
        FALL_DISTANCE.remove(player.getUniqueId());
    }

    /** Tracks the fall and applies the accumulated damage on landing (called from the drain tick). */
    private static void tickFallDamage() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                UUID uuid = player.getUniqueId();

                // Skip players whose flight is not from this charm: creative-like
                // modes and other plugins' flight keep full vanilla behaviour.
                if (!GRANTED_FLIGHT.contains(uuid)
                        || player.getGameMode() == GameMode.CREATIVE
                        || player.getGameMode() == GameMode.SPECTATOR) {
                    FALL_DISTANCE.remove(uuid);
                    continue;
                }

                // Reset the counter in water, on ladders, while gliding and with
                // slow falling — the same cases vanilla ignores fall distance.
                if (player.isInWater()
                        || player.isClimbing()
                        || player.isGliding()
                        || player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING)) {
                    FALL_DISTANCE.remove(uuid);
                    continue;
                }

                if (player.isOnGround() || player.isInWater()) {
                    // Map#remove returns null when nothing was accumulated —
                    // unbox safely (standing on the ground is the common case).
                    Float accumulated = FALL_DISTANCE.remove(uuid);
                    float distance = accumulated != null ? accumulated : 0.0f;
                    if (distance > SAFE_FALL_DISTANCE) {
                        double damage = Math.floor(distance - SAFE_FALL_DISTANCE);
                        if (damage > 0) {
                            DamageSource source = DamageSource.builder(DamageType.FALL)
                                    .withCausingEntity(player)
                                    .withDirectEntity(player)
                                    .build();
                            player.damage(damage, source);
                        }
                    }
                    continue;
                }

                // Airborne: accumulate. While actively flying upward/hovering the
                // distance does not grow (only real downward motion counts), so
                // the counter tracks net downward travel.
                double yVelocity = player.getVelocity().getY();
                if (yVelocity < -0.05) {
                    float current = FALL_DISTANCE.getOrDefault(uuid, 0.0f);
                    FALL_DISTANCE.put(uuid, current + (float) Math.abs(yVelocity));
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[Flight] Fall-damage tick error for " + player.getName()
                        + ": " + e.getMessage());
            }
        }
    }

    /** Periodic failsafe sweep of every online player. */
    private static void sweepAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                updatePlayer(player);
            } catch (Exception e) {
                ConsoleLogger.warn("[Flight] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * While a player is actively flying with the Flight chestplate, the
     * chestplate loses 1 use of integrity per second — flight is no longer free.
     * "Actively flying" also covers hovering: were flying and still airborne,
     * even when {@code isFlying()} desyncs to false while hanging in the air.
     */
    private static void drainFlightIntegrity() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                UUID uuid = player.getUniqueId();

                // Update the flight-state tracker:
                //  - flying right now → active;
                //  - landed, lost flight, or falling fast (flight toggled off
                //    mid-air → a normal fall, not flight) → not active;
                //  - still airborne after flying with ~zero fall speed
                //    (hover desync) → stays active.
                if (player.isFlying()) {
                    FLIGHT_ACTIVE.add(uuid);
                } else if (player.isOnGround()
                        || !player.getAllowFlight()
                        || player.getVelocity().getY() < -0.5) {
                    FLIGHT_ACTIVE.remove(uuid);
                }

                if (!FLIGHT_ACTIVE.contains(uuid)) continue;

                ItemStack chest = player.getInventory().getChestplate();
                if (chest == null || chest.getType() == Material.AIR) {
                    FLIGHT_ACTIVE.remove(uuid);
                    continue;
                }
                if (com.ultimateimprovments.enchantment.flight.Enchantment.getLevel(chest) <= 0) {
                    FLIGHT_ACTIVE.remove(uuid);
                    continue;
                }

                ItemIntegrityAPI.decreaseItemIntegrity(chest, 1, player);
            } catch (Exception e) {
                ConsoleLogger.warn("[Flight] Integrity drain error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Registers the listener and starts the periodic flight sweep.
     */
    public static void register(Main plugin) {
        Bukkit.getPluginManager().registerEvents(new EnchantmentListener(), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::sweepAllPlayers,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::drainFlightIntegrity,
                INTEGRITY_DRAIN_INTERVAL_TICKS, INTEGRITY_DRAIN_INTERVAL_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::tickFallDamage, 1L, 1L);
        ConsoleLogger.info("[Flight] Listener registered (flight sweep every "
                + (SWEEP_INTERVAL_TICKS / 20.0) + "s, 1 integrity-use/s while flying, fall damage on landing).");
    }
}
