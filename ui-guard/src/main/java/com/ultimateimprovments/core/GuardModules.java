package com.ultimateimprovments.core;

import com.ultimateimprovments.mechanics.security.botprotect.BotProtectionListener;
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
import org.bukkit.event.HandlerList;
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
    }
}
