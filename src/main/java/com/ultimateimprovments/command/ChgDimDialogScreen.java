package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * ChgDimDialogScreen — создаёт и открывает Custom Screen (Dialog) для телепортации между мирами.
 * <p>
 * Использует Minecraft 26.2 Dialog API для показа экрана с полем ввода названия мира
 * и кнопками «✔ Teleport» / «✖ Cancel».
 * <p>
 * При ошибке (мир не найден, нет прав, кулдаун) вызывается повторно с текстом ошибки,
 * который отображается прямо в диалоговом окне.
 */
public class ChgDimDialogScreen {

    /** Идентификатор CustomAll действия для телепортации. */
    public static final Identifier CHGDIM_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "chgdim_submit");
    /** Идентификатор CustomAll действия для отмены. */
    public static final Identifier CHGDIM_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "chgdim_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ChgDimDialogScreen() {}

    /**
     * Открывает экран телепортации для игрока.
     *
     * @param player целевой игрок
     * @param errorMessage опциональное сообщение об ошибке (null если нет ошибки)
     */
    public static void open(Player player, String errorMessage) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[ChgDimDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Заголовки ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.title",
                "<gold>✦ Change Dimension</gold>"))
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Teleport to World</gray>")
        );

        // ─── Текст тела ───
        String bodyStr;
        if (errorMessage != null && !errorMessage.isEmpty()) {
            // Показываем ошибку прямо в диалоге
            bodyStr = "<red>❌ " + errorMessage + "</red>\n\n<white>Enter a valid world name:</white>";
        } else {
            bodyStr = Main.getInstance().getConfig().getString(
                "changedimmension.dialog.body",
                "<white>Where do you want to teleport?</white>");
        }
        net.minecraft.network.chat.Component bodyText = toNative(MM.deserialize(bodyStr));

        // ─── Поле ввода названия мира ───
        net.minecraft.network.chat.Component inputLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.world_label",
                "<gray>World Name</gray>"))
        );
        TextInput worldInput = new TextInput(200, inputLabel, false, "", 64, Optional.empty());

        // ─── Кнопки ───
        net.minecraft.network.chat.Component tpLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.tp_button",
                "<green>✔ Teleport</green>"))
        );
        net.minecraft.network.chat.Component cancelLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString(
                "changedimmension.dialog.cancel_button",
                "<red>✖ Cancel</red>"))
        );

        // TP → CustomAll action (отправляет название мира)
        ActionButton tpBtn = new ActionButton(
            new CommonButtonData(tpLabel, 150),
            Optional.of(new CustomAll(CHGDIM_SUBMIT_ID, Optional.empty()))
        );
        // Cancel → CustomAll action (закрывает диалог без телепортации)
        ActionButton cancelBtn = new ActionButton(
            new CommonButtonData(cancelLabel, 150),
            Optional.of(new CustomAll(CHGDIM_CANCEL_ID, Optional.empty()))
        );

        // ─── Сборка диалога ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            true,                           // canCloseWithEscape — можно закрыть ESC
            true,                           // pause
            DialogAction.WAIT_FOR_RESPONSE,  // afterAction — ждём ввод
            List.of(new PlainMessage(bodyText, 310)),
            List.of(new Input("world_name", worldInput))
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(tpBtn),     // mainActions
            Optional.of(cancelBtn), // exitAction
            1                       // columns
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[ChgDimDialog] Opened dimension change screen for " + player.getName());
    }

    /**
     * Открывает экран телепортации без ошибки (чистый диалог).
     */
    public static void open(Player player) {
        open(player, null);
    }

    /**
     * Закрывает открытый диалог у игрока.
     */
    public static void close(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        if (craftPlayer.getHandle().connection == null) return;
        craftPlayer.getHandle().connection.send(
            net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE
        );
    }

    /**
     * Преобразует Adventure Component → Minecraft Component.
     * Сериализует через legacy section-коды, затем создаёт Minecraft Component.
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
