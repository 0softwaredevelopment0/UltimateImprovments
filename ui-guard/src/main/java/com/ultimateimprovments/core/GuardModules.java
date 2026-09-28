package com.ultimateimprovments.core;

import com.ultimateimprovments.maintenance.MaintenanceManager;
import com.ultimateimprovments.mechanics.security.botprotect.BotProtectionListener;
import com.ultimateimprovments.mechanics.security.check.CheckListener;
import com.ultimateimprovments.mechanics.security.check.CheckManager;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelCleanupTask;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelDialogHandler;
import com.ultimateimprovments.mechanics.security.codepanel.CodePanelSession;
import com.ultimateimprovments.mechanics.security.sudo.SudoCommandInterceptor;
import com.ultimateimprovments.mechanics.security.sudo.SudoManager;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.module.SimpleModule;
import com.ultimateimprovments.server.EmergencyEntitiesKill;
import com.ultimateimprovments.server.PacketGuard;
import com.ultimateimprovments.server.ProxyServerListener;
import com.ultimateimprovments.server.RedstoneGuard;
import com.ultimateimprovments.server.RedstoneGuardListener;
import com.ultimateimprovments.server.RedstoneGuardTask;
import com.ultimateimprovments.server.ServerOverloadListener;
import com.ultimateimprovments.server.ServerOverloadWarning;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * GuardModules — registration of the guard addon's modules (packet/proxy/redstone
 * guard, bot protection, server overload). Moved out of ui-other's SimpleModules.
 */
public final class GuardModules {

    private GuardModules() {}

    public static void registerAll(ModuleManager mm) {
        // RedstoneGuard
        mm.register(new SimpleModule("RedstoneGuard", "infrastructure/server", false) {
            private BukkitTask task;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                RedstoneGuard.init(main);
                main.getServer().getPluginManager().registerEvents(new RedstoneGuardListener(), main);
                task = new RedstoneGuardTask().runTaskTimer(main, 1L, 1L);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (task != null) {
                    task.cancel();
                    task = null;
                }
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                RedstoneGuard.reload();
                EmergencyEntitiesKill.reload();
                ServerOverloadWarning.reload();
            }
        });

        // PacketGuard
        mm.register(new SimpleModule("PacketGuard", "infrastructure/server", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                PacketGuard.init((Main) plugin);
            }
        });

        // ProxyServer
        mm.register(new SimpleModule("ProxyServer", "infrastructure/server", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                ProxyServerListener.init((Main) plugin);
            }
        });

        // BotProtection
        mm.register(new PluginModule("BotProtection", "mechanics/security/botprotect", false) {
            private BotProtectionListener listener;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                this.listener = new BotProtectionListener(main);
                main.getServer().getPluginManager().registerEvents(listener, main);
                ConsoleLogger.info("[BotProtection] Anti-bot system initialized.");
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (listener != null) {
                    HandlerList.unregisterAll(listener);
                    this.listener = null;
                }
            }

            @Override
            protected void onReloadConfig(JavaPlugin plugin) {
                if (listener != null) {
                    listener.loadConfig();
                }
            }
        });

        // ServerOverload
        mm.register(new SimpleModule("ServerOverload", "mechanics/features/server_overload", false) {
            private BukkitTask overloadTask;
            private BukkitTask warningTask;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                ServerOverloadListener.register(main);
                overloadTask = new EmergencyEntitiesKill().runTaskTimer(main, 20L, 20L);
                warningTask = new ServerOverloadWarning().runTaskTimer(main, 20L, 20L);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (overloadTask != null) {
                    overloadTask.cancel();
                    overloadTask = null;
                }
                if (warningTask != null) {
                    warningTask.cancel();
                    warningTask = null;
                }
            }
        });

        // Check (freeze suspects, inspector instrument panel)
        mm.register(new SimpleModule("Check", "mechanics/security/check", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                CheckManager.init();
                JavaPlugin guard = UIGuard.getInstance();
                guard.getServer().getPluginManager().registerEvents(new CheckListener(), guard);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                CheckManager.shutdown();
            }
        });

        // CodePanel (numeric code doors: dialog + key database)
        mm.register(new SimpleModule("CodePanel", "mechanics/security/codepanel", false) {
            private BukkitTask cleanupTask;

            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                JavaPlugin guard = UIGuard.getInstance();
                CodePanelDialogHandler.register(guard);
                guard.getServer().getPluginManager().registerEvents(new CodePanelQuitListener(), guard);
                cleanupTask = new CodePanelCleanupTask().runTaskTimer(guard, 200L, 400L);
            }

            @Override
            protected void onDisable(JavaPlugin plugin) {
                if (cleanupTask != null) {
                    cleanupTask.cancel();
                    cleanupTask = null;
                }
            }
        });

        // Sudo (GitHub-style sudo mode for dangerous commands)
        mm.register(new SimpleModule("Sudo", "mechanics/security/sudo", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                if (!SudoManager.isEnabled()) {
                    ConsoleLogger.info("[SudoModule] Sudo mode is disabled in config (sudo.enabled: false).");
                    return;
                }
                SudoManager.init();
                JavaPlugin guard = UIGuard.getInstance();
                guard.getServer().getPluginManager().registerEvents(new SudoCommandInterceptor(), guard);
                guard.getServer().getPluginManager().registerEvents(new SudoQuitListener(), guard);
                ConsoleLogger.info("[SudoModule] Sudo mode initialized.");
            }
        });

        // Maintenance (whitelist-only join mode)
        mm.register(new SimpleModule("Maintenance", "maintenance", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                MaintenanceManager.init();
            }
        });
    }

    /**
     * Clears a player's sudo state on quit (sessions, cooldowns, pending commands).
     */
    private static class SudoQuitListener implements Listener {
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            SudoManager manager = SudoManager.getInstance();
            if (manager != null) {
                manager.removePlayer(event.getPlayer().getUniqueId());
            }
        }
    }

    /** Drops the per-player code-panel input buffer on quit. */
    private static class CodePanelQuitListener implements Listener {
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            CodePanelSession.cleanup(event.getPlayer().getUniqueId());
        }
    }
}
