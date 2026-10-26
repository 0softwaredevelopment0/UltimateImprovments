package com.ultimateimprovments.mechanics.features.kits;

import com.ultimateimprovments.util.MessageUtil;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Locale;

/**
 * Kit — one named set of up to 27 items (a shulker-box worth of content).
 * <p>
 * The kit is addressed in commands by its <b>plain</b> name: MiniMessage tags are
 * stripped and the result is compared case-insensitively ({@link #keyOf(String)}).
 * The raw MiniMessage name/description are kept for DISPLAY only ({@link #displayName()}).
 * <p>
 * Items are stored per slot (index 0..26 == shulker slot 1..27). Encoding for the
 * database: one Base64 string per non-empty slot, joined with {@code '\n'} — the
 * map to the player's inventory top-27 rows is done by {@link KitService}.
 */
public final class Kit {

    /** Kit size — exactly one shulker box. */
    public static final int SLOTS = 27;

    private final String key;          // lowercase plain name (identity, unique)
    private final String name;         // raw MiniMessage display name
    private final String description;  // raw MiniMessage description
    private final ItemStack[] items;   // exactly SLOTS entries, null = empty slot
    private final String createdBy;
    private final long createdAt;
    private long updatedAt;
    private final int cooldownSeconds;

    public Kit(String key, String name, String description, ItemStack[] items,
               String createdBy, long createdAt, long updatedAt, int cooldownSeconds) {
        this.key = key;
        this.name = name;
        this.description = description == null ? "" : description;
        this.items = normalize(items);
        this.createdBy = createdBy == null ? "" : createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    /** Unique identity: lowercase plain name (no MiniMessage tags). */
    public String key() {
        return key;
    }

    /** Raw MiniMessage name (as entered). */
    public String name() {
        return name;
    }

    /** Raw MiniMessage description. */
    public String description() {
        return description;
    }

    /** Defensive copy of the 27 item slots (null entries = empty). */
    public ItemStack[] items() {
        return items.clone();
    }

    public ItemStack itemAt(int slot) {
        return slot >= 0 && slot < SLOTS ? items[slot] : null;
    }

    public String createdBy() {
        return createdBy;
    }

    public long createdAt() {
        return createdAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    /**
     * Per-recipient cooldown of the kit in seconds; {@code 0} = no cooldown.
     * The value lives on the kit; the per-player usage timestamps are kept by
     * {@code KitStore} (table {@code kit_cooldowns}).
     */
    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    /** True when the kit restricts how often a player may receive it. */
    public boolean hasCooldown() {
        return cooldownSeconds > 0;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /** Rendered display name (MiniMessage parsed) — used in chat/GUI. */
    public Component displayName() {
        return MessageUtil.parse(name);
    }

    /** Number of non-empty slots. */
    public int itemCount() {
        int n = 0;
        for (ItemStack item : items) {
            if (isFilled(item)) n++;
        }
        return n;
    }

    /** True when at least one slot holds an item. */
    public boolean hasItems() {
        return itemCount() > 0;
    }

    // =========================================================================
    // NAME / KEY HELPERS
    // =========================================================================

    /**
     * Command identity of a kit: MiniMessage tags stripped, trimmed, lowercased.
     *
     * @param rawName user input, e.g. {@code "<red>Kit1"} or {@code "kit1"}
     * @return plain key, empty string when the input has no visible text
     */
    public static String keyOf(String rawName) {
        if (rawName == null) return "";
        return safePlain(rawName).trim().toLowerCase(Locale.ROOT);
    }

    /** Strips MiniMessage/legacy formatting without throwing on malformed input. */
    private static String safePlain(String raw) {
        try {
            return MessageUtil.toPlainText(raw);
        } catch (Exception e) {
            // Malformed MiniMessage — fall back to the raw text without the tags
            return raw.replaceAll("<[^>]*>", "");
        }
    }

    // =========================================================================
    // ITEM (DE)SERIALIZATION
    // =========================================================================

    /** Encodes the 27 slots into a DB-safe string (Base64 per slot, '\n'-joined). */
    public static String encodeItems(ItemStack[] items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            if (i > 0) sb.append('\n');
            ItemStack item = items != null && i < items.length ? items[i] : null;
            if (!isFilled(item)) continue;
            try {
                byte[] raw = item.serializeAsBytes();
                if (raw != null) sb.append(Base64.getEncoder().encodeToString(raw));
            } catch (Exception ignored) {
                // A single corrupt slot must not kill the whole kit
            }
        }
        return sb.toString();
    }

    /** Decodes the DB string back into 27 slots (null entries = empty). */
    public static ItemStack[] decodeItems(String data) {
        ItemStack[] out = new ItemStack[SLOTS];
        if (data == null || data.isEmpty()) return out;
        String[] parts = data.split("\n", -1);
        for (int i = 0; i < SLOTS && i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            try {
                out[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[i]));
            } catch (Exception ignored) {
                // skip corrupt slot
            }
        }
        return out;
    }

    /** Normalizes any array to exactly 27 entries (defensive copies). */
    private static ItemStack[] normalize(ItemStack[] source) {
        ItemStack[] out = new ItemStack[SLOTS];
        if (source == null) return out;
        for (int i = 0; i < SLOTS && i < source.length; i++) {
            ItemStack item = source[i];
            out[i] = isFilled(item) ? item.clone() : null;
        }
        return out;
    }

    /** True when the stack exists and is not AIR. */
    public static boolean isFilled(ItemStack item) {
        return item != null && item.getType() != org.bukkit.Material.AIR
                && !item.getType().isAir();
    }
}
