package com.mcplugin.mechanics.features.structure;

import com.mcplugin.core.Main;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StructureIntegrityManager — отслеживает напряжение (stress), деградацию (degradation)
 * и целостность (integrity) структур (пока только эндер-сундуков).
 *
 * <h3>Механика:</h3>
 * <ul>
 *   <li><b>Stress</b> (0–1000%): растёт на +1% при каждом открытии/закрытии эндер-сундука.
 *       Естественный спад: 1%/30 сек.</li>
 *   <li><b>Degradation</b>: когда stress &gt; 100%, integrity падает.
 *       degradation = baseRate × (stress / 100)^exponent × exp(degTicks × timeExponent / 20).
 *       Скорость растёт от стресса (экспоненциально) И от времени деградации (экспоненциально).</li>
 *   <li><b>Integrity</b> (0–100%): падает со скоростью degradation, когда stress &gt; 100%.
 *       Восстанавливается 1%/мин, когда stress &lt; 100%.
 *       При 0% — взрыв.</li>
 * </ul>
 */
public class StructureIntegrityManager {

    private static StructureIntegrityManager instance;
    private final Map<String, StructureData> dataMap = new ConcurrentHashMap<>();
    private final Main plugin;
    private boolean initialized = false;
    private boolean dataDirty = false; // true если были изменения с上次 сохранения

    // Цвета для градиентов (HEX без #)
    private static final String COLOR_GREEN = "00AA00";
    private static final String COLOR_RED = "FF0000";
    private static final String COLOR_YELLOW = "FFAA00";
    private static final String COLOR_DARK_GREEN = "006600";

    // =========================
    // КОНФИГУРИРУЕМЫЕ ПАРАМЕТРЫ (из config.yml)
    // =========================
    private boolean enabled = true;
    private double stressPerOpenClose = 1.0;
    private double stressDecayPerSecond = 1.0 / 30.0;
    private double baseDegradationRate = 0.1; // 0.1%/sec initial rate
    private double degradationExponent = 2.0;
    private double degradationTimeExponent = 0.01;  // экспонента времени деградации
    private double stressMax = 1000.0;
    private double stressThreshold = 100.0;
    private double explosionPower = 10.0;
    private double integrityRecoveryPerSecond = 1.0 / 60.0;

    // =========================
    // DATA MODEL
    // =========================
    public static final class StructureData {
        private final Location location;
        private double stress;            // 0–1000
        private double integrity;         // 0–100
        private int degradationTicks;     // сколько тиков stress > 100% (для time-экспоненты)

        public StructureData(Location location) {
            this.location = location;
            this.stress = 0.0;
            this.integrity = 100.0;
            this.degradationTicks = 0;
        }

        public Location getLocation() { return location; }
        public double getStress() { return stress; }
        public double getIntegrity() { return integrity; }
        public int getDegradationTicks() { return degradationTicks; }

        public String getStatus() {
            if (integrity <= 0) return "DESTROYED";
            if (stress == 0) return "IDLE";
            if (stress <= 20) return "VERY LOW STRESS";
            if (stress <= 40) return "LOW STRESS";
            if (stress <= 60) return "MEDIUM STRESS";
            if (stress <= 80) return "HIGH STRESS";
            if (stress <= 100) return "VERY HIGH STRESS";
            return "OVERLOAD";
        }

        public void addStress(double amount, double max) {
            this.stress = Math.min(max, this.stress + amount);
        }
    }

    // =========================
    // SINGLETON
    // =========================
    public static StructureIntegrityManager getInstance() {
        return instance;
    }

    public static void init(Main plugin) {
        if (instance != null) return;
        instance = new StructureIntegrityManager(plugin);
    }

    private StructureIntegrityManager(Main plugin) {
        this.plugin = plugin;
        loadConfig();
        loadData();
        migrateOldDat();
        startTicker();
        startAutoSave();
        initialized = true;
        ConsoleLogger.info("[StructureIntegrity] Initialized.");
    }

