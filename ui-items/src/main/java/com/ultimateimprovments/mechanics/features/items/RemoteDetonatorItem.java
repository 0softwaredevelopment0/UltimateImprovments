package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The Remote Detonator item: identity, PDC binding and durability.
 * <p>
 * Base material is {@link Material#FLINT_AND_STEEL} — the detonator looks like a
 * lighter, and
 * {@link RemoteDetonatorListener} makes sure it is never used as one. Every
 * detonation costs one use; the item breaks when the last use is spent.
 * <p>
 * Name, lore and every message live in {@code configs/UI-Items.toml} under
 * {@code [messages.remote_detonator]} (RU) / {@code [messages_en.remote_detonator]}
 * (EN), so the item is fully bilingual.
 */
public final class RemoteDetonatorItem {

    /** Base material of the detonator. */
    public static final Material MATERIAL = Material.FLINT_AND_STEEL;

    /** Uses an item has when the config does not say otherwise. */
    public static final int DEFAULT_MAX_USES = 128;

    private static final String DEFAULT_NAME = "<white>Remote Detonator *</white>";
    private static final List<String> DEFAULT_LORE = List.of(
            "<gray>RMB — detonate the bound TNT</gray>",
            "<gray>Shift + RMB on TNT — bind it</gray>",
            "<gray>Shift + RMB elsewhere — clear the binding</gray>");

    private RemoteDetonatorItem() {}

    /** Builds a fresh, unbound detonator with the configured durability. */
    public static ItemStack createItem() {
        ItemStack item = new ItemStack(MATERIAL);
        item.editMeta(meta -> {
            meta.displayName(MessageUtil.parse("<i:false>" + MessagesManager.getString(
                    "remote_detonator.name", DEFAULT_NAME)));
            meta.lore(loreLines());
            meta.getPersistentDataContainer().set(
                    Keys.REMOTE_DETONATOR, PersistentDataType.BYTE, (byte) 1);
            if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(maxUses());
                damageable.setDamage(0);
            }
        });
        return item;
    }

    /** @return {@code true} when the stack is a Remote Detonator. */
    public static boolean isDetonator(ItemStack item) {
        if (item == null || item.getType() != MATERIAL) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(Keys.REMOTE_DETONATOR, PersistentDataType.BYTE);
    }

    /** @return the bound TNT block, or {@code null} when the item is unbound. */
    public static DetonatorTarget getTarget(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return DetonatorTarget.decode(meta.getPersistentDataContainer()
                .get(Keys.DETONATOR_TARGET, PersistentDataType.STRING));
    }

    /** Binds the item to {@code target}, replacing any previous binding. */
    public static void setTarget(ItemStack item, DetonatorTarget target) {
        if (target == null) {
            clearTarget(item);
            return;
        }
        item.editMeta(meta -> meta.getPersistentDataContainer().set(
                Keys.DETONATOR_TARGET, PersistentDataType.STRING, target.encode()));
    }

    /** Removes the binding, if any. */
    public static void clearTarget(ItemStack item) {
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .remove(Keys.DETONATOR_TARGET));
    }

    /**
     * Spends one use.
     *
     * @return {@code true} when that use was the last one and the item must break
     */
    public static boolean useOnce(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;

        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : maxUses();
        int damage = damageable.getDamage() + 1;
        if (damage >= max) return true;

        damageable.setDamage(damage);
        item.setItemMeta(damageable);
        return false;
    }

    /** Configured number of uses ({@code remote_detonator.max_uses}). */
    public static int maxUses() {
        Main main = Main.getInstance();
        if (main == null) return DEFAULT_MAX_USES;
        return Math.max(1, main.getConfig().getInt("remote_detonator.max_uses", DEFAULT_MAX_USES));
    }

    private static List<Component> loreLines() {
        String base = "en".equals(MessagesManager.currentLanguage())
                ? "messages_en.remote_detonator"
                : "messages.remote_detonator";

        List<String> raw = new ArrayList<>();
        Main main = Main.getInstance();
        if (main != null) raw = main.getConfig().getStringList(base + ".lore");
        if (raw == null || raw.isEmpty()) raw = DEFAULT_LORE;

        List<Component> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            lines.add(MessageUtil.parse("<i:false>" + line));
        }
        return lines;
    }
}
