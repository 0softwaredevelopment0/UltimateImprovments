package com.mcplugin.cp;

import com.mcplugin.Main;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.SoundUtil;
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
 * Обрабатывает клики и закрытие GUI кодовой панели (двойной сундук).
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
        // Allow clicks in player's own inventory (slots >= GUI_SIZE)
        if (slot < 0 || slot >= CodePanelGUI.GUI_SIZE) return;

        event.setCancelled(true);

        // Screen slots → do nothing
        for (int s : CodePanelGUI.SCREEN_SLOTS) {
            if (slot == s) return;
        }

        String action = CodePanelGUI.BUTTON_MAP.get(slot);
        if (action == null) return; // glass pane

        UUID uuid = player.getUniqueId();

        // Enter cooldown check — защита от спама
        if ("ENTER".equals(action) && CodePanelSession.isEnterOnCooldown(uuid)) {
            long left = CodePanelSession.getRemainingCooldown(uuid) / 1000;
            player.sendMessage("§cПодождите §e" + Math.max(0, left) + "§c сек перед повторным вводом");
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
    // INVENTORY CLOSE — ничего не сбрасываем,
    // игрок может открыть панель снова и продолжить ввод.
    // =========================
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!isOurGUI(event.getInventory())) return;
        if (!isOurTitle(event.getView().getTitle())) return;
        // Session не чистим — код сохраняется между открытиями
    }

    // =========================
    // HANDLERS
    // =========================

    private void handleDigit(Player player, String digit) {
        if (!isSafe()) return;

        UUID uuid = player.getUniqueId();
        int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
        String code = CodePanelSession.getCode(uuid);

        if (code.length() >= max) return; // silently ignore

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

        // Set Enter cooldown to prevent spam
        int cooldown = cfgInt("codepanel.enter_cooldown", 3);
        CodePanelSession.setEnterCooldown(uuid, cooldown * 1000L);

        // Play enter sound, close GUI instantly, and check code immediately
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
                player.sendMessage("§4❌ §cУ вас нет доступа к этому коду!");
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
                                    + newUsed + "/" + key.maxAttempts + " uses."
                    );
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
                            Main.getInstance().getServer().getConsoleSender(),
                            cmd
                    );
                }
            }
            return;
        }

        // WRONG CODE — просто сообщение, без блокировки
        player.sendMessage(MessageUtil.parse(msg("codepanel.messages.error")));
        playSound(player, "fail");
    }

    // =========================
    // SOUND
    // =========================
    private void playSound(Player player, String path) {
        if (!isSafe()) return;
        try {
            String name = Main.getInstance()
                    .getConfig()
                    .getString("codepanel.sounds." + path);
            if (name == null) return;
            Sound sound = SoundUtil.getSound(name);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1f, 1f);
            }
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

    private boolean isSafe() {
        return Main.getInstance() != null;
    }

    // =========================
    // GUI DETECTION — проверяем размер + заголовок инвентаря
    // =========================
    public static boolean isOurGUI(Inventory inv) {
        if (inv == null) return false;
        if (inv.getSize() != CodePanelGUI.GUI_SIZE) return false;
        if (inv.getHolder() != null) return false; // our GUI has null holder
        return true;
    }

    /**
     * Дополнительная проверка заголовка — чтобы не перехватывать
     * чужие 54-слотовые GUI (например, NotesManager).
     */
    private static boolean isOurTitle(String title) {
        return title != null && title.equals(CodePanelGUI.GUI_TITLE);
    }
}
