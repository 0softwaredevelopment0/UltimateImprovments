package com.ultimateimprovments.datapack;

import com.ultimateimprovments.util.ConsoleLogger;
import io.papermc.paper.datapack.Datapack;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * DatapackInstaller — runtime status reporter for the bundled UI-Datapack.
 * <p>
 * The datapack itself is registered <b>before the worlds load</b> by
 * {@link UIDatapackBootstrap} through Paper's {@code DATAPACK_DISCOVERY} lifecycle
 * event (extracted fresh from the addon JAR on every server start). This class no
 * longer copies the pack into {@code world/datapacks} and no longer needs a
 * {@code /datapack enable} + reload/restart dance: it only verifies and, if needed,
 * enables the already-discovered pack.
 */
public class DatapackInstaller {

    private static DatapackInstaller instance;

    public static void init(JavaPlugin plugin) {
        instance = new DatapackInstaller();
    }

    public static DatapackInstaller getInstance() {
        return instance;
    }

    /**
     * Logs the datapack state and enables the discovered pack if it was disabled.
     * Never triggers a server restart/reload — the pack is loaded before the worlds.
     */
    public void reportStatus(JavaPlugin plugin) {
        if (!DatapackModules.isMasterEnabled()) {
            ConsoleLogger.info("[Datapack] Master toggle OFF (datapack.enabled: false) — "
                    + "the bundled datapack was not registered.");
            return;
        }

        ConsoleLogger.info("[Datapack] Bundled pack is provided by the bootstrapper "
                + "(Paper DATAPACK_DISCOVERY, before worlds). Parts: " + DatapackModules.describe());

        // Migration: old versions copied the pack into world/datapacks. Remove that
        // copy once the worlds are available, otherwise the same pack id would load twice.
        plugin.getServer().getScheduler().runTaskLater(plugin, this::removeLegacyWorldCopy, 40L);

        try {
            Datapack pack = findPack();
            if (pack == null) {
                ConsoleLogger.warn("[Datapack] UI-Datapack was not discovered at startup. "
                        + "Check that 'bootstrapper' is set in paper-plugin.yml and datapack.enabled: true; "
                        + "a server restart is required after changing these.");
                return;
            }
            if (!pack.isEnabled()) {
                ConsoleLogger.warn("[Datapack] UI-Datapack is discovered but disabled — enabling it now "
                        + "(this triggers a data reload, but no server restart).");
                pack.setEnabled(true);
            } else {
                ConsoleLogger.success("[Datapack] UI-Datapack is enabled (loaded before the worlds).");
            }
        } catch (Throwable t) {
            ConsoleLogger.warn("[Datapack] Could not query the datapack manager: " + t.getMessage());
        }
    }

    /**
     * Deletes the legacy {@code world/datapacks/UI-Datapack} copy installed by older
     * versions (the pack now comes from the plugin/bootstrapper). Without this the same
     * content would be loaded twice (world pack + plugin pack).
     */
    private void removeLegacyWorldCopy() {
        try {
            if (Bukkit.getWorlds().isEmpty()) return;
            World first = Bukkit.getWorlds().get(0);
            File legacy = new File(new File(first.getWorldFolder(), "datapacks"), "UI-Datapack");
            if (legacy.isDirectory()) {
                ConsoleLogger.warn("[Datapack] Removing legacy world copy "
                        + legacy.getAbsolutePath() + " — the pack is now provided by the plugin "
                        + "(prevents a duplicate pack).");
                deleteRecursively(legacy.toPath());
            }
        } catch (Throwable t) {
            ConsoleLogger.warn("[Datapack] Legacy world-copy cleanup failed: " + t.getMessage());
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    /** Finds the discovered UI-Datapack by name (Paper combines the id with the plugin). */
    private static Datapack findPack() {
        for (Datapack p : Bukkit.getDatapackManager().getPacks()) {
            String name = p.getName();
            if (name != null && name.contains("UI-Datapack")) {
                return p;
            }
        }
        return null;
    }
}
