package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
 * 🛡 Integrity System
 * <p>
 * Replaces Minecraft's vanilla durability with a custom percentage-based
 * integrity system. All durable items become unbreakable through vanilla
 * mechanics, and integrity is displayed in the item lore.
 * <p>
 * Integrity is stored as a percentage (0.0 — 100.0).
 * When an item is used (mining blocks, attacking, armor taking damage),
 * its integrity decreases. At 0 the item breaks.
 * <p>
 * The lore shows ONLY the percentage — no numeric durability values.
 */
public class IntegrityManager extends BukkitRunnable {

    private static IntegrityManager instance;

    // ===== CONSTANTS =====
    /** Integrity system version for detecting migration of old PDC data (V3 = percentage system) */
    private static final int INTEGRITY_VERSION = 3;

    // ===== SETTINGS (loaded from config.yml) =====
    private static boolean enabled = true;
    private static int intervalTicks = 10;
    private static double costMultiplier = 1.0;

    // ===== HEX GRADIENT (from dark-green to dark-red) =====
    private static int gradientRedHigh = 0x00;     // R at 100% integrity (dark-green)
    private static int gradientGreenHigh = 0x66;    // G at 100%
    private static int gradientBlueHigh = 0x00;     // B at 100%
    private static int gradientRedLow = 0x99;       // R at 0% integrity (dark-red)
    private static int gradientGreenLow = 0x00;     // G at 0%
    private static int gradientBlueLow = 0x00;      // B at 0%

    // Lore text (stored both as plain and colored)
    private static String loreText = "<i:false><gray>Целостность:</gray>";
    private static String bareLorePrefix = "Целостность:";

    // Break behavior
    private static boolean breakPlaySound = true;
    private static boolean breakSendMessage = true;
    private static String breakMessage = "<dark_red>❌</dark_red> <red>Ваш предмет</red> <white>%item%</white> <red>сломался!</red>";
    private static String breakSoundName = "ENTITY_ITEM_BREAK";
    private static float breakSoundVolume = 1.0f;
    private static float breakSoundPitch = 1.0f;

    // Logging
    private static boolean logInit = false;
    private static boolean logBreak = true;
    private static boolean logErrors = false;

    // Filters
    private static Set<String> blacklist = new HashSet<>();
    private static Set<String> whitelist = new HashSet<>();

    // ===== XP → INTEGRITY (collecting XP restores integrity of all items) =====
    private static boolean xpIntegrityEnabled = true;
    private static double xpIntegrityPerXp = 0.1;
    private static String xpIntegrityMessage = "<green>✨</green> <white>Сбор опыта восстановил</white> <yellow>%amount%%</yellow> <white>целостности всех предметов!</white>";

    // ===== LOW INTEGRITY WARNING =====
    private static boolean lowIntegrityWarningEnabled = true;
    private static List<Integer> lowIntegrityThresholds = List.of(5, 10, 25, 50, 75);
    private static String lowIntegrityWarningMessage = "<yellow>⚠</yellow> <white>Ваш предмет</white> <yellow>%item%</yellow> <white>имеет</white> <red>%pct%%</red> <white>целостности!</white>";

    // ===== ADDITIONAL SETTINGS (wear, repair, etc.) =====
    // Anvil repair
    private static boolean anvilRepairEnabled = true;
    private static double anvilRepairMultiplier = 0.25;
    private static boolean anvilCombineEnabled = true;
    private static double anvilCombineBonus = 0.1;

    // Crafting with materials in an anvil (+N% integrity per material unit)
    private static boolean anvilMaterialCraftEnabled = true;
    private static double anvilMaterialCraftBonus = 10.0;
    private static String anvilMaterialCraftMessage = "<green>🔨</green> <white>Создан новый предмет! Целостность:</white> <yellow>%current%%</yellow> <white>(+%bonus%% за материалы)</white>";

    // XP + Mending
    private static boolean mendingXpEnabled = true;
    private static double mendingXpMultiplier = 0.5;

