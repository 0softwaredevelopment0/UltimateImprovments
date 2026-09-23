package com.ultimateimprovments.mechanics.features.integrity;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;


/**
 * 🛡 ItemDurabilityUtil — minimal durability helper built directly on top of
 * <b>vanilla Minecraft durability</b>.
 * <p>
 * The old custom integrity system (PDC counters + % lore + anvil/mending/
 * grindstone interception) was removed entirely: it fought the vanilla repair
 * economy and produced desync bugs (e.g. anvil repairs bypassing the XP cost).
 * All item state now lives in the vanilla {@code damage} data component —
 * anvil, grindstone and Mending behave exactly like vanilla, with no possible
 * bypass or desync, and items can no longer break "twice".
 * <p>
 * What remains (all optional, config-gated):
 * <ul>
 *   <li><b>Custom wear</b> — extra vanilla damage points from custom
 *       enchantments (aoe, veinminer, treecapitator, flight, degradation) and
 *       the sunburn mechanic, applied as one vanilla durability point per use
 *       unless a caller passes a bigger cost. When
 *       {@code features.integrity.unbreaking.enabled} is true, custom wear
 *       also passes through the vanilla (level + 1) Unbreaking chance roll;
 *       when disabled (default), Unbreaking applies only to vanilla damage —
 *       exactly like vanilla.</li>
 *   <li><b>Piercing</b> — {@code features.integrity.piercing.enabled}:
 *       hits with a PIERCING weapon deal extra vanilla damage to the target's
 *       armor (PiercingListener).</li>
 * </ul>
 * <p>
 * Units are baked into the method names to avoid confusion:
 * <ul>
 *   <li>{@code setItemIntegrity} — sets an exact integrity % (0.0–100.0)</li>
 *   <li>{@code decreaseItemIntegrity} — applies N points of vanilla damage</li>
 *   <li>{@code decreaseItemIntegrityPercent / increaseItemIntegrityPercent} —
 *       changes by exactly X%</li>
 * </ul>
 * All write methods return the <b>actual</b> integrity % (0.0–100.0)
 * <i>after</i> the operation. No lore is written — the vanilla durability bar
 * is the single visual indicator.
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
    private static java.util.List<Integer> warnThresholds = new java.util.ArrayList<>();

    /** Marker of items migrated from the old integrity system (PDC data already removed). */
    private static NamespacedKey MIGRATED_TAG;

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
    }

    public static boolean isEnabled() { return enabled; }
    public static boolean isUnbreakingEnabled() { return unbreakingEnabled; }
    public static boolean isPiercingEnabled() { return piercingEnabled; }
    public static double getPiercingExtraCost() { return piercingExtraCost; }

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

    /** One-time legacy migration — drops the old PDC integrity data and lore if present. */
    public static void initializeItemIntegrity(ItemStack item) {
        migrateLegacyItem(item);
        stripLegacyIntegrityLore(item);
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
        item.setItemMeta(meta);

        if (clamped <= 0) {
            breakItem(item, null);
            return 0.0;
        }
        return clamped;
    }

    /**
     * Applies {@code iterations} points of vanilla damage to the item.
     * Returns the actual integrity % after the deduction.
     */
    public static double decreaseItemIntegrity(ItemStack item, int iterations, Player owner) {
        if (item == null || iterations <= 0) return getItemIntegrityPercent(item);
        int max = getMaxDurability(item);
        if (max <= 0) return getItemIntegrityPercent(item);
        return decreaseItemIntegrityPercent(item, 100.0 * iterations / max, owner);
    }

    /** Increases integrity by as much as N durability points. Returns the actual % after repair. */
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
        item.setItemMeta(meta);

        if (after >= max) {
            breakItem(item, owner);
            return 0.0;
        } else {
            warnOnWear(owner, item, max, before, after);
        }
        return getItemIntegrityPercent(item);
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
        item.setItemMeta(meta);
        return getItemIntegrityPercent(item);
    }

    // =========================
    // VANILLA UNBREAKABLE
    // =========================

    /** Whether the item is vanilla-unbreakable (minecraft:unbreakable component). */
    public static boolean isUnbreakable(ItemStack item) {
        return item != null && item.getItemMeta() != null && item.getItemMeta().isUnbreakable();
    }

    /** Toggles the vanilla unbreakable component on the item. */
    public static void setVanillaUnbreakable(ItemStack item, boolean unbreakable) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setUnbreakable(unbreakable);
        item.setItemMeta(meta);
    }

    // =========================
    // LOW DURABILITY WARNING (threshold crossing, migrated from the old IIS)
    // =========================

    /**
     * Warns the owner when a wear event crosses a warning threshold downward
     * (e.g. drops below 50%). Stateless: a hit crossing several thresholds at
     * once produces one message; repairs re-arm the thresholds naturally.
     *
     * @param beforeDamage vanilla damage before the wear
     * @param afterDamage  vanilla damage after the wear
     */
    public static void warnOnWear(Player owner, ItemStack item, int max,
                                  int beforeDamage, int afterDamage) {
        if (owner == null || warnThresholds == null || warnThresholds.isEmpty()) return;
        if (max <= 0 || beforeDamage >= max) return;

        double pctBefore = 100.0 * (1.0 - (double) Math.min(beforeDamage, max) / max);
        double pctAfter = 100.0 * (1.0 - (double) Math.min(afterDamage, max) / max);

        for (int threshold : warnThresholds) {
            if (pctBefore > threshold && pctAfter <= threshold) {
                String msg = com.ultimateimprovments.config.MessagesManager.getString(
                        "features.integrity.low_integrity_warning.message",
                        "<yellow>⚠</yellow> <white>Your item</white> <yellow>%item%</yellow> <white>has</white> <red>%pct%%</red> <white>durability remaining!</white>")
                        .replace("%item%", item.getType().name().toLowerCase().replace('_', ' '))
                        .replace("%pct%", String.format("%.0f", pctAfter));
                owner.sendMessage(MessageUtil.parse(msg));
                break; // one message per hit even if several thresholds were crossed
            }
        }
    }

    /**
     * Warns the owner when a wear event crosses a warning threshold downward.
     * Convenience overload for vanilla damage events: the delta is added to
     * the item's current vanilla damage.
     */
    public static void warnOnWear(Player owner, ItemStack item, int damageDelta) {
        if (owner == null || item == null || item.getType() == Material.AIR || damageDelta <= 0) return;
        int max = getMaxDurability(item);
        if (max <= 0) return;
        int before = getVanillaDamage(item);
        warnOnWear(owner, item, max, before, Math.min(max, before + damageDelta));
    }

    // =========================
    // BREAK
    // =========================

    /**
     * Breaks the item the way the old integrity system (and every consumer of
     * this API) expects: the stack is destroyed via {@code setAmount(0)} so
     * enchantment sweeps (aoe/veinminer/treecapitator/degradation) that poll
     * {@code getAmount() <= 0} actually stop, and the break sound plays at the
     * owner's location (not world spawn).
     */
    private static void breakItem(ItemStack item, Player owner) {
        int max = getMaxDurability(item);
        if (max > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable dmg) {
                dmg.setDamage(max);
                item.setItemMeta(meta);
            }
        }
        item.setAmount(0);
        if (onBreakPlaySound && owner != null) {
            try {
                owner.getWorld().playSound(owner.getLocation(),
                        Sound.ENTITY_ITEM_BREAK,
                        org.bukkit.SoundCategory.PLAYERS,
                        (float) onBreakSoundVolume, (float) onBreakSoundPitch);
            } catch (Exception e) {
                ConsoleLogger.warn("[Durability] break sound error: " + e.getMessage());
            }
        }
    }

    /**
     * Removes the old "Integrity: N%" lore line wherever the item shows up
     * (pickup, craft, hotbar swap, click in an open inventory — see
     * {@link IntegrityLoreCleanupListener}).
     */
    private static void removeIntegrityLore(ItemMeta meta) {
        if (!meta.hasLore() || meta.lore() == null) return;
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore());
        lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(line).toLowerCase().contains("integrity:"));
        if (lore.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(lore);
        }
    }

    // =========================
    // INTEGRITY LORE MIGRATION
    // =========================

    /**
     * Strips the legacy integrity lore line from the item if present.
     * Returns true if the meta was changed (caller must write it back).
     * Runs for every item passing through the cleanup listener — even items
     * without vanilla durability can carry the old lore (e.g. renamed books).
     */
    public static boolean stripLegacyIntegrityLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (!meta.hasLore()) return false;
        int before = meta.lore() != null ? meta.lore().size() : 0;
        removeIntegrityLore(meta);
        int after = meta.lore() != null ? meta.lore().size() : 0;
        if (after != before) {
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }
}
