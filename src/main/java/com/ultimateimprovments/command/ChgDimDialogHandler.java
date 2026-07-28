package com.ultimateimprovments.command;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * ChgDimDialogHandler — слушает {@link PlayerCustomClickEvent} и обрабатывает
 * отправку названия мира или отмену из Custom Screen телепортации.
 * <p>
 * Два режима:
 * <ul>
 *   <li>{@code ultimateimprovments:chgdim_submit} — телепортация в указанный мир</li>
 *   <li>{@code ultimateimprovments:chgdim_cancel} — отмена телепортации</li>
 * </ul>
 * <p>
 * При ошибке (мир не найден, нет прав, кулдаун) — переоткрывает диалог
 * с текстом ошибки прямо внутри окна.
 */
public class ChgDimDialogHandler implements Listener {

    private static final Key CHGDIM_SUBMIT_KEY = Key.key("ultimateimprovments", "chgdim_submit");
    private static final Key CHGDIM_CANCEL_KEY = Key.key("ultimateimprovments", "chgdim_cancel");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        // Получаем игрока через PlayerGameConnection
        Player player = getPlayerFromConnection(event);
        if (player == null) return;

        // ─── Cancel ───
        if (identifier.equals(CHGDIM_CANCEL_KEY)) {
            ChgDimDialogScreen.close(player);
            player.sendMessage(MessageUtil.parse(
                "<gray>✦ Teleportation cancelled.</gray>"
            ));
            ConsoleLogger.info("[ChgDimDialog] Player " + player.getName() + " cancelled teleportation.");
            return;
        }

        // ─── Submit (TP) ───
        if (!identifier.equals(CHGDIM_SUBMIT_KEY)) return;

        // Извлекаем название мира из DialogResponseView (Paper API)
        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            ConsoleLogger.warn("[ChgDimDialog] No dialog response view from " + player.getName());
            reopenWithError(player, "No response from dialog. Please try again.");
            return;
        }

        String worldName = response.getText("world_name");
        if (worldName == null || worldName.trim().isEmpty()) {
            reopenWithError(player, "World name cannot be empty!");
            return;
        }

        worldName = worldName.trim();

        ConsoleLogger.info("[ChgDimDialog] World name submitted by " + player.getName()
            + ": \"" + worldName + "\"");

        // Проверяем права на конкретный мир
        if (!player.hasPermission("ui.command.chgdim." + worldName)) {
            reopenWithError(player, "You do not have permission to teleport to \"" + worldName + "\"!");
            return;
        }

        // Проверяем кулдаун
        String cooldownError = checkCooldown(player);
        if (cooldownError != null) {
            reopenWithError(player, cooldownError);
            return;
        }

        // Проверяем, настроен ли мир в конфиге
        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null || !worldsSection.contains(worldName)) {
            reopenWithError(player, "World \"" + worldName + "\" is not configured!");
            return;
        }

        // Проверяем, существует ли мир на сервере
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            reopenWithError(player, "World \"" + worldName + "\" not found on the server!");
            return;
        }

        // ─── Успех — телепортируем ───
        ChgDimDialogScreen.close(player);

        String finalWorldName = worldName;
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            ChgDimCommand.teleport(player, finalWorldName);
        });
    }

    /**
     * Проверяет кулдаун телепортации для игрока.
     *
     * @return null если кулдаун пройден, или строку с сообщением об ошибке
     */
    private String checkCooldown(Player player) {
        java.util.UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis() / 1000;
        int cooldownSecs = Main.getInstance().getConfig()
            .getInt("changedimmension.cooldown_seconds", 10);

        if (ChgDimCommand.cooldowns.containsKey(playerUuid)) {
            long lastUse = ChgDimCommand.cooldowns.get(playerUuid);
            long elapsed = now - lastUse;
            if (elapsed < cooldownSecs) {
                long remaining = cooldownSecs - elapsed;
                return "Please wait " + remaining + " seconds before teleporting again!";
            }
        }
        return null;
    }

    /**
     * Получает Bukkit Player из PlayerCustomClickEvent через PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[ChgDimDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Закрывает диалог и переоткрывает его с сообщением об ошибке.
     */
    private void reopenWithError(Player player, String errorMessage) {
        ChgDimDialogScreen.close(player);
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                ChgDimDialogScreen.open(player, errorMessage);
            }
        }, 10L);
    }

    /**
     * Регистрирует слушатель в плагине.
     */
    public static void register() {
        Bukkit.getPluginManager().registerEvents(new ChgDimDialogHandler(), Main.getInstance());
        ConsoleLogger.info("[ChgDimDialog] ChgDimDialogHandler registered");
    }
}
