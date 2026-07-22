package com.mcplugin.command;

import com.mcplugin.command.subcommands.LegacySubCommandAdapter;
import com.mcplugin.command.subcommands.*;
import com.mcplugin.command.home.HomeCommand;
import com.mcplugin.command.vote.VoteManager;
import com.mcplugin.util.ConsoleLogger;
import static com.mcplugin.command.subcommands.LegacySubCommandAdapter.tc;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Диспетчер команд /mp.
 * <p>
 * Все субкоманды регистрируются в {@link SubCommandRegistry}.
 * Новые субкоманды: создай класс implements {@link SubCommand} и
 * добавь {@code registry.register(new MyCmd())} в init().
 * Не нужно править dispatch или tabComplete — всё автоматически.
 */
public class PluginReloadCommand implements CommandExecutor, TabCompleter {

    private static boolean initialized = false;

    /**
     * Инициализирует реестр субкоманд. Вызывается один раз при старте.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        SubCommandRegistry registry = SubCommandRegistry.getInstance();

        // ── Авто-обнаружение SubCommand-классов через сканирование JAR ──
        com.mcplugin.core.CommandScanner.autoRegister(registry,
                com.mcplugin.core.Main.getInstance(),
                "com/mcplugin/command/subcommands");

        // ── SubCommand-имплементации (новее) ──
        // HelpSubCommand регистрируется через авто-сканер (CommandScanner) — не нужно вручную
        registry.register(LegacySubCommandAdapter.of("reload",
                (s, a) -> ReloadSubcommand.execute(s)));

        // ── Legacy адаптеры с tab-complete ──
        registry.register(LegacySubCommandAdapter.of("punish", PunishSubcommand::execute,
                tc((s, a) -> PunishSubcommand.tabComplete(a))));
        registry.register(LegacySubCommandAdapter.of("money", EconomySubcommand::execute,
                tc((s, a) -> EconomySubcommand.tabComplete(a))));
        registry.register(LegacySubCommandAdapter.of("whitelist", WhitelistSubcommand::execute,
                tc((s, a) -> WhitelistSubcommand.tabComplete(a))));
        registry.register(LegacySubCommandAdapter.of("chgop", ChgOpSubcommand::execute,
                tc((s, a) -> ChgOpSubcommand.tabComplete(a))));
        registry.register(LegacySubCommandAdapter.of("bc", BroadcastSubcommand::execute,
                tc((s, a) -> BroadcastSubcommand.tabComplete(a))));
        registry.register(LegacySubCommandAdapter.of("blacklist", BlacklistSubcommand::execute,
                tc((s, a) -> BlacklistSubcommand.tabComplete(a))));
        registry.register(LegacySubCommandAdapter.of("maint", MaintSubcommand::execute,
                tc((s, a) -> MaintSubcommand.tabComplete(a))));
        registry.register(LegacySubCommandAdapter.of("opwhitelist", OpWhitelistSubcommand::execute,
                tc((s, a) -> OpWhitelistSubcommand.tabComplete(a))));

        // ── Legacy адаптеры (простые статические вызовы) ──
        registry.register(LegacySubCommandAdapter.of("chgdim", ChgDimSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("codepane", CodePaneSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("pane_click", CodePaneSubcommand::paneClick));
        registry.register(LegacySubCommandAdapter.of("item", ItemSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("auth", AuthSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("power", PowerSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("modules", ModulesSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("checkver",
                (s, a) -> { UpdateSubcommand.checkOnly(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("updatejar",
                (s, a) -> { UpdateSubcommand.downloadAndReplace(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("vanish",
                (s, a) -> { MiscSubcommand.vanish(s, a); return true; }));
        registry.register(LegacySubCommandAdapter.of("notes",
                (s, a) -> { MiscSubcommand.notes(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("setrad", RadiationSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("togglespeed",
                (s, a) -> { MiscSubcommand.toggleSpeed(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("togglefly",
                (s, a) -> { MiscSubcommand.toggleFly(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("toggleautocraft",
                (s, a) -> { MiscSubcommand.toggleAutoCraft(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("togglebb",
                (s, a) -> { MiscSubcommand.toggleBossBar(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("togglesb",
                (s, a) -> { MiscSubcommand.toggleScoreboard(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("toggleping",
                (s, a) -> { MiscSubcommand.togglePing(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("swapjar", SwapJarSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("near", NearSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("rtp", RtpSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("meteor", MeteorSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("plugin", PluginSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("report", ReportSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("reports", ReportsSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("modreport", ModReportSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("repstatus",
                (s, a) -> { RepStatusSubcommand.execute(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("check", CheckSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("uncheck",
                (s, a) -> { CheckSubcommand.uncheck(s, a); return true; }));
        registry.register(LegacySubCommandAdapter.of("expsplit", ExpSplitSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("invsee", InvseeCommand::execute));
        registry.register(LegacySubCommandAdapter.of("endersee",
                (s, a) -> { InvseeCommand.executeEnder(s, a); return true; }));
        registry.register(LegacySubCommandAdapter.of("ac", AcStatsSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("cilist",
                (s, a) -> { CilistCommand.execute(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("togglebind",
                (s, a) -> { MiscSubcommand.toggleBind(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("toggleradview",
                (s, a) -> { MiscSubcommand.toggleRadView(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("fly",
                (s, a) -> { MiscSubcommand.fly(s, a); return true; }));
        registry.register(LegacySubCommandAdapter.of("god",
                (s, a) -> { MiscSubcommand.god(s, a); return true; }));

        // ── Heal/Feed с tab-complete ──
        var healTc = tc((s, a) -> HealFeedSubcommand.tabComplete(a));
        registry.register(LegacySubCommandAdapter.of("heal",
                (s, a) -> { HealFeedSubcommand.heal(s, a); return true; }, healTc));
        registry.register(LegacySubCommandAdapter.of("feed",
                (s, a) -> { HealFeedSubcommand.feed(s, a); return true; }, healTc));

        // ── Home — консолидирован (1 команда, 6 алиасов) ──
        registry.register(LegacySubCommandAdapter.of("home",
                (s, a) -> { HomeCommand.dispatch(s, a); return true; },
                tc((s, a) -> HomeCommand.tabComplete(s, a)),
                java.util.List.of("sethome", "delhome", "listhomes", "ophomels", "opdelhome")));

        // ── Spawn ──
        registry.register(LegacySubCommandAdapter.of("setspawn", (s, a) -> {
            SpawnCommand.dispatch(s, new String[]{"setspawn", a.length > 1 ? a[1] : ""});
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("spawn", (s, a) -> {
            SpawnCommand.dispatch(s, new String[]{});
            return true;
        }));

        // ── Субкоманды с дополнительной логикой ──
        registry.register(LegacySubCommandAdapter.of("menu", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            com.mcplugin.mechanics.features.omniscanner.AdminMenuGUI.open(p);
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("suicide", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            SuicideCommand.execute(p);
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("forcesuicide", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (!p.hasPermission("mcplugin.command.forcesuicide")) return false;
            if (a.length < 2) return false;
            Player target = Bukkit.getPlayerExact(a[1]);
            if (target == null) return false;
            SuicideCommand.forceExecute(target, p);
            p.sendMessage("§a✔ §fPlayer §e" + target.getName() + " §fhas been force-suicided.");
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("str", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (!p.hasPermission("mcplugin.command.structures")) return false;
            StructureSubcommand.execute(p, a);
            return true;
        }));

        // ── Protection Block admin ops ──
        registry.register(LegacySubCommandAdapter.of("protection",
                (s, a) -> { ProtectionSubcommand.execute(s, a); return true; }));
        registry.register(LegacySubCommandAdapter.of("chgdim_teleport", (s, a) -> {
            if (!(s instanceof Player p) || a.length < 2) return false;
            ChgDimCommand.teleport(p, a[1]);
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("chgdim_return", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            ChgDimCommand.teleportBack(p);
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("op", (s, a) -> {
            if (!(s instanceof ConsoleCommandSender)) return false;
            if (a.length < 2) return false;
            Player target = Bukkit.getPlayerExact(a[1]);
            if (target == null) return false;
            target.setOp(true);
            ConsoleLogger.info("[OpManager] Console granted OP to " + target.getName());
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("deop", (s, a) -> {
            if (!(s instanceof ConsoleCommandSender)) return false;
            if (a.length < 2) return false;
            Player target = Bukkit.getPlayerExact(a[1]);
            if (target == null) return false;
            target.setOp(false);
            ConsoleLogger.info("[OpManager] Console revoked OP from " + target.getName());
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("i_want_to_get_impossible_achivement_uwu", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            try {
                var adv = Bukkit.getAdvancement(
                        new org.bukkit.NamespacedKey("minecraft", "datapack/impossible"));
                if (adv != null) {
                    var progress = p.getAdvancementProgress(adv);
                    if (!progress.isDone()) {
                        progress.awardCriteria("1");
                        p.sendMessage("§b✦ §fНевозможное достижение получено!");
                    }
                }
            } catch (Exception ignored) {}
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("unlock", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (a.length < 2) return false;
            return switch (a[1].toLowerCase()) {
                case "book" -> { MiscSubcommand.unlockBook(s); yield true; }
                case "sign" -> { MiscSubcommand.unlockSign(s); yield true; }
                default -> false;
            };
        }));
        registry.register(LegacySubCommandAdapter.of("askcords", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            AskCordsManager.execute(p, a);
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("askcords_accept", (s, a) -> {
            if (!(s instanceof Player p) || a.length < 2) return false;
            AskCordsManager.accept(p, a[1]);
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("askcords_decline", (s, a) -> {
            if (!(s instanceof Player p) || a.length < 2) return false;
            AskCordsManager.decline(p, a[1]);
            return true;
        }));

        // ── AoE Enchant ──
        registry.register(LegacySubCommandAdapter.of("enchant", EnchantSubcommand::execute,
                tc((s, a) -> EnchantSubcommand.tabComplete(s, a))));
        registry.register(LegacySubCommandAdapter.of("vote", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (!p.hasPermission("mcplugin.command.vote")) return false;
            if (a.length < 2) {
                VoteManager.list(p);
                return true;
            }
            String vs = a[1].toLowerCase();
            return switch (vs) {
                case "create" -> {
                    if (!p.hasPermission("mcplugin.command.vote.create")) yield false;
                    if (a.length < 5) yield false;
                    VoteManager.parseCreate(p, a, 2);
                    yield true;
                }
                case "delete" -> {
                    if (a.length < 3) yield false;
                    VoteManager.delete(p, a[2]);
                    yield true;
                }
                case "change" -> {
                    if (!p.hasPermission("mcplugin.command.vote.change")) yield false;
                    if (a.length < 4) yield false;
                    VoteManager.change(p, a[2], a, 3);
                    yield true;
                }
                case "stats" -> {
                    if (!p.hasPermission("mcplugin.command.vote.stats")) yield false;
                    if (a.length < 3) yield false;
                    VoteManager.view(p, a[2]);
                    yield true;
                }
                default -> {
                    String vn = a[1];
                    if (a.length >= 3) VoteManager.vote(p, vn, a[2]);
                    else VoteManager.view(p, vn);
                    yield true;
                }
            };
        }));

        ConsoleLogger.info("[COMMANDS] SubCommand registry initialized.");
    }

    // =========================
    // CommandExecutor
    // =========================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return SubCommandRegistry.getInstance().dispatch(sender, args);
    }

    // =========================
    // TabCompleter
    // =========================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return SubCommandRegistry.getInstance().tabComplete(sender, args);
    }
}
