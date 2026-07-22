package com.ultimateimprovments.combat.weapons.core;

import java.util.*;

public class ProjectileManager {

    // =========================
    // MAIN STORAGE
    // =========================
    private static final List<BaseProjectile> PROJECTILES = new ArrayList<>();

    // =========================
    // SAFE ADD QUEUE
    // =========================
    private static final List<BaseProjectile> ADD_QUEUE = new ArrayList<>();

    // =========================
    // ADD PROJECTILE
    // =========================
    public static void add(BaseProjectile p) {
        ADD_QUEUE.add(p);
    }

    // =========================
    // MAIN TICK
    // =========================
    public static void tickAll() {

        if (!ADD_QUEUE.isEmpty()) {
            PROJECTILES.addAll(ADD_QUEUE);
            ADD_QUEUE.clear();
        }

        Iterator<BaseProjectile> it = PROJECTILES.iterator();

        while (it.hasNext()) {

            BaseProjectile p = it.next();

            if (p == null) {
                it.remove();
                continue;
            }

            p.tick();

            if (p.isDead()) {
                it.remove();
            }
        }
    }

    // =========================
    // PLASMA CHECK
    // =========================
    public static boolean hasPlasmaProjectiles() {

        for (BaseProjectile p : PROJECTILES) {
            if (p.getType() == ProjectileType.PLASMA) {
                return true;
            }
        }
        return false;
    }

    // =========================
    // REMOVE ALL PLASMA
    // =========================
    public static int removePlasmaProjectiles() {

        int removed = 0;

        Iterator<BaseProjectile> it = PROJECTILES.iterator();

        while (it.hasNext()) {

            BaseProjectile p = it.next();

            if (p.getType() == ProjectileType.PLASMA) {
                it.remove();
                removed++;
            }
        }

        return removed;
    }

    // =========================
    // DEBUG
    // =========================
    public static int getActiveCount() {
        return PROJECTILES.size();
    }

    public static void clearAll() {
        PROJECTILES.clear();
        ADD_QUEUE.clear();
    }

    public static List<BaseProjectile> getAll() {
        return Collections.unmodifiableList(PROJECTILES);
    }
}