    // Unbreaking
    private static boolean unbreakingEnabled = true;

    // ===== PIERCING =====
    // When the attacker uses a weapon with the PIERCING enchantment,
    // the target's armor loses an additional +piercingExtraCost% integrity per hit.
    // Armor is NOT bypassed — protection works as usual.
    // Unbreaking is applied to the final cost (not ignored).
    private static boolean piercingEnabled = true;
    private static double piercingExtraCost = 0.5;

    // Flag: the current armor hit was caused by a PIERCING weapon
    // Reset at the start of the next tick (run())
    private static boolean piercingActive = false;

    // Flag: the task was scheduled (runTaskTimer was called)
    // Prevents cancel() of an unscheduled task in reloadConfig() during init()
    private static boolean taskScheduled = false;

    // Crafting / grindstone — combining
    private static boolean combineEnabled = true;
    private static double combineLossRate = 0.0;

    // Messages
    private static String anvilRepairMessage = "<green>🔧</green> <white>Целостность восстановлена до</white> <yellow>%current%%</yellow><white>!</white>";
    private static String anvilCombineMessage = "<green>🔗</green> <white>Предметы объединены! Целостность:</white> <yellow>%current%%</yellow><white></white>";
    private static String mendingMessage = "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>%amount%%</yellow> <white>целостности!</white>";

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

        // Register the PiercingListener (handler for PIERCING hits)
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

        // Number format — always percentages now, legacy settings are ignored

        // ===== HEX GRADIENT (loaded from config) =====
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

        // Lore text
        loreText = cfg.getString("lore_text", "<gray>Целостность:</gray>");
        bareLorePrefix = MessageUtil.toPlainText(loreText).trim();

        // Break behavior
        var onBreak = cfg.getConfigurationSection("on_break");
        if (onBreak != null) {
            breakPlaySound = onBreak.getBoolean("play_sound", true);
            breakSendMessage = onBreak.getBoolean("send_message", true);
            breakMessage = MessagesManager.getString("features.integrity.on_break.message", "<dark_red>❌</dark_red> <red>Ваш предмет</red> <white>%item%</white> <red>сломался!</red>");
            breakSoundName = onBreak.getString("sound", "ENTITY_ITEM_BREAK");
            breakSoundVolume = (float) onBreak.getDouble("sound_volume", 1.0);
            breakSoundPitch = (float) onBreak.getDouble("sound_pitch", 1.0);
        }

        // Logging
        var logging = cfg.getConfigurationSection("logging");
        if (logging != null) {
            logInit = logging.getBoolean("log_init", false);
            logBreak = logging.getBoolean("log_break", true);
            logErrors = logging.getBoolean("log_errors", false);
        }

