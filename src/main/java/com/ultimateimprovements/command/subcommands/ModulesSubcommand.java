package com.ultimateimprovements.command.subcommands;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.module.ModuleManager;
import com.ultimateimprovements.module.PluginModule;
import com.ultimateimprovements.util.ConsoleLogger;
import org.bukkit.command.CommandSender;

import java.util.*;

/**
 * ModulesSubcommand — показывает архитектуру плагина в виде иерархии модулей.
 * <p>
 * Дерево строится из путей зарегистрированных модулей (modulePath).
 * Работает идентично в dev и production (не зависит от файловой системы).
 * <p>
 * Каждый листовой модуль: ✔ (включён) / ❌ (выключен) / ? (нет модуля).
 * Команды:
 * <ul>
 *   <li>{@code /mp modules list} — иерархический список</li>
 *   <li>{@code /mp modules enable <путь>} — включить</li>
 *   <li>{@code /mp modules disable <путь>} — выключить</li>
 * </ul>
 */
public final class ModulesSubcommand {

    private ModulesSubcommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp modules list|enable|disable §7<путь>");
            return true;
        }
        var mm = ModuleManager.getInstance();
        return switch (args[1].toLowerCase()) {
            case "list" -> handleList(sender, mm);
            case "enable" -> handleEnable(sender, args, mm);
            case "disable" -> handleDisable(sender, args, mm);
            default -> {
                sender.sendMessage("§4❌ §cИспользование: §f/mp modules list|enable|disable §7<путь>");
                yield true;
            }
        };
    }

    // ============================================================
    // TREE NODE
    // ============================================================
    private static class TreeNode {
        String name;
        PluginModule module; // null = промежуточная папка
        Map<String, TreeNode> children = new LinkedHashMap<>();

        TreeNode(String name) { this.name = name; }
    }

    // ============================================================
    // BUILD TREE FROM MODULE PATHS (no filesystem dependency)
    // ============================================================
    private static TreeNode buildTree(ModuleManager mm) {
        TreeNode root = new TreeNode("mcplugin");

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

        sender.sendMessage("§6══════════════════════════════════");
        sender.sendMessage("§6  ✦ §fАрхитектура модулей UltimateImprovements");
        sender.sendMessage("§6══════════════════════════════════");
        sender.sendMessage("§3📁 §fmcplugin/");

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

        sender.sendMessage("§6══════════════════════════════════");
        sender.sendMessage("§8  §a✔§8 включён  §c❌§8 выключен  ⚡ ядро");
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
            String status = on ? "§a✔" : "§c❌";
            String essential = node.module.isEssential() ? " §8⚡" : "";
            String line = prefix + connector + status + " §f" + node.name + essential;
            line += " §7(" + node.module.getName() + ")";
            sender.sendMessage(line);
        } else {
            // Intermediate directory
            sender.sendMessage(prefix + connector + "§3📁 §f" + node.name + "/");
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
            sender.sendMessage("§4❌ §cУ вас нет прав на управление модулями!");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp modules enable §7<путь>");
            sender.sendMessage("§8  Пример: §7/mp modules enable energy/generation/basic");
            return true;
        }
        PluginModule found = findModuleByPath(mm, args[2]);
        if (found == null) {
            sender.sendMessage("§4❌ §cМодуль по пути §e" + args[2] + "§c не найден!");
            sender.sendMessage("§8  Используйте §7/mp modules list§8 для просмотра.");
            return true;
        }
        if (found.isEnabled()) {
            sender.sendMessage("§eℹ §fМодуль §e" + found.getName() + "§f уже включён.");
            return true;
        }
        boolean ok = mm.enableModule(found.getName());
        if (ok && found.isEnabled()) {
            sender.sendMessage("§a✔ §fМодуль §e" + found.getName() + "§f включён!");
            sender.sendMessage("§8  Путь: §7" + found.getModulePath());
            ConsoleLogger.info("[CMD] " + sender.getName() + " enabled: " + found.getModulePath());
        } else {
            sender.sendMessage("§4❌ §cНе удалось включить модуль §e" + found.getName() + "§c!");
            if (found.getDisableReason() != null)
                sender.sendMessage("§8  Причина: §7" + found.getDisableReason());
        }
        return true;
    }

    // ============================================================
    // DISABLE
    // ============================================================
    private static boolean handleDisable(CommandSender sender, String[] args, ModuleManager mm) {
        if (!sender.hasPermission("*") && !sender.isOp()) {
            sender.sendMessage("§4❌ §cУ вас нет прав на управление модулями!");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§4❌ §cИспользование: §f/mp modules disable §7<путь>");
            sender.sendMessage("§8  Пример: §7/mp modules disable energy/generation/basic");
            return true;
        }
        PluginModule found = findModuleByPath(mm, args[2]);
        if (found == null) {
            sender.sendMessage("§4❌ §cМодуль по пути §e" + args[2] + "§c не найден!");
            sender.sendMessage("§8  Используйте §7/mp modules list§8 для просмотра.");
            return true;
        }
        if (!found.isEnabled()) {
            sender.sendMessage("§eℹ §fМодуль §e" + found.getName() + "§f уже выключен.");
            return true;
        }
        if (found.isEssential()) {
            sender.sendMessage("§4❌ §cНельзя отключить ядерный модуль §e" + found.getName() + "§c!");
            sender.sendMessage("§8  ⚡ = модули ядра (без них плагин нестабилен)");
            return true;
        }
        mm.disableModule(found.getName());
        sender.sendMessage("§c❌ §fМодуль §e" + found.getName() + "§f отключён.");
        sender.sendMessage("§8  Путь: §7" + found.getModulePath());
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
