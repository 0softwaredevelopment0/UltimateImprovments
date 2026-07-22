package com.ultimateimprovments.core;

import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.util.ConsoleLogger;

import java.lang.reflect.Constructor;

/**
 * ModuleScanner — автоматическое обнаружение и регистрация модулей.
 * <p>
 * Сканирует пакет {@code com.ultimateimprovments.module} в JAR плагина,
 * находит все классы, наследующие {@link PluginModule} с публичным
 * конструктором без параметров, и регистрирует их в {@link ModuleManager}.
 * <p>
 * Если на классе есть {@link ModuleInfo} — использует name/path/essential оттуда.
 * Иначе выводит name из имени класса (убирает суффикс "Module").
 */
public final class ModuleScanner {

    private ModuleScanner() {}

    /**
     * Автоматически находит и регистрирует все модули в указанном пакете.
     *
     * @param mm     ModuleManager
     * @param plugin экземпляр плагина
com.ultimateimprovments
     */
    public static void autoRegister(ModuleManager mm, Main plugin, String scanPackage) {
        var jarFile = plugin.getPluginFile();
        var classes = ClassScanner.findAnnotatedClasses(jarFile, ModuleInfo.class, scanPackage);

        // Сначала регистрируем помеченные @ModuleInfo
        for (var clazz : classes) {
            registerModule(mm, clazz);
        }

        // Затем сканируем все наследники PluginModule (для модулей без аннотации)
        scanModuleSubclasses(mm, jarFile, scanPackage);

        ConsoleLogger.info("[ModuleScanner] Auto-registration complete.");
    }

    private static void registerModule(ModuleManager mm, Class<?> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();

            if (instance instanceof PluginModule module) {
                // Если есть @ModuleInfo, используем её метаданные
                ModuleInfo info = clazz.getAnnotation(ModuleInfo.class);
                // Аннотация уже есть — модуль будет использовать свои конструкторные значения
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
     * Сканирует JAR на предмет классов, наследующих PluginModule,
     * но не имеющих @ModuleInfo.
     */
    private static void scanModuleSubclasses(ModuleManager mm, java.io.File jarFile, String packagePrefix) {
        // Получаем уже зарегистрированные имена (чтобы не дублировать)
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

                    // Уже зарегистрирован через @ModuleInfo?
                    if (registeredNames.contains(clazz.getName())) continue;

                    // Наследник PluginModule?
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
