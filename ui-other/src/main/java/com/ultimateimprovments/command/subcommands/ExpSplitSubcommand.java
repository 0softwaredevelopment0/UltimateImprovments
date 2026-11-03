package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /ui expsplit — packs the WHOLE experience bar into a bottle, and unpacks it
 * again on right-click.
 * <p>
 * The bottle is an ordinary {@link Material#EXPERIENCE_BOTTLE} (Bottle o'
 * Enchanting) carrying the exact stored amount in PDC
 * {@code ui:exp_split_amount} (int). Everything the player sees — item name,
 * lore and every chat line — comes from the config
 * ({@code messages.expsplit.*} / {@code messages_en.expsplit.*}, routed to
 * {@code configs/UI-Other.toml} by {@code AddonCatalog}).
 * <p>
 * Right-clicking never throws the bottle: UI-Items installs
 * {@code ExpSplitBottleListener}, which denies the vanilla item use, cancels
 * any projectile launched from such a bottle and zeroes the vanilla XP drop of
 * {@code ExpBottleEvent}. Old GLASS_BOTTLE items from previous versions keep
 * working (they are recognised by the PDC tag, not by the material).
 */
public final class ExpSplitSubcommand {

    /** PDC key holding the amount of experience stored in the bottle (int). */
    private static final NamespacedKey EXP_SPLIT_KEY = new NamespacedKey("ui", "exp_split_amount");

    // ═══════════ default texts (used when the config key is missing) ═══════════

    private static final String DEF_NO_XP =
            "<red>❌ У вас нет опыта для разделения!</red>";
    private static final String DEF_INVENTORY_FULL =
            "<red>❌ Инвентарь полон! Освободите место.</red>";
    private static final String DEF_BOTTLE_NAME =
            "<i:false><gold>✦ Колба опыта ✦</gold>";
    private static final String DEF_LORE_CONTAINS =
            "<i:false><gray>Содержит: <yellow>%xp%</yellow> XP</gray>";
    private static final String DEF_LORE_HINT =
            "<i:false><gray>ПКМ — забрать опыт</gray>";
    private static final String DEF_SPLIT_SUCCESS =
            "<green>✔</green> <white>Весь опыт (<yellow>%xp%</yellow> XP) помещён в колбу!</white>";
    private static final String DEF_COLLECT_SUCCESS =
            "<green>✔</green> <white>Получено <yellow>%xp%</yellow> XP из колбы!</white>";
    private static final String DEF_BOTTLE_EMPTY =
            "<red>❌ В этой колбе нет опыта!</red>";

    private ExpSplitSubcommand() {}

    /**
     * Hook implementation installed into {@link CoreHooks} by UI-Other, so
     * UI-Items can recognise and unpack the bottle without depending on this addon.
     */
    public static CoreHooks.ExpBottleUser hook() {
        return new CoreHooks.ExpBottleUser() {
            @Override
            public boolean isBottle(ItemStack item) {
                return ExpSplitSubcommand.isBottle(item);
            }

            @Override
            public boolean useBottle(Player player, ItemStack item, EquipmentSlot hand) {
                return ExpSplitSubcommand.useBottle(player, item, hand);
            }
        };
    }

    // ═══════════ /ui expsplit ═══════════

