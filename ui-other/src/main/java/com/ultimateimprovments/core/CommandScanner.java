package com.ultimateimprovments.core;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.command.SubCommandRegistry;
import com.ultimateimprovments.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovments.util.ConsoleLogger;

import java.lang.reflect.Constructor;

/**
 * CommandScanner — automatic discovery and registration of /ultimateimprovments subcommands.
 * <p>
 * Scans the {@code com.ultimateimprovments.command.subcommands} package in the plugin JAR,
 * finds all classes implementing {@link SubCommand} with a public no-arg
 * constructor, and registers them in {@link SubCommandRegistry}.
 * <p>
 * If the class has {@link SubCommandInfo} — uses its name/aliases.
 * Otherwise derives the name from {@link SubCommand#getName()}.
 */
public final class CommandScanner {

    private CommandScanner() {}

    /**
     * Automatically finds and registers all subcommands.
     *
     * @param registry    the SubCommandRegistry
     * @param plugin      the plugin instance
     * @param scanPackage the package to scan (e.g. "com.ultimateimprovments.command.subcommands")
     */
    public static void autoRegister(SubCommandRegistry registry, Main plugin, String scanPackage) {
        var jarFile = plugin.getPluginFile();
        var classes = ClassScanner.findAnnotatedClasses(jarFile, SubCommandInfo.class, scanPackage);

        int registered = 0;

        // Register classes annotated with @SubCommandInfo
        for (var clazz : classes) {
            if (registerSubCommand(registry, clazz)) {
                registered++;
            }
        }

        // Then scan all SubCommand implementations (for classes without the annotation)
        registered += scanSubCommandImplementations(registry, jarFile, scanPackage);

        ConsoleLogger.info("[CommandScanner] Auto-registered " + registered + " subcommands.");
    }

    private static boolean registerSubCommand(SubCommandRegistry registry, Class<?> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();

            if (instance instanceof SubCommand cmd) {
                // If @SubCommandInfo has a name, wrap in an adapter with aliases
                SubCommandInfo info = clazz.getAnnotation(SubCommandInfo.class);
                if (info != null && !info.name().isEmpty()) {
                    String customName = info.name();
                    var aliases = java.util.List.of(info.aliases());
                    registry.register(LegacySubCommandAdapter.of(customName,
                            (s, a) -> cmd.execute(s, a),
                            (s, a) -> cmd.tabComplete(s, a),
                            aliases));
                } else {
                    registry.register(cmd);
                }
                return true;
            }
        } catch (NoSuchMethodException e) {
            // No no-arg constructor — skip
        } catch (Exception e) {
            ConsoleLogger.warn("[CommandScanner] Failed: " + clazz.getSimpleName() + " - " + e.getMessage());
        }
        return false;
    }

    /**
     * Scans the JAR for classes implementing SubCommand
     * but lacking @SubCommandInfo.
     */
    private static int scanSubCommandImplementations(
            SubCommandRegistry registry, java.io.File jarFile, String packagePrefix) {

        int count = 0;
        String prefix = packagePrefix.replace('.', '/');

        try (var jar = new java.util.jar.JarFile(jarFile)) {
            var entries = jar.entries();

            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();

                if (!name.endsWith(".class") || !name.startsWith(prefix)) continue;
                if (name.contains("$")) continue;
                // Skip utility classes
                if (name.contains("LegacySubCommandAdapter") || name.contains("HelpSubCommand")) continue;

                String className = name.replace('/', '.').substring(0, name.length() - ".class".length());

                try {
                    Class<?> clazz = Class.forName(className, false, CommandScanner.class.getClassLoader());

                    // Already registered via @SubCommandInfo?
                    if (clazz.isAnnotationPresent(SubCommandInfo.class)) continue;

                    // Implements SubCommand?
                    if (SubCommand.class.isAssignableFrom(clazz)
                            && !clazz.isInterface()
                            && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        if (registerSubCommand(registry, clazz)) {
                            count++;
                        }
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // skip
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[CommandScanner] JAR scan failed (dev mode?): " + e.getMessage());
        }

        return count;
    }
}
