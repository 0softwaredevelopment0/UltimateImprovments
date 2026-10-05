package com.ultimateimprovments.command;

import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.command.subcommands.*;
import com.ultimateimprovments.command.vote.VoteManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import static com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.tc;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.List;
/**
     * Dispatcher of /ui commands.
     * <p>
     * All subcommands are registered in {@link SubCommandRegistry}.
     * New subcommands: create a class implementing {@link SubCommand} and
     * add {@code registry.register(new MyCmd())} in init().
     * No need to edit dispatch or tabComplete — everything is automatic.
     */
public class PluginReloadCommand implements CommandExecutor, TabCompleter {

    private static boolean initialized = false;

    /** Resets the initialization flag for a correct /ui reload. */
    public static void reset() {
        initialized = false;
    }

    /**
     * Registers plugin permissions in code (not plugin.yml) so they are
     * available to the permission system with their defaults. Idempotent —
     * safe across /ui reload (init() may run again after reset()).
     */
    private static void registerCodePermissions() {
        registerPermission("ui.command.dont_run_this_command", PermissionDefault.TRUE);
    }

    private static void registerPermission(String name, PermissionDefault def) {
        try {
            if (Bukkit.getPluginManager().getPermission(name) == null) {
                Bukkit.getPluginManager().addPermission(new Permission(name, def));
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Permissions] Failed to register " + name + ": " + e.getMessage());
        }
    }

