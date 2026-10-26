package com.ultimateimprovments.mechanics.features.kits;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.MessageUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
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
 * export / list / cooldown) with their command-error codes and chat feedback.
 * <p>
 * Invoked from {@link KitDialogHandler} (dialog submissions, players only) and
 * from {@code /ui kit ...} — players go through dialogs, console and other
 * non-player senders use plain arguments/flags (see {@code KitSubcommand}).
 * Every method sends its own result message (success or error 022-033) and
 * returns whether the operation succeeded.
 * <p>
 * Slot map: kit slot {@code i} (0..26, i.e. shulker slot {@code i+1}) is written
 * into the target's inventory slot {@code 9 + i} — the top three inventory rows,
 * which is exactly the shulker-box GUI layout.
 * <p>
 * Cooldown: a kit may carry {@link Kit#cooldownSeconds()} (0 = none). The
 * cooldown is <b>per recipient and per kit</b>: once a player receives the kit
 * (give/export) they may not receive it again until it expires, unless they
 * hold {@code ui.command.kit.cooldown.bypass}.
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
    /** 031 — the recipient is still on the kit's cooldown. */
    public static final int ERR_COOLDOWN = 31;
    /** 032 — the cooldown value is not a whole number of seconds in range. */
    public static final int ERR_INVALID_COOLDOWN = 32;
    /** 033 — a non-player sender must name the player (no main hand / inventory). */
    public static final int ERR_TARGET_REQUIRED = 33;

    /** Inventory offset of the top three inventory rows (the "27 slots"). */
    public static final int INVENTORY_OFFSET = 9;

    /** Fallback cap for kit cooldowns when the config key is missing. */
    private static final int DEFAULT_MAX_COOLDOWN_SECONDS = 2_592_000;

    private KitService() {}

    // =========================================================================
    // GIVE
    // =========================================================================

    /**
     * Gives a kit to the giver (empty target field) or to a third party.
     *
     * @param sender    the staff member / console running /ui kit give
     * @param rawKit    the kit name as entered (MiniMessage tags allowed)
     * @param rawTarget the recipient nickname; blank = the sender himself
     *                  (players only, console must name the recipient)
     * @param force     {@code -f}: overwrite occupied slots instead of failing
     * @return true when the kit was handed out
     */
    public static boolean give(CommandSender sender, String rawKit, String rawTarget, boolean force) {
        if (!sender.hasPermission(Permissions.CMD_KIT_GIVE)) {
            CommandErrors.noPermission(sender, Permissions.CMD_KIT_GIVE);
            return false;
        }
        if (force && !sender.hasPermission(Permissions.CMD_KIT_GIVE_FORCE)) {
            CommandErrors.noPermission(sender, Permissions.CMD_KIT_GIVE_FORCE);
            return false;
        }

        String targetName = rawTarget == null ? "" : rawTarget.trim();

        // Non-empty target field → third-party give, which needs its own node
        // (per spec: even when the nickname happens to be the giver's own).
        if (!targetName.isEmpty() && !sender.hasPermission(Permissions.CMD_KIT_GIVE_OTHER)) {
            CommandErrors.noPermission(sender, Permissions.CMD_KIT_GIVE_OTHER);
            return false;
        }

        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(sender, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }

        Player target = resolveTarget(sender, targetName, true);
        if (target == null) return false;

        if (!kit.hasItems()) {
            error(sender, ERR_KIT_EMPTY, get("kit_empty",
                    "<red>Kit</red> <white>%kit%</white> <red>has no items — import a shulker box first!</red>",
                    "%kit%", kit.name()));
            return false;
        }

        if (cooldownBlocked(sender, kit, target)) return false;

        ItemStack[] items = kit.items();
        PlayerInventory inv = target.getInventory();

        // Slots that would be overwritten (kit slot indexes, 0-based)
        List<Integer> occupied = new ArrayList<>();
        for (int i = 0; i < Kit.SLOTS; i++) {
            if (!Kit.isFilled(items[i])) continue;
            if (Kit.isFilled(inv.getItem(INVENTORY_OFFSET + i))) occupied.add(i);
        }

        if (!occupied.isEmpty() && !force) {
            error(sender, ERR_NO_SPACE, get("no_space",
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
        startCooldown(kit, target);

        // ── Feedback ──
        if (occupied.isEmpty()) {
            send(sender, get("given",
                    "<green>✔</green> <white>Kit</white> <yellow>%kit%</yellow> <white>given to</white> "
                            + "<yellow>%player%</yellow><white>.</white>",
                    "%kit%", kit.name(), "%player%", target.getName()));
        } else {
            send(sender, get("given_force",
                    "<green>✔</green> <white>Kit</white> <yellow>%kit%</yellow> <white>force-given to</white> "
                            + "<yellow>%player%</yellow><white>: overwritten</white> <red>%count%</red> "
                            + "<white>slot(s).</white>",
                    "%kit%", kit.name(), "%player%", target.getName(),
                    "%count%", String.valueOf(occupied.size())));
            send(sender, get("overwritten",
                    "<gray>↳ overwritten slots: </gray><yellow>%slots%</yellow>",
                    "%slots%", slotList(occupied)));
        }

        if (!(sender instanceof Player giverPlayer) || target != giverPlayer) {
            send(target, get("received",
                    "<green>✔</green> <white>You received kit</white> <yellow>%kit%</yellow> "
                            + "<white>from</white> <yellow>%player%</yellow><white>.</white>",
                    "%kit%", kit.name(), "%player%", senderName(sender)));
        }
        return true;
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    public static boolean create(CommandSender sender, String rawName, String rawDescription) {
        String key = Kit.keyOf(rawName);
        if (key.isEmpty()) {
            error(sender, ERR_INVALID_INPUT, get("invalid_name",
                    "<red>The kit name must not be empty!</red>"));
            return false;
        }
        int maxName = config().getInt("kits.max_name_length", 48);
        int maxDesc = config().getInt("kits.max_description_length", 128);
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() > maxName || (rawDescription != null && rawDescription.length() > maxDesc)) {
            error(sender, ERR_INVALID_INPUT, get("input_too_long",
                    "<red>Name must be ≤</red><yellow>%name%</yellow><red> and description ≤</red>"
                            + "<yellow>%desc%</yellow><red> characters.</red>",
                    "%name%", String.valueOf(maxName), "%desc%", String.valueOf(maxDesc)));
            return false;
        }
        if (KitStore.exists(rawName)) {
            error(sender, ERR_KIT_EXISTS, get("kit_exists",
                    "<red>A kit named</red> <white>%kit%</white> <red>already exists!</red>",
                    "%kit%", plainOr(rawName)));
            return false;
        }

        Kit kit = new Kit(key, name, rawDescription == null ? "" : rawDescription.trim(),
                new ItemStack[Kit.SLOTS], senderName(sender),
                System.currentTimeMillis(), System.currentTimeMillis(), 0, 0);
        if (!KitStore.save(kit)) {
            CommandErrors.unknownError(sender);
            return false;
        }
        send(sender, get("created",
                "<green>✔</green> <white>Kit created:</white> <yellow>%kit%</yellow>"
                        + "<gray> (use </gray><white>/ui kit import</white><gray> to fill it)</gray>",
                "%kit%", kit.name()));
        return true;
    }

    // =========================================================================
    // RENAME
    // =========================================================================

    public static boolean rename(CommandSender sender, String rawOld, String rawNew) {
        String oldKey = Kit.keyOf(rawOld);
        String newKey = Kit.keyOf(rawNew);
        if (oldKey.isEmpty() || newKey.isEmpty()) {
            error(sender, ERR_INVALID_INPUT, get("invalid_name",
                    "<red>The kit name must not be empty!</red>"));
            return false;
        }
        int maxName = config().getInt("kits.max_name_length", 48);
        String newName = rawNew == null ? "" : rawNew.trim();
        if (newName.length() > maxName) {
            error(sender, ERR_INVALID_INPUT, get("input_too_long",
                    "<red>Name must be ≤</red><yellow>%name%</yellow><red> and description ≤</red>"
                            + "<yellow>%desc%</yellow><red> characters.</red>",
                    "%name%", String.valueOf(maxName),
                    "%desc%", String.valueOf(config().getInt("kits.max_description_length", 128))));
            return false;
        }

        Kit kit = KitStore.getByKey(oldKey);
        if (kit == null) {
            error(sender, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawOld)));
            return false;
        }
        if (!newKey.equals(oldKey) && KitStore.getByKey(newKey) != null) {
            error(sender, ERR_KIT_EXISTS, get("kit_exists",
                    "<red>A kit named</red> <white>%kit%</white> <red>already exists!</red>",
                    "%kit%", plainOr(rawNew)));
            return false;
        }

        if (!KitStore.rename(kit, newKey, newName)) {
            CommandErrors.unknownError(sender);
            return false;
        }
        send(sender, get("renamed",
                "<green>✔</green> <white>Kit</white> <yellow>%old%</yellow> <white>renamed to</white> "
                        + "<yellow>%new%</yellow><white>.</white>",
                "%old%", kit.name(), "%new%", newName));
        return true;
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    public static boolean delete(CommandSender sender, String rawKit, boolean confirmed) {
        if (!confirmed) {
            error(sender, ERR_NOT_CONFIRMED, get("not_confirmed_console",
                    "<red>Deletion was not confirmed — tick the checkbox "
                            + "(console: append </red><white>-y</white><red>).</red>"));
            return false;
        }
        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(sender, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }
        if (!KitStore.delete(kit.key())) {
            CommandErrors.unknownError(sender);
            return false;
        }
        send(sender, get("deleted",
                "<green>✔</green> <white>Kit deleted:</white> <yellow>%kit%</yellow>",
                "%kit%", kit.name()));
        return true;
    }

    // =========================================================================
    // IMPORT (shulker in the main hand → kit)
    // =========================================================================

    /**
     * Copies the shulker box from a player's main hand into the kit.
     *
     * @param sender    executor (player = own hand, console = the named player)
     * @param rawKit    the kit name
     * @param rawPlayer the holder nickname; blank = the sender himself
     */
    public static boolean importFromHand(CommandSender sender, String rawKit, String rawPlayer) {
        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(sender, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }

        Player holder = resolveTarget(sender, rawPlayer, true);
        if (holder == null) return false;

        ItemStack inHand = holder.getInventory().getItemInMainHand();
        if (!isShulker(inHand)) {
            error(sender, ERR_NO_SHULKER, get("no_shulker",
                    "<red>You must hold a shulker box in your main hand!</red>"));
            return false;
        }

        ItemStack[] contents = readShulker(inHand);
        int filled = 0;
        for (ItemStack item : contents) {
            if (Kit.isFilled(item)) filled++;
        }
        if (filled == 0) {
            error(sender, ERR_SHULKER_EMPTY, get("shulker_empty",
                    "<red>The shulker box in your hand is empty!</red>"));
            return false;
        }

        // The shulker box itself stays untouched in the player's hand.
        Kit updated = new Kit(kit.key(), kit.name(), kit.description(), contents,
                kit.createdBy(), kit.createdAt(), System.currentTimeMillis(),
                kit.cooldownSeconds(), kit.globalCooldownSeconds());
        if (!KitStore.save(updated)) {
            CommandErrors.unknownError(sender);
            return false;
        }
        send(sender, get("imported",
                "<green>✔</green> <white>Copied</white> <yellow>%count%</yellow> "
                        + "<white>item(s) from the shulker box into kit</white> <yellow>%kit%</yellow><white>.</white>",
                "%kit%", kit.name(), "%count%", String.valueOf(filled)));
        if (!(sender instanceof Player senderPlayer) || holder != senderPlayer) {
            send(holder, get("imported_holder",
                    "<gray>✦ Your shulker box was copied into kit</gray> <yellow>%kit%</yellow>"
                            + " <gray>by</gray> <yellow>%player%</yellow><gray>.</gray>",
                    "%kit%", kit.name(), "%player%", senderName(sender)));
        }
        return true;
    }

    // =========================================================================
    // EXPORT (kit → shulker into the recipient's main hand)
    // =========================================================================

    /**
     * Builds a shulker box from the kit and hands it to a player.
     *
     * @param sender    executor (player = own inventory, console = the named player)
     * @param rawKit    the kit name
     * @param rawPlayer the recipient nickname; blank = the sender himself
     * @param force     {@code -f}: overwrite the item in the recipient's main hand
     */
    public static boolean exportToHand(CommandSender sender, String rawKit, String rawPlayer, boolean force) {
        if (force && !sender.hasPermission(Permissions.CMD_KIT_GIVE_FORCE)) {
            CommandErrors.noPermission(sender, Permissions.CMD_KIT_GIVE_FORCE);
            return false;
        }

        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(sender, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }
        if (!kit.hasItems()) {
            error(sender, ERR_KIT_EMPTY, get("kit_empty",
                    "<red>Kit</red> <white>%kit%</white> <red>has no items — import a shulker box first!</red>",
                    "%kit%", kit.name()));
            return false;
        }

        Player target = resolveTarget(sender, rawPlayer, true);
        if (target == null) return false;

        // A player exporting to someone else needs the third-party node as well.
        if (!(sender instanceof Player self) || target != self) {
            if (!sender.hasPermission(Permissions.CMD_KIT_GIVE_OTHER)) {
                CommandErrors.noPermission(sender, Permissions.CMD_KIT_GIVE_OTHER);
                return false;
            }
        }

        if (cooldownBlocked(sender, kit, target)) return false;

        ItemStack shulker = buildShulker(kit);
        PlayerInventory inv = target.getInventory();
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
            error(sender, ERR_NO_SPACE, get("no_space_export",
                    "<red>No free slot for the shulker box — free a slot or use </red>"
                            + "<white>/ui kit export -f</white><red>.</red>"));
            return false;
        }
        target.updateInventory();
        startCooldown(kit, target);

        send(sender, get("exported",
                "<green>✔</green> <white>Shulker box with kit</white> <yellow>%kit%</yellow> "
                        + "<white>handed to</white> <yellow>%player%</yellow><white>.</white>",
                "%kit%", kit.name(), "%player%", target.getName()));
        if (!(sender instanceof Player self) || target != self) {
            send(target, get("exported_received",
                    "<green>✔</green> <white>You received a shulker box with kit</white> "
                            + "<yellow>%kit%</yellow> <white>from</white> <yellow>%player%</yellow><white>.</white>",
                    "%kit%", kit.name(), "%player%", senderName(sender)));
        }
        return true;
    }

    // =========================================================================
    // COOLDOWN
    // =========================================================================

    /**
     * Sets or clears the per-recipient cooldown of a kit.
     *
     * @param rawSeconds whole seconds, {@code 0} removes the cooldown
     */
    public static boolean setCooldown(CommandSender sender, String rawKit, String rawSeconds) {
        if (!sender.hasPermission(Permissions.CMD_KIT_COOLDOWN)) {
            CommandErrors.noPermission(sender, Permissions.CMD_KIT_COOLDOWN);
            return false;
        }
        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(sender, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }

        long seconds = parseSeconds(rawSeconds);
        int max = maxCooldownSeconds();
        if (seconds < 0 || seconds > max) {
            error(sender, ERR_INVALID_COOLDOWN, invalidCooldownText(max));
            return false;
        }

        Kit updated = new Kit(kit.key(), kit.name(), kit.description(), kit.items(),
                kit.createdBy(), kit.createdAt(), System.currentTimeMillis(),
                (int) seconds, kit.globalCooldownSeconds());
        if (!KitStore.save(updated)) {
            CommandErrors.unknownError(sender);
            return false;
        }

        if (seconds == 0) {
            // Disabling the cooldown also stops the timers that are still running.
            KitStore.clearKitPlayerCooldowns(kit.key());
            send(sender, get("cooldown_removed",
                    "<green>✔</green> <white>Cooldown of kit</white> <yellow>%kit%</yellow> "
                            + "<white>removed.</white>",
                    "%kit%", kit.name()));
        } else {
            send(sender, get("cooldown_set",
                    "<green>✔</green> <white>Cooldown of kit</white> <yellow>%kit%</yellow> "
                            + "<white>set to</white> <yellow>%seconds%</yellow> <white>second(s).</white>",
                    "%kit%", kit.name(), "%seconds%", String.valueOf(seconds)));
        }
        return true;
    }

    /**
     * Sets or clears the server-wide cooldown of a kit — once anyone receives
     * the kit, nobody may receive it until the cooldown expires.
     *
     * @param rawSeconds whole seconds, {@code 0} removes the cooldown
     */
    public static boolean setGlobalCooldown(CommandSender sender, String rawKit, String rawSeconds) {
        if (!sender.hasPermission(Permissions.CMD_KIT_GLOBALCD)) {
            CommandErrors.noPermission(sender, Permissions.CMD_KIT_GLOBALCD);
            return false;
        }
        Kit kit = KitStore.get(rawKit);
        if (kit == null) {
            error(sender, ERR_KIT_NOT_FOUND, get("kit_not_found",
                    "<red>Kit</red> <white>\"%kit%\"</white> <red>not found!</red>",
                    "%kit%", plainOr(rawKit)));
            return false;
        }

        long seconds = parseSeconds(rawSeconds);
        int max = maxCooldownSeconds();
        if (seconds < 0 || seconds > max) {
            error(sender, ERR_INVALID_COOLDOWN, invalidCooldownText(max));
            return false;
        }

        Kit updated = new Kit(kit.key(), kit.name(), kit.description(), kit.items(),
                kit.createdBy(), kit.createdAt(), System.currentTimeMillis(),
                kit.cooldownSeconds(), (int) seconds);
        if (!KitStore.save(updated)) {
            CommandErrors.unknownError(sender);
            return false;
        }

        if (seconds == 0) {
            KitStore.clearGlobalCooldown(kit.key());
            send(sender, get("globalcd_removed",
                    "<green>✔</green> <white>Global cooldown of kit</white> <yellow>%kit%</yellow> "
                            + "<white>removed.</white>",
                    "%kit%", kit.name()));
        } else {
            send(sender, get("globalcd_set",
                    "<green>✔</green> <white>Global cooldown of kit</white> <yellow>%kit%</yellow> "
                            + "<white>set to</white> <yellow>%seconds%</yellow> <white>second(s).</white>",
                    "%kit%", kit.name(), "%seconds%", String.valueOf(seconds)));
        }
        return true;
    }

    // =========================================================================
    // RESET COOLDOWNS
    // =========================================================================

    /**
     * Clears every per-player kit cooldown of one player. Global (server-wide)
     * cooldowns belong to the kit and are left untouched.
     *
     * @param confirmed the confirmation checkbox / the {@code -c} flag
     */
    public static boolean resetCooldowns(CommandSender sender, String rawPlayer, boolean confirmed) {
        if (!sender.hasPermission(Permissions.CMD_KIT_RESETCD)) {
            CommandErrors.noPermission(sender, Permissions.CMD_KIT_RESETCD);
            return false;
        }
        if (!confirmed) {
            error(sender, ERR_NOT_CONFIRMED, get("not_confirmed_reset",
                    "<red>Reset was not confirmed — tick the checkbox "
                            + "(console: append </red><white>-c</white><red>).</red>"));
            return false;
        }

        String name = rawPlayer == null ? "" : rawPlayer.trim();
        if (name.isEmpty()) {
            error(sender, ERR_TARGET_REQUIRED, get("resetcd_target_required",
                    "<red>Specify the player whose kit cooldowns should be reset!</red>"));
            return false;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            error(sender, ERR_PLAYER_NOT_FOUND, get("player_not_found",
                    "<red>Player</red> <white>\"%player%\"</white> <red>is not online!</red>",
                    "%player%", name));
            return false;
        }

        int cleared = KitStore.clearPlayerCooldowns(target.getUniqueId());
        send(sender, get("resetcd_done",
                "<green>✔</green> <white>Kit cooldowns reset for</white> <yellow>%player%</yellow> "
                        + "<gray>(</gray><yellow>%count%</yellow> <gray>cooldown(s) cleared).</gray>",
                "%player%", target.getName(), "%count%", String.valueOf(cleared)));
        if (!(sender instanceof Player self) || target != self) {
            send(target, get("resetcd_notify",
                    "<gray>✦ Your kit cooldowns were reset by</gray> <yellow>%player%</yellow><gray>.</gray>",
                    "%player%", senderName(sender)));
        }
        return true;
    }

    /** Parses a whole non-negative number of seconds; -1 when the text is not a number. */
    private static long parseSeconds(String raw) {
        if (raw == null) return -1;
        String text = raw.trim();
        if (text.isEmpty()) return -1;
        try {
            long value = Long.parseLong(text);
            return value < 0 ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int maxCooldownSeconds() {
        return Math.max(0, config().getInt("kits.max_cooldown_seconds", DEFAULT_MAX_COOLDOWN_SECONDS));
    }

    private static String invalidCooldownText(int max) {
        return get("invalid_cooldown",
                "<red>The cooldown must be a whole number of seconds between</red> <yellow>0</yellow> "
                        + "<red>and</red> <yellow>%max%</yellow><red> (0 = no cooldown).</red>",
                "%max%", String.valueOf(max));
    }

    /** Reports error 031 when the recipient is still on the kit's cooldown. */
    private static boolean cooldownBlocked(CommandSender sender, Kit kit, Player target) {
        // Global (server-wide) cooldown first — it blocks everyone.
        if (kit.hasGlobalCooldown() && !target.hasPermission(Permissions.CMD_KIT_GLOBALCD_BYPASS)) {
            long globalRemaining = KitStore.remainingGlobalCooldownSeconds(kit.key());
            if (globalRemaining > 0) {
                error(sender, ERR_COOLDOWN, get("globalcd_active",
                        "<red>Kit</red> <white>%kit%</white> <red>is on a global cooldown — </red>"
                                + "<yellow>%seconds%</yellow><red>s left.</red>",
                        "%kit%", kit.name(), "%seconds%", String.valueOf(globalRemaining)));
                return true;
            }
        }
        if (!kit.hasCooldown() || target.hasPermission(Permissions.CMD_KIT_COOLDOWN_BYPASS)) return false;
        long remaining = KitStore.remainingCooldownSeconds(target.getUniqueId(), kit.key());
        if (remaining <= 0) return false;
        error(sender, ERR_COOLDOWN, get("cooldown_active",
                "<red>Kit</red> <white>%kit%</white> <red>is on cooldown for</red> <white>%player%</white>"
                        + "<red> — </red><yellow>%seconds%</yellow><red>s left.</red>",
                "%kit%", kit.name(), "%player%", target.getName(),
                "%seconds%", String.valueOf(remaining)));
        return true;
    }

    /** Starts the kit cooldown for the recipient (no-op for cooldown-free kits). */
    /** Starts the kit cooldowns for the recipient (no-op for cooldown-free kits). */
    private static void startCooldown(Kit kit, Player target) {
        if (kit.hasGlobalCooldown() && !target.hasPermission(Permissions.CMD_KIT_GLOBALCD_BYPASS)) {
            KitStore.markGlobalCooldown(kit.key());
        }
        if (kit.hasCooldown() && !target.hasPermission(Permissions.CMD_KIT_COOLDOWN_BYPASS)) {
            KitStore.markCooldown(target.getUniqueId(), kit.key());
        }
    }

    // =========================================================================
    // LIST
    // =========================================================================

    /** Renders one page of the kit list ({@code kits.list_page_size} rows). */
    public static boolean list(CommandSender sender, int page) {
        List<Kit> kits = KitStore.all();
        int perPage = Math.max(1, config().getInt("kits.list_page_size", 7));
        int totalPages = Math.max(1, (kits.size() + perPage - 1) / perPage);
        int current = Math.max(1, Math.min(page, totalPages));

        send(sender, get("list_header",
                "<gold>══════════════════════════════════\n"
                        + "  ✦ <white>Kits</white> <gray>(%total%)</gray>\n"
                        + "══════════════════════════════════",
                "%total%", String.valueOf(kits.size())));

        if (kits.isEmpty()) {
            send(sender, get("list_empty",
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
            Component rowComponent;
            if (nameAt < 0) {
                rowComponent = MessageUtil.parse(row, playerOrNull(sender));
            } else {
                Component prefix = MessageUtil.parse(row.substring(0, nameAt));
                Component suffix = MessageUtil.parse(row.substring(nameAt + "%kit%".length()));
                rowComponent = prefix.append(kit.displayName()).append(suffix);
            }
            if (kit.hasCooldown()) {
                rowComponent = rowComponent.append(MessageUtil.parse(get("list_row_cooldown",
                        " <gold>[</gold><yellow>%seconds%s</yellow><gold>]</gold>")
                        .replace("%seconds%", String.valueOf(kit.cooldownSeconds()))));
            }
            if (kit.hasGlobalCooldown()) {
                rowComponent = rowComponent.append(MessageUtil.parse(get("list_row_global_cooldown",
                        " <gold>[</gold><yellow>global %seconds%s</yellow><gold>]</gold>")
                        .replace("%seconds%", String.valueOf(kit.globalCooldownSeconds()))));
            }
            sender.sendMessage(rowComponent);
        }

        String footer = get("list_footer", "<gold>══════════════════════════════════");
        sender.sendMessage(MessageUtil.parse(footer));

        if (totalPages > 1) {
            String prev = get("list_prev", "<yellow>◀ Prev</yellow>");
            String next = get("list_next", "<yellow>Next ▶</yellow>");
            String pageLabel = get("list_page", "<gray>page %current%/%total%</gray>")
                    .replace("%current%", String.valueOf(current))
                    .replace("%total%", String.valueOf(totalPages))
                    .replace("%per_page%", String.valueOf(perPage));

            // Clickable navigation only makes sense for players (chat is the
            // only surface with click events); the console gets the plain hint.
            if (sender instanceof Player) {
                Component prevComp = current > 1
                        ? MessageUtil.parse(prev).clickEvent(ClickEvent.runCommand("/ui kit list " + (current - 1)))
                        : Component.empty();
                Component nextComp = current < totalPages
                        ? MessageUtil.parse(next).clickEvent(ClickEvent.runCommand("/ui kit list " + (current + 1)))
                        : Component.empty();
                sender.sendMessage(prevComp
                        .append(Component.text("  "))
                        .append(MessageUtil.parse(pageLabel))
                        .append(Component.text("  "))
                        .append(nextComp));
            } else {
                sender.sendMessage(MessageUtil.parse(pageLabel));
            }
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

    /**
     * Resolves the player a kit operation works with.
     * <p>
     * Blank nickname = the sender himself, but only for player senders —
     * console has no hand/inventory, so it must name the player (error 033).
     * A named but offline player is error 026.
     *
     * @param allowSelf true when a blank nickname may resolve to a player sender;
     *                  false when a blank nickname is simply "no target"
     */
    private static Player resolveTarget(CommandSender sender, String rawPlayer, boolean allowSelf) {
        String name = rawPlayer == null ? "" : rawPlayer.trim();
        if (name.isEmpty()) {
            if (allowSelf && sender instanceof Player player) return player;
            error(sender, ERR_TARGET_REQUIRED, get("target_required",
                    "<red>This form of the command must name a player "
                            + "(console has no inventory / main hand)!</red>"));
            return null;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            error(sender, ERR_PLAYER_NOT_FOUND, get("player_not_found",
                    "<red>Player</red> <white>\"%player%\"</white> <red>is not online!</red>",
                    "%player%", name));
            return null;
        }
        return target;
    }

    /** Display name of a sender for feedback texts (console → "console"). */
    private static String senderName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "console";
    }

    /** Player form of a sender, used only for MiniMessage placeholders. */
    private static Player playerOrNull(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

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

    private static void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(MessageUtil.parse(miniMessage, playerOrNull(sender)));
    }

    private static void error(CommandSender sender, int code, String miniMessage) {
        CommandErrors.custom(sender, code, miniMessage);
    }
}
