package com.ultimateimprovments.mechanics.protection;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.UIProtection;
import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.mechanics.protection.ProtectionGUI.GUIListener;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * «Protection Block» module — comprehensive territory protection via a placeable
 * physical block with a GUI, upgrade points and a hologram.
 * <p>
 * Module path: {@code mechanics/protection}.
 * Essential: false (can be disabled).
 * <p>
 * Honours the {@code protection.enabled} master switch: when it is false the module
 * initializes (no startup error) but registers nothing at all.
 */
public class ProtectionModule extends PluginModule {

    /** The CoreHooks bridge installed for AoE / VeinMiner / TreeCapitator / piston guards. */
    private static final class ProtectionHook implements CoreHooks.BlockProtectionApi {
        private final ProtectionManager manager;
        ProtectionHook(ProtectionManager manager) { this.manager = manager; }

        @Override
        public boolean isProtected(Location loc, Player actor) {
            return manager.isProtectedFor(loc, actor);
        }

        @Override
        public boolean isProtectionBlock(Location loc) {
            return manager.isProtectionBlockAt(loc);
        }

        @Override
        public void chargeAttempt(Location loc, int blocks) {
            manager.chargeZoneAttempt(loc, blocks);
        }
    }

    public ProtectionModule() {
        super("ProtectionBlock", "mechanics/protection", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;
        ConsoleLogger.info("[ProtectionBlock] Module initializing...");

        // Master switch (protection.enabled). Before, this getter had no callers:
        // the config option was silently ignored and the system always ran.
        if (!ProtectionConfig.isEnabled()) {
            ConsoleLogger.info("[ProtectionBlock] protection.enabled = false — module skipped.");
            return;
        }

        JavaPlugin holder = holder(plugin);

        ProtectionManager manager = ProtectionManager.getInstance();
        manager.init();

        ProtectionItem.init(main, holder);
        new ProtectionListener(manager, holder);
        new GUIListener(manager, holder);

        // Handler for player-name chat input for the whitelist menu
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(), holder);

        // Cross-addon contract: area-breakers and piston guards query the zones
        // through CoreHooks instead of importing this addon.
        CoreHooks.setBlockProtection(new ProtectionHook(manager));

        ConsoleLogger.info("[ProtectionBlock] ✔ Initialized.");
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        CoreHooks.setBlockProtection(null);
        ProtectionGUI.clearAwaiting();
        ProtectionManager.getInstance().shutdown();
        ConsoleLogger.info("[ProtectionBlock] Disabled.");
    }

    /** The addon plugin instance (so /ui reload can unregister our listeners). */
    private static JavaPlugin holder(JavaPlugin fallback) {
        UIProtection addon = UIProtection.getInstance();
        return addon != null ? addon : fallback;
    }

    // =========================
    // CHAT INPUT LISTENER
    // Accepts the player's nickname after the openAddPlayerMenu command.
    // Fires only if the player is currently in the waiting state.
    // =========================
    private static class ChatInputListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncChatEvent e) {
            Player player = e.getPlayer();
            UUID pid = player.getUniqueId();
            // consume returns the block (previously split into consume+getAwaitingBlock,
            // which made getAwaitingBlock always return null and broke the feature).
            ProtectionBlock block = ProtectionGUI.consumeAwaitingPlayerName(player);
            if (block == null) return;

            // Player is in the waiting state → capture the message
            e.setCancelled(true);
            String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(e.message()).trim();
            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "whitelist_cancelled",
                        "<yellow>Добавление игрока отменено.</yellow>")));
                ProtectionGUI.openWhitelistMenu(player, block);
                return;
            }
            // First look online (cheap lookup), then in the offline player cache.
            // Do NOT use the deprecated Bukkit.getOfflinePlayer(name) — it may
            // block the netty thread on a web lookup for unknown names.
            org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(msg);
            if (target == null) target = Bukkit.getOfflinePlayerIfCached(msg);
            if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "whitelist_player_not_found",
                        "<red>Игрок не найден: </red><white>%name%</white>"
                                + "<gray> (должен быть онлайн или заходить на сервер раньше)</gray>")
                        .replace("%name%", msg)));
                ProtectionGUI.openWhitelistMenu(player, block);
                return;
            }
            UUID targetId = target.getUniqueId();
            if (block.isWhitelisted(targetId)) {
                player.sendMessage(MessageUtil.parse(ProtectionConfig.getMessage(
                        "whitelist_already",
                        "<yellow>Игрок уже в whitelist.</yellow>")));
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
