package com.ultimateimprovments.util;

import com.ultimateimprovments.mbs.UIMBS;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.GZIPInputStream;

/**
 * Loads and matches Minecraft NBT structure files (.nbt) against the live world.
 * <p>
 * Matching is <b>exact and full</b>: every cell of the template must match the world,
 * including cells that must be AIR (interior chambers and the space above the top face).
 * Block orientation (stairs, trapdoors, levers, signs) is ignored — only the material type counts.
 * Wall signs of any wood type match each other.
 * <p>
 * When no exact match exists, {@link #bestMatch(Location, int)} finds the candidate
 * center with the fewest mismatched cells and reports the match percentage together
 * with concrete fix instructions (place/break/replace with coordinates).
 * <p>
 * Usage:
 * <pre>
 *   Location center = tmpl.findMatch(frameLocation, 5);          // exact match
 *   MatchResult best = tmpl.bestMatch(frameLocation, 5);         // closest candidate
 * </pre>
 */
public class StructureTemplate {

    /** A single template cell relative to the structure center (top-center of the template). */
    public record BlockEntry(int dx, int dy, int dz, Material material) {}

    /** One mismatch found while checking a candidate position. */
    public record Fix(int dx, int dy, int dz, Material expected, Material actual) {}

    /** Result of checking one candidate center position against a template. */
    public record MatchResult(boolean matched, int total, int mismatches, Location center, List<Fix> fixes) {

        /** Percentage of matching cells (0..100). */
        public int percent() {
            return total == 0 ? 100 : Math.max(0, Math.round((total - mismatches) * 100f / total));
        }
    }

    /** The template whose shape is closest to what the player actually built. */
    public record BestCandidate(StructureTemplate template, MatchResult result) {}

    private final String name;
    private final String displayName;

    /** Required solid blocks (non-air template cells). */
    private final List<BlockEntry> blocks = new ArrayList<>();
    /** Cells that must be AIR in the world (interior chambers, space above the top face). */
    private final List<BlockEntry> airBlocks = new ArrayList<>();

    /** Bounding-box of the structure (relative to center). Used for quick bounds check. */
    private int minX, maxX, minY, maxY, minZ, maxZ;

    public StructureTemplate(String name) {
        this(name, name);
    }

    public StructureTemplate(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
    }

    public String getName() { return name; }
    /** Human-readable Russian name (used in player messages). */
    public String getDisplayName() { return displayName; }
    public List<BlockEntry> getBlocks() { return java.util.Collections.unmodifiableList(blocks); }
    public List<BlockEntry> getAirBlocks() { return java.util.Collections.unmodifiableList(airBlocks); }

    /** Total number of checked cells (solid + required-air). */
    public int totalCells() { return blocks.size() + airBlocks.size(); }

    private void addBlock(int dx, int dy, int dz, Material material) {
        blocks.add(new BlockEntry(dx, dy, dz, material));
        trackBounds(dx, dy, dz);
    }

    private void addAir(int dx, int dy, int dz) {
        airBlocks.add(new BlockEntry(dx, dy, dz, Material.AIR));
        trackBounds(dx, dy, dz);
    }

    private void trackBounds(int dx, int dy, int dz) {
        if (dx < minX) minX = dx;
        if (dx > maxX) maxX = dx;
        if (dy < minY) minY = dy;
        if (dy > maxY) maxY = dy;
        if (dz < minZ) minZ = dz;
        if (dz > maxZ) maxZ = dz;
    }

    // =========================
    // MATCHING
    // =========================

