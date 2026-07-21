package com.mcplugin.mechanics.particle;

import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Defines collision recipes for particle accelerator:
 * - Two particles collide at the right speed range → new item
 * - Speed is percentage of MAX_SPEED (5.0 blocks/tick = 100%)
 * <p>
 * Рецепты загружаются из config.yml (particle_accelerator.collision_recipes).
 */
public class ParticleCollisionHandler {

    private static final List<CollisionRecipe> recipes = new ArrayList<>();

    private static boolean loaded = false;

    /**
     * Загружает рецепты коллизий из config.yml.
     * Вызывается из {@link ParticleAcceleratorManager#init(Main)}.
     * <p>
     * Если секция отсутствует или пуста, используются хардкодные дефолты
     * (Gold+Di→Em, Di+Em→Netherite Scrap).
     */
    public static void loadConfig(Main plugin) {
        if (loaded) {
            recipes.clear(); // allow reload
        }
        loaded = true;

        List<?> rawList = plugin.getConfig().getList("particle_accelerator.collision_recipes");
        if (rawList == null || rawList.isEmpty()) {
            // Загружаем дефолты, если нет секции в конфиге
            loadDefaults();
            return;
        }

        int count = 0;
        for (int idx = 0; idx < rawList.size(); idx++) {
            Object raw = rawList.get(idx);
            if (!(raw instanceof Map<?, ?> recipeMap)) continue;
            try {
                String matAStr = (String) recipeMap.get("material_a");
                String matBStr = (String) recipeMap.get("material_b");
                String resultStr = (String) recipeMap.get("result");
                double speedMin = ((Number) recipeMap.get("speed_min_pct")).doubleValue();
                double speedMax = ((Number) recipeMap.get("speed_max_pct")).doubleValue();

                if (matAStr == null || matBStr == null || resultStr == null) {
                    ConsoleLogger.warn("[ParticleCollision] Skipping recipe #" + idx + ": missing material_a, material_b, or result");
                    continue;
                }

                Material matA = Material.getMaterial(matAStr.toUpperCase());
                Material matB = Material.getMaterial(matBStr.toUpperCase());
                Material result = Material.getMaterial(resultStr.toUpperCase());

                if (matA == null || matB == null || result == null) {
                    ConsoleLogger.warn("[ParticleCollision] Skipping recipe #" + idx + ": unknown material(s)");
                    continue;
                }

                register(new CollisionRecipe(matA, matB, speedMin, speedMax, result));
                count++;
            } catch (Exception e) {
                ConsoleLogger.warn("[ParticleCollision] Failed to load recipe #" + idx + ": " + e.getMessage());
            }
        }

        if (count == 0) {
            ConsoleLogger.info("[ParticleCollision] No recipes in config, loading defaults.");
            loadDefaults();
        } else {
            ConsoleLogger.info("[ParticleCollision] Loaded " + count + " collision recipe(s) from config.");
        }
    }

    private static void loadDefaults() {
        register(new CollisionRecipe(
                Material.GOLD_INGOT, Material.DIAMOND,
                5.0, 10.0,
                Material.EMERALD
        ));
        register(new CollisionRecipe(
                Material.DIAMOND, Material.EMERALD,
                10.0, 15.0,
                Material.NETHERITE_SCRAP
        ));
        ConsoleLogger.info("[ParticleCollision] Loaded " + recipes.size() + " default collision recipe(s).");
    }

    private ParticleCollisionHandler() {}

    public static void register(CollisionRecipe recipe) {
        recipes.add(recipe);
    }

    /**
     * Check if two materials at a given speed produce a collision result.
     * @return result Material, or AIR if no match
     */
    public static Material checkRecipe(Material a, Material b, double speedPct) {
        for (CollisionRecipe r : recipes) {
            if (r.matches(a, b, speedPct)) {
                return r.result;
            }
        }
        return Material.AIR;
    }

    public static class CollisionRecipe {
        public final Material matA;
        public final Material matB;
        public final double speedMinPct;
        public final double speedMaxPct;
        public final Material result;

        public CollisionRecipe(Material matA, Material matB, double speedMinPct, double speedMaxPct, Material result) {
            this.matA = matA;
            this.matB = matB;
            this.speedMinPct = speedMinPct;
            this.speedMaxPct = speedMaxPct;
            this.result = result;
        }

        public boolean matches(Material a, Material b, double speedPct) {
            // Check materials (order-independent)
            boolean matMatch = (a == matA && b == matB) || (a == matB && b == matA);
            if (!matMatch) return false;

            // Check speed range
            return speedPct >= speedMinPct && speedPct <= speedMaxPct;
        }
    }
}
