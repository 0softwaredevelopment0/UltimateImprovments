package com.ultimateimprovments.module;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base for most modules: {@code onDisable} is a no-op. Modules with task
 * cleanup/shutdown override {@code onDisable} and inherit {@link PluginModule}
 * directly.
 * <p>
 * Public (was a private nested class in ui-other's {@code SimpleModules}) so every
 * addon's module registrar can use it.
 */
public abstract class SimpleModule extends PluginModule {

    protected SimpleModule(String name, String path, boolean essential) {
        super(name, path, essential);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {}
}
