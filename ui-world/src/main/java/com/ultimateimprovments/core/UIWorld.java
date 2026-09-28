package com.ultimateimprovments.core;

import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.mechanics.features.world.CmdBlockTracker;
import com.ultimateimprovments.mechanics.features.world.EnderPearlChallenge;
import com.ultimateimprovments.mechanics.features.world.WoodcutterChallenge;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

/**
 * UIWorld — the world-mechanics addon (block collapse/friction, beacon/dragon egg,
 * wireless redstone, minecart speed, bedrock/sky challenges, meteor).
 * Modules are declared in {@link WorldModules}.
 */
public class UIWorld extends JavaPlugin {

    private static final Set<String> OWNED_MODULES = Set.of(
            "Beacon", "BlockCollapse", "DragonEgg", "BlockFriction", "BedrockBreak", "Kaboom",
            "EarthCore", "WoodcutterChallenge", "EnderPearlChallenge", "NetheriteKing",
            "OutOfMemory", "ServerFreeze", "CmdBlockTracker", "DeathBell", "MinecartSpeed",
            "WirelessRedstone", "Meteor");

    private static UIWorld instance;

    public static UIWorld getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        ConsoleLogger.info("");
        ConsoleLogger.info("===========================================");
        ConsoleLogger.info("  UI-World v" + getDescription().getVersion());
        ConsoleLogger.info("===========================================");

        ModuleManager mm = ModuleManager.getInstance();
        if (mm == null) {
            ConsoleLogger.error("[UI-World] UI-Core not loaded! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        WorldModules.registerAll(mm);
        mm.initAll();

        registerCommands();
        reportModuleStats(mm);

        ConsoleLogger.success("[UI-World] World mechanics enabled!");
    }

    @Override
    public void onDisable() {
        ConsoleLogger.info("[UI-World] Disabling...");
        HandlerList.unregisterAll(this);
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null) {
            mm.shutdownAll();
        }
        instance = null;
        ConsoleLogger.success("[UI-World] Disabled!");
    }

    /** Registers the addon's {@code /ui} subcommands (static utilities). */
    private void registerCommands() {
        try {
            SubCommandRegistry registry = SubCommandRegistry.getInstance();
            registry.register(LegacySubCommandAdapter.of("cmdblocklist", CmdBlockTracker::execute));
            registry.register(LegacySubCommandAdapter.of("advancement", (s, a) -> {
                if (!(s instanceof Player p)) return false;
                if (a.length < 2) {
                    p.sendMessage(MessageUtil.parse("<red>Использование: <white>/ui advancement start <название></white>"));
                    return true;
                }
                if (a[1].equalsIgnoreCase("start")) {
                    if (a.length < 3) {
                        p.sendMessage(MessageUtil.parse("<red>Укажи название ачивки: <white>woodcutter, teleport</white>"));
                        return true;
                    }
                    String challenge = a[2];
                    if (challenge.equalsIgnoreCase("teleport") || challenge.equalsIgnoreCase("let_me_teleport")) {
                        EnderPearlChallenge.start(p, challenge);
                    } else {
                        WoodcutterChallenge.start(p, challenge);
                    }
                    return true;
                }
                if (a[1].equalsIgnoreCase("stop")) {
                    boolean stopped = WoodcutterChallenge.stop(p) || EnderPearlChallenge.stop(p);
                    if (!stopped) {
                        p.sendMessage(MessageUtil.parse("<red>✖ <white>You don't have an active challenge.</white>"));
                    }
                    return true;
                }
                p.sendMessage(MessageUtil.parse("<red>Неизвестная подкоманда. Доступно: <white>start, stop</white>"));
                return true;
            }, (s, a) -> {
                if (a.length == 2) return List.of("start", "stop");
                if (a.length == 3 && a[1].equalsIgnoreCase("start"))
                    return List.of("woodcutter", "teleport");
                return List.of();
            }));
        } catch (Exception e) {
            ConsoleLogger.warn("[UI-World] Failed to register commands: " + e.getMessage());
        }
    }

    /** Feeds only this addon's module counters/failures into AddonRegistry. */
    private void reportModuleStats(ModuleManager mm) {
        try {
            int total = 0;
            int loaded = 0;
            for (var m : mm.getModules()) {
                if (!OWNED_MODULES.contains(m.getName())) continue;
                total++;
                if (m.isEnabled()) {
                    loaded++;
                } else {
                    com.ultimateimprovments.addon.AddonRegistry.reportError(getName(),
                            "module " + m.getName() + " failed: " + m.getDisableReason());
                }
            }
            com.ultimateimprovments.addon.AddonRegistry.reportModules(getName(), loaded, total);
        } catch (Throwable t) {
            ConsoleLogger.warn("[UI-World] Module stats report failed: " + t.getMessage());
        }
    }
}
