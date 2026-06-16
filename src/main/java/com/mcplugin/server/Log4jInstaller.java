package com.mcplugin.server;

import com.mcplugin.Main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 📦 Log4jInstaller — автоматическая установка log4j2.xml на сервер.
 * <p>
 * При загрузке плагина копирует {@code log4j2.xml} из ресурсов плагина
 * в корневую папку сервера (рядом с server.jar), если его там нет.
 * <p>
 * Также создаёт {@code start.bat} с флагом {@code -Dlog4j.configurationFile=log4j2.xml},
 * который подключает кастомный конфиг для фильтрации спам-сообщений
 * (например, высотных карт / heightmap).
 * <p>
 * Если {@code start.bat} уже существует, но не содержит нужный флаг —
 * в консоль выводится предупреждение.
 */
public class Log4jInstaller {

    private static Log4jInstaller instance;

    public static void init(Main plugin) {
        instance = new Log4jInstaller();
        instance.install(plugin);
    }

    public static Log4jInstaller getInstance() {
        return instance;
    }

    // =========================
    // INSTALL
    // =========================
    public void install(Main plugin) {

        try {
            // Корень сервера — туда, где лежат server.jar, start.bat и т.д.
            // Плагин: <root>/plugins/MC-Plugin/ → поднимаемся на 2 уровня
            File serverRoot = plugin.getDataFolder()
                    .getParentFile()   // plugins/
                    .getParentFile();  // <root>/

            if (serverRoot == null || !serverRoot.exists()) {
                // Fallback: рабочая директория
                serverRoot = new File(System.getProperty("user.dir"));
                plugin.getLogger().info("[Log4jInstaller] Using user.dir as server root: " + serverRoot);
            }

            // =========================
            // 1. Копируем log4j2.xml
            // =========================
            File log4jFile = new File(serverRoot, "log4j2.xml");

            if (!log4jFile.exists()) {
                copyLog4jXml(plugin, log4jFile);
                plugin.getLogger().info("[Log4jInstaller] ✓ log4j2.xml скопирован в " + log4jFile.getAbsolutePath());
            } else {
                plugin.getLogger().fine("[Log4jInstaller] log4j2.xml уже существует — пропускаем копирование.");
            }

            // =========================
            // 2. Создаём / проверяем start.bat
            // =========================
            File startBat = new File(serverRoot, "start.bat");

            if (!startBat.exists()) {
                createStartBat(plugin, startBat);
                plugin.getLogger().info("[Log4jInstaller] ✓ start.bat создан: " + startBat.getAbsolutePath());
            } else {
                // Проверяем, есть ли нужный флаг
                checkStartBat(plugin, startBat);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[Log4jInstaller] Ошибка: " + e.getMessage());
        }
    }

    // =========================
    // КОПИРОВАНИЕ log4j2.xml
    // =========================
    private void copyLog4jXml(Main plugin, File target) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("log4j2.xml")) {
            if (in == null) {
                plugin.getLogger().warning("[Log4jInstaller] log4j2.xml не найден в ресурсах плагина!");
                return;
            }
            try (FileOutputStream out = new FileOutputStream(target)) {
                in.transferTo(out);
            }
        }
    }

    // =========================
    // СОЗДАНИЕ start.bat
    // =========================
    private void createStartBat(Main plugin, File target) throws IOException {
        String content = """

@echo off
title Minecraft Server 1.21.4

REM ═══════════════════════════════════════════════════════════
REM  Запуск Paper-сервера с кастомным log4j2.xml
REM  Флаг -Dlog4j.configurationFile=log4j2.xml подключает
REM  конфиг, который фильтрует спам (например, heightmap).
REM ═══════════════════════════════════════════════════════════

java -Xmx4G -Dlog4j.configurationFile=log4j2.xml -jar server.jar nogui

pause
""".trim();

        Files.writeString(target.toPath(), content);
    }

    // =========================
    // ПРОВЕРКА start.bat
    // =========================
    private void checkStartBat(Main plugin, File bat) {
        try {
            String content = Files.readString(bat.toPath());

            if (!content.contains("log4j.configurationFile")) {
                plugin.getLogger().warning(
                        "[Log4jInstaller] ⚠ start.bat уже существует, но в нём нет флага -Dlog4j.configurationFile=log4j2.xml.\n" +
                        "    Добавьте его вручную, чтобы включить фильтрацию спама:\n" +
                        "    java -Xmx4G -Dlog4j.configurationFile=log4j2.xml -jar server.jar nogui"
                );
            } else {
                plugin.getLogger().info("[Log4jInstaller] ✓ start.bat уже содержит флаг log4j.configurationFile.");
            }
        } catch (IOException e) {
            plugin.getLogger().fine("[Log4jInstaller] Не удалось прочитать start.bat: " + e.getMessage());
        }
    }
}
