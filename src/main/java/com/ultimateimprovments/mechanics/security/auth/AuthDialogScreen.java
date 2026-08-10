package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
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
 * AuthDialogScreen — создаёт и открывает Custom Screen (Dialog) для авторизации.
 * <p>
 * Использует Minecraft 26.2 Dialog API для показа экрана с полем пароля
 * и кнопками «Continue» / «Exit» (kick).
 * <p>
 * Диалог использует {@link DialogAction#CLOSE} (а НЕ {@link DialogAction#WAIT_FOR_RESPONSE}):
 * при {@code WAIT_FOR_RESPONSE} клиент после клика уходит на экран «Waiting for server…»
 * и игнорирует {@code ClientboundClearDialogPacket}, поэтому окно висело бы ~4 секунды.
 * С {@code CLOSE} клиент закрывает окно СРАЗУ после клика сам; игрок всё равно заморожен
 * (freezePlayer) до успешного входа, а при ошибке диалог переоткрывается сервером.
 */
public class AuthDialogScreen {

    /** Идентификатор CustomAll действия для отправки формы (логин/регистрация). */
    public static final Identifier AUTH_SUBMIT_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "auth_submit");
    /** Идентификатор CustomAll действия для отмены (выход с сервера). */
    public static final Identifier AUTH_CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "auth_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private AuthDialogScreen() {}

    /**
     * Открывает экран авторизации для игрока.
     */
    public static void open(Player player, boolean isRegistered) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[AuthDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        // ─── Заголовки ───
        net.minecraft.network.chat.Component title = toNative(
            MM.deserialize(isRegistered
                ? Main.getInstance().getConfig().getString("messages.auth.dialog.login_title",
                    "<gold>✦ Login Required</gold>")
                : Main.getInstance().getConfig().getString("messages.auth.dialog.register_title",
                    "<gold>✦ Registration Required</gold>"))
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
            MM.deserialize("<gray>Server Authentication</gray>")
        );

        // ─── Текст тела ───
        net.minecraft.network.chat.Component bodyText = toNative(
            MM.deserialize(isRegistered
                ? Main.getInstance().getConfig().getString("messages.auth.dialog.body_login",
                    "<white>Please enter your password to log in.</white>")
                : Main.getInstance().getConfig().getString("messages.auth.dialog.body_register",
                    "<white>Please choose a password to register.</white>"))
        );

        // ─── Поле ввода пароля ───
        net.minecraft.network.chat.Component pwLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.auth.dialog.password_label",
                "<gray>Password</gray>"))
        );
        int maxPwLen = Main.getInstance().getConfig().getInt("auth.max_password_length", 32);
        TextInput passwordInput = new TextInput(200, pwLabel, true, "", maxPwLen, Optional.empty());

        // ─── Кнопки ───
        net.minecraft.network.chat.Component continueLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.auth.dialog.continue_button",
                "<green>✔ Continue</green>"))
        );
        net.minecraft.network.chat.Component exitLabel = toNative(
            MM.deserialize(Main.getInstance().getConfig().getString("messages.auth.dialog.exit_button",
                "<red>✖ Exit Server</red>"))
        );

        // Continue → CustomAll action (отправляет форму с паролем)
        ActionButton continueBtn = new ActionButton(
            new CommonButtonData(continueLabel, 150),
            Optional.of(new CustomAll(AUTH_SUBMIT_ID, Optional.empty()))
        );
        // Exit → CustomAll action (отправляет сигнал на отмену → сервер кикает игрока)
        ActionButton exitBtn = new ActionButton(
            new CommonButtonData(exitLabel, 150),
            Optional.of(new CustomAll(AUTH_CANCEL_ID, Optional.empty()))
        );

        // ─── Сборка диалога ───
        CommonDialogData data = new CommonDialogData(
            title,
            Optional.of(externalTitle),
            false,                          // canCloseWithEscape — запрещаем закрытие ESC
            true,                           // pause
            DialogAction.CLOSE,               // afterAction — клиент закрывает окно сам, мгновенно (без «Waiting for server»)
            List.of(new PlainMessage(bodyText, 310)),
            List.of(new Input("password", passwordInput))
        );

        MultiActionDialog dialog = new MultiActionDialog(
            data,
            List.of(continueBtn),   // mainActions
            Optional.of(exitBtn),   // exitAction (нажатие кнопки выхода)
            1                       // columns
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[AuthDialog] Opened auth screen for " + player.getName()
            + " (registered=" + isRegistered + ")");
    }

    /**
     * Закрывает открытый диалог у игрока (после успешной/провальной авторизации).
     */
    public static void close(Player player) {
        if (!(player instanceof CraftPlayer craftPlayer)) return;
        if (craftPlayer.getHandle().connection == null) return;
        craftPlayer.getHandle().connection.send(
            net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE
        );
    }

    /**
     * Парсит SNBT строку в CompoundTag.
     */
    public static CompoundTag parseTag(String snbt) {
        try {
            return net.minecraft.nbt.TagParser.parseCompoundFully(snbt);
        } catch (Exception e) {
            ConsoleLogger.warn("[AuthDialog] Failed to parse SNBT: " + e.getMessage());
            return null;
        }
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
