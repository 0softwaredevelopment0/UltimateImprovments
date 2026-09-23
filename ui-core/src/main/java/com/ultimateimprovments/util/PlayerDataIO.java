package com.ultimateimprovments.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Offline playerdata (.dat) I/O.
 *
 * <p>Reads and writes a player's {@code <uuid>.dat} NBT file. The inventory is stored
 * in the main world's {@code playerdata} folder (the same file across all dimensions),
 * so locating is Multiverse-aware: it scans every loaded world folder plus the server
 * world container, preferring the file that actually contains {@code Inventory} /
 * {@code EnderItems} data.</p>
 *
 * <p>Format note (Minecraft 26.x): the player root is still a gzip {@link CompoundTag},
 * but {@code Inventory} / {@code EnderItems} are typed lists serialized with
 * {@link ItemStackWithSlot#CODEC}, and armor/offhand live in a single {@code equipment}
 * compound serialized with {@link EntityEquipment#CODEC}. This class uses those exact
 * vanilla codecs (with the server registry serialization context), so a round-trip is
 * byte-for-byte equivalent to what the server itself writes.</p>
 *
 * <p>Every write first copies the current file to {@code <uuid>-backup.dat} next to it.</p>
 */
public final class PlayerDataIO {

    private PlayerDataIO() {}

    // ═══════════════════════════════════════════════════════════════
    // SNAPSHOT MODEL
    // ═══════════════════════════════════════════════════════════════

    /** Armor + offhand of a player. */
    public static final class EquipmentSnapshot {
        public org.bukkit.inventory.ItemStack helmet;
        public org.bukkit.inventory.ItemStack chestplate;
        public org.bukkit.inventory.ItemStack leggings;
        public org.bukkit.inventory.ItemStack boots;
        public org.bukkit.inventory.ItemStack offhand;
    }

    // ═══════════════════════════════════════════════════════════════
    // FILE LOCATING (Multiverse-aware)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Locates the playerdata file for the given UUID.
     * Returns null if the player has no data file at all.
     */
    public static File locate(UUID uuid) {
        // All candidate playerdata folders (main world, loaded worlds,
        // world-container children — covers unloaded Multiverse worlds).
        File firstExisting = null;
        for (File folder : playerdataFolders()) {
            File candidate = new File(folder, uuid + ".dat");
            if (!candidate.isFile()) continue;
            if (firstExisting == null) firstExisting = candidate;
            if (hasInventoryData(candidate)) return candidate;
        }

        return firstExisting;
    }

    /** Returns {@code <worldFolder>/playerdata/<uuid>.dat}. */
    private static File dataFile(File worldFolder, UUID uuid) {
        return new File(worldFolder, "playerdata" + File.separator + uuid + ".dat");
    }

    /**
     * Resolves a player name to the UUID of an existing playerdata file.
     *
     * <p>Order of resolution:</p>
     * <ol>
     *   <li>Online player ({@link Bukkit#getPlayerExact});</li>
     *   <li>Bukkit caches: {@code getOfflinePlayerIfCached} / {@code usercache.json}</li>
     *       — but only if a data file with that UUID actually exists</li>
     *       (never a fake offline-mode UUID);</li>
     *   <li>Scan of every {@code playerdata/*.dat}: the NBT root is checked for
     *       {@code bukkit.lastKnownName} (written by Paper on every logout) —
     *       case-insensitive match. Also accepts {@code <uuid>.dat} where the
     *       name itself is a UUID.</li>
     * </ol>
     *
     * @return the UUID, or null when no known player with that name has a data file
     */
    public static UUID resolveUuidByName(String name) {
        if (name == null || name.isBlank()) return null;

        // 1) Online — trivially correct
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        // Name given as a raw UUID — use it directly
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // not a UUID — continue with name lookup
        }

        // 2) Bukkit caches (usercache.json / banned / ops / whitelist).
        //    getOfflinePlayer(name) is intentionally NOT used: for unknown names it
        //    fabricates a Mode-OFFLINE UUID that can never match a .dat file.
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            UUID id = cached.getUniqueId();
            if (locate(id) != null) return id;
        }

        // 3) Scan playerdata files for bukkit.lastKnownName
        for (File dir : playerdataFolders()) {
            File[] files = dir.listFiles((d, fn) -> fn != null && fn.toLowerCase().endsWith(".dat"));
            if (files == null) continue;
            for (File file : files) {
                UUID id = uuidFromFileName(file.getName());
                if (id == null) continue;
                if (matchesLastKnownName(file, name)) return id;
            }
        }
        return null;
    }

    /** Parses {@code <uuid>.dat} (also accepts the {@code -backup} suffix). */
    private static UUID uuidFromFileName(String fileName) {
        String base = fileName.endsWith(".dat") ? fileName.substring(0, fileName.length() - 4) : fileName;
        if (base.endsWith("-backup")) base = base.substring(0, base.length() - 7);
        try {
            return UUID.fromString(base);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True if the file root carries {@code bukkit.lastKnownName} equal (ci) to the name. */
    private static boolean matchesLastKnownName(File file, String name) {
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            if (!tag.contains("bukkit")) return false;
            Tag bukkitTag = tag.get("bukkit");
            if (!(bukkitTag instanceof CompoundTag bukkit)) return false;
            return bukkit.contains("lastKnownName")
                    && name.equalsIgnoreCase(bukkit.getString("lastKnownName").orElse(""));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Every {@code playerdata} folder to consider: the main world and all loaded
     * worlds, then one level of directories under the world container
     * (covers unloaded Multiverse worlds).
     */
    private static List<File> playerdataFolders() {
        List<File> folders = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            folders.add(new File(world.getWorldFolder(), "playerdata"));
        }
        File container = Bukkit.getWorldContainer();
        File[] children = container.listFiles(File::isDirectory);
        if (children != null) {
            for (File dir : children) {
                folders.add(new File(dir, "playerdata"));
            }
        }
        return folders;
    }

    /** True if the file root contains Inventory or EnderItems tags. */
    private static boolean hasInventoryData(File file) {
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            return tag.contains("Inventory") || tag.contains("EnderItems");
        } catch (Exception e) {
            return false;
        }
    }

    /** Backup file path: {@code <uuid>-backup.dat} next to the data file. */
    public static File backupFile(File dataFile) {
        String name = dataFile.getName();
        String base = name.endsWith(".dat") ? name.substring(0, name.length() - 4) : name;
        return new File(dataFile.getParentFile(), base + "-backup.dat");
    }

    // ═══════════════════════════════════════════════════════════════
    // RAW READ / WRITE
    // ═══════════════════════════════════════════════════════════════

    public static CompoundTag readData(File file) throws IOException {
        return NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
    }

    /**
     * Backs up the current file to {@code <uuid>-backup.dat} and writes the tag.
     * The write goes through a temp file and is moved into place to avoid corrupting
     * the player data if the JVM dies mid-write.
     */
    public static void writeData(File file, CompoundTag tag) throws IOException {
        File backup = backupFile(file);
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            ConsoleLogger.warn("[PlayerDataIO] Backup failed for " + file.getName() + ": " + e.getMessage());
        }

        Path target = file.toPath();
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            NbtIo.writeCompressed(tag, tmp);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INVENTORY READ / WRITE
    // ═══════════════════════════════════════════════════════════════

    /** Reads the main inventory (Bukkit storage layout, slots 0-35). */
    public static org.bukkit.inventory.ItemStack[] readMain(CompoundTag root) {
        return readList(root, "Inventory", 36);
    }

    /** Reads armor + offhand from the {@code equipment} compound. */
    public static EquipmentSnapshot readEquipment(CompoundTag root) {
        EquipmentSnapshot out = new EquipmentSnapshot();
        if (!root.contains("equipment")) return out;

        EntityEquipment eq = EntityEquipment.CODEC
                .parse(ops(), root.getCompoundOrEmpty("equipment"))
                .result()
                .orElse(null);
        if (eq == null) return out;

        out.helmet = nmsToBukkit(eq.get(EquipmentSlot.HEAD));
        out.chestplate = nmsToBukkit(eq.get(EquipmentSlot.CHEST));
        out.leggings = nmsToBukkit(eq.get(EquipmentSlot.LEGS));
        out.boots = nmsToBukkit(eq.get(EquipmentSlot.FEET));
        out.offhand = nmsToBukkit(eq.get(EquipmentSlot.OFFHAND));
        return out;
    }

    /** Reads the ender chest (27 slots). */
    public static org.bukkit.inventory.ItemStack[] readEnder(CompoundTag root) {
        return readList(root, "EnderItems", 27);
    }

    /** Writes the main inventory (only the Inventory tag is touched). */
    public static void writeMain(CompoundTag root, org.bukkit.inventory.ItemStack[] main) {
        writeList(root, "Inventory", main);
    }

    /**
     * Writes armor + offhand. Preserves any other equipment slots (body/saddle) that
     * may already be present, and removes the {@code equipment} key when everything
     * is empty — exactly like vanilla.
     */
    public static void writeEquipment(CompoundTag root, EquipmentSnapshot equipment) {
        RegistryOps<Tag> ops = ops();

        EntityEquipment eq = new EntityEquipment();
        if (root.contains("equipment")) {
            EntityEquipment existing = EntityEquipment.CODEC
                    .parse(ops, root.getCompoundOrEmpty("equipment"))
                    .result()
                    .orElse(null);
            if (existing != null) {
                eq.setAll(existing);
            }
        }

        eq.set(EquipmentSlot.HEAD, bukkitToNmsOrEmpty(equipment.helmet));
        eq.set(EquipmentSlot.CHEST, bukkitToNmsOrEmpty(equipment.chestplate));
        eq.set(EquipmentSlot.LEGS, bukkitToNmsOrEmpty(equipment.leggings));
        eq.set(EquipmentSlot.FEET, bukkitToNmsOrEmpty(equipment.boots));
        eq.set(EquipmentSlot.OFFHAND, bukkitToNmsOrEmpty(equipment.offhand));

        if (eq.isEmpty()) {
            root.remove("equipment");
            return;
        }

        Tag tag = EntityEquipment.CODEC.encodeStart(ops, eq).result().orElse(null);
        if (tag != null) {
            root.put("equipment", tag);
        }
    }

    /** Writes the ender chest (only the EnderItems tag is touched). */
    public static void writeEnder(CompoundTag root, org.bukkit.inventory.ItemStack[] ender) {
        writeList(root, "EnderItems", ender);
    }

    // ═══════════════════════════════════════════════════════════════
    // LIST + ITEM CONVERSION
    // ═══════════════════════════════════════════════════════════════

    private static org.bukkit.inventory.ItemStack[] readList(CompoundTag root, String key, int size) {
        org.bukkit.inventory.ItemStack[] out = new org.bukkit.inventory.ItemStack[size];
        if (!root.contains(key)) return out;

        ListTag list = root.getListOrEmpty(key);
        for (int i = 0; i < list.size(); i++) {
            ItemStackWithSlot entry = ItemStackWithSlot.CODEC
                    .parse(ops(), list.get(i))
                    .result()
                    .orElse(null);
            if (entry == null) continue;
            int slot = entry.slot();
            if (slot >= 0 && slot < size) {
                out[slot] = nmsToBukkit(entry.stack());
            }
        }
        return out;
    }

    private static void writeList(CompoundTag root, String key, org.bukkit.inventory.ItemStack[] contents) {
        RegistryOps<Tag> ops = ops();
        ListTag list = new ListTag();
        for (int i = 0; i < contents.length; i++) {
            ItemStack nms = bukkitToNmsOrEmpty(contents[i]);
            if (nms.isEmpty()) continue;
            Tag tag = ItemStackWithSlot.CODEC
                    .encodeStart(ops, new ItemStackWithSlot(i, nms))
                    .result()
                    .orElse(null);
            if (tag != null) {
                list.add(tag);
            }
        }
        root.put(key, list);
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION CONTEXT + NMS/BUKKIT BRIDGE
    // ═══════════════════════════════════════════════════════════════

    private static RegistryOps<Tag> ops() {
        return ((CraftServer) Bukkit.getServer())
                .getServer()
                .registryAccess()
                .createSerializationContext(NbtOps.INSTANCE);
    }

    private static ItemStack bukkitToNmsOrEmpty(org.bukkit.inventory.ItemStack bukkit) {
        if (bukkit == null || bukkit.getType() == Material.AIR) {
            return ItemStack.EMPTY;
        }
        return CraftItemStack.asNMSCopy(bukkit);
    }

    /**
     * NMS → Bukkit. {@code CraftItemStack.asBukkitCopy} became private in Leaf 26.2,
     * so it is invoked via reflection (same pattern as NotesGUI).
     */
    private static org.bukkit.inventory.ItemStack nmsToBukkit(ItemStack nms) {
        if (nms == null || nms.isEmpty()) return null;
        try {
            Method method = CraftItemStack.class.getDeclaredMethod("asBukkitCopy", ItemStack.class);
            method.setAccessible(true);
            return (org.bukkit.inventory.ItemStack) method.invoke(null, nms);
        } catch (Throwable e) {
            ConsoleLogger.warn("[PlayerDataIO] NMS→Bukkit conversion failed: " + e.getMessage());
            return null;
        }
    }
}
