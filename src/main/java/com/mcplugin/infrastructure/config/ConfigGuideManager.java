package com.mcplugin.infrastructure.config;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.FileLogger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

/**
 * 📖 ConfigGuideManager — управляет файлом plugin-guide.txt.
 * <p>
 * plugin-guide.txt — это подробное руководство пользователя, которое объясняет
 * что такое MC-Plugin, как им пользоваться и как настраивать config.yml + messages.yml.
 * <p>
 * Если plugin-guide.txt не совпадает с эталоном в JAR — он перезаписывается (override).
 * Это гарантирует, что гайд всегда актуален версии плагина.
 */
public class ConfigGuideManager {

    private static final String GUIDE_FILE = "plugin-guide.txt";
    private static final String HASH_META_FILE = "plugin-guide.hash";

    private ConfigGuideManager() {}

    /**
     * Инициализирует plugin-guide.txt: сохраняет из ресурсов (если нет),
     * проверяет хеш, перезаписывает при несовпадении.
     */
    public static void init(Main plugin) {
        File guideFile = new File(plugin.getDataFolder(), GUIDE_FILE);

        // При первом запуске — просто сохраняем
        if (!guideFile.exists()) {
            saveGuideFromResources(plugin);
            plugin.getLogger().info("[Guide] Created: " + GUIDE_FILE);
            saveHash(plugin, computeHash(guideFile));
            return;
        }

        // Сравниваем хеш текущего гайда с эталоном в JAR
        String currentHash = computeHash(guideFile);
        String savedHash = loadHash(plugin);

        if (currentHash == null || !currentHash.equals(savedHash)) {
            plugin.getLogger().info("[Guide] Guide file changed or hash mismatch — overriding with plugin version.");
            saveGuideFromResources(plugin);
            saveHash(plugin, computeHash(guideFile));
            plugin.getLogger().info("[Guide] Override complete.");
        } else {
            plugin.getLogger().info("[Guide] Guide is up-to-date.");
        }
    }

    /**
     * Сохраняет plugin-guide.txt из ресурсов JAR в папку плагина.
     */
    private static void saveGuideFromResources(Main plugin) {
        try (InputStream in = plugin.getResource(GUIDE_FILE)) {
            if (in == null) {
                plugin.getLogger().warning("[Guide] Resource not found: " + GUIDE_FILE);
                return;
            }
            File target = new File(plugin.getDataFolder(), GUIDE_FILE);
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Guide] Failed to save " + GUIDE_FILE, e);
        }
    }

    /**
     * Вычисляет SHA-256 хеш файла.
     */
    private static String computeHash(File file) {
        if (file == null || !file.exists()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] content = Files.readAllBytes(file.toPath());
            byte[] hash = md.digest(content);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Сохраняет хеш гайда в файл-метку.
     */
    private static void saveHash(Main plugin, String hash) {
        if (hash == null) return;
        try {
            File hashFile = new File(plugin.getDataFolder(), HASH_META_FILE);
            Files.writeString(hashFile.toPath(), hash);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[Guide] Failed to save hash", e);
        }
    }

    /**
     * Загружает сохранённый хеш из файла-метки.
     */
    private static String loadHash(Main plugin) {
        try {
            File hashFile = new File(plugin.getDataFolder(), HASH_META_FILE);
            if (!hashFile.exists()) return null;
            return Files.readString(hashFile.toPath()).trim();
        } catch (Exception e) {
            return null;
        }
    }
}
