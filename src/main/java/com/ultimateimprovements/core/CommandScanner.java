package com.ultimateimprovements.core;

import com.ultimateimprovements.command.SubCommand;
import com.ultimateimprovements.command.SubCommandRegistry;
import com.ultimateimprovements.command.subcommands.LegacySubCommandAdapter;
import com.ultimateimprovements.util.ConsoleLogger;

import java.lang.reflect.Constructor;

/**
 * CommandScanner — автоматическое обнаружение и регистрация субкоманд /mp.
 * <p>
 * Сканирует пакет {@code com.ultimateimprovements.command.subcommands} в JAR плагина,
 * находит все классы, реализующие {@link SubCommand} с публичным
 * конструктором без параметров, и регистрирует их в {@link SubCommandRegistry}.
 * <p>
 * Если на классе есть {@link SubCommandInfo} — использует name/aliases оттуда.
 * Иначе выводит name из {@link SubCommand#getName()}.
 */
public final class CommandScanner {

    private CommandScanner() {}

    /**
     * Автоматически находит и регистрирует все субкоманды.
     *
     * @param registry    SubCommandRegistry
     * @param plugin      экземпляр плагина
     * @param scanPackage пакет для сканирования (например "com.ultimateimprovements.command.subcommands")
     */
    public static void autoRegister(SubCommandRegistry registry, Main plugin, String scanPackage) {
        var jarFile = plugin.getPluginFile();
        var classes = ClassScanner.findAnnotatedClasses(jarFile, SubCommandInfo.class, scanPackage);

        int registered = 0;

        // Регистрируем помеченные @SubCommandInfo
        for (var clazz : classes) {
            if (registerSubCommand(registry, clazz)) {
                registered++;
            }
        }

        // Затем сканируем все реализации SubCommand (для классов без аннотации)
        registered += scanSubCommandImplementations(registry, jarFile, scanPackage);

        ConsoleLogger.info("[CommandScanner] Auto-registered " + registered + " subcommands.");
    }

    private static boolean registerSubCommand(SubCommandRegistry registry, Class<?> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();

            if (instance instanceof SubCommand cmd) {
                // Если есть @SubCommandInfo с name, оборачиваем в адаптер с алиасами
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
            // Нет конструктора без параметров — пропускаем
        } catch (Exception e) {
            ConsoleLogger.warn("[CommandScanner] Failed: " + clazz.getSimpleName() + " - " + e.getMessage());
        }
        return false;
    }

    /**
     * Сканирует JAR на предмет классов, реализующих SubCommand,
     * но не имеющих @SubCommandInfo.
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
                // Пропускаем служебные классы
                if (name.contains("LegacySubCommandAdapter") || name.contains("HelpSubCommand")) continue;

                String className = name.replace('/', '.').substring(0, name.length() - ".class".length());

                try {
                    Class<?> clazz = Class.forName(className, false, CommandScanner.class.getClassLoader());

                    // Уже зарегистрирован через @SubCommandInfo?
                    if (clazz.isAnnotationPresent(SubCommandInfo.class)) continue;

                    // Реализует SubCommand?
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
