package com.ultimateimprovments.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Instant fish bite with water volume checking.
 *
 * Scans WATER blocks within a radius of 5 from the hook:
 *   ≥ 128 → instant bite
 *   < 128 → "No fish at all", the hook is removed
 *
 * The scan result is cached by block coordinates.
 * The cache is invalidated when any block changes within a radius of 5 from the cached position
 * (BlockBreak, BlockPlace, BlockFromTo).
 */
public class FishingListener extends BukkitRunnable implements Listener {

    private static FishingListener instance;

    /** Active hooks (entityId -> hook) */
    private final Map<Integer, FishHook> activeHooks = new HashMap<>();

    /**
     * Scan result cache: "world,x,y,z" -> true (has fish, ≥128 water) / false (no fish)
     */
    private final Map<String, Boolean> waterCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 500;

    // =========================
    // WATER CHECK PARAMETERS
    // =========================
    private static final int WATER_CHECK_RADIUS = 5;
    private static final int MIN_WATER_BLOCKS = 128;

    // =========================
    // NMS REFLECTION (cache)
    // =========================
    private static Method craftGetHandle;
    private static Field timeUntilLuredField;
    private static Field timeUntilHookedField;
    private static boolean reflectionReady = false;
    private static boolean reflectionAttempted = false;

    public static FishingListener getInstance() {
        if (instance == null) {
            instance = new FishingListener();
        }
        return instance;
    }

    // =========================
    // CACHE KEY
    // =========================
    private static String cacheKey(World world, int x, int y, int z) {
        return world.getName() + "," + x + "," + y + "," + z;
    }

    private static String cacheKey(Location loc) {
        return cacheKey(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // =========================
    // EVENT: ROD CAST
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.FISHING) return;

        FishHook hook = e.getHook();
        if (hook == null) return;

        // Zero the timers BEFORE the hook hits the water
        hook.setWaitTime(0);
        hook.setMinWaitTime(0);
        hook.setMaxWaitTime(0);

        activeHooks.put(hook.getEntityId(), hook);
    }

    // =========================
    // TASK: CHECK EVERY TICK
    // =========================
    @Override
    public void run() {
        if (activeHooks.isEmpty()) return;

        Iterator<Map.Entry<Integer, FishHook>> it = activeHooks.entrySet().iterator();

        while (it.hasNext()) {
            FishHook hook = it.next().getValue();

            // Hook is dead — remove it
            if (hook.isDead() || !hook.isValid()) {
                it.remove();
                continue;
            }

            Location loc = hook.getLocation();

            // Not in water yet — wait
            if (!loc.getBlock().isLiquid()) continue;

            // The player who owns the hook
            Player player = (Player) hook.getShooter();
            if (player == null) {
                it.remove();
                continue;
            }

            // =========================
            // CACHE CHECK / WATER SCAN
            // =========================
            String key = cacheKey(loc);

            Boolean cached = waterCache.get(key);

            if (cached == null) {
                // Not in cache — scan the water blocks
                int waterCount = countWaterBlocks(loc);
                cached = (waterCount >= MIN_WATER_BLOCKS);

                // Add to cache (clear on overflow)
                if (waterCache.size() >= MAX_CACHE_SIZE) {
                    waterCache.clear();
                }
                waterCache.put(key, cached);
            }

            if (!cached) {
                // Not enough water — "no fish at all"
                hook.remove();
                it.remove();
                player.sendMessage(MessageUtil.parse(MessagesManager.getString("fishing.no_fish", "<dark_gray>[<red>⛔</red>] <red>No fish at all!</red></dark_gray>")));
                continue;
            }

            // =========================
            // ENOUGH WATER — INSTANT BITE
            // =========================
            hook.setWaitTime(0);
            hook.setMinWaitTime(0);
            hook.setMaxWaitTime(0);

            // NMS reflection (best-effort)
            forceInstantBiteNMS(hook);

            // Remove from tracking
            it.remove();
        }
    }

    // =========================
    // CACHE INVALIDATION ON BLOCK CHANGES
    // =========================