    /**
     * /ui expsplit — takes all experience (levels + bar progress) from the
     * player and gives back one bottle charged with the exact amount.
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (!player.hasPermission(Permissions.CMD_EXPSPLIT)) {
            CommandErrors.noPermission(sender, Permissions.CMD_EXPSPLIT);
            return true;
        }

        int level = player.getLevel();
        float progress = player.getExp();

        // Total XP: whole levels + the partial progress of the current level
        int totalXp = getXpFromLevels(level) + (int) (getXpForLevel(level) * progress);

        if (level == 0 && progress == 0 || totalXp <= 0) {
            player.sendMessage(MessageUtil.parse(msg("no_xp", DEF_NO_XP), player));
            return true;
        }

        // Room for the bottle (checked BEFORE the XP is taken — never eat XP for nothing)
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(MessageUtil.parse(msg("inventory_full", DEF_INVENTORY_FULL), player));
            return true;
        }

        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);

        player.getInventory().addItem(createBottle(totalXp));

        player.sendMessage(MessageUtil.parse(
                msg("split_success", DEF_SPLIT_SUCCESS).replace("%xp%", String.valueOf(totalXp)),
                player));
        return true;
    }

    // ═══════════ bottle creation ═══════════

    /**
     * Builds an exp-split bottle charged with {@code xp}.
     *
     * @param xp amount of experience to store (must be positive)
     * @return the finished item, or a plain bottle when item meta is unavailable
     */
    public static ItemStack createBottle(int xp) {
        ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = bottle.getItemMeta();
        if (meta == null) return bottle;

        meta.displayName(MessageUtil.parse(msg("bottle_name", DEF_BOTTLE_NAME)));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MessageUtil.parse(
                msg("bottle_lore_contains", DEF_LORE_CONTAINS).replace("%xp%", String.valueOf(xp))));
        lore.add(MessageUtil.parse(msg("bottle_lore_hint", DEF_LORE_HINT)));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(EXP_SPLIT_KEY, PersistentDataType.INTEGER, xp);
        bottle.setItemMeta(meta);
        return bottle;
    }

    // ═══════════ right-click: unpack ═══════════

    /**
     * Unpacks the stored experience on right-click.
     * <p>
     * The bottle is consumed from the hand it was used from, so an off-hand use
     * never eats the main-hand stack.
     *
     * @return true only when the charge was applied
     */
    public static boolean useBottle(Player player, ItemStack item, EquipmentSlot hand) {
        if (!isBottle(item)) return false;

        // Work on the live inventory stack — the event's copy is not authoritative
        ItemStack handItem = player.getInventory().getItem(hand);
        if (!isBottle(handItem)) return false;

        int amount = storedXp(handItem);
        if (amount <= 0) {
            player.sendMessage(MessageUtil.parse(msg("bottle_empty", DEF_BOTTLE_EMPTY), player));
            return false;
        }

        // Give the experience first, consume only afterwards
        player.giveExp(amount);

        if (handItem.getAmount() > 1) {
            handItem.setAmount(handItem.getAmount() - 1);
            player.getInventory().setItem(hand, handItem);
        } else {
            player.getInventory().setItem(hand, null);
        }

        player.sendMessage(MessageUtil.parse(
                msg("collect_success", DEF_COLLECT_SUCCESS).replace("%xp%", String.valueOf(amount)),
                player));
        return true;
    }

    // ═══════════ item checks ═══════════

    /**
     * Returns true when the item is an exp-split bottle: an EXPERIENCE_BOTTLE
     * (or a legacy GLASS_BOTTLE) tagged with a stored amount.
     */
    public static boolean isBottle(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        if (type != Material.EXPERIENCE_BOTTLE && type != Material.GLASS_BOTTLE) return false;
        return hasStoredXp(item);
    }

    /** @return the stored amount, or 0 when the item carries no exp-split tag. */
    public static int storedXp(ItemStack item) {
        if (!hasStoredXp(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer stored = meta.getPersistentDataContainer().get(EXP_SPLIT_KEY, PersistentDataType.INTEGER);
        return stored == null ? 0 : Math.max(0, stored);
    }

    private static boolean hasStoredXp(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(EXP_SPLIT_KEY, PersistentDataType.INTEGER);
    }

    // ═══════════ config helper ═══════════

    /** Reads {@code messages.expsplit.<key>} for the active language. */
    private static String msg(String key, String def) {
        return MessageUtil.raw("expsplit." + key, def);
    }

    // ═══════════ XP CALCULATION (Minecraft formula) ═══════════

    public static int getXpForLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    public static int getXpFromLevels(int level) {
        int total = 0;
        for (int i = 0; i < level; i++) {
            total += getXpForLevel(i);
        }
        return total;
    }

    public static NamespacedKey getExpSplitKey() {
        return EXP_SPLIT_KEY;
    }
}
