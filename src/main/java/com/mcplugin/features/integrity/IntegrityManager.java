package com.mcplugin.features.integrity;

import com.mcplugin.Keys;
import com.mcplugin.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 🛡 Integrity System (Система целостности)
 * <p>
 * Заменяет стандартную прочность Minecraft на кастомную систему целостности
 * в процентах. Все предметы с прочностью становятся неразрушимыми
 * через ванильную механику, а целостность отображается в описании (lore).
 * <p>
 * Целостность хранится в виде процентов (0.0 — 100.0).
 * При использовании предмета (ломка блоков, атака, получение урона в броне)
 * целостность уменьшается. При достижении 0 предмет ломается.
 * <p>
 * В лоре отображается ТОЛЬКО процент, никаких числовых значений прочности.
 */
public class IntegrityManager extends BukkitRunnable {

    private static IntegrityManager instance;

    // ===== КОНСТАНТЫ =====
    /** Максимальная целостность — всегда 100.0% */
    private static final double MAX_INTEGRITY = 100.0;

    // ===== НАСТРОЙКИ (загружаются из config.yml) =====
    private static boolean enabled = true;
    private static int intervalTicks = 10;
    private static double costMultiplier = 1.0;

    // ===== HEX ГРАДИЕНТ (от тёмно-зелёного к тёмно-красному) =====
    private static int gradientRedHigh = 0x00;     // R при 100% целостности (тёмно-зелёный)
    private static int gradientGreenHigh = 0x66;    // G при 100%
    private static int gradientBlueHigh = 0x00;     // B при 100%
    private static int gradientRedLow = 0x99;       // R при 0% целостности (тёмно-красный)
    private static int gradientGreenLow = 0x00;     // G при 0%
    private static int gradientBlueLow = 0x00;      // B при 0%

    // Текст лора (храним как plain и цветной)
    private static String loreText = "§7Целостность:";
    private static String bareLorePrefix = "Целостность:";

    // Поведение при поломке
    private static boolean breakPlaySound = true;
    private static boolean breakSendMessage = true;
    private static String breakMessage = "§4✖ §cВаш предмет §f{item} §cсломался!";
    private static String breakSoundName = "ENTITY_ITEM_BREAK";
    private static float breakSoundVolume = 1.0f;
    private static float breakSoundPitch = 1.0f;

    // Логирование
    private static boolean logInit = false;
    private static boolean logBreak = true;
    private static boolean logErrors = false;

    // Фильтры
    private static Set<String> blacklist = new HashSet<>();
    private static Set<String> whitelist = new HashSet<>();

    // ===== ДОП. НАСТРОЙКИ (износа, ремонта и т.д.) =====
    // Ремонт в наковальне
    private static boolean anvilRepairEnabled = true;
    private static double anvilRepairMultiplier = 0.25;
    private static boolean anvilCombineEnabled = true;
    private static double anvilCombineBonus = 0.1;

    // XP + Silk Touch
    private static boolean silkTouchXpEnabled = true;
    private static double silkTouchXpMultiplier = 0.5;

    // Крафт / точило — объединение
    private static boolean combineEnabled = true;
    private static double combineLossRate = 0.0;

    // Сообщения
    private static String anvilRepairMessage = "§a🔧 §fЦелостность восстановлена до §e{current}%§f!";
    private static String anvilCombineMessage = "§a🔗 §fПредметы объединены! Целостность: §e{current}%§f";
    private static String silkTouchMessage = "§b✨ §fШёлковое касание восстановило §e{amount}%§f целостности!";

