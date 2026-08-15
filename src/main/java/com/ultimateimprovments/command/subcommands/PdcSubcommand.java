package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /ui pdc — PersistentDataContainer manager across multiple storage types.
 *
 * <p>Storage types (first argument) and their targets:</p>
 * <pre>
 *   /ui pdc item   &lt;player&gt;        &lt;ns:key&gt; &lt;op&gt; ...   — item in the player's main hand
 *   /ui pdc entity &lt;uuid&gt;          &lt;ns:key&gt; &lt;op&gt; ...   — entity by UUID
 *   /ui pdc player &lt;player&gt;        &lt;ns:key&gt; &lt;op&gt; ...   — player entity
 *   /ui pdc block  &lt;x&gt; &lt;y&gt; &lt;z&gt;    &lt;ns:key&gt; &lt;op&gt; ...   — block entity (TileState)
 *   /ui pdc chunk  &lt;x&gt; &lt;z&gt;         &lt;ns:key&gt; &lt;op&gt; ...   — chunk PDC
 *   /ui pdc world  &lt;world&gt;         &lt;ns:key&gt; &lt;op&gt; ...   — world-level PDC
 * </pre>
 *
 * <p>Operations: add &lt;type&gt; &lt;data&gt;, modify &lt;type&gt; &lt;data&gt;, remove,
 * list &lt;all|namespace|key&gt; [data], clear &lt;all|namespace|key&gt; [data],
 * container &lt;containerKey&gt; &lt;innerKey&gt; &lt;add|modify|remove&gt; &lt;type&gt; &lt;data&gt;,
 * container &lt;containerKey&gt; clear &lt;all|namespace|key&gt; [data].</p>
 *
 * <p>Supported PDC types: BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, STRING,
 * BYTE_ARRAY, INTEGER_ARRAY, LONG_ARRAY, TAG_CONTAINER, BOOLEAN.</p>
 *
 * <p>Note: there is NO {@code plugin} storage — Bukkit's {@code Plugin} is not a
 * {@link org.bukkit.persistence.PersistentDataHolder} (no PDC in the plugin API).</p>
 *
 * <p>Permission: {@code ui.command.pdc} (registered in {@code Permissions} — in code, not plugin.yml).</p>
 */
public final class PdcSubcommand implements SubCommand {

    private static final String PERMISSION = "ui.command.pdc";

    /** Storage names shown in tab-complete and usage. */
    private static final List<String> STORAGES = List.of("item", "entity", "player", "block", "chunk", "world");

    // ── Type names in tab-complete order ──
    private static final List<String> TYPES = List.of(
            "BYTE", "SHORT", "INTEGER", "LONG", "FLOAT", "DOUBLE", "STRING",
            "BYTE_ARRAY", "INTEGER_ARRAY", "LONG_ARRAY",
            "TAG_CONTAINER",
            "BOOLEAN");

    private static final List<String> KEY_ACTIONS = List.of("add", "modify", "remove");
    private static final List<String> LIST_MODES = List.of("all", "namespace", "key");

    /** Where the storage target arguments start (index in args) and how many there are. */
    private record StorageInfo(int targetStart, int targetCount) {}

    /** Resolved PDC holder: live container + how to persist changes + display name. */
    private record PdcTarget(PersistentDataContainer container, Runnable save, String name) {}

    @Override
    public String getName() {
        return "pdc";
    }