    /**
     * Initializes the subcommand registry. Called once on startup.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // ── Permissions are registered in code (not plugin.yml) ──
        registerCodePermissions();

        SubCommandRegistry registry = SubCommandRegistry.getInstance();

        // ── Auto-discovery of SubCommand classes via JAR scanning ──
        com.ultimateimprovments.core.CommandScanner.autoRegister(registry,
                com.ultimateimprovments.core.Main.getInstance(),
                "com/ultimateimprovments/command/subcommands");

        // ── SubCommand implementations (newer) ──
        registry.register(new ItemNbtSubcommand());
        registry.register(new ClanSubcommand());
        registry.register(new ClearSubcommand()); // /ui clear <chat|attributes> — replaces /ui clearchat
        registry.register(new ChatChannelSubcommand());
        registry.register(new CmdLogSubcommand());
        registry.register(new TurretSubcommand());
        registry.register(LegacySubCommandAdapter.of("reload",
                (s, a) -> ReloadSubcommand.execute(s, a),
                tc((s, a) -> a.length == 2 ? ReloadSubcommand.tabCompleteTargets(a.length >= 2 ? a[1] : "") : List.of())));

        // ── Legacy adapters with tab-complete ──
        // ── /ui money is registered by the UI-Admin addon ──
        registry.register(LegacySubCommandAdapter.of("broadcast", BroadcastSubcommand::execute,
                tc((s, a) -> BroadcastSubcommand.tabComplete(a)),
                List.of("bc"))); // /ui bc — compatibility alias

        // ── Legacy adapters (simple static calls) ──
        registry.register(LegacySubCommandAdapter.of("chgdim", ChgDimSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("item", ItemSubcommand::execute));
        // ── /ui dura — held-item durability scaling (mechanics + raw variants) ──
        registry.register(LegacySubCommandAdapter.of("dura", DuraSubcommand::execute,
                tc((s, a) -> DuraSubcommand.tabComplete(s, a))));
        // ── /ui auth is registered by the UI-Auth addon ──
        registry.register(LegacySubCommandAdapter.of("power", PowerSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("modules", ModulesSubcommand::execute));
        // ── /ui checkver and /ui updatejar are registered by the UI-Admin addon ──
        registry.register(LegacySubCommandAdapter.of("vanish",
                (s, a) -> { MiscSubcommand.vanish(s, a); return true; }));
        registry.register(LegacySubCommandAdapter.of("notes",
                (s, a) -> { MiscSubcommand.notes(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("setrad", RadiationSubcommand::execute));
        // ── Renamed toggles (old names removed): on|off with dedicated permissions ──
        registry.register(LegacySubCommandAdapter.of("showspeed",
                (s, a) -> { MiscSubcommand.showSpeed(s, a); return true; },
                tc((s, a) -> { if (a.length == 2) return List.of("on", "off"); return List.of(); })));
        registry.register(LegacySubCommandAdapter.of("elytraboost",
                (s, a) -> { MiscSubcommand.elytraBoost(s, a); return true; },
                tc((s, a) -> { if (a.length == 2) return List.of("on", "off"); return List.of(); })));
        registry.register(LegacySubCommandAdapter.of("bossbar",
                (s, a) -> { MiscSubcommand.bossbar(s, a); return true; },
                tc((s, a) -> { if (a.length == 2) return List.of("on", "off"); return List.of(); })));
        registry.register(LegacySubCommandAdapter.of("scoreboard",
                (s, a) -> { MiscSubcommand.scoreboard(s, a); return true; },
                tc((s, a) -> { if (a.length == 2) return List.of("on", "off"); return List.of(); })));
        registry.register(LegacySubCommandAdapter.of("pingsound",
                (s, a) -> { MiscSubcommand.pingsound(s, a); return true; },
                tc((s, a) -> { if (a.length == 2) return List.of("on", "off"); return List.of(); })));
        registry.register(LegacySubCommandAdapter.of("wirelessbind",
                (s, a) -> { MiscSubcommand.wirelessbind(s, a); return true; },
                tc((s, a) -> { if (a.length == 2) return List.of("on", "off"); return List.of(); })));
        // ── Free craft: /ui craftrecipe <recipe> ──
        registry.register(LegacySubCommandAdapter.of("craftrecipe",
                CraftRecipeSubcommand::execute,
                tc((s, a) -> CraftRecipeSubcommand.tabComplete(a))));
        registry.register(LegacySubCommandAdapter.of("swapjar", SwapJarSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("meteor", MeteorSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("plugin", PluginSubcommand::execute,
                tc((s, a) -> PluginSubcommand.tabComplete(s, a))));
        // ── /ui redstone, /ui check|uncheck, /ui codepane, /ui maint, /ui sudo,
        //    /ui console, /ui server are registered by the UI-Guard addon ──
        registry.register(LegacySubCommandAdapter.of("repstatus",
                (s, a) -> { RepStatusSubcommand.execute(s); return true; }));
        registry.register(LegacySubCommandAdapter.of("expsplit", ExpSplitSubcommand::execute));
        registry.register(LegacySubCommandAdapter.of("radview",
                (s, a) -> { MiscSubcommand.radview(s, a); return true; },
                tc((s, a) -> { if (a.length == 2) return List.of("on", "off"); return List.of(); })));
        registry.register(LegacySubCommandAdapter.of("fly",
                (s, a) -> { MiscSubcommand.fly(s, a); return true; }));
        registry.register(LegacySubCommandAdapter.of("god",
                (s, a) -> { MiscSubcommand.god(s, a); return true; }));
        registry.register(LegacySubCommandAdapter.of("execchat", ExecChatSubcommand::execute,
                tc((s, a) -> ExecChatSubcommand.tabComplete(a))));

        // ── Heal/Feed with tab-complete ──
        var healTc = tc((s, a) -> HealFeedSubcommand.tabComplete(a));

        // ── Home — consolidated (1 command, 6 aliases) ──

        // ── Spawn ──

        // ── Subcommands with additional logic ──
        // ── /ui menu is registered by the UI-Items addon ──
        registry.register(LegacySubCommandAdapter.of("suicide", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (!p.hasPermission("ui.command.suicide")) {
                CommandErrors.noPermission(p, "ui.command.suicide");
                return true;
            }
            SuicideCommand.execute(p);
            return true;
        }));
        registry.register(LegacySubCommandAdapter.of("forcesuicide", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (!p.hasPermission("ui.command.forcesuicide")) { CommandErrors.noPermission(p, "ui.command.forcesuicide"); return false; }
            if (a.length < 2) return false;
            Player target = Bukkit.getPlayerExact(a[1]);
            if (target == null) return false;
            SuicideCommand.forceExecute(target, p);
            p.sendMessage(com.ultimateimprovments.util.MessageUtil.parse("<green>✔ <white>Player <yellow>" + target.getName() + " <white>has been force-suicided."));
            return true;
        }));

        // ── /ui protection is registered by the UI-Protection addon ──
        // ── /ui op, /ui deop, /ui oplist are registered by the UI-Admin addon ──
        registry.register(LegacySubCommandAdapter.of("dont_run_this_command", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (!p.hasPermission("ui.command.dont_run_this_command")) { CommandErrors.noPermission(p, "ui.command.dont_run_this_command"); return false; }
            try {
                var adv = Bukkit.getAdvancement(
                        new org.bukkit.NamespacedKey("ui", "datapack/impossible"));
                if (adv != null) {
                    var progress = p.getAdvancementProgress(adv);
                    if (!progress.isDone()) {
                        progress.awardCriteria("1");
                        p.sendMessage(MessageUtil.parse(
                                "<red>⚠ <white>Тебе же сказали <red>НЕ</red> выполнять эту команду...</white> "
                                + "<yellow>Невозможное достижение получено!</yellow>"));
                    }
                }
            } catch (Exception ignored) {}
            return true;
        }));

        // ── /ui cmdblocklist and /ui advancement are registered by the UI-World addon ──
        registry.register(LegacySubCommandAdapter.of("unlock", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (a.length < 2) {
                p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui unlock <book|sign></white>"));
                return false;
            }
            boolean handled = switch (a[1].toLowerCase()) {
                case "book" -> { MiscSubcommand.unlockBook(s); yield true; }
                case "sign" -> { MiscSubcommand.unlockSign(s); yield true; }
                default -> false;
            };
            if (!handled) {
                p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui unlock <book|sign></white>"));
            }
            return handled;
        }, tc((s, a) -> {
            if (a.length == 2) return List.of("book", "sign");
            // Empty suggestion so the dispatcher does not fall back to player names.
            return List.of("");
        })));
        // ── AskPos: dialog-based coordinate request (formerly askcords; accept/decline removed — all in dialogs) ──
        registry.register(LegacySubCommandAdapter.of("askpos", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (!p.hasPermission("ui.command.askpos")) {
                CommandErrors.noPermission(p, "ui.command.askpos");
                return true;
            }
            AskCordsManager.execute(p);
            return true;
        }));

        // ── /ui enchant is registered by the UI-Enchant addon (it owns the subcommand) ──
        registry.register(LegacySubCommandAdapter.of("vote", (s, a) -> {
            if (!(s instanceof Player p)) return false;
            if (!p.hasPermission("ui.command.vote")) { CommandErrors.noPermission(p, "ui.command.vote"); return false; }
            if (a.length < 2) {
                VoteManager.list(p);
                return true;
            }
            String vs = a[1].toLowerCase();
            return switch (vs) {
                case "create" -> {
                    if (!p.hasPermission("ui.command.vote.create")) { CommandErrors.noPermission(p, "ui.command.vote.create"); yield false; }
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
                    if (!p.hasPermission("ui.command.vote.change")) { CommandErrors.noPermission(p, "ui.command.vote.change"); yield false; }
                    if (a.length < 4) yield false;
                    VoteManager.change(p, a[2], a, 3);
                    yield true;
                }
                case "stats" -> {
                    if (!p.hasPermission("ui.command.vote.stats")) { CommandErrors.noPermission(p, "ui.command.vote.stats"); yield false; }
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
        }, tc((s, a) -> {
            if (!(s instanceof Player p)) return List.of();
            return VoteManager.tabComplete(p, a);
        })));

        // Space dimension
        registry.register(LegacySubCommandAdapter.of("space", SpaceSubcommand::execute,
                tc((s, a) -> SpaceSubcommand.tabComplete(a))));

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
