package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.config.AddonCatalog;
import com.ultimateimprovments.config.AddonConfigManager;
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
 *       granted via {@code ui.*});</li>
 *   <li>the {@code config.commands.enabled} config flag (default
 *       {@code false} — the whole subcommand refuses to run while disabled);</li>
 *   <li>an explicit confirmation step ({@code /ui config confirm} within
 *       60 seconds, clickable buttons like {@code /ui addon}).</li>
 * </ul>
 */
public class ConfigSubcommand implements SubCommand {

    /** How long a pending request stays valid, in ms. */
    private static final long CONFIRM_TTL_MS = 60_000L;

    /** sender uuid → pending destructive request (regen or reset). */
    private static final Map<UUID, PendingAction> PENDING = new ConcurrentHashMap<>();

    /** Kind of a pending destructive action. */
    private enum ActionKind { REGEN, RESET }

    /** One confirmed-pending request. */
    private record PendingAction(ActionKind kind, List<String> addons, long createdAt) {}

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "regen" -> handleRegen(sender, args);
            case "reset" -> handleReset(sender, args);
            case "confirm" -> handleConfirm(sender);
            case "cancel" -> {
                PENDING.remove(uuid(sender));
                sender.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>Cancelled.</white>"));
            }
            default -> usage(sender);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // /ui config <sub>
        if (args.length == 2) {
            return filter(List.of("regen", "reset", "confirm", "cancel"), args[1]);
        }
        // /ui config regen <file>
        if (args.length == 3 && "regen".equalsIgnoreCase(args[1])) {
            return filter(brokenConfigFileNames(), args[2]);
        }
        // /ui config reset <addon|all>
        if (args.length == 3 && "reset".equalsIgnoreCase(args[1])) {
            List<String> options = new ArrayList<>(AddonConfigManager.regenerableAddons());
            options.add("all");
            return filter(options, args[2]);
        }
        return List.of();
    }

    // ============================================================
    // REGEN
    // ============================================================

    private void handleRegen(CommandSender sender, String[] args) {
        if (!requireEnabled(sender) || !requirePermission(sender, Permissions.CMD_CONFIG_REGEN)) return;
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
        PENDING.put(uuid(sender), new PendingAction(ActionKind.REGEN, List.of(addon), System.currentTimeMillis()));

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
        PendingAction pending = PENDING.remove(uuid(sender));
        if (pending == null) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ No pending action. Run </red><white>/ui config regen UI-<Addon>.toml</white><red> or </red>"
                            + "<white>/ui config reset <addon|all></white><red> first.</red>"));
            return;
        }
        if (System.currentTimeMillis() - pending.createdAt() > CONFIRM_TTL_MS) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ The pending action expired (60 s). Run the command again.</red>"));
            return;
        }
        // The flag/permission can be flipped between the request and the confirm — re-check.
        boolean allowed = switch (pending.kind()) {
            case REGEN -> requireEnabled(sender) && requirePermission(sender, Permissions.CMD_CONFIG_REGEN);
            case RESET -> requireEnabled(sender) && requirePermission(sender, Permissions.CMD_CONFIG_RESET);
        };
        if (!allowed) return;

        int ok = 0;
        List<String> failures = new ArrayList<>();
        for (String addon : pending.addons()) {
            try {
                String backup = AddonConfigManager.backupAndRegenerate(addon);
                ok++;
                sender.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <yellow>" + esc(addon) + ".toml</yellow><white> "
                                + (pending.kind() == ActionKind.REGEN ? "regenerated" : "reset")
                                + "</white><gray> — backed up as configs/" + esc(backup) + "</gray>"));
                ConsoleLogger.info("[Config] " + sender.getName()
                        + (pending.kind() == ActionKind.REGEN ? " regenerated " : " reset ")
                        + addon + ".toml (backup: " + backup + ")");
            } catch (Exception e) {
                failures.add(addon + ": " + e.getMessage());
                ConsoleLogger.warn("[Config] " + pending.kind() + " of " + addon + ".toml failed: " + e.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Failed (" + failures.size() + "): </red><white>" + esc(String.join("; ", failures)) + "</white>"));
        }
        if (ok > 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<gray>Done (" + ok + " file(s)). Run </gray><white>/ui reload</white><gray> to apply.</gray>"));
        }
    }

    // ============================================================
    // RESET (/ui config reset <addon|all>)
    // ============================================================

    private void handleReset(CommandSender sender, String[] args) {
        if (!requireEnabled(sender) || !requirePermission(sender, Permissions.CMD_CONFIG_RESET)) return;
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌ Usage: </red><white>/ui config reset UI-Other</white><gray> or </red><white>/ui config reset all</white>"));
            return;
        }

        String raw = args[2];
        List<String> addons;
        if (raw.equalsIgnoreCase("all")) {
            addons = AddonConfigManager.regenerableAddons();
        } else {
            String addon = AddonCatalog.lookup(raw);
            if (addon == null) {
                sender.sendMessage(MessageUtil.parse(
                        "<red>❌ Unknown addon: </red><white>" + esc(raw)
                                + "</white><gray>. Tab-complete lists the valid names.</gray>"));
                return;
            }
            addons = List.of(addon);
        }

        PENDING.put(uuid(sender), new PendingAction(ActionKind.RESET, addons, System.currentTimeMillis()));

        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(MessageUtil.parse(
                "<yellow>⚠</yellow> <red>You are about to RESET " + addons.size() + " config(s) to the bundled defaults:</red>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>" + esc(String.join(", ", addons)) + "</white>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>All current edits are LOST — every file is backed up as</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <white>configs/UI-<Addon>-broken-<N>.toml</white><gray> first.</gray>"));
        sender.sendMessage(MessageUtil.parse(""));
        sender.sendMessage(confirmButton("/ui config confirm")
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(cancelButton("/ui config cancel")));
        sender.sendMessage(MessageUtil.parse(""));
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private boolean requireEnabled(CommandSender sender) {
        if (commandsEnabled()) return true;
        CommandErrors.moduleDisabled(sender, "config");
        return false;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(MessageUtil.parse(
                "<red>❌ You need the </red><white>" + permission
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
                "  <white>/ui config reset <addon|all></white> <dark_gray>—</dark_gray>"
                        + " <gray>reset config(s) to the bundled defaults (backed up first)</gray>"));
        sender.sendMessage(MessageUtil.parse(
                "  <gray>Available now: </gray><white>" + String.join(", ", brokenConfigFileNames()) + "</white>"));
    }
}
