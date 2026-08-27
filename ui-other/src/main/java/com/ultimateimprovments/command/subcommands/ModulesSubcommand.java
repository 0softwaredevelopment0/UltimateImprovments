package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.module.ModuleManager;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.command.CommandSender;

import java.util.*;

/**
 * ModulesSubcommand — shows the plugin architecture as a module hierarchy.
 * <p>
 * The tree is built from the registered modules' paths (modulePath).
 * Works identically in dev and production (independent of the filesystem).
 * <p>
 * Each leaf module: ✔ (enabled) / ❌ (disabled) / ? (no module).
 * Commands:
 * <ul>
 *   <li>{@code /ui modules list} — hierarchical list</li>
 *   <li>{@code /ui modules enable <path>} — enable</li>
 *   <li>{@code /ui modules disable <path>} — disable</li>
 * </ul>
 */
public final class ModulesSubcommand {

    private ModulesSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui modules list|enable|disable <gray><путь>"));
            return true;
        }
        var mm = ModuleManager.getInstance();
        return switch (args[1].toLowerCase()) {
            case "list" -> handleList(sender, mm);
            case "enable" -> handleEnable(sender, args, mm);
            case "disable" -> handleDisable(sender, args, mm);
            default -> {
                sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui modules list|enable|disable <gray><путь>"));
                yield true;
            }
        };
    }

    // ============================================================
    // TREE NODE
    // ============================================================
    private static class TreeNode {
        String name;
        PluginModule module; // null = intermediate folder
        Map<String, TreeNode> children = new LinkedHashMap<>();

        TreeNode(String name) { this.name = name; }
    }

    // ============================================================
    // BUILD TREE FROM MODULE PATHS (no filesystem dependency)
    // ============================================================
    private static TreeNode buildTree(ModuleManager mm) {
        TreeNode root = new TreeNode("ui");

        for (PluginModule m : mm.getModules()) {
            String p = m.getModulePath();
            if (p == null || p.isEmpty()) continue;

            String[] parts = p.split("/");
            TreeNode current = root;
            for (String part : parts) {
                current.children.putIfAbsent(part, new TreeNode(part));
                current = current.children.get(part);
            }
            current.module = m;
        }
        return root;
    }

    // ============================================================
    // LIST
    // ============================================================
    private static boolean handleList(CommandSender sender, ModuleManager mm) {
        TreeNode root = buildTree(mm);

        sender.sendMessage(MessageUtil.parse("<gold>══════════════════════════════════"));
        sender.sendMessage(MessageUtil.parse("<gold>  ✦ <white>Архитектура модулей UltimateImprovments"));
        sender.sendMessage(MessageUtil.parse("<gold>══════════════════════════════════"));
        sender.sendMessage(MessageUtil.parse("<dark_aqua>📁 <white>ui/"));

        List<Map.Entry<String, TreeNode>> entries = new ArrayList<>(root.children.entrySet());
        // Sort: folders first (no module), then by name
        entries.sort((a, b) -> {
            boolean aLeaf = a.getValue().module != null;
            boolean bLeaf = b.getValue().module != null;
            if (aLeaf != bLeaf) return aLeaf ? 1 : -1;
            return a.getKey().compareToIgnoreCase(b.getKey());
        });

        for (int i = 0; i < entries.size(); i++) {
            printTree(sender, entries.get(i).getValue(), "", i == entries.size() - 1);
        }

        sender.sendMessage(MessageUtil.parse("<gold>══════════════════════════════════"));
        sender.sendMessage(MessageUtil.parse("<dark_gray>  <green>✔<dark_gray> включён  <red>❌<dark_gray> выключен  ⚡ ядро"));
        return true;
    }

    private static void printTree(CommandSender sender, TreeNode node, String prefix, boolean isLast) {
        String connector = isLast ? "  └─ " : "  ├─ ";

        // Sort children: folders first, then alphabetically
        List<Map.Entry<String, TreeNode>> entries = new ArrayList<>(node.children.entrySet());
        entries.sort((a, b) -> {
            boolean aLeaf = a.getValue().module != null;
            boolean bLeaf = b.getValue().module != null;
            if (aLeaf != bLeaf) return aLeaf ? 1 : -1;
            return a.getKey().compareToIgnoreCase(b.getKey());
        });

        if (node.module != null) {
            // Leaf system
            boolean on = node.module.isEnabled();
            String status = on ? "<green>✔" : "<red>❌";
            String essential = node.module.isEssential() ? " <dark_gray>⚡" : "";
            String line = prefix + connector + status + " <white>" + node.name + essential;
            line += " <gray>(" + node.module.getName() + ")";
            sender.sendMessage(MessageUtil.parse(line));
        } else {
            // Intermediate directory
            sender.sendMessage(MessageUtil.parse(prefix + connector + "<dark_aqua>📁 <white>" + node.name + "/"));
        }

        for (int i = 0; i < entries.size(); i++) {
            printTree(sender, entries.get(i).getValue(),
                    prefix + (isLast ? "   " : "  │"),
                    i == entries.size() - 1);
        }
    }

    // ============================================================
    // ENABLE
    // ============================================================
    private static boolean handleEnable(CommandSender sender, String[] args, ModuleManager mm) {
        if (!sender.hasPermission("*") && !sender.isOp()) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>У вас нет прав на управление модулями!"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui modules enable <gray><путь>"));
            sender.sendMessage(MessageUtil.parse("<dark_gray>  Пример: <gray>/ui modules enable energy/generation/basic"));
            return true;
        }
        PluginModule found = findModuleByPath(mm, args[2]);
        if (found == null) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Модуль по пути <yellow>" + args[2] + "<red> не найден!"));
            sender.sendMessage(MessageUtil.parse("<dark_gray>  Используйте <gray>/ui modules list<dark_gray> для просмотра."));
            return true;
        }
        if (found.isEnabled()) {
            sender.sendMessage(MessageUtil.parse("<yellow>ℹ <white>Модуль <yellow>" + found.getName() + "<white> уже включён."));
            return true;
        }
        boolean ok = mm.enableModule(found.getName());
        if (ok && found.isEnabled()) {
            sender.sendMessage(MessageUtil.parse("<green>✔ <white>Модуль <yellow>" + found.getName() + "<white> включён!"));
            sender.sendMessage(MessageUtil.parse("<dark_gray>  Путь: <gray>" + found.getModulePath()));
            ConsoleLogger.info("[CMD] " + sender.getName() + " enabled: " + found.getModulePath());
        } else {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Не удалось включить модуль <yellow>" + found.getName() + "<red>!"));
            if (found.getDisableReason() != null)
                sender.sendMessage(MessageUtil.parse("<dark_gray>  Причина: <gray>" + found.getDisableReason()));
        }
        return true;
    }

    // ============================================================
    // DISABLE
    // ============================================================
    private static boolean handleDisable(CommandSender sender, String[] args, ModuleManager mm) {
        if (!sender.hasPermission("*") && !sender.isOp()) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>У вас нет прав на управление модулями!"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Использование: <white>/ui modules disable <gray><путь>"));
            sender.sendMessage(MessageUtil.parse("<dark_gray>  Пример: <gray>/ui modules disable energy/generation/basic"));
            return true;
        }
        PluginModule found = findModuleByPath(mm, args[2]);
        if (found == null) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Модуль по пути <yellow>" + args[2] + "<red> не найден!"));
            sender.sendMessage(MessageUtil.parse("<dark_gray>  Используйте <gray>/ui modules list<dark_gray> для просмотра."));
            return true;
        }
        if (!found.isEnabled()) {
            sender.sendMessage(MessageUtil.parse("<yellow>ℹ <white>Модуль <yellow>" + found.getName() + "<white> уже выключен."));
            return true;
        }
        if (found.isEssential()) {
            sender.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Нельзя отключить ядерный модуль <yellow>" + found.getName() + "<red>!"));
            sender.sendMessage(MessageUtil.parse("<dark_gray>  ⚡ = модули ядра (без них плагин нестабилен)"));
            return true;
        }
        mm.disableModule(found.getName());
        sender.sendMessage(MessageUtil.parse("<red>❌ <white>Модуль <yellow>" + found.getName() + "<white> отключён."));
        sender.sendMessage(MessageUtil.parse("<dark_gray>  Путь: <gray>" + found.getModulePath()));
        ConsoleLogger.info("[CMD] " + sender.getName() + " disabled: " + found.getModulePath());
        return true;
    }

    // ============================================================
    // FIND BY PATH (exact → partial → name)
    // ============================================================
    private static PluginModule findModuleByPath(ModuleManager mm, String searchPath) {
        // Exact match
        for (PluginModule m : mm.getModules())
            if (m.getModulePath().equals(searchPath)) return m;
        // Ends-with match
        List<PluginModule> partial = new ArrayList<>();
        for (PluginModule m : mm.getModules())
            if (m.getModulePath().endsWith("/" + searchPath)) partial.add(m);
        if (partial.size() == 1) return partial.get(0);
        if (partial.size() > 1) {
            partial.sort(Comparator.comparingInt(a -> a.getModulePath().length()));
            return partial.get(0);
        }
        // Name match
        for (PluginModule m : mm.getModules())
            if (m.getName().equalsIgnoreCase(searchPath)) return m;
        return null;
    }
}