        // Filters
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
            lowIntegrityWarningMessage = MessagesManager.getString("features.integrity.low_integrity_warning.message", "<yellow>⚠</yellow> <white>Ваш предмет</white> <yellow>%item%</yellow> <white>имеет</white> <red>%pct%%</red> <white>целостности!</white>");
        }

        // ===== XP → INTEGRITY =====
        var xpInt = cfg.getConfigurationSection("xp_integrity");
        if (xpInt != null) {
            xpIntegrityEnabled = xpInt.getBoolean("enabled", true);
            xpIntegrityPerXp = xpInt.getDouble("integrity_per_xp", 0.1);
            xpIntegrityMessage = MessagesManager.getString("features.integrity.xp_integrity.message", "<green>✨</green> <white>Сбор опыта восстановил</white> <yellow>%amount%%</yellow> <white>целостности всех предметов!</white>");
        }

        // ===== ANVIL REPAIR =====
        var anvil = cfg.getConfigurationSection("anvil_repair");
        if (anvil != null) {
            anvilRepairEnabled = anvil.getBoolean("enabled", true);
            anvilRepairMultiplier = anvil.getDouble("integrity_multiplier", 0.25);
            anvilCombineEnabled = anvil.getBoolean("combine_enabled", true);
            anvilCombineBonus = anvil.getDouble("combine_bonus", 0.1);
            anvilRepairMessage = MessagesManager.getString("features.integrity.anvil_repair.repair_message", "<green>🔧</green> <white>Целостность восстановлена до</white> <yellow>%current%%</yellow><white>!</white>");
            anvilCombineMessage = MessagesManager.getString("features.integrity.anvil_repair.combine_message", "<green>🔗</green> <white>Предметы объединены! Целостность:</white> <yellow>%current%%</yellow><white></white>");

            // ===== MATERIAL CRAFTING =====
            var matCraft = anvil.getConfigurationSection("material_craft");
            if (matCraft != null) {
                anvilMaterialCraftEnabled = matCraft.getBoolean("enabled", true);
                anvilMaterialCraftBonus = matCraft.getDouble("integrity_per_material", 10.0);
                anvilMaterialCraftMessage = MessagesManager.getString("features.integrity.anvil_repair.material_craft.message", "<green>🔨</green> <white>Создан новый предмет! Целостность:</white> <yellow>%current%%</yellow> <white>(+%bonus%% за материалы)</white>");
            }
        }

        // ===== XP + MENDING =====
        var mending = cfg.getConfigurationSection("mending_xp");
        if (mending != null) {
            mendingXpEnabled = mending.getBoolean("enabled", true);
            mendingXpMultiplier = mending.getDouble("integrity_multiplier", 0.5);
            mendingMessage = MessagesManager.getString("features.integrity.mending_xp.message", "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>%amount%%</yellow> <white>целостности!</white>");
        } else {
            // Fallback: legacy silk_touch_xp key (for backward compatibility)
            var stxp = cfg.getConfigurationSection("silk_touch_xp");
            if (stxp != null) {
                mendingXpEnabled = stxp.getBoolean("enabled", true);
                mendingXpMultiplier = stxp.getDouble("integrity_multiplier", 0.5);
                mendingMessage = MessagesManager.getString("features.integrity.silk_touch_message", "<aqua>✨</aqua> <white>Починка восстановила</white> <yellow>%amount%%</yellow> <white>целостности!</white>");
            }
        }

        // ===== ITEM COMBINING =====
        var combine = cfg.getConfigurationSection("combine");
        if (combine != null) {
            combineEnabled = combine.getBoolean("enabled", true);
            combineLossRate = combine.getDouble("loss_rate", 0.0);
        }

        // Restart the task — only if it was already scheduled (protects against init())
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
    // SYNC VANILLA DAMAGE — always resets vanilla damage.
    // Integrity is stored ONLY in PDC. Vanilla damage = 0
    // so the item never loses vanilla durability.
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
    // HEX GRADIENT — smooth gradient from dark-green (100%) to dark-red (0%)
    // =========================

    /**
     * Parses a HEX string (#RRGGBB) and returns an [R, G, B] array or null on error.
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
     * Computes the HEX gradient color for the given integrity percentage.
     * 100% = dark-green (highColor), 0% = dark-red (lowColor).
     * Returns a MiniMessage HEX tag: {@code <#RRGGBB>}
     */
    public static String getGradientColor(double pct) {
        double t = Math.max(0.0, Math.min(1.0, pct / 100.0));
        
        // Linear RGB interpolation
        int r = (int) Math.round(gradientRedLow + (gradientRedHigh - gradientRedLow) * t);
        int g = (int) Math.round(gradientGreenLow + (gradientGreenHigh - gradientGreenLow) * t);
        int b = (int) Math.round(gradientBlueLow + (gradientBlueHigh - gradientBlueLow) * t);
        
        // Clipping
        r = Math.max(0, Math.min(0xFF, r));
        g = Math.max(0, Math.min(0xFF, g));
        b = Math.max(0, Math.min(0xFF, b));
        
        // MiniMessage HEX format: <#RRGGBB>
        return String.format("<#%02X%02X%02X>", r, g, b);
    }

    // =========================
    // CONFIG GETTERS
    // =========================
    // ===== XP → INTEGRITY =====
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

    // ===== MENDING XP =====
    public static boolean isMendingXpEnabled() { return mendingXpEnabled; }
    public static double getMendingXpMultiplier() { return mendingXpMultiplier; }
    public static String getMendingMessage() { return mendingMessage; }

    // ===== PIERCING =====
    public static boolean isPiercingEnabled() { return piercingEnabled; }
    public static double getPiercingExtraCost() { return piercingExtraCost; }

    /**
     * Sets the flag that the current armor hit was caused by a PIERCING weapon.
     * The flag is reset at the start of each tick (run()).
     */
    public static void setPiercingActive(boolean active) { piercingActive = active; }

    /** Checks whether PIERCING is active for the current hit. */
    private static boolean isPiercingActive() { return piercingActive; }

    // =========================
    // TICK — scanning inventories
    // =========================
    @Override
    public void run() {
        if (!enabled) return;

        // Reset the PIERCING flag at the start of each tick
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
    // PROCESS ITEM — initialization + lore update
    // =========================
    private void processItem(ItemStack item) {
        // Unbreakable items always have 100% integrity
        if (isUnbreakable(item)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                var pdc = meta.getPersistentDataContainer();
                boolean alreadyTagged = pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE);
                pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
                pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
                pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
                pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 100.0);
                syncVanillaDamage(item, meta, 100.0);
                // Optimization: rewrite meta only if the lore actually changed
                // (or the item was not initialized yet), not every tick.
                if (updateLore(meta) || !alreadyTagged) {
                    item.setItemMeta(meta);
                }
            }
            return;
        }

        int maxDurability = getMaxDurability(item);
        if (maxDurability <= 0) return;

        // Filter check
        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return;
        if (blacklist.contains(matName)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        boolean isTagged = pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE);

        double itemMaxDura = (double) maxDurability;

        // Migration: if an old INTEGRITY_TAG exists but no INTEGRITY_MAX — re-initialize
        if (isTagged && !pdc.has(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE)) {
            isTagged = false;
        }

        // Migration detection: check the system version stored in PDC
        int storedVersion = pdc.getOrDefault(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, 0);
        boolean migrated = false;
        if (isTagged && storedVersion < INTEGRITY_VERSION) {
            double oldMax = pdc.getOrDefault(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 0.0);
            double oldCurrent = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);
            double newCurrent;

            if (oldMax == 100.0) {
                // V1 data: already percentages (max=100.0), just bump the version
                newCurrent = Math.max(0, Math.min(100.0, oldCurrent));
            } else if (oldMax > 0) {
                // V2 data: absolute values (max=durability) → convert to percentages
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
            // Initialization: reset vanilla damage to 0 and set 100% integrity.
            // During migration, all items with partial durability become fully intact
            // since wear is now managed only by the integrity system.
            double initialCurrent = 100.0;

            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
            pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
            pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
            pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, initialCurrent);
            // Mirror into vanilla damage — a backup copy
            syncVanillaDamage(item, meta, initialCurrent);

            if (logInit) {
                ConsoleLogger.info("[INTEGRITY] Initialized " + item.getType()
                        + " with max=" + (int)itemMaxDura + " integrity (current="
                        + String.format("%.1f%%", initialCurrent) + ")");
            }
        }

        // Update the lore; apply meta only if the lore changed.
        // If there was a migration — always save meta (to not lose PDC data)
        if (updateLore(meta) || migrated) {
            item.setItemMeta(meta);
        }
    }

    // =========================
    // UPDATE LORE — updates the item lore (returns true if the lore changed)
    // Shows ONLY the integrity percentage, no numeric durability values.
    // =========================
    private static boolean updateLore(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();

        // Unbreakable — show "◆ Unbreakable" instead of the percentage
        if (meta.isUnbreakable() || pdc.has(Keys.INTEGRITY_UNBREAKABLE, PersistentDataType.BYTE)) {
            // Optimization: lore already shows "◆ Unbreakable" — no rewrite needed
            if (pdc.getOrDefault(Keys.INTEGRITY_LAST_SEEN, PersistentDataType.DOUBLE, -1.0) == 100.0
                    && loreHasUnbreakableLine(meta)) {
                return false;
            }

            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            // Remove old integrity lines
            lore.removeIf(line -> plain(line).contains(bareLorePrefix));
            // Add "◆ Unbreakable"
            lore.add(MessageUtil.parse(loreText + " <aqua>◆ Unbreakable</aqua>"));
            meta.lore(lore);
            pdc.set(Keys.INTEGRITY_LAST_SEEN, PersistentDataType.DOUBLE, 100.0);
            return true;
        }

        double maxIntegrity = pdc.getOrDefault(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 0.0);
        double currentIntegrity = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);

        if (maxIntegrity <= 0) return false;

        // Optimization: integrity unchanged AND lore already shows the percentage —
        // skip. The lore content check ensures that after removing
        // "Unbreakable" at 100% the percentage line is restored immediately.
        if (pdc.getOrDefault(Keys.INTEGRITY_LAST_SEEN, PersistentDataType.DOUBLE, -1.0) == currentIntegrity
                && loreHasPercentLine(meta)) {
            return false; // Displayed data is up to date — no update needed
        }

        // Compute the percentage (0.0 — 100.0, with fractional part)
        double pct = (currentIntegrity / maxIntegrity) * 100.0;
        pct = Math.max(0, Math.min(100.0, pct));

        // Work with the lore
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        // Remove old integrity lines (by plain prefix, ignoring formatting)
        lore.removeIf(line -> plain(line).contains(bareLorePrefix));

        // Smooth HEX gradient from dark-green (100%) to dark-red (0%)
        String color = getGradientColor(pct);

        // Format the percentage: 75.500, 100.000, 0.000
        String pctStr = PCT_FMT.format(pct);

        // Build the lore line
        // Example: <gray>Integrity:</gray> <#006600>75.500%
        lore.add(MessageUtil.parse(loreText + " " + color + pctStr + "%"));
        meta.lore(lore);
        pdc.set(Keys.INTEGRITY_LAST_SEEN, PersistentDataType.DOUBLE, currentIntegrity);

        return true;
    }

    /**
     * Forcefully updates the integrity lore on an item.
     * Used when an item's integrity changes outside the tick (e.g. in an anvil).
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
     * Serializes a lore component to plain text (strips all formatting).
     */
    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * true if the lore already contains an integrity line with a percentage (not "◆ Unbreakable").
     * Used to avoid rewriting meta when the displayed data is up to date.
     */
    private static boolean loreHasPercentLine(ItemMeta meta) {
        if (!meta.hasLore() || meta.lore() == null) return false;
        for (Component line : meta.lore()) {
            String clean = plain(line);
            if (clean.contains(bareLorePrefix) && clean.contains("%") && !clean.contains("◆")) {
                return true;
            }
        }
        return false;
    }

    /**
     * true if the lore already contains a "◆ Unbreakable" line (instead of a percentage).
     */
    private static boolean loreHasUnbreakableLine(ItemMeta meta) {
        if (!meta.hasLore() || meta.lore() == null) return false;
        for (Component line : meta.lore()) {
            String clean = plain(line);
            if (clean.contains(bareLorePrefix) && clean.contains("◆")) {
                return true;
            }
        }
        return false;
    }

    // =========================
    // ENSURE INITIALIZED — guarantees the item is initialized
    // =========================
    public static void ensureInitialized(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isItemApplicable(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) return;            // Initialization is always 100.0% integrity, vanilla damage gets reset
            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
        pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
        pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 100.0);

        syncVanillaDamage(item, meta, 100.0);

        item.setItemMeta(meta);
    }

    // =========================
    // INCREASE INTEGRITY — increases integrity (capped at 100.0%)
    // =========================
    public static void increaseIntegrity(ItemStack item, double amount) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isItemApplicable(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        // If not initialized — do NOT repair (the item will be initialized in processItem)
        if (!pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) {
            return;
        }

        double maxIntegrity = 100.0;

        double current = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);

        if (current >= maxIntegrity) return; // Already at maximum

        double newVal = Math.min(maxIntegrity, current + amount);

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, newVal);

        // Mirror into vanilla damage
        syncVanillaDamage(item, meta, newVal);

        item.setItemMeta(meta);
    }

    // =========================
    // SET CURRENT INTEGRITY — sets the current integrity directly (percentage 0.0 — 100.0)
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

        // Optimization: value unchanged — don't rewrite meta
        double current = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);
        if (current == clamped) return;

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, clamped);
        syncVanillaDamage(item, meta, clamped);
        item.setItemMeta(meta);
    }

    // =========================
    // CHECK UNBREAKABLE — checks whether the item has an unbreakable tag
    // =========================
    public static boolean isUnbreakable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        // Custom PDC tag OR vanilla Unbreakable (ItemMeta.isUnbreakable())
        if (meta.isUnbreakable()) return true;
        return meta.getPersistentDataContainer()
                .has(Keys.INTEGRITY_UNBREAKABLE, PersistentDataType.BYTE);
    }

    // =========================
    // DECREASE INTEGRITY — decreases an item's integrity
    // =========================
    public static void decreaseIntegrity(ItemStack item, double amount, Player owner) {
        // Unbreakable items never lose integrity
        if (isUnbreakable(item)) return;

        if (item == null || item.getType() == Material.AIR) return;

        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return;
        if (blacklist.contains(matName)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        // If the item is not initialized yet — initialize it with 100% integrity
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

        // Normalized formula: (amount / maxDura) × 100% × costMultiplier × amount
        // The amount multiplier gives a quadratic relationship: the stronger the hit → the more wear.
        // For tools (amount=1 when mining a block) behavior is unchanged.
        // For armor: amount is proportional to incoming damage (≈ originalDamage / 4).
        double cost = (amount / (double) maxDura) * 100.0 * costMultiplier * amount;

        // ⚔ PIERCING: adds +piercingExtraCost% to the armor's integrity cost
        if (piercingEnabled && isPiercingActive()) {
            cost += piercingExtraCost;
        }

        // 🔮 Unbreaking: the chance to spend durability is reduced by (level + 1) times
        // E.g. Unbreaking I = x2 less chance, Unbreaking II = x3, Unbreaking III = x4, etc.
        if (unbreakingEnabled) {
            int unbreakingLevel = item.getEnchantmentLevel(Enchantment.UNBREAKING);
            if (unbreakingLevel > 0) {
                double divisor = unbreakingLevel + 1.0;
                if (Math.random() > 1.0 / divisor) {
                    // Lucky — durability is not spent
                    return;
                }
            }
        }

        double newVal = Math.max(0, current - cost);

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, newVal);
        syncVanillaDamage(item, meta, newVal);
        item.setItemMeta(meta);

        // If integrity ran out — break the item
        if (newVal <= 0) {
            breakItem(item, owner);
        } else {
            // Otherwise check warning thresholds
            checkLowIntegrityWarning(item, owner);
        }
    }

    // =========================
    // DECREASE INTEGRITY BY PERCENT — decreases by exactly N% integrity
    // (no durability normalization: direct percentage, no Unbreaking)
    // =========================
    public static void decreaseIntegrityPercent(ItemStack item, double percent, Player owner) {
        if (item == null || item.getType() == Material.AIR) return;
        if (percent <= 0) return;
        if (isUnbreakable(item)) return;

        String matName = item.getType().name();
        if (!whitelist.isEmpty() && !whitelist.contains(matName)) return;
        if (blacklist.contains(matName)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        // If the item is not initialized yet — initialize it with 100% integrity
        if (!pdc.has(Keys.INTEGRITY_TAG, PersistentDataType.BYTE)) {
            pdc.set(Keys.INTEGRITY_TAG, PersistentDataType.BYTE, (byte) 1);
            pdc.set(Keys.INTEGRITY_VERSION, PersistentDataType.INTEGER, INTEGRITY_VERSION);
            pdc.set(Keys.INTEGRITY_MAX, PersistentDataType.DOUBLE, 100.0);
            pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 100.0);
        }

        double current = pdc.getOrDefault(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, 0.0);
        if (current <= 0) return;

        double newVal = Math.max(0, current - percent);

        pdc.set(Keys.INTEGRITY_CURRENT, PersistentDataType.DOUBLE, newVal);
        syncVanillaDamage(item, meta, newVal);
        item.setItemMeta(meta);

        // If integrity ran out — break the item
        if (newVal <= 0) {
            breakItem(item, owner);
        } else {
            checkLowIntegrityWarning(item, owner);
        }
    }

    // =========================
    // LOW INTEGRITY WARNING — warns about low integrity
    // Each threshold (75,50,25,10,5%) fires once until the next repair
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

            // If integrity ≤ threshold AND the flag is not set yet — warn
            if (pct <= threshold && (warnFlags & bit) == 0) {
                warnFlags |= bit;
                warned = true;
            }

            // If integrity > threshold AND the flag is set — clear it (item was repaired)
            if (pct > threshold && (warnFlags & bit) != 0) {
                warnFlags &= ~bit;
            }
        }

        // On the first scan (warnFlags == 0) pre-set flags for thresholds
        // above the current integrity — to avoid spamming about "skipped" thresholds.
        // Example: an item at 30% → 75% and 50% are immediately marked as "already warned"
        int prevFlags = pdc.getOrDefault(Keys.INTEGRITY_WARN_FLAGS, PersistentDataType.INTEGER, 0);
        if (prevFlags == 0 && warnFlags > 0) {
            warned = false; // don't send a message on first initialization
        }

        // Save the flags to PDC
        int oldFlags = pdc.getOrDefault(Keys.INTEGRITY_WARN_FLAGS, PersistentDataType.INTEGER, 0);
        if (warned || warnFlags != oldFlags) {
            pdc.set(Keys.INTEGRITY_WARN_FLAGS, PersistentDataType.INTEGER, warnFlags);
            item.setItemMeta(meta);

            if (warned) {
                String itemName = getItemName(item);
                String msg = lowIntegrityWarningMessage
                        .replace("%item%", itemName)
                        .replace("%pct%", PCT_FMT.format(pct));
                player.sendMessage(MessageUtil.parse(msg));
            }
        }
    }

    // =========================
    // BREAK ITEM — breaks an item
    // =========================
    private static void breakItem(ItemStack item, Player owner) {
        // Get the name BEFORE setAmount(0), otherwise the item becomes AIR
        String itemName = getItemName(item);

        // Set the amount to 0 (item disappears) — only after getting the name
        item.setAmount(0);

        if (owner == null) return;
        // Note: owner should now always be passed from the caller context (decreaseIntegrity, etc.)
        // IMPORTANT: callers always pass a Player — removed the expensive O(n²) fallback

        // Play the break sound
        if (breakPlaySound) {
            Sound sound = getSound(breakSoundName, Sound.ENTITY_ITEM_BREAK);
            owner.getWorld().playSound(owner.getLocation(), sound, breakSoundVolume, breakSoundPitch);
        }

        // Send the message
        if (breakSendMessage) {
            String msg = breakMessage.replace("%item%", itemName);
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
     * Returns the default max integrity for an item.
     * Equals the vanilla maxDurability. Returns 0 if no item is given.
     * @deprecated Use {@link #getMaxIntegrity(ItemStack)} instead of a constant.
     */
    @Deprecated
    public static double getMaxIntegrityConstant() {
        return 100.0;
    }
}
