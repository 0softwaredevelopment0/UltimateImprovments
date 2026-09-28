package com.ultimateimprovments.mechanics.protection;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.mechanics.protection.ProtectionGUI.GUIListener;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * «Protection Block» module — comprehensive territory protection via a placeable
 * physical block with a GUI, upgrade points and a hologram.
 * <p>
 * Module path: {@code mechanics/protection}.
 * Essential: false (can be disabled).
 */
public class ProtectionModule extends PluginModule {

    public ProtectionModule() {
        super("ProtectionBlock", "mechanics/protection", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        ConsoleLogger.info("[ProtectionBlock] Module initializing...");

        ProtectionManager manager = ProtectionManager.getInstance();
        manager.init();

        ProtectionItem.init(main);
        new ProtectionListener(manager);
        new GUIListener(manager);

        // Handler for player-name chat input for the whitelist menu
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(), main);

        ConsoleLogger.info("[ProtectionBlock] ✔ Initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        ProtectionManager.getInstance().shutdown();
        ConsoleLogger.info("[ProtectionBlock] Disabled.");
    }

    // =========================
    // CHAT INPUT LISTENER
    // Accepts the player's nickname after the openAddPlayerMenu command.
    // Fires only if the player is currently in the waiting state.
    // =========================
    private static class ChatInputListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncPlayerChatEvent e) {
            Player player = e.getPlayer();
            UUID pid = player.getUniqueId();
            // consume now returns the block (previously split into consume+getAwaitingBlock,
            // which made getAwaitingBlock always return null and broke the feature).
            ProtectionBlock block = ProtectionGUI.consumeAwaitingPlayerName(player);
            if (block == null) return;

            // Player is in the waiting state → capture the message
            e.setCancelled(true);
            String msg = e.getMessage().trim();
            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(MessageUtil.parse(
                        "<yellow>Добавление игрока отменено.</yellow>"));
                ProtectionGUI.openWhitelistMenu(player, block);
                return;
            }
            // First look online (cheap lookup), then in the offline player cache.
            // Do NOT use the deprecated Bukkit.getOfflinePlayer(name) — it may
            // block the netty thread on a web lookup for unknown names.
            org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(msg);
            if (target == null) target = Bukkit.getOfflinePlayerIfCached(msg);
            if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
                player.sendMessage(MessageUtil.parse(
                        "<red>Игрок не найден: </red><white>" + msg + "</white>"
                                + "<gray> (должен быть онлайн или заходить на сервер раньше)</gray>"));
                ProtectionGUI.openWhitelistMenu(player, block);
                return;
            }
            UUID targetId = target.getUniqueId();
            if (block.isWhitelisted(targetId)) {
                player.sendMessage(MessageUtil.parse(
                        "<yellow>Игрок уже в whitelist.</yellow>"));
                ProtectionGUI.openWhitelistMenu(player, block);
                return;
            }
            block.addToWhitelist(targetId);
            ProtectionDatabase.saveWhitelist(block);
            player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                    "whitelist_added",
                    "<green>✔</green> <white>Игрок <yellow>%name%</yellow> добавлен в whitelist.</white>")
                    .replace("%name%", target.getName() != null ? target.getName() : msg)));
            ProtectionGUI.openWhitelistMenu(player, block);
        }
    }
}
