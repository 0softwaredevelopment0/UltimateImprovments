package com.mcplugin.infrastructure.tab;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Управляет кастомным таб-листом:
 * <ul>
 *   <li>Header — текст над списком игроков (MiniMessage + плейсхолдеры)</li>
 *   <li>Footer — текст под списком игроков (MiniMessage + плейсхолдеры)</li>
 *   <li>PlayerList name — префикс/суффикс перед/после ника (пинг, PAPI и т.д.)</li>
 * </ul>
 */
public class TabManager extends BukkitRunnable {

    private static TabManager instance;
    private boolean enabled;
    private List<String> headerLines;
    private List<String> footerLines;
    private boolean objectiveEnabled;
    private String objectivePrefix;
    private String objectiveSuffix;
    private String objectiveFormat;
    private int intervalTicks;

    public static void init() {
        instance = new TabManager();
        instance.reloadConfig();
        if (instance.enabled) {
            instance.runTaskTimer(Main.getInstance(), 20L, instance.intervalTicks);
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
    }

    public static void reload() {
        if (instance != null) {
            instance.cancel();
            instance.reloadConfig();
            if (instance.enabled) {
                instance.runTaskTimer(Main.getInstance(), 20L, instance.intervalTicks);
            }
        }
    }

    private void reloadConfig() {
        FileConfiguration config = Main.getInstance().getConfig();
        this.enabled = config.getBoolean("tab.enabled", false);
        this.headerLines = config.getStringList("tab.header");
        this.footerLines = config.getStringList("tab.footer");
        this.objectiveEnabled = config.getBoolean("tab.player_list.objective_enabled", false);
        this.objectivePrefix = config.getString("tab.player_list.objective_prefix", "");
        this.objectiveSuffix = config.getString("tab.player_list.objective_suffix", "");
        this.objectiveFormat = config.getString("tab.player_list.format", "");
        this.intervalTicks = Math.max(10, config.getInt("tab.update_interval_ticks", 20));
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            // Per-player header/footer (with player-specific placeholders)
            Component playerHeader = buildComponent(headerLines, player);
            Component playerFooter = buildComponent(footerLines, player);
            player.sendPlayerListHeaderAndFooter(playerHeader, playerFooter);

            // Player list name — кастомный формат или prefix+name+suffix
            if (objectiveEnabled) {
                if (!objectiveFormat.isEmpty()) {
                    // Кастомный формат — полный контроль: %luckperms_prefix%{player_name}...
                    String resolved = PlaceholderResolver.resolve(objectiveFormat, player);
                    player.playerListName(MessageUtil.parse(resolved));
                } else {
                    // Старая логика: prefix + name + suffix
                    String prefix = PlaceholderResolver.resolve(objectivePrefix, player);
                    String suffix = PlaceholderResolver.resolve(objectiveSuffix, player);

                    Component prefixComp = prefix.isEmpty() ? Component.empty() : MessageUtil.parse(prefix);
                    Component nameComp = Component.text(player.getName());
                    Component suffixComp = suffix.isEmpty() ? Component.empty() : MessageUtil.parse(suffix);

                    player.playerListName(prefixComp.append(nameComp).append(suffixComp));
                }
            }
        }
    }

    /**
     * Строит Component из списка строк MiniMessage + плейсхолдеры.
     */
    private Component buildComponent(List<String> lines, Player player) {
        if (lines == null || lines.isEmpty()) return Component.empty();

        String joined = String.join("\n", lines);
        String resolved = PlaceholderResolver.resolve(joined, player);
        return MessageUtil.parse(resolved);
    }
}