    // =========================
    // CONFIG
    // =========================
    public void loadConfig() {
        var cfg = plugin.getConfig().getConfigurationSection("structure_integrity");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        stressPerOpenClose = cfg.getDouble("stress_per_open_close", 1.0);
        stressDecayPerSecond = cfg.getDouble("stress_decay_per_second", 1.0 / 30.0);
        baseDegradationRate = cfg.getDouble("base_degradation_rate", 0.1);
        degradationExponent = cfg.getDouble("degradation_exponent", 2.0);
        degradationTimeExponent = cfg.getDouble("degradation_time_exponent", 0.01);
        stressMax = cfg.getDouble("stress_max", 1000.0);
        stressThreshold = cfg.getDouble("stress_threshold", 100.0);
        explosionPower = cfg.getDouble("explosion_power", 10.0);
        integrityRecoveryPerSecond = cfg.getDouble("integrity_recovery_per_second", 1.0 / 60.0);
    }

    // =========================
    // PUBLIC API
    // =========================

    /** Вызывается при открытии или закрытии эндер-сундука — добавляет stress */
    public void onEnderChestInteract(Location chestLocation) {
        if (!enabled) return;
        String key = locationToKey(chestLocation);
        StructureData data = dataMap.computeIfAbsent(key, k -> new StructureData(chestLocation));
        data.addStress(stressPerOpenClose, stressMax);
        dataDirty = true;
    }

    /** Получить данные структуры по локации блока */
    public StructureData getData(Location location) {
        return dataMap.get(locationToKey(location));
    }

    /** Проверяет, деградирует ли структура (stress > 100% и integrity < 100%) */
    public boolean isDegrading(Location location) {
        StructureData data = getData(location);
        if (data == null) return false;
        return data.stress > stressThreshold || data.integrity < 100.0;
    }

    /** Вычисляет degradation rate для данных структуры */
    private double computeDegradation(StructureData data) {
        if (data.stress <= stressThreshold) return 0.0;
        double ratio = data.stress / stressThreshold;
        double stressFactor = Math.pow(ratio, degradationExponent);
        double timeSeconds = data.degradationTicks;
        double timeFactor = Math.exp(timeSeconds * degradationTimeExponent);
        return baseDegradationRate * stressFactor * timeFactor;
    }

    /**
     * Оценивает время до взрыва (сек) при текущем stress и degradationTicks.
     * Учитывает экспоненциальное ускорение деградации от времени.
     * @return секунд до integrity = 0%, или -1 если деградация не идёт
     */
    private double estimateDetonationTime(StructureData data) {
        if (data.stress <= stressThreshold || data.integrity <= 0) return -1;
        double ratio = data.stress / stressThreshold;
        double stressFactor = Math.pow(ratio, degradationExponent);
        double curTicks = data.degradationTicks;

        if (degradationTimeExponent <= 0) {
            // Без time-экспоненты — линейная оценка
            double deg = computeDegradation(data);
            if (deg <= 0) return -1;
            return data.integrity / deg;
        }

        // Интегральная оценка с учётом экспоненты времени:
        // integrity = base * factor * integral(exp((curTicks + t) * timeExp) dt) от 0 до T
        // integrity = base * factor * (exp((curTicks+T)*timeExp) - exp(curTicks*timeExp)) / timeExp
        // Решаем относительно T:
        double factor = baseDegradationRate * stressFactor;
        if (factor <= 0) return -1;
        double expCur = Math.exp(curTicks * degradationTimeExponent);
        double target = data.integrity * degradationTimeExponent / factor + expCur;
        if (target <= 0) return -1;
        double t = Math.log(target) / degradationTimeExponent - curTicks;
        return Math.max(0, t);
    }

    // =========================
    // GRADIENT COLORS
    // =========================
    /** Возвращает HEX цвет (#RRGGBB) для stress (0-100%, зелёный→жёлтый→красный, выше 100% — красный) */
    private static String gradientStress(double stress) {
        double t = Math.max(0, Math.min(1, stress / 100.0));
        return gradientHex(t, COLOR_GREEN, COLOR_YELLOW, COLOR_RED);
    }

