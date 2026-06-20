package com.mcplugin.features.containertrigger;

import com.mcplugin.Main;
import com.mcplugin.util.LocationUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

/**
 * Конфигурируемая система: если в контейнере (сундук, бочка и т.д.) на заданных
 * координатах есть предметы — указанные блоки начинают моргать заданным
 * BlockState-свойством с настраиваемой скоростью.
 * <p>
 * Когда контейнер пуст — блоки возвращаются в off-value состояние.
 */
public class ContainerTriggerManager extends BukkitRunnable {

    private static ContainerTriggerManager instance;

    // =========================
    // ⚙ КОНФИГУРАЦИЯ
    // =========================
    private static boolean enabled = true;
    private static int intervalTicks = 1;
    private static final List<TriggerConfig> triggers = new ArrayList<>();

    // =========================
    // TRIGGER CONFIG
    // =========================
    public static class TriggerConfig {
        public String name;
        public String sourceWorld;
        public int sourceX, sourceY, sourceZ;
        public List<TargetBlock> targetBlocks = new ArrayList<>();
        public String stateProperty;
        public String onValue;
        public String offValue;
        public double blinkRate; // blinks per second (e.g. 2.0 = 2 blinks/sec)
    }

    public static class TargetBlock {
        public String world;
        public int x, y, z;
    }

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        instance = new ContainerTriggerManager();
        reloadConfig();
        if (intervalTicks < 1) intervalTicks = 1;
        instance.runTaskTimer(plugin, 20L, intervalTicks);
        Main.getInstance().getLogger().info("[ContainerTrigger] Initialized with " + triggers.size() + " trigger(s).");
    }

    // =========================
    // RELOAD CONFIG
    // =========================
    @SuppressWarnings("unchecked")
    public static void reloadConfig() {
        // Cancel old runnable so intervalTicks changes take effect on next init
        if (instance != null) {
            try { instance.cancel(); } catch (Exception ignored) {}
        }

        triggers.clear();
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.container_trigger");
        if (cfg == null) {
            enabled = false;
            return;
        }

        enabled = cfg.getBoolean("enabled", true);
        intervalTicks = cfg.getInt("interval_ticks", 1);
        if (intervalTicks < 1) intervalTicks = 1;

        List<Map<?, ?>> triggersRaw = cfg.getMapList("triggers");
        if (triggersRaw == null || triggersRaw.isEmpty()) {
            Main.getInstance().getLogger().info("[ContainerTrigger] No triggers configured.");
            return;
        }

        for (Map<?, ?> raw : triggersRaw) {
            try {
                TriggerConfig trigger = new TriggerConfig();
                trigger.name = strVal(raw, "name", "unnamed");

                Map<?, ?> source = (Map<?, ?>) raw.get("source");
                if (source == null) {
                    Main.getInstance().getLogger().warning("[ContainerTrigger] Trigger '" + trigger.name + "': missing 'source' section, skipping.");
                    continue;
                }
                trigger.sourceWorld = strVal(source, "world", "world");
                trigger.sourceX = intVal(source, "x");
                trigger.sourceY = intVal(source, "y");
                trigger.sourceZ = intVal(source, "z");

                List<Map<?, ?>> targetsRaw = (List<Map<?, ?>>) raw.get("target_blocks");
                if (targetsRaw == null || targetsRaw.isEmpty()) {
                    Main.getInstance().getLogger().warning("[ContainerTrigger] Trigger '" + trigger.name + "': missing 'target_blocks', skipping.");
                    continue;
                }
                for (Map<?, ?> t : targetsRaw) {
                    TargetBlock tb = new TargetBlock();
                    tb.world = strVal(t, "world", trigger.sourceWorld);
                    tb.x = intVal(t, "x");
                    tb.y = intVal(t, "y");
                    tb.z = intVal(t, "z");
                    trigger.targetBlocks.add(tb);
                }

                trigger.stateProperty = strVal(raw, "state_property", "lit").toLowerCase(Locale.ROOT);
                trigger.onValue = strVal(raw, "on_value", "true");
                trigger.offValue = strVal(raw, "off_value", "false");
                trigger.blinkRate = dblVal(raw, "blink_rate", 2.0);
                if (trigger.blinkRate <= 0) trigger.blinkRate = 2.0;

                triggers.add(trigger);
                Main.getInstance().getLogger().info("[ContainerTrigger] Loaded trigger '" + trigger.name
                        + "': source=" + trigger.sourceWorld + " " + trigger.sourceX + "," + trigger.sourceY + "," + trigger.sourceZ
                        + " targets=" + trigger.targetBlocks.size()
                        + " blink_rate=" + trigger.blinkRate + "/s");
            } catch (Exception e) {
                Main.getInstance().getLogger().log(Level.WARNING, "[ContainerTrigger] Error parsing trigger: " + e.getMessage(), e);
            }
        }

        // Restart runnable with potentially new intervalTicks
        instance = new ContainerTriggerManager();
        if (intervalTicks < 1) intervalTicks = 1;
        instance.runTaskTimer(Main.getInstance(), 20L, intervalTicks);
    }

    // =========================
    // CONFIG VALUE HELPERS (avoid Map<?, ?> capture issues)
    // =========================
    private static String strVal(Map<?, ?> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : def;
    }

    private static int intVal(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); }
            catch (NumberFormatException e) { /* fall through */ }
        }
        return 0;
    }

    private static double dblVal(Map<?, ?> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try { return Double.parseDouble(val.toString()); }
            catch (NumberFormatException e) { /* fall through */ }
        }
        return def;
    }

    // =========================
    // RUN (периодическая задача)
    // =========================
    @Override
    public void run() {
        if (!enabled || triggers.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (TriggerConfig trigger : triggers) {
            try {
                World sourceWorld = Bukkit.getWorld(trigger.sourceWorld);
                if (sourceWorld == null) continue;

                Location sourceLoc = LocationUtil.normalize(
                        new Location(sourceWorld, trigger.sourceX, trigger.sourceY, trigger.sourceZ));
                if (sourceLoc == null) continue;

                // Проверка загрузки чанка
                if (!sourceWorld.isChunkLoaded(sourceLoc.getBlockX() >> 4, sourceLoc.getBlockZ() >> 4)) continue;

                Block sourceBlock = sourceLoc.getBlock();
                boolean hasItems = checkContainerHasItems(sourceBlock);

                applyBlinkState(trigger, now, hasItems);
            } catch (Exception e) {
                Main.getInstance().getLogger().log(Level.WARNING,
                        "[ContainerTrigger] Error processing trigger '" + trigger.name + "': " + e.getMessage());
            }
        }
    }

    // =========================
    // CHECK CONTAINER HAS ITEMS
    // =========================
    private static boolean checkContainerHasItems(Block block) {
        if (block.getState() instanceof Container container) {
            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    return true;
                }
            }
        }
        return false;
    }

    // =========================
    // APPLY BLINK STATE
    // =========================
    private static void applyBlinkState(TriggerConfig trigger, long nowMs, boolean hasItems) {
        // Determine if each target block should be ON or OFF
        String targetValue;
        if (!hasItems) {
            // Empty container — force off state, no blinking
            targetValue = trigger.offValue;
        } else {
            // Has items — calculate blink phase using fixed-point arithmetic to avoid drift
            // Scale period by 1000 to avoid floating-point rounding in modulo
            long scaledPeriod = (long) (1000.0 * 1000.0 / trigger.blinkRate);     // period in microseconds
            long scaledHalf = scaledPeriod / 2;                                     // half-period
            long scaledPhase = (nowMs * 1000L) % scaledPeriod;                      // current phase in microseconds
            boolean isOnPhase = scaledPhase < scaledHalf;
            targetValue = isOnPhase ? trigger.onValue : trigger.offValue;
        }

        for (TargetBlock target : trigger.targetBlocks) {
            try {
                World targetWorld = Bukkit.getWorld(target.world);
                if (targetWorld == null) continue;

                Location targetLoc = LocationUtil.normalize(
                        new Location(targetWorld, target.x, target.y, target.z));
                if (targetLoc == null) continue;

                if (!targetWorld.isChunkLoaded(targetLoc.getBlockX() >> 4, targetLoc.getBlockZ() >> 4)) continue;

                Block targetBlock = targetLoc.getBlock();
                setBlockStateProperty(targetBlock, trigger.stateProperty, targetValue);
            } catch (Exception e) {
                // Skip individual block errors to not break the loop
            }
        }
    }

    // =========================
    // SET BLOCK STATE PROPERTY BY NAME
    // =========================
    private static void setBlockStateProperty(Block block, String property, String value) {
        BlockData data = block.getBlockData();
        String dataStr = data.getAsString();

        // Формат: "minecraft:block_type[prop1=val1,prop2=val2]"
        // Ищем "property=currentValue" и заменяем на "property=newValue"
        // property уже приведён к lowercase при загрузке конфига
        String searchPattern = property + "=";
        int propStart = dataStr.indexOf(searchPattern);
        if (propStart == -1) {
            // Property not found on this block type — skip
            return;
        }

        int valueStart = propStart + searchPattern.length();
        int valueEnd = valueStart;
        while (valueEnd < dataStr.length()
                && dataStr.charAt(valueEnd) != ','
                && dataStr.charAt(valueEnd) != ']') {
            valueEnd++;
        }

        String currentValue = dataStr.substring(valueStart, valueEnd);
        if (currentValue.equals(value)) {
            // Already at target value — no update needed (avoids unnecessary block updates)
            return;
        }

        String newDataStr = dataStr.substring(0, valueStart) + value + dataStr.substring(valueEnd);

        try {
            BlockData newData = Bukkit.createBlockData(newDataStr);
            block.setBlockData(newData, false); // false = без физического обновления
        } catch (Exception e) {
            // Invalid block data string — block type may not support this property/value
        }
    }

    // =========================
    // GETTERS
    // =========================
    public static boolean isEnabled() { return enabled; }
    public static int getTriggerCount() { return triggers.size(); }
    public static List<TriggerConfig> getTriggers() { return Collections.unmodifiableList(triggers); }
}
