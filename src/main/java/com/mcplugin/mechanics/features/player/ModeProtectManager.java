package com.mcplugin.mechanics.features.player;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
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
    private static String bypassPermission = "mcplugin.gmprotect.bypass";
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

        bypassPermission = cfg.getString("bypass_permission", "mcplugin.gmprotect.bypass");
        message = MessagesManager.getString("features.modeprotect.message", "<red>Вы не можете сменить режим игры в этом мире!</red>");
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!enabled) return;
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Пропускаем, если у игрока есть право на байпасс
        if (player.hasPermission(bypassPermission)) return;

        // Пропускаем, если мир не в списке защищённых
        // Если список пуст — защита действует во всех мирах
        String worldName = player.getWorld().getName();
        if (!protectedWorlds.isEmpty() && !protectedWorlds.contains(worldName)) return;

        // Отменяем смену режима
        event.setCancelled(true);

        // Принудительно переключаем обратно в выживание
        if (player.getGameMode() != GameMode.SURVIVAL) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        // Сообщение игроку
        player.sendMessage(MessageUtil.parse(message));
    }
}
