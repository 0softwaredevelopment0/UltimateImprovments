package com.ultimateimprovments.mechanics.features.kits;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.MessageUtil;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * KitService — all kit operations (give / create / rename / delete / import /
 * export / list) with their command-error codes and chat feedback.
 * <p>
 * Invoked from {@link KitDialogHandler} (dialog submissions) and from
 * {@code /ui kit list}. Every method sends its own result message (success or
 * error 022-030) and returns whether the operation succeeded.
 * <p>
 * Slot map: kit slot {@code i} (0..26, i.e. shulker slot {@code i+1}) is written
 * into the target's inventory slot {@code 9 + i} — the top three inventory rows,
 * which is exactly the shulker-box GUI layout.
 */
public final class KitService {

    // =========================================================================
    // COMMAND-SPECIFIC ERROR CODES (022+, checked against the codes already
    // used in ui-admin (006), ui-guard (007-010, 012-017), ui-core (001-005,
    // 011, 018) and scoreboard-nick/enchant (019-021))
    // =========================================================================

    /** 022 — no kit with that name. */
    public static final int ERR_KIT_NOT_FOUND = 22;
    /** 023 — a kit with that (plain) name already exists. */
    public static final int ERR_KIT_EXISTS = 23;
    /** 024 — no shulker box in the main hand. */
    public static final int ERR_NO_SHULKER = 24;
    /** 025 — not enough space in the target inventory. */
    public static final int ERR_NO_SPACE = 25;
    /** 026 — the target player is not online / not found. */
    public static final int ERR_PLAYER_NOT_FOUND = 26;
    /** 027 — invalid input (empty / too long name or description). */
    public static final int ERR_INVALID_INPUT = 27;
    /** 028 — the confirmation checkbox was not ticked. */
    public static final int ERR_NOT_CONFIRMED = 28;
    /** 029 — the kit has no items stored yet. */
    public static final int ERR_KIT_EMPTY = 29;
    /** 030 — the shulker box in the main hand is empty. */
    public static final int ERR_SHULKER_EMPTY = 30;

    /** Inventory offset of the top three inventory rows (the "27 slots"). */
    public static final int INVENTORY_OFFSET = 9;

    private KitService() {}

    // =========================================================================
    // GIVE
    // =========================================================================

    /**
     * Gives a kit to the giver (empty target field) or to a third party.
     *
     * @param giver     the staff member running /ui kit give
     * @param rawKit    the kit name as entered (MiniMessage tags allowed)
     * @param rawTarget the recipient nickname; blank = the giver himself
     * @param force     {@code -f}: overwrite occupied slots instead of failing
     * @return true when the kit was handed out
     */
    public static boolean give(Player giver, String rawKit, String rawTarget, boolean force) {
        if (!giver.hasPermission(Permissions.CMD_KIT_GIVE)) {
            CommandErrors.noPermission(giver, Permissions.CMD_KIT_GIVE);
            return false;
        }
        if (force && !giver.hasPermission(Permissions.CMD_KIT_GIVE_FORCE)) {
            CommandErrors.noPermission(giver, Permissions.CMD_KIT_GIVE_FORCE);
            return false;
        }

        String targetName = rawTarget == null ? "" : rawTarget.trim();

        // Non-empty target field → third-party give, which needs its own node
        // (per spec: even when the nickname happens to be the giver's own).
        if (!targetName.isEmpty() && !giver.hasPermission(Permissions.CMD_KIT_GIVE_OTHER)) {
            CommandErrors.noPermission(giver, Permissions.CMD_KIT_GIVE_OTHER);
            return false;
        }

        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(giver, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }

        Player target;
        if (targetName.isEmpty()) {
            target = giver;
        } else {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                error(giver, ERR_PLAYER_NOT_FOUND, get("player_not_found",
                        "<red>Player</red> <white>\"%player%\"</white> <red>is not online!</red>",
                        "%player%", targetName));
                return false;
            }
        }

