package com.mcplugin.mechanics.features.structure;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.ConsoleLogger;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StructureIntegrityManager — отслеживает напряжение (stress), деградацию (degradation)
 * и целостность (integrity) структур (пока только эндер-сундуков).
 *
 * <h3>Механика:</h3>
 * <ul>
 *   <li><b>Stress</b> (0–100%): растёт на +1% при каждом открытии/закрытии эндер-сундука.
 *       Естественный спад: 1%/30 сек.</li>
 *   <li><b>Degradation</b>: когда stress ≥ 100%, degradation начинает экспоненциально расти.
 *       Чем дольше stress на 100%, тем быстрее падает integrity.</li>
 *   <li><b>Integrity</b> (0–100%): падает со скоростью degradation, когда stress ≥ 100%.
 *       Восстанавливается 1%/мин, когда stress &lt; 100%.
 *       При 0% — взрыв.</li>
 * </ul>
 */
public class StructureIntegrityManager {

    private static StructureIntegrityManager instance;
    private final Map<String, StructureData> dataMap = new ConcurrentHashMap<>();
    private final Main plugin;
    private boolean initialized = false;

    // =========================
    // КОНФИГУРИРУЕМЫЕ ПАРАМЕТРЫ (из config.yml)
    // =========================
    private boolean enabled = true;
    private double stressPerOpenClose = 1.0;         // +1% за каждое открытие/закрытие
    private double stressDecayPerTick = 1.0 / 30.0;  // 1%/30 сек → ~0.0333%/сек
    private double baseDegradationRate = 1.0;         // базовый % деградации/сек при stress=100%
    private double degradationExponentMultiplier = 0.1; // множитель экспоненциального роста
    private double integrityRecoveryPerTick = 1.0 / 60.0; // 1%/мин → ~0.01666%/сек
    private double integrityRecoveryThreshold = 100.0;     // stress должен быть < 100% для восстановления

    // =========================
    // DATA MODEL
    // =========================
    public static final class StructureData {
        private final Location location;
        private double stress;        // 0–100
        private double integrity;     // 0–100
        private double degradation;   // скорость потери integrity (%/сек)
        private int stressFullTicks;  // сколько тиков stress находится на 100%

        public StructureData(Location location) {
            this.location = location;
            this.stress = 0.0;
            this.integrity = 100.0;
            this.degradation = 0.0;
            this.stressFullTicks = 0;
        }

        public Location getLocation() { return location; }
        public double getStress() { return stress; }
        public double getIntegrity() { return integrity; }
        public double getDegradation() { return degradation; }
        public String getStatus() {
            if (integrity <= 0) return "§4§lDESTROYED";
            if (stress >= 100.0) return "§c§lOVERLOADED";
            if (stress >= 75.0) return "§e§lHIGH STRESS";
            if (degradation > 0.1) return "§6DEGRADING";
            if (stress > 0) return "§aSTRESSED";
            return "§2STABLE";
        }

        public void addStress(double amount) {
            this.stress = Math.min(100.0, this.stress + amount);
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
        startTicker();
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
        stressDecayPerTick = cfg.getDouble("stress_decay_per_second", 1.0 / 30.0);
        baseDegradationRate = cfg.getDouble("base_degradation_rate", 1.0);
        degradationExponentMultiplier = cfg.getDouble("degradation_exponent_multiplier", 0.1);
        integrityRecoveryPerTick = cfg.getDouble("integrity_recovery_per_second", 1.0 / 60.0);
        integrityRecoveryThreshold = cfg.getDouble("integrity_recovery_threshold", 100.0);
    }

    // =========================
    // PUBLIC API
    // =========================

    /** Вызывается при открытии или закрытии эндер-сундука — добавляет stress */
    public void onEnderChestInteract(Location chestLocation) {
        if (!enabled) return;
        String key = locationToKey(chestLocation);
        StructureData data = dataMap.computeIfAbsent(key, k -> new StructureData(chestLocation));
        data.addStress(stressPerOpenClose);
    }

    /** Получить данные структуры по локации блока */
    public StructureData getData(Location location) {
        return dataMap.get(locationToKey(location));
    }

    /** Показать информацию о структуре игроку (английский) */
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

        // Stress bar
        double stress = Math.max(0, Math.min(100, data.getStress()));
        String stressColor = stress >= 100 ? "<red>" : (stress >= 75 ? "<yellow>" : (stress > 0 ? "<green>" : "<dark_green>"));
        player.sendMessage(MessageUtil.parse(" <gray>Structure Stress: </gray>" + stressColor + String.format("%.1f%%", stress)));
        player.sendMessage(MessageUtil.parse("  " + drawBar(stress, 100, 20, stressColor)));

        // Degradation
        double deg = Math.max(0, data.getDegradation());
        String degColor = deg > 5 ? "<red>" : (deg > 1 ? "<yellow>" : "<dark_green>");
        player.sendMessage(MessageUtil.parse(" <gray>Degradation: </gray>" + degColor + String.format("%.2f%%/s", deg)));

