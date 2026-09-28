package com.ultimateimprovments.core;

import com.ultimateimprovments.mechanics.protection.ProtectionModule;
import com.ultimateimprovments.mechanics.protection.VoidProtectionListener;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.SimpleModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ProtectionModules — registration of the protection addon's modules
 * (block protection + void protection). Moved out of ui-other.
 */
public final class ProtectionModules {

    private ProtectionModules() {}

    /** Registers the Protection and VoidProtection modules. */
    public static void registerAll(ModuleManager mm) {
        mm.register(new ProtectionModule());

        mm.register(new SimpleModule("VoidProtection", "infrastructure/listeners", false) {
            @Override
            protected void onInit(JavaPlugin plugin) throws Exception {
                Main main = (Main) plugin;
                main.getServer().getPluginManager().registerEvents(new VoidProtectionListener(), main);
            }
        });
    }
}