    /**
     * Block broken — invalidate the nearby cache.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        invalidateCacheNear(e.getBlock());
    }

    /**
     * Block placed — invalidate the nearby cache.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        invalidateCacheNear(e.getBlock());
    }

    /**
     * Liquid (water/lava) flowed — invalidate the cache at the source and the destination.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        invalidateCacheNear(e.getBlock());
        invalidateCacheNear(e.getToBlock());
    }

    /**
     * Removes from the cache all entries within WATER_CHECK_RADIUS
     * of the changed block.
     */
    private void invalidateCacheNear(Block changed) {
        if (waterCache.isEmpty()) return;

        String changedWorld = changed.getWorld().getName();
        int cx = changed.getX();
        int cy = changed.getY();
        int cz = changed.getZ();

        Iterator<Map.Entry<String, Boolean>> it = waterCache.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Boolean> entry = it.next();
            String key = entry.getKey();

            // Parse the "world,x,y,z" key
            int firstComma = key.indexOf(',');
            if (firstComma == -1) { it.remove(); continue; }

            String worldName = key.substring(0, firstComma);
            if (!worldName.equals(changedWorld)) continue;

            int secondComma = key.indexOf(',', firstComma + 1);
            if (secondComma == -1) { it.remove(); continue; }

            int thirdComma = key.indexOf(',', secondComma + 1);
            if (thirdComma == -1) { it.remove(); continue; }

            try {
                int kx = Integer.parseInt(key.substring(firstComma + 1, secondComma));
                int ky = Integer.parseInt(key.substring(secondComma + 1, thirdComma));
                int kz = Integer.parseInt(key.substring(thirdComma + 1));

                int dx = Math.abs(cx - kx);
                int dy = Math.abs(cy - ky);
                int dz = Math.abs(cz - kz);

                if (dx <= WATER_CHECK_RADIUS && dy <= WATER_CHECK_RADIUS && dz <= WATER_CHECK_RADIUS) {
                    it.remove();
                }
            } catch (NumberFormatException e) {
                ConsoleLogger.warn("[Fishing] Invalid cache key format: " + e.getMessage());
                it.remove();
            }
        }
    }

    // =========================
    // COUNT WATER BLOCKS
    // =========================
    private int countWaterBlocks(Location center) {
        int count = 0;
        int bx = center.getBlockX();
        int by = center.getBlockY();
        int bz = center.getBlockZ();

        for (int dx = -WATER_CHECK_RADIUS; dx <= WATER_CHECK_RADIUS && count < MIN_WATER_BLOCKS; dx++) {
            for (int dz = -WATER_CHECK_RADIUS; dz <= WATER_CHECK_RADIUS && count < MIN_WATER_BLOCKS; dz++) {
                for (int dy = -WATER_CHECK_RADIUS; dy <= WATER_CHECK_RADIUS && count < MIN_WATER_BLOCKS; dy++) {
                    Block block = center.getWorld().getBlockAt(bx + dx, by + dy, bz + dz);
                    if (block.getType() == Material.WATER) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // =========================
    // NMS REFLECTION
    // =========================
    private static void initReflection(FishHook hook) {
        if (reflectionAttempted) return;
        reflectionAttempted = true;

        try {
            Class<?> craftClass = hook.getClass();
            craftGetHandle = craftClass.getMethod("getHandle");
            Object nmsHook = craftGetHandle.invoke(hook);
            Class<?> nmsClass = nmsHook.getClass();

            try {
                timeUntilLuredField = nmsClass.getDeclaredField("timeUntilLured");
                timeUntilLuredField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                ConsoleLogger.warn("[Fishing] No timeUntilLured field: " + e.getMessage());
            }

            try {
                timeUntilHookedField = nmsClass.getDeclaredField("timeUntilHooked");
                timeUntilHookedField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                ConsoleLogger.warn("[Fishing] No timeUntilHooked field: " + e.getMessage());
            }

            reflectionReady = (timeUntilLuredField != null || timeUntilHookedField != null);
        } catch (Exception e) {
            ConsoleLogger.warn("[Fishing] initReflection error: " + e.getMessage());
        }
    }

    private void forceInstantBiteNMS(FishHook hook) {
        if (!reflectionReady && !reflectionAttempted) {
            initReflection(hook);
        }
        if (!reflectionReady) return;

        try {
            Object nmsHook = craftGetHandle.invoke(hook);
            if (timeUntilLuredField != null) {
                timeUntilLuredField.setInt(nmsHook, 0);
            }
            if (timeUntilHookedField != null) {
                timeUntilHookedField.setInt(nmsHook, 0);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Fishing] forceInstantBiteNMS error: " + e.getMessage());
        }
    }
}
