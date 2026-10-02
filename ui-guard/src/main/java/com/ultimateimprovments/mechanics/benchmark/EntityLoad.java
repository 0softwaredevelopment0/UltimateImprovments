package com.ultimateimprovments.mechanics.benchmark;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code entity} load — repeated spawning of entities around the anchor.
 * <p>
 * Measures the cost of entity creation (PDC, tracker registration) plus the
 * steady-state cost of ticking AI, collision and entity tracking once the
 * {@code stresstest.max_entities} cap is reached. Every spawned entity is
 * tagged with the {@link #MARKER} PDC key so leftovers can be swept after a
 * crash ({@link #sweepAllWorlds()}) and are always removed by {@link #stop()}.
 */
public final class EntityLoad implements StressLoad {

    /** PDC marker on every entity spawned by a stress test (namespace "ui"). */
    public static final NamespacedKey MARKER = new NamespacedKey("ui", "stresstest");

    private final StressPower power;
    private final List<Entity> spawned = new ArrayList<>();

    private Location anchor;
    private EntityType entityType;
    private int maxEntities;
    private int perCycle;
    private long work;
    private boolean capReported;

    public EntityLoad(StressPower power) {
        this.power = power;
    }

    @Override
    public void start(Location anchor) {
        this.anchor = anchor;
        this.entityType = resolveEntityType();
        this.maxEntities = Math.max(1, StressTestManager.cfg().getInt("stresstest.max_entities", 2000));
        this.perCycle = power.multiplier();
        if (anchor == null || anchor.getWorld() == null) {
            throw new IllegalStateException("no world to spawn entities in");
        }
    }

    @Override
    public void tick() {
        if (anchor == null || entityType == null) return;
        World world = anchor.getWorld();
        for (int i = 0; i < perCycle; i++) {
            pruneDead();
            if (spawned.size() >= maxEntities) {
                if (!capReported) {
                    capReported = true;
                    ConsoleLogger.warn("[StressTest] entity cap reached (" + maxEntities
                            + ", stresstest.max_entities) — spawning paused, ticking load continues.");
                }
                return;
            }
            Location spot = anchor.clone().add(
                    ThreadLocalRandom.current().nextDouble(-2.5, 2.5),
                    0.5D,
                    ThreadLocalRandom.current().nextDouble(-2.5, 2.5));
            Entity entity = world.spawnEntity(spot, entityType);
            entity.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
            spawned.add(entity);
            work++;
        }
    }

    @Override
    public void stop() {
        for (Entity entity : spawned) {
            try {
                if (!entity.isDead()) {
                    entity.remove();
                }
            } catch (Throwable ignored) {
                // The entity may already be gone (world unloading, plugin kill).
            }
        }
        spawned.clear();
        capReported = false;
    }

    @Override
    public long work() {
        return work;
    }

    /** Drops references to entities that died or were removed by someone else. */
    private void pruneDead() {
        spawned.removeIf(entity -> !entity.isValid());
    }

    /** Reads {@code stresstest.entity_type} and falls back to a cow. */
    private static EntityType resolveEntityType() {
        String raw = StressTestManager.cfg().getString("stresstest.entity_type", "COW");
        if (raw == null || raw.isBlank()) raw = "COW";
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            ConsoleLogger.warn("[StressTest] Unknown stresstest.entity_type '" + raw
                    + "' — falling back to COW.");
            return EntityType.COW;
        }
    }

    /**
     * Removes every leftover stress-test entity in all loaded worlds.
     * Needed after a crash or a hard kill, when {@link #stop()} never ran.
     *
     * @return how many entities were removed
     */
    static int sweepAllWorlds() {
        int removed = 0;
        for (World world : Main.getInstance().getServer().getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                try {
                    if (entity.getPersistentDataContainer().has(MARKER, PersistentDataType.BYTE)) {
                        entity.remove();
                        removed++;
                    }
                } catch (Throwable ignored) {
                    // Exotic entity implementations may not expose a PDC.
                }
            }
        }
        return removed;
    }
}
