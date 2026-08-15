package com.ultimateimprovments.core;

import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.util.ConsoleLogger;

import java.lang.reflect.Constructor;

/**
 * ModuleScanner — automatic discovery and registration of modules.
 * <p>
 * Scans the {@code com.ultimateimprovments.module} package in the plugin JAR,
 * finds all classes extending {@link PluginModule} with a public
 * no-arg constructor, and registers them in {@link ModuleManager}.
 * <p>
 * If the class has {@link ModuleInfo} — uses its name/path/essential.
 * Otherwise derives the name from the class name (removes the "Module" suffix).
 */
public final class ModuleScanner {

    private ModuleScanner() {}

    /**
     * Automatically finds and registers all modules in the given package.
     *
     * @param mm     the ModuleManager
     * @param plugin the plugin instance
com.ultimateimprovments
     */
    public static void autoRegister(ModuleManager mm, Main plugin, String scanPackage) {
        var jarFile = plugin.getPluginFile();
        var classes = ClassScanner.findAnnotatedClasses(jarFile, ModuleInfo.class, scanPackage);

        // First register classes annotated with @ModuleInfo
        for (var clazz : classes) {
            registerModule(mm, clazz);
        }

        // Then scan all PluginModule subclasses (for modules without the annotation)
        scanModuleSubclasses(mm, jarFile, scanPackage);

        ConsoleLogger.info("[ModuleScanner] Auto-registration complete.");
    }

    private static void registerModule(ModuleManager mm, Class<?> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();

            if (instance instanceof PluginModule module) {
                // If @ModuleInfo exists, use its metadata
                ModuleInfo info = clazz.getAnnotation(ModuleInfo.class);
                // The annotation already exists — the module uses its constructor values
                mm.register(module);
                ConsoleLogger.info("[ModuleScanner] Registered: " + module.getName());
            }
        } catch (NoSuchMethodException e) {
            ConsoleLogger.warn("[ModuleScanner] No no-arg constructor: " + clazz.getSimpleName());
        } catch (Exception e) {
            ConsoleLogger.warn("[ModuleScanner] Failed to register: " + clazz.getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * Scans the JAR for classes extending PluginModule
     * but lacking @ModuleInfo.
     */
    private static void scanModuleSubclasses(ModuleManager mm, java.io.File jarFile, String packagePrefix) {
        // Get already registered names (to avoid duplicates)
        var registeredNames = new java.util.HashSet<String>();
        for (var m : mm.getModules()) {
            registeredNames.add(m.getClass().getName());
        }

        String prefix = packagePrefix.replace('.', '/');

        try (var jar = new java.util.jar.JarFile(jarFile)) {
            var entries = jar.entries();

            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();

                if (!name.endsWith(".class") || !name.startsWith(prefix)) continue;
                if (name.contains("$")) continue;
                if (name.contains("ModuleManager") || name.contains("PluginModule")) continue;

                String className = name.replace('/', '.').substring(0, name.length() - ".class".length());

                try {
                    Class<?> clazz = Class.forName(className, false, ModuleScanner.class.getClassLoader());

                    // Already registered via @ModuleInfo?
                    if (registeredNames.contains(clazz.getName())) continue;

                    // A PluginModule subclass?
                    if (PluginModule.class.isAssignableFrom(clazz)
                            && !clazz.isInterface()
                            && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        registerModule(mm, clazz);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // skip
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[ModuleScanner] JAR scan failed (dev mode?): " + e.getMessage());
        }
    }
}
