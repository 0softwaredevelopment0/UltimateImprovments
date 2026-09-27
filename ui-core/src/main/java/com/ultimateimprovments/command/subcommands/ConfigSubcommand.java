package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.config.AddonConfigManager;
import com.ultimateimprovments.config.CompositeConfig;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ui config — config management utilities.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code /ui config regen <UI-<Addon>.toml>} — back up the live config
 *       as {@code UI-<Addon>-broken-<N>.toml} (numbered 1, 2, 3, ... inside
 *       {@code configs/}) and regenerate the file from the bundled template.</li>
 * </ul>
 * Regeneration is destructive (user edits of that file are lost, only the
 * backup survives), so it is gated behind:
 * <ul>
 *   <li>the {@code ui.command.configregen} permission (default FALSE —
 *       granted via {@code ui.admin} / {@code ui.*});</li>
 *   <li>the {@code config.commands.enabled} config flag (default
 *       {@code false} — the whole subcommand refuses to run while disabled);</li>
 *   <li>an explicit confirmation step ({@code /ui config confirm} within
 *       60 seconds, clickable buttons like {@code /ui addon}).</li>
 * </ul>
 */
public class ConfigSubcommand implements SubCommand {

    /** How long a pending regeneration request stays valid, in ms. */
    private static final long CONFIRM_TTL_MS = 60_000L;

    /** sender uuid → pending regeneration target. */
    private static final Map<UUID, PendingRegen> PENDING = new ConcurrentHashMap<>();

    /** One confirmed-pending regeneration request. */
    private record PendingRegen(String addon, File configFile, long createdAt) {}

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "regen" -> handleRegen(sender, args);
            case "confirm" -> handleConfirm(sender);
            case "cancel" -> {
                PENDING.remove(uuid(sender));
                sender.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>Regeneration cancelled.</white>"));
            }
            default -> usage(sender);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // /ui config <sub>
        if (args.length == 2) {
            return filter(List.of("regen", "confirm", "cancel"), args[1]);
        }
        // /ui config regen <file>
        if (args.length == 3 && "regen".equalsIgnoreCase(args[1])) {
            return filter(brokenConfigFileNames(), args[2]);
        }
        return List.of();
    }

    // ============================================================
    // REGEN
    // ============================================================

    private void handleRegen(CommandSender sender, String[] args) {
        if (!requireEnabled(sender) || !requirePermission(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui config regen UI-Other.toml</white>"));
            return;
        }

        String fileName = args[2];
        String addon = AddonConfigManager.addonForConfigFileName(fileName);
        if (addon == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Unknown config file: </red><white>" + esc(fileName)
                            + "</white><gray>. Tab-complete lists the valid names.</gray>"));
            return;
        }

        File configFile = AddonConfigManager.configFileOf(addon);
        PENDING.put(uuid(sender), new PendingRegen(addon, configFile, System.currentTimeMillis()));

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<yellow>⚠</yellow> <red>You are about to REGENERATE the config:</red>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>" + esc(configFile.getName()) + "</white> <gray>(addon: " + esc(addon) + ")</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>All current edits to this file are LOST — they will be backed up as</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>configs/" + esc(AddonConfigManager.brokenBackupName(addon)) + "</white><gray>.</gray>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(confirmButton("/ui config confirm")
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(cancelButton("/ui config cancel")));
        sender.sendMessage(MessageUtil.parse(""));
    }

    private void handleConfirm(CommandSender sender) {
        PendingRegen pending = PENDING.remove(uuid(sender));
        if (pending == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ No pending regeneration. Run </red><white>/ui config regen UI-<Addon>.toml</white><red> first.</red>"));
            return;
        }
        if (System.currentTimeMillis() - pending.createdAt() > CONFIRM_TTL_MS) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ The pending regeneration expired (60 s). Run the command again.</red>"));
            return;
        }
        // The flag can be flipped between regen and confirm — re-check.
        if (!requireEnabled(sender) || !requirePermission(sender)) return;

        String addon = pending.addon();
        try {
            String backup = AddonConfigManager.backupAndRegenerate(addon);
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Regenerated </white><yellow>" + esc(addon) + ".toml</yellow><white>.</white>"));
            sender.sendMessage(MessageUtil.parse(
                    "  <gray>Old file backed up as </gray><white>configs/" + esc(backup) + "</white>"));
            sender.sendMessage(MessageUtil.parse(
                    "  <gray>Run </gray><white>/ui reload</white><gray> to apply.</gray>"));
            ConsoleLogger.info("[Config] " + sender.getName() + " regenerated " + addon
                    + ".toml (backup: " + backup + ")");
        } catch (Exception e) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Regeneration failed: </red><white>" + esc(e.getMessage()) + "</white>"));
            ConsoleLogger.warn("[Config] Regeneration of " + addon + ".toml failed: " + e.getMessage());
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private boolean requireEnabled(CommandSender sender) {
        if (commandsEnabled()) return true;
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ Config commands are disabled in the config</red> <dark_gray>(</dark_gray>"
                        + "<white>config.commands.enabled: false</white><dark_gray>)</dark_gray><red>.</red>"));
        return false;
    }

    private boolean requirePermission(CommandSender sender) {
        if (sender.hasPermission(Permissions.CMD_CONFIG_REGEN)) return true;
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ You need the </red><white>" + Permissions.CMD_CONFIG_REGEN
                        + "</white><red> permission for this.</red>"));
        return false;
    }

    /** Config gate: {@code config.commands.enabled}, default false. */
    static boolean commandsEnabled() {
        Main plugin = Main.getInstance();
        if (plugin == null) return false;
        return plugin.getConfig().getBoolean("config.commands.enabled", false);
    }

    /** All regenerable config file names (one per known addon). */
    private static List<String> brokenConfigFileNames() {
        List<String> names = new ArrayList<>();
        for (String addon : AddonConfigManager.regenerableAddons()) {
            names.add(addon + ".toml");
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(low)) out.add(option);
        }
        return out;
    }

    private static Component confirmButton(String command) {
        return Component.text("[", NamedTextColor.DARK_GREEN)
                .append(Component.text("✔ Confirm", NamedTextColor.GREEN))
                .append(Component.text("]", NamedTextColor.DARK_GREEN))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private static Component cancelButton(String command) {
        return Component.text("[", NamedTextColor.DARK_RED)
                .append(Component.text("✖ Cancel", NamedTextColor.RED))
                .append(Component.text("]", NamedTextColor.DARK_RED))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "");
    }

    private static UUID uuid(CommandSender sender) {
        return sender instanceof org.bukkit.entity.Player player
                ? player.getUniqueId()
                : new UUID(0, 0);
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<white>Running <yellow>UltimateImprovments</yellow> config tools:</white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>/ui config regen UI-<Addon>.toml</white> <dark_gray>—</dark_gray>"
                        + " <gray>back up (configs/UI-<Addon>-broken-<N>.toml) and regenerate from the template</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Available now: </gray><white>" + String.join(", ", brokenConfigFileNames()) + "</white>"));
    }
}
