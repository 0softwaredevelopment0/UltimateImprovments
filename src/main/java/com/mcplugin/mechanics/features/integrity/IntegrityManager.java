package com.mcplugin.mechanics.features.integrity;

import com.mcplugin.core.Keys;
import com.mcplugin.core.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.SoundUtil;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
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
    /** Версия системы целостности для детекта миграции старых PDC данных (V3 = процентная система) */
    private static final int INTEGRITY_VERSION = 3;

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
    private static String breakMessage = "<dark_red>❌</dark_red> <red>Ваш предмет</red> <white>{item}</white> <red>сломался!</red>";
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

    // ===== XP → ЦЕЛОСТНОСТЬ (сбор опыта восстанавливает целостность всех предметов) =====
    private static boolean xpIntegrityEnabled = true;
    private static double xpIntegrityPerXp = 0.1;
    private static String xpIntegrityMessage = "<green>✨</green> <white>Сбор опыта восстановил</white> <yellow>{amount}%</yellow> <white>целостности всех предметов!</white>";

    // ===== LOW INTEGRITY WARNING — предупреждение при низкой целостности =====
    private static boolean lowIntegrityWarningEnabled = true;
    private static List<Integer> lowIntegrityThresholds = List.of(5, 10, 25, 50, 75);
    private static String lowIntegrityWarningMessage = "<yellow>⚠</yellow> <white>Ваш предмет</white> <yellow>{item}</yellow> <white>имеет</white> <red>{pct}%</red> <white>целостности!</white>";

    // ===== ДОП. НАСТРОЙКИ (износа, ремонта и т.д.) =====
    // Ремонт в наковальне
    private static boolean anvilRepairEnabled = true;
    private static double anvilRepairMultiplier = 0.25;
    private static boolean anvilCombineEnabled = true;
    private static double anvilCombineBonus = 0.1;

    // Крафт материалом в наковальне (+N% целостности за каждую единицу материала)
    private static boolean anvilMaterialCraftEnabled = true;
    private static double anvilMaterialCraftBonus = 10.0;
    private static String anvilMaterialCraftMessage = "<green>🔨</green> <white>Создан новый предмет! Целостность:</white> <yellow>{current}%</yellow> <white>(+{bonus}% за материалы)</white>";

    // XP + Mending (Починка)
    private static boolean mendingXpEnabled = true;
    private static double mendingXpMultiplier = 0.5;

    // Unbreaking (Неразрушимость)
    private static boolean unbreakingEnabled = true;

    // ===== PIERCING (Пробитие) =====
    // Когда атакующий использует оружие с зачарованием PIERCING,
    // броня цели получает +piercingExtraCost% целостности при ударе.
    // Броня НЕ игнорируется — защита работает как обычно.
    // Unbreaking проверяется на итоговую стоимость (не игнорируется).
    private static boolean piercingEnabled = true;
    private static double piercingExtraCost = 0.5;

    // Флаг: текущий удар по броне вызван PIERCING-оружием
    // Сбрасывается при старте следующего тика (run())
    private static boolean piercingActive = false;

    // Флаг: таск был запланирован (runTaskTimer вызывался)
    // Предотвращает cancel() незапланированного таска в reloadConfig() при init()
    private static boolean taskScheduled = false;

    // Крафт / точило — объединение
    private static boolean combineEnabled = true;
    private static double combineLossRate = 0.0;

    // Сообщения
    private static String anvilRepairMessage = "<green>🔧</green> <white>Целостность восстановлена до</white> <yellow>{current}%</yellow><white>!</white>";
    private static String anvilCombineMessage = "<green>🔗</green> <white>Предметы объединены! Целостность:</white> <yellow>{current}%</yellow><white></white>";
    private static String mendingMessage = "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>{amount}%</yellow> <white>целостности!</white>";

    private static final DecimalFormat PCT_FMT = new DecimalFormat("0.000");

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        if (instance != null) {
            instance.cancel();
        }
        instance = new IntegrityManager();
        taskScheduled = false;
        reloadConfig();
        instance.runTaskTimer(plugin, 40L, intervalTicks);
        taskScheduled = true;

        // Регистрируем PiercingListener (обработчик PIERCING-ударов)
        PiercingListener.init(plugin);

        ConsoleLogger.info("[INTEGRITY] System initialized (interval=" + intervalTicks + " ticks)");
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
            breakMessage = MessagesManager.getString("features.integrity.on_break.message", "<dark_red>❌</dark_red> <red>Ваш предмет</red> <white>{item}</white> <red>сломался!</red>");
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

        // ===== UNBREAKING =====
        unbreakingEnabled = cfg.getBoolean("unbreaking.enabled", true);

        // ===== PIERCING =====
        var piercingSection = cfg.getConfigurationSection("piercing");
        if (piercingSection != null) {
            piercingEnabled = piercingSection.getBoolean("enabled", true);
            piercingExtraCost = piercingSection.getDouble("extra_integrity_cost", 0.5);
        } else {
            piercingEnabled = true;
            piercingExtraCost = 0.5;
        }

        // ===== LOW INTEGRITY WARNING =====
        var warn = cfg.getConfigurationSection("low_integrity_warning");
        if (warn != null) {
            lowIntegrityWarningEnabled = warn.getBoolean("enabled", true);
            lowIntegrityThresholds = warn.getIntegerList("thresholds");
            if (lowIntegrityThresholds.isEmpty()) {
                lowIntegrityThresholds = List.of(5, 10, 25, 50, 75);
            }
            lowIntegrityWarningMessage = MessagesManager.getString("features.integrity.low_integrity_warning.message", "<yellow>⚠</yellow> <white>Ваш предмет</white> <yellow>{item}</yellow> <white>имеет</white> <red>{pct}%</red> <white>целостности!</white>");
        }

        // ===== XP → ЦЕЛОСТНОСТЬ =====
        var xpInt = cfg.getConfigurationSection("xp_integrity");
        if (xpInt != null) {
            xpIntegrityEnabled = xpInt.getBoolean("enabled", true);
            xpIntegrityPerXp = xpInt.getDouble("integrity_per_xp", 0.1);
            xpIntegrityMessage = MessagesManager.getString("features.integrity.xp_integrity.message", "<green>✨</green> <white>Сбор опыта восстановил</white> <yellow>{amount}%</yellow> <white>целостности всех предметов!</white>");
        }

        // ===== РЕМОНТ В НАКОВАЛЬНЕ =====
        var anvil = cfg.getConfigurationSection("anvil_repair");
        if (anvil != null) {
            anvilRepairEnabled = anvil.getBoolean("enabled", true);
            anvilRepairMultiplier = anvil.getDouble("integrity_multiplier", 0.25);
            anvilCombineEnabled = anvil.getBoolean("combine_enabled", true);
            anvilCombineBonus = anvil.getDouble("combine_bonus", 0.1);
            anvilRepairMessage = MessagesManager.getString("features.integrity.anvil_repair.repair_message", "<green>🔧</green> <white>Целостность восстановлена до</white> <yellow>{current}%</yellow><white>!</white>");
            anvilCombineMessage = MessagesManager.getString("features.integrity.anvil_repair.combine_message", "<green>🔗</green> <white>Предметы объединены! Целостность:</white> <yellow>{current}%</yellow><white></white>");

            // ===== КРАФТ МАТЕРИАЛОМ =====
            var matCraft = anvil.getConfigurationSection("material_craft");
            if (matCraft != null) {
                anvilMaterialCraftEnabled = matCraft.getBoolean("enabled", true);
                anvilMaterialCraftBonus = matCraft.getDouble("integrity_per_material", 10.0);
                anvilMaterialCraftMessage = MessagesManager.getString("features.integrity.anvil_repair.material_craft.message", "<green>🔨</green> <white>Создан новый предмет! Целостность:</white> <yellow>{current}%</yellow> <white>(+{bonus}% за материалы)</white>");
            }
        }

        // ===== XP + MENDING (ПОЧИНКА) =====
        var mending = cfg.getConfigurationSection("mending_xp");
        if (mending != null) {
            mendingXpEnabled = mending.getBoolean("enabled", true);
            mendingXpMultiplier = mending.getDouble("integrity_multiplier", 0.5);
            mendingMessage = MessagesManager.getString("features.integrity.mending_xp.message", "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>{amount}%</yellow> <white>целостности!</white>");
        } else {
            // Fallback: старый ключ silk_touch_xp (для обратной совместимости)
            var stxp = cfg.getConfigurationSection("silk_touch_xp");
            if (stxp != null) {
                mendingXpEnabled = stxp.getBoolean("enabled", true);
                mendingXpMultiplier = stxp.getDouble("integrity_multiplier", 0.5);
                mendingMessage = MessagesManager.getString("features.integrity.silk_touch_message", "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>{amount}%</yellow> <white>целостности!</white>");
            }
        }

        // ===== ОБЪЕДИНЕНИЕ ПРЕДМЕТОВ =====
        var combine = cfg.getConfigurationSection("combine");
        if (combine != null) {
            combineEnabled = combine.getBoolean("enabled", true);
            combineLossRate = combine.getDouble("loss_rate", 0.0);
        }

        // Перезапуск таска — только если он уже был запланирован (защита от init())
        if (instance != null && taskScheduled) {
            try {
                instance.cancel();
                instance = new IntegrityManager();
                instance.runTaskTimer(Main.getInstance(), 40L, intervalTicks);
            } catch (Exception e) {
                ConsoleLogger.warn("[INTEGRITY] Failed to restart task: " + e.getMessage());
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
    // SYNC VANILLA DAMAGE — всегда обнуляет ванильный damage.
    // Целостность хранится ТОЛЬКО в PDC. Ванильный damage = 0,
    // чтобы предмет не терял ванильную прочность.
    // =========================
    private static void syncVanillaDamage(ItemStack item, ItemMeta meta, double currentIntegrity) {
        if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
            damageable.setDamage(0);
        }
    }

    /**
     * Returns the max durability for an item.
     * <p>
     * In Paper 1.21.4+, durability is a data component ({@code minecraft:max_damage}),
     * NOT a material property. Fresh items may have {@code getItemMeta()} return a
     * non-{@code Damageable} instance, and {@code Material.getMaxDurability()}
     * may return 0 (deprecated in favour of the component API).
     * <p>
     * Strategy (three-tier fallback):
     * <ol>
     *   <li>{@code Damageable.hasMaxDamage()} — for items that already have damage data</li>
     *   <li>{@code Material.getMaxDurability()} — legacy API, may return 0 in 1.21.4+</li>
     *   <li><b>NMS Fallback</b> — {@code CraftItemStack.asNMSCopy(item).getMaxDamage()}
     *       reads the max_damage data component directly from the NMS item stack.</li>
     * </ol>
     */
    public static int getMaxDurability(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;

        // 1) Check Damageable component (items that already have damage data)
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable dmg && dmg.hasMaxDamage()) {
            int componentMax = dmg.getMaxDamage();
            if (componentMax > 0) return componentMax;
        }

        // 2) Legacy Material.getMaxDurability() — may return 0 in 1.21.4+
        int matMax = item.getType().getMaxDurability();
        if (matMax > 0) return matMax;

        // 3) NMS Fallback — CraftItemStack.asNMSCopy().getMaxDamage()
        //    Paper 1.21.4+ stores max_damage as a data component.
        //    Damageable.hasMaxDamage() may return false for fresh/undamaged items
        //    (e.g. Mace, Trident), so we fall back to the NMS API.
        try {
            net.minecraft.world.item.ItemStack nmsStack = CraftItemStack.asNMSCopy(item);
            int nmsMax = nmsStack.getMaxDamage();
            if (nmsMax > 0) return nmsMax;
        } catch (Exception ignored) {
            // NMS not available or incompatible version — skip
        }

        return 0;
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
    // ===== XP → ЦЕЛОСТНОСТЬ =====
    public static boolean isXpIntegrityEnabled() { return xpIntegrityEnabled; }
    public static double getXpIntegrityPerXp() { return xpIntegrityPerXp; }
    public static String getXpIntegrityMessage() { return xpIntegrityMessage; }

    // ===== LOW INTEGRITY WARNING =====
    public static String getLowIntegrityWarningMessage() { return lowIntegrityWarningMessage; }

    public static boolean isAnvilRepairEnabled() { return anvilRepairEnabled; }
    public static boolean isAnvilMaterialCraftEnabled() { return anvilMaterialCraftEnabled; }
    public static double getAnvilMaterialCraftBonus() { return anvilMaterialCraftBonus; }
    public static String getAnvilMaterialCraftMessage() { return anvilMaterialCraftMessage; }
    public static boolean isAnvilCombineEnabled() { return anvilCombineEnabled; }
    @Deprecated public static boolean isSilkTouchXpEnabled() { return mendingXpEnabled; }
    public static boolean isCombineEnabled() { return combineEnabled; }
    public static double getAnvilRepairMultiplier() { return anvilRepairMultiplier; }
    public static double getAnvilCombineBonus() { return anvilCombineBonus; }
    @Deprecated public static double getSilkTouchXpMultiplier() { return mendingXpMultiplier; }
    public static double getCombineLossRate() { return combineLossRate; }
    public static String getAnvilRepairMessage() { return anvilRepairMessage; }
    public static String getAnvilCombineMessage() { return anvilCombineMessage; }
    @Deprecated public static String getSilkTouchMessage() { return mendingMessage; }

    // ===== MENDING XP (Починка) =====
    public static boolean isMendingXpEnabled() { return mendingXpEnabled; }
    public static double getMendingXpMultiplier() { return mendingXpMultiplier; }
    public static String getMendingMessage() { return mendingMessage; }

    // ===== PIERCING =====
    public static boolean isPiercingEnabled() { return piercingEnabled; }
    public static double getPiercingExtraCost() { return piercingExtraCost; }

    /**
     * Устанавливает флаг, что текущий удар по броне вызван PIERCING-оружием.
     * Флаг сбрасывается в начале каждого тика (run()).
     */
    public static void setPiercingActive(boolean active) { piercingActive = active; }

    /** Проверяет, активен ли PIERCING для текущего удара. */
    private static boolean isPiercingActive() { return piercingActive; }

    // =========================
    // TICK — сканирование инвентарей
    // =========================
    @Override
    public void run() {
        if (!enabled) return;

        // Сбрасываем флаг PIERCING в начале каждого тика
        piercingActive = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();

            for (int i = 0; i <= 40; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() == Material.AIR) continue;

                try {
                    processItem(item);
                    checkLowIntegrityWarning(item, player);
                } catch (Exception e) {
                    if (logErrors) {
                        ConsoleLogger.warn("[INTEGRITY] Error processing item " + item.getType() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    // =========================
    // PROCESS ITEM — инициализация + обновление лора
    // =========================
    private void processItem(ItemStack item) {
        // Unbreakable предметы всегда имеют 100% целостности
        if (isUnbreakable(item)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                var pdc = meta.getPersistentDataContainer();
                pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
                pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
                pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 100.0);
                syncVanillaDamage(item, meta, 100.0);
                updateLore(meta);
                item.setItemMeta(meta);
            }
            return;
        }

        int maxDurability = getMaxDurability(item);
        if (maxDurability <= 0) return;

        // Проверка фильтров
        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return;
        if (blacklist.contains(matName)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        boolean isTagged = pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE);

        double itemMaxDura = (double) maxDurability;

        // Миграция: если есть старый INTEGRITY_TAG, но нет INTEGRITY_MAX — переинициализируем
        if (isTagged && !pdc.has(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE)) {
            isTagged = false;
        }

        // Детект миграции: проверяем версию системы в PDC
        int storedVersion = pdc.getOrDefault(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, 0);
        boolean migrated = false;
        if (isTagged && storedVersion < INTEGRITY_VERSION) {
            double oldMax = pdc.getOrDefault(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 0.0);
            double oldCurrent = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);
            double newCurrent;

            if (oldMax == 100.0) {
                // V1 данные: уже в процентах (max=100.0), просто обновляем версию
                newCurrent = Math.max(0, Math.min(100.0, oldCurrent));
            } else if (oldMax > 0) {
                // V2 данные: абсолютные значения (max=durability) → конвертируем в проценты
                newCurrent = (oldCurrent / oldMax) * 100.0;
            } else {
                newCurrent = 100.0;
            }

            pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
            pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
            pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, Math.max(0, Math.min(100.0, newCurrent)));
            migrated = true;

            if (logInit) {
                ConsoleLogger.info("[INTEGRITY] Migrated to V3 " + item.getType()
                        + " (current=" + String.format("%.1f%%", newCurrent) + ")");
            }
        }

        if (!isTagged) {
            // Инициализация: сбрасываем ванильный damage в 0 и ставим 100% целостности.
            // При миграции все предметы с неполной прочностью становятся полностью целыми,
            // т.к. износ теперь управляется только системой целостности.
            double initialCurrent = 100.0;

            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
            pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
            pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
            pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, initialCurrent);
            // Зеркалим в ванильный damage — резервная копия
            syncVanillaDamage(item, meta, initialCurrent);

            if (logInit) {
                ConsoleLogger.info("[INTEGRITY] Initialized " + item.getType()
                        + " with max=" + (int)itemMaxDura + " integrity (current="
                        + String.format("%.1f%%", initialCurrent) + ")");
            }
        }

        // Обновляем лор; применяем meta только если лор изменился
        // Если была миграция — всегда сохраняем meta (чтобы не потерять PDC данные)
        if (updateLore(meta) || migrated) {
            item.setItemMeta(meta);
        }
    }

    // =========================
    // UPDATE LORE — обновление описания (возвращает true, если лор был изменён)
    // Показывает ТОЛЬКО процент целостности, никаких числовых значений прочности.
    // =========================
    private static boolean updateLore(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();

        // Unbreakable — показываем "◆ Unbreakable" вместо процента
        if (meta.isUnbreakable() || pdc.has(Keys.INTEGRITY_UNBREAKABLE, PersistentDataType.BYTE)) {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            if (lore == null) lore = new ArrayList<>();
            // Удаляем старые строки целостности
            lore.removeIf(line -> stripColor(line).contains(bareLorePrefix));
            // Добавляем "◆ Unbreakable"
            lore.add(loreText + " §b◆ Unbreakable");
            meta.setLore(lore);
            pdc.set(Keys.INTEGRITY_LAST_SEEN, PersistentDataType.DOUBLE, 100.0);
            return true;
        }

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
     * Принудительно обновляет лор целостности на предмете.
     * Используется когда предмет меняет целостность вне тика (например, в наковальне).
     */
    public static void updateItemLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (getMaxDurability(item) <= 0) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (updateLore(meta)) {
            item.setItemMeta(meta);
        }
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
        if (pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) return;            // Инициализация — всегда 100.0% целостности, ванильный damage сбрасывается
            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
        pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
        pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 100.0);

        syncVanillaDamage(item, meta, 100.0);

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

        // Если не инициализирован — НЕ чиним (предмет проинициализируется в processItem)
        if (!pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) {
            return;
        }

        double maxIntegrity = 100.0;

        double current = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);

        if (current >= maxIntegrity) return; // Уже на максимуме

        double newVal = Math.min(maxIntegrity, current + amount);

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, newVal);

        // Зеркалим в ванильный damage
        syncVanillaDamage(item, meta, newVal);

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

        double maxIntegrity = 100.0;
        double clamped = Math.max(0, Math.min(maxIntegrity, value));

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, clamped);
        syncVanillaDamage(item, meta, clamped);
        item.setItemMeta(meta);
    }

    // =========================
    // CHECK UNBREAKABLE — проверяет, есть ли у предмета тег неразрушимости
    // =========================
    public static boolean isUnbreakable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        // Кастомный PDC тег ИЛИ ванильный Unbreakable (ItemMeta.isUnbreakable())
        if (meta.isUnbreakable()) return true;
        return meta.getPersistentDataContainer()
                .has(Keys.INTEGRITY_UNBREAKABLE, PersistentDataType.BYTE);
    }

    // =========================
    // DECREASE INTEGRITY — уменьшение целостности предмета
    // =========================
    public static void decreaseIntegrity(ItemStack item, double amount, Player owner) {
        // Unbreakable предметы не теряют целостность
        if (isUnbreakable(item)) return;

        if (item == null || item.getType() == Material.AIR) return;

        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return;
        if (blacklist.contains(matName)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        // Если предмет ещё не инициализирован — инициализируем с 100% целостности
        if (!pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) {
            double initialCurrent = 100.0;

            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
            pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
            pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
            pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, initialCurrent);
        }

        double current = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);

        if (current <= 0) return;

        int maxDura = getMaxDurability(item);
        if (maxDura <= 0) return;

        // Нормированная формула: (amount / maxDura) × 100% × costMultiplier × amount
        // Множитель amount даёт квадратичную зависимость: чем сильнее удар → тем больше износ.
        // Для инструментов (amount=1 при ломке блока) поведение не меняется.
        // Для брони: amount пропорционален входящему урону (≈ originalDamage / 4).
        double cost = (amount / (double) maxDura) * 100.0 * costMultiplier * amount;

        // ⚔ PIERCING (Пробитие): добавляет +piercingExtraCost% к трате целостности брони
        if (piercingEnabled && isPiercingActive()) {
            cost += piercingExtraCost;
        }

        // 🔮 Unbreaking: шанс потратить прочность уменьшается в (уровень + 1) раз
        // Например: Unbreaking I = x2 меньше шанс, Unbreaking II = x3, Unbreaking III = x4 и т.д.
        if (unbreakingEnabled) {
            int unbreakingLevel = item.getEnchantmentLevel(Enchantment.UNBREAKING);
            if (unbreakingLevel > 0) {
                double divisor = unbreakingLevel + 1.0;
                if (Math.random() > 1.0 / divisor) {
                    // Удача — прочность не тратится
                    return;
                }
            }
        }

        double newVal = Math.max(0, current - cost);

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, newVal);
        syncVanillaDamage(item, meta, newVal);
        item.setItemMeta(meta);

        // Если целостность закончилась — ломаем предмет
        if (newVal <= 0) {
            breakItem(item, owner);
        } else {
            // Иначе проверяем пороги для предупреждения
            checkLowIntegrityWarning(item, owner);
        }
    }

    // =========================
    // LOW INTEGRITY WARNING — предупреждение при низкой целостности
    // Каждый порог (75,50,25,10,5%) срабатывает 1 раз до следующего ремонта
    // =========================
    private static void checkLowIntegrityWarning(ItemStack item, Player player) {
        if (!lowIntegrityWarningEnabled) return;
        if (item == null || item.getType() == Material.AIR) return;
        if (getMaxDurability(item) <= 0) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) return;

        double currentIntegrity = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);
        double maxIntegrity = pdc.getOrDefault(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
        if (maxIntegrity <= 0) return;
        double pct = (currentIntegrity / maxIntegrity) * 100.0;

        int warnFlags = pdc.getOrDefault(Keys.INTEGRITY_WARN_FLAGS, PersistentDataType.INTEGER, 0);
        boolean warned = false;

        for (int i = 0; i < lowIntegrityThresholds.size(); i++) {
            int threshold = lowIntegrityThresholds.get(i);
            int bit = 1 << i;

            // Если целостность ≤ порога И флаг ещё не стоит — предупреждаем
            if (pct <= threshold && (warnFlags & bit) == 0) {
                warnFlags |= bit;
                warned = true;
            }

            // Если целостность > порога И флаг стоит — снимаем (предмет починили)
            if (pct > threshold && (warnFlags & bit) != 0) {
                warnFlags &= ~bit;
            }
        }

        // При первом сканировании (warnFlags == 0) pre-set флаги для порогов
        // выше текущей целостности — чтобы не спамить за "пропущенные" пороги.
        // Например: предмет на 30% → 75% и 50% сразу помечаются как "уже предупреждено"
        int prevFlags = pdc.getOrDefault(Keys.INTEGRITY_WARN_FLAGS, PersistentDataType.INTEGER, 0);
        if (prevFlags == 0 && warnFlags > 0) {
            warned = false; // не шлём сообщение при первой инициализации
        }

        // Сохраняем флаги в PDC
        int oldFlags = pdc.getOrDefault(Keys.INTEGRITY_WARN_FLAGS, PersistentDataType.INTEGER, 0);
        if (warned || warnFlags != oldFlags) {
            pdc.set(Keys.INTEGRITY_WARN_FLAGS, PersistentDataType.INTEGER, warnFlags);
            item.setItemMeta(meta);

            if (warned) {
                String itemName = getItemName(item);
                String msg = lowIntegrityWarningMessage
                        .replace("{item}", itemName)
                        .replace("{pct}", PCT_FMT.format(pct));
                player.sendMessage(MessageUtil.parse(msg));
            }
        }
    }

    // =========================
    // BREAK ITEM — ломание предмета
    // =========================
    private static void breakItem(ItemStack item, Player owner) {
        // Получаем имя ДО setAmount(0), иначе item станет AIR
        String itemName = getItemName(item);

        // Устанавливаем количество 0 (предмет исчезает) — только после получения имени
        item.setAmount(0);

        if (owner == null) return;
        // Примечание: owner теперь всегда должен передаваться из контекста (decreaseIntegrity, etc.)
        // ВАЖНО: вызывающий код всегда передаёт Player — удаляем дорогой O(n²) fallback

        // Воспроизводим звук поломки
        if (breakPlaySound) {
            Sound sound = getSound(breakSoundName, Sound.ENTITY_ITEM_BREAK);
            owner.getWorld().playSound(owner.getLocation(), sound, breakSoundVolume, breakSoundPitch);
        }

        // Отправляем сообщение
        if (breakSendMessage) {
            String msg = breakMessage.replace("{item}", itemName);
            owner.sendMessage(MessageUtil.parse(msg));
        }

        if (logBreak) {
            ConsoleLogger.info("[INTEGRITY] " + owner.getName() + "'s " + itemName + " broke!");
        }
    }

    private static Sound getSound(String name, Sound fallback) {
        Sound sound = SoundUtil.getSound(name);
        return sound != null ? sound : fallback;
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
        if (getMaxDurability(item) <= 0) return false;

        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return false;
        return !blacklist.contains(matName);
    }

    // =========================
    // UTILITY METHODS
    // =========================
    public static boolean hasIntegrity(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        // In Paper 1.21.4+ hasItemMeta() returns false for fresh items.
        // getItemMeta() may return null for some item types.
        var meta = item.getItemMeta();
        if (meta == null) return false;
        var pdc = meta.getPersistentDataContainer();
        return pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)
                && pdc.has(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE);
    }

    public static double getCurrentIntegrity(ItemStack item) {
        if (!hasIntegrity(item)) return -1;
        var meta = item.getItemMeta();
        if (meta == null) return -1;
        return meta.getPersistentDataContainer()
                .getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);
    }

    public static double getMaxIntegrity(ItemStack item) {
        if (!hasIntegrity(item)) return -1;
        var meta = item.getItemMeta();
        if (meta == null) return -1;
        return meta.getPersistentDataContainer()
                .getOrDefault(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 0.0);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Возвращает макс. целостность для предмета по умолчанию.
     * Равна ванильной maxDurability. Если предмет не указан — возвращает 0.
     * @deprecated Используйте {@link #getMaxIntegrity(ItemStack)} вместо константы.
     */
    @Deprecated
    public static double getMaxIntegrityConstant() {
        return 100.0;
    }
}