    /** Возвращает HEX цвет (#RRGGBB) для degradation (0-10, зелёный→жёлтый→красный) */
    private static String gradientDegradation(double deg) {
        double t = Math.max(0, Math.min(1, deg / 10.0));
        return gradientHex(t, COLOR_GREEN, COLOR_YELLOW, COLOR_RED);
    }

    /** Возвращает HEX цвет (#RRGGBB) для integrity (100→0, зелёный→жёлтый→красный) */
    private static String gradientIntegrity(double integrity) {
        double t = 1.0 - Math.max(0, Math.min(1, integrity / 100.0));
        return gradientHex(t, COLOR_GREEN, COLOR_YELLOW, COLOR_RED);
    }

    /**
     * Вычисляет HEX цвет по 3-точечному градиенту.
     * t=0 → color1, t=0.5 → color2, t=1 → color3
     */
    private static String gradientHex(double t, String c1, String c2, String c3) {
        int r, g, b;
        if (t < 0.5) {
            // c1 → c2
            double lt = t * 2.0;
            r = lerpHex(c1.substring(0, 2), c2.substring(0, 2), lt);
            g = lerpHex(c1.substring(2, 4), c2.substring(2, 4), lt);
            b = lerpHex(c1.substring(4, 6), c2.substring(4, 6), lt);
        } else {
            // c2 → c3
            double lt = (t - 0.5) * 2.0;
            r = lerpHex(c2.substring(0, 2), c3.substring(0, 2), lt);
            g = lerpHex(c2.substring(2, 4), c3.substring(2, 4), lt);
            b = lerpHex(c2.substring(4, 6), c3.substring(4, 6), lt);
        }
        return String.format("#%02X%02X%02X", clamp(r), clamp(g), clamp(b));
    }

