package com.ultimateimprovments.module;

import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.*;
import com.ultimateimprovments.command.home.HomeCommand;
import com.ultimateimprovments.report.ReportManager;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class UIEssentials extends JavaPlugin {

    private static UIEssentials instance;

    public static UIEssentials getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        // Single config lives in UI-Core (Main.getInstance().getConfig());
        // UI-Essentials does not ship its own config.yml.
        ConsoleLogger.info("[Essentials] Initializing...");
        ReportManager.init();
        registerCommands();
        ConsoleLogger.info("[Essentials] Enabled.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        ConsoleLogger.info("[Essentials] Disabled.");
    }

    private void registerCommands() {
        try {
            SubCommandRegistry registry = SubCommandRegistry.getInstance();
            if (registry == null) {
                ConsoleLogger.warn("[Essentials] SubCommandRegistry not available.");
                return;
            }

            // Home system
            registry.register(LegacySubCommandAdapter.of("home",
                    (s, a) -> { HomeCommand.dispatch(s, a); return true; },
                    LegacySubCommandAdapter.tc((s, a) -> HomeCommand.tabComplete(s, a)),
                    java.util.List.of("sethome", "delhome", "listhomes", "ophomels", "opdelhome")));

            // Heal/Feed
            registry.register(LegacySubCommandAdapter.of("heal",
                    (s, a) -> { HealFeedSubcommand.heal(s, a); return true; },
                    LegacySubCommandAdapter.tc((s, a) -> HealFeedSubcommand.tabComplete(a))));
            registry.register(LegacySubCommandAdapter.of("feed",
                    (s, a) -> { HealFeedSubcommand.feed(s, a); return true; },
                    LegacySubCommandAdapter.tc((s, a) -> HealFeedSubcommand.tabComplete(a))));

            // Teleport
            registry.register(LegacySubCommandAdapter.of("rtp", RtpSubcommand::execute));
            registry.register(LegacySubCommandAdapter.of("spawn",
                    (s, a) -> { SpawnCommand.dispatch(s, a); return true; }));
            registry.register(LegacySubCommandAdapter.of("setspawn",
                    (s, a) -> { SpawnCommand.dispatch(s, new String[]{"setspawn"}); return true; }));

            // Position
            registry.register(new GetPosSubcommand());
            registry.register(new SharePosSubcommand());

            // Near
            registry.register(LegacySubCommandAdapter.of("near", NearSubcommand::execute));

            // UUID
            registry.register(LegacySubCommandAdapter.of("uuid", UuidSubcommand::execute,
                    LegacySubCommandAdapter.tc((s, a) -> UuidSubcommand.tabComplete(a))));

            // Fly speed
            registry.register(LegacySubCommandAdapter.of("flyspeed", FlySpeedSubcommand::execute,
                    LegacySubCommandAdapter.tc((s, a) -> FlySpeedSubcommand.tabComplete(a))));

            // Invsee/Endersee
            registry.register(new InvseeSubcommand());
            registry.register(new EnderseeSubcommand());

            // Reports
            registry.register(LegacySubCommandAdapter.of("report", ReportSubcommand::execute,
                    LegacySubCommandAdapter.tc((s, a) -> ReportSubcommand.tabComplete(a))));
            registry.register(LegacySubCommandAdapter.of("reports", ReportsSubcommand::execute,
                    LegacySubCommandAdapter.tc((s, a) -> ReportsSubcommand.tabComplete(a))));
            registry.register(LegacySubCommandAdapter.of("modreport", ModReportSubcommand::execute));

            // PDC
            registry.register(new PdcSubcommand());

            // Cilist
            registry.register(LegacySubCommandAdapter.of("cilist",
                    (s, a) -> { CilistCommand.execute(s); return true; }));

            // Help
            registry.register(new HelpSubCommand());

            // ChgOp
            registry.register(LegacySubCommandAdapter.of("chgop", ChgOpSubcommand::execute));

            ConsoleLogger.info("[Essentials] Registered 20+ commands.");
        } catch (Exception e) {
            ConsoleLogger.warn("[Essentials] Failed: " + e.getMessage());
        }
    }
}
