package com.mcplugin.infrastructure.listeners;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

import java.io.File;

/**
 * Обработчик ServerListPingEvent — кастомный MOTD (2 строки, MiniMessage) и иконка сервера (motd.png).
 * <p>
 * Конфигурация в config.yml → секция motd:
 * <ul>
 *   <li>enabled — вкл/выкл</li>
 *   <li>line1 / line2 — текст с MiniMessage-форматированием</li>
 *   <li>icon_enabled — показывать ли motd.png как иконку сервера</li>
 * </ul>
 */
public class MOTDListener implements Listener {

    private CachedServerIcon cachedIcon;
    private boolean iconLoaded = false;

    public MOTDListener() {
        loadIcon();
    }

    /**
     * Загружает иконку motd.png из папки плагина.
     * Если файла нет — просто логирует информационное сообщение.
     * Можно вызвать повторно для перезагрузки иконки (например при /mp reload).
     */
    public void loadIcon() {
        File iconFile = new File(Main.getInstance().getDataFolder(), "motd.png");
        if (!iconFile.exists()) {
            this.iconLoaded = false;
            this.cachedIcon = null;
            return;
        }

        try {
            this.cachedIcon = Bukkit.getServer().loadServerIcon(iconFile);
            this.iconLoaded = true;
            Main.getInstance().getLogger().info("[MOTD] Loaded server icon: motd.png");
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("[MOTD] Failed to load motd.png (must be 64×64 PNG): " + e.getMessage());
            this.iconLoaded = false;
            this.cachedIcon = null;
        }
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        var config = Main.getInstance().getConfig();

        if (!config.getBoolean("motd.enabled", false)) {
            return;
        }

        // =========================
        // SET MOTD TEXT (2 lines, MiniMessage via shared MessageUtil)
        // =========================
        String line1 = config.getString("motd.line1", "");
        String line2 = config.getString("motd.line2", "");

        if (!line1.isEmpty() || !line2.isEmpty()) {
            Component motd;
            if (line2.isEmpty()) {
                motd = MessageUtil.parse(line1);
            } else {
                motd = Component.textOfChildren(
                        MessageUtil.parse(line1),
                        Component.newline(),
                        MessageUtil.parse(line2)
                );
            }
            event.motd(motd);
        }

        // =========================
        // SET SERVER ICON (iconLoaded гарантирует что cachedIcon != null)
        // =========================
        if (config.getBoolean("motd.icon_enabled", true) && iconLoaded) {
            event.setServerIcon(cachedIcon);
        }
    }
}
