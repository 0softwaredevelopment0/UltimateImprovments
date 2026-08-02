package com.ultimateimprovments.mechanics.security.codepanel;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CodePanelDialogScreen — кодовый панель как Custom Screen (Dialog).
 * <p>
 * Вместо двойного сундука/чат-клавиатуры — нативное диалоговое окно
 * Minecraft 26.2 Dialog API с кнопками цифр 0-9, экраном введённого кода,
 * кнопками «←», «✔ Подтвердить» и «✖ Отмена».
 * <p>
 * Клики обрабатываются в {@link CodePanelDialogHandler} через
 * {@link io.papermc.paper.event.player.PlayerCustomClickEvent}.
 */
public class CodePanelDialogScreen {

    /** Префикс идентификаторов цифровых кнопок: ultimateimprovments:codepanel_digit_N. */
    public static final String DIGIT_PREFIX = "codepanel_digit_";
    /** Идентификатор кнопки «←» (удалить последнюю цифру). */
    public static final Identifier BACKSPACE_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "codepanel_backspace");
    /** Идентификатор кнопки «✔ Подтвердить». */
    public static final Identifier CONFIRM_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "codepanel_confirm");
    /** Идентификатор кнопки «✖ Отмена». */
    public static final Identifier CANCEL_ID = Identifier.fromNamespaceAndPath("ultimateimprovments", "codepanel_cancel");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private CodePanelDialogScreen() {}

    /** Идентификатор CustomAll для цифровой кнопки N. */
    public static Identifier digitId(int digit) {
        return Identifier.fromNamespaceAndPath("ultimateimprovments", DIGIT_PREFIX + digit);
    }

    /**
     * Открывает диалог кодовой панели с текущим состоянием ввода из {@link CodePanelSession}.
     *
     * @param player игрок
     */
    public static void open(Player player) {
        open(player, null);
    }

    /**
     * Открывает диалог кодовой панели с текущим состоянием ввода из {@link CodePanelSession}.
     *
     * @param player       игрок
     * @param errorMessage опциональный текст ошибки, показываемый прямо в диалоге (null если ошибки нет)
     */
    public static void open(Player player, String errorMessage) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            ConsoleLogger.warn("[CodePanelDialog] Cannot open dialog for non-CraftPlayer: " + player.getName());
            return;
        }
        ServerPlayer serverPlayer = craftPlayer.getHandle();

        int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
        String code = CodePanelSession.getCode(player.getUniqueId());
        if (code.length() > max) code = code.substring(0, max);

        // ─── Заголовки ───
        net.minecraft.network.chat.Component title = toNative(
                MM.deserialize("<gold>✦ Code Panel</gold>")
        );
        net.minecraft.network.chat.Component externalTitle = toNative(
                MM.deserialize("<gray>Enter Code</gray>")
        );

        // ─── Экран: введённый код (+ ошибка если есть) ───
        String display = buildCodeDisplay(code, max);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            // errorMessage — уже готовая MiniMessage-строка, оборачивать её повторно нельзя
            display = errorMessage + "\n\n" + display;
        }
        net.minecraft.network.chat.Component bodyText = toNative(
                MM.deserialize(display)
        );

        // ─── Кнопки: 1-9 ───
        List<ActionButton> actions = new ArrayList<>();
        for (int d = 1; d <= 9; d++) {
            actions.add(digitButton(d));
        }
        // ─── 0, ←, ✔ ───
        actions.add(digitButton(0));
        actions.add(backspaceButton());
        actions.add(confirmButton());

        // ─── ✖ Отмена (exitAction) ───
        ActionButton cancelBtn = new ActionButton(
                new CommonButtonData(toNative(MM.deserialize("<red>✖ Cancel</red>")), 150),
                Optional.of(new CustomAll(CANCEL_ID, Optional.empty()))
        );

        // ─── Сборка диалога ───
        CommonDialogData data = new CommonDialogData(
                title,
                Optional.of(externalTitle),
                true,                           // canCloseWithEscape
                true,                           // pause
                DialogAction.WAIT_FOR_RESPONSE,  // ждём ответа после каждого клика
                List.of(new PlainMessage(bodyText, 310)),
                List.of()                        // без текстовых инпутов — только кнопки
        );

        MultiActionDialog dialog = new MultiActionDialog(
                data,
                actions,                        // mainActions (3 колонки × 4 ряда)
                Optional.of(cancelBtn),         // exitAction
                3                               // columns
        );

        serverPlayer.openDialog(Holder.direct(dialog));
        ConsoleLogger.info("[CodePanelDialog] Opened code panel for " + player.getName());
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

    // =========================
    // BUILDERS
    // =========================

    /**
     * Строит строку отображения кода, например: {@code <gray>[1][2][-][-]</gray>}.
     */
    private static String buildCodeDisplay(String code, int max) {
        StringBuilder sb = new StringBuilder("<white>Code: </white>");
        for (int i = 0; i < max; i++) {
            if (i < code.length()) {
                sb.append("<green>[").append(code.charAt(i)).append("]</green>");
            } else {
                sb.append("<dark_gray>[-]</dark_gray>");
            }
        }
        return sb.toString();
    }

    private static ActionButton digitButton(int digit) {
        net.minecraft.network.chat.Component label = toNative(
                MM.deserialize("<white>" + digit + "</white>")
        );
        return new ActionButton(
                new CommonButtonData(label, 60),
                Optional.of(new CustomAll(digitId(digit), Optional.empty()))
        );
    }

    private static ActionButton backspaceButton() {
        net.minecraft.network.chat.Component label = toNative(
                MM.deserialize("<yellow>←</yellow>")
        );
        return new ActionButton(
                new CommonButtonData(label, 60),
                Optional.of(new CustomAll(BACKSPACE_ID, Optional.empty()))
        );
    }

    private static ActionButton confirmButton() {
        net.minecraft.network.chat.Component label = toNative(
                MM.deserialize("<green>✔</green>")
        );
        return new ActionButton(
                new CommonButtonData(label, 60),
                Optional.of(new CustomAll(CONFIRM_ID, Optional.empty()))
        );
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Преобразует Adventure Component → Minecraft Component.
     */
    private static net.minecraft.network.chat.Component toNative(Component adv) {
        String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(adv);
        return net.minecraft.network.chat.Component.literal(legacy);
    }
}
