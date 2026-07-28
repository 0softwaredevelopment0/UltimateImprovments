package com.ultimateimprovments.mechanics.security.auth;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * AuthDialogHandler — слушает {@link PlayerCustomClickEvent} и обрабатывает
 * отправку формы с паролем или отмену авторизации из Custom Screen.
 * <p>
 * Два режима:
 * <ul>
 *   <li>{@code ultimateimprovments:auth_submit} — отправка пароля (Continue)</li>
 *   <li>{@code ultimateimprovments:auth_cancel} — отмена авторизации (Exit → kick)</li>
 * </ul>
 */
public class AuthDialogHandler implements Listener {

    private static final Key AUTH_SUBMIT_KEY = Key.key("ultimateimprovments", "auth_submit");
    private static final Key AUTH_CANCEL_KEY = Key.key("ultimateimprovments", "auth_cancel");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        // Получаем игрока через PlayerGameConnection
        Player player = getPlayerFromConnection(event);
        if (player == null) return;

        // ─── Cancel (Exit Server) ───
        if (identifier.equals(AUTH_CANCEL_KEY)) {
            String kickMsg = Main.getInstance().getConfig()
                .getString("messages.auth.dialog.kick_cancelled",
                    "§c❌ Authentication cancelled.\n§7You cancelled the authentication process.");
            // Небольшая задержка, чтобы клиент успел закрыть диалог
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                if (player.isOnline()) {
                    player.kickPlayer(kickMsg);
                }
            }, 5L);
            ConsoleLogger.info("[AuthDialog] Player " + player.getName() + " cancelled authentication (Exit button).");
            return;
        }

        // ─── Submit (Continue) ───
        if (!identifier.equals(AUTH_SUBMIT_KEY)) return;

        // Извлекаем пароль из DialogResponseView (Paper API)
        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            ConsoleLogger.warn("[AuthDialog] No dialog response view in auth submit from " + player.getName());
            showErrorAndReopen(player);
            return;
        }

        String password = response.getText("password");
        if (password == null || password.isEmpty()) {
            showErrorAndReopen(player);
            return;
        }

        ConsoleLogger.info("[AuthDialog] Password submitted by " + player.getName()
            + " (len=" + password.length() + ")");

        // Делегируем существующей системе авторизации
        AuthManager manager = AuthManager.getInstance();
        if (manager != null) {
            manager.handlePasswordSubmit(player, password);
        }
    }

    /**
     * Получает Bukkit Player из PlayerCustomClickEvent через PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[AuthDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Показывает ошибку и открывает диалог заново.
     */
    private void showErrorAndReopen(Player player) {
        boolean registered = AuthDatabase.isRegistered(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                AuthDialogScreen.open(player, registered);
            }
        }, 10L);
    }



    /**
     * Регистрирует слушатель в плагине.
     */
    public static void register() {
        Bukkit.getPluginManager().registerEvents(new AuthDialogHandler(), Main.getInstance());
        ConsoleLogger.info("[AuthDialog] AuthDialogHandler registered");
    }
}
