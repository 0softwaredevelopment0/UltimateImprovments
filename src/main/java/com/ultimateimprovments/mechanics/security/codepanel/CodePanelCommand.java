package com.ultimateimprovments.mechanics.security.codepanel;

import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.ultimateimprovments.core.Main;

public class CodePanelCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        return handleCommand(player);
    }

    /**
     * Static entry point for /mp codepane subcommand.
     * Opens the GUI instead of sending chat messages.
     */
    public static boolean handleCommand(Player player) {

        // 💥 RELOAD SAFETY
        if (!isSafe()) {
            player.sendMessage("§cCode panel is reloading, try again later...");
            return true;
        }

        CodePanelGUI.open(player);
        return true;
    }

    // =========================
    // SAFE CHECK
    // =========================
    public static boolean isSafe() {

        Main plugin = Main.getInstance();
        if (plugin == null) return false;

        try {
            plugin.getConfig();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void sendPanel(Player player) {

        if (!isSafe()) return;

        FileConfiguration cfg = Main.getInstance().getConfig();

        int max = cfg.getInt("codepanel.max_length", 10);
        String emptyChar = cfg.getString("codepanel.empty_char", "-");

        String code = CodePanelSession.getCode(player.getUniqueId());
        if (code == null) code = "";

        if (code.length() > max) {
            code = code.substring(0, max);
        }

        player.spigot().sendMessage(buildCodeLine(code, max, emptyChar, cfg));
        player.sendMessage(" ");

        int offset = cfg.getInt("codepanel.keyboard_offset", 2);

        player.spigot().sendMessage(buildRow("1","2","3", offset, cfg));
        player.spigot().sendMessage(buildRow("4","5","6", offset, cfg));
        player.spigot().sendMessage(buildRow("7","8","9", offset, cfg));
        player.spigot().sendMessage(buildRow("R","0","E", offset, cfg));
    }

    // =========================
    // CODE LINE
    // =========================
    private static BaseComponent[] buildCodeLine(String code, int max, String empty, FileConfiguration cfg) {

        StringBuilder shown = new StringBuilder();

        for (int i = 0; i < max; i++) {
            if (i < code.length()) {
                shown.append(code.charAt(i));
            } else {
                shown.append(empty);
            }
        }

        ChatColor bracketColor = safeColor(
                cfg.getString("codepanel.colors.brackets", "DARK_RED"),
                ChatColor.DARK_RED
        );

        ChatColor codeColor = safeColor(
                cfg.getString("codepanel.colors.code", "WHITE"),
                ChatColor.WHITE
        );

        return new ComponentBuilder("")
                .append("<").color(bracketColor).bold(false)
                .append(shown.toString()).color(codeColor).bold(false)
                .append(">").color(bracketColor).bold(false)
                .create();
    }

    // =========================
    // ROW
    // =========================
    private static BaseComponent[] buildRow(String a, String b, String c, int offset, FileConfiguration cfg) {

        StringBuilder space = new StringBuilder();
        space.append(" ".repeat(Math.max(0, offset)));

        return new ComponentBuilder(space.toString())
                .append(button(a, cfg)).append(" ")
                .append(button(b, cfg)).append(" ")
                .append(button(c, cfg))
                .create();
    }

    // =========================
    // BUTTON
    // =========================
    private static BaseComponent button(String value, FileConfiguration cfg) {

        TextComponent tc = new TextComponent("[" + value + "]");

        ChatColor color;

        switch (value) {

            case "R" -> color = safeColor(
                    cfg.getString("codepanel.buttons.reset", "DARK_RED"),
                    ChatColor.DARK_RED
            );

            case "E" -> color = safeColor(
                    cfg.getString("codepanel.buttons.enter", "DARK_GREEN"),
                    ChatColor.DARK_GREEN
            );

            default -> color = safeColor(
                    cfg.getString("codepanel.buttons.normal", "WHITE"),
                    ChatColor.WHITE
            );
        }

        tc.setColor(color);
        tc.setBold(false);

        tc.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/mp pane_click " + value
        ));

        return tc;
    }

    // =========================
    // SAFE COLOR
    // =========================
    private static ChatColor safeColor(String name, ChatColor def) {

        if (name == null) return def;

        try {
            return ChatColor.valueOf(name);
        } catch (Exception e) {
            return def;
        }
    }
}