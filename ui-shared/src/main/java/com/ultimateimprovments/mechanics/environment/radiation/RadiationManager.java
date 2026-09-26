package com.ultimateimprovments.mechanics.environment.radiation;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RadiationManager implements Listener {

    private static RadiationManager instance;

    // =========================
    // IN-MEMORY STORAGE (UUID -> radiation)
    // =========================
    // Radiation is a smooth double value now: fractional sources accumulate
    // between whole seconds instead of being truncated away.
    private final Map<UUID, Double> radiationMap = new ConcurrentHashMap<>();
    private final Set<UUID> radViewEnabled = new HashSet<>();

    public static boolean isRadViewEnabled(Player player) {
        if (instance == null || player == null) return false;
        return instance.radViewEnabled.contains(player.getUniqueId());
    }

    public static void toggleRadView(Player player) {
        if (instance == null || player == null) return;
        UUID uuid = player.getUniqueId();
        if (!instance.radViewEnabled.remove(uuid)) {
            instance.radViewEnabled.add(uuid);
        }
    }

    public static RadiationManager getInstance() {
        return instance;
    }

    public static void init() {
        instance = new RadiationManager();
        instance.loadConfig();

        // Register events
        Main plugin = Main.getInstance();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
    }

    // =========================
    // CONFIG
    // =========================
    private boolean enabled;
    private int naturalDecay;
    private boolean effectsEnabled;
    private int ancientDebrisRad;
    private int basaltDeltasRad;
    private int endRad;
    private int killReduction;
    private boolean deathReset;
    private int maceUseRad;
    private int tridentUseRad;
    private int elytraUseRad;
    private int reactorCoreRad;
    private int reactorPressRad;
    private int reactorMeltdownCloseRad;
    private int reactorMeltdownFarRad;
    private int spaceRadiation;
    private int spaceIntervalTicks;
    private int spaceSecondCounter = 0;

    private void loadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("radiation.enabled", true);
        naturalDecay = cfg.getInt("radiation.natural_decay", 1);
        effectsEnabled = cfg.getBoolean("radiation.effects_enabled", true);
        ancientDebrisRad = cfg.getInt("radiation.ancient_debris_radiation", 2);
        basaltDeltasRad = cfg.getInt("radiation.basalt_deltas_radiation", 2);
        endRad = cfg.getInt("radiation.end_radiation", 2);
        killReduction = cfg.getInt("radiation.kill_reduction", 100);
        deathReset = cfg.getBoolean("radiation.death_reset", true);
        maceUseRad = cfg.getInt("radiation.mace_use_radiation", 50);
        tridentUseRad = cfg.getInt("radiation.trident_use_radiation", 50);
        elytraUseRad = cfg.getInt("radiation.elytra_use_radiation", 50);
        reactorCoreRad = cfg.getInt("radiation.reactor_core_radiation", 10);
        reactorPressRad = cfg.getInt("radiation.reactor_pressure_radiation", 600);
        reactorMeltdownCloseRad = cfg.getInt("radiation.reactor_meltdown_close", 6400);
        reactorMeltdownFarRad = cfg.getInt("radiation.reactor_meltdown_far", 3200);
        spaceRadiation = cfg.getInt("radiation.space_radiation", 199);
        spaceIntervalTicks = Math.max(1, cfg.getInt("radiation.space_interval_ticks", 200));
    }

    public void reloadConfig() {
        loadConfig();
    }

    // =========================
    // PUBLIC API
    // =========================

    public static void addRadiation(Player player, double amount) {
        if (instance == null || !instance.enabled || player == null) return;
        double current = instance.radiationMap.getOrDefault(player.getUniqueId(), 0.0);
        instance.radiationMap.put(player.getUniqueId(), Math.max(0.0, current + amount));
    }

    public static void addRadiationNear(Location loc, double radius, double amount) {
        if (instance == null || !instance.enabled || loc == null || loc.getWorld() == null) return;
        double radiusSq = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(loc.getWorld())
                    && player.getLocation().distanceSquared(loc) <= radiusSq) {
                addRadiation(player, amount);
            }
        }
    }

    public static double getRadiation(Player player) {
        if (instance == null || player == null) return 0.0;
        return instance.radiationMap.getOrDefault(player.getUniqueId(), 0.0);
    }

    public static void setRadiation(Player player, double amount) {
        if (instance == null || player == null) return;
        instance.radiationMap.put(player.getUniqueId(), Math.max(0.0, amount));
    }

    public static void resetRadiation(Player player) {
        setRadiation(player, 0);
    }

    // =========================
    // DB PERSISTENCE (REAL column — no rounding)
    // =========================

    private void saveToDB(Player player) {
        double rad = radiationMap.getOrDefault(player.getUniqueId(), 0.0);
        saveToDB(player.getUniqueId(), rad);
    }

    private void saveToDB(UUID uuid, double radiation) {
        String sql = "INSERT OR REPLACE INTO player_radiation (uuid, radiation) VALUES (?, ?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, uuid.toString());
            st.setDouble(2, radiation);
            st.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double loadFromDB(UUID uuid) {
        String sql = "SELECT radiation FROM player_radiation WHERE uuid = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, uuid.toString());
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return rs.getDouble("radiation");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public static void saveAll() {
        if (instance == null) return;
        for (Map.Entry<UUID, Double> entry : instance.radiationMap.entrySet()) {
            instance.saveToDB(entry.getKey(), entry.getValue());
        }
    }

    // =========================
    // DEATH / RESPAWN / JOIN / QUIT EVENTS
    // =========================

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if (!enabled || !deathReset) return;
        resetRadiation(player);
        // Persist 0 IMMEDIATELY — otherwise the old value stays in the DB until the
        // next autosave (up to 5 min) or quit, and a restart within that window
        // would restore the radiation the player should have lost on death.
        saveToDB(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        if (!enabled || !deathReset) return;
        // Safety net: after respawn radiation is definitely 0
        resetRadiation(player);
        saveToDB(player);
    }

    // =========================
    // KILL REDUCTION (kill_reduction config)
    // =========================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (!enabled) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (e.getEntity() instanceof Player) {
            onPlayerKill(killer);
        } else {
            onMobKill(killer);
        }
    }

    // =========================
    // WEAPON-USE RADIATION (mace/trident/elytra configs)
    // =========================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent e) {
        if (!enabled) return;
        if (!(e.getDamager() instanceof Player player)) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.MACE) return;
        onMaceUse(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTridentThrow(ProjectileLaunchEvent e) {
        if (!enabled) return;
        if (e.getEntity() instanceof Trident && e.getEntity().getShooter() instanceof Player player) {
            onTridentUse(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        double rad = loadFromDB(player.getUniqueId());
        radiationMap.put(player.getUniqueId(), Math.max(0.0, rad));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        // Save to DB before removing from memory so radiation is not lost
        saveToDB(player);
        radiationMap.remove(player.getUniqueId());
        radViewEnabled.remove(player.getUniqueId());
        DosimeterTask.clearRate(player.getUniqueId());
        HazmatManager.handleQuit(player.getUniqueId());
    }

    // =========================
    // MAIN TICK (every 20 ticks = 1 second)
    // =========================
    public void tick() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Game mode does NOT matter — radiation is physics, not a privilege.
            // (Creative/Spectator are intentionally included.)

            // Skip dead players — radiation was reset on death
            if (player.isDead() || player.getHealth() <= 0) continue;

            UUID uuid = player.getUniqueId();
            double rad = radiationMap.getOrDefault(uuid, 0.0);
            double beforeSources = rad; // snapshot for the dosimeter rate readout

            // =========================
            // NATURAL DECAY — proportional to the current level, 1 unit/sec at
            // 100 rad. Small doses fade slowly; large doses decay proportionally
            // faster. Floor-cut at ~0 so the value actually reaches zero.
            // =========================
            if (rad > 0.0) {
                rad = Math.max(0.0, rad - naturalDecay * (rad / 100.0));
            }

            // =========================
            // ANCIENT DEBRIS IN INVENTORY — radiation × amount.
            // 1 debris → 1x rate, 64 debris → 64x rate. Bundles do NOT shield
            // (debris inside still counts), shulker boxes DO shield.
            // =========================
            int debrisCount = countInInventory(player, Material.ANCIENT_DEBRIS);
            if (debrisCount > 0) {
                rad += ancientDebrisRad * debrisCount;
            }

            // =========================
            // BASALT_DELTAS BIOME
            // =========================
            if (player.getLocation().getBlock().getBiome() == org.bukkit.block.Biome.BASALT_DELTAS) {
                rad += basaltDeltasRad;
            }

            // =========================
            // THE END — RADIATION UNDER OPEN SKY
            // The End has no skylight engine, so "open sky" means no block above
            // the player up to the max build height (upward block ray trace).
            // =========================
            if (player.getWorld().getEnvironment() == World.Environment.THE_END
                    && hasNoBlocksAbove(player)) {
                rad += endRad;
            }

            // =========================
            // SPACE DIMENSION — RADIATION EVERY spaceIntervalTicks
            // Config is in TICKS (200 = 10 sec). tick() runs once per second,
            // so the counter tracks seconds and fires when the configured tick
            // interval has elapsed. The old code compared ticks to seconds and
            // fired 20x too rarely.
            // =========================
            if (com.ultimateimprovments.space.SpaceManager.isInSpace(player.getWorld())
                    && spaceSecondCounter * 20 >= spaceIntervalTicks) {
                rad += spaceRadiation;
            }

            // =========================
            // ELYTRA GLIDING ADDS RADIATION (elytra_use_radiation)
            // =========================
            if (player.isGliding()) {
                rad += elytraUseRad;
            }

            // =========================
            // HAZMAT SUIT — reduces INCOMING radiation by a percentage
            // (radiation is not removed, only weakened: rad *= 1 - protection).
            // Protection level comes from the worn hazmat pieces (0..0.8);
            // the manager scans the armor on its own 2-second cadence.
            // =========================
            double protection = com.ultimateimprovments.mechanics.environment.radiation.HazmatManager.getProtection(player);
            if (protection > 0.0) {
                rad = Math.max(0.0, rad * (1.0 - protection));
            }

            // =========================
            // TOGGLE RADIATION DISPLAY IN ACTIONBAR (R/h)
            // =========================
            if (radViewEnabled.contains(uuid)) {
                double roentgen = rad / 100.0;
                player.sendActionBar(MessageUtil.parse("<white>Radiation: </white><gray>"
                        + String.format(Locale.US, "%.1f", roentgen) + "</gray> <white>R/h</white>"));
            }

            radiationMap.put(uuid, Math.max(0.0, rad));

            // Feed the dosimeter rate readout: net gain this second (sources
            // minus decay), so R shows what the environment is doing to the
            // player right now, not the stored dose.
            DosimeterTask.recordRate(uuid, rad - beforeSources);
        }

        // Space radiation counter — 1 second per tick() call; fires when the
        // configured tick interval elapses (see tick comment above).
        spaceSecondCounter++;
        if (spaceSecondCounter * 20 >= spaceIntervalTicks) {
            spaceSecondCounter = 0;
        }
    }

    // =========================
    // EFFECTS TICK (every 10 ticks)
    // =========================
    public void tickEffects() {
        if (!enabled || !effectsEnabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Game mode does NOT matter (see tick()).
            if (player.isDead() || player.getHealth() <= 0) continue;

            double rad = radiationMap.getOrDefault(player.getUniqueId(), 0.0);
            if (rad < 200) continue;

            int duration = 40; // 2 seconds

            if (rad < 400) {
                // Level 1: 200-399  (~2-4 R/h)
                giveEffect(player, PotionEffectType.HUNGER, duration, 0);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 0);
            } else if (rad < 800) {
                // Level 2: 400-799  (~4-8 R/h)
                giveEffect(player, PotionEffectType.HUNGER, duration, 1);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 1);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 0);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 0);
            } else if (rad < 1600) {
                // Level 3: 800-1599  (~8-16 R/h)
                giveEffect(player, PotionEffectType.HUNGER, duration, 2);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 2);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 0);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 1);
            } else if (rad < 3200) {
                // Level 4: 1600-3199  (~16-32 R/h)
                giveEffect(player, PotionEffectType.HUNGER, duration, 4);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 4);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 1);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 2);
                giveEffect(player, PotionEffectType.BLINDNESS, duration, 0);
            } else if (rad < 6400) {
                // Level 5: 3200-6399  (~32-64 R/h)
                giveEffect(player, PotionEffectType.HUNGER, duration, 6);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 6);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 1);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 3);
                giveEffect(player, PotionEffectType.BLINDNESS, duration, 0);
                giveEffect(player, PotionEffectType.MINING_FATIGUE, duration, 1);
            } else {
                // Level 6: 6400+  (64+ R/h)
                giveEffect(player, PotionEffectType.HUNGER, duration, 10);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 10);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 2);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 5);
                giveEffect(player, PotionEffectType.BLINDNESS, duration, 0);
                giveEffect(player, PotionEffectType.MINING_FATIGUE, duration, 3);
                giveEffect(player, PotionEffectType.DARKNESS, duration, 0);
            }
        }
    }

    private void giveEffect(Player player, PotionEffectType type, int duration, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true));
    }

    // =========================
    // LISTENER TRIGGERS (call from event handlers)
    // =========================

    public static void onPlayerKill(Player killer) {
        if (instance == null || !instance.enabled) return;
        double rad = instance.radiationMap.getOrDefault(killer.getUniqueId(), 0.0);
        if (rad >= 200) {
            addRadiation(killer, -instance.killReduction);
        }
    }

    public static void onMobKill(Player killer) {
        if (instance == null || !instance.enabled) return;
        double rad = instance.radiationMap.getOrDefault(killer.getUniqueId(), 0.0);
        if (rad >= 200) {
            addRadiation(killer, -instance.killReduction);
        }
    }

    public static void onMaceUse(Player player) {
        if (instance == null || !instance.enabled) return;
        addRadiation(player, instance.maceUseRad);
    }

    public static void onTridentUse(Player player) {
        if (instance == null || !instance.enabled) return;
        addRadiation(player, instance.tridentUseRad);
    }

    public static void onElytraUse(Player player) {
        if (instance == null || !instance.enabled) return;
        addRadiation(player, instance.elytraUseRad);
    }

    // =========================
    // FOOD REDUCES RADIATION (-10, if radiation > 200)
    // =========================

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent e) {
        if (!enabled) return;
        Player player = e.getPlayer();
        // Game mode does NOT matter.

        ItemStack item = e.getItem();
        if (item == null || item.getType().isAir()) return;
        if (!item.getType().isEdible()) return;

        double rad = radiationMap.getOrDefault(player.getUniqueId(), 0.0);
        if (rad >= 200) {
            int reduction = 10;
            radiationMap.put(player.getUniqueId(), Math.max(0.0, rad - reduction));
            player.sendMessage(MessageUtil.parse("<green>🥗 -10 Radiation (ate food)</green>"));
        }
    }

    /*
     * ANTIRAD ITEMS — removed together with the Lead Shield.
     * The legacy config key radiation.antirad_reduction is still read above
     * so old on-disk configs do not trigger repair warnings, but nothing
     * consumes it. Hazmat armor (HazmatManager) is the replacement.
     */

    // =========================
    // HELPER METHODS
    // =========================

    private int countInInventory(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == material) {
                count += item.getAmount();
            }
            // Bundles are NOT shielding: debris inside a bundle still radiates.
            count += countInBundle(item, material);
            // Shulker boxes (any color) and any other container items DO shield:
            // their contents are not scanned.
        }
        for (ItemStack item : player.getInventory().getExtraContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
            if (item != null) {
                count += countInBundle(item, material);
            }
        }
        return count;
    }

    /**
     * Counts {@code material} inside a bundle's BUNDLE_CONTENTS component.
     * Recurses for nested bundles — a bundle in a bundle is still not shielding.
     */
    private int countInBundle(ItemStack item, Material material) {
        if (item.getType() != Material.BUNDLE
                && !item.getType().getKey().getKey().endsWith("_bundle")) {
            return 0;
        }
        BundleContents contents = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (contents == null) return 0;
        int count = 0;
        for (ItemStack inner : contents.contents()) {
            if (inner == null) continue;
            if (inner.getType() == material) {
                count += inner.getAmount();
            }
            count += countInBundle(inner, material);
        }
        return count;
    }

    /**
     * True if nothing solid is above the player up to the max build height.
     * Used instead of the skylight check in dimensions without a skylight
     * engine (the End): an upward block ray trace from the eyes; passable
     * blocks (grass, torches, ...) and liquids do not count as cover.
     */
    private boolean hasNoBlocksAbove(Player player) {
        Location eye = player.getEyeLocation();
        double topY = player.getWorld().getMaxHeight();
        if (eye.getY() >= topY) return true; // already above the build limit
        org.bukkit.util.RayTraceResult hit = player.getWorld().rayTraceBlocks(
                eye,
                new org.bukkit.util.Vector(0, 1, 0), // straight up
                topY - eye.getY(),                    // up to the build limit
                org.bukkit.FluidCollisionMode.NEVER,
                true);                                // ignore passable blocks
        return hit == null;
    }
}
