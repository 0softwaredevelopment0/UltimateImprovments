package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * /ui swapjar — applies the downloaded update: moves the {@code UI-*.jar}
 * files from {@code plugins/UltimateImprovments/update/} into {@code plugins/},
 * deleting the old JARs of the same artifacts first (a MOVE, not a copy).
 * <p>
 * Paper 26.3+ blocks runtime plugin registration, so the swap takes effect
 * only after a <b>server restart</b>. On Windows the running JARs are locked
 * by the JVM — a locked artifact is reported and left in {@code update/};
 * on Linux the swap succeeds (open file handles survive the delete).
 * Download step: {@code /ui update [tag]}.
 * <p>
 * Requires the permission: {@code ui.command.swapjar}.
 */
public final class SwapJarSubcommand {

    private static final String PERMISSION = "ui.command.swapjar";

    private SwapJarSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender, PERMISSION);
            return true;
        }

        File updateDir = new File(Main.getInstance().getDataFolder(), "update");
        File pluginsDir = Main.getInstance().getDataFolder().getParentFile();

        File[] jars = updateDir.listFiles((dir, name) -> name.startsWith("UI-") && name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>No downloaded updates in </white><yellow>"
                            + updateDir.getName() + "</yellow><white>. Run </white><yellow>/ui update</yellow>"
                            + "<white> first.</white>"));
            return true;
        }

        int moved = 0;
        List<String> failed = new ArrayList<>();

        for (File jar : jars) {
            String artifact = artifactOf(jar.getName());
            if (artifact == null) {
                failed.add(jar.getName() + " (unrecognized name)");
                continue;
            }
            try {
                // 1. Remove the old JARs of this artifact. A locked file
                //    (Windows: the JVM holds the JAR open) fails the whole
                //    artifact — the new JAR stays in update/ instead of
                //    duplicating the plugin in plugins/.
                boolean oldRemoved = true;
                File[] oldJars = pluginsDir.listFiles(
                        (dir, name) -> name.startsWith(artifact + "-") && name.endsWith(".jar"));
                if (oldJars != null) {
                    for (File old : oldJars) {
                        try {
                            Files.deleteIfExists(old.toPath());
                            ConsoleLogger.info("[SwapJar] Removed old " + old.getName());
                        } catch (IOException e) {
                            oldRemoved = false;
                            failed.add(artifact + " — old " + old.getName() + " is locked by the server");
                            break;
                        }
                    }
                }
                if (!oldRemoved) continue;

                // 2. Move the new JAR into plugins/ (keeps its own name)
                Files.move(jar.toPath(),
                        new File(pluginsDir, jar.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                moved++;
                ConsoleLogger.info("[SwapJar] Moved " + jar.getName() + " into plugins/");
            } catch (IOException e) {
                failed.add(jar.getName() + " (" + e.getMessage() + ")");
            }
        }

        sender.sendMessage(MessageUtil.parse(""));
        if (moved > 0) {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Moved </white><yellow>" + moved + "</yellow><white> JAR(s) into plugins/."));
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Restart the server to apply them</white>"
                            + "<gray> (Paper 26.3+ does not load JARs at runtime).</gray>"));
            ConsoleLogger.info("[SwapJar] Applied: " + moved + " JAR(s) moved into plugins/ — restart required");
        }
        if (!failed.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<red>❌</red> <white>" + failed.size() + " JAR(s) could not be moved:</white>"));
            for (String f : failed) {
                sender.sendMessage(MessageUtil.parse("<dark_gray> • </dark_gray><red>" + f + "</red>"));
            }
            sender.sendMessage(MessageUtil.parse(
                    "<gray>They remain in </gray><white>plugins/" + updateDir.getName()
                            + "/</white><gray> — apply them manually:</gray>"));
            sender.sendMessage(MessageUtil.parse(
                    "<gray>  1) Stop the server</gray><white> (running JARs are locked on Windows)</white><gray>;</gray>"));
            sender.sendMessage(MessageUtil.parse(
                    "<gray>  2) Delete the old </gray><white>UI-*.jar</white><gray> files in </gray><white>plugins/</white><gray>;</gray>"));
            sender.sendMessage(MessageUtil.parse(
                    "<gray>  3) Move the new JARs from </gray><white>plugins/" + updateDir.getName()
                            + "/</white><gray> into </gray><white>plugins/</white><gray>.</gray>"));
            ConsoleLogger.warn("[SwapJar] " + failed.size() + " JAR(s) left in update/ — manual step required");
        }
        if (moved == 0 && failed.isEmpty()) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Nothing was moved.</white>"));
        }
        sender.sendMessage(MessageUtil.parse(""));
        return true;
    }

    /**
     * Artifact prefix of a family JAR name:
     * {@code UI-Admin-1.8.3-alpha.8-all.jar} → {@code UI-Admin}
     * (the version carries no dashes, so the last dash separates it).
     */
    private static String artifactOf(String fileName) {
        String s = fileName;
        if (s.endsWith(".jar")) s = s.substring(0, s.length() - 4);
        if (s.endsWith("-all")) s = s.substring(0, s.length() - 4);
        int dash = s.lastIndexOf('-');
        if (dash <= 0) return null;
        return s.substring(0, dash);
    }
}