        if (!kit.hasItems()) {
            error(giver, ERR_KIT_EMPTY, get("kit_empty",
                    "<red>Kit</red> <white>%kit%</white> <red>has no items — import a shulker box first!</red>",
                    "%kit%", kit.name()));
            return false;
        }

        ItemStack[] items = kit.items();
        PlayerInventory inv = target.getInventory();

        // Slots that would be overwritten (kit slot indexes, 0-based)
        List<Integer> occupied = new ArrayList<>();
        for (int i = 0; i < Kit.SLOTS; i++) {
            if (!Kit.isFilled(items[i])) continue;
            if (Kit.isFilled(inv.getItem(INVENTORY_OFFSET + i))) occupied.add(i);
        }

        if (!occupied.isEmpty() && !force) {
            error(giver, ERR_NO_SPACE, get("no_space",
                    "<red>Not enough space in</red> <white>%player%</white><red>'s inventory! "
                            + "Occupied slots: </red><yellow>%slots%</yellow><red>. "
                            + "Use </red><white>/ui kit give -f</white><red> to overwrite them.</red>",
                    "%player%", target.getName(),
                    "%slots%", slotList(occupied)));
            return false;
        }

        for (int i = 0; i < Kit.SLOTS; i++) {
            if (!Kit.isFilled(items[i])) continue;
            inv.setItem(INVENTORY_OFFSET + i, items[i].clone());
        }
        target.updateInventory();

        // ── Feedback ──
        if (occupied.isEmpty()) {
            send(giver, get("given",
                    "<green>✔</green> <white>Kit</white> <yellow>%kit%</yellow> <white>given to</white> "
                            + "<yellow>%player%</yellow><white>.</white>",
                    "%kit%", kit.name(), "%player%", target.getName()));
        } else {
            send(giver, get("given_force",
                    "<green>✔</green> <white>Kit</white> <yellow>%kit%</yellow> <white>force-given to</white> "
                            + "<yellow>%player%</yellow><white>: overwritten</white> <red>%count%</red> "
                            + "<white>slot(s).</white>",
                    "%kit%", kit.name(), "%player%", target.getName(),
                    "%count%", String.valueOf(occupied.size())));
            send(giver, get("overwritten",
                    "<gray>↳ overwritten slots: </gray><yellow>%slots%</yellow>",
                    "%slots%", slotList(occupied)));
        }

        if (target != giver) {
            send(target, get("received",
                    "<green>✔</green> <white>You received kit</white> <yellow>%kit%</yellow> "
                            + "<white>from</white> <yellow>%player%</yellow><white>.</white>",
                    "%kit%", kit.name(), "%player%", giver.getName()));
        }
        return true;
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    public static boolean create(Player creator, String rawName, String rawDescription) {
        String key = Kit.keyOf(rawName);
        if (key.isEmpty()) {
            error(creator, ERR_INVALID_INPUT, get("invalid_name",
                    "<red>The kit name must not be empty!</red>"));
            return false;
        }
        int maxName = config().getInt("kits.max_name_length", 48);
        int maxDesc = config().getInt("kits.max_description_length", 128);
        if (rawName.trim().length() > maxName || (rawDescription != null && rawDescription.length() > maxDesc)) {
            error(creator, ERR_INVALID_INPUT, get("input_too_long",
                    "<red>Name must be ≤</red><yellow>%name%</yellow><red> and description ≤</red>"
                            + "<yellow>%desc%</yellow><red> characters.</red>",
                    "%name%", String.valueOf(maxName), "%desc%", String.valueOf(maxDesc)));
            return false;
        }
        if (KitStore.exists(rawName)) {
            error(creator, ERR_KIT_EXISTS, get("kit_exists",
                    "<red>A kit named</red> <white>%kit%</white> <red>already exists!</red>",
                    "%kit%", plainOr(rawName)));
            return false;
        }

        Kit kit = new Kit(key, rawName.trim(), rawDescription == null ? "" : rawDescription.trim(),
                new ItemStack[Kit.SLOTS], creator.getName(),
                System.currentTimeMillis(), System.currentTimeMillis());
        if (!KitStore.save(kit)) {
            CommandErrors.unknownError(creator);
            return false;
        }
        send(creator, get("created",
                "<green>✔</green> <white>Kit created:</white> <yellow>%kit%</yellow>"
                        + "<gray> (use </gray><white>/ui kit import</white><gray> to fill it)</gray>",
                "%kit%", kit.name()));
        return true;
    }

