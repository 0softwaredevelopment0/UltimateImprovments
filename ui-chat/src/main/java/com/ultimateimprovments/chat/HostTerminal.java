package com.ultimateimprovments.chat;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes host (terminal) commands for the {@link ChatChannel#LINUX} channel.
 * <p>
 * Each player runs at most one tracked process at a time; typing {@code ^C} in
 * chat interrupts it (like Ctrl+C). Command output is streamed back to the player.
 * <p>
 * DANGER: this runs arbitrary shell commands on the host. It must only be
 * reached via the channel permission + config whitelist + access control.
 */
public final class HostTerminal {

    /** Tracks the currently running process per player so ^C can interrupt it. */
    private static final Map<UUID, Process> running = new ConcurrentHashMap<>();

    private HostTerminal() {}

    /**
     * Runs a command asynchronously on the host shell and sends its output
     * (stdout + stderr, exit code) back to the player.
     */
    public static void execute(Player player, String command) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            Process proc;
            try {
                ProcessBuilder pb = new ProcessBuilder(buildCommand(command));
                pb.redirectErrorStream(true);
                proc = pb.start();
            } catch (Exception e) {
                send(player, "<red>\\u274c Failed to start command: </red><gray>" + escape(e.getMessage()));
                return;
            }

            running.put(player.getUniqueId(), proc);
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                int code = proc.waitFor();
                send(player, "<dark_gray>┌─ </dark_gray><yellow>/" + escape(command)
                        + "</yellow> <gray>(exit " + code + ")</gray>");
                if (lines.isEmpty()) {
                    send(player, "<gray>    (no output)</gray>");
                } else {
                    int limit = Math.min(lines.size(), 200);
                    for (int i = 0; i < limit; i++) {
                        send(player, "<gray>    </gray><white>" + escape(lines.get(i)) + "</white>");
                    }
                    if (lines.size() > limit) {
                        send(player, "<gray>    … " + (lines.size() - limit) + " more lines suppressed</gray>");
                    }
                }
            } catch (Exception e) {
                send(player, "<red>\\u274c Command failed: </red><gray>" + escape(e.getMessage()));
            } finally {
                running.remove(player.getUniqueId());
                proc.destroyForcibly();
            }
        });
    }

    /**
     * Handles {@code ^C} — interrupts the player's currently running command.
     */
    public static void interrupt(Player player) {
        Process proc = running.get(player.getUniqueId());
        if (proc == null) {
            send(player, "<gray>No command is currently running.</gray>");
            return;
        }
        proc.destroy();
        send(player, "<yellow>^C — command interrupted.</yellow>");
    }

    /**
     * Builds the shell command line for the host OS (cmd.exe on Windows,
     * /bin/sh -c elsewhere).
     */
    private static List<String> buildCommand(String command) {
        List<String> list = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            list.add("cmd.exe");
            list.add("/c");
            list.add(command);
        } else {
            list.add("/bin/sh");
            list.add("-c");
            list.add(command);
        }
        return list;
    }

    private static void send(Player player, String miniMessage) {
        player.sendMessage(MessageUtil.parse(miniMessage));
    }

    /** Escapes MiniMessage tag characters so raw terminal output stays literal. */
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("<", "\\<").replace(">", "\\>")
                .replace("\u00A7", "");
    }
}