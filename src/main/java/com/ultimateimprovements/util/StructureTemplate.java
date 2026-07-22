package com.ultimateimprovements.util;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Loads and matches Minecraft NBT structure files (.nbt) against the live world.
 * <p>
 * Usage:
 * <pre>
 *   StructureTemplate tmpl = StructureTemplate.loadFromNbt(plugin.getResource("NBT-Files/lightning_str.nbt"), "lightning");
 *   Location center = tmpl.findMatch(frameLocation, 5);
 * </pre>
 */
public class StructureTemplate {

    /** A single required block relative to the structure center. */
    public record BlockEntry(int dx, int dy, int dz, Material material) {}

    private final String name;
    private final List<BlockEntry> blocks = new ArrayList<>();

    /** Bounding-box of the structure (relative to center). Used for quick bounds check. */
    private int minX, maxX, minY, maxY, minZ, maxZ;

    public StructureTemplate(String name) {
        this.name = name;
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
    }

    public String getName() { return name; }
    public List<BlockEntry> getBlocks() { return Collections.unmodifiableList(blocks); }

    private void addBlock(int dx, int dy, int dz, Material material) {
        blocks.add(new BlockEntry(dx, dy, dz, material));
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
     * Scan within {@code radius} blocks of {@code origin} to find a position
     * where EVERY block in this template matches the world.
     *
     * @param origin  the reference location (usually the item frame position)
     * @param radius  search radius in blocks
     * @return the matching center position, or {@code null} if not found
     */
    public Location findMatch(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null || blocks.isEmpty()) return null;

        World world = origin.getWorld();
        int fx = origin.getBlockX(), fy = origin.getBlockY(), fz = origin.getBlockZ();

        // Pre-fetch the first block for a quick rejection (anchor)
        BlockEntry first = blocks.get(0);

        // Scan all candidate center positions within the radius
        for (int cx = fx - radius; cx <= fx + radius; cx++) {
            for (int cy = fy - radius; cy <= fy + radius; cy++) {
                for (int cz = fz - radius; cz <= fz + radius; cz++) {

                    // Quick reject: check the first block (anchor) first
                    Material anchor = world.getBlockAt(
                            cx + first.dx(), cy + first.dy(), cz + first.dz()
                    ).getType();
                    if (anchor != first.material()) continue;

                    // Check ALL remaining template blocks
                    boolean allMatch = true;
                    for (BlockEntry entry : blocks) {
                        Material actual = world.getBlockAt(
                                cx + entry.dx(), cy + entry.dy(), cz + entry.dz()
                        ).getType();
                        if (actual != entry.material()) {
                            allMatch = false;
                            break;
                        }
                    }

                    if (allMatch) {
                        return new Location(world, cx, cy, cz);
                    }
                }
            }
        }

        return null;
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

            // Extract size (может быть TAG_Int_Array или TAG_List)
            int[] size = toIntArray(root.get("size"));
            if (size == null || size.length < 3) {
                throw new IOException("Missing or invalid 'size' in structure file");
            }

            // Compute top-center offset — all NBT block positions are relative to
            // the bottom-north-west origin (0,0,0), but we need them relative to the
            // top-center of the structure (where the item frame / connection point is).
            int topCenterX = size[0] / 2;
            int topCenterY = size[1] - 1;
            int topCenterZ = size[2] / 2;

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
                    // Use a placeholder — but this will cause matching to fail,
                    // which is correct since we don't know what block this is.
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

            StructureTemplate tmpl = new StructureTemplate(name);

            for (Object obj : blocksList) {
                Map<String, Object> blockEntry = (Map<String, Object>) obj;
                int[] pos = toIntArray(blockEntry.get("pos"));
                if (pos == null || pos.length < 3) continue;

                int state = (int) blockEntry.get("state");
                if (state < 0 || state >= paletteSize) continue;

                Material mat = palette[state];
                if (mat == Material.AIR || mat == Material.STRUCTURE_VOID) continue; // skip air/void

                // Shift from NBT origin to top-center offset
                int dx = pos[0] - topCenterX;
                int dy = pos[1] - topCenterY;
                int dz = pos[2] - topCenterZ;
                tmpl.addBlock(dx, dy, dz, mat);
            }

            ConsoleLogger.info(
                    "[Structure] Loaded template '" + name + "' with "
                            + tmpl.blocks.size() + " non-air blocks, size "
                            + size[0] + "×" + size[1] + "×" + size[2]
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
     * Get the loading error for a specific template, or null if it loaded successfully.
     */
    public static String getTemplateError(String name) {
        return templateErrors.get(name);
    }

    /**
     * Load all NBT structure files bundled in the plugin resources.
     * Call this once during plugin startup (or reload).
     */
    public static void initAll() {
        templates.clear();
        templateErrors.clear();

        loadTemplate("reactor",   "NBT-Files/reactorcore1.nbt");
        loadTemplate("lightning", "NBT-Files/lightning_str.nbt");

        ConsoleLogger.info(
                "[Structure] Loaded " + templates.size() + " structure templates"
        );
    }

    private static void loadTemplate(String name, String resourcePath) {
        try (InputStream is = Main.getInstance().getResource(resourcePath)) {
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
            Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
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