    private static int lerpHex(String a, String b, double t) {
        int ia = Integer.parseInt(a, 16);
        int ib = Integer.parseInt(b, 16);
        return (int) Math.round(ia + (ib - ia) * t);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Рисует ASCII-бар с градиентным цветом для числа */
    private static String coloredValue(String prefix, double value, String unit, String hexColor) {
        return "<gray>" + prefix + "</gray><color:" + hexColor + ">" + String.format("%.2f", value) + unit + "</color>";
    }

    /** Рисует ASCII-бар с градиентным цветом */
    private String drawBar(double current, double max, int segments, String hexColor) {
        int filled = (int) Math.round((current / max) * segments);
        filled = Math.max(0, Math.min(segments, filled));
        StringBuilder sb = new StringBuilder("  <color:").append(hexColor).append(">");
        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? "█" : "░");
        }
        sb.append("</color>");
        return sb.toString();
    }

    // =========================
    // SHOW INFO
    // =========================
    public void showInfo(Player player, Location loc) {
        StructureData data = getData(loc);
        if (data == null) {
            player.sendMessage(MessageUtil.parse("<gray>This structure has no integrity data yet.</gray>"));
            return;
        }

        Block block = loc.getBlock();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "?";

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));
        player.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Structure Integrity Report</white>"));
        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));

        // Coordinates
        player.sendMessage(MessageUtil.parse(" <gray>Location: </gray><white>" + worldName
                + "  " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "</white>"));

        // Block type
        player.sendMessage(MessageUtil.parse(" <gray>Block: </gray><white>" + block.getType().name().toLowerCase().replace('_', ' ') + "</white>"));

        // Status
        player.sendMessage(MessageUtil.parse(" <gray>Status: </gray>" + parseMiniMessageStatus(data)));

        // Stress bar (0–100%, gradient от 0-100%, выше 100% — красный/полный бар)
        double stress = Math.max(0, Math.min(stressMax, data.getStress()));
        String stressHex = gradientStress(stress);
        double barVal = Math.min(stressThreshold, stress); // бар не выше threshold
        player.sendMessage(MessageUtil.parse(coloredValue("Structure Stress: ", stress, "%", stressHex)));
        player.sendMessage(MessageUtil.parse(drawBar(barVal, 100.0, 20, stressHex)));
        if (stress > stressThreshold) {
            player.sendMessage(MessageUtil.parse(" <red>⚠ OVERLOAD! Stress exceeds " + String.format("%.0f", stressThreshold) + "%!</red>"));
        }

        // Degradation rate (0-inf, gradient green→yellow→red, capped at 10 for bar/color)
        double deg = computeDegradation(data);
        String degHex = gradientDegradation(deg);
        double degBarVal = Math.min(10.0, deg); // бар до 10%/с (не лимит, только шкала)
        player.sendMessage(MessageUtil.parse(coloredValue("Degradation: ", deg, "%/s", degHex)));
        player.sendMessage(MessageUtil.parse(drawBar(degBarVal, 10.0, 20, degHex)));

        // Integrity bar (0–100%, gradient red→yellow→green)
        double integrity = Math.max(0, Math.min(100, data.getIntegrity()));
        String intHex = gradientIntegrity(integrity);
        player.sendMessage(MessageUtil.parse(coloredValue("Integrity: ", integrity, "%", intHex)));
        player.sendMessage(MessageUtil.parse(drawBar(integrity, 100, 20, intHex)));

        // Degradation time & detonation estimate
        if (data.degradationTicks > 0) {
            double secs = data.degradationTicks;
            double detSecs = estimateDetonationTime(data);
            player.sendMessage(MessageUtil.parse(" <gray>Degrading for: </gray><yellow>" + String.format(Locale.US, "%.2fs", secs) + "</yellow>"));
            if (detSecs >= 0) {
                String detHex = detSecs <= 10 ? "red" : detSecs <= 60 ? "yellow" : "green";
                player.sendMessage(MessageUtil.parse(" <gray>Detonation in: </gray><" + detHex + ">" + String.format(Locale.US, "%.2fs", detSecs) + "</" + detHex + ">"));
            }
        }

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));

        // Warnings
        if (integrity <= 20 && integrity > 0) {
            player.sendMessage(MessageUtil.parse("<red>⚠ WARNING: Structure integrity critically low!</red>"));
        }
        if (stress >= stressThreshold) {
            player.sendMessage(MessageUtil.parse("<red>⚠ WARNING: Structure is overloaded! Integrity is degrading!</red>"));
            if (stress >= 500.0) {
                player.sendMessage(MessageUtil.parse("<dark_red>⚠ DANGER: Critical overload! Integrity dropping rapidly!</dark_red>"));
            }
        }
        if (data.degradationTicks > 0 && data.degradationTicks % 20 == 0) {
            // Показываем acceleration warning каждые 20 тиков (20 сек)
            if (data.degradationTicks >= 40) {                    player.sendMessage(MessageUtil.parse("<yellow>⚠ Degradation accelerating! (" + data.degradationTicks + "s)</yellow>"));
            }
        }
    }

    // =========================
    // TICKER (1 раз в секунду = 20 тиков)
    // =========================
    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                tickAll();
            }
        }.runTaskTimer(plugin, 20L, 20L); // каждую секунду
    }

    private void tickAll() {
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, StructureData> entry : dataMap.entrySet()) {
            String key = entry.getKey();
            StructureData data = entry.getValue();
            Location loc = data.getLocation();

            // Проверяем, существует ли ещё блок
            Block block = loc.getBlock();
            if (block.getType() != Material.ENDER_CHEST) {
                toRemove.add(key);
                continue;
            }

            // Stress decay (всегда, если stress > 0)
            if (data.stress > 0) {
                data.stress = Math.max(0, data.stress - stressDecayPerSecond);
            }

            if (data.stress > stressThreshold) {
                // Stress > threshold — деградация
                data.degradationTicks++;

                double ratio = data.stress / stressThreshold;
                double stressFactor = Math.pow(ratio, degradationExponent);
                double timeSeconds = data.degradationTicks;
                double timeFactor = Math.exp(timeSeconds * degradationTimeExponent);
                double degradation = baseDegradationRate * stressFactor * timeFactor;

                data.integrity = Math.max(0, data.integrity - degradation);

                // Взрыв при 0%
                if (data.integrity <= 0) {
                    explodeStructure(loc);
                    toRemove.add(key);
                }
            } else {
                // Stress ≤ threshold — восстановление, сброс счётчика деградации
                if (data.degradationTicks > 0) {
                    data.degradationTicks = 0;
                }
                if (data.integrity < 100.0) {
                    data.integrity = Math.min(100.0, data.integrity + integrityRecoveryPerSecond);
                }
            }

            // =========================
            // VISUAL: портал-частицы — пропорционально повреждению целостности
            // =========================
            spawnIntegrityParticles(loc, data.getIntegrity());
        }

        // Очищаем удалённые блоки
        for (String key : toRemove) {
            dataMap.remove(key);
        }
        dataDirty = true;
    }

    // =========================
    // EXPLOSION
    // =========================
    private void explodeStructure(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        loc.getBlock().setType(Material.AIR);
        world.createExplosion(loc, (float) explosionPower, false, true);

        ConsoleLogger.warn("[StructureIntegrity] Ender chest at "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " in " + world.getName() + " exploded due to 0% integrity!");
    }

    // =========================
    // DATA PERSISTENCE (SQLite)
    // =========================
    private String locationToKey(Location loc) {
        return loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
    }

    private static Location keyToLocation(String key, World fallbackWorld) {
        String[] parts = key.split("\\|");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            world = fallbackWorld;
        }
        if (world == null) {
            // fallbackWorld может быть null если Bukkit.getWorlds() пуст
            if (!Bukkit.getWorlds().isEmpty()) {
                world = Bukkit.getWorlds().get(0);
            } else {
                return null; // нет миров вообще — не можем создать Location
            }
        }
        return new Location(world,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }

    /**
     * Мигрирует данные из старого structure_integrity.dat (Java serialization)
     * в SQLite. Запускается один раз при старте после loadData().
     */
    private void migrateOldDat() {
        File oldDat = new File(plugin.getDataFolder(), "structure_integrity.dat");
        if (!oldDat.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(oldDat))) {
            Object obj = ois.readObject();
            if (!(obj instanceof Map)) return;

            @SuppressWarnings("unchecked")
            Map<String, double[]> legacyMap = (Map<String, double[]>) obj;
            int migrated = 0;

            String sql = "INSERT OR IGNORE INTO structure_integrity (world, x, y, z, stress, integrity, degradation_ticks) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection con = DatabaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                for (Map.Entry<String, double[]> entry : legacyMap.entrySet()) {
                    String key = entry.getKey();
                    double[] vals = entry.getValue();

                    Location loc = keyToLocation(key, Bukkit.getWorlds().get(0));
                    if (loc == null) continue;

                    ps.setString(1, loc.getWorld().getName());
                    ps.setInt(2, loc.getBlockX());
                    ps.setInt(3, loc.getBlockY());
                    ps.setInt(4, loc.getBlockZ());
                    ps.setDouble(5, vals[0]); // stress
                    ps.setDouble(6, vals[1]); // integrity
                    ps.setInt(7, vals.length >= 3 ? (int) vals[2] : 0); // degradation_ticks
                    ps.addBatch();
                    migrated++;

                    // Also load into in-memory map if not already present
                    String mapKey = locationToKey(loc);
                    if (!dataMap.containsKey(mapKey)) {
                        StructureData data = new StructureData(loc);
                        data.stress = vals[0];
                        data.integrity = vals[1];
                        if (vals.length >= 3) data.degradationTicks = (int) vals[2];
                        dataMap.put(mapKey, data);
                    }
                }

                ps.executeBatch();
            }

            // Удаляем старый файл после успешной миграции
            oldDat.delete();
            ConsoleLogger.info("[StructureIntegrity] Migrated " + migrated + " entry(ies) from legacy .dat to DB.");

        } catch (Exception e) {
            ConsoleLogger.warn("[StructureIntegrity] Failed to migrate legacy .dat: " + e.getMessage());
        }
    }

    /**
     * Авто-сохранение в БД раз в 30 секунд (600 тиков).
     * Предотвращает потерю данных при краше сервера.
     */
    private void startAutoSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled || dataMap.isEmpty()) return;
                saveData(true); // тихий авто-сейв (без лога)
            }
        }.runTaskTimer(plugin, 600L, 600L);
    }

    public void saveData() {
        saveData(false);
    }

    public void saveData(boolean isAutoSave) {
        String sql = "INSERT OR REPLACE INTO structure_integrity (world, x, y, z, stress, integrity, degradation_ticks) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            if (!dataDirty && isAutoSave) return; // нет изменений — пропускаем

            for (Map.Entry<String, StructureData> entry : dataMap.entrySet()) {
                StructureData d = entry.getValue();
                Location loc = d.getLocation();
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.setDouble(5, d.stress);
                ps.setDouble(6, d.integrity);
                ps.setInt(7, d.degradationTicks);
                ps.addBatch();
            }

            ps.executeBatch();
            dataDirty = false;

            if (!isAutoSave) {
                ConsoleLogger.info("[StructureIntegrity] Saved " + dataMap.size() + " entry(ies) to DB.");
            }

        } catch (Exception e) {
            ConsoleLogger.warn("[StructureIntegrity] Failed to save data to DB: " + e.getMessage());
        }
    }

    private void loadData() {
        String sql = "SELECT world, x, y, z, stress, integrity, degradation_ticks FROM structure_integrity";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                String worldName = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                double stress = rs.getDouble("stress");
                double integrity = rs.getDouble("integrity");
                int degTicks = rs.getInt("degradation_ticks");

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Location loc = new Location(world, x, y, z);
                String key = locationToKey(loc);
                StructureData data = new StructureData(loc);
                data.stress = stress;
                data.integrity = integrity;
                data.degradationTicks = degTicks;
                dataMap.put(key, data);
                count++;
            }

            if (count > 0) {
                ConsoleLogger.info("[StructureIntegrity] Loaded " + count + " entry(ies) from DB.");
            }

        } catch (Exception e) {
            ConsoleLogger.warn("[StructureIntegrity] Failed to load data from DB: " + e.getMessage());
        }
    }

    // =========================
    // VISUAL: портал-частицы на эндер-сундуке
    // =========================
    private void spawnIntegrityParticles(Location loc, double integrity) {
        if (integrity >= 100.0) return;

        World world = loc.getWorld();
        if (world == null) return;

        // Количество частиц: от 0 при 100% до ~50 при 0%
        // Степень 1.5 для более резкого нарастания при критических значениях
        double damage = (100.0 - Math.max(0, integrity)) / 100.0;
        int count = (int) Math.round(Math.pow(damage, 1.5) * 50);
        if (count <= 0) return;

        // Центр блока, откуда летят частицы
        Location center = loc.clone().add(0.5, 0.5, 0.5);

        // Портал-частицы в объёме блока — чем ниже integrity, тем больше
        world.spawnParticle(Particle.PORTAL, center, count, 0.4, 0.4, 0.4, 0.05);

        // При integrity ниже 25% — дополнительные частицы вылетают ИЗ блока наружу
        if (integrity < 25.0) {
            int burstCount = (int) Math.round((25.0 - integrity) / 25.0 * 20) + 1;
            world.spawnParticle(Particle.PORTAL, center, burstCount, 1.5, 1.5, 1.5, 0.1);
        }

        // При integrity ниже 10% — редкие багровые искры (критические частицы)
        if (integrity < 10.0 && integrity > 0) {
            int sparkCount = (int) Math.round((10.0 - integrity) / 10.0 * 5) + 1;
            world.spawnParticle(Particle.END_ROD, center, sparkCount, 1.0, 1.0, 1.0, 0.05);
        }
    }

    // =========================
    // UTILITY
    // =========================
    private String parseMiniMessageStatus(StructureData data) {
        if (data.getIntegrity() <= 0) return "<red><bold>DESTROYED</bold></red>";
        double stress = data.getStress();
        String hex = gradientStress(stress);
        return "<color:" + hex + "><bold>" + data.getStatus() + "</bold></color>";
    }

    public void shutdown() {
        saveData();
        initialized = false;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
