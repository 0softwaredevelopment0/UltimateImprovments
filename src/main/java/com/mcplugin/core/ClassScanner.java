package com.mcplugin.core;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * ClassScanner — сканирует JAR плагина и находит классы с указанной аннотацией.
 * <p>
 * Используется для авто-обнаружения:
 * <ul>
 *   <li>{@link SubCommandInfo} — субкоманды {@code /mp}</li>
 *   <li>{@link ModuleInfo} — модули плагина</li>
 * </ul>
 * <p>
 * Не требует внешних библиотек (сканирует JAR-entries через {@link JarFile}).
 */
public final class ClassScanner {

    private ClassScanner() {}

    /**
     * Сканирует JAR-файл плагина и находит все классы с указанной аннотацией.
     *
     * @param jarFile       JAR-файл плагина (из {@link Main#getPluginFile()})
     * @param annotation    класс аннотации для поиска
     * @param packagePrefix фильтр пакета (например "com.mcplugin")
     * @param <A>           тип аннотации
     * @return список классов, имеющих аннотацию
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> List<Class<?>> findAnnotatedClasses(
            File jarFile, Class<A> annotation, String packagePrefix) {

        List<Class<?>> result = new ArrayList<>();

        if (jarFile == null || !jarFile.exists()) {
            return result;
        }

        String prefix = packagePrefix.replace('.', '/');

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // Только .class файлы в нужном пакете
                if (!name.endsWith(".class") || !name.startsWith(prefix)) {
                    continue;
                }

                // Пропускаем внутренние классы
                if (name.contains("$")) continue;

                // com/mcplugin/command/subcommands/Example.class → com.mcplugin.command.subcommands.Example
                String className = name.replace('/', '.')
                        .substring(0, name.length() - ".class".length());

                try {
                    Class<?> clazz = Class.forName(className, false, ClassScanner.class.getClassLoader());

                    if (clazz.isAnnotationPresent(annotation)) {
                        // Проверяем, что это не абстрактный класс и не интерфейс
                        if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                            result.add(clazz);
                        }
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // Класс может зависеть от Bukkit API — пропускаем
                }
            }
        } catch (Exception e) {
            // В dev-среде (IDE) JAR может не существовать — сканируем classpath
            return findAnnotatedClassesClasspath(annotation, packagePrefix);
        }

        return result;
    }

    /**
     * Fallback: сканирует classpath для dev-среды без JAR.
     */
    private static <A extends Annotation> List<Class<?>> findAnnotatedClassesClasspath(
            Class<A> annotation, String packagePrefix) {

        List<Class<?>> result = new ArrayList<>();
        String packagePath = packagePrefix.replace('.', '/');

        ClassLoader cl = ClassScanner.class.getClassLoader();
        if (!(cl instanceof URLClassLoader ucl)) return result;

        for (URL url : ucl.getURLs()) {
            File file = new File(url.getFile());
            if (file.isDirectory()) {
                scanDirectory(file, file, packagePath, annotation, result);
            }
        }

        return result;
    }

    private static <A extends Annotation> void scanDirectory(
            File root, File dir, String packagePath,
            Class<A> annotation, List<Class<?>> result) {

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(root, file, packagePath, annotation, result);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                // Вычисляем полное имя класса из пути
                String relativePath = file.getAbsolutePath()
                        .substring(root.getAbsolutePath().length() + 1)
                        .replace(File.separatorChar, '.');
                relativePath = relativePath.substring(0, relativePath.length() - ".class".length());

                if (!relativePath.startsWith(packagePath.replace('/', '.'))) continue;

                try {
                    Class<?> clazz = Class.forName(relativePath, false, ClassScanner.class.getClassLoader());
                    if (clazz.isAnnotationPresent(annotation)
                            && !clazz.isInterface()
                            && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        result.add(clazz);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // skip
                }
            }
        }
    }
}
