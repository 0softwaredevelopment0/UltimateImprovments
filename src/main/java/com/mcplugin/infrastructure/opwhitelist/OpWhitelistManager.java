package com.mcplugin.infrastructure.opwhitelist;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * 🛡 OP Whitelist — белый список операторов.
 * <p>
 * Если {@code enabled = true} и игрок имеет OP, но не в вайтлисте —
 * OP мгновенно снимается.
 * <p>
 * Поддерживает команды:
 * <ul>
 *   <li>{@code /mp opwhitelist add <ник>}</li>
 *   <li>{@code /mp opwhitelist remove <ник>}</li>
 *   <li>{@code /mp opwhitelist list}</li>
 *   <li>{@code /mp opwhitelist on}</li>
 *   <li>{@code /mp opwhitelist off}</li>
 * </ul>
 * <p>
 * Данные хранятся в {@code op-whitelist.json} в папке плагина.
 */
public class OpWhitelistManager implements Listener {

    private static final String FILE_NAME = "op-whitelist.json";
    private static boolean enabled = false;
    private static final Set<String> whitelist = new HashSet<>(); // lowercase names
    private static int taskId = -1;

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init(Main plugin) {
        load();
        plugin.getServer().getPluginManager().registerEvents(new OpWhitelistManager(), plugin);
        // Проверка OP каждые 3 секунды
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, OpWhitelistManager::checkAllOnline, 60L, 60L).getTaskId();
    }

    public static void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        save();
    }

    // ════════════════════════════════════════
    // LOAD / SAVE
    // ════════════════════════════════════════
    @SuppressWarnings("unchecked")
    public static void load() {
        whitelist.clear();
        enabled = false;

        File file = new File(Main.getInstance().getDataFolder(), FILE_NAME);
        if (!file.exists()) return;

        try {
            String json = Files.readString(file.toPath()).trim();
            if (json.isEmpty() || json.equals("{}")) return;

            // Формат: {"enabled":true,"names":["player1","player2"]}
            // Парсим без Gson

            // enabled
            int enIdx = json.indexOf("\"enabled\"");
            if (enIdx >= 0) {
                int colonIdx = json.indexOf(':', enIdx);
                if (colonIdx >= 0) {
                    String rest = json.substring(colonIdx + 1).trim();
                    enabled = rest.startsWith("true");
                }
            }

            // names array
            int namesIdx = json.indexOf("\"names\"");
            if (namesIdx >= 0) {
                int arrStart = json.indexOf('[', namesIdx);
                int arrEnd = json.indexOf(']', arrStart);
                if (arrStart >= 0 && arrEnd > arrStart) {
                    String arr = json.substring(arrStart + 1, arrEnd);
                    String[] parts = arr.split(",");
                    for (String p : parts) {
                        p = p.trim().replaceAll("^\"|\"$", "").toLowerCase();
                        if (!p.isEmpty()) {
                            whitelist.add(p);
                        }
                    }
                }
            }

            Main.getInstance().getLogger().info("[OpWhitelist] Loaded: " + whitelist.size() + " players, enabled=" + enabled);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to load: " + e.getMessage(), e);
        }
    }

    public static void save() {
        try {
            StringBuilder json = new StringBuilder("{");
            json.append("\"enabled\":").append(enabled).append(",");
            json.append("\"names\":[");

            List<String> sorted = new ArrayList<>(whitelist);
            Collections.sort(sorted);
            boolean first = true;
            for (String name : sorted) {
                if (!first) json.append(",");
                first = false;
                json.append("\"").append(name).append("\"");
            }

            json.append("]}");

            File file = new File(Main.getInstance().getDataFolder(), FILE_NAME);
            Files.writeString(file.toPath(), json);

            Main.getInstance().getLogger().fine("[OpWhitelist] Saved " + whitelist.size() + " players.");
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "[OpWhitelist] Failed to save: " + e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════
    // GETTERS
    // ════════════════════════════════════════
    public static boolean isEnabled() {
        return enabled;
    }

    public static List<String> getWhitelistNames() {
        List<String> result = new ArrayList<>(whitelist);
        Collections.sort(result);
        return result;
    }

    // ════════════════════════════════════════
    // ADD / REMOVE
    // ════════════════════════════════════════
    public static boolean add(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        String lower = playerName.toLowerCase().trim();
        if (whitelist.contains(lower)) return false;
        whitelist.add(lower);
        save();
        return true;
    }

    public static boolean remove(String playerName) {
        if (playerName == null) return false;
        String lower = playerName.toLowerCase().trim();
        if (!whitelist.remove(lower)) return false;
        save();
        return true;
    }

    // ════════════════════════════════════════
    // TOGGLE
    // ════════════════════════════════════════
    public static boolean setEnabled(boolean val) {
        if (enabled == val) return false;
        enabled = val;
        save();
        if (enabled) {
            checkAllOnline();
        }
        return true;
    }

    // ════════════════════════════════════════
    // JOIN EVENT — проверка при входе
    // ════════════════════════════════════════
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        checkAndDeop(e.getPlayer());
    }

    // ════════════════════════════════════════
    // CHECK ALL ONLINE — периодическая проверка
    // ════════════════════════════════════════
    public static void checkAllOnline() {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndDeop(player);
        }
    }

    // ════════════════════════════════════════
    // CHECK + DEOP
    // ════════════════════════════════════════
    private static void checkAndDeop(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!player.isOp()) return;

        String lower = player.getName().toLowerCase();
        if (whitelist.contains(lower)) return;

        // Игрок OP, но не в вайтлисте — снимаем OP
        player.setOp(false);
        player.sendMessage(MessageUtil.parse(
                "<red>⛔</red> <white>Your operator status has been removed — you are not in the OP whitelist.</white>"
        ));
        Main.getInstance().getLogger().info("[OpWhitelist] Removed OP from " + player.getName() + " (not whitelisted)");
    }
}
