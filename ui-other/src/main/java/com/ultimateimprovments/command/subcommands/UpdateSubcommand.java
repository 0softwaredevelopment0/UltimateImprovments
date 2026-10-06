package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * /ui update [tag] — downloads the family release JARs into
 * {@code plugins/UltimateImprovments/update/}.
 * <p>
 * The JARs are <b>NOT applied automatically</b> (Paper 26.3+ blocks runtime
 * plugin registration): {@code /ui swapjar} moves them into {@code plugins/}
 * and a server restart makes them live. Without an argument the latest GitHub
 * release is used; a tag argument ("1.8.4" or "v1.8.4") downloads that
 * release, even if it matches the current version.
 * <p>
 * The release must attach the {@code *-jars.tar} archive (the standard
 * release pipeline asset). Requires the permission:
 * {@code ui.command.swapjar} (one workflow together with {@code /ui swapjar}).
 */
public final class UpdateSubcommand {

    private static final String PERMISSION = "ui.command.swapjar";
    private static final String REPO = "0softwaredevelopment0/UltimateImprovments";
    private static final String API_LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String API_TAG = "https://api.github.com/repos/" + REPO + "/releases/tags/";
    private static final String USER_AGENT = "UltimateImprovments-Updater";
    private static final int TIMEOUT_SECONDS = 30;

    /** Guards against concurrent downloads. */
    private static volatile boolean running = false;

