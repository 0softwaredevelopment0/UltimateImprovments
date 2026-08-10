package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;

import com.ultimateimprovments.util.MessageUtil;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

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
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * /ui enchant — advanced enchantment manager.
 * <p>
 * Supports ALL vanilla enchantments (via {@link Registry#ENCHANTMENT})
 * plus custom ones from the config {@code enchant.custom_enchantments} (e.g. AoE).
 * <pre>
 *   /ui enchant give <enchantment> <level 1-255> <player> <slot>
 *   /ui enchant take <enchantment> <level> <player> <slot>
 *   /ui enchant check <player> [page]
 * </pre>
 * Slots: mainhand, offhand, bothhand, cursor, hotbar, armor, inventory, all.
 * Settings are in config.yml → {@code enchant:}.
 */
public final class EnchantSubcommand {

    /** How many rows to show on one page of /ui enchant check. */
    private static final int PER_PAGE = 5;

    private EnchantSubcommand() {}

    // =========================
    // CONFIG
    // =========================

    private static boolean isEnabled() {
        return Main.getInstance().getConfig().getBoolean("enchant.enabled", true);
    }

    private static int getMaxLevel() {
        return Math.max(1, Math.min(255,
                Main.getInstance().getConfig().getInt("enchant.max_level", 255)));
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
        String displayName() { return isCustom() ? customName : vanilla.getKey().getKey(); }
    }

    /**
     * Looks up an enchantment by name (custom or vanilla).
     *
     * @return ResolvedEnchant or null if not found
     */
    private static ResolvedEnchant resolveEnchant(String input) {
        if (input == null) return null;
        String norm = input.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');

        // Custom enchantments
        if (isCustomEnchant(norm)) {
            return new ResolvedEnchant(norm, null);
        }

        // Vanilla: "sharpness", "minecraft:sharpness", plus enchantments from other plugins
        String key = norm;
        int colon = norm.indexOf(':');
        if (colon >= 0) {
            key = norm.substring(colon + 1);
        }
        try {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
            if (ench != null) return new ResolvedEnchant(null, ench);
        } catch (IllegalArgumentException ignored) {
            // invalid NamespacedKey
        }
        // Fallback: search the REGISTRY by key name (includes custom enchants from other plugins)
        for (Enchantment ench : Registry.ENCHANTMENT) {
            if (ench.getKey().getKey().equalsIgnoreCase(key)) {
                return new ResolvedEnchant(null, ench);
            }
        }
        return null;
    }

    /** All enchantment names (vanilla + custom) for tab-complete. */
    private static List<String> allEnchantNames() {
        List<String> names = new ArrayList<>();
        for (Enchantment ench : Registry.ENCHANTMENT) {
            names.add(ench.getKey().getKey());
        }
        List<String> customs = Main.getInstance().getConfig().getStringList("enchant.custom_enchantments");
        for (String c : customs) {
            if (c != null && !c.isEmpty() && !names.contains(c.toLowerCase())) {
                names.add(c.toLowerCase());
            }
        }
        return names;
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
            sender.sendMessage(MessageUtil.parse("<red>❌ Enchant manager is disabled in config.</red>"));
            return true;
        }

        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to use this command!</red>"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "give" -> give(sender, args);
            case "take" -> take(sender, args);
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
                + "<white>/ui enchant give <enchantment> <level> <player> <slot></white>\n"
                + "<white>/ui enchant take <enchantment> <level> <player> <slot></white>\n"
                + "<white>/ui enchant check <player> [page]</white>\n"
                + "<gray>Slots: mainhand, offhand, bothhand, cursor, hotbar, armor, inventory, all</gray>")));
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

    private static boolean apply(CommandSender sender, String[] args, boolean isGive) {
        // /ui enchant give|take <enchant> <level> <player> <slot>
        if (args.length < 6) {
            sender.sendMessage(MessageUtil.parse(isGive
                    ? "<red>❌ Usage: </red><white>/ui enchant give <enchantment> <level> <player> <slot></white>"
                    : "<red>❌ Usage: </red><white>/ui enchant take <enchantment> <level> <player> <slot></white>"));
            return true;
        }

        // ─── Enchantment ───
        ResolvedEnchant ench = resolveEnchant(args[2]);
        if (ench == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.invalid_enchant",
                            "<red>❌ Unknown enchantment: </red><yellow>%enchant%</yellow>")
                            .replace("%enchant%", args[2])));
            return true;
        }

        // ─── Level ───
        int maxLevel = getMaxLevel();
        int level;
        try {
            level = Integer.parseInt(args[3].trim());
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.invalid_level",
                            "<red>❌ Level must be a number between 1 and </red><yellow>%max%</yellow><red>!</red>")
                            .replace("%max%", String.valueOf(maxLevel))));
            return true;
        }
        if (level < 1 || level > maxLevel) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.level_out_of_range",
                            "<red>❌ Level </red><yellow>%level%</yellow><red> is out of range (1-</red><yellow>%max%</yellow><red>)!</red>")
                            .replace("%level%", String.valueOf(level))
                            .replace("%max%", String.valueOf(maxLevel))));
            return true;
        }

        // ─── Player ───
        @SuppressWarnings("deprecation")
        Player targetPlayer = Bukkit.getPlayerExact(args[4]);
        if (targetPlayer == null) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.player_not_found",
                            "<red>❌ Player </red><yellow>%player%</yellow><red> is not online!</red>")
                            .replace("%player%", args[4])));
            return true;
        }

        // ─── Slot ───
        String target = args[5].toLowerCase();
        if (!isValidTarget(target)) {
            sender.sendMessage(MessageUtil.parse(
                    MessagesManager.getString("enchant.invalid_target",
                            "<red>❌ Unknown slot: </red><yellow>%target%</yellow><red>. Valid: mainhand, offhand, bothhand, cursor, hotbar, armor, inventory, all</red>")
                            .replace("%target%", args[5])));
            return true;
        }

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
                                com.ultimateimprovments.enchantment.aoe.Enchantment.setLevel(item, level);
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
                                com.ultimateimprovments.enchantment.autosmelt.Enchantment.setLevel(item, 1);
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
                                com.ultimateimprovments.enchantment.veinminer.Enchantment.setLevel(item, 1);
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
                                com.ultimateimprovments.enchantment.treecapitator.Enchantment.setLevel(item, 1);
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
                                com.ultimateimprovments.enchantment.flight.Enchantment.setLevel(item, 1);
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
                                com.ultimateimprovments.enchantment.magnet.Enchantment.setLevel(item, 1);
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
                                com.ultimateimprovments.enchantment.igniting.Enchantment.setLevel(item, level);
                                count++;
                            } else if (com.ultimateimprovments.enchantment.igniting.Enchantment.hasIgniting(item)) {
                                com.ultimateimprovments.enchantment.igniting.Enchantment.removeLevel(item);
                                count++;
                            }
                        }
                        // Igniting requires an armor piece — skip silently
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
        return true;
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
        sender.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        sender.sendMessage("§8┃  §6✦ §fEnchantments §7of §e" + targetPlayer.getName()
                + " §8(" + page + "/" + totalPages + ")");
        sender.sendMessage("§8┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫");

        if (entries.isEmpty()) {
            sender.sendMessage("§8┃  §7No enchantments found.");
        } else {
            for (int i = from; i < to; i++) {
                sender.sendMessage("§8┃  " + entries.get(i));
            }
        }

        // ─── Footer: pagination ───
        sender.sendMessage("§8┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫");
        TextComponent footer = new TextComponent("§8┃  §7Page §e" + page + "§7/" + totalPages + "   ");

        if (page > 1) {
            TextComponent prev = new TextComponent("§e[<]");
            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/ui enchant check " + targetPlayer.getName() + " " + (page - 1)));
            prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§7Previous page").create()));
            footer.addExtra(prev);
        } else {
            footer.addExtra(new TextComponent("§8[<]"));
        }

        footer.addExtra(new TextComponent("  "));

        if (page < totalPages) {
            TextComponent next = new TextComponent("§e[>]");
            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/ui enchant check " + targetPlayer.getName() + " " + (page + 1)));
            next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§7Next page").create()));
            footer.addExtra(next);
        } else {
            footer.addExtra(new TextComponent("§8[>]"));
        }

        sender.spigot().sendMessage(footer);
        sender.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
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

            // Vanilla enchantments (skip the real minecraft:aoe / minecraft:autosmelt —
            // they are listed as the custom enchants below)
            Map<Enchantment, Integer> vanilla = item.getEnchantments();
            for (Map.Entry<Enchantment, Integer> e : vanilla.entrySet()) {
                if (e.getKey().equals(com.ultimateimprovments.enchantment.aoe.Enchantment.ENCHANTMENT_KEY)
                        || e.getKey().equals(com.ultimateimprovments.enchantment.autosmelt.Enchantment.ENCHANTMENT_KEY)
                        || e.getKey().equals(com.ultimateimprovments.enchantment.veinminer.Enchantment.ENCHANTMENT_KEY)
                        || e.getKey().equals(com.ultimateimprovments.enchantment.treecapitator.Enchantment.ENCHANTMENT_KEY)
                        || e.getKey().equals(com.ultimateimprovments.enchantment.flight.Enchantment.ENCHANTMENT_KEY)
                        || e.getKey().equals(com.ultimateimprovments.enchantment.magnet.Enchantment.ENCHANTMENT_KEY)
                        || e.getKey().equals(com.ultimateimprovments.enchantment.igniting.Enchantment.ENCHANTMENT_KEY)) {
                    continue;
                }
                enchants.add("§a" + e.getKey().getKey() + " " + e.getValue());
            }

            // Custom enchants — real enchantment or legacy PDC
            int aoe = com.ultimateimprovments.enchantment.aoe.Enchantment.getLevel(item);
            if (aoe > 0) {
                enchants.add("§bAoe " + aoe);
            }
            if (com.ultimateimprovments.enchantment.autosmelt.Enchantment.getLevel(item) > 0) {
                enchants.add("§bAutoSmelt");
            }
            if (com.ultimateimprovments.enchantment.veinminer.Enchantment.getLevel(item) > 0) {
                enchants.add("§bVeinMiner");
            }
            if (com.ultimateimprovments.enchantment.treecapitator.Enchantment.getLevel(item) > 0) {
                enchants.add("§bTreeCapitator");
            }
            if (com.ultimateimprovments.enchantment.flight.Enchantment.getLevel(item) > 0) {
                enchants.add("§bFlight");
            }
            if (com.ultimateimprovments.enchantment.magnet.Enchantment.getLevel(item) > 0) {
                enchants.add("§bMagnet");
            }
            int igniting = com.ultimateimprovments.enchantment.igniting.Enchantment.getLevel(item);
            if (igniting > 0) {
                enchants.add("§bIgniting " + igniting);
            }

            if (enchants.isEmpty()) continue;

            String itemName = item.getType().name().toLowerCase().replace('_', ' ');
            entries.add("§7" + slot.name + " §8— §f" + itemName
                    + " §7: §r" + String.join("§7, ", enchants));
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
            for (String s : List.of("give", "take", "check")) {
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
            for (String l : List.of("1", "2", "3", "4", "5", "10", "50", "100", "255")) result.add(l);
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