        // Integrity bar
        double integrity = Math.max(0, Math.min(100, data.getIntegrity()));
        String intColor = integrity <= 10 ? "<red>" : (integrity <= 30 ? "<yellow>" : (integrity <= 50 ? "<gold>" : "<green>"));
        player.sendMessage(MessageUtil.parse(" <gray>Integrity: </gray>" + intColor + String.format("%.1f%%", integrity)));
        player.sendMessage(MessageUtil.parse("  " + drawBar(integrity, 100, 20, intColor)));

        player.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════</gold>"));

        // Warning at low integrity
        if (integrity <= 20 && integrity > 0) {
            player.sendMessage(MessageUtil.parse("<red>⚠ WARNING: Structure integrity critically low!</red>"));
        }
        if (stress >= 100) {
            player.sendMessage(MessageUtil.parse("<red>⚠ WARNING: Structure is overloaded! Integrity is degrading!</red>"));
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

            // Stress decay
            if (data.stress > 0) {
                data.stress = Math.max(0, data.stress - stressDecayPerTick);
            }

            if (data.stress >= integrityRecoveryThreshold) {
                // Stress на 100% — degradation растёт экспоненциально
                data.stressFullTicks++;
                double exponentFactor = Math.exp(degradationExponentMultiplier * data.stressFullTicks / 20.0); // time in seconds
                data.degradation = baseDegradationRate * exponentFactor;

                // Integrity падает
                data.integrity = Math.max(0, data.integrity - data.degradation);

                // Взрыв при 0%
                if (data.integrity <= 0) {
                    explodeStructure(loc);
                    toRemove.add(key);
                    continue;
                }
            } else {
                // Stress < 100% — degradation затухает
                data.stressFullTicks = 0;
                data.degradation = Math.max(0, data.degradation - 0.5);
                if (data.degradation < 0.01) data.degradation = 0.0;

                // Integrity восстанавливается
                if (data.integrity < 100.0) {
                    data.integrity = Math.min(100.0, data.integrity + integrityRecoveryPerTick);
                }
            }
        }

        // Очищаем удалённые блоки
        for (String key : toRemove) {
            dataMap.remove(key);
        }
    }

    // =========================
    // EXPLOSION
    // =========================
    private void explodeStructure(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        // Удаляем блок
        loc.getBlock().setType(Material.AIR);

        // Взрыв (сила как у rate-limit)
        world.createExplosion(loc, 10.0f, false, true);

        // Урон игрокам рядом
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(loc) <= 8) {
                player.damage(20.0);
            }
        }

        ConsoleLogger.warn("[StructureIntegrity] Ender chest at "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " in " + world.getName() + " exploded due to 0% integrity!");
    }

    // =========================
    // DATA PERSISTENCE (JSON-like, simple format)
    // =========================
    private String locationToKey(Location loc) {
        return loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
    }

    private static Location keyToLocation(String key, World fallbackWorld) {
        String[] parts = key.split("\\|");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) world = fallbackWorld;
        return new Location(world,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }

    private File getDataFile() {
        return new File(plugin.getDataFolder(), "structure_integrity.dat");
    }

    public void saveData() {
        File file = getDataFile();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            Map<String, double[]> saveMap = new HashMap<>();
            for (Map.Entry<String, StructureData> entry : dataMap.entrySet()) {
                StructureData d = entry.getValue();
                saveMap.put(entry.getKey(), new double[]{
                        d.stress, d.integrity, d.degradation, d.stressFullTicks
                });
            }
            oos.writeObject(saveMap);
            oos.flush();
        } catch (IOException e) {
            ConsoleLogger.warn("[StructureIntegrity] Failed to save data: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = getDataFile();
        if (!file.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof Map) {
                Map<String, double[]> loadMap = (Map<String, double[]>) obj;
                for (Map.Entry<String, double[]> entry : loadMap.entrySet()) {
                    Location loc = keyToLocation(entry.getKey(), Bukkit.getWorlds().get(0));
                    if (loc == null) continue;
                    StructureData data = new StructureData(loc);
                    double[] vals = entry.getValue();
                    data.stress = vals[0];
                    data.integrity = vals[1];
                    data.degradation = vals[2];
                    data.stressFullTicks = (int) vals[3];
                    dataMap.put(entry.getKey(), data);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            ConsoleLogger.warn("[StructureIntegrity] Failed to load data: " + e.getMessage());
        }
    }

    // =========================
    // UTILITY
    // =========================
    private String drawBar(double current, double max, int segments, String color) {
        int filled = (int) Math.round((current / max) * segments);
        filled = Math.max(0, Math.min(segments, filled));
        StringBuilder sb = new StringBuilder(color);
        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? "█" : "░");
        }
        sb.append("</" + color.substring(1)); // close color tag
        return sb.toString();
    }

    private String parseMiniMessageStatus(StructureData data) {
        if (data.getIntegrity() <= 0) return "<red><bold>DESTROYED</bold></red>";
        if (data.getStress() >= 100.0) return "<red><bold>OVERLOADED</bold></red>";
        if (data.getStress() >= 75.0) return "<yellow><bold>HIGH STRESS</bold></yellow>";
        if (data.getDegradation() > 0.1) return "<gold><bold>DEGRADING</bold></gold>";
        if (data.getStress() > 0) return "<green>STRESSED</green>";
        return "<dark_green>STABLE</dark_green>";
    }

    public void shutdown() {
        saveData();
        initialized = false;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