    private UpdateSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender, PERMISSION);
            return true;
        }

        String tag = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) continue; // future flags
            tag = a;
        }

        if (running) {
            sender.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>A download is already in progress — wait for it to finish.</white>"));
            return true;
        }

        final String requestedTag = tag;
        running = true;
        sender.sendMessage(MessageUtil.parse(
                "<yellow>⟳</yellow> <gray>Resolving the release"
                        + (requestedTag != null ? " </gray><white>" + requestedTag : "") + "<gray>...</gray>"));
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            try {
                download(sender, requestedTag);
            } catch (Exception e) {
                ConsoleLogger.error("[Update] Download failed: " + e.getMessage());
                e.printStackTrace();
                sync(sender, () -> {
                    sender.sendMessage(MessageUtil.parse(
                            "<dark_red>❌</dark_red> <red>Download failed: </red><white>"
                                    + e.getMessage() + "</white>"));
                    sender.sendMessage(MessageUtil.parse(
                            "<gray>Stack trace in the server console.</gray>"));
                });
            } finally {
                running = false;
            }
        });
        return true;
    }

    // ==========================================================================
    // DOWNLOAD (async)
    // ==========================================================================

    private static void download(CommandSender sender, String requestedTag) throws Exception {
        String current = Main.getInstance().getPluginMeta().getVersion();

        // 1. Resolve the release
        JsonObject release = requestedTag != null ? fetchReleaseByTag(requestedTag) : fetchLatest();
        String tagName = release.get("tag_name").getAsString();
        String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;

        if (requestedTag == null && version.equals(current)) {
            sync(sender, () -> sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Already on the latest release (</white><yellow>v" + version
                            + "</yellow><white>). Pass a tag to re-download: </white><yellow>/ui update <tag>")));
            return;
        }

        // 2. Find the jars.tar asset
        JsonArray assets = release.getAsJsonArray("assets");
        String assetUrl = null;
        String assetName = null;
        List<String> assetNames = new ArrayList<>();
        for (JsonElement el : assets) {
            JsonObject asset = el.getAsJsonObject();
            String name = asset.get("name").getAsString();
            assetNames.add(name);
            if (name.endsWith("-jars.tar")) {
                assetUrl = asset.get("browser_download_url").getAsString();
                assetName = name;
            }
        }
        if (assetUrl == null) {
            sync(sender, () -> {
                sender.sendMessage(MessageUtil.parse(
                        "<dark_red>❌</dark_red> <red>Release </red><white>" + tagName
                                + "</white><red> has no *-jars.tar asset.</red>"));
                sender.sendMessage(MessageUtil.parse(
                        "<gray>Assets: </gray><white>" + String.join(", ", assetNames) + "</white>"));
            });
            return;
        }

        // 3. Download the archive
        File updateDir = new File(Main.getInstance().getDataFolder(), "update");
        Files.createDirectories(updateDir.toPath());
        File tarFile = new File(updateDir, assetName);
        Files.deleteIfExists(tarFile.toPath());

        final String assetNameFinal = assetName;
        sync(sender, () -> sender.sendMessage(MessageUtil.parse(
                "<yellow>⟳</yellow> <gray>Downloading </gray><white>" + assetNameFinal + "</white><gray>...</gray>")));

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(assetUrl))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/octet-stream")
                .timeout(Duration.ofSeconds(300))
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(tarFile.toPath()));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(tarFile.toPath());
            throw new IllegalStateException("HTTP " + response.statusCode()
                    + " while downloading " + assetName);
        }

        // 4. Extract the family JARs
        int count = extractJars(tarFile, updateDir);
        Files.deleteIfExists(tarFile.toPath());

        if (count == 0) {
            throw new IllegalStateException("No UI-*.jar files found inside " + assetName);
        }

        final String finalTag = tagName;
        final int finalCount = count;
        sync(sender, () -> {
            sender.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>Downloaded </white><yellow>" + finalTag
                            + "</yellow><white>: </white><yellow>" + finalCount
                            + "</yellow><white> JAR(s) → plugins/UltimateImprovments/update/</white>"));
            sender.sendMessage(MessageUtil.parse(
                    "<gray>They are NOT applied automatically. Run </gray><white>/ui swapjar</white>"
                            + "<gray> to move them into plugins/, then restart the server.</gray>"));
        });
        ConsoleLogger.info("[Update] " + tagName + " downloaded: " + finalCount + " JAR(s) into update/");
    }

    /** GitHub release by explicit tag ("1.8.4" or "v1.8.4" accepted). */
    private static JsonObject fetchReleaseByTag(String tag) throws Exception {
        JsonObject direct = fetchJson(API_TAG + tag);
        if (direct != null) return direct;
        if (!tag.startsWith("v")) {
            JsonObject prefixed = fetchJson(API_TAG + "v" + tag);
            if (prefixed != null) return prefixed;
        }
        throw new IllegalStateException("Release not found: " + tag);
    }

    private static JsonObject fetchLatest() throws Exception {
        JsonObject release = fetchJson(API_LATEST);
        if (release == null) throw new IllegalStateException("GitHub returned no latest release");
        return release;
    }

    /** GET + JSON parse; null on 404. */
    private static JsonObject fetchJson(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() == 403 || response.statusCode() == 429) {
            throw new IllegalStateException("GitHub API rate limit exceeded (HTTP "
                    + response.statusCode() + ") — try later");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub API returned HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    // ==========================================================================
    // MINIMAL TAR READER (ustar + GNU longname) — extracts UI-*.jar entries
    // ==========================================================================

    private static int extractJars(File tar, File outDir) throws Exception {
        int count = 0;
        try (InputStream in = new BufferedInputStream(new FileInputStream(tar))) {
            byte[] header = new byte[512];
            String longName = null;
            while (true) {
                if (in.read(header, 0, 512) < 512) break;

                boolean allZero = true;
                for (byte b : header) {
                    if (b != 0) { allZero = false; break; }
                }
                if (allZero) break; // end-of-archive block

                String name = longName != null ? longName : parseTarString(header, 0, 100);
                String prefix = parseTarString(header, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                long size = parseTarOctal(header, 124, 12);
                byte typeflag = header[156];

                if (typeflag == 'L') { // GNU long name: the data block holds the next name
                    byte[] buf = new byte[(int) size];
                    readFully(in, buf);
                    longName = parseTarString(buf, 0, buf.length);
                    continue;
                }
                longName = null;

                boolean regular = typeflag == '0' || typeflag == 0;
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (regular && fileName.startsWith("UI-") && fileName.endsWith(".jar")) {
                    File target = new File(outDir, fileName);
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        copyExactly(in, out, size);
                    }
                    count++;
                    ConsoleLogger.info("[Update] Extracted " + fileName);
                } else {
                    in.skipNBytes(size);
                }
                // Entries are padded to a 512-byte boundary
                long pad = (512 - size % 512) % 512;
                in.skipNBytes(pad);
            }
        }
        return count;
    }

    private static void copyExactly(InputStream in, FileOutputStream out, long size) throws Exception {
        byte[] buf = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int chunk = (int) Math.min(buf.length, remaining);
            int read = in.read(buf, 0, chunk);
            if (read < 0) throw new java.io.EOFException("Unexpected end of tar archive");
            out.write(buf, 0, read);
            remaining -= read;
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws Exception {
        int done = 0;
        while (done < buf.length) {
            int read = in.read(buf, done, buf.length - done);
            if (read < 0) throw new java.io.EOFException("Unexpected end of tar archive");
            done += read;
        }
    }

    private static String parseTarString(byte[] data, int offset, int length) {
        int end = offset;
        int limit = offset + length;
        while (end < limit && data[end] != 0) end++;
        return new String(data, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long parseTarOctal(byte[] data, int offset, int length) {
        String s = parseTarString(data, offset, length).trim();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Runs the runnable on the main thread (message delivery). */
    private static void sync(CommandSender sender, Runnable runnable) {
        try {
            Bukkit.getScheduler().runTask(Main.getInstance(), runnable);
        } catch (Exception e) {
            // Scheduler unavailable (e.g. plugin disabling) — run inline
            runnable.run();
        }
    }
}
