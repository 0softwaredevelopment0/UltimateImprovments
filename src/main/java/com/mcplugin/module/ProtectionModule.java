package com.mcplugin.module;

import com.mcplugin.Main;
import com.mcplugin.server.EmergencyEntitiesKill;
import com.mcplugin.server.PacketGuard;
import com.mcplugin.server.RedstoneGuard;
import com.mcplugin.server.ServerOverloadWarning;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль защиты сервера — RedstoneGuard, PacketGuard.
 * Неessential — защита важна, но без неё плагин работает.
 * Каждый компонент инициализируется в защищённом try-catch.
 */
public class ProtectionModule extends PluginModule {

    public ProtectionModule() {
        super("Protection", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        Main main = (Main) plugin;

        // Каждый guard инициализируем с защитой
        initRedstoneGuard(main);
        initPacketGuard(main);

        plugin.getLogger().info("[ProtectionModule] ✓ Server protection initialized.");
    }

    private void initRedstoneGuard(Main main) {
        try {
            RedstoneGuard.init(main);
            main.getLogger().info("[ProtectionModule]   \u2713 RedstoneGuard");
        } catch (Throwable t) {
            main.getLogger().warning("[ProtectionModule]   \u2717 RedstoneGuard failed: " + t.getMessage());
        }
    }

    private void initPacketGuard(Main main) {
        try {
            PacketGuard.init(main);
            main.getLogger().info("[ProtectionModule]   \u2713 PacketGuard");
        } catch (Throwable t) {
            main.getLogger().warning("[ProtectionModule]   \u2717 PacketGuard failed: " + t.getMessage());
        }
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        // Nothing to clean up
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        try {
            RedstoneGuard.reload();
        } catch (Throwable t) {
            plugin.getLogger().warning("[ProtectionModule] RedstoneGuard reload error: " + t.getMessage());
        }
        try {
            EmergencyEntitiesKill.reload();
        } catch (Throwable t) {
            plugin.getLogger().warning("[ProtectionModule] EmergencyEntitiesKill reload error: " + t.getMessage());
        }
        try {
            ServerOverloadWarning.reload();
        } catch (Throwable t) {
            plugin.getLogger().warning("[ProtectionModule] ServerOverloadWarning reload error: " + t.getMessage());
        }
    }
}
