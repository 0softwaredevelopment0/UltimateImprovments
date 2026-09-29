package com.ultimateimprovments.mechanics.crafting;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RecipeRegistry implements Listener {

    private static final Set<NamespacedKey> CUSTOM_RECIPES = new HashSet<>();

    // =========================
    // REGISTER A CUSTOM RECIPE KEY
    // =========================
    public static void registerRecipe(NamespacedKey key) {
        CUSTOM_RECIPES.add(key);
    }

    /**
     * @return an immutable set of all registered custom recipes
     */
    public static Set<NamespacedKey> getCustomRecipes() {
        return Collections.unmodifiableSet(CUSTOM_RECIPES);
    }

    // =========================
    // CRAFTER-ONLY GATE (PREPARE)
    // =========================
    // Custom items can only be crafted in any vanilla Crafter block ("assembler").
    // A workbench / 2x2 grid shows the recipe book preview, but the result slot
    // is cleared so nothing can actually be crafted there — the player gets an
    // actionbar hint (throttled, the event fires on every slot change).
    // HIGHEST — runs after the per-item prepare handlers, so a handler that
    // re-sets the result cannot resurrect the preview outside the Crafter.
    private static final java.util.Map<java.util.UUID, Long> LAST_HINT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long HINT_THROTTLE_MS = 2_000L;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraftGate(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;
        if (!CUSTOM_RECIPES.contains(keyed.getKey())) return;

        if (e.getInventory().getType() != InventoryType.CRAFTER) {
            e.getInventory().setResult(null);

            if (e.getView().getPlayer() instanceof Player player) {
                long now = System.currentTimeMillis();
                Long last = LAST_HINT.get(player.getUniqueId());
                if (last == null || now - last >= HINT_THROTTLE_MS) {
                    LAST_HINT.put(player.getUniqueId(), now);
                    player.sendActionBar(MessageUtil.parse(MessagesManager.getString("crafting.crafter_only",
                            "<gold>✧</gold> <gray>This item can only be crafted in a</gray> <aqua>Crafter</aqua><gray>!</gray>")));
                }
            }
        }
    }

    // =========================
    // THROTTLE CLEANUP
    // =========================
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        LAST_HINT.remove(e.getPlayer().getUniqueId());
    }

    // =========================
    // CRAFTER BLOCK — REDSTONE AUTO-CRAFT
    // =========================
    // The vanilla Crafter powered by redstone does NOT fire the regular craft
    // events: it evaluates the recipe itself and fires CrafterCraftEvent,
    // dispensing event.getResult(). Re-apply the full custom result here so
    // PDC / lore / durability always survive an auto-craft.
    @EventHandler(ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;
        if (!CUSTOM_RECIPES.contains(keyed.getKey())) return;

        ItemStack result = recipe.getResult();
        if (result == null || result.getType().isAir()) return;

        e.setResult(result.clone());
    }

    // =========================
    // FINAL CRAFT — HARD BLOCK OUTSIDE THE CRAFTER
    // =========================
    // Safety net for any path the prepare gate missed (e.g. another plugin
    // re-setting the result at MONITOR): the actual craft is cancelled and
    // the player is told to use a Crafter.
    @EventHandler(ignoreCancelled = false)
    public void onCraft(CraftItemEvent e) {
        if (e.getInventory().getType() == InventoryType.CRAFTER) return;

        Recipe recipe = e.getRecipe();
        if (recipe instanceof Keyed keyed && CUSTOM_RECIPES.contains(keyed.getKey())) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player player) {
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("crafting.crafter_only",
                        "<gold>✧</gold> <gray>This item can only be crafted in a</gray> <aqua>Crafter</aqua><gray>!</gray>")));
            }
        }
    }

    // =========================
    // DISCOVER ALL CUSTOM RECIPES ON JOIN
    // =========================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (CUSTOM_RECIPES.isEmpty()) return;

        e.getPlayer().discoverRecipes(CUSTOM_RECIPES);
    }

    // =========================
    // INIT
    // =========================
    public static void init() {
        Main plugin = Main.getInstance();
        plugin.getServer().getPluginManager().registerEvents(new RecipeRegistry(), plugin);
        ConsoleLogger.info("[RECIPES] RecipeRegistry initialized (" + CUSTOM_RECIPES.size() + " recipes, Crafter-only).");
    }
}
