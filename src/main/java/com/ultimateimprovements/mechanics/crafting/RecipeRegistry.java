package com.ultimateimprovements.mechanics.crafting;

import com.ultimateimprovements.core.Main;
import com.ultimateimprovements.util.ConsoleLogger;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

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
     * @return неизменяемое множество всех зарегистрированных кастомных рецептов
     */
    public static Set<NamespacedKey> getCustomRecipes() {
        return Collections.unmodifiableSet(CUSTOM_RECIPES);
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
        ConsoleLogger.info("[RECIPES] RecipeRegistry initialized (" + CUSTOM_RECIPES.size() + " recipes).");
    }
}
