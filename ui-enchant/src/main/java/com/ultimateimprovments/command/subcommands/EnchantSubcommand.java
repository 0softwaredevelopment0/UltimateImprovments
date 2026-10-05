package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;

import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.Registries;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * /ui enchant — advanced enchantment manager.
 * <p>
 * Supports ALL vanilla enchantments (via {@link Registry#ENCHANTMENT})
 * plus custom ones, addressed as {@code namespace:name} — "minecraft:mending",
 * "ui:aoe" (bare names still resolve for convenience).
 * <pre>
 *   /ui enchant give <enchantment> <level> [player] [slot]
 *   /ui enchant confirm
 *   /ui enchant cancel
 *   /ui enchant take <enchantment> <level> [player] [slot]
 *   /ui enchant check <player> [page]
 * </pre>
 * There is no hard level cap, but a give above {@code enchant.max_level}
 * (default 10) requires {@code /ui enchant confirm} within 60 seconds
 * (or {@code /ui enchant cancel} to drop it). A bare name registered in
 * several namespaces fails with error 019 — use {@code namespace:name}.
 * Slots: mainhand, offhand, bothhand, cursor, hotbar, armor, inventory, all.
 * Settings are in config.yml → {@code enchant:}.
 */
public final class EnchantSubcommand {

    /** How many rows to show on one page of /ui enchant check. */
    private static final int PER_PAGE = 5;

    /** Error 019: a bare enchantment name matches several namespaces (use namespace:name). */
    private static final int ERR_AMBIGUOUS_ENCHANT = 19;

    private EnchantSubcommand() {}

    // =========================
    // CONFIG
    // =========================

    private static boolean isEnabled() {
        return Main.getInstance().getConfig().getBoolean("enchant.enabled", true);
    }

    /**
     * The confirmation threshold from {@code enchant.max_level} (default 10).
     * NOT a hard cap — a give above this level asks for {@code /ui enchant confirm}.
     */
    private static int getConfirmThreshold() {
        return Math.max(1, Main.getInstance().getConfig().getInt("enchant.max_level", 10));
    }

    private static String getPermission() {
        return Main.getInstance().getConfig().getString("enchant.permission", "ui.command.enchant");
    }

    private static boolean isCustomEnchant(String name) {
        List<String> customs = Main.getInstance().getConfig().getStringList("enchant.custom_enchantments");
        for (String c : customs) {
            if (c != null && c.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    // =========================
    // RESOLVED ENCHANTMENT
    // =========================

    /** A resolved enchantment: either vanilla or custom (customName). */
    private record ResolvedEnchant(String customName, Enchantment vanilla) {
        boolean isCustom() { return customName != null; }
        String displayName() { return isCustom() ? "ui:" + customName : vanilla.getKey().toString(); }
    }

    /** Outcome of resolving an enchantment argument. */
    private record ResolveResult(ResolvedEnchant ench, java.util.List<String> ambiguousIds) {
        static ResolveResult of(ResolvedEnchant ench) { return new ResolveResult(ench, null); }
        static ResolveResult ambiguous(java.util.List<String> ids) { return new ResolveResult(null, ids); }
        boolean isAmbiguous() { return ambiguousIds != null; }
    }

    /**
     * Looks up an enchantment by name (custom or vanilla).
     * <p>
     * An explicit {@code namespace:name} input resolves only through that exact
     * namespace. A BARE name that matches entries in SEVERAL namespaces
     * (e.g. "aoe" when both ui:aoe and test:aoe are registered) returns an
     * AMBIGUOUS result — the caller reports error 019 and asks for an
     * explicit {@code namespace:name}.
     *
     * @return ResolveResult; {@code ench} is null when not found
     */
    private static ResolveResult resolveEnchant(String input) {
        if (input == null) return ResolveResult.of(null);
        String norm = input.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        int colon = norm.indexOf(':');
        String key = colon >= 0 ? norm.substring(colon + 1) : norm;

        // ─── Explicit namespaced input: "minecraft:mending", "ui:aoe", "test:ench" ───
        if (colon >= 0) {
            // Our custom enchantments take the modular path (setLevel with the
            // PDC mirror, tool check) — but only when ui: is typed explicitly
            if (norm.startsWith("ui:") && isCustomEnchant(key)) {
                return ResolveResult.of(new ResolvedEnchant(key, null));
            }
            try {
                NamespacedKey exact = NamespacedKey.fromString(norm);
                if (exact != null) {
                    Enchantment ench = Registries.enchantment().get(exact);
                    if (ench != null) return ResolveResult.of(new ResolvedEnchant(null, ench));
                }
            } catch (IllegalArgumentException ignored) {
                // invalid NamespacedKey
            }
            return ResolveResult.of(null); // explicit namespace → no bare-name fallbacks
        }

        // ─── Bare name ───

        // Every registry entry with this short key, across ALL namespaces
        List<Enchantment> shortKeyMatches = new ArrayList<>();
        for (Enchantment e : Registries.enchantment()) {
            if (e.getKey().getKey().equalsIgnoreCase(key)) shortKeyMatches.add(e);
        }
        Set<String> namespaces = new java.util.LinkedHashSet<>();
        for (Enchantment e : shortKeyMatches) namespaces.add(e.getKey().getNamespace());
        // A config custom counts as ui: even when the datapack is down
        // (nothing in the registry yet) — so the ambiguity check still applies
        if (isCustomEnchant(key)) namespaces.add("ui");

        if (namespaces.size() > 1) {
            Set<String> ids = new java.util.LinkedHashSet<>();
            for (Enchantment e : shortKeyMatches) ids.add(e.getKey().toString());
            if (isCustomEnchant(key)) ids.add("ui:" + key);
            List<String> sorted = new ArrayList<>(ids);
            sorted.sort(String::compareTo);
            return ResolveResult.ambiguous(sorted);
        }

        // Exactly one namespace → resolve it
        if (shortKeyMatches.size() == 1) {
            Enchantment ench = shortKeyMatches.get(0);
            if (ench.getKey().getNamespace().equals("ui") && isCustomEnchant(key)) {
                // modular path with the PDC mirror + per-enchant MAX_LEVEL
                return ResolveResult.of(new ResolvedEnchant(key, null));
            }
            return ResolveResult.of(new ResolvedEnchant(null, ench));
        }

        // Nothing in the registry → legacy fallbacks
        if (isCustomEnchant(key)) {
            return ResolveResult.of(new ResolvedEnchant(key, null)); // datapack down
        }
        try {
            Enchantment ench = Registries.enchantment().get(NamespacedKey.minecraft(key));
            if (ench != null) return ResolveResult.of(new ResolvedEnchant(null, ench));
        } catch (IllegalArgumentException ignored) {
            // invalid NamespacedKey
        }
        return ResolveResult.of(null);
    }

    /**
     * All enchantment ids ({@code namespace:name}) for tab-complete.
     * <p>
     * Built from THREE sources so nothing is ever missed:
     * <ol>
     *   <li>{@link Registry#ENCHANTMENT} — all vanilla enchantments + enchantments
     *       registered by other plugins (full key, e.g. "minecraft:sharpness");</li>
     *   <li>The bundled UI-Datapack {@code data/ui/enchantment/*.json} files — the custom
     *       enchantments (ui:aoe, ui:autosmelt, ...) even if the registry does not
     *       expose them in iteration (data-driven registries often don't);</li>
     *   <li>{@code enchant.custom_enchantments} from the config (bare names
     *       get the {@code ui:} prefix).</li>
     * </ol>
     */
    private static List<String> allEnchantNames() {
        Set<String> names = new java.util.LinkedHashSet<>();

        // 1. Registry — vanilla + other plugins
        for (Enchantment ench : Registries.enchantment()) {
            names.add(ench.getKey().toString());
        }

        // 2. UI-Datapack enchantments — read from the bundled datapack files
        for (String uiEnchant : datapackEnchantNames()) {
            names.add("ui:" + uiEnchant);
        }

        // 3. Config custom enchantments (bare names → ui: namespace)
        List<String> customs = Main.getInstance().getConfig().getStringList("enchant.custom_enchantments");
        for (String c : customs) {
            if (c != null && !c.isEmpty()) {
                String n = c.toLowerCase(java.util.Locale.ROOT);
                names.add(n.indexOf(':') >= 0 ? n : "ui:" + n);
            }
        }
        return new ArrayList<>(names);
    }

    /** Cached list of UI-Datapack enchantment ids (file names of data/ui/enchantment/*.json). */
    private static volatile List<String> cachedDatapackEnchants = null;

    /**
     * Reads {@code datapacks/UI-Datapack/data/ui/enchantment/*.json} from the plugin jar
     * and returns their ids (file names without the .json extension).
     */
    private static List<String> datapackEnchantNames() {
        List<String> cached = cachedDatapackEnchants;
        if (cached != null) return cached;

        List<String> result = new ArrayList<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(Main.getInstance().getPluginFile())) {
            var entries = zip.entries();
            String prefix = "datapacks/UI-Datapack/data/ui/enchantment/";
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.startsWith(prefix) || !name.endsWith(".json")) continue;
                String id = name.substring(prefix.length(), name.length() - ".json".length());
                result.add(id);
            }
        } catch (Exception e) {
            // datapack not reachable — registry + config still cover the list
        }
        result.sort(String::compareTo);
        cachedDatapackEnchants = result;
        return result;
    }

    // =========================
    // SLOTS
    // =========================

    /** An inventory slot: name + getter + setter. */
    private static final class Slot {
        final String name;
        final Supplier<ItemStack> getter;
        final Consumer<ItemStack> setter;

        Slot(String name, Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
            this.name = name;
            this.getter = getter;
            this.setter = setter;
        }

        ItemStack get() {
            return getter.get();
        }

        void set(ItemStack item) {
            setter.accept(item);
        }
    }

    private static Slot simple(String name, Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        return new Slot(name, getter, setter);
    }

    /**
     * Collects the slots for the given target argument.
     */
    private static List<Slot> collectSlots(Player player, String target) {
        PlayerInventory inv = player.getInventory();
        List<Slot> slots = new ArrayList<>();

        switch (target) {
            case "mainhand" -> slots.add(simple("Main Hand", inv::getItemInMainHand, inv::setItemInMainHand));
            case "offhand" -> slots.add(simple("Off Hand", inv::getItemInOffHand, inv::setItemInOffHand));
            case "bothhand" -> {
                slots.add(simple("Main Hand", inv::getItemInMainHand, inv::setItemInMainHand));
                slots.add(simple("Off Hand", inv::getItemInOffHand, inv::setItemInOffHand));
            }
            case "cursor" -> slots.add(simple("Cursor",
                    () -> player.getOpenInventory().getCursor(),
                    item -> player.getOpenInventory().setCursor(item)));
            case "hotbar" -> {
                for (int i = 0; i < 9; i++) {
                    int idx = i;
                    slots.add(simple("Hotbar " + (i + 1),
                            () -> inv.getItem(idx), item -> inv.setItem(idx, item)));
                }
            }
            case "armor" -> {
                ItemStack[] armor = inv.getArmorContents();
                String[] names = {"Boots", "Leggings", "Chestplate", "Helmet"};
                for (int i = 0; i < 4 && i < armor.length; i++) {
                    int idx = i;
                    slots.add(simple(names[idx],
                            () -> armor[idx],
                            item -> {
                                armor[idx] = item;
                                inv.setArmorContents(armor);
                            }));
                }
            }
            case "inventory" -> {
                for (int i = 9; i < 36; i++) {
                    int idx = i;
                    slots.add(simple("Inventory " + (i - 8),
                            () -> inv.getItem(idx), item -> inv.setItem(idx, item)));
                }
            }
            case "all" -> {
                slots.addAll(collectSlots(player, "mainhand"));
                slots.addAll(collectSlots(player, "offhand"));
                slots.addAll(collectSlots(player, "armor"));
                slots.addAll(collectSlots(player, "hotbar"));
                slots.addAll(collectSlots(player, "inventory"));
                slots.addAll(collectSlots(player, "cursor"));
            }
            default -> { /* invalid slot — handled earlier */ }
        }
        return slots;
    }

    private static boolean isValidTarget(String target) {
        return switch (target) {
            case "mainhand", "offhand", "bothhand", "cursor", "hotbar", "armor", "inventory", "all" -> true;
            default -> false;
        };
    }

    private static List<String> targetNames() {
        return List.of("mainhand", "offhand", "bothhand", "cursor", "hotbar", "armor", "inventory", "all");
    }

    // =========================
    // EXECUTE
    // =========================

    public static boolean execute(CommandSender sender, String[] args) {
        if (!isEnabled()) {
            CommandErrors.moduleDisabled(sender, "enchant");
            return true;
        }

        if (!sender.hasPermission(getPermission())) {
            CommandErrors.noPermission(sender, getPermission());
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "give" -> give(sender, args);
            case "take" -> take(sender, args);
            case "confirm" -> confirm(sender);
            case "cancel" -> cancel(sender);
            case "check" -> check(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse(MessagesManager.getString("enchant.usage",
                "<yellow>Usage:</yellow>\n"
                + "<white>/ui enchant give <enchantment> <level> [player] [slot]</white>\n"
                + "<white>/ui enchant confirm</white> <gray>- apply a give above the safe level</gray>\n"
                + "<white>/ui enchant cancel</white> <gray>- drop a pending give</gray>\n"
                + "<white>/ui enchant take <enchantment> <level> [player] [slot]</white>\n"
                + "<white>/ui enchant check <player> [page]</white>\n"
                + "<gray>player/slot optional: sender + mainhand. Slots: mainhand, offhand, bothhand, cursor, hotbar, armor, inventory, all</gray>")));
    }

    // =========================
    // GIVE / TAKE
    // =========================

    private static boolean give(CommandSender sender, String[] args) {
        return apply(sender, args, true);
    }

    private static boolean take(CommandSender sender, String[] args) {
        return apply(sender, args, false);
    }

    /** A fully validated give/take request. */
    private record ValidatedApply(ResolvedEnchant ench, int level, Player targetPlayer, String target) {}

    /** A high-level give waiting for {@code /ui enchant confirm}. */
    private record PendingApply(String enchantArg, int level, String playerName, String slot, long createdAt) {}

    /** How long a pending give stays confirmable. */
    private static final long CONFIRM_TTL_MS = 60_000L;

    /** Per-sender pending gives (player UUID as string, or "console"). */
    private static final java.util.concurrent.ConcurrentHashMap<String, PendingApply> PENDING =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static String pendingKey(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId().toString() : "console";
    }

    private static boolean apply(CommandSender sender, String[] args, boolean isGive) {
        ValidatedApply v = validateApply(sender, args, isGive);
        if (v == null) return true;

        // A give above the safe level requires an explicit /ui enchant confirm
        if (isGive && v.level() > getConfirmThreshold()) {
            PENDING.put(pendingKey(sender), new PendingApply(
                    args[2], v.level(), v.targetPlayer().getName(), v.target(), System.currentTimeMillis()));
            String body = MessagesManager.getString("enchant.confirm_required",
                    "<yellow>⚠</yellow> <white>%enchant% %level%</white> <gray>is above the safe level</gray>"
                    + " <yellow>%max%</yellow><gray>. To apply it on</gray> <yellow>%player%</yellow>"
                    + "<gray>, click (valid for 60 seconds):</gray>")
                    .replace("%enchant%", v.ench().displayName())
                    .replace("%level%", String.valueOf(v.level()))
                    .replace("%max%", String.valueOf(getConfirmThreshold()))
                    .replace("%player%", v.targetPlayer().getName());
            sender.sendMessage(MessageUtil.parse(body)
                    .append(net.kyori.adventure.text.Component.newline())
                    .append(clickable("/ui enchant confirm",
                            net.kyori.adventure.text.format.NamedTextColor.WHITE, "Click to confirm"))
                    .append(net.kyori.adventure.text.Component.text("   ")
                            .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                    .append(clickable("/ui enchant cancel",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY, "Click to cancel")));
            return true;
        }
        executeApply(sender, v, isGive);
        return true;
    }

    /** A clickable command hint (runs the command on click). */
    private static net.kyori.adventure.text.Component clickable(String command,
            net.kyori.adventure.text.format.NamedTextColor color, String hover) {
        return net.kyori.adventure.text.Component.text(command)
                .color(color)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        MessageUtil.parse("<gray>" + hover + "</gray>")));
    }

    /** /ui enchant confirm — applies the pending high-level give. */
    private static boolean confirm(CommandSender sender) {
        PendingApply p = PENDING.remove(pendingKey(sender));
        if (p == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.confirm_none",
                            "<red>❌ Nothing to confirm — run /ui enchant give first.</red>")));
            return true;
        }
        if (System.currentTimeMillis() - p.createdAt() > CONFIRM_TTL_MS) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.confirm_expired",
                            "<red>❌ The pending enchantment expired — run /ui enchant give again.</red>")));
            return true;
        }
        ResolveResult resolved = resolveEnchant(p.enchantArg());
        if (resolved.isAmbiguous()) {
            CommandErrors.custom(sender, ERR_AMBIGUOUS_ENCHANT,
                    MessagesManager.getString("enchant.ambiguous_enchant",
                            "<white>%enchant%</white> <red>exists in several namespaces (</red><yellow>%list%</yellow>"
                            + "<red>) — specify one explicitly</red>")
                            .replace("%enchant%", p.enchantArg())
                            .replace("%list%", String.join(", ", resolved.ambiguousIds())));
            return true;
        }
        ResolvedEnchant ench = resolved.ench();
        if (ench == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.invalid_enchant",
                            "<red>❌ Unknown enchantment: </red><yellow>%enchant%</yellow>")
                            .replace("%enchant%", p.enchantArg())));
            return true;
        }
        @SuppressWarnings("deprecation")
        Player targetPlayer = Bukkit.getPlayerExact(p.playerName());
        if (targetPlayer == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.player_not_found",
                            "<red>❌ Player </red><yellow>%player%</yellow><red> is not online!</red>")
                            .replace("%player%", p.playerName())));
            return true;
        }
        executeApply(sender, new ValidatedApply(ench, p.level(), targetPlayer, p.slot()), true);
        return true;
    }

    /** /ui enchant cancel — drops the pending high-level give. */
    private static boolean cancel(CommandSender sender) {
        PendingApply p = PENDING.remove(pendingKey(sender));
        if (p == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.cancel_none",
                            "<red>❌ Nothing to cancel — no pending enchantment.</red>")));
            return true;
        }
        sender.sendMessage(MessageUtil.parse(
                MessagesManager.getString("enchant.cancel_done",
                        "<green>✔</green> <gray>Pending</gray> <white>%enchant% %level%</white>"
                        + " <gray>for</gray> <yellow>%player%</yellow> <gray>cancelled.</gray>")
                        .replace("%enchant%", p.enchantArg())
                        .replace("%level%", String.valueOf(p.level()))
                        .replace("%player%", p.playerName())));
        return true;
    }

    /**
     * Validates the /ui enchant give|take arguments.
     * <p>
     * {@code <player>} and {@code <slot>} are OPTIONAL: they default to the
     * sender (players only) and {@code mainhand}.
     * Sends the error message and returns {@code null} on failure.
     */
    private static ValidatedApply validateApply(CommandSender sender, String[] args, boolean isGive) {
        // /ui enchant give|take <enchant> <level> [player] [slot]
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.parse(isGive
                    ? "<red>❌ Usage: </red><white>/ui enchant give <enchantment> <level> [player] [slot]</white>"
                    : "<red>❌ Usage: </red><white>/ui enchant take <enchantment> <level> [player] [slot]</white>"));
            return null;
        }

        // ─── Enchantment ───
        ResolveResult resolved = resolveEnchant(args[2]);
        if (resolved.isAmbiguous()) {
            CommandErrors.custom(sender, ERR_AMBIGUOUS_ENCHANT,
                    MessagesManager.getString("enchant.ambiguous_enchant",
                            "<white>%enchant%</white> <red>exists in several namespaces (</red><yellow>%list%</yellow>"
                            + "<red>) — specify one explicitly</red>")
                            .replace("%enchant%", args[2])
                            .replace("%list%", String.join(", ", resolved.ambiguousIds())));
            return null;
        }
        ResolvedEnchant ench = resolved.ench();
        if (ench == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.invalid_enchant",
                            "<red>❌ Unknown enchantment: </red><yellow>%enchant%</yellow>")
                            .replace("%enchant%", args[2])));
            return null;
        }

        // ─── Level ───
        int level;
        try {
            level = Integer.parseInt(args[3].trim());
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.invalid_level",
                            "<red>❌ Level must be a whole number of 1 or higher!</red>")));
            return null;
        }
        if (level < 1) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.invalid_level",
                            "<red>❌ Level must be a whole number of 1 or higher!</red>")));
            return null;
        }

        // ─── Player (optional — defaults to the sender) ───
        String playerName;
        if (args.length >= 5) {
            playerName = args[4];
        } else if (sender instanceof Player self) {
            playerName = self.getName();
        } else {
            CommandErrors.playerOnly(sender);
            return null;
        }
        @SuppressWarnings("deprecation")
        Player targetPlayer = Bukkit.getPlayerExact(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.player_not_found",
                            "<red>❌ Player </red><yellow>%player%</yellow><red> is not online!</red>")
                            .replace("%player%", playerName)));
            return null;
        }

        // ─── Slot (optional — defaults to the main hand) ───
        String target = args.length >= 6 ? args[5].toLowerCase() : "mainhand";
        if (!isValidTarget(target)) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.invalid_target",
                            "<red>❌ Unknown slot: </red><yellow>%target%</yellow><red>. Valid: mainhand, offhand, bothhand, cursor, hotbar, armor, inventory, all</red>")
                            .replace("%target%", target)));
            return null;
        }
        return new ValidatedApply(ench, level, targetPlayer, target);
    }

    /**
     * Applies a custom enchantment level: the normal {@code setLevel} (real
     * enchantment + PDC mirror) when the level is within the enchantment's own
     * MAX_LEVEL, otherwise the real enchantment alone at the raw level — the
     * mechanics clamp at read, the display keeps the requested number.
     */
    private static void giveLevel(ItemStack item, int level,
                                  java.util.function.ObjIntConsumer<ItemStack> setLevel,
                                  java.util.function.Supplier<Enchantment> realEnchant) {
        setLevel.accept(item, level);
        Enchantment real = realEnchant.get();
        if (real != null && item.getEnchantmentLevel(real) < level) {
            item.addUnsafeEnchantment(real, level);
        }
    }

    private static void executeApply(CommandSender sender, ValidatedApply v, boolean isGive) {
        ResolvedEnchant ench = v.ench();
        int level = v.level();
        Player targetPlayer = v.targetPlayer();
        String target = v.target();

        // ─── Apply ───
        int count = 0;
        for (Slot slot : collectSlots(targetPlayer, target)) {
            ItemStack item = slot.get();
            if (item == null || item.getType().isAir()) continue;

            if (ench.isCustom()) {
                switch (ench.customName()) {
                    case "aoe" -> {
                        if (com.ultimateimprovments.enchantment.aoe.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.aoe.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.aoe.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.aoe.Enchantment.hasAoe(item)) {
                                com.ultimateimprovments.enchantment.aoe.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // AoE cannot go on a non-tool — skip silently
                    }
                    case "autosmelt" -> {
                        if (com.ultimateimprovments.enchantment.autosmelt.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.autosmelt.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.autosmelt.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.autosmelt.Enchantment.hasAutoSmelt(item)) {
                                com.ultimateimprovments.enchantment.autosmelt.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // AutoSmelt cannot go on a non-tool — skip silently
                    }
                    case "veinminer" -> {
                        if (com.ultimateimprovments.enchantment.veinminer.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.veinminer.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.veinminer.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.veinminer.Enchantment.hasVeinMiner(item)) {
                                com.ultimateimprovments.enchantment.veinminer.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // VeinMiner requires a pickaxe — skip silently
                    }
                    case "treecapitator" -> {
                        if (com.ultimateimprovments.enchantment.treecapitator.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.treecapitator.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.treecapitator.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.treecapitator.Enchantment.hasTreeCapitator(item)) {
                                com.ultimateimprovments.enchantment.treecapitator.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // TreeCapitator requires an axe — skip silently
                    }
                    case "flight" -> {
                        if (com.ultimateimprovments.enchantment.flight.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.flight.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.flight.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.flight.Enchantment.hasFlight(item)) {
                                com.ultimateimprovments.enchantment.flight.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Flight requires a chestplate — skip silently
                    }
                    case "magnet" -> {
                        if (com.ultimateimprovments.enchantment.magnet.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.magnet.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.magnet.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.magnet.Enchantment.hasMagnet(item)) {
                                com.ultimateimprovments.enchantment.magnet.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Magnet requires a tool — skip silently
                    }
                    case "igniting" -> {
                        if (com.ultimateimprovments.enchantment.igniting.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.igniting.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.igniting.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.igniting.Enchantment.hasIgniting(item)) {
                                com.ultimateimprovments.enchantment.igniting.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Igniting requires an armor piece — skip silently
                    }
                    case "levitation" -> {
                        if (com.ultimateimprovments.enchantment.levitation.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.levitation.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.levitation.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.levitation.Enchantment.hasLevitation(item)) {
                                com.ultimateimprovments.enchantment.levitation.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Levitation requires a chestplate — skip silently
                    }
                    case "self_destruct" -> {
                        // The curse goes on ANY item.
                        if (isGive) {
                            giveLevel(item, level,
                                    com.ultimateimprovments.enchantment.selfdestruct.Enchantment::setLevel,
                                    com.ultimateimprovments.enchantment.selfdestruct.Enchantment::getRegisteredEnchantment);
                            count++;
                        } else if (com.ultimateimprovments.enchantment.selfdestruct.Enchantment.hasSelfDestruct(item)) {
                            com.ultimateimprovments.enchantment.selfdestruct.Enchantment.removeLevel(item);
                            count++;
                        }
                    }
                    case "degradation" -> {
                        // The curse goes on ANY item with durability.
                        if (com.ultimateimprovments.enchantment.degradation.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.degradation.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.degradation.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.degradation.Enchantment.hasDegradation(item)) {
                                com.ultimateimprovments.enchantment.degradation.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Degradation requires an item with durability — skip silently
                    }
                    case "attack_aoe" -> {
                        if (com.ultimateimprovments.enchantment.attackaoe.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.attackaoe.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.attackaoe.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.attackaoe.Enchantment.hasAttackAoe(item)) {
                                com.ultimateimprovments.enchantment.attackaoe.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Attack AoE requires a sword or an axe — skip silently
                    }
                    case "item_stealing" -> {
                        if (com.ultimateimprovments.enchantment.itemstealing.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.itemstealing.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.itemstealing.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.itemstealing.Enchantment.hasItemStealing(item)) {
                                com.ultimateimprovments.enchantment.itemstealing.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Item Stealing requires a fishing rod — skip silently
                    }
                    case "repairing" -> {
                        // Works on ANY item with durability.
                        if (com.ultimateimprovments.enchantment.repairing.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.repairing.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.repairing.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.repairing.Enchantment.hasRepairing(item)) {
                                com.ultimateimprovments.enchantment.repairing.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Repairing requires an item with durability — skip silently
                    }
                    case "lava_walker" -> {
                        if (com.ultimateimprovments.enchantment.lavawalker.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.lavawalker.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.lavawalker.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.lavawalker.Enchantment.hasLavaWalker(item)) {
                                com.ultimateimprovments.enchantment.lavawalker.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Lava Walker requires boots — skip silently
                    }
                    case "container_stealing" -> {
                        if (com.ultimateimprovments.enchantment.containerstealing.Enchantment.isValidTool(item)) {
                            if (isGive) {
                                giveLevel(item, level,
                                        com.ultimateimprovments.enchantment.containerstealing.Enchantment::setLevel,
                                        com.ultimateimprovments.enchantment.containerstealing.Enchantment::getRegisteredEnchantment);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.containerstealing.Enchantment.hasContainerStealing(item)) {
                                com.ultimateimprovments.enchantment.containerstealing.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Container Stealing requires a tool — skip silently
                    }
                    default -> { /* unknown custom enchant — skip */ }
                }
            } else {
                if (isGive) {
                    item.addUnsafeEnchantment(ench.vanilla(), level);
                    count++;
                } else if (item.containsEnchantment(ench.vanilla())) {
                    item.removeEnchantment(ench.vanilla());
                    count++;
                }
            }

            slot.set(item);
        }

        String action = isGive ? "Applied" : "Removed";
        if (count == 0) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.none_found",
                            "<yellow>⚠</yellow> <gray>No items to %action% in</gray> <white>%target%</white><gray>.</gray>")
                            .replace("%action%", isGive ? "enchant" : "take from")
                            .replace("%target%", target)));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.success",
                            "<green>✔</green> <white>%action%</white> <aqua>%enchant% %level%</aqua> <white>on</white> <yellow>%count%</yellow> <white>item(s) of</white> <yellow>%player%</yellow><white>.</white>")
                            .replace("%action%", action)
                            .replace("%enchant%", ench.displayName())
                            .replace("%level%", String.valueOf(level))
                            .replace("%count%", String.valueOf(count))
                            .replace("%player%", targetPlayer.getName())));
        }
    }

    // =========================
    // CHECK — a player's enchantments list with pagination
    // =========================

    private static boolean check(CommandSender sender, String[] args) {
        // /ui enchant check <player> [page]
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui enchant check <player> [page]</white>"));
            return true;
        }

        @SuppressWarnings("deprecation")
        Player targetPlayer = Bukkit.getPlayerExact(args[2]);
        if (targetPlayer == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.player_not_found",
                            "<red>❌ Player </red><yellow>%player%</yellow><red> is not online!</red>")
                            .replace("%player%", args[2])));
            return true;
        }

        int page = 1;
        if (args.length >= 4) {
            try {
                page = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                // stay on page 1
            }
        }

        List<String> entries = buildCheckEntries(targetPlayer);
        int totalPages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, entries.size());

        // ─── Header ───
        sender.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gold>✦ <white>Enchantments <gray>of <yellow>" + targetPlayer.getName()
                + " <dark_gray>(" + page + "/" + totalPages + ")"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫"));

        if (entries.isEmpty()) {
            sender.sendMessage(MessageUtil.parse("<dark_gray>┃  <gray>No enchantments found."));
        } else {
            for (int i = from; i < to; i++) {
                sender.sendMessage(MessageUtil.parse("<dark_gray>┃  " + entries.get(i)));
            }
        }

        // ─── Footer: pagination (Adventure) ───
        sender.sendMessage(MessageUtil.parse("<dark_gray>┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫"));
        net.kyori.adventure.text.Component footer = MessageUtil.parse(
                "<dark_gray>┃  <gray>Page <yellow>" + page + "<gray>/" + totalPages + "   ");

        if (page > 1) {
            net.kyori.adventure.text.Component prev = net.kyori.adventure.text.Component.text("[<]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/ui enchant check " + targetPlayer.getName() + " " + (page - 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            MessageUtil.parse("<gray>Previous page")));
            footer = footer.append(prev);
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[<]"));
        }

        footer = footer.append(MessageUtil.parse("  "));

        if (page < totalPages) {
            net.kyori.adventure.text.Component next = net.kyori.adventure.text.Component.text("[>]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/ui enchant check " + targetPlayer.getName() + " " + (page + 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            MessageUtil.parse("<gray>Next page")));
            footer = footer.append(next);
        } else {
            footer = footer.append(MessageUtil.parse("<dark_gray>[>]"));
        }

        sender.sendMessage(footer);
        sender.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
        return true;
    }

    /**
     * Builds the «slot — item: enchantments» rows for /ui enchant check.
     */
    private static List<String> buildCheckEntries(Player player) {
        List<String> entries = new ArrayList<>();

        for (Slot slot : collectSlots(player, "all")) {
            ItemStack item = slot.get();
            if (item == null || item.getType().isAir()) continue;

            List<String> enchants = new ArrayList<>();

            // Real registry entries: every non-ui enchantment by its full id
            // ("minecraft:sharpness 5"). The ui:* ones are listed below via their
            // Enchantment classes — that also covers PDC-only items after a
            // datapack crash (and avoids a duplicate row for the same charm).
            Map<Enchantment, Integer> vanilla = item.getEnchantments();
            for (Map.Entry<Enchantment, Integer> e : vanilla.entrySet()) {
                String id = e.getKey().getKey().toString(); // "ui:aoe", "minecraft:sharpness", ...
                if (id.startsWith("ui:")) continue;
                enchants.add("<green>" + id + " " + e.getValue());
            }

            // Custom enchants — real enchantment or legacy PDC
            int aoe = com.ultimateimprovments.enchantment.aoe.Enchantment.getLevel(item);
            if (aoe > 0) {
                enchants.add("<aqua>ui:aoe " + aoe);
            }
            if (com.ultimateimprovments.enchantment.autosmelt.Enchantment.getLevel(item) > 0) {
                enchants.add("<aqua>ui:autosmelt");
            }
            if (com.ultimateimprovments.enchantment.veinminer.Enchantment.getLevel(item) > 0) {
                enchants.add("<aqua>ui:veinminer");
            }
            if (com.ultimateimprovments.enchantment.treecapitator.Enchantment.getLevel(item) > 0) {
                enchants.add("<aqua>ui:treecapitator");
            }
            if (com.ultimateimprovments.enchantment.flight.Enchantment.getLevel(item) > 0) {
                enchants.add("<aqua>ui:flight");
            }
            if (com.ultimateimprovments.enchantment.magnet.Enchantment.getLevel(item) > 0) {
                enchants.add("<aqua>ui:magnet");
            }
            int igniting = com.ultimateimprovments.enchantment.igniting.Enchantment.getLevel(item);
            if (igniting > 0) {
                enchants.add("<aqua>ui:igniting " + igniting);
            }
            if (com.ultimateimprovments.enchantment.levitation.Enchantment.getLevel(item) > 0) {
                enchants.add("<aqua>ui:levitation");
            }
            // Curses — shown in red
            if (com.ultimateimprovments.enchantment.selfdestruct.Enchantment.getLevel(item) > 0) {
                enchants.add("<red>ui:self_destruct");
            }
            int degradation = com.ultimateimprovments.enchantment.degradation.Enchantment.getLevel(item);
            if (degradation > 0) {
                enchants.add("<red>ui:degradation " + degradation);
            }
            int attackAoe = com.ultimateimprovments.enchantment.attackaoe.Enchantment.getLevel(item);
            if (attackAoe > 0) {
                enchants.add("<aqua>ui:attack_aoe " + attackAoe);
            }
            if (com.ultimateimprovments.enchantment.itemstealing.Enchantment.getLevel(item) > 0) {
                enchants.add("<aqua>ui:item_stealing");
            }
            if (com.ultimateimprovments.enchantment.containerstealing.Enchantment.getLevel(item) > 0) {
                enchants.add("<aqua>ui:container_stealing");
            }
            int repairing = com.ultimateimprovments.enchantment.repairing.Enchantment.getLevel(item);
            if (repairing > 0) {
                enchants.add("<aqua>ui:repairing " + repairing);
            }
            int lavaWalker = com.ultimateimprovments.enchantment.lavawalker.Enchantment.getLevel(item);
            if (lavaWalker > 0) {
                enchants.add("<aqua>ui:lava_walker " + lavaWalker);
            }
            int blunting = com.ultimateimprovments.enchantment.blunting.Enchantment.getLevel(item);
            if (blunting > 0) {
                enchants.add("<red>ui:blunting " + blunting);
            }
            int vulnerability = com.ultimateimprovments.enchantment.vulnerability.Enchantment.getLevel(item);
            if (vulnerability > 0) {
                enchants.add("<red>ui:vulnerability " + vulnerability);
            }
            int disappearance = com.ultimateimprovments.enchantment.disappearance.Enchantment.getLevel(item);
            if (disappearance > 0) {
                enchants.add("<red>ui:disappearance " + disappearance);
            }

            if (enchants.isEmpty()) continue;

            String itemName = item.getType().name().toLowerCase().replace('_', ' ');
            entries.add("<gray>" + slot.name + " <dark_gray>— <white>" + itemName
                    + " <gray>: <reset>" + String.join("<gray>, ", enchants));
        }

        return entries;
    }

    // =========================
    // TAB-COMPLETE
    // =========================

    public static List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length < 2) {
            return result; // just /ui enchant — nothing to suggest
        }

        if (args.length == 2) {
            for (String s : List.of("give", "take", "confirm", "cancel", "check")) {
                if (s.startsWith(args[1].toLowerCase())) result.add(s);
            }
            return result;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "give", "take" -> {
                switch (args.length) {
                    case 3 -> result = allEnchantNames();
                    case 4 -> result = levelSuggestions(args[3]);
                    case 5 -> result = onlinePlayerNames();
                    case 6 -> result = targetNames();
                    default -> { }
                }
            }
            case "check" -> {
                if (args.length == 3) {
                    result = onlinePlayerNames();
                } else if (args.length == 4) {
                    @SuppressWarnings("deprecation")
                    Player targetPlayer = Bukkit.getPlayerExact(args[2]);
                    if (targetPlayer != null) {
                        int pages = Math.max(1,
                                (buildCheckEntries(targetPlayer).size() + PER_PAGE - 1) / PER_PAGE);
                        for (int p = 1; p <= pages; p++) {
                            result.add(String.valueOf(p));
                        }
                    }
                }
            }
            default -> { }
        }
        return result;
    }

    private static List<String> levelSuggestions(String partial) {
        List<String> result = new ArrayList<>();
        if (partial.isEmpty()) {
            for (String l : List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")) result.add(l);
        } else {
            result.add(partial);
        }
        return result;
    }

    private static List<String> onlinePlayerNames() {
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            result.add(p.getName());
        }
        return result;
    }
}
