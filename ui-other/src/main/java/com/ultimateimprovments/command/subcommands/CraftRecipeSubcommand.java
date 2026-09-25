package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /ui craftrecipe &lt;recipe&gt; — admin free-craft.
 * <p>
 * "Crafts" the given registered recipe without consuming any resources and puts
 * the result straight into the player's inventory. If the inventory cannot fit
 * the result, the command says so and drops nothing (nothing is lost).
 * <p>
 * Tab-complete lists EVERY registered recipe — including custom UI recipes that
 * are hidden from the vanilla recipe book (Crafter-only ones included).
 * <p>
 * Permission: {@code ui.command.craftrecipe} (admin-only, default OP).
 */
public final class CraftRecipeSubcommand {

    private CraftRecipeSubcommand() {}

    private static final String PERMISSION = "ui.command.craftrecipe";

    public static boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "general.player_only", "<red>❌ Only players can use this command!</red>")));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "craftrecipe.usage",
                    "<red>❌ Usage: </red><white>/ui craftrecipe <recipe></white> <gray>(tab-complete lists all)</gray>")));
            return true;
        }

        String input = args[1].toLowerCase();
        NamespacedKey key = resolveKey(input);
        if (key == null) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "craftrecipe.not_found",
                            "<red>❌ Recipe not found: </red><white>%recipe%</white> <gray>(tab-complete lists all)</gray>")
                    .replace("%recipe%", input)));
            return true;
        }

        Recipe recipe = Bukkit.getRecipe(key);
        if (recipe == null) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "craftrecipe.not_found",
                            "<red>❌ Recipe not found: </red><white>%recipe%</white> <gray>(tab-complete lists all)</gray>")
                    .replace("%recipe%", input)));
            return true;
        }

        ItemStack result = recipe.getResult();
        if (result == null || result.getType().isAir()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "craftrecipe.no_result", "<red>❌ This recipe has no craftable result.</red>")));
            return true;
        }

        ItemStack toGive = result.clone();
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(toGive);
        int given = toGive.getAmount() - overflow.values().stream()
                .mapToInt(ItemStack::getAmount).sum();

        if (overflow.isEmpty()) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "craftrecipe.success",
                            "<green>✔</green> <white>Crafted </white><yellow>%item%</yellow><white> ×%amount% (no resources used).</white>")
                    .replace("%item%", prettify(result.getType()))
                    .replace("%amount%", String.valueOf(result.getAmount()))));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString(
                            "craftrecipe.inventory_full",
                            "<yellow>⚠ Inventory full! Only</yellow> <white>%given%/%total%</white> <yellow>fit — nothing was dropped.</yellow>")
                    .replace("%given%", String.valueOf(given))
                    .replace("%total%", String.valueOf(result.getAmount()))));
        }
        return true;
    }

    /**
     * Resolves user input to a NamespacedKey: accepts bare names ("dfc"),
     * "ui:dfc" (namespaced) and vanilla keys ("diamond_sword").
     */
    private static NamespacedKey resolveKey(String input) {
        // 1. Exact key with namespace
        if (input.contains(":")) {
            int idx = input.indexOf(':');
            return new NamespacedKey(input.substring(0, idx), input.substring(idx + 1));
        }
        // 2. ui: namespace first (custom recipes)
        NamespacedKey ui = NamespacedKey.fromString("ui:" + input);
        if (ui != null && Bukkit.getRecipe(ui) != null) return ui;
        // 3. minecraft: namespace
        NamespacedKey mc = NamespacedKey.minecraft(input);
        if (Bukkit.getRecipe(mc) != null) return mc;
        return ui;
    }

    /** Tab-complete: EVERY registered recipe, custom ones included. */
    public static List<String> tabComplete(String[] args) {
        if (args.length != 2) return List.of();
        String partial = args[1].toLowerCase();

        List<String> out = new ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof Keyed keyed) {
                String key = keyed.getKey().toString();
                String bare = keyed.getKey().getKey();
                if (key.startsWith("ui:")) {
                    // custom recipes are suggested by bare name (most used)
                    if (bare.startsWith(partial)) out.add(bare);
                } else if (bare.startsWith(partial)) {
                    out.add(bare);
                }
            }
        });
        return out;
    }

    private static String prettify(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
