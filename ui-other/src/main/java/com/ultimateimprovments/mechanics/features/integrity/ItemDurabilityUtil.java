package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 🛡 ItemDurabilityUtil — thin durability helper built directly on top of
 * <b>vanilla Minecraft durability</b>.
 * <p>
 * The old custom integrity system (PDC counters + lore scanner + anvil/
 * mending interception) was removed: it fought the vanilla repair economy and
 * produced desync bugs (e.g. anvil repairs bypassing the XP cost). All state
 * now lives in the vanilla {@code damage} data component — anvil, grindstone
 * and Mending behave exactly like vanilla, with no possible bypass or
 * desync, and items can no longer break "twice".
 * <p>
 * What remains (all optional, config-gated):
 * <ul>
 *   <li><b>Integrity % lore</b> — a lore line computed from
 *       {@code 100% × (1 - damage / maxDamage)}, refreshed after every change.</li>
 *   <li><b>Unbreaking</b> — {@code features.integrity.unbreaking.enabled}:
 *       when enabled, custom wear passes through the vanilla (level + 1)
 *       chance roll; when disabled (default), Unbreaking applies only to
 *       vanilla damage, exactly like vanilla.</li>
 *   <li><b>Piercing</b> — {@code features.integrity.piercing.enabled}:
 *       arrows shot through blocks (PIERCING crossbow bolts) deal extra
 *       vanilla damage to the target's armor (PiercingListener).</li>
 * </ul>
 * <p>
 * Units are baked into the method names to avoid confusion:
 * <ul>
 *   <li>{@code setItemIntegrity} — sets an exact integrity % (0.0–100.0)</li>
 *   <li>{@code decreaseItemIntegrity} — applies N uses of vanilla damage</li>
 *   <li>{@code decreaseItemIntegrityPercent / increaseItemIntegrityPercent} —
 *       changes by exactly X%</li>
 * </ul>
 * All write methods return the <b>actual</b> integrity % (0.0–100.0)
 * <i>after</i> the operation — the source of truth for messages.
 */
public final class ItemDurabilityUtil {

    private ItemDurabilityUtil() {}

    // ===== SETTINGS (loaded from config.yml, features.integrity.*) =====
    private static boolean enabled = true;
    private static boolean unbreakingEnabled = false;
    private static boolean piercingEnabled = true;
    private static double piercingExtraCost = 0.5;
    private static boolean onBreakPlaySound = true;
    private static double onBreakSoundVolume = 2.0;
    private static double onBreakSoundPitch = 1.0;
    private static List<Integer> warnThresholds = new ArrayList<>();
    private static String loreText = "<i:false><white>Integrity:</white>";

    /** Bare (formatting-stripped) lore prefix used to find/replace the lore line. */
    private static String bareLorePrefix = "Integrity:";

    /** Marker of items migrated from the old integrity system (lore already removed). */
    private static NamespacedKey MIGRATED_TAG;

    private static final DecimalFormat FMT = new DecimalFormat("#.##");

    // =========================
    // INIT / CONFIG
    // =========================
    public static void init(Main plugin) {
        MIGRATED_TAG = new NamespacedKey(plugin, "integrity_migrated");
        reloadConfig();
    }

    public static void reloadConfig() {
        ConfigurationSection cfg = Main.getInstance().getConfig()
                .getConfigurationSection("features.integrity");
        if (cfg == null) return;

        enabled = cfg.getBoolean("enabled", true);
        unbreakingEnabled = cfg.getBoolean("unbreaking.enabled", false);
        piercingEnabled = cfg.getBoolean("piercing.enabled", true);
        piercingExtraCost = cfg.getDouble("piercing.extra_integrity_cost", 0.5);
        onBreakPlaySound = cfg.getBoolean("on_break.play_sound", true);
        onBreakSoundVolume = cfg.getDouble("on_break.sound_volume", 2.0);
        onBreakSoundPitch = cfg.getDouble("on_break.sound_pitch", 1.0);
        warnThresholds = cfg.getIntegerList("low_integrity_warning.thresholds");
        loreText = cfg.getString("lore_text", "<i:false><white>Integrity:</white>");
        bareLorePrefix = MessageUtil.toPlainText(loreText).trim();

        if (PiercingListener.isReloadPending()) {
            PiercingListener.setReloaded();
        }
    }

