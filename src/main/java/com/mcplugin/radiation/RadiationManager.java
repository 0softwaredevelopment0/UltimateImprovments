package com.mcplugin.radiation;

import com.mcplugin.Keys;
import com.mcplugin.Main;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.util.MessageUtil;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RadiationManager implements Listener {

    private static RadiationManager instance;

    // =========================
    // IN-MEMORY STORAGE (UUID -> radiation)
    // =========================
    private final Map<UUID, Integer> radiationMap = new HashMap<>();

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
    private boolean dosimeterEnabled;
    private int ancientDebrisRad;
    private int basaltDeltasRad;
    private int endRad;
    private int leadShieldReduction;
    private int antiradReduction;
    private int killReduction;
    private boolean deathReset;
    private int maceUseRad;
    private int tridentUseRad;
    private int elytraUseRad;
    private int reactorCoreRad;
    private int reactorPressRad;
    private int reactorMeltdownCloseRad;
    private int reactorMeltdownFarRad;

    private void loadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();

        enabled = cfg.getBoolean("radiation.enabled", true);
        naturalDecay = cfg.getInt("radiation.natural_decay", 1);
        effectsEnabled = cfg.getBoolean("radiation.effects_enabled", true);
        dosimeterEnabled = cfg.getBoolean("radiation.dosimeter_enabled", true);
        ancientDebrisRad = cfg.getInt("radiation.ancient_debris_radiation", 2);
        basaltDeltasRad = cfg.getInt("radiation.basalt_deltas_radiation", 2);
        endRad = cfg.getInt("radiation.end_radiation", 2);
        leadShieldReduction = cfg.getInt("radiation.lead_shield_reduction", 2);
        antiradReduction = cfg.getInt("radiation.antirad_reduction", 100);
        killReduction = cfg.getInt("radiation.kill_reduction", 100);
        deathReset = cfg.getBoolean("radiation.death_reset", true);
        maceUseRad = cfg.getInt("radiation.mace_use_radiation", 50);
        tridentUseRad = cfg.getInt("radiation.trident_use_radiation", 50);
        elytraUseRad = cfg.getInt("radiation.elytra_use_radiation", 50);
        reactorCoreRad = cfg.getInt("radiation.reactor_core_radiation", 10);
        reactorPressRad = cfg.getInt("radiation.reactor_pressure_radiation", 600);
        reactorMeltdownCloseRad = cfg.getInt("radiation.reactor_meltdown_close", 6400);
        reactorMeltdownFarRad = cfg.getInt("radiation.reactor_meltdown_far", 3200);
    }

    public void reloadConfig() {
        loadConfig();
    }

    // =========================
    // PUBLIC API
    // =========================

    public static void addRadiation(Player player, int amount) {
        if (instance == null || !instance.enabled || player == null) return;
        int current = instance.radiationMap.getOrDefault(player.getUniqueId(), 0);
        instance.radiationMap.put(player.getUniqueId(), Math.max(0, current + amount));
    }

    public static void addRadiationNear(Location loc, double radius, int amount) {
        if (instance == null || !instance.enabled || loc == null || loc.getWorld() == null) return;
        double radiusSq = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(loc.getWorld())
                    && player.getLocation().distanceSquared(loc) <= radiusSq) {
                addRadiation(player, amount);
            }
        }
    }

    public static int getRadiation(Player player) {
        if (instance == null || player == null) return 0;
        return instance.radiationMap.getOrDefault(player.getUniqueId(), 0);
    }

    public static void setRadiation(Player player, int amount) {
        if (instance == null || player == null) return;
        instance.radiationMap.put(player.getUniqueId(), Math.max(0, amount));
    }

    public static void resetRadiation(Player player) {
        setRadiation(player, 0);
    }

    // =========================
    // DB PERSISTENCE
    // =========================

    private void saveToDB(Player player) {
        int rad = radiationMap.getOrDefault(player.getUniqueId(), 0);
        saveToDB(player.getUniqueId(), rad);
    }

    private void saveToDB(UUID uuid, int radiation) {
        String sql = "INSERT OR REPLACE INTO player_radiation (uuid, radiation) VALUES (?, ?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, uuid.toString());
            st.setInt(2, radiation);
            st.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int loadFromDB(UUID uuid) {
        String sql = "SELECT radiation FROM player_radiation WHERE uuid = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, uuid.toString());
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return rs.getInt("radiation");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void saveAll() {
        if (instance == null) return;
        for (Map.Entry<UUID, Integer> entry : instance.radiationMap.entrySet()) {
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
        // БД: не пишем сразу — AsyncAutoSaveManager сохранит каждые 5 мин
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        if (!enabled || !deathReset) return;
        // Подстраховка: после возрождения радиация точно 0
        resetRadiation(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        int rad = loadFromDB(player.getUniqueId());
        radiationMap.put(player.getUniqueId(), rad);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        // Сохраняем в БД перед удалением из памяти, чтобы радиация не терялась
        saveToDB(player);
        radiationMap.remove(player.getUniqueId());
    }

    // =========================
    // MAIN TICK (every 20 ticks = 1 second)
    // =========================
    public void tick() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR) continue;

            // Пропускаем мёртвых игроков — радиация сброшена при смерти
            if (player.isDead() || player.getHealth() <= 0) continue;

            UUID uuid = player.getUniqueId();
            int rad = radiationMap.getOrDefault(uuid, 0);

            // =========================
            // ЕСТЕСТВЕННЫЙ СПАД (-1 за тик)
            // =========================
            if (rad > 0) {
                rad = Math.max(0, rad - naturalDecay);
            }

            // =========================
            // ДРЕВНИЕ ОБЛОМКИ В ИНВЕНТАРЕ
            // =========================
            if (hasInInventory(player, Material.ANCIENT_DEBRIS)) {
                rad += ancientDebrisRad;
            }

            // =========================
            // БИОМ BASALT_DELTAS
            // =========================
            if (player.getLocation().getBlock().getBiome() == org.bukkit.block.Biome.BASALT_DELTAS) {
                rad += basaltDeltasRad;
            }

            // =========================
            // THE END — РАДИАЦИЯ ПОД ОТКРЫТЫМ НЕБОМ
            // =========================
            if (player.getWorld().getEnvironment() == World.Environment.THE_END
                    && player.getLocation().getBlock().getLightFromSky() > 0) {
                rad += endRad;
            }

            // =========================
            // СВИНЦОВЫЙ ЩИТ УМЕНЬШАЕТ РАДИАЦИЮ
            // =========================
            if (hasCustomItem(player.getInventory().getItemInMainHand(), Keys.LEAD_SHIELD)
                    || hasCustomItem(player.getInventory().getItemInOffHand(), Keys.LEAD_SHIELD)) {
                rad = Math.max(0, rad - leadShieldReduction);
            }

            // =========================
            // ДОЗИМЕТР — ОТОБРАЖЕНИЕ В ACTIONBAR (Р/Ч)
            // =========================
            if (dosimeterEnabled && (hasCustomItem(player.getInventory().getItemInMainHand(), Keys.DOSIMETER)
                    || hasCustomItem(player.getInventory().getItemInOffHand(), Keys.DOSIMETER))) {
                double roentgen = rad / 100.0;
                player.sendActionBar(MessageUtil.parse("<white>Радиация: </white><gray>" + String.format(Locale.US, "%.1f", roentgen) + "</gray> <white>Р/Ч</white>"));
            }

            radiationMap.put(uuid, Math.max(0, rad));
        }
    }

    // =========================
    // EFFECTS TICK (every 10 ticks)
    // =========================
    public void tickEffects() {
        if (!enabled || !effectsEnabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR) continue;

            // Пропускаем мёртвых игроков
            if (player.isDead() || player.getHealth() <= 0) continue;

            int rad = radiationMap.getOrDefault(player.getUniqueId(), 0);
            if (rad < 200) continue;

            int duration = 40; // 2 seconds

            if (rad < 400) {
                // Уровень 1: 200-399  (~2-4 Р/Ч)
                giveEffect(player, PotionEffectType.HUNGER, duration, 0);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 0);
            } else if (rad < 800) {
                // Уровень 2: 400-799  (~4-8 Р/Ч)
                giveEffect(player, PotionEffectType.HUNGER, duration, 1);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 1);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 0);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 0);
            } else if (rad < 1600) {
                // Уровень 3: 800-1599  (~8-16 Р/Ч)
                giveEffect(player, PotionEffectType.HUNGER, duration, 2);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 2);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 0);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 1);
            } else if (rad < 3200) {
                // Уровень 4: 1600-3199  (~16-32 Р/Ч)
                giveEffect(player, PotionEffectType.HUNGER, duration, 4);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 4);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 1);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 2);
                giveEffect(player, PotionEffectType.BLINDNESS, duration, 0);
            } else if (rad < 6400) {
                // Уровень 5: 3200-6399  (~32-64 Р/Ч)
                giveEffect(player, PotionEffectType.HUNGER, duration, 6);
                giveEffect(player, PotionEffectType.SLOWNESS, duration, 6);
                giveEffect(player, PotionEffectType.NAUSEA, duration, 1);
                giveEffect(player, PotionEffectType.WEAKNESS, duration, 3);
                giveEffect(player, PotionEffectType.BLINDNESS, duration, 0);
                giveEffect(player, PotionEffectType.MINING_FATIGUE, duration, 1);
            } else {
                // Уровень 6: 6400+  (64+ Р/Ч)
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
        int rad = instance.radiationMap.getOrDefault(killer.getUniqueId(), 0);
        if (rad >= 200) {
            addRadiation(killer, -instance.killReduction);
        }
    }

    public static void onMobKill(Player killer) {
        if (instance == null || !instance.enabled) return;
        int rad = instance.radiationMap.getOrDefault(killer.getUniqueId(), 0);
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

    public static void onAntiRad(Player player) {
        if (instance == null || !instance.enabled) return;
        int rad = instance.radiationMap.getOrDefault(player.getUniqueId(), 0);
        if (rad >= 200) {
            addRadiation(player, -instance.antiradReduction);
        }
    }

    // =========================
    // HELPER METHODS
    // =========================

    private boolean hasInInventory(Player player, Material material) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) return true;
        }
        for (ItemStack item : player.getInventory().getExtraContents()) {
            if (item != null && item.getType() == material) return true;
        }
        return false;
    }

    private boolean hasCustomItem(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType() == Material.AIR) return false;
        // In Paper 1.21.4+ hasItemMeta() returns false for fresh items.
        // getItemMeta() always returns non-null for non-AIR.
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
