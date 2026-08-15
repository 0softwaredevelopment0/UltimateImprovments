package com.ultimateimprovments.mechanics.security.codepanel;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.SoundUtil;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * CodePanelDialogHandler — listens to {@link PlayerCustomClickEvent} and handles
 * clicks on the code panel buttons (Custom Screen).
 * <p>
 * Actions:
 * <ul>
 *   <li>{@code ultimateimprovments:codepanel_digit_N} — add digit N</li>
 *   <li>{@code ultimateimprovments:codepanel_backspace} — delete the last digit</li>
 *   <li>{@code ultimateimprovments:codepanel_confirm} — check the entered code</li>
 *   <li>{@code ultimateimprovments:codepanel_cancel} — close the dialog and reset the input</li>
 * </ul>
 * <p>
 * Code checking — via the {@link CodePanelDatabase} DB (keys with permissions/attempts/commands),
 * as in the old {@code CodePanelClick#check} / {@code CodePanelGUIListener#checkCode}.
 */
public class CodePanelDialogHandler implements Listener {

    private static final Key CANCEL_KEY = Key.key("ultimateimprovments", "codepanel_cancel");
    private static final Key CONFIRM_KEY = Key.key("ultimateimprovments", "codepanel_confirm");
    private static final Key BACKSPACE_KEY = Key.key("ultimateimprovments", "codepanel_backspace");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();

        Player player = getPlayerFromConnection(event);
        if (player == null) return;

        // ─── Cancel — close and reset ───
        if (identifier.equals(CANCEL_KEY)) {
            CodePanelDialogScreen.close(player);
            CodePanelSession.reset(player.getUniqueId());
            player.sendMessage(MessageUtil.parse(
                    msg("codepanel.messages.cancelled", "<gray>✖ Code entry cancelled.</gray>")
            ));
            ConsoleLogger.info("[CodePanelDialog] Player " + player.getName() + " cancelled code entry.");
            return;
        }

        // ─── Backspace — delete the last digit ───
        if (identifier.equals(BACKSPACE_KEY)) {
            StringBuilder sb = CodePanelSession.get(player.getUniqueId());
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
                playSound(player, "backspace");
            }
            reopen(player);
            return;
        }

        // ─── Confirm — check the code ───
        if (identifier.equals(CONFIRM_KEY)) {
            int cooldown = Main.getInstance().getConfig().getInt("codepanel.enter_cooldown", 3);
            if (CodePanelSession.isEnterOnCooldown(player.getUniqueId())) {
                long left = CodePanelSession.getRemainingCooldown(player.getUniqueId()) / 1000;
                player.sendMessage(MessageUtil.parse(
                        msg("codepanel.messages.cooldown", "<red>Please wait </red><yellow>%seconds%</yellow><red> seconds before entering again!</red>")
                                .replace("%seconds%", String.valueOf(Math.max(0, left)))
                ));
                reopen(player);
                return;
            }
            CodePanelSession.setEnterCooldown(player.getUniqueId(), cooldown * 1000L);
            CodePanelDialogScreen.close(player);
            playSound(player, "enter");
            checkCode(player);
            return;
        }

        // ─── Digit — add a digit ───
        if (identifier.namespace().equals("ultimateimprovments")
                && identifier.value().startsWith(CodePanelDialogScreen.DIGIT_PREFIX)) {
            String digit = identifier.value().substring(CodePanelDialogScreen.DIGIT_PREFIX.length());
            if (digit.length() != 1 || !Character.isDigit(digit.charAt(0))) return;

            int max = Main.getInstance().getConfig().getInt("codepanel.max_length", 10);
            String code = CodePanelSession.getCode(player.getUniqueId());
            if (code.length() >= max) {
                // Max reached — give feedback so the player knows the input was rejected
                playSound(player, "fail");
                return;
            }
            CodePanelSession.add(player.getUniqueId(), digit);
            playSound(player, "digit");
            reopen(player);
        }
    }

    /**
     * Reopens the dialog so the screen shows the updated code.
     */
    private void reopen(Player player) {
        reopen(player, null);
    }

    /**
     * Closes and reopens the dialog, showing the error right in the window (ChgDim pattern).
     * 10-tick delay — the client must process the old dialog's close before
     * the new one is sent (as in ChgDimDialogHandler/SudoDialogHandler).
     */
    private void reopenWithError(Player player, String errorMessage) {
        CodePanelDialogScreen.close(player);
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                CodePanelDialogScreen.open(player, errorMessage);
            }
        }, 10L);
    }

    private void reopen(Player player, String errorMessage) {
        // 1 tick — the client already closed the window itself (DialogAction.CLOSE), Show reopens it
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (player.isOnline()) {
                CodePanelDialogScreen.open(player, errorMessage);
            }
        }, 1L);
    }

    // =========================
    // CHECK CODE — key DB
    // =========================
    private void checkCode(Player player) {
        String input = CodePanelSession.getCode(player.getUniqueId());

        // Clean up expired keys in the DB
        CodePanelDatabase.cleanupExpiredKeys();

        List<CodePanelDatabase.CodePanelKey> keys = CodePanelDatabase.getAllKeys();

        if (keys.isEmpty()) {
            player.sendMessage(MessageUtil.parse(msg("codepanel.messages.no_config",
                    "<dark_red>❌</dark_red> <red>Error:</red> <gray>No keys found in the database!</gray>")));
            return;
        }

        for (CodePanelDatabase.CodePanelKey key : keys) {
            if (!key.code.equals(input)) continue;

            // Whitelist / blacklist check
            if (!key.isPlayerAllowed(player.getName())) {
                // The error is shown right in the dialog
                playSound(player, "fail");
                reopenWithError(player, msg("codepanel.messages.no_access",
                        "<red>❌ You don't have access to this code!</red>"));
                return;
            }

            // max_attempts — increment the counter
            if (key.maxAttempts > 0) {
                CodePanelDatabase.incrementAttemptsUsed(key.keyName);
                int newUsed = key.attemptsUsed + 1;
                if (newUsed >= key.maxAttempts) {
                    CodePanelDatabase.removeKey(key.keyName);
                    ConsoleLogger.info(
                            "[CodePanel] Key '" + key.keyName + "' removed after "
                                    + newUsed + "/" + key.maxAttempts + " uses."
                    );
                }
            }

            player.sendMessage(MessageUtil.parse(msg("codepanel.messages.success",
                    "<dark_green>✔</dark_green> <green>Success:</green> <gray>Correct code!</gray>")));
            playSound(player, "success");
            CodePanelSession.reset(player.getUniqueId());

            // Execute the bound commands (comma-separated, $entity/%entity% → player)
            if (key.command != null && !key.command.isEmpty()) {
                String[] commands = key.command.split(",");
                for (String rawCmd : commands) {
                    String trimmedCmd = rawCmd.trim();
                    if (trimmedCmd.isEmpty()) continue;
                    String cmd = trimmedCmd
                            .replace("$entity", player.getName())
                            .replace("%entity%", player.getName());
                    Main.getInstance().getServer().dispatchCommand(
                            Main.getInstance().getServer().getConsoleSender(), cmd
                    );
                }
            }
            return;
        }

        // WRONG CODE — close and reopen the dialog with the error in the window
        playSound(player, "fail");
        reopenWithError(player, msg("codepanel.messages.error",
                "<dark_red>❌</dark_red> <red>Error:</red> <gray>Incorrect code!</gray>"));
    }

    // =========================
    // SOUND / MSG HELPERS
    // =========================
    private void playSound(Player player, String path) {
        try {
            String name = Main.getInstance().getConfig().getString("codepanel.sounds." + path);
            if (name == null) return;
            Sound sound = SoundUtil.getSound(name);
            if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (Exception e) {
            ConsoleLogger.warn("[CodePanel] Sound error: " + e.getMessage());
        }
    }

    private String msg(String path, String def) {
        String value = MessagesManager.getString(path, def);
        return value == null ? def : value;
    }

    /**
     * Gets the Bukkit Player from PlayerCustomClickEvent via PlayerGameConnection.
     */
    private static Player getPlayerFromConnection(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        ConsoleLogger.warn("[CodePanelDialog] Could not get Player from event connection");
        return null;
    }

    /**
     * Registers the listener in the plugin.
     */
    public static void register() {
        Bukkit.getPluginManager().registerEvents(new CodePanelDialogHandler(), Main.getInstance());
        ConsoleLogger.info("[CodePanelDialog] CodePanelDialogHandler registered");
    }
}
