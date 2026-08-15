package com.ultimateimprovments.mechanics.features.player;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModeProtectManager implements Listener {

    private static boolean enabled = true;
    private static Set<String> protectedWorlds = new HashSet<>();
    private static String bypassPermission = "ui.gmprotect.bypass";
    private static String message = "<red>Вы не можете сменить режим игры в этом мире!</red>";

    public static void init(Main plugin) {
        ModeProtectManager listener = new ModeProtectManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.modeprotect");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", false);

        List<String> worlds = cfg.getStringList("worlds");
        protectedWorlds = new HashSet<>(worlds);

        bypassPermission = cfg.getString("bypass_permission", "ui.gmprotect.bypass");
        message = MessagesManager.getString("features.modeprotect.message", "<red>Вы не можете сменить режим игры в этом мире!</red>");
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // Skip if the player has the bypass permission
        if (player.hasPermission(bypassPermission)) return;

        // Skip if the world is not in the protected list
        // If the list is empty — protection applies in all worlds
        String worldName = player.getWorld().getName();
        if (!protectedWorlds.isEmpty() && !protectedWorlds.contains(worldName)) return;

        // Cancel the gamemode change
        event.setCancelled(true);

        // Forcefully switch back to survival
        if (player.getGameMode() != GameMode.SURVIVAL) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        // Message to the player
        player.sendMessage(MessageUtil.parse(message));
    }
}
