package com.mcplugin.mechanics.particle;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines collision recipes for particle accelerator:
 * - Two particles collide at the right speed range → new item
 * - Speed is percentage of MAX_SPEED (5.0 blocks/tick = 100%)
 */
public class ParticleCollisionHandler {

    private static final List<CollisionRecipe> recipes = new ArrayList<>();

    static {
        // Gold + Diamond at 5-10% speed of light → Emerald
        register(new CollisionRecipe(
                Material.GOLD_INGOT, Material.DIAMOND,
                5.0, 10.0,
                Material.EMERALD
        ));
        // Diamond + Emerald at 10-15% speed of light → Netherite Scrap
        register(new CollisionRecipe(
                Material.DIAMOND, Material.EMERALD,
                10.0, 15.0,
                Material.NETHERITE_SCRAP
        ));
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
