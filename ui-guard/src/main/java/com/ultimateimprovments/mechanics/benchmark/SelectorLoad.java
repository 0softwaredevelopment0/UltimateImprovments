package com.ultimateimprovments.mechanics.benchmark;

import com.ultimateimprovments.core.Main;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.List;

/**
 * {@code selector} load — spam of iterations over every entity on the server.
 * <p>
 * Each cycle re-collects the global entity list ({@code world.getEntities()} is
 * an O(n) copy) a number of times proportional to the power level and walks it
 * with the same getters a selector-heavy plugin would call, plus periodic
 * {@code getNearbyEntities} spatial queries. This measures list copying, entity
 * lookups and AABB-tree queries rather than AI.
 */
public final class SelectorLoad implements StressLoad {

    /** Iterations over the whole entity list per cycle at power = minimal. */
    private static final int BASE_ITERATIONS = 4;

    private final StressPower power;

    private Location anchor;
    private Location scratch;
    private int iterations;
    private long work;

    public SelectorLoad(StressPower power) {
        this.power = power;
    }

    @Override
    public void start(Location anchor) {
        this.anchor = anchor;
        this.scratch = new Location(anchor != null ? anchor.getWorld() : null, 0.0D, 0.0D, 0.0D);
        this.iterations = BASE_ITERATIONS * power.multiplier();
    }

    @Override
    public void tick() {
        for (int iteration = 0; iteration < iterations; iteration++) {
            for (World world : Main.getInstance().getServer().getWorlds()) {
                List<Entity> entities = world.getEntities();
                for (Entity entity : entities) {
                    entity.getType();
                    entity.getLocation(scratch);
                    entity.getBoundingBox();
                    entity.isInsideVehicle();
                    entity.isDead();
                    entity.getScoreboardTags().size();
                    entity.customName();
                    entity.isSilent();
                    work++;
                }
            }
            // Spatial query: the expensive part of most selector implementations.
            if (anchor != null && anchor.getWorld() != null && (iteration & 3) == 0) {
                anchor.getWorld().getNearbyEntities(anchor, 16.0D, 16.0D, 16.0D);
            }
        }
    }

    @Override
    public void stop() {
        anchor = null;
    }

    @Override
    public long work() {
        return work;
    }
}
