package com.ultimateimprovments.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * UltimateDirs — single shared data folder for the whole UltimateImprovments family.
 * <p>
 * Historically every {@code UI-*} plugin owned its own {@code plugins/UI-<Name>/}
 * folder, but only UI-Core actually used it — the addons kept all their data in the
 * core folder via {@code Main.getInstance()}. Now the layout is explicit:
 * <pre>
 * plugins/UltimateImprovments/           ← one folder for everything
 * ├── database.db (+ -wal/-shm)          ← ONE shared SQLite database for all addons
 * ├── configs/                           ← one TOML file per addon + the core
 * │   ├── UI-Core.toml
 * │   ├── UI-MBS.toml
 * │   └── ...
 * ├── deaths.log, server-icon.png ...    ← addon-owned files, same folder as before
 * └── logs/
 * </pre>
 * Migration is idempotent: on first start after the rename, {@code database.db},
 * {@code logs/} and known loose files are moved out of the old
 * {@code plugins/UI-Core/} folder, which is then removed if empty.
 */
public final class UltimateDirs {

    /** Shared folder name directly under {@code plugins/}. */
    public static final String DIR_NAME = "UltimateImprovments";

    /** Sub-folder holding per-addon TOML configs. */
    public static final String CONFIGS = "configs";

    /** Name of the shared SQLite database file. */
    public static final String DB_FILE = "database.db";

    /** Loose files migrated from the legacy UI-Core folder on first start. */
    private static final String[] MIGRATE_FILES = {
            "deaths.log", "server-icon.png", "op-whitelist.json", "structure-chunks.json",
            "structure_integrity.dat", "config_backup.toml"
    };

    private UltimateDirs() {}

    /** {@code plugins/UltimateImprovments} */
    public static File base() {
        return new File(baseParent(), DIR_NAME);
    }

    /** {@code plugins/} — the parent of the shared folder. */
    public static File baseParent() {
        return new File("plugins");
    }

    /** {@code plugins/UltimateImprovments/<name>} — used by every family member. */
    public static File file(String name) {
        File base = base();
        if (!base.exists() && !base.mkdirs() && !base.isDirectory()) {
            throw new IllegalStateException("Cannot create " + base);
        }
        return new File(base, name);
    }

    /** {@code plugins/UltimateImprovments/<sub>/<name>} (sub-folder auto-created). */
    public static File fileIn(String sub, String name) {
        File dir = file(sub);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Cannot create " + dir);
        }
        return new File(dir, name);
    }

    /** {@code plugins/UltimateImprovments/configs/UI-<Name>.toml} */
    public static File addonConfigToml(String addonName) {
        return fileIn(CONFIGS, addonName + ".toml");
    }

    /** {@code plugins/UltimateImprovments/configs} */
    public static File configsDir() {
        return file(CONFIGS);
    }

    /** {@code plugins/UltimateImprovments/database.db} */
    public static File databaseFile() {
        return file(DB_FILE);
    }

    /**
     * One-time migration from the legacy per-plugin folders into the shared folder.
     * Called once by {@link Main#getDataFolder()} bootstrap.
     */
    public static synchronized void migrateLegacyFolders() {
        File base = base();
        if (!base.isDirectory()) return;

        File oldCore = new File(baseParent(), "UI-Core");
        boolean moved = false;

        if (oldCore.isDirectory()) {
            // Database (+ WAL side files) — the most valuable data.
            for (String name : new String[]{DB_FILE, DB_FILE + "-wal", DB_FILE + "-shm"}) {
                moved |= moveIfExists(new File(oldCore, name), new File(base, name));
            }
            // Known loose files.
            for (String name : MIGRATE_FILES) {
                moved |= moveIfExists(new File(oldCore, name), new File(base, name));
            }
            // Log directory.
            File oldLogs = new File(oldCore, "logs");
            if (oldLogs.isDirectory()) {
                moved |= moveTree(oldLogs, new File(base, "logs"));
            }
        }

        // Other UI-* folders: move their known files too, then remove empties.
        for (String name : MIGRATE_FILES) {
            File addonDir = new File(baseParent(), "UI-" + suffixCandidates(name));
            if (addonDir.isDirectory()) {
                moved |= moveIfExists(new File(addonDir, name), new File(base, name));
            }
        }

        if (moved) {
            com.ultimateimprovments.util.ConsoleLogger.info(
                    "[Dirs] Migrated data from legacy plugin folders into " + base.getName());
        }

        // Remove the old folder if nothing valuable is left in it.
        if (oldCore.isDirectory()) {
            String[] left = oldCore.list();
            if (left != null && left.length == 0 && oldCore.delete()) {
                com.ultimateimprovments.util.ConsoleLogger.info("[Dirs] Removed empty legacy folder UI-Core");
            }
        }
    }

    /** Mapping of loose file names to the addon folder that may still hold them. */
    private static String suffixCandidates(String fileName) {
        return switch (fileName) {
            case "structure-chunks.json" -> "MBS";
            case "deaths.log" -> "Other";
            default -> "Core";
        };
    }

    private static boolean moveIfExists(File from, File to) {
        if (!from.isFile()) return false;
        try {
            if (to.exists()) {
                // Keep both: the newer file wins the canonical name.
                File backup = new File(to.getParentFile(), to.getName() + ".migrated");
                Files.move(from.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(from.toPath(), to.toPath());
            }
            return true;
        } catch (Exception e) {
            com.ultimateimprovments.util.ConsoleLogger.warn(
                    "[Dirs] Could not move " + from.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean moveTree(File from, File to) {
        try {
            if (to.exists()) {
                // merge: copy children, delete source
                try (var walk = Files.walk(from.toPath())) {
                    for (Path src : walk.toList()) {
                        Path dst = to.toPath().resolve(from.toPath().relativize(src));
                        if (Files.isDirectory(src)) {
                            Files.createDirectories(dst);
                        } else {
                            Files.createDirectories(dst.getParent());
                            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                deleteRecursively(from.toPath());
            } else {
                Files.move(from.toPath(), to.toPath());
            }
            return true;
        } catch (Exception e) {
            com.ultimateimprovments.util.ConsoleLogger.warn(
                    "[Dirs] Could not move logs: " + e.getMessage());
            return false;
        }
    }

    /** Recursively deletes a directory tree (best-effort, used for empty leftovers). */
    public static void deleteRecursively(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    /** Lists addon names that already have a TOML config on disk. */
    public static List<String> existingAddonConfigs() {
        List<String> out = new ArrayList<>();
        File dir = configsDir();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".toml"));
        if (files != null) {
            for (File f : files) out.add(f.getName().substring(0, f.getName().length() - 5));
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
