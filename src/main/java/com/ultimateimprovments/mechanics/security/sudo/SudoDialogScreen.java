package com.ultimateimprovments.mechanics.security.sudo;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
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
 * SudoDialogScreen — «второе диалоговое окно авторизации»: Custom Screen
 * с полем для sudo-пароля (по образцу {@code AuthDialogScreen}).
 * <p>
 * Два режима:
 * <ul>
 *   <li>{@code registered=true} — игрок уже задавал sudo-пароль → ввод пароля;</li>
 *   <li>{@code registered=false} — пароля нет → просьба задать пароль и не забывать его.</li>
 * </ul>
 * <p>
 * Диалог использует {@link DialogAction#CLOSE} (а НЕ {@link DialogAction#WAIT_FOR_RESPONSE}):
 * при {@code WAIT_FOR_RESPONSE} клиент после клика уходит на экран «Waiting for server…»
 * и игнорирует {@code ClientboundClearDialogPacket}, поэтому окно висело бы ~4 секунды.
 * С {@code CLOSE} клиент закрывает окно СРАЗУ после клика сам, а сервер переоткрывает
 * его или выполняет действие (sudo-команды всё равно перехватываются, пока сессия не активна).
 */
public class SudoDialogScreen {

    /** Идентификатор CustomAll действия для отправки sudo-пароля. */
    public static final Identifier SUDO_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "sudo_submit");
    /** Идентификатор CustomAll действия для отмены (закрыть диалог). */
    public static final Identifier SUDO_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "sudo_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private SudoDialogScreen() {}

    /**
     * Открывает диалог sudo-пароля.
     *
     * @param player     игрок
     * @param registered есть ли уже sudo-пароль (true → вход, false → создание)
     */
    public static void open(Player player, boolean registered) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[SudoDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Заголовки ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.sudo.dialog.title",
                "<gold>✦ Sudo Mode Required</gold>"))
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Server Sudo</gray>")
        );

        // ─── Текст тела ───
        net.minecraft.network.chat.Component bodyText = toNative(
            MM.deserialize(registered
                ? Main.getInstance().getConfig().getString("messages.sudo.dialog.body_login",
                    "<white>Enter your sudo password to continue.</white>")
                : Main.getInstance().getConfig().getString("messages.sudo.dialog.body_register",
                    "<white>Set a sudo password. <yellow>Do not forget it!</yellow></white>"))
        );

        // ─── Поле ввода пароля ───
        net.minecraft.network.chat.Component pwLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.sudo.dialog.password_label",
                "<gray>Sudo Password</gray>"))
        );
        int maxPwLen = Main.getInstance().getConfig().getInt("auth.max_password_length", 32);
        TextInput passwordInput = new TextInput(200, pwLabel, true, "", maxPwLen, Optional.empty());

        // ─── Кнопки ───
        net.minecraft.network.chat.Component continueLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.sudo.dialog.continue_button",
                "<green>✔ Continue</green>"))
        );
        net.minecraft.network.chat.Component cancelLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.sudo.dialog.cancel_button",
                "<gray>✖ Cancel</gray>"))
        );

        ActionButton continueBtn = new ActionButton(
            new CommonButtonData(continueLabel, 150),
            Optional.of(new CustomAll(SUDO_SUBMIT_ID, Optional.empty()))
        );
        ActionButton cancelBtn = new ActionButton(
            new CommonButtonData(cancelLabel, 150),
            Optional.of(new CustomAll(SUDO_CANCEL_ID, Optional.empty()))
        );

        // ─── Сборка диалога ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            false,                          // canCloseWithEscape
            true,                           // pause
            DialogAction.CLOSE,               // клиент закрывает окно сам, мгновенно (без «Waiting for server»)
            List.of(new PlainMessage(bodyText, 310)),
            List.of(new Input("password", passwordInput))
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(continueBtn),
            Optional.of(cancelBtn),
            1
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[SudoDialog] Opened sudo screen for " + player.getName()
            + " (registered=" + registered + ")");
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
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
