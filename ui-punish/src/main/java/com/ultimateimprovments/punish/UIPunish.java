package com.ultimateimprovments.punish;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.whitelist.BlacklistManager;
import com.ultimateimprovments.whitelist.OpWhitelistManager;
import com.ultimateimprovments.whitelist.WhitelistManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class UIPunish extends JavaPlugin {

    private static UIPunish instance;

    @Override
    public void onEnable() {
        instance = this;
        Main main = Main.getInstance();
        if (main == null) {
            getLogger().severe("UI-Core not loaded! UI-Punish cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var pm = getServer().getPluginManager();

        // Register punishment listener
        pm.registerEvents(new PunishJoinListener(), main);

        // Register /ui punish in the shared SubCommandRegistry. The CommandScanner
        // only scans the UI-Core JAR, so module subcommands must register
        // themselves — otherwise /ui punish falls into "unknown command".
        com.ultimateimprovments.command.SubCommandRegistry registry =
                com.ultimateimprovments.command.SubCommandRegistry.getInstance();
        if (registry != null) {
            registry.register(com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.of(
                    "punish",
                    com.ultimateimprovments.command.subcommands.PunishSubcommand::execute,
                    com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.tc(
                            (s, a) -> com.ultimateimprovments.command.subcommands.PunishSubcommand.tabComplete(a))));

            // Access-list commands live in this module too. They lost their
            // registration in the multi-module refactor (the CommandScanner only
            // scans the UI-Core JAR), so /ui whitelist, /ui blacklist and
            // /ui opwhitelist answered "unknown command".
            registry.register(com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.of(
                    "whitelist",
                    com.ultimateimprovments.command.subcommands.WhitelistSubcommand::execute,
                    com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.tc(
                            (s, a) -> com.ultimateimprovments.command.subcommands.WhitelistSubcommand.tabComplete(a))));
            registry.register(com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.of(
                    "blacklist",
                    com.ultimateimprovments.command.subcommands.BlacklistSubcommand::execute,
                    com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.tc(
                            (s, a) -> com.ultimateimprovments.command.subcommands.BlacklistSubcommand.tabComplete(a))));
            registry.register(com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.of(
                    "opwhitelist",
                    com.ultimateimprovments.command.subcommands.OpWhitelistSubcommand::execute,
                    com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter.tc(
                            (s, a) -> com.ultimateimprovments.command.subcommands.OpWhitelistSubcommand.tabComplete(a))));
        } else {
            getLogger().severe("SubCommandRegistry not available — /ui punish is not registered!");
        }

        // Initialize whitelist/blacklist
        WhitelistManager.init(main);
        BlacklistManager.init(main);
        OpWhitelistManager.init(main);

        // NOTE: the periodic AccessListCheckTask is started by UI-Other
        // (initPostModuleSystems) — starting it here too only recreated the
        // task with a new id on every enable (double kicks/deops on one pass).

        // Clean old kicks async
        Bukkit.getScheduler().runTaskAsynchronously(main, PunishmentManager::deleteOldKicks);

        getLogger().info("UI-Punish enabled!");
    }

    @Override
    public void onDisable() {
        // AccessListCheckTask is owned by UI-Other — do not stop it here.
        org.bukkit.event.HandlerList.unregisterAll(this);
        getLogger().info("UI-Punish disabled!");
        instance = null;
    }

    public static UIPunish getInstance() {
        return instance;
    }
}