    public static boolean isEnabled() { return enabled; }
    public static boolean isUnbreakingEnabled() { return unbreakingEnabled; }
    public static boolean isPiercingEnabled() { return piercingEnabled; }
    public static double getPiercingExtraCost() { return piercingExtraCost; }
    public static String formatPercent(double value) { return FMT.format(value); }

    // =========================
    // VANILLA DURABILITY CORE
    // =========================

    /**
     * Max vanilla durability of the item (Paper 1.21.4+: the max_damage data
     * component with a Material fallback). 0 = the item has no durability.
     */
    public static int getMaxDurability(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable dmg && dmg.hasMaxDamage()) {
            int max = dmg.getMaxDamage();
            if (max > 0) return max;
        }
        return Math.max(0, item.getType().getMaxDurability());
    }

    /** Current vanilla damage (0..maxDamage). */
    public static int getVanillaDamage(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable dmg && dmg.hasDamage()) {
            return Math.max(0, dmg.getDamage());
        }
        return 0;
    }

    /** Items with zero max durability are outside the system entirely. */
    public static boolean hasItemIntegrity(ItemStack item) {
        return getMaxDurability(item) > 0;
    }

    /**
     * Integrity % of the item: {@code 100 × (1 - damage / maxDamage)}.
     * Items without durability report 100% (they can't wear down).
     */
    public static double getItemIntegrityPercent(ItemStack item) {
        int max = getMaxDurability(item);
        if (max <= 0) return 100.0;
        return Math.max(0.0, Math.min(100.0, 100.0 * (1.0 - (double) getVanillaDamage(item) / max)));
    }

    /** Max integrity is always 100% (kept for API compatibility with old call sites). */
    public static double getItemMaxIntegrityPercent(ItemStack item) {
        return getMaxDurability(item) > 0 ? 100.0 : -1;
    }

    /** No-op kept for API compatibility — vanilla items are always "initialized". */
    public static void initializeItemIntegrity(ItemStack item) {
        // Legacy migration: drop the old PDC integrity data + lore once.
        migrateLegacyItem(item);
    }

    // =========================
    // LEGACY MIGRATION
    // =========================

    /**
     * One-time conversion of items that lived in the old custom integrity
     * system: the PDC counters are removed and the integrity lore line is
     * stripped. The vanilla damage component already mirrors the old value
     * (the old system synced it), so no other conversion is needed.
     */
    private static void migrateLegacyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(Keys.INTEGRITY_TAG, org.bukkit.persistence.PersistentDataType.BYTE)
                && !pdc.has(MIGRATED_TAG, org.bukkit.persistence.PersistentDataType.BYTE)) {
            pdc.remove(Keys.INTEGRITY_TAG);
            pdc.remove(Keys.INTEGRITY_MAX);
            pdc.remove(Keys.INTEGRITY_CURRENT);
            pdc.remove(Keys.INTEGRITY_LAST_SEEN);
            pdc.remove(Keys.INTEGRITY_WARN_FLAGS);
            pdc.remove(Keys.INTEGRITY_VERSION);
            pdc.remove(Keys.INTEGRITY_UNBREAKABLE);
            pdc.set(MIGRATED_TAG, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            removeIntegrityLore(meta);
            item.setItemMeta(meta);
        }
    }

    // =========================
    // WRITE
    // =========================

    /** Sets the item to an exact integrity % (0.0–100.0). Returns the actual % after setting. */
    public static double setItemIntegrity(ItemStack item, double percent) {
        int max = getMaxDurability(item);
        if (max <= 0) return -1;
        double clamped = Math.max(0.0, Math.min(100.0, percent));
        int damage = (int) Math.round(max * (1.0 - clamped / 100.0));

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg)) return -1;
        dmg.setDamage(Math.max(0, Math.min(max, damage)));
        updateLoreLine(meta, clamped);
        item.setItemMeta(meta);

        if (clamped <= 0) breakItem(item);
        return clamped;
    }

    /**
     * Applies {@code iterations} uses of vanilla damage (1 iteration = 1
     * durability point by default, see the per-callsite cost tables).
     * Returns the actual integrity % after the deduction.
     */
    public static double decreaseItemIntegrity(ItemStack item, int iterations, Player owner) {
        if (item == null || iterations <= 0) return getItemIntegrityPercent(item);
        int max = getMaxDurability(item);
        if (max <= 0) return getItemIntegrityPercent(item);
        return decreaseItemIntegrityPercent(item, 100.0 * iterations / max, owner);
    }

    /** Increases integrity by as much as N uses would spend. Returns the actual % after repair. */
    public static double increaseItemIntegrity(ItemStack item, int iterations) {
        if (item == null || iterations <= 0) return getItemIntegrityPercent(item);
        int max = getMaxDurability(item);
        if (max <= 0) return getItemIntegrityPercent(item);
        return increaseItemIntegrityPercent(item, 100.0 * iterations / max);
    }

    /** Decreases integrity by exactly X% (double). At 0 the item breaks as usual. */
    public static double decreaseItemIntegrityPercent(ItemStack item, double percent, Player owner) {
        int max = getMaxDurability(item);
        if (max <= 0 || percent <= 0) return getItemIntegrityPercent(item);

        // Optional Unbreaking gate for CUSTOM wear (vanilla damage from normal
        // play is already covered by the vanilla Unbreaking roll).
        if (unbreakingEnabled && owner != null) {
            int level = item.getEnchantmentLevel(Enchantment.UNBREAKING);
            if (level > 0 && Math.random() > 1.0 / (level + 1.0)) {
                return getItemIntegrityPercent(item);
            }
        }

        int add = (int) Math.floor(max * percent / 100.0);
        if (add <= 0) return getItemIntegrityPercent(item);

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg)) return getItemIntegrityPercent(item);
        int before = dmg.hasDamage() ? dmg.getDamage() : 0;
        int after = Math.min(max, before + add);
        dmg.setDamage(after);
        double pct = Math.max(0.0, Math.min(100.0, 100.0 * (1.0 - (double) after / max)));
        updateLoreLine(meta, pct);
        item.setItemMeta(meta);

        if (after >= max) {
            breakItem(item);
        } else if (owner != null) {
            checkLowIntegrityWarning(item, owner, pct);
        }
        return pct;
    }

    /** Increases integrity by exactly X%. Returns the actual % after repair. */
    public static double increaseItemIntegrityPercent(ItemStack item, double percent) {
        int max = getMaxDurability(item);
        if (max <= 0 || percent <= 0) return getItemIntegrityPercent(item);

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable dmg)) return getItemIntegrityPercent(item);
        int before = dmg.hasDamage() ? dmg.getDamage() : 0;
        int after = Math.max(0, before - (int) Math.floor(max * percent / 100.0));
        dmg.setDamage(after);
        double pct = Math.max(0.0, Math.min(100.0, 100.0 * (1.0 - (double) after / max)));
        updateLoreLine(meta, pct);
        item.setItemMeta(meta);
        return pct;
    }

    // =========================
    // VANILLA UNBREAKABLE
    // =========================

    /** Whether the item is vanilla-unbreakable (minecraft:unbreakable component). */
    public static boolean isUnbreakable(ItemStack item) {
        return item != null && item.getItemMeta() != null && item.getItemMeta().isUnbreakable();
    }

    /**
     * Toggles vanilla unbreakable on the item and swaps the integrity lore
     * line for an "◆ Unbreakable" marker (or removes it).
     */
    public static void setVanillaUnbreakable(ItemStack item, boolean unbreakable) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setUnbreakable(unbreakable);
        if (unbreakable) {
            removeIntegrityLore(meta);
            var lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<net.kyori.adventure.text.Component>();
            lore.add(MessageUtil.parse(loreText + " <aqua>◆ Unbreakable</aqua>"));
            meta.lore(lore);
        } else {
            removeUnbreakableLoreLine(meta);
            updateLoreLine(meta, getItemIntegrityPercent(item));
        }
        item.setItemMeta(meta);
    }

    // =========================
    // BREAK
    // =========================

    private static void breakItem(ItemStack item) {
        int max = getMaxDurability(item);
        if (max > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable dmg) {
                dmg.setDamage(max);
                removeIntegrityLore(meta);
                item.setItemMeta(meta);
            }
        }
        if (onBreakPlaySound) {
            try {
                var world = Bukkit.getWorlds().get(0);
                world.playSound(world.getSpawnLocation(),
                        Sound.valueOf("ENTITY_ITEM_BREAK"),
                        org.bukkit.SoundCategory.PLAYERS,
                        (float) onBreakSoundVolume, (float) onBreakSoundPitch);
            } catch (Exception e) {
                ConsoleLogger.warn("[Durability] break sound error: " + e.getMessage());
            }
        }
    }

    // =========================
    // LOW INTEGRITY WARNING
    // =========================

    private static void checkLowIntegrityWarning(ItemStack item, Player owner, double pct) {
        if (warnThresholds == null || warnThresholds.isEmpty() || owner == null) return;
        for (int threshold : warnThresholds) {
            if (Math.abs(pct - threshold) < 0.01) {
                String msg = com.ultimateimprovments.config.MessagesManager.getString(
                        "features.integrity.low_integrity_warning.message",
                        "<yellow>⚠</yellow> <white>Your item</white> <yellow>%item%</yellow> <white>has</white> <red>%pct%%</red> <white>integrity remaining!</white>")
                        .replace("%item%", item.getType().name())
                        .replace("%pct%", FMT.format(pct));
                owner.sendMessage(MessageUtil.parse(msg));
                break;
            }
        }
    }

    // =========================
    // LORE
    // =========================

    /** Refreshes the integrity lore line right after a change. */
    public static void updateItemLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (getMaxDurability(item) <= 0) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (updateLoreLine(meta, getItemIntegrityPercent(item))) {
            item.setItemMeta(meta);
        }
    }

    private static boolean updateLoreLine(ItemMeta meta, double pct) {
        List<net.kyori.adventure.text.Component> lore =
                meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        String line = loreText + " " + integrityColor(pct) + FMT.format(pct) + "%";
        boolean found = false;
        for (int i = 0; i < lore.size(); i++) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(lore.get(i));
            if (plain.contains(bareLorePrefix)) {
                lore.set(i, MessageUtil.parse(line));
                found = true;
                break;
            }
        }
        if (!found) {
            lore.add(MessageUtil.parse(line));
        }
        if (loreEquals(meta.lore(), lore)) return false;
        meta.lore(lore);
        return true;
    }

    private static void removeIntegrityLore(ItemMeta meta) {
        if (!meta.hasLore() || meta.lore() == null) return;
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore());
        lore.removeIf(line -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line);
            return plain.contains(bareLorePrefix) && !plain.contains("◆");
        });
        if (lore.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(lore);
        }
    }

    private static void removeUnbreakableLoreLine(ItemMeta meta) {
        if (!meta.hasLore() || meta.lore() == null) return;
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore());
        lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(line).contains("◆ Unbreakable"));
        if (lore.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(lore);
        }
    }

    private static boolean loreEquals(List<net.kyori.adventure.text.Component> a,
                                      List<net.kyori.adventure.text.Component> b) {
        if (a == null || a.size() != b.size()) return false;
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for (int i = 0; i < a.size(); i++) {
            if (!plain.serialize(a.get(i)).equals(plain.serialize(b.get(i)))) return false;
        }
        return true;
    }

    /** Green → yellow → red lore color by integrity %. */
    private static String integrityColor(double pct) {
        if (pct > 66) return "<green>";
        if (pct > 33) return "<yellow>";
        return "<red>";
    }
}
