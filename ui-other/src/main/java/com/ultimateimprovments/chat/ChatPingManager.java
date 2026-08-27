package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.database.PlayerSettingsDB;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 🔔 ChatPingManager — chat ping system.
 * <p>
 * Allows pinging players via special tags in the message:
 * <ul>
 *   <li>{@code @everyone} — all players (except the sender)</li>
 *   <li>{@code @<nick>} — a specific player (by nick)</li>
 *   <li>{@code @non-op} — all players without OP</li>
 *   <li>{@code @is-admin} — all with the ui.admin or ui.* permission</li>
 *   <li>{@code @is-non-admin} — all without the ui.admin/ui.* permission</li>
 * </ul>
 * <p>
 * Pings are processed in the message after placeholder resolution:
 * the tag is replaced with the player nick(s) underlined and colored,
 * and a sound is played for every pinged player.
 */
public class ChatPingManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static boolean enabled = true;
    private static String pingStyle;
    private static String pingSoundName;
    private static float pingSoundVolume;
    private static float pingSoundPitch;

    // Pattern for finding @tags in the message
    // Looks for @everyone, @non-op, @is-admin, @is-non-admin, @<nick>
    private static final Pattern PING_PATTERN = Pattern.compile(
            "@(everyone|non-op|is-admin|is-non-admin|[\\w]+)"
    );

    private ChatPingManager() {}

    /**
     * Loads the settings from the config.
     */
    public static void reloadConfig() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        enabled = cfg.getBoolean("chat_ping.enabled", true);
        pingStyle = cfg.getString("chat_ping.ping_style",
                "<underlined><color:#FFAA00>@%s</color></underlined>");
        pingSoundName = cfg.getString("chat_ping.sound_name", "ENTITY_EXPERIENCE_ORB_PICKUP");
        pingSoundVolume = (float) cfg.getDouble("chat_ping.sound_volume", 0.5);
        pingSoundPitch = (float) cfg.getDouble("chat_ping.sound_pitch", 1.5);
    }

    /**
     * Processes pings in the message.
     * <p>
     * Called from {@link ChatManager#onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent)}
     * AFTER placeholder resolution, but BEFORE the message is sent.
     *
     * @param message  the current text message (with placeholders already resolved)
     * @param sender   the player who sent the message
     * @return the processing result (modified message + list of pinged players)
     */
    public static PingResult processPings(String message, Player sender) {
        if (!enabled || message == null || message.isEmpty()) {
            return new PingResult(message, List.of());
        }

        List<Player> pingedPlayers = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        Matcher m = PING_PATTERN.matcher(message);
        int lastEnd = 0;

        while (m.find()) {
            sb.append(message, lastEnd, m.start());

            String keyword = m.group(1);
            List<Player> targets = resolveTargets(keyword, sender);

            if (targets.isEmpty()) {
                // Nick not found or the tag yielded no results — leave as is
                sb.append(m.group());
            } else {
                // Build the styled text for each target
                List<String> styledNames = new ArrayList<>();
                for (Player target : targets) {
                    // Replace %s with the player nick in pingStyle
                    String styled = pingStyle.replace("%s", target.getName());
                    styledNames.add(styled);
                    if (!target.equals(sender)) {
                        pingedPlayers.add(target);
                    }
                }
                sb.append(String.join("<gray>, </gray>", styledNames));
            }

            lastEnd = m.end();
        }

        sb.append(message.substring(lastEnd));

        return new PingResult(sb.toString(), pingedPlayers);
    }

    /**
     * Plays the ping sound and sends a notification to the given players.
     */
    public static void notifyPingedPlayers(List<Player> players, Player sender) {
        if (players == null || players.isEmpty()) return;
        Sound sound = SoundUtil.getSound(pingSoundName, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        String notificationMsg = MessagesManager.getString("chat_ping.notification", "");
        Component notification = notificationMsg.isEmpty() ? null
                : MessageUtil.parse(notificationMsg.replace("%sender%", sender.getName()));
        for (Player p : players) {
            // Respect per-player ping sound toggle
            if (PlayerSettingsDB.isPingEnabled(p.getUniqueId())) {
                SoundUtil.playSound(p, sound, pingSoundVolume, pingSoundPitch);
            }
            if (notification != null) {
                p.sendMessage(notification);
            }
        }
    }

    /**
     * Resolves a ping tag into a list of players.
     */
    private static List<Player> resolveTargets(String keyword, Player sender) {
        // Special tags
        switch (keyword.toLowerCase()) {
            case "everyone" -> {
                List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
                all.remove(sender);
                return all;
            }
            case "non-op" -> {
                List<Player> result = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender) && !p.isOp()) {
                        result.add(p);
                    }
                }
                return result;
            }
            case "is-admin" -> {
                List<Player> result = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender) && (p.hasPermission("ui.admin") || p.hasPermission("ui.*"))) {
                        result.add(p);
                    }
                }
                return result;
            }
            case "is-non-admin" -> {
                List<Player> result = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender) && !p.hasPermission("ui.admin") && !p.hasPermission("ui.*")) {
                        result.add(p);
                    }
                }
                return result;
            }
            default -> {
                // A specific nick
                Player target = Bukkit.getPlayerExact(keyword);
                if (target != null && target.isOnline()) {
                    return List.of(target);
                }
                return List.of();
            }
        }
    }

    /**
     * The result of ping processing.
     */
    public record PingResult(
            String formattedMessage,
            List<Player> pingedPlayers
    ) {}
}