    /**
     * Check the whole structure at the given candidate center.
     * Every template cell (solid AND air) is compared with the world.
     */
    public MatchResult checkAt(Location center) {
        if (center == null || center.getWorld() == null || totalCells() == 0) {
            return new MatchResult(false, Math.max(1, totalCells()), Math.max(1, totalCells()), center, List.of());
        }

        World world = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();

        List<Fix> fixes = new ArrayList<>();
        for (BlockEntry b : blocks) {
            Material actual = world.getBlockAt(cx + b.dx(), cy + b.dy(), cz + b.dz()).getType();
            if (!materialMatches(b.material(), actual)) {
                fixes.add(new Fix(b.dx(), b.dy(), b.dz(), b.material(), actual));
            }
        }
        for (BlockEntry b : airBlocks) {
            Material actual = world.getBlockAt(cx + b.dx(), cy + b.dy(), cz + b.dz()).getType();
            if (actual != Material.AIR) {
                fixes.add(new Fix(b.dx(), b.dy(), b.dz(), Material.AIR, actual));
            }
        }

        return new MatchResult(fixes.isEmpty(), totalCells(), fixes.size(), center, List.copyOf(fixes));
    }

    /**
     * Scan within {@code radius} blocks of {@code origin} to find a position
     * where EVERY template cell (solid and air) matches the world.
     *
     * @param origin  the reference location (usually the item frame position)
     * @param radius  search radius in blocks
     * @return the matching center position, or {@code null} if not found
     */
    public Location findMatch(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null || blocks.isEmpty()) return null;

        World world = origin.getWorld();
        int fx = origin.getBlockX(), fy = origin.getBlockY(), fz = origin.getBlockZ();

        // Quick reject: check the first solid block (anchor) first
        BlockEntry first = blocks.get(0);

        for (int cx = fx - radius; cx <= fx + radius; cx++) {
            for (int cy = fy - radius; cy <= fy + radius; cy++) {
                for (int cz = fz - radius; cz <= fz + radius; cz++) {

                    Material anchor = world.getBlockAt(cx + first.dx(), cy + first.dy(), cz + first.dz()).getType();
                    if (!materialMatches(first.material(), anchor)) continue;

                    MatchResult r = checkAt(new Location(world, cx, cy, cz));
                    if (r.matched()) return new Location(world, cx, cy, cz);
                }
            }
        }

