package com.ultimateimprovements.mechanics.protection;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.mechanics.protection.ProtectionGUI.GUIListener;
import com.ultimateimprovements.module.PluginModule;
import com.ultimateimprovements.util.ConsoleLogger;
import com.ultimateimprovements.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Модуль «Блок защиты» — комплексная защита территории через размещаемый
 * физический блок с GUI, очками прокачки и голограммой.
 * <p>
 * Module path: {@code mechanics/protection}.
 * Essential: false (можно отключить).
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

        // Обработчик ввода имени игрока в чат для whitelist-меню
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
    // Принимает ник игрока после команды openAddPlayerMenu.
    // Срабатывает только если игрок сейчас в режиме ожидания.
    // =========================
    private static class ChatInputListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncPlayerChatEvent e) {
            Player player = e.getPlayer();
            UUID pid = player.getUniqueId();
            // consume теперь возвращает блок (раньше разделялся на consume+getAwaitingBlock,
            // из-за чего getAwaitingBlock всегда возвращал null и фича была сломана).
            ProtectionBlock block = ProtectionGUI.consumeAwaitingPlayerName(player);
            if (block == null) return;

            // Игрок в режиме ожидания → захватываем сообщение
            e.setCancelled(true);
            String msg = e.getMessage().trim();
            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(MessageUtil.parse(
                        "<yellow>Добавление игрока отменено.</yellow>"));
                ProtectionGUI.openWhitelistMenu(player, block);
                return;
            }
            // Сначала ищем онлайн (дешёвый lookup), потом — в кеше оффлайн-игроков.
            // НЕ используем депрекейтнутый Bukkit.getOfflinePlayer(name) — он может
            // блокировать netty thread на веб-lookup для незнакомых имён.
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