    // =========================================================================
    // RENAME
    // =========================================================================

    public static boolean rename(Player player, String rawOld, String rawNew) {
        String oldKey = Kit.keyOf(rawOld);
        String newKey = Kit.keyOf(rawNew);
        if (oldKey.isEmpty() || newKey.isEmpty()) {
            error(player, ERR_INVALID_INPUT, get("invalid_name",
                    "<red>The kit name must not be empty!</red>"));
            return false;
        }
        int maxName = config().getInt("kits.max_name_length", 48);
        if (rawNew.trim().length() > maxName) {
            error(player, ERR_INVALID_INPUT, get("input_too_long",
                    "<red>Name must be ≤</red><yellow>%name%</yellow><red> and description ≤</red>"
                            + "<yellow>%desc%</yellow><red> characters.</red>",
                    "%name%", String.valueOf(maxName),
                    "%desc%", String.valueOf(config().getInt("kits.max_description_length", 128))));
            return false;
        }

        Kit kit = KitStore.getByKey(oldKey);
        if (kit == null) {
            error(player, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawOld)));
            return false;
        }
        if (!newKey.equals(oldKey) && KitStore.getByKey(newKey) != null) {
            error(player, ERR_KIT_EXISTS, get("kit_exists",
                    "<red>A kit named</red> <white>%kit%</white> <red>already exists!</red>",
                    "%kit%", plainOr(rawNew)));
            return false;
        }

        if (!KitStore.rename(kit, newKey, rawNew.trim())) {
            CommandErrors.unknownError(player);
            return false;
        }
        send(player, get("renamed",
                "<green>✔</green> <white>Kit</white> <yellow>%old%</yellow> <white>renamed to</white> "
                        + "<yellow>%new%</yellow><white>.</white>",
                "%old%", kit.name(), "%new%", rawNew.trim()));
        return true;
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    public static boolean delete(Player player, String rawKit, boolean confirmed) {
        if (!confirmed) {
            error(player, ERR_NOT_CONFIRMED, get("not_confirmed",
                    "<red>Deletion was not confirmed — tick the checkbox first.</red>"));
            return false;
        }
        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(player, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }
        if (!KitStore.delete(kit.key())) {
            CommandErrors.unknownError(player);
            return false;
        }
        send(player, get("deleted",
                "<green>✔</green> <white>Kit deleted:</white> <yellow>%kit%</yellow>",
                "%kit%", kit.name()));
        return true;
    }

    // =========================================================================
    // IMPORT (shulker in the main hand → kit)
    // =========================================================================

    public static boolean importFromHand(Player player, String rawKit) {
        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(player, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!isShulker(inHand)) {
            error(player, ERR_NO_SHULKER, get("no_shulker",
                    "<red>You must hold a shulker box in your main hand!</red>"));
            return false;
        }

        ItemStack[] contents = readShulker(inHand);
        int filled = 0;
        for (ItemStack item : contents) {
            if (Kit.isFilled(item)) filled++;
        }
        if (filled == 0) {
            error(player, ERR_SHULKER_EMPTY, get("shulker_empty",
                    "<red>The shulker box in your hand is empty!</red>"));
            return false;
        }

        // The shulker box itself stays untouched in the player's hand.
        Kit updated = new Kit(kit.key(), kit.name(), kit.description(), contents,
                kit.createdBy(), kit.createdAt(), System.currentTimeMillis());
        if (!KitStore.save(updated)) {
            CommandErrors.unknownError(player);
            return false;
        }
        send(player, get("imported",
                "<green>✔</green> <white>Copied</white> <yellow>%count%</yellow> "
                        + "<white>item(s) from the shulker box into kit</white> <yellow>%kit%</yellow><white>.</white>",
                "%kit%", kit.name(), "%count%", String.valueOf(filled)));
        return true;
    }

    // =========================================================================
    // EXPORT (kit → shulker into the main hand)
    // =========================================================================

    public static boolean exportToHand(Player player, String rawKit, boolean force) {
        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(player, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }
        if (!kit.hasItems()) {
            error(player, ERR_KIT_EMPTY, get("kit_empty",
                    "<red>Kit</red> <white>%kit%</white> <red>has no items — import a shulker box first!</red>",
                    "%kit%", kit.name()));
            return false;
        }

        ItemStack shulker = buildShulker(kit);
        PlayerInventory inv = player.getInventory();
        int handSlot = inv.getHeldItemSlot();
        boolean handBusy = Kit.isFilled(inv.getItem(handSlot));

        if (!handBusy) {
            inv.setItem(handSlot, shulker);
        } else if (force) {
            // -f: the old hand item is removed, the shulker takes its place
            inv.setItem(handSlot, shulker);
        } else if (firstFreeSlot(inv) >= 0) {
            inv.addItem(shulker);
        } else {
            error(player, ERR_NO_SPACE, get("no_space_export",
                    "<red>No free slot for the shulker box — free a slot or use </red>"
                            + "<white>/ui kit export -f</white><red>.</red>"));
            return false;
        }
        player.updateInventory();

        send(player, get("exported",
                "<green>✔</green> <white>Shulker box with kit</white> <yellow>%kit%</yellow> "
                        + "<white>handed to you.</white>",
                "%kit%", kit.name()));
        return true;
    }

    // =========================================================================
    // LIST
    // =========================================================================

    /** Renders one page of the kit list ({@code kits.list_page_size} rows). */
    public static boolean list(Player player, int page) {
        List<Kit> kits = KitStore.all();
        int perPage = Math.max(1, config().getInt("kits.list_page_size", 7));
        int totalPages = Math.max(1, (kits.size() + perPage - 1) / perPage);
        int current = Math.max(1, Math.min(page, totalPages));

        send(player, get("list_header",
                "<gold>══════════════════════════════════\n"
                        + "  ✦ <white>Kits</white> <gray>(%total%)</gray>\n"
                        + "══════════════════════════════════",
                "%total%", String.valueOf(kits.size())));

        if (kits.isEmpty()) {
            send(player, get("list_empty",
                    "<gray>No kits yet — create one with</gray> <white>/ui kit create</white><gray>.</gray>"));
            return true;
        }

        int from = (current - 1) * perPage;
        int to = Math.min(from + perPage, kits.size());

        for (int i = from; i < to; i++) {
            Kit kit = kits.get(i);
            String row = get("list_row", "<gray>%number%.</gray> %kit%%description%");
            row = row.replace("%number%", String.format("%2d", i + 1));
            row = row.replace("%description%", kit.description().isBlank() ? ""
                    : get("list_row_description", " <dark_gray>—</dark_gray> <gray>%description%</gray>")
                            .replace("%description%", kit.description()));
            // The display name is inserted as a parsed component so its own
            // MiniMessage colours are rendered exactly as stored.
            int nameAt = row.indexOf("%kit%");
            if (nameAt < 0) {
                player.sendMessage(MessageUtil.parse(row, player));
            } else {
                Component prefix = MessageUtil.parse(row.substring(0, nameAt));
                Component suffix = MessageUtil.parse(row.substring(nameAt + "%kit%".length()));
                player.sendMessage(prefix.append(kit.displayName()).append(suffix));
            }
        }

        String footer = get("list_footer", "<gold>══════════════════════════════════");
        player.sendMessage(MessageUtil.parse(footer));

        if (totalPages > 1) {
            String prev = get("list_prev", "<yellow>◀ Prev</yellow>");
            String next = get("list_next", "<yellow>Next ▶</yellow>");
            String pageLabel = get("list_page", "<gray>page %current%/%total%</gray>")
                    .replace("%current%", String.valueOf(current))
                    .replace("%total%", String.valueOf(totalPages))
                    .replace("%per_page%", String.valueOf(perPage));

            Component prevComp = current > 1
                    ? MessageUtil.parse(prev).clickEvent(net.kyori.adventure.text.event.ClickEvent
                            .runCommand("/ui kit list " + (current - 1)))
                    : Component.empty();
            Component nextComp = current < totalPages
                    ? MessageUtil.parse(next).clickEvent(net.kyori.adventure.text.event.ClickEvent
                            .runCommand("/ui kit list " + (current + 1)))
                    : Component.empty();

            player.sendMessage(prevComp
                    .append(Component.text("  "))
                    .append(MessageUtil.parse(pageLabel))
                    .append(Component.text("  "))
                    .append(nextComp));
        }
        return true;
    }

    // =========================================================================
    // SHULKER HELPERS
    // =========================================================================

    /** True for any shulker box item (plain or coloured). */
    public static boolean isShulker(ItemStack item) {
        if (!Kit.isFilled(item)) return false;
        String name = item.getType().name();
        return name.equals("SHULKER_BOX") || name.endsWith("_SHULKER_BOX");
    }

    /** Reads the 27 contents of a shulker-box item (null entries = empty slots). */
    public static ItemStack[] readShulker(ItemStack shulkerItem) {
        ItemStack[] out = new ItemStack[Kit.SLOTS];
        ItemMeta meta = shulkerItem.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) return out;
        BlockState state = bsm.getBlockState();
        if (!(state instanceof ShulkerBox box)) return out;
        Inventory contents = box.getInventory();
        for (int i = 0; i < Kit.SLOTS; i++) {
            ItemStack item = contents.getItem(i);
            out[i] = Kit.isFilled(item) ? item.clone() : null;
        }
        return out;
    }

    /** Builds a shulker-box item holding the kit's contents. */
    public static ItemStack buildShulker(Kit kit) {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        ItemMeta meta = shulker.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            Inventory contents = box.getInventory();
            ItemStack[] items = kit.items();
            for (int i = 0; i < Kit.SLOTS; i++) {
                contents.setItem(i, Kit.isFilled(items[i]) ? items[i].clone() : null);
            }
            bsm.setBlockState(box);
            shulker.setItemMeta(bsm);
        }
        return shulker;
    }

    /** First empty main-inventory slot (0..35) or -1. */
    private static int firstFreeSlot(PlayerInventory inv) {
        for (int i = 0; i <= 35; i++) {
            if (!Kit.isFilled(inv.getItem(i))) return i;
        }
        return -1;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** "3, 5, 12" — kit slot numbers (1-based, shulker layout). */
    private static String slotList(List<Integer> zeroBasedSlots) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int slot : zeroBasedSlots) joiner.add(String.valueOf(slot + 1));
        return joiner.toString();
    }

    /** Plain (tag-free) form of a raw name, used inside error texts. */
    private static String plainOr(String raw) {
        String plain = Kit.keyOf(raw);
        return plain.isEmpty() ? String.valueOf(raw) : plain;
    }

    private static org.bukkit.configuration.file.FileConfiguration config() {
        return Main.getInstance().getConfig();
    }

    private static String get(String key, String def, String... replacements) {
        String text = MessagesManager.getString("kits." + key, def);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    private static void send(Player player, String miniMessage) {
        player.sendMessage(MessageUtil.parse(miniMessage, player));
    }

    private static void error(Player player, int code, String miniMessage) {
        CommandErrors.custom(player, code, miniMessage);
    }
}