        return null;
    }

    /**
     * Scan within {@code radius} blocks of {@code origin} and return the candidate
     * position with the FEWEST mismatches against this template (even if it does not match).
     *
     * @return the best {@link MatchResult}, or {@code null} if the template is empty
     */
    public MatchResult bestMatch(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null || totalCells() == 0) return null;

        World world = origin.getWorld();
        int fx = origin.getBlockX(), fy = origin.getBlockY(), fz = origin.getBlockZ();

        MatchResult best = null;

        // Cheap pruning sample: the first few solid blocks. If more of them are
        // already mismatched than the best candidate's TOTAL mismatches, this
        // position can never win — skip the expensive full check.
        List<BlockEntry> sample = blocks.size() > 12 ? blocks.subList(0, 12) : blocks;

        for (int cx = fx - radius; cx <= fx + radius; cx++) {
            for (int cy = fy - radius; cy <= fy + radius; cy++) {
                for (int cz = fz - radius; cz <= fz + radius; cz++) {

                    if (best != null) {
                        int sampleMismatch = 0;
                        for (BlockEntry b : sample) {
                            Material actual = world.getBlockAt(cx + b.dx(), cy + b.dy(), cz + b.dz()).getType();
                            if (!materialMatches(b.material(), actual)) sampleMismatch++;
                        }
                        if (sampleMismatch >= best.mismatches()) continue;
                    }

                    MatchResult r = checkAt(new Location(world, cx, cy, cz));
                    if (r.matched()) return r; // exact match — nothing better exists

                    if (best == null || r.mismatches() < best.mismatches()) {
                        best = r;
                    }
                }
            }
        }

        return best;
    }

    /**
     * Across ALL loaded templates find the one whose shape is closest to what
     * stands near {@code origin} — compared by MATCH PERCENT, not by absolute
     * mismatch count. A tiny 100-cell template with 27 wrong cells (73%) must
     * NOT beat a 981-cell reactor with 28 wrong cells (97%): the reactor is
     * clearly the structure the player almost built, and only the percent
     * comparison reveals that (with the fix list below it).
     * Ties are broken by the smaller absolute mismatch count.
     */
    public static BestCandidate findBestCandidate(Location origin, int radius) {
        BestCandidate best = null;
        for (StructureTemplate t : templates.values()) {
            MatchResult r = t.bestMatch(origin, radius);
            if (r == null) continue;
            if (best == null
                    || r.percent() > best.result().percent()
                    || (r.percent() == best.result().percent()
                        && r.mismatches() < best.result().mismatches())) {
                best = new BestCandidate(t, r);
            }
        }
        return best;
    }

    /**
     * Material comparison used everywhere in this class: exact match,
     * except wall signs of different wood types which are interchangeable.
     */
    private static boolean materialMatches(Material expected, Material actual) {
        if (expected == actual) return true;
        if (SIGN_TYPES.contains(expected) && SIGN_TYPES.contains(actual)) return true;
        // Levers are ignored everywhere (see the parser): any world block at a
        // lever template cell counts as a match, and a lever never mismatches.
        if (expected == Material.LEVER || actual == Material.LEVER) return true;
        return false;
    }

    // =========================
    // FIX FORMATTING (Russian, for player messages)
    // =========================

    /**
     * Format one mismatch as a concrete instruction with ABSOLUTE coordinates.
     * Uses the localized {@code structures.fix_*} config messages
     * (with {@code %block%}, {@code %expected%} and {@code %coords%} placeholders).
     */
    public static String formatFix(Fix f, Location base) {
        if (base == null || base.getWorld() == null) {
            return formatFixRelative(f);
        }
        String where = "<white>[" + (base.getBlockX() + f.dx())
                + " " + (base.getBlockY() + f.dy())
                + " " + (base.getBlockZ() + f.dz()) + "]";
        return formatFixMessage(f, where);
    }

    /** Same as {@link #formatFix(Fix, Location)} but with offsets relative to the center. */
    public static String formatFixRelative(Fix f) {
        return formatFixMessage(f, "<white>[" + f.dx() + ", " + f.dy() + ", " + f.dz() + "]");
    }

    private static String formatFixMessage(Fix f, String where) {
        if (f.expected() == Material.AIR) {
            return StructuresMessages.get("fix_break",
                    "<red>break</red> <gray>%block% <gray>at %coords%")
                    .replace("%block%", blockName(f.actual()))
                    .replace("%coords%", where);
        }
        if (f.actual() == Material.AIR) {
            return StructuresMessages.get("fix_place",
                    "<green>place</green> <yellow>%block% <gray>at %coords%")
                    .replace("%block%", blockName(f.expected()))
                    .replace("%coords%", where);
        }
        return StructuresMessages.get("fix_replace",
                "<yellow>replace</yellow> <gray>%block% <yellow>→ %expected% <gray>at %coords%")
                .replace("%block%", blockName(f.actual()))
                .replace("%expected%", blockName(f.expected()))
                .replace("%coords%", where);
    }

    private static String blockName(Material m) {
        if (m == null) return "?";
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // =========================
    // NBT PARSER — Minecraft Structure file format (gzip compressed)
    // =========================

    /**
     * Load a structure template from a gzip-compressed NBT structure file.
     *
     * @param inputStream  the input stream of the .nbt file
     * @param name         a human-readable name for this template
     * @return the loaded StructureTemplate
     * @throws IOException if reading or parsing fails
     */
    @SuppressWarnings("unchecked")
    public static StructureTemplate loadFromNbt(InputStream inputStream, String name) throws IOException {
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(inputStream))) {

            // Read root tag
            int rootType = dis.readByte();
            if (rootType != 10) { // TAG_Compound
                throw new IOException("Expected TAG_Compound (10) at root, got " + rootType);
            }
            readString(dis); // root name (unused)
            Map<String, Object> root = (Map<String, Object>) readTag(dis, rootType);

            // Extract size (can be TAG_Int_Array or TAG_List)
            int[] size = toIntArray(root.get("size"));
            if (size == null || size.length < 3) {
                throw new IOException("Missing or invalid 'size' in structure file");
            }

            // Compute the anchor offset — all NBT block positions are relative to
            // the bottom-north-west origin (0,0,0), but we need them relative to the
            // anchor cell (where the item frame / connection point is). By default
            // that is the top-center cell; per-template ANCHOR_ADJUSTMENTS shift it.
            int[] anchorAdj = ANCHOR_ADJUSTMENTS.getOrDefault(name, new int[]{0, 0, 0});
            int topCenterX = size[0] / 2 + anchorAdj[0];
            int topCenterY = size[1] - 1 + anchorAdj[1];
            int topCenterZ = size[2] / 2 + anchorAdj[2];

            // Extract palette
            List<Object> paletteList = (List<Object>) root.get("palette");
            if (paletteList == null) {
                paletteList = (List<Object>) root.get("Palette");
            }
            if (paletteList == null) {
                throw new IOException("Missing 'palette' in structure file");
            }
            int paletteSize = paletteList.size();

            // Build palette array: palette index → Material
            Material[] palette = new Material[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                Map<String, Object> entry = (Map<String, Object>) paletteList.get(i);
                String blockName = (String) entry.get("Name");
                if (blockName == null) {
                    throw new IOException("Palette entry " + i + " has no 'Name'");
                }
                // Strip "minecraft:" prefix
                if (blockName.startsWith("minecraft:")) {
                    blockName = blockName.substring(10);
                }
                Material mat = Material.matchMaterial(blockName, false);
                if (mat == null) {
                    ConsoleLogger.warn(
                            "[Structure] Unknown material in palette[" + i + "]: " + blockName
                    );
                    // Unknown blocks become AIR: they will be required to be air.
                    // Matching against real builds will fail with a clear fix message.
                    mat = Material.AIR;
                }
                palette[i] = mat;
            }

            // Extract blocks
            List<Object> blocksList = (List<Object>) root.get("blocks");
            if (blocksList == null) {
                blocksList = (List<Object>) root.get("Blocks");
            }
            if (blocksList == null) {
                throw new IOException("Missing 'blocks' in structure file");
            }

            StructureTemplate tmpl = new StructureTemplate(name, StructuresMessages.structureName(name));

            for (Object obj : blocksList) {
                Map<String, Object> blockEntry = (Map<String, Object>) obj;
                int[] pos = toIntArray(blockEntry.get("pos"));
                if (pos == null || pos.length < 3) continue;

                int state = (int) blockEntry.get("state");
                if (state < 0 || state >= paletteSize) continue;

                Material mat = palette[state];
                if (mat == Material.STRUCTURE_VOID) continue; // void = not checked at all
                // Levers are interaction devices (redstone control of the lamps):
                // ignored everywhere — not required at assembly, not tracked as
                // structure blocks by the damage/repair system.
                if (mat == Material.LEVER) continue;

                // Shift from NBT origin to top-center offset
                int dx = pos[0] - topCenterX;
                int dy = pos[1] - topCenterY;
                int dz = pos[2] - topCenterZ;

                if (mat == Material.AIR) {
                    tmpl.addAir(dx, dy, dz); // air cells ARE checked — must be empty
                } else {
                    tmpl.addBlock(dx, dy, dz, mat);
                }
            }

            ConsoleLogger.info(
                    "[Structure] Loaded template '" + name + "' with "
                            + tmpl.blocks.size() + " solid + " + tmpl.airBlocks.size()
                            + " air cells, size " + size[0] + "×" + size[1] + "×" + size[2]
            );

            return tmpl;
        }
    }

    // =========================
    // HELPER: convert Object (TAG_List or TAG_Int_Array) to int[]
    // =========================

    /**
     * Safely convert an NBT value that may be either a TAG_List of ints (ArrayList)
     * or a TAG_Int_Array (int[]) to a uniform int[].
     */
    private static int[] toIntArray(Object obj) throws IOException {
        if (obj instanceof int[]) {
            return (int[]) obj;
        } else if (obj instanceof List<?> list) {
            int[] arr = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object val = list.get(i);
                if (val instanceof Number num) {
                    arr[i] = num.intValue();
                } else {
                    throw new IOException("Expected numeric value in list at index " + i + ", got " + val.getClass().getName());
                }
            }
            return arr;
        }
        throw new IOException("Expected int[] or List for NBT value, got " + (obj == null ? "null" : obj.getClass().getName()));
    }

    // =========================
    // LOW-LEVEL NBT READER
    // =========================

    private static String readString(DataInputStream dis) throws IOException {
        short len = dis.readShort();
        if (len < 0) throw new IOException("Negative string length: " + len);
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Object readTag(DataInputStream dis, int tagType) throws IOException {
        return switch (tagType) {
            case 0  -> null;                                // TAG_End
            case 1  -> (int) dis.readByte() & 0xFF;        // TAG_Byte (unsigned)
            case 2  -> (int) dis.readShort();               // TAG_Short
            case 3  -> dis.readInt();                       // TAG_Int
            case 4  -> dis.readLong();                      // TAG_Long
            case 5  -> dis.readFloat();                     // TAG_Float
            case 6  -> dis.readDouble();                    // TAG_Double
            case 7  -> {                                    // TAG_Byte_Array
                int len = dis.readInt();
                byte[] arr = new byte[len];
                dis.readFully(arr);
                yield arr;
            }
            case 8  -> readString(dis);                     // TAG_String
            case 9  -> {                                    // TAG_List
                int elemType = dis.readByte() & 0xFF;
                int len = dis.readInt();
                List<Object> list = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    list.add(readTag(dis, elemType));
                }
                yield list;
            }
            case 10 -> {                                    // TAG_Compound
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int type = dis.readByte() & 0xFF;
                    if (type == 0) break; // TAG_End
                    String fieldName = readString(dis);
                    map.put(fieldName, readTag(dis, type));
                }
                yield map;
            }
            case 11 -> {                                    // TAG_Int_Array
                int len = dis.readInt();
                int[] arr = new int[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = dis.readInt();
                }
                yield arr;
            }
            case 12 -> {                                    // TAG_Long_Array
                int len = dis.readInt();
                long[] arr = new long[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = dis.readLong();
                }
                yield arr;
            }
            default -> throw new IOException("Unknown NBT tag type: " + tagType);
        };
    }

    // =========================
    // STATIC TEMPLATE REGISTRY
    // =========================

    private static final Map<String, StructureTemplate> templates = new LinkedHashMap<>();

    /** Stores loading errors per template name (e.g. "reactor" → "Missing 'size'"). */
    private static final Map<String, String> templateErrors = new LinkedHashMap<>();

    /**
     * Per-template anchor adjustments relative to the top-center cell.
     * The anchor is the template cell where the item frame is placed:
     * all template offsets (and the runtime ReactorStructure key checks) are
     * relative to it.
     * <p>
     * darkfusionreactor: the frame stands on the TOP FACE of the central top
     * copper bulb — 0.5 blocks above it, one block below the control bulbs —
     * i.e. in the free cell directly above the top-center bulb
     * (template cell (5, 9, 4) for the 10×11×9 template).
     */
    private static final Map<String, int[]> ANCHOR_ADJUSTMENTS = Map.of(
            "darkfusionreactor", new int[]{ 0, -1, 0 }
    );

    /** Wall and standing sign materials of every wood type (interchangeable when matching). */
    private static final Set<Material> SIGN_TYPES = buildSignTypes();

    private static Set<Material> buildSignTypes() {
        Set<Material> set = new HashSet<>();
        for (String wood : Arrays.asList(
                "oak", "dark_oak", "birch", "spruce", "jungle", "acacia",
                "cherry", "mangrove", "bamboo", "crimson", "warped", "pale_oak")) {
            Material wall = Material.matchMaterial(wood + "_wall_sign", false);
            if (wall != null) set.add(wall);
            Material standing = Material.matchMaterial(wood + "_sign", false);
            if (standing != null) set.add(standing);
        }
        return set;
    }

    /**
     * Get the loading error for a specific template, or null if it loaded successfully.
     */
    public static String getTemplateError(String name) {
        return templateErrors.get(name);
    }

    /**
     * Load ALL .nbt structure files bundled in the plugin resources
     * (auto-discovery: any new .nbt file added to NBT-Files/ is picked up
     * on the next start / template reload without code changes).
     * Call this once during plugin startup (or reload).
     */
    public static void initAll() {
        templates.clear();
        templateErrors.clear();

        int count = 0;
        try {
            File source = findCodeSourceLocation();
            if (source != null && source.isFile()) {
                count = loadFromJar(source);
            } else if (source != null && source.isDirectory()) {
                count = loadFromClassesDirectory(source.toPath());
            } else {
                ConsoleLogger.warn("[Structure] Could not locate plugin code source — no templates loaded");
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Structure] Failed to scan NBT-Files: " + e.getMessage());
        }

        ConsoleLogger.info("[Structure] Loaded " + templates.size() + " structure templates");
    }

    private static File findCodeSourceLocation() {
        try {
            return new File(UIMBS.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Load every NBT-Files/*.nbt entry from the plugin JAR. */
    private static int loadFromJar(File jarFile) throws IOException {
        int count = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            List<String> paths = new ArrayList<>();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String n = e.getName();
                if (!e.isDirectory() && n.startsWith("NBT-Files/") && n.endsWith(".nbt")) {
                    paths.add(n);
                }
            }
            for (String path : paths) {
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                loadTemplate(templateName(fileName), path);
                count++;
            }
        }
        return count;
    }

    /** Load every *.nbt from build/resources/main/NBT-Files (IDE / exploded run). */
    private static int loadFromClassesDirectory(Path classesDir) throws IOException {
        Path dir = classesDir.resolve("NBT-Files");
        if (!Files.isDirectory(dir)) return 0;

        int count = 0;
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".nbt")).forEach(files::add);
        }
        for (Path p : files) {
            String fileName = p.getFileName().toString();
            loadTemplate(templateName(fileName), "NBT-Files/" + fileName);
            count++;
        }
        return count;
    }

    private static String templateName(String fileName) {
        String n = fileName;
        if (n.endsWith(".nbt")) n = n.substring(0, n.length() - 4);
        // lightning_str.nbt → "lightning": the listener (ReactorListener) and the
        // config display names (structures.names.lightning) reference the short
        // name; without this the template loaded under "lightning_str" and every
        // get("lightning") returned null.
        if (n.equals("lightning_str")) n = "lightning";
        return n;
    }

    private static void loadTemplate(String name, String resourcePath) {
        try (InputStream is = UIMBS.getInstance().getResource(resourcePath)) {
            if (is == null) {
                String err = "Resource not found: " + resourcePath;
                ConsoleLogger.error("[Structure] " + err);
                templateErrors.put(name, err);
                return;
            }
            StructureTemplate tmpl = loadFromNbt(is, name);
            templates.put(name, tmpl);
            templateErrors.remove(name); // clear any previous error on success
        } catch (Exception e) {
            String err = e.getMessage();
            UIMBS.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                    "[Structure] Failed to load template '" + name + "'", e);
            templateErrors.put(name, err != null ? err : e.getClass().getSimpleName());
        }
    }

    /**
     * Get all loaded templates.
     */
    public static Collection<StructureTemplate> getAll() {
        return templates.values();
    }

    /**
     * Get a specific template by name.
     */
    public static StructureTemplate get(String name) {
        return templates.get(name);
    }
}
