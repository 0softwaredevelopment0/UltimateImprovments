package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.commands.home.HomeCommand;
import com.mcplugin.commands.vote.VoteManager;
import com.mcplugin.config.MessagesManager;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.commands.home.HomeDatabase;
import com.mcplugin.commands.subcommands.*;
import com.mcplugin.cp.CodePanelDatabase;
import com.mcplugin.module.ModuleManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thin dispatcher — delegates to subcommand classes in {@code subcommands/} package.
 */
public class PluginReloadCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.unknown_command", "<red>❌ Unknown command! </red><gray>Use </gray><white>/mp help</white><gray> for the command list.</gray>")));
            return true;
        }

        if (sender instanceof Player p && !p.hasPermission("mcplugin")) {
            p.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission to use MC-Plugin commands!</red>")));
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "help" -> { HelpCommand.execute(sender); yield true; }
            case "chgdim", "chgdim_teleport", "chgdim_return" -> ChgDimSubcommand.execute(sender, args);
            case "codepane" -> CodePaneSubcommand.execute(sender, args);
            case "pane_click" -> CodePaneSubcommand.paneClick(sender, args);
            case "structures", "str" -> handleStructures(sender, args);
            case "item" -> ItemSubcommand.execute(sender, args);
            case "auth" -> AuthSubcommand.execute(sender, args);
            case "power" -> PowerSubcommand.execute(sender, args);
            case "suicide" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); yield true; }
                SuicideCommand.execute(player);
                yield true;
            }
            case "forcesuicide" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); yield true; }
                if (!player.hasPermission("mcplugin.command.forcesuicide")) {
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission to use this command!</red>")));
                    yield true;
                }
                if (args.length < 2) {
                    player.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/mp forcesuicide <nick></white>"));
                    yield true;
                }
                String targetName = args[1];
                @SuppressWarnings("deprecation")
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_player_not_found", "<red>❌ Player</red> <yellow>{player}</yellow> <red>not found!</red>").replace("{player}", targetName)));
                    yield true;
                }
                // Запускаем таймер суицида у цели (форсированно)
                SuicideCommand.forceExecute(target, player);
                player.sendMessage(MessageUtil.parse("<green>✅</green> <white>Player</white> <yellow>" + target.getName() + "</yellow> <white>has been force-suicided.</white>"));
                yield true;
            }
            case "modules" -> ModulesSubcommand.execute(sender, args);
            case "sethome", "home", "delhome", "listhomes", "ophomels", "opdelhome" -> HomeCommand.dispatch(sender, args);
            case "checkver" -> UpdateSubcommand.checkOnly(sender);
            case "updatejar" -> UpdateSubcommand.downloadAndReplace(sender);
            case "vanish" -> MiscSubcommand.vanish(sender, args);
            case "notes" -> MiscSubcommand.notes(sender);
            case "checkrad", "setrad" -> RadiationSubcommand.execute(sender, args);
            case "togglespeed" -> MiscSubcommand.toggleSpeed(sender);
            case "reload" -> ReloadSubcommand.execute(sender);
            case "vote" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); yield true; }
                if (!player.hasPermission("mcplugin.command.vote")) {
                    player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.no_permission", "<red>❌ You don't have permission to use this command!</red>")));
                    yield true;
                }
                if (args.length < 2) {
                    VoteManager.list(player);
                    yield true;
                }
                String voteSub = args[1].toLowerCase();
                switch (voteSub) {
                    case "create" -> {
                        if (!player.hasPermission("mcplugin.command.vote.create")) {
                            player.sendMessage(MessageUtil.parse(MessagesManager.getString("vote.errors.no_permission_create", "<red>❌ You don't have permission to create votes!</red>")));
                            yield true;
                        }
                        if (args.length < 5) {
                            player.sendMessage(MessageUtil.parse(MessagesManager.getString("vote.errors.usage_create", "<red>❌ Usage:</red> <white>/mp vote create <name> <title> <description> -answer_<N>:<title,desc> ... -time:<N><s|m|h|d></white>")));
                            yield true;
                        }
                        VoteManager.parseCreate(player, args, 2);
                    }
                    case "delete" -> {
                        if (args.length < 3) {
                            player.sendMessage(MessageUtil.parse("<red>❌ Usage:</red> <white>/mp vote delete <name></white>"));
                            yield true;
                        }
                        VoteManager.delete(player, args[2]);
                    }
                    case "change" -> {
                        if (!player.hasPermission("mcplugin.command.vote.change")) {
                            player.sendMessage(MessageUtil.parse(MessagesManager.getString("vote.errors.no_permission_change", "<red>❌ You don't have permission to change votes!</red>")));
                            yield true;
                        }
                        if (args.length < 4) {
                            player.sendMessage(MessageUtil.parse(MessagesManager.getString("vote.errors.usage_change", "<red>❌ Usage:</red> <white>/mp vote change <name> <params...></white>")));
                            yield true;
                        }
                        VoteManager.change(player, args[2], args, 3);
                    }
                    case "stats" -> {
                        if (!player.hasPermission("mcplugin.command.vote.stats")) {
                            player.sendMessage(MessageUtil.parse(MessagesManager.getString("vote.errors.no_permission_stats", "<red>❌ You don't have permission to view vote statistics!</red>")));
                            yield true;
                        }
                        if (args.length < 3) {
                            player.sendMessage(MessageUtil.parse(MessagesManager.getString("vote.errors.usage_stats", "<red>❌ Usage:</red> <white>/mp vote stats <name></white>")));
                            yield true;
                        }
                        VoteManager.view(player, args[2]);
                    }
                    default -> {
                        // /mp vote <name> [answer]
                        String voteName = args[1];
                        if (args.length >= 3) {
                            String answerStr = args[2];
                            VoteManager.vote(player, voteName, answerStr);
                        } else {
                            VoteManager.view(player, voteName);
                        }
                    }
                }
                yield true;
            }
            case "i_want_to_get_impossible_achivement_uwu" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only", "<red>❌ Only players can use this command!</red>"))); yield true; }
                grantImpossibleAdvancement(player);
                yield true;
            }
            default -> {
                sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.unknown_command", "<red>❌ Unknown command! </red><gray>Use </gray><white>/mp help</white><gray> for the command list.</gray>")));
                yield true;
            }
        };
    }

    private boolean handleStructures(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.structure_player_only", "<red>❌ Error: </red><gray>Only players can use this command.</gray>")));
            return true;
        }
        if (!player.hasPermission("mcplugin.command.structures")) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.structure_no_permission", "<red>❌ You don't have permission to manage structures!</red>")));
            return true;
        }
        StructureSubcommand.execute(player, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("help", "checkver", "updatejar",
                    "checkrad", "setrad", "reload", "structures", "str", "power", "suicide",
                    "auth", "chgdim", "chgdim_teleport", "chgdim_return", "vanish", "notes",
                    "codepane", "pane_click", "item", "modules", "togglespeed", "vote",
                    "sethome", "home", "delhome", "listhomes", "ophomels", "opdelhome"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("vote")) {
            completions.add("create");
            completions.add("delete");
            completions.add("change");
            completions.add("stats");
            completions.addAll(VoteManager.getVoteNames());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("vote") && (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("change") || args[1].equalsIgnoreCase("stats"))) {
            completions.addAll(VoteManager.getVoteNames());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("vote") && VoteManager.getVote(args[1]) != null) {
            var vote = VoteManager.getVote(args[1]);
            for (int i = 0; i < vote.answers.size(); i++) {
                completions.add(String.valueOf(i));
                completions.add(vote.answers.get(i).title);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("forcesuicide")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("auth")) {
            completions.addAll(List.of("forcelogin", "resetauth", "chgpass", "delsession", "logout"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("auth")
                && (args[1].equalsIgnoreCase("forcelogin") || args[1].equalsIgnoreCase("resetauth")
                || args[1].equalsIgnoreCase("chgpass") || args[1].equalsIgnoreCase("delsession"))) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("chgdim_teleport")) {
            ChgDimSubcommand.tabCompleteChgdimWorlds(completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("structures") || args[0].equalsIgnoreCase("str"))) {
            completions.addAll(List.of("dfc", "magnet", "lightning"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("codepane")) {
            completions.add("key");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("codepane") && args[1].equalsIgnoreCase("key")) {
            completions.addAll(List.of("add", "list", "remove", "modify"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("codepane") && args[1].equalsIgnoreCase("key")
                && (args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("modify"))) {
            completions.addAll(CodePanelDatabase.getAllKeyNames());
        } else if (args.length >= 5 && args[0].equalsIgnoreCase("codepane") && args[1].equalsIgnoreCase("key")
                && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("modify"))) {
            tabCompleteCodePaneFlags(args, completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("sethome") || args[0].equalsIgnoreCase("home") || args[0].equalsIgnoreCase("delhome"))) {
            if (sender instanceof Player player) {
                completions.addAll(HomeDatabase.getHomeNames(player.getUniqueId()));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ophomels")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("opdelhome")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("opdelhome")) {
            @SuppressWarnings("deprecation")
            var target = Bukkit.getOfflinePlayer(args[1]);
            completions.addAll(HomeDatabase.getHomeNames(target.getUniqueId()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("modules")) {
            completions.addAll(List.of("list", "enable", "disable"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("modules") && (args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("disable"))) {
            for (var m : ModuleManager.getInstance().getModules()) completions.add(m.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("power")) {
            completions.addAll(List.of("off", "reboot", "confirm", "undo"));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("structures") || args[0].equalsIgnoreCase("str")) && args[1].equalsIgnoreCase("dfc")) {
            completions.addAll(List.of("stats", "assemble"));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("structures") || args[0].equalsIgnoreCase("str")) && args[1].equalsIgnoreCase("magnet")) {
            completions.addAll(List.of("assemble", "stats"));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("structures") || args[0].equalsIgnoreCase("str")) && args[1].equalsIgnoreCase("lightning")) {
            completions.addAll(List.of("enable", "disable", "stats"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("vanish")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("checkrad")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setrad")) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        }

        String last = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

    private void grantImpossibleAdvancement(Player player) {
        try {
            var adv = Bukkit.getAdvancement(new org.bukkit.NamespacedKey("minecraft", "datapack/impossible"));
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                    player.sendMessage(MessageUtil.parse("<aqua>✦</aqua> <white>Невозможное достижение получено!</white>"));
                } else {
                    player.sendMessage(MessageUtil.parse("<yellow>✦</yellow> <white>Ты уже получил это достижение!</white>"));
                }
            }
        } catch (Exception e) {
            player.sendMessage(MessageUtil.parse("<red>❌ Ошибка при выдаче достижения!</red>"));
        }
    }

    private static void tabCompleteCodePaneFlags(String[] args, List<String> completions) {
        java.util.Set<String> usedFlags = new java.util.HashSet<>();
        for (int i = 5; i < args.length - 1; i++) {
            String a = args[i].toLowerCase();
            if (a.startsWith("attempts:")) usedFlags.add("attempts:");
            else if (a.startsWith("time:")) usedFlags.add("time:");
            else if (a.startsWith("whitelist:")) usedFlags.add("whitelist:");
            else if (a.startsWith("blacklist:")) usedFlags.add("blacklist:");
            else if (a.startsWith("command:")) usedFlags.add("command:");
        }
        String last = args[args.length - 1].toLowerCase();
        if (!last.startsWith("attempts:") && !last.startsWith("time:")
                && !last.startsWith("whitelist:") && !last.startsWith("blacklist:")
                && !last.startsWith("command:")) {
            for (String f : List.of("attempts:", "time:", "whitelist:", "blacklist:", "command:")) {
                if (!usedFlags.contains(f)) completions.add(f);
            }
        }
    }
}
