package com.ultimateimprovments.mechanics.features.kits;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * KitDialogs — the six Custom Screen windows of {@code /ui kit}.
 * <ul>
 *   <li>{@link #openGive(Player, boolean)} — kit name + optional recipient;</li>
 *   <li>{@link #openCreate(Player)} — kit name + description;</li>
 *   <li>{@link #openRename(Player)} — old name + new name;</li>
 *   <li>{@link #openDelete(Player)} — kit name + confirmation checkbox;</li>
 *   <li>{@link #openImport(Player)} — kit name (contents come from the main hand);</li>
 *   <li>{@link #openExport(Player, boolean)} — kit name (result goes to the main hand);</li>
 *   <li>{@link #openCooldown(Player)} — kit name + cooldown in seconds.</li>
 * </ul>
 * <p>
 * All dialogs use {@link DialogAction#CLOSE} (NOT {@code WAIT_FOR_RESPONSE}): with
 * {@code WAIT_FOR_RESPONSE} the client switches to the separate
 * {@code WaitingForResponseScreen} ("Waiting for server…") after a click and
 * ignores {@code ClientboundClearDialogPacket}, so the window would hang for ~4
 * seconds. With {@code CLOSE} the client closes the window itself immediately
 * after the click (the click-with-input packet is still sent) and the server only
 * performs the operation — errors are then reported as command errors 022-030.
 */
public final class KitDialogs {

    private static final String NS = "ultimateimprovments";

    /** Give → submit (no force). */
    public static final Identifier GIVE_SUBMIT = id("kit_give_submit");
    /** Give → submit with {@code -f}. */
    public static final Identifier GIVE_SUBMIT_FORCE = id("kit_give_submit_force");
    /** Create → submit. */
    public static final Identifier CREATE_SUBMIT = id("kit_create_submit");
    /** Rename → submit. */
    public static final Identifier RENAME_SUBMIT = id("kit_rename_submit");
    /** Delete → submit. */
    public static final Identifier DELETE_SUBMIT = id("kit_delete_submit");
    /** Import → submit. */
    public static final Identifier IMPORT_SUBMIT = id("kit_import_submit");
    /** Export → submit (no force). */
    public static final Identifier EXPORT_SUBMIT = id("kit_export_submit");
    /** Export → submit with {@code -f}. */
    public static final Identifier EXPORT_SUBMIT_FORCE = id("kit_export_submit_force");
    /** Cooldown → submit. */
    public static final Identifier COOLDOWN_SUBMIT = id("kit_cooldown_submit");
    /** Cancel (any dialog). */
    public static final Identifier CANCEL = id("kit_cancel");

    /** Input keys read back through {@code DialogResponseView}. */
    public static final String KEY_NAME = "kit_name";
    public static final String KEY_TARGET = "kit_target";
    public static final String KEY_DESCRIPTION = "kit_description";
    public static final String KEY_OLD = "kit_old";
    public static final String KEY_NEW = "kit_new";
    public static final String KEY_CONFIRM = "kit_confirm";
    public static final String KEY_SECONDS = "kit_seconds";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private KitDialogs() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    // =========================================================================
    // PUBLIC OPENERS
    // =========================================================================

    /** Give: kit name + recipient (empty recipient = yourself). */
    public static void openGive(Player player, boolean force) {
        open(player,
                "give", "<white>Enter the kit name and the recipient (leave it empty to give the kit to yourself):</white>",
                List.of(
                        input(KEY_NAME, cfgString("kits.dialog.give.kit_label", "<gray>Kit name</gray>"), maxName()),
                        input(KEY_TARGET, cfgString("kits.dialog.give.target_label",
                                "<gray>Recipient (empty = yourself)</gray>"), maxName())),
                force ? GIVE_SUBMIT_FORCE : GIVE_SUBMIT);
    }

    /** Create: kit name + description (both accept MiniMessage). */
    public static void openCreate(Player player) {
        open(player,
                "create", "<white>Enter the new kit name and its description "
                        + "<gray>(MiniMessage tags are allowed)</gray><white>:</white>",
                List.of(
                        input(KEY_NAME, cfgString("kits.dialog.create.name_label", "<gray>Kit name</gray>"), maxName()),
                        input(KEY_DESCRIPTION, cfgString("kits.dialog.create.description_label",
                                "<gray>Description</gray>"), maxDescription())),
                CREATE_SUBMIT);
    }

    /** Rename: old name + new name. */
    public static void openRename(Player player) {
        open(player,
                "rename", "<white>Enter the current kit name and the new one:</white>",
                List.of(
                        input(KEY_OLD, cfgString("kits.dialog.rename.old_label", "<gray>Current name</gray>"), maxName()),
                        input(KEY_NEW, cfgString("kits.dialog.rename.new_label", "<gray>New name</gray>"), maxName())),
                RENAME_SUBMIT);
    }

    /** Delete: kit name + a confirmation checkbox. */
    public static void openDelete(Player player) {
        var checkboxLabel = nativeText(cfgString("kits.dialog.delete.checkbox_label",
                "<red>I really want to delete this kit</red>"));
        BooleanInput confirmInput = new BooleanInput(checkboxLabel, false, "true", "false");

        open(player,
                "delete", "<white>Enter the kit name and tick the confirmation checkbox:</white>",
                List.of(
                        input(KEY_NAME, cfgString("kits.dialog.delete.kit_label", "<gray>Kit name</gray>"), maxName()),
                        new Input(KEY_CONFIRM, confirmInput)),
                DELETE_SUBMIT);
    }

    /** Import: kit name; the contents are read from the shulker box in the main hand. */
    public static void openImport(Player player) {
        open(player,
                "import", "<white>Enter the kit name that the shulker box in your </white>"
                        + "<yellow>main hand</yellow><white> should be copied into:</white>",
                List.of(input(KEY_NAME, cfgString("kits.dialog.import.kit_label", "<gray>Kit name</gray>"), maxName())),
                IMPORT_SUBMIT);
    }

    /** Export: kit name; the shulker box is placed into the main hand. */
    public static void openExport(Player player, boolean force) {
        open(player,
                "export", "<white>Enter the kit name that should be exported into a shulker box:</white>",
                List.of(input(KEY_NAME, cfgString("kits.dialog.export.kit_label", "<gray>Kit name</gray>"), maxName())),
                force ? EXPORT_SUBMIT_FORCE : EXPORT_SUBMIT);
    }

    /** Cooldown: kit name + cooldown in seconds (0 = no cooldown). */
    public static void openCooldown(Player player) {
        open(player,
                "cooldown", "<white>Enter the kit name and its cooldown in seconds "
                        + "<gray>(0 = no cooldown)</gray><white>:</white>",
                List.of(
                        input(KEY_NAME, cfgString("kits.dialog.cooldown.kit_label", "<gray>Kit name</gray>"), maxName()),
                        input(KEY_SECONDS, cfgString("kits.dialog.cooldown.seconds_label",
                                "<gray>Cooldown (seconds, 0 = off)</gray>"), 12)),
                COOLDOWN_SUBMIT);
    }

    // =========================================================================
    // BUILDER
    // =========================================================================

    private static void open(Player player, String dialogKey, String bodyDef,
                             List<Input> inputs, Identifier submitId) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[Kits] Cannot open the dialog for a non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        var title = nativeText(cfgString("kits.dialog.title", "<gold>✦ Kits</gold>"));
        var externalTitle = nativeText(cfgString("kits.dialog.external_title", "<gray>Kit manager</gray>"));

        String body = cfgString("kits.dialog." + dialogKey + ".body", bodyDef);
        var bodyText = nativeText(body);

        var confirmLabel = nativeText(cfgString("kits.dialog.confirm_button", "<green>✔ Confirm</green>"));
        var cancelLabel = nativeText(cfgString("kits.dialog.cancel_button", "<red>❌ Cancel</red>"));

        ActionButton confirmBtn = new ActionButton(
                new CommonButtonData(confirmLabel, 150),
                Optional.of(new CustomAll(submitId, Optional.empty())));
        ActionButton cancelBtn = new ActionButton(
                new CommonButtonData(cancelLabel, 150),
                Optional.of(new CustomAll(CANCEL, Optional.empty())));

        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                 // canCloseWithEscape
                true,                 // pause
                DialogAction.CLOSE,   // the client closes the window itself, instantly (no "Waiting for server")
                List.of(new PlainMessage(bodyText, 310)),
                inputs);

        MultiActionDialog dialog = new MultiActionDialog(
                data,
                List.of(confirmBtn),
                Optional.of(cancelBtn),
                1);

        serverPlayer.openDialog(Holder.direct(dialog));
    }

    private static Input input(String key, String labelMiniMessage, int maxLength) {
        TextInput field = new TextInput(200, nativeText(labelMiniMessage), false, "", maxLength, Optional.empty());
        return new Input(key, field);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static int maxName() {
        return Math.max(1, Main.getInstance().getConfig().getInt("kits.max_name_length", 48));
    }

    private static int maxDescription() {
        return Math.max(1, Main.getInstance().getConfig().getInt("kits.max_description_length", 128));
    }

    private static String cfgString(String key, String def) {
        String value = Main.getInstance().getConfig().getString(key, def);
        return value == null ? def : value;
    }

    /** Adventure Component → Minecraft Component through §-codes (project convention). */
    private static net.minecraft.network.chat.Component nativeText(String miniMessage) {
        return nativeText(MM.deserialize(miniMessage));
    }

    private static net.minecraft.network.chat.Component nativeText(Component adventure) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(adventure);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