    private static final DecimalFormat PCT_FMT = new DecimalFormat("0.000");

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        if (instance != null) {
            instance.cancel();
        }
        instance = new IntegrityManager();
        reloadConfig();
        instance.runTaskTimer(plugin, 40L, intervalTicks);
        Main.getInstance().getLogger().info("[INTEGRITY] System initialized (interval=" + intervalTicks + " ticks)");
    }

    // =========================
    // RELOAD
    // =========================
    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.integrity");
        if (cfg == null) {
            enabled = false;
            return;
        }

        enabled = cfg.getBoolean("enabled", true);
        intervalTicks = cfg.getInt("interval_ticks", 10);
        costMultiplier = cfg.getDouble("cost_multiplier", 1.0);

        // Формат чисел — теперь всегда проценты, игнорируем старые настройки

        // ===== HEX ГРАДИЕНТ (загружаем из конфига) =====
        var gradient = cfg.getConfigurationSection("gradient");
        if (gradient != null) {
            int[] high = parseHexColor(gradient.getString("high_color", "#006600"));
            int[] low = parseHexColor(gradient.getString("low_color", "#990000"));
            if (high != null) {
                gradientRedHigh = high[0];
                gradientGreenHigh = high[1];
                gradientBlueHigh = high[2];
            }
            if (low != null) {
                gradientRedLow = low[0];
                gradientGreenLow = low[1];
                gradientBlueLow = low[2];
            }
        }

        // Текст лора
        loreText = cfg.getString("lore_text", "§7Целостность:");
        bareLorePrefix = loreText.replaceAll("§.", "").trim();

        // Поведение при поломке
        var onBreak = cfg.getConfigurationSection("on_break");
        if (onBreak != null) {
            breakPlaySound = onBreak.getBoolean("play_sound", true);
            breakSendMessage = onBreak.getBoolean("send_message", true);
            breakMessage = onBreak.getString("message", "§4✖ §cВаш предмет §f{item} §cсломался!");
            breakSoundName = onBreak.getString("sound", "ENTITY_ITEM_BREAK");
            breakSoundVolume = (float) onBreak.getDouble("sound_volume", 1.0);
            breakSoundPitch = (float) onBreak.getDouble("sound_pitch", 1.0);
        }

        // Логирование
        var logging = cfg.getConfigurationSection("logging");
        if (logging != null) {
            logInit = logging.getBoolean("log_init", false);
            logBreak = logging.getBoolean("log_break", true);
            logErrors = logging.getBoolean("log_errors", false);
        }

        // Фильтры
        blacklist = new HashSet<>(cfg.getStringList("blacklist"));
        whitelist = new HashSet<>(cfg.getStringList("whitelist"));

        // ===== РЕМОНТ В НАКОВАЛЬНЕ =====
        var anvil = cfg.getConfigurationSection("anvil_repair");
        if (anvil != null) {
            anvilRepairEnabled = anvil.getBoolean("enabled", true);
            anvilRepairMultiplier = anvil.getDouble("integrity_multiplier", 0.25);
            anvilCombineEnabled = anvil.getBoolean("combine_enabled", true);
            anvilCombineBonus = anvil.getDouble("combine_bonus", 0.1);
            anvilRepairMessage = anvil.getString("repair_message", "§a🔧 §fЦелостность восстановлена до §e{current}%§f!");
            anvilCombineMessage = anvil.getString("combine_message", "§a🔗 §fПредметы объединены! Целостность: §e{current}%§f");
        }

        // ===== XP + ШЁЛКОВОЕ КАСАНИЕ =====
        var stxp = cfg.getConfigurationSection("silk_touch_xp");
        if (stxp != null) {
            silkTouchXpEnabled = stxp.getBoolean("enabled", true);
            silkTouchXpMultiplier = stxp.getDouble("integrity_multiplier", 0.5);
            silkTouchMessage = stxp.getString("message", "§b✨ §fШёлковое касание восстановило §e{amount}%§f целостности!");
        }

        // ===== ОБЪЕДИНЕНИЕ ПРЕДМЕТОВ =====
        var combine = cfg.getConfigurationSection("combine");
        if (combine != null) {
            combineEnabled = combine.getBoolean("enabled", true);
            combineLossRate = combine.getDouble("loss_rate", 0.0);
        }

        // Перезапуск таска
        if (instance != null) {
            try {
                instance.cancel();
                instance = new IntegrityManager();
                instance.runTaskTimer(Main.getInstance(), 40L, intervalTicks);
            } catch (Exception e) {
                Main.getInstance().getLogger().warning("[INTEGRITY] Failed to restart task: " + e.getMessage());
            }
        }
    }

    public static IntegrityManager getInstance() {
        return instance;
    }

    public static double getCostMultiplier() {
        return costMultiplier;
    }

    public static String formatPercent(double value) {
        return PCT_FMT.format(value);
    }

    // =========================
    // HEX GRADIENT — плавный градиент от тёмно-зелёного (100%) до тёмно-красного (0%)
    // =========================

    /**
     * Парсит HEX строку (#RRGGBB) и возвращает массив [R, G, B] или null при ошибке.
     */
    private static int[] parseHexColor(String hex) {
        try {
            String clean = hex.replace("#", "").trim();
            if (clean.length() == 6) {
                return new int[]{
                    Integer.parseInt(clean.substring(0, 2), 16),
                    Integer.parseInt(clean.substring(2, 4), 16),
                    Integer.parseInt(clean.substring(4, 6), 16)
                };
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Вычисляет цвет HEX градиента для указанного процента целостности.
     * 100% = тёмно-зелёный (highColor), 0% = тёмно-красный (lowColor).
     * Возвращает Minecraft HEX-формат: §x§R§R§G§G§B§B
     */
    public static String getGradientColor(double pct) {
        double t = Math.max(0.0, Math.min(1.0, pct / 100.0));
        
        // Линейная интерполяция RGB
        int r = (int) Math.round(gradientRedLow + (gradientRedHigh - gradientRedLow) * t);
        int g = (int) Math.round(gradientGreenLow + (gradientGreenHigh - gradientGreenLow) * t);
        int b = (int) Math.round(gradientBlueLow + (gradientBlueHigh - gradientBlueLow) * t);
        
        // Клиппинг
        r = Math.max(0, Math.min(0xFF, r));
        g = Math.max(0, Math.min(0xFF, g));
        b = Math.max(0, Math.min(0xFF, b));
        
        // Minecraft HEX формат: §x§R§R§G§G§B§B
        return String.format("§x§%X§%X§%X§%X§%X§%X",
                (r >> 4) & 0xF, r & 0xF,
                (g >> 4) & 0xF, g & 0xF,
                (b >> 4) & 0xF, b & 0xF);
    }

    // =========================
    // GETTERS ДЛЯ КОНФИГА
    // =========================
    public static boolean isAnvilRepairEnabled() { return anvilRepairEnabled; }
    public static boolean isAnvilCombineEnabled() { return anvilCombineEnabled; }
    public static boolean isSilkTouchXpEnabled() { return silkTouchXpEnabled; }
    public static boolean isCombineEnabled() { return combineEnabled; }
    public static double getAnvilRepairMultiplier() { return anvilRepairMultiplier; }
    public static double getAnvilCombineBonus() { return anvilCombineBonus; }
    public static double getSilkTouchXpMultiplier() { return silkTouchXpMultiplier; }
    public static double getCombineLossRate() { return combineLossRate; }
    public static String getAnvilRepairMessage() { return anvilRepairMessage; }
    public static String getAnvilCombineMessage() { return anvilCombineMessage; }
    public static String getSilkTouchMessage() { return silkTouchMessage; }

    // =========================
    // TICK — сканирование инвентарей
    // =========================
    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();

            for (int i = 0; i <= 40; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() == Material.AIR) continue;

                try {
                    processItem(item);
                } catch (Exception e) {
                    if (logErrors) {
                        Main.getInstance().getLogger().warning("[INTEGRITY] Error processing item " + item.getType() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    // =========================
    // PROCESS ITEM — инициализация + обновление лора
    // =========================
    private void processItem(ItemStack item) {
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return;

        // Проверка фильтров
        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return;
        if (blacklist.contains(matName)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        boolean isTagged = pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE);

        // Миграция: если есть старый INTEGRITY_TAG, но нет INTEGRITY_MAX — переинициализируем
        if (isTagged && !pdc.has(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE)) {
            isTagged = false;
        }

        if (!isTagged) {
            // Инициализация нового предмета — целостность всегда 100.0%
            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
            pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, MAX_INTEGRITY);
            pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, MAX_INTEGRITY);

            if (meta instanceof Damageable damageable) {
                damageable.setDamage(0);
            }

            if (logInit) {
                Main.getInstance().getLogger().info("[INTEGRITY] Initialized " + item.getType() + " with 100.0% integrity");
            }
        } else {
            // Миграция старых данных: если max не 100.0 — переустанавливаем
            double oldMax = pdc.getOrDefault(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 0.0);
            if (oldMax != MAX_INTEGRITY) {
                double oldCurrent = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);
                // Конвертируем старые значения в проценты
                double newCurrent = (oldMax > 0) ? (oldCurrent / oldMax) * MAX_INTEGRITY : MAX_INTEGRITY;
                pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, MAX_INTEGRITY);
                pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, Math.max(0, Math.min(MAX_INTEGRITY, newCurrent)));
            }

            // Сбрасываем ванильный damage
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(0);
            }
        }

        // Обновляем лор; применяем meta только если лор изменился
        if (updateLore(meta)) {
            item.setItemMeta(meta);
        }
    }

    // =========================
    // UPDATE LORE — обновление описания (возвращает true, если лор был изменён)
    // Показывает ТОЛЬКО процент целостности, никаких числовых значений прочности.
    // =========================
    private boolean updateLore(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();

        double maxIntegrity = pdc.getOrDefault(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 0.0);
        double currentIntegrity = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);

        if (maxIntegrity <= 0) return false;

        // Оптимизация: проверяем, изменилась ли целостность с прошлого раза
        if (pdc.has(Keys.INTEGRITY_LAST_SEEN, PersistentDataType.DOUBLE)) {
            double lastSeen = pdc.get(Keys.INTEGRITY_LAST_SEEN, PersistentDataType.DOUBLE);
            if (lastSeen == currentIntegrity) {
                return false; // Целостность не изменилась — пропускаем обновление
            }
        }

        // Вычисляем процент (0.0 — 100.0, с дробной частью)
        double pct = (currentIntegrity / maxIntegrity) * 100.0;
        pct = Math.max(0, Math.min(100.0, pct));

        // Работаем с лором
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        // Удаляем старые строки целостности (по plain-префиксу, без §-цветов)
        lore.removeIf(line -> stripColor(line).contains(bareLorePrefix));

        // Плавный HEX градиент от тёмно-зелёного (100%) до тёмно-красного (0%)
        String color = getGradientColor(pct);

        // Форматируем процент: 75.500, 100.000, 0.000
        String pctStr = PCT_FMT.format(pct);

        // Собираем строку лора
        // Пример: §7Целостность: §x§0§0§6§6§0§075.500%
        StringBuilder sb = new StringBuilder();
        sb.append(loreText).append(" ");
        sb.append(color).append(pctStr).append("%");

        lore.add(sb.toString());
        meta.setLore(lore);
        pdc.set(Keys.INTEGRITY_LAST_SEEN, PersistentDataType.DOUBLE, currentIntegrity);

        return true;
    }

    /**
     * Удаляет все §-цвета из строки
     */
    private static String stripColor(String input) {
        return input.replaceAll("§.", "");
    }

    // =========================
    // ENSURE INITIALIZED — гарантирует, что предмет инициализирован
    // =========================
    public static void ensureInitialized(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isItemApplicable(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) return;

        // Инициализация — всегда 100.0%
        pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
        pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, MAX_INTEGRITY);
        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, MAX_INTEGRITY);

        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
        }

        item.setItemMeta(meta);
    }

    // =========================
    // INCREASE INTEGRITY — увеличивает целостность (с капом на 100.0%)
    // =========================
    public static void increaseIntegrity(ItemStack item, double amount) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isItemApplicable(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        // Если не инициализирован — инициализируем
        if (!pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) {
            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
            pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, MAX_INTEGRITY);
            pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, MAX_INTEGRITY);

            if (meta instanceof Damageable damageable) {
                damageable.setDamage(0);
            }
            item.setItemMeta(meta);
            return;
        }

        double current = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);

        if (current >= MAX_INTEGRITY) return; // Уже на максимуме

        double newVal = Math.min(MAX_INTEGRITY, current + amount);

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, newVal);

        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
        }

        item.setItemMeta(meta);
    }

    // =========================
    // SET CURRENT INTEGRITY — устанавливает текущую целостность напрямую (в процентах 0.0 — 100.0)
    // =========================
    public static void setCurrentIntegrity(ItemStack item, double value) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isItemApplicable(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) return;

        double clamped = Math.max(0, Math.min(MAX_INTEGRITY, value));

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, clamped);

        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
        }
        item.setItemMeta(meta);
    }

    // =========================
    // DECREASE INTEGRITY — уменьшение целостности предмета
    // =========================
    public static void decreaseIntegrity(ItemStack item, double amount, Player owner) {
        if (item == null || item.getType() == Material.AIR) return;

        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return;
        if (blacklist.contains(matName)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        // Если предмет ещё не инициализирован — инициализируем
        if (!pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) {
            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
            pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, MAX_INTEGRITY);
            pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, MAX_INTEGRITY);
        }

        double current = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);

        if (current <= 0) return;

        // Конвертируем ванильный урон в проценты целостности
        int maxDura = item.getType().getMaxDurability();
        if (maxDura <= 0) return;

        // 1 единица ванильного урона = (1 / maxDurability) * 100% от целостности
        double pctCost = (amount / (double) maxDura) * MAX_INTEGRITY * costMultiplier;
        double newVal = Math.max(0, current - pctCost);

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, newVal);

        // Сбрасываем ванильный damage
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
        }

        item.setItemMeta(meta);

        // Если целостность закончилась — ломаем предмет
        if (newVal <= 0) {
            breakItem(item, owner);
        }
    }

    // =========================
    // BREAK ITEM — ломание предмета
    // =========================
    private static void breakItem(ItemStack item, Player owner) {
        // Устанавливаем количество 0 (предмет исчезает)
        item.setAmount(0);

        if (owner == null) {
            // Fallback: ищем владельца по инвентарям
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (ItemStack invItem : player.getInventory().getContents()) {
                    if (invItem == item) {
                        owner = player;
                        break;
                    }
                }
                if (owner != null) break;
            }
        }

        if (owner == null) return;

        String itemName = getItemName(item);

        // Воспроизводим звук поломки
        if (breakPlaySound) {
            Sound sound = getSound(breakSoundName, Sound.ENTITY_ITEM_BREAK);
            owner.getWorld().playSound(owner.getLocation(), sound, breakSoundVolume, breakSoundPitch);
        }

        // Отправляем сообщение
        if (breakSendMessage) {
            String msg = breakMessage.replace("{item}", itemName);
            owner.sendMessage(msg);
        }

        if (logBreak) {
            Main.getInstance().getLogger().info("[INTEGRITY] " + owner.getName() + "'s " + itemName + " broke!");
        }
    }

    private static Sound getSound(String name, Sound fallback) {
        try {
            return Sound.valueOf(name);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String name = item.getType().name().toLowerCase().replace("_", " ");
        if (name.length() > 0) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }

    // =========================
    // FILTER CHECK
    // =========================
    private static boolean isItemApplicable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType().getMaxDurability() <= 0) return false;

        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return false;
        return !blacklist.contains(matName);
    }

    // =========================
    // UTILITY METHODS
    // =========================
    public static boolean hasIntegrity(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)
                && pdc.has(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE);
    }

    public static double getCurrentIntegrity(ItemStack item) {
        if (!hasIntegrity(item)) return -1;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);
    }

    public static double getMaxIntegrity(ItemStack item) {
        if (!hasIntegrity(item)) return -1;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, MAX_INTEGRITY);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * @return константа MAX_INTEGRITY (100.0)
     */
    public static double getMaxIntegrityConstant() {
        return MAX_INTEGRITY;
    }
}
