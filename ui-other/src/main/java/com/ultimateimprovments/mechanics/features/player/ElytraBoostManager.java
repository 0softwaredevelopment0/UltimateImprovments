package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ElytraBoost — pressing SPACE while gliding on an elytra gives a
 * MAXIMUM speed boost (without consuming fireworks).
 * <p>
 * In Paper 1.21.4 pressing SPACE while gliding does NOT trigger PlayerToggleFlightEvent.
 * Instead the server calls {@code jumpFromElytra()}, which abruptly changes the
 * player's Y-velocity from negative (falling) to positive (rising).
 * <p>
 * We detect this via {@link PlayerMoveEvent}: if the player abruptly stopped falling
 * and started rising — SPACE was pressed.
 * <p>
 * We also handle {@link PlayerToggleFlightEvent} in case future Paper
 * versions start firing it again.
 */
public class ElytraBoostManager implements Listener {

    private static ElytraBoostManager instance;

    /** Previous Y-delta for each player (for burst detection). */
    private static final Map<UUID, Double> lastYDelta = new HashMap<>();

    /** Boost cooldown (ms) — small, so ticks aren't spammed. */
    private static final long BOOST_COOLDOWN_MS = 200;

    /** Last boost time for each player. */
    private static final Map<UUID, Long> lastBoostTime = new HashMap<>();

    /** Players who disabled the automatic boost on jump (/ui togglefly). */
    private static final Set<UUID> flyDisabled = ConcurrentHashMap.newKeySet();

    // =========================
    // TOGGLE FLY
    // =========================
    public static boolean isFlyEnabled(UUID uuid) {
        return !flyDisabled.contains(uuid);
    }

    public static void toggleFlyEnabled(UUID uuid) {
        if (flyDisabled.contains(uuid)) {
            flyDisabled.remove(uuid);
            deleteFlyDisabledFromDb(uuid);
        } else {
            flyDisabled.add(uuid);
            saveFlyDisabledToDb(uuid);
        }
    }

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        instance = new ElytraBoostManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        loadFlyDisabledFromDb();
        ConsoleLogger.info("[ElytraBoost] ✔ Enabled — press SPACE while gliding to boost.");
    }

    public static ElytraBoostManager getInstance() {
        return instance;
    }

    // =========================
    // DETECT SPACE VIA Y-DELTA (works in Paper 1.21.4)
    // =========================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        // Toggle check: if the player disabled the boost — skip
        if (flyDisabled.contains(player.getUniqueId())) return;

        // Must have a chestplate/elytra with the glider component
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null) return;
        ItemMeta meta = chest.getItemMeta();
        if (meta == null || !meta.isGlider()) return;

        // Must be in the air
        if (player.isOnGround()) return;

        double yDelta = event.getTo().getY() - event.getFrom().getY();
        Double prevDelta = lastYDelta.get(player.getUniqueId());
        lastYDelta.put(player.getUniqueId(), yDelta);

        if (prevDelta == null) return;

        // Cooldown
        long now = System.currentTimeMillis();
        if (now - lastBoostTime.getOrDefault(player.getUniqueId(), 0L) < BOOST_COOLDOWN_MS) return;

        // Detection: abrupt change from falling to rising
        // jumpFromElytra() gives a Y-velocity of ~0.4
        // Use the prevDelta - yDelta difference to be independent of the tickrate
        if (prevDelta < -0.1 && yDelta - prevDelta > 0.4) {
            applyBoost(player, now);
        }
    }

    // =========================
    // FALLBACK: PlayerToggleFlightEvent (in case it fires)
    // =========================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        // Toggle check: if the player disabled the boost — skip
        if (flyDisabled.contains(player.getUniqueId())) return;

        // Check the chestplate/elytra with the glider component
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null) return;
        ItemMeta meta = chest.getItemMeta();
        if (meta == null || !meta.isGlider()) return;

        // Protection: if the player was NOT gliding before — skip (flight start)
        if (!player.isGliding() && !lastYDelta.containsKey(player.getUniqueId())) return;

        // Cooldown
        long now = System.currentTimeMillis();
        if (now - lastBoostTime.getOrDefault(player.getUniqueId(), 0L) < BOOST_COOLDOWN_MS) return;

        // Cancel the event and restore gliding
        event.setCancelled(true);
        player.setGliding(true);
        applyBoost(player, now);
    }

    // =========================
    // BOOST LOGIC
    // =========================

    private static void applyBoost(Player player, long now) {
        lastBoostTime.put(player.getUniqueId(), now);

        // MAXIMUM boost: strong acceleration forward + up
        Vector direction = player.getLocation().getDirection();
        Vector boost = direction.clone().multiply(5.0).setY(Math.max(direction.getY() * 0.5 + 1.2, 0.8));
        player.setVelocity(player.getVelocity().add(boost));

        // Make sure gliding continues
        player.setGliding(true);

        // Effects
        var loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.FIREWORK, loc, 40, 1.0, 1.0, 1.0, 0.1);
        player.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.5, 0.5, 0.5, 0);
        player.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.8f);
        player.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.5f);
    }

    // =========================
    // CLEANUP
    // =========================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastYDelta.remove(uuid);
        lastBoostTime.remove(uuid);
        // Keep flyDisabled in memory — DB persistence handles server restarts.
        // Player will re-check on next join via the in-memory set.
    }

    // =========================
    // DB PERSISTENCE
    // =========================

    private static void loadFlyDisabledFromDb() {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;
        try (PreparedStatement ps = con.prepareStatement("SELECT uuid FROM elytra_boost_disabled");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    flyDisabled.add(UUID.fromString(rs.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                    // Corrupted UUID in DB — skip
                }
            }
            ConsoleLogger.info(
                    "[ElytraBoost] Loaded " + flyDisabled.size() + " disabled players from DB");
        } catch (SQLException e) {
            ConsoleLogger.error("[ElytraBoost] DB load error: " + e.getMessage());
        }
    }

    private static void saveFlyDisabledToDb(UUID uuid) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT OR REPLACE INTO elytra_boost_disabled (uuid) VALUES (?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.error("[ElytraBoost] DB save error: " + e.getMessage());
        }
    }

    private static void deleteFlyDisabledFromDb(UUID uuid) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM elytra_boost_disabled WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.error("[ElytraBoost] DB delete error: " + e.getMessage());
        }
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Checks whether the player was boosted by the plugin in the last windowMs milliseconds.
     * Used by the ElytraCheck anti-cheat check so legitimate boosts are not flagged.
     */
    public static boolean isRecentlyBoosted(UUID uuid, long windowMs) {
        Long lastTime = lastBoostTime.get(uuid);
        if (lastTime == null) return false;
        return System.currentTimeMillis() - lastTime <= windowMs;
    }
}