    // ============================================================
    // EXECUTE
    // ============================================================

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String storage = args[1].toLowerCase();
        StorageInfo info = storageInfo(storage);
        if (info == null) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Unknown storage: </red><yellow>" + args[1]
                    + "</yellow><gray>. Valid: item, entity, player, block, chunk, world.</gray>"));
            sendUsage(sender);
            return true;
        }

        if (args.length < info.targetStart() + info.targetCount() + 2) {
            sendUsage(sender);
            return true;
        }

        PdcTarget target = resolveTarget(sender, storage, args, info);
        if (target == null) return true;

        int keyStart = info.targetStart() + info.targetCount(); // index of the first ns:key / subcommand

        String sub = args[keyStart].toLowerCase();
        switch (sub) {
            case "container" -> containerPath(sender, target, args, keyStart);
            case "list" -> listPath(sender, target, args, keyStart);
            case "clear" -> clearPath(sender, target, args, keyStart);
            default -> keyPath(sender, target, args, keyStart);
        }
        return true;
    }

    // ── Storage resolution ──

    /** Returns the target-args layout for a storage name, or null if unknown. */
    private StorageInfo storageInfo(String storage) {
        return switch (storage) {
            case "item", "entity", "player", "world" -> new StorageInfo(2, 1);
            case "block" -> new StorageInfo(2, 3); // x y z
            case "chunk" -> new StorageInfo(2, 2); // x z
            default -> null;
        };
    }

    /** Resolves the storage target into a live PDC holder. Sends an error message and returns null on failure. */
    private PdcTarget resolveTarget(CommandSender sender, String storage, String[] args, StorageInfo info) {
        switch (storage) {
            case "item" -> {
                Player p = Bukkit.getPlayerExact(args[info.targetStart()]);
                if (p == null) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Player </red><yellow>" + args[info.targetStart()]
                            + "</yellow><red> is not online!</red>"));
                    return null;
                }
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item == null || item.getType().isAir()) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ </red><yellow>" + p.getName()
                            + "</yellow><red> has no item in their main hand!</red>"));
                    return null;
                }
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    meta = Bukkit.getItemFactory().getItemMeta(item.getType());
                    if (meta == null) {
                        sender.sendMessage(MessageUtil.parse("<red>❌ This item cannot hold PDC data.</red>"));
                        return null;
                    }
                }
                ItemMeta finalMeta = meta;
                return new PdcTarget(meta.getPersistentDataContainer(),
                        () -> p.getInventory().getItemInMainHand().setItemMeta(finalMeta),
                        "item of " + p.getName());
            }
            case "entity" -> {
                UUID uuid = parseUuid(sender, args[info.targetStart()]);
                if (uuid == null) return null;
                Entity e = Bukkit.getEntity(uuid);
                if (e == null) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ No loaded entity with UUID </red><yellow>" + uuid
                            + "</yellow><red>.</red>"));
                    return null;
                }
                return new PdcTarget(e.getPersistentDataContainer(), () -> { /* entity PDC is live */ },
                        "entity " + uuid);
            }
            case "player" -> {
                Player p = Bukkit.getPlayerExact(args[info.targetStart()]);
                if (p == null) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Player </red><yellow>" + args[info.targetStart()]
                            + "</yellow><red> is not online!</red>"));
                    return null;
                }
                return new PdcTarget(p.getPersistentDataContainer(), () -> { /* player PDC is live */ },
                        "player " + p.getName());
            }
            case "block" -> {
                World w = senderWorld(sender);
                int x = parseIntArg(sender, args, info.targetStart(), "X");
                int y = parseIntArg(sender, args, info.targetStart() + 1, "Y");
                int z = parseIntArg(sender, args, info.targetStart() + 2, "Z");
                if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) return null;
                BlockState state = w.getBlockAt(x, y, z).getState();
                if (!(state instanceof TileState ts)) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Block at </red><yellow>" + x + " " + y + " " + z
                            + "</yellow><red> is not a block entity (no PDC).</red>"));
                    return null;
                }
                return new PdcTarget(ts.getPersistentDataContainer(),
                        () -> ts.update(), "block " + x + " " + y + " " + z);
            }
            case "chunk" -> {
                World w = senderWorld(sender);
                int cx = parseIntArg(sender, args, info.targetStart(), "chunk X");
                int cz = parseIntArg(sender, args, info.targetStart() + 1, "chunk Z");
                if (cx == Integer.MIN_VALUE || cz == Integer.MIN_VALUE) return null;
                Chunk chunk = w.getChunkAt(cx, cz);
                return new PdcTarget(chunk.getPersistentDataContainer(), () -> { /* chunk PDC persists on save */ },
                        "chunk " + cx + " " + cz + " (" + w.getName() + ")");
            }
            case "world" -> {
                World w = Bukkit.getWorld(args[info.targetStart()]);
                if (w == null) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ World </red><yellow>" + args[info.targetStart()]
                            + "</yellow><red> not found!</red>"));
                    return null;
                }
                return new PdcTarget(w.getPersistentDataContainer(), () -> { /* world PDC persists on save */ },
                        "world " + w.getName());
            }
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Unknown storage: </red><yellow>" + storage
                        + "</yellow><gray>. Valid: item, entity, player, block, chunk, world.</gray>"));
                return null;
            }
        }
    }

    /** World of the sender (player's world, or the first world for console). */
    private World senderWorld(CommandSender sender) {
        if (sender instanceof Player p) return p.getWorld();
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    private UUID parseUuid(CommandSender sender, String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Invalid UUID: </red><yellow>" + raw + "</yellow>"));
            return null;
        }
    }

    /** Parses an integer argument; returns MIN_VALUE (and sends an error) on failure. */
    private int parseIntArg(CommandSender sender, String[] args, int index, String label) {
        String raw;
        try {
            raw = args[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Missing </red><aqua>" + label
                    + "</aqua><red> coordinate.</red>"));
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Invalid </red><aqua>" + label
                    + "</aqua><red> coordinate: </red><yellow>" + raw + "</yellow>"));
            return Integer.MIN_VALUE;
        }
    }

    // ── Direct key operations: <ns:key> add|modify|remove ... ──

    private void keyPath(CommandSender sender, PdcTarget target, String[] args, int keyStart) {
        if (args.length < keyStart + 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> <namespace:key> <add|modify|remove> [type] [data]</white>"));
            return;
        }

        NamespacedKey key = parseKey(sender, args[keyStart]);
        if (key == null) return;

        switch (args[keyStart + 1].toLowerCase()) {
            case "add" -> addValue(sender, target, key, args, keyStart + 2);
            case "modify" -> modifyValue(sender, target, key, args, keyStart + 2);
            case "remove" -> removeValue(sender, target, key);
            default -> sender.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + args[keyStart + 1]
                    + "</yellow><gray>. Use add, modify or remove.</gray>"));
        }
    }

    // ── Container operations ──

    private void containerPath(CommandSender sender, PdcTarget target, String[] args, int keyStart) {
        // /ui pdc <storage> <target...> container <containerKey> <innerKey> <add|modify|remove> [type] [data]
        // /ui pdc <storage> <target...> container <containerKey> clear <all|namespace|key> [data]
        if (args.length < keyStart + 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> container <containerKey> <innerKey|clear> ...</white>"));
            return;
        }

        NamespacedKey containerKey = parseKey(sender, args[keyStart + 1]);
        if (containerKey == null) return;

        // Read the existing container (or start empty for add)
        PersistentDataContainer container = target.container().get(containerKey, PersistentDataType.TAG_CONTAINER);

        String inner = args[keyStart + 2].toLowerCase();
        if (inner.equals("clear")) {
            // /ui pdc <storage> <target...> container <cKey> clear <all|namespace|key> [data]
            if (container == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Container </red><yellow>" + containerKey
                        + "</yellow><red> does not exist on </red><yellow>" + target.name() + "</yellow><red>.</red>"));
                return;
            }
            clearInContainer(sender, target, containerKey, container, args, keyStart + 3);
            return;
        }

        NamespacedKey innerKey = parseKey(sender, args[keyStart + 2]);
        if (innerKey == null) return;

        if (args.length < keyStart + 4) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> container <containerKey> <innerKey> <add|modify|remove> [type] [data]</white>"));
            return;
        }

        switch (args[keyStart + 3].toLowerCase()) {
            case "add" -> {
                if (container == null) {
                    container = target.container().getAdapterContext().newPersistentDataContainer();
                }
                if (container.has(innerKey)) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Key </red><yellow>" + innerKey
                            + "</yellow><red> already exists in the container — use modify.</red>"));
                    return;
                }
                Object value = parseData(sender, args, keyStart + 4, target.container().getAdapterContext());
                if (value == null) return;
                setTyped(container, innerKey, args[keyStart + 4].toUpperCase(), value);
                target.container().set(containerKey, PersistentDataType.TAG_CONTAINER, container);
                target.save().run();
                sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Added </white><aqua>" + args[keyStart + 4].toUpperCase()
                        + "</aqua> <white>to container </white><yellow>" + containerKey
                        + "</yellow><white> → </white><yellow>" + innerKey + "</yellow><white> on </white><yellow>"
                        + target.name() + "</yellow><white>.</white>"));
            }
            case "modify" -> {
                if (container == null || !container.has(innerKey)) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Key </red><yellow>" + innerKey
                            + "</yellow><red> not found in container — use add.</red>"));
                    return;
                }
                Object value = parseData(sender, args, keyStart + 4, target.container().getAdapterContext());
                if (value == null) return;
                setTyped(container, innerKey, args[keyStart + 4].toUpperCase(), value);
                target.container().set(containerKey, PersistentDataType.TAG_CONTAINER, container);
                target.save().run();
                sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Modified </white><aqua>" + args[keyStart + 4].toUpperCase()
                        + "</aqua> <white>in container </white><yellow>" + containerKey
                        + "</yellow><white> → </white><yellow>" + innerKey + "</yellow><white> on </white><yellow>"
                        + target.name() + "</yellow><white>.</white>"));
            }
            case "remove" -> {
                if (container == null || !container.has(innerKey)) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Key </red><yellow>" + innerKey
                            + "</yellow><red> not found in container.</red>"));
                    return;
                }
                container.remove(innerKey);
                target.container().set(containerKey, PersistentDataType.TAG_CONTAINER, container);
                target.save().run();
                sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Removed </white><yellow>" + innerKey
                        + "</yellow> <white>from container </white><yellow>" + containerKey
                        + "</yellow><white> on </white><yellow>" + target.name() + "</yellow><white>.</white>"));
            }
            default -> sender.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + args[keyStart + 3]
                    + "</yellow><gray>. Use add, modify or remove.</gray>"));
        }
    }

    // ── add / modify / remove on the top-level PDC ──

    private void addValue(CommandSender sender, PdcTarget target, NamespacedKey key, String[] args, int offset) {
        if (target.container().has(key)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Key </red><yellow>" + key
                    + "</yellow><red> already exists — use modify.</red>"));
            return;
        }
        Object value = parseData(sender, args, offset, target.container().getAdapterContext());
        if (value == null) return;
        setTyped(target.container(), key, args[offset].toUpperCase(), value);
        target.save().run();
        sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Added </white><aqua>" + args[offset].toUpperCase()
                + "</aqua> <white>key </white><yellow>" + key + "</yellow> <white>on </white><yellow>"
                + target.name() + "</yellow><white>.</white>"));
    }

    private void modifyValue(CommandSender sender, PdcTarget target, NamespacedKey key, String[] args, int offset) {
        if (!target.container().has(key)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Key </red><yellow>" + key
                    + "</yellow><red> does not exist — use add.</red>"));
            return;
        }
        Object value = parseData(sender, args, offset, target.container().getAdapterContext());
        if (value == null) return;
        setTyped(target.container(), key, args[offset].toUpperCase(), value);
        target.save().run();
        sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Modified key </white><yellow>" + key
                + "</yellow> <white>on </white><yellow>" + target.name() + "</yellow><white>.</white>"));
    }

    private void removeValue(CommandSender sender, PdcTarget target, NamespacedKey key) {
        if (!target.container().has(key)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Key </red><yellow>" + key
                    + "</yellow><red> does not exist on </red><yellow>" + target.name() + "</yellow><red>.</red>"));
            return;
        }
        target.container().remove(key);
        target.save().run();
        sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Removed key </white><yellow>" + key
                + "</yellow> <white>from </white><yellow>" + target.name() + "</yellow><white>.</white>"));
    }

    // ── clear inside a TAG_CONTAINER ──

    private void clearInContainer(CommandSender sender, PdcTarget target, NamespacedKey containerKey,
                                  PersistentDataContainer container, String[] args, int offset) {
        if (args.length < offset + 1) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> container <containerKey> clear <all|namespace|key> [data]</white>"));
            return;
        }

        String mode = args[offset].toLowerCase();
        switch (mode) {
            case "all" -> container.getKeys().forEach(container::remove);
            case "namespace" -> {
                if (args.length < offset + 2) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> container <containerKey> clear namespace <namespace></white>"));
                    return;
                }
                String ns = args[offset + 1];
                for (NamespacedKey k : new ArrayList<>(container.getKeys())) {
                    if (k.getNamespace().equalsIgnoreCase(ns)) container.remove(k);
                }
            }
            case "key" -> {
                if (args.length < offset + 2) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> container <containerKey> clear key <key></white>"));
                    return;
                }
                String keyName = args[offset + 1];
                for (NamespacedKey k : new ArrayList<>(container.getKeys())) {
                    if (k.getKey().equalsIgnoreCase(keyName)) container.remove(k);
                }
            }
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Unknown mode: </red><yellow>" + mode
                        + "</yellow><gray>. Use all, namespace or key.</gray>"));
                return;
            }
        }

        // clear acts ONLY on the tags inside the container — the container key itself
        // stays on the storage (even if now empty), the outside PDC is untouched.
        target.container().set(containerKey, PersistentDataType.TAG_CONTAINER, container);
        target.save().run();
        sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Cleared container </white><yellow>" + containerKey
                + "</yellow> <white>on </white><yellow>" + target.name() + "</yellow><white>.</white>"));
    }

    // ── list ──

    private void listPath(CommandSender sender, PdcTarget target, String[] args, int keyStart) {
        if (args.length < keyStart + 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> list <all|namespace|key> [data]</white>"));
            return;
        }

        String mode = args[keyStart + 1].toLowerCase();
        Set<NamespacedKey> keys = target.container().getKeys();

        sender.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        sender.sendMessage("§8┃  §6✦ §fPDC of §e" + target.name());

        switch (mode) {
            case "all" -> {
                if (keys.isEmpty()) {
                    sender.sendMessage("§8┃  §7No PDC keys found.");
                } else {
                    for (NamespacedKey k : keys) {
                        sender.sendMessage("§8┃  §a" + k + " §8→ " + describeValue(target.container(), k));
                    }
                }
            }
            case "namespace" -> {
                if (args.length < keyStart + 3) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> list namespace <namespace></white>"));
                    sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
                    return;
                }
                String ns = args[keyStart + 2];
                boolean any = false;
                for (NamespacedKey k : keys) {
                    if (k.getNamespace().equalsIgnoreCase(ns)) {
                        sender.sendMessage("§8┃  §a" + k + " §8→ " + describeValue(target.container(), k));
                        any = true;
                    }
                }
                if (!any) {
                    sender.sendMessage("§8┃  §7No keys in namespace §e" + ns + "§7.");
                }
            }
            case "key" -> {
                if (args.length < keyStart + 3) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> list key <key></white>"));
                    sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
                    return;
                }
                String keyName = args[keyStart + 2];
                boolean any = false;
                for (NamespacedKey k : keys) {
                    if (k.getKey().equalsIgnoreCase(keyName)) {
                        sender.sendMessage("§8┃  §a" + k + " §8→ " + describeValue(target.container(), k));
                        any = true;
                    }
                }
                if (!any) {
                    sender.sendMessage("§8┃  §7No keys named §e" + keyName + "§7.");
                }
            }
            default -> sender.sendMessage(MessageUtil.parse("<red>❌ Unknown mode: </red><yellow>" + mode
                    + "</yellow><gray>. Use all, namespace or key.</gray>"));
        }
        sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
    }

    // ── clear (top level) ──

    private void clearPath(CommandSender sender, PdcTarget target, String[] args, int keyStart) {
        if (args.length < keyStart + 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> clear <all|namespace|key> [data]</white>"));
            return;
        }

        String mode = args[keyStart + 1].toLowerCase();
        switch (mode) {
            case "all" -> target.container().getKeys().forEach(target.container()::remove);
            case "namespace" -> {
                if (args.length < keyStart + 3) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> clear namespace <namespace></white>"));
                    return;
                }
                String ns = args[keyStart + 2];
                for (NamespacedKey k : new ArrayList<>(target.container().getKeys())) {
                    if (k.getNamespace().equalsIgnoreCase(ns)) target.container().remove(k);
                }
            }
            case "key" -> {
                if (args.length < keyStart + 3) {
                    sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui pdc <storage> <target...> clear key <key></white>"));
                    return;
                }
                String keyName = args[keyStart + 2];
                for (NamespacedKey k : new ArrayList<>(target.container().getKeys())) {
                    if (k.getKey().equalsIgnoreCase(keyName)) target.container().remove(k);
                }
            }
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Unknown mode: </red><yellow>" + mode
                        + "</yellow><gray>. Use all, namespace or key.</gray>"));
                return;
            }
        }

        target.save().run();
        sender.sendMessage(MessageUtil.parse("<green>✔</green> <white>Cleared PDC (</white><aqua>" + mode
                + "</aqua><white>) on </white><yellow>" + target.name() + "</yellow><white>.</white>"));
    }

    // ============================================================
    // HELPERS
    // ============================================================

    /** Parses "namespace:key" (plain key → minecraft namespace). Returns null + error message on failure. */
    private NamespacedKey parseKey(CommandSender sender, String raw) {
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Invalid key: </red><yellow>" + raw
                    + "</yellow><gray>. Use format </gray><white>namespace:key</white><gray>.</gray>"));
            return null;
        }
        return key;
    }

    /**
     * Parses the type + data arguments at {@code offset} ({@code args[offset]} = type, {@code args[offset+1]} = data).
     * Returns the parsed value, or null (after sending the type-error message).
     */
    private Object parseData(CommandSender sender, String[] args, int offset, PersistentDataAdapterContext ctx) {
        if (args.length < offset + 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>... <type> <data></white>"));
            return null;
        }
        String type = args[offset].toUpperCase();
        String data = args[offset + 1];

        try {
            return parseValue(type, data, ctx);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Invalid </red><aqua>" + type
                    + "</aqua><red> data: </red><yellow>" + data + "</yellow><red>.</red> <gray>" + e.getMessage() + "</gray>"));
            return null;
        }
    }

    /** Converts a raw string into a PDC value of the requested type. Throws on type mismatch. */
    private Object parseValue(String type, String data, PersistentDataAdapterContext ctx) {
        return switch (type) {
            case "BYTE" -> Byte.parseByte(data.trim());
            case "SHORT" -> Short.parseShort(data.trim());
            case "INTEGER" -> Integer.parseInt(data.trim());
            case "LONG" -> Long.parseLong(data.trim());
            case "FLOAT" -> Float.parseFloat(data.trim());
            case "DOUBLE" -> Double.parseDouble(data.trim());
            case "STRING" -> data;
            case "BOOLEAN" -> {
                if (data.equalsIgnoreCase("true")) yield true;
                if (data.equalsIgnoreCase("false")) yield false;
                throw new IllegalArgumentException("Expected true or false.");
            }
            case "BYTE_ARRAY" -> toByteArray(data);
            case "INTEGER_ARRAY" -> toIntArray(data);
            case "LONG_ARRAY" -> toLongArray(data);
            case "TAG_CONTAINER" -> ctx.newPersistentDataContainer(); // data is ignored — the container starts empty
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    private byte[] toByteArray(String data) {
        String[] parts = data.split("[,\\s]+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Byte.parseByte(parts[i].trim());
        }
        return out;
    }

    private int[] toIntArray(String data) {
        String[] parts = data.split("[,\\s]+");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }

    private long[] toLongArray(String data) {
        String[] parts = data.split("[,\\s]+");
        long[] out = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Long.parseLong(parts[i].trim());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private <T, Z> void setTyped(PersistentDataContainer pdc, NamespacedKey key, String type, Object value) {
        switch (type) {
            case "BYTE" -> pdc.set(key, PersistentDataType.BYTE, (Byte) value);
            case "SHORT" -> pdc.set(key, PersistentDataType.SHORT, (Short) value);
            case "INTEGER" -> pdc.set(key, PersistentDataType.INTEGER, (Integer) value);
            case "LONG" -> pdc.set(key, PersistentDataType.LONG, (Long) value);
            case "FLOAT" -> pdc.set(key, PersistentDataType.FLOAT, (Float) value);
            case "DOUBLE" -> pdc.set(key, PersistentDataType.DOUBLE, (Double) value);
            case "STRING" -> pdc.set(key, PersistentDataType.STRING, (String) value);
            case "BOOLEAN" -> pdc.set(key, PersistentDataType.BOOLEAN, (Boolean) value);
            case "BYTE_ARRAY" -> pdc.set(key, PersistentDataType.BYTE_ARRAY, (byte[]) value);
            case "INTEGER_ARRAY" -> pdc.set(key, PersistentDataType.INTEGER_ARRAY, (int[]) value);
            case "LONG_ARRAY" -> pdc.set(key, PersistentDataType.LONG_ARRAY, (long[]) value);
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    /** Human-readable description of a PDC value: "TYPE: value". */
    private String describeValue(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.BYTE)) return "§bBYTE§8: §f" + pdc.get(key, PersistentDataType.BYTE);
        if (pdc.has(key, PersistentDataType.SHORT)) return "§bSHORT§8: §f" + pdc.get(key, PersistentDataType.SHORT);
        if (pdc.has(key, PersistentDataType.INTEGER)) return "§bINTEGER§8: §f" + pdc.get(key, PersistentDataType.INTEGER);
        if (pdc.has(key, PersistentDataType.LONG)) return "§bLONG§8: §f" + pdc.get(key, PersistentDataType.LONG);
        if (pdc.has(key, PersistentDataType.FLOAT)) return "§bFLOAT§8: §f" + pdc.get(key, PersistentDataType.FLOAT);
        if (pdc.has(key, PersistentDataType.DOUBLE)) return "§bDOUBLE§8: §f" + pdc.get(key, PersistentDataType.DOUBLE);
        if (pdc.has(key, PersistentDataType.STRING)) return "§bSTRING§8: §f\"" + pdc.get(key, PersistentDataType.STRING) + "\"";
        if (pdc.has(key, PersistentDataType.BOOLEAN)) return "§bBOOLEAN§8: §f" + pdc.get(key, PersistentDataType.BOOLEAN);
        if (pdc.has(key, PersistentDataType.BYTE_ARRAY)) return "§bBYTE_ARRAY§8: §f" + Arrays.toString(pdc.get(key, PersistentDataType.BYTE_ARRAY));
        if (pdc.has(key, PersistentDataType.INTEGER_ARRAY)) return "§bINTEGER_ARRAY§8: §f" + Arrays.toString(pdc.get(key, PersistentDataType.INTEGER_ARRAY));
        if (pdc.has(key, PersistentDataType.LONG_ARRAY)) return "§bLONG_ARRAY§8: §f" + Arrays.toString(pdc.get(key, PersistentDataType.LONG_ARRAY));
        if (pdc.has(key, PersistentDataType.TAG_CONTAINER)) {
            PersistentDataContainer inner = pdc.get(key, PersistentDataType.TAG_CONTAINER);
            return "§bTAG_CONTAINER§8: §f{" + inner.getKeys().size() + " key(s)}";
        }
        return "§7unknown type";
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(
                "<yellow>Usage:</yellow>\n"
                + "<white>/ui pdc <storage> <target...> <namespace:key> add <type> <data></white>\n"
                + "<white>/ui pdc <storage> <target...> <namespace:key> modify <type> <data></white>\n"
                + "<white>/ui pdc <storage> <target...> <namespace:key> remove</white>\n"
                + "<white>/ui pdc <storage> <target...> list <all|namespace|key> [data]</white>\n"
                + "<white>/ui pdc <storage> <target...> clear <all|namespace|key> [data]</white>\n"
                + "<white>/ui pdc <storage> <target...> container <containerKey> <innerKey> <add|modify|remove> <type> <data></white>\n"
                + "<white>/ui pdc <storage> <target...> container <containerKey> clear <all|namespace|key> [data]</white>\n"
                + "<gray>Storages: item &lt;player&gt; | entity &lt;uuid&gt; | player &lt;player&gt; | "
                + "block &lt;x y z&gt; | chunk &lt;x z&gt; | world &lt;world&gt;</gray>\n"
                + "<gray>Types: BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, STRING, BOOLEAN, "
                + "BYTE_ARRAY, INTEGER_ARRAY, LONG_ARRAY, TAG_CONTAINER</gray>"));
    }

    // ============================================================
    // TAB COMPLETION — shows everything: storages, players, worlds,
    // the entity/block you're looking at, and every namespace:key
    // present on the resolved storage.
    // ============================================================

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();

        // <storage>
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (String s : STORAGES) {
                if (s.startsWith(partial)) result.add(s);
            }
            return result;
        }

        StorageInfo info = storageInfo(args[1].toLowerCase());
        if (info == null) {
            return result; // unknown storage
        }
        String storage = args[1].toLowerCase();

        // Storage target arguments
        if (args.length <= info.targetStart() + info.targetCount()) {
            return targetTab(sender, storage, args, info);
        }

        int keyStart = info.targetStart() + info.targetCount();

        // Resolve the storage to suggest its real namespace:key entries
        PersistentDataContainer pdc = tryResolvePdc(sender, storage, args, info);

        // The word right after the target: subcommand or an existing ns:key
        if (args.length == keyStart + 1) {
            String partial = args[keyStart].toLowerCase();
            for (String s : List.of("container", "list", "clear")) {
                if (s.startsWith(partial)) result.add(s);
            }
            if (pdc != null) {
                for (NamespacedKey k : pdc.getKeys()) {
                    if (k.toString().toLowerCase().startsWith(partial)) result.add(k.toString());
                }
            }
            return result;
        }

        // ── container path ──
        if (args[keyStart].equalsIgnoreCase("container")) {
            return containerTab(pdc, args, keyStart);
        }

        // ── list / clear path ──
        if (args[keyStart].equalsIgnoreCase("list") || args[keyStart].equalsIgnoreCase("clear")) {
            String partial = args[args.length - 1].toLowerCase();
            if (args.length == keyStart + 2) {
                for (String m : LIST_MODES) {
                    if (m.startsWith(partial)) result.add(m);
                }
                return result;
            }
            // suggest namespaces (for namespace mode) or keys (for key mode)
            if (args.length == keyStart + 3 && pdc != null) {
                String mode = args[keyStart + 1].toLowerCase();
                Set<String> names = new LinkedHashSet<>();
                for (NamespacedKey k : pdc.getKeys()) {
                    names.add(mode.equals("key") ? k.getKey() : k.getNamespace());
                }
                for (String n : names) {
                    if (n.toLowerCase().startsWith(partial)) result.add(n);
                }
            }
            return result;
        }

        // ── direct key path: <ns:key> add|modify|remove [type] [data] ──
        if (args.length == keyStart + 2) {
            String partial = args[keyStart + 1].toLowerCase();
            for (String a : KEY_ACTIONS) {
                if (a.startsWith(partial)) result.add(a);
            }
            return result;
        }
        if (args.length == keyStart + 3) {
            // the type
            String partial = args[keyStart + 2].toLowerCase();
            for (String t : TYPES) {
                if (t.toLowerCase().startsWith(partial)) result.add(t);
            }
            return result;
        }
        if (args.length == keyStart + 4) {
            // the data — suggest plausible values per type
            String type = args[keyStart + 2].toUpperCase();
            String partial = args[keyStart + 3].toLowerCase();
            for (String d : dataSuggestions(type)) {
                if (d.toLowerCase().startsWith(partial)) result.add(d);
            }
        }
        return result;
    }

    /** Tab-complete suggestions for the storage target arguments. */
    private List<String> targetTab(CommandSender sender, String storage, String[] args, StorageInfo info) {
        List<String> result = new ArrayList<>();
        String partial = args[args.length - 1].toLowerCase();

        switch (storage) {
            case "item", "player" -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) result.add(p.getName());
                }
            }
            case "entity" -> {
                // The entity you're looking at + nearby loaded entities
                if (sender instanceof LivingEntity le) {
                    Entity target = le.getTargetEntity(10);
                    if (target != null && target.getUniqueId().toString().toLowerCase().startsWith(partial)) {
                        result.add(target.getUniqueId().toString());
                    }
                }
                if (sender instanceof Player p) {
                    for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 32, 32, 32)) {
                        if (result.size() > 40) break;
                        String uid = e.getUniqueId().toString();
                        if (uid.toLowerCase().startsWith(partial)) result.add(uid);
                    }
                }
            }
            case "block" -> {
                // The block you're looking at (x y z)
                Block block = sender instanceof LivingEntity le
                        ? le.getTargetBlockExact(10) : null;
                if (block == null) return result;
                // args[targetStart] = x, [targetStart+1] = y, [targetStart+2] = z
                int i = args.length - info.targetStart() - 1;
                int coord = switch (i) {
                    case 0 -> block.getX();
                    case 1 -> block.getY();
                    case 2 -> block.getZ();
                    default -> Integer.MIN_VALUE;
                };
                if (coord != Integer.MIN_VALUE && String.valueOf(coord).startsWith(partial)) {
                    result.add(String.valueOf(coord));
                }
            }
            case "chunk" -> {
                // The chunk you're looking at (chunk x z of the target block)
                Block block = sender instanceof LivingEntity le
                        ? le.getTargetBlockExact(10) : null;
                if (block == null) return result;
                // args[targetStart] = chunk x, [targetStart+1] = chunk z
                int i = args.length - info.targetStart() - 1;
                int coord = switch (i) {
                    case 0 -> block.getChunk().getX();
                    case 1 -> block.getChunk().getZ();
                    default -> Integer.MIN_VALUE;
                };
                if (coord != Integer.MIN_VALUE && String.valueOf(coord).startsWith(partial)) {
                    result.add(String.valueOf(coord));
                }
            }
            case "world" -> {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getName().toLowerCase().startsWith(partial)) result.add(w.getName());
                }
            }
            default -> { }
        }
        return result;
    }

    /** Resolves the storage PDC for tab-complete (no error messages). */
    private PersistentDataContainer tryResolvePdc(CommandSender sender, String storage, String[] args, StorageInfo info) {
        try {
            switch (storage) {
                case "item" -> {
                    Player p = Bukkit.getPlayerExact(args[info.targetStart()]);
                    if (p == null) return null;
                    ItemStack item = p.getInventory().getItemInMainHand();
                    if (item == null || item.hasItemMeta() == false || item.getItemMeta() == null) return null;
                    return item.getItemMeta().getPersistentDataContainer();
                }
                case "entity" -> {
                    UUID uuid = UUID.fromString(args[info.targetStart()].trim());
                    Entity e = Bukkit.getEntity(uuid);
                    return e == null ? null : e.getPersistentDataContainer();
                }
                case "player" -> {
                    Player p = Bukkit.getPlayerExact(args[info.targetStart()]);
                    return p == null ? null : p.getPersistentDataContainer();
                }
                case "block" -> {
                    World w = senderWorld(sender);
                    int x = Integer.parseInt(args[info.targetStart()]);
                    int y = Integer.parseInt(args[info.targetStart() + 1]);
                    int z = Integer.parseInt(args[info.targetStart() + 2]);
                    BlockState state = w.getBlockAt(x, y, z).getState();
                    return state instanceof TileState ts ? ts.getPersistentDataContainer() : null;
                }
                case "chunk" -> {
                    World w = senderWorld(sender);
                    int cx = Integer.parseInt(args[info.targetStart()]);
                    int cz = Integer.parseInt(args[info.targetStart() + 1]);
                    return w.getChunkAt(cx, cz).getPersistentDataContainer();
                }
                case "world" -> {
                    World w = Bukkit.getWorld(args[info.targetStart()]);
                    return w == null ? null : w.getPersistentDataContainer();
                }
                default -> {
                    return null;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> containerTab(PersistentDataContainer pdc, String[] args, int keyStart) {
        List<String> result = new ArrayList<>();

        if (args.length == keyStart + 2) {
            // container key — suggest existing TAG_CONTAINER keys
            String partial = args[keyStart + 1].toLowerCase();
            if (pdc != null) {
                for (NamespacedKey k : pdc.getKeys()) {
                    if (pdc.has(k, PersistentDataType.TAG_CONTAINER)
                            && k.toString().toLowerCase().startsWith(partial)) {
                        result.add(k.toString());
                    }
                }
            }
            return result;
        }

        // Resolve the container to suggest inner keys
        PersistentDataContainer container = null;
        if (pdc != null) {
            NamespacedKey ck = NamespacedKey.fromString(args[keyStart + 1]);
            if (ck != null) container = pdc.get(ck, PersistentDataType.TAG_CONTAINER);
        }

        if (args.length == keyStart + 3) {
            String partial = args[keyStart + 2].toLowerCase();
            // inner key — existing keys inside the container, plus "clear"
            if ("clear".startsWith(partial)) result.add("clear");
            if (container != null) {
                for (NamespacedKey k : container.getKeys()) {
                    if (k.toString().toLowerCase().startsWith(partial)) result.add(k.toString());
                }
            }
            return result;
        }

        if (args.length == keyStart + 4) {
            // inner action: add/modify/remove
            String partial = args[keyStart + 3].toLowerCase();
            for (String a : KEY_ACTIONS) {
                if (a.startsWith(partial)) result.add(a);
            }
            return result;
        }

        if (args.length == keyStart + 5) {
            String partial = args[keyStart + 4].toLowerCase();
            for (String t : TYPES) {
                if (t.toLowerCase().startsWith(partial)) result.add(t);
            }
            return result;
        }

        if (args.length == keyStart + 6) {
            String type = args[keyStart + 4].toUpperCase();
            String partial = args[keyStart + 5].toLowerCase();
            for (String d : dataSuggestions(type)) {
                if (d.toLowerCase().startsWith(partial)) result.add(d);
            }
        }

        return result;
    }

    /** Plausible example values for tab-completing the data argument. */
    private List<String> dataSuggestions(String type) {
        return switch (type) {
            case "BYTE" -> List.of("0", "1", "2", "5", "10", "100", "127");
            case "SHORT", "INTEGER" -> List.of("0", "1", "5", "10", "100", "1000", "255");
            case "LONG" -> List.of("0", "1", "100", "1000000");
            case "FLOAT", "DOUBLE" -> List.of("0.5", "1.0", "2.5", "10.0");
            case "BOOLEAN" -> List.of("true", "false");
            case "BYTE_ARRAY" -> List.of("1,2,3");
            case "INTEGER_ARRAY", "LONG_ARRAY" -> List.of("1,2,3");
            default -> List.of();
        };
    }
}
