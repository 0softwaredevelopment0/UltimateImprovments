package com.mcplugin.mechanics.security.codepanel;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import com.mcplugin.infrastructure.util.SoundUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Handles clicks and close events for the code panel GUI (double chest).
 */
public class CodePanelGUIListener implements Listener {

    // =========================
    // INVENTORY CLICK
    // =========================
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isOurGUI(event.getInventory())) return;
        if (!isOurTitle(event.getView().getTitle())) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= CodePanelGUI.GUI_SIZE) return;

        event.setCancelled(true);

        for (int s : CodePanelGUI.SCREEN_SLOTS) {
            if (slot == s) return;
        }

        String action = CodePanelGUI.BUTTON_MAP.get(slot);
        if (action == null) return;

        UUID uuid = player.getUniqueId();

        if ("ENTER".equals(action) && CodePanelSession.isEnterOnCooldown(uuid)) {
            long left = CodePanelSession.getRemainingCooldown(uuid) / 1000;
            player.sendMessage(MessageUtil.parse("<red>Please wait </red><yellow>" + Math.max(0, left) + "</yellow><red> sec before entering again</red>"));
            return;
        }

        switch (action) {
            case "BACKSPACE" -> handleBackspace(player);
            case "RESET" -> handleReset(player);
            case "ENTER" -> handleEnter(player);
            default -> handleDigit(player, action);
        }
    }

    // =========================
    // INVENTORY DRAG
    // =========================
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player
                && isOurGUI(event.getInventory())
                && isOurTitle(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    // =========================
    // INVENTORY CLOSE
    // =========================
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!isOurGUI(event.getInventory())) return;
        if (!isOurTitle(event.getView().getTitle())) return;
    }

    // =========================
    // HANDLERS
    // =========================
    private void handleDigit(Player player, String digit) {
        if (!isSafe()) return;
        UUID uuid = player.getUniqueId();
        int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
        String code = CodePanelSession.getCode(uuid);
        if (code.length() >= max) return;
        CodePanelSession.add(uuid, digit);
        CodePanelGUI.updateScreen(player);
        playSound(player, "digit");
    }

    private void handleBackspace(Player player) {
        UUID uuid = player.getUniqueId();
        StringBuilder sb = CodePanelSession.get(uuid);
        if (sb.length() == 0) return;
        sb.setLength(sb.length() - 1);
        CodePanelGUI.updateScreen(player);
        playSound(player, "backspace");
    }

    private void handleReset(Player player) {
        UUID uuid = player.getUniqueId();
        CodePanelSession.reset(uuid);
        CodePanelGUI.updateScreen(player);
        playSound(player, "reset");
    }

    private void handleEnter(Player player) {
        UUID uuid = player.getUniqueId();
        int cooldown = cfgInt("codepanel.enter_cooldown", 3);
        CodePanelSession.setEnterCooldown(uuid, cooldown * 1000L);
        playSound(player, "enter");
        player.closeInventory();
        checkCode(player);
    }

    // =========================
    // CHECK CODE
    // =========================
    private void checkCode(Player player) {
        if (!isSafe()) return;
        String input = CodePanelSession.getCode(player.getUniqueId());
        CodePanelDatabase.cleanupExpiredKeys();
        var keys = CodePanelDatabase.getAllKeys();

        if (keys.isEmpty()) {
            player.sendMessage(MessageUtil.parse(msg("codepanel.messages.no_config")));
            return;
        }

        for (var key : keys) {
            if (!key.code.equals(input)) continue;
            if (!key.isPlayerAllowed(player.getName())) {
                player.sendMessage(MessageUtil.parse("<dark_red>❌</dark_red> <red>You don't have access to this code!</red>"));
                playSound(player, "fail");
                return;
            }
            if (key.maxAttempts > 0) {
                CodePanelDatabase.incrementAttemptsUsed(key.keyName);
                int newUsed = key.attemptsUsed + 1;
                if (newUsed >= key.maxAttempts) {
                    CodePanelDatabase.removeKey(key.keyName);
                    Main.getInstance().getLogger().info(
                            "[CodePanel] Key '" + key.keyName + "' removed after "
                                    + newUsed + "/" + key.maxAttempts + " uses.");
                }
            }
            player.sendMessage(MessageUtil.parse(msg("codepanel.messages.success")));
            playSound(player, "success");
            if (key.command != null && !key.command.isEmpty()) {
                String[] commands = key.command.split(",");
                for (String rawCmd : commands) {
                    String trimmedCmd = rawCmd.trim();
                    if (trimmedCmd.isEmpty()) continue;
                    String cmd = trimmedCmd
                            .replace("$entity", player.getName())
                            .replace("%entity%", player.getName());
                    Main.getInstance().getServer().dispatchCommand(
                            Main.getInstance().getServer().getConsoleSender(), cmd);
                }
            }
            return;
        }
        player.sendMessage(MessageUtil.parse(msg("codepanel.messages.error")));
        playSound(player, "fail");
    }

    // =========================
    // SOUND
    // =========================
    private void playSound(Player player, String path) {
        if (!isSafe()) return;
        try {
            String name = Main.getInstance().getConfig().getString("codepanel.sounds." + path);
            if (name == null) return;
            Sound sound = SoundUtil.getSound(name);
            if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (Exception ignored) {}
    }

    // =========================
    // HELPERS
    // =========================
    private String msg(String path) {
        if (!isSafe()) return path;
        return MessagesManager.getString(path, path);
    }

    private int cfgInt(String path, int def) {
        if (!isSafe()) return def;
        return Main.getInstance().getConfig().getInt(path, def);
    }

    private boolean isSafe() { return Main.getInstance() != null; }

    public static boolean isOurGUI(Inventory inv) {
        if (inv == null) return false;
        if (inv.getSize() != CodePanelGUI.GUI_SIZE) return false;
        return inv.getHolder() == null;
    }

    private static boolean isOurTitle(String title) {
        return title != null && (title.equals(CodePanelGUI.GUI_TITLE)
                || title.equals(MessageUtil.legacy(CodePanelGUI.GUI_TITLE)));
    }
}
