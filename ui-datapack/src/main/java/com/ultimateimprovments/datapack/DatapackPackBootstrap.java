package com.ultimateimprovments.datapack;

import com.moandjiezana.toml.Toml;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DatapackPackBootstrap — prepares the bundled UI-Datapack on disk for the
 * {@code DATAPACK_DISCOVERY} event (runs very early, before UI-Core is loaded, so it
 * cannot use the family's {@code CompositeConfig}).
 * <p>
 * The addon ships the SAME pack in two language variants:
 * {@code datapacks/UI-Datapack-en/**} and {@code datapacks/UI-Datapack-ru/**} — the
 * content is identical, only the displayed names/descriptions are translated. The
 * variant is selected by {@code datapack.lang} in
 * {@code plugins/UltimateImprovments/configs/UI-Datapack.toml} ({"en"} or {"ru"};
 * {@code auto} follows {@code messages.lang} from {@code UI-Core.toml}).
 * <p>
 * It also reads {@code datapack.enabled} / {@code datapack.modules.*} from the same
 * TOML (via toml4j bundled in this addon), extracts the enabled subset of the selected
 * pack into {@code plugins/UltimateImprovments/datapack/UI-Datapack-<lang>}, and
 * returns that folder (or {@code null} when the master toggle is off).
 */
final class DatapackPackBootstrap {

    private DatapackPackBootstrap() {}

    /** Bundled resource folder prefix inside the JAR (the language suffix follows). */
    private static final String JAR_PREFIX = "datapacks/UI-Datapack-";

    /** Name prefix of the pack on disk (the language suffix follows). */
    static final String PACK_PREFIX = "UI-Datapack";

    private static final String EN = PackLanguage.EN;
    private static final String RU = PackLanguage.RU;

    /** Language used when nothing is configured and UI-Core has no preference yet. */
    private static final String DEFAULT_LANG = PackLanguage.DEFAULT_LANG;

    private static final String[] PARTS = {
            "enchantments", "advancements", "custom_recipes", "vanilla_recipes",
            "loot_tables", "worldgen", "dimension_limits"
    };

    /** Datapack path prefixes per part (relative to the pack's {@code data/} folder). */
    private static final Map<String, List<String>> PREFIXES = Map.ofEntries(
            Map.entry("enchantments", List.of("ui/enchantment/", "ui/tags/item/", "minecraft/tags/enchantment/")),
            Map.entry("advancements", List.of("ui/advancement/")),
            Map.entry("custom_recipes", List.of("ui/recipe/")),
            Map.entry("vanilla_recipes", List.of("minecraft/recipe/")),
            Map.entry("loot_tables", List.of("minecraft/loot_table/")),
            Map.entry("worldgen", List.of("minecraft/worldgen/", "minecraft/structure/")),
            Map.entry("dimension_limits", List.of("minecraft/dimension_type/"))
    );

    static Path prepare(ComponentLogger log) throws Exception {
        File shared = new File(new File("plugins"), "UltimateImprovments");
        File configFile = new File(new File(shared, "configs"), "UI-Datapack.toml");

        boolean enabled = true;
        String configuredLang = "auto";
        Map<String, Boolean> parts = new HashMap<>();
        for (String part : PARTS) parts.put(part, true);

        if (configFile.isFile()) {
            try {
                Toml toml = new Toml().read(configFile);
                enabled = !Boolean.FALSE.equals(toml.getBoolean("datapack.enabled", true));
                configuredLang = toml.getString("datapack.lang", "auto");
                Toml modules = toml.getTable("datapack.modules");
                if (modules != null) {
                    for (String part : PARTS) {
                        parts.put(part, !Boolean.FALSE.equals(modules.getBoolean(part, true)));
                    }
                }
            } catch (Exception e) {
                log.warn("Could not read " + configFile + " (" + e.getMessage()
                        + ") — using defaults (all parts enabled, language auto).");
            }
        } else {
            log.info("Config not found yet (" + configFile
                    + ") — using defaults (all parts enabled, language auto).");
        }

        if (!enabled) {
            log.info("datapack.enabled=false — the bundled datapack is not registered.");
            return null;
        }

        String lang = PackLanguage.resolve(configuredLang, shared, log);

        File source = locateSource(lang, log);
        if (source == null) {
            // The requested variant is not in this JAR (e.g. a stripped build) — the
            // other one is better than shipping no datapack at all.
            String fallback = EN.equals(lang) ? RU : EN;
            source = locateSource(fallback, log);
            if (source != null) {
                log.warn("Bundled datapack '" + PACK_PREFIX + "-" + lang + "' was not found — "
                        + "falling back to '" + PACK_PREFIX + "-" + fallback + "'.");
                lang = fallback;
            }
        }
        if (source == null) {
            throw new IOException("UI-Datapack resources not found — no JAR/dir contains '"
                    + JAR_PREFIX + "<lang>/pack.mcmeta'");
        }

        log.info("Datapack language: " + lang + " (datapack.lang: " + configuredLang + ")");

        String packName = PACK_PREFIX + "-" + lang;
        File packRoot = new File(shared, "datapack");
        deleteStalePacks(packRoot);
        File target = new File(packRoot, packName);
        if (!target.mkdirs() && !target.isDirectory()) {
            throw new IOException("Cannot create datapack folder: " + target.getAbsolutePath());
        }

        String jarPrefix = JAR_PREFIX + lang + "/";
        int copied = source.isDirectory()
                ? copyFromDirectory(source, target, parts, log, jarPrefix)
                : copyFromJar(source, target, parts, jarPrefix);
        if (copied == 0) {
            throw new IOException(packName + " extraction copied 0 files");
        }

        log.info("Extracted " + copied + " datapack file(s) from " + packName
                + " to " + target.getAbsolutePath());
        return target.toPath();
    }

    // =========================
    // SOURCE LOCATION
    // =========================

    private static File locateSource(String lang, ComponentLogger log) {
        List<File> candidates = new ArrayList<>();
        File self = codeSource(UIDatapackBootstrap.class);
        if (self != null) {
            candidates.add(self);
            if (self.isDirectory()) {
                // Exploded dev layout: build/classes/java/main → build/resources/main
                File dir = self;
                for (int i = 0; i < 3 && dir != null; i++) {
                    File res = new File(dir, "resources/main");
                    if (res.isDirectory()) candidates.add(res);
                    dir = dir.getParentFile();
                }
            }
        }
        for (File candidate : candidates) {
            if (hasDatapack(candidate, lang)) return candidate;
        }
        // Last resort: scan the plugins folder for the addon JAR.
        File plugins = new File("plugins");
        File[] jars = plugins.listFiles((d, n) -> n.endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                if (hasDatapack(jar, lang)) return jar;
            }
        }
        log.warn("Datapack source not found via code source; will rely on discovered pack if any.");
        return null;
    }

    private static File codeSource(Class<?> clazz) {
        try {
            var location = clazz.getProtectionDomain().getCodeSource().getLocation();
            return location == null ? null : new File(location.toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasDatapack(File file, String lang) {
        if (file == null) return false;
        String entry = JAR_PREFIX + lang + "/pack.mcmeta";
        try {
            if (file.isFile()) {
                try (ZipFile zip = new ZipFile(file)) {
                    return zip.getEntry(entry) != null;
                }
            }
            if (file.isDirectory()) {
                return new File(file, entry).isFile();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // =========================
    // EXTRACTION
    // =========================

    private static int copyFromJar(File jar, File target, Map<String, Boolean> parts,
                                   String jarPrefix) throws Exception {
        int copied = 0;
        try (ZipFile zip = new ZipFile(jar)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(jarPrefix)) continue;
                String rel = entry.getName().substring(jarPrefix.length());
                if (rel.isEmpty()) continue;
                if (entry.isDirectory()) {
                    new File(target, rel).mkdirs();
                    continue;
                }
                if (!isPathEnabled(parts, rel)) continue;
                if (write(target, rel, zip.getInputStream(entry))) copied++;
            }
        }
        return copied;
    }

    private static int copyFromDirectory(File root, File target, Map<String, Boolean> parts,
                                         ComponentLogger log, String jarPrefix) throws Exception {
        Path packRoot = root.toPath().resolve(jarPrefix);
        if (!Files.isDirectory(packRoot)) return 0;
        List<Path> files;
        try (var walk = Files.walk(packRoot)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        int copied = 0;
        for (Path file : files) {
            String rel = packRoot.relativize(file).toString().replace('\\', '/');
            if (!isPathEnabled(parts, rel)) continue;
            if (write(target, rel, Files.newInputStream(file))) copied++;
        }
        return copied;
    }

    private static boolean write(File target, String rel, InputStream in) throws Exception {
        try (in) {
            File outFile = new File(target, rel);
            File parent = outFile.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                in.transferTo(out);
            }
            return true;
        }
    }

    /** Mirrors {@code DatapackModules.isPathEnabled} without the plugin config dependency. */
    private static boolean isPathEnabled(Map<String, Boolean> parts, String relPath) {
        if (relPath == null || !relPath.startsWith("data/")) return true;
        for (Map.Entry<String, List<String>> e : PREFIXES.entrySet()) {
            for (String prefix : e.getValue()) {
                if (relPath.startsWith("data/" + prefix)) {
                    return parts.getOrDefault(e.getKey(), Boolean.TRUE);
                }
            }
        }
        return true;
    }

    /**
     * Removes every previously extracted pack ({@code UI-Datapack},
     * {@code UI-Datapack-en}, {@code UI-Datapack-ru}, ...) so switching the language
     * never leaves a stale copy behind.
     */
    private static void deleteStalePacks(File packRoot) {
        File[] children = packRoot.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory() && child.getName().startsWith(PACK_PREFIX)) {
                deleteRecursively(child.toPath());
            }
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
}
