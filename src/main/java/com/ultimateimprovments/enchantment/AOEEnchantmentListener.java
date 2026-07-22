package com.ultimateimprovments.enchantment;

import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener: Area-of-Effect (AoE) block breaking.
 * <p>
 * When a player breaks a block with an AoE tool,
 * all blocks of the same type within the radius also break.
 * <p>
 * Radius = enchantment level (max 255).
 * Sneaking disables AoE for precise single-block mining.
 */
public class AOEEnchantmentListener implements Listener {

    /**
     * Maximum blocks to break in one event to prevent server lag.
     */
    private static final int MAX_BLOCKS_PER_EVENT = 500;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Sneaking = precise single-block mining
        if (player.isSneaking()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        // Get AoE level from PDC
        int level = AOEEnchantment.getLevel(tool);
        if (level <= 0) return;

        Block originBlock = event.getBlock();
        Material targetType = originBlock.getType();
        Location origin = LocationUtil.normalize(originBlock.getLocation());
        if (origin == null || origin.getWorld() == null) return;

        // Skip non-solids, fluids, and instant-break blocks
        if (!targetType.isBlock() || targetType.isAir() || targetType.isInteractable()) return;

        World world = origin.getWorld();
        int radius = Math.min(level, 255); // Clamp to max level

        // Scan and collect matching blocks
        List<Location> targets = scanBlocks(world, origin, targetType, radius);

        if (targets.isEmpty()) return;

        // Break all matching blocks (the original one is handled by the event)
        int brokenCount = 0;
        for (Location loc : targets) {
            // Skip the original block
            if (loc.equals(origin)) continue;

            Block block = world.getBlockAt(loc);
            if (block.getType() != targetType) continue;

            // Check world border
            if (!world.getWorldBorder().isInside(loc)) continue;

            // Check if player can build here (basic permission check)
            // Note: full WorldGuard/GriefPrevention integration would need external hooks
            if (!player.hasPermission("ui.enchant.aoe.bypass")) {
                if (!loc.getBlock().isPreferredTool(tool)) continue;
            }

            // Break naturally with tool (respects Silk Touch, Fortune)
            block.breakNaturally(tool, true);

            // Consume 1 durability for each AoE-broken block
            damageTool(tool, 1);

            brokenCount++;

            if (brokenCount >= MAX_BLOCKS_PER_EVENT) break;
        }
    }

    /**
     * Damages the tool item by the given amount.
     */
    private void damageTool(@NotNull ItemStack tool, int amount) {
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int newDamage = damageable.getDamage() + amount;
            int maxDamage = tool.getType().getMaxDurability();
            if (newDamage >= maxDamage) {
                // Tool breaks — remove it
                tool.setAmount(0);
                // Play break sound at player's location (handled by server)
            } else {
                damageable.setDamage(newDamage);
                tool.setItemMeta(meta);
            }
        }
    }

    /**
     * Scans a cubic area around {@code origin} for blocks matching {@code targetType},
     * iterating by Chebyshev distance layers (closest blocks first).
     * <p>
     * Each layer is the shell of the cube where max(|dx|,|dy|,|dz|) == layer.
     * Only scans already-loaded chunks. Stops early once the limit is reached.
     */
    private @NotNull List<Location> scanBlocks(World world, Location origin,
                                                Material targetType, int radius) {
        List<Location> found = new ArrayList<>();

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        int minY = Math.max(world.getMinHeight(), oy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + radius);

        // Scan each Chebyshev layer (cube shell) from nearest to farthest
        outer:
        for (int layer = 1; layer <= radius; layer++) {
            // ── Top & bottom faces: y = ±layer ──
            for (int dx = -layer; dx <= layer; dx++) {
                int x = ox + dx;
                for (int dz = -layer; dz <= layer; dz++) {
                    int z = oz + dz;

                    // Top face: y = +layer
                    int yTop = oy + layer;
                    if (yTop <= maxY && world.isChunkLoaded(x >> 4, z >> 4)
                            && world.getBlockAt(x, yTop, z).getType() == targetType) {
                        found.add(new Location(world, x, yTop, z));
                        if (found.size() >= MAX_BLOCKS_PER_EVENT) break outer;
                    }

                    // Bottom face: y = -layer
                    int yBot = oy - layer;
                    if (yBot >= minY && world.isChunkLoaded(x >> 4, z >> 4)
                            && world.getBlockAt(x, yBot, z).getType() == targetType) {
                        found.add(new Location(world, x, yBot, z));
                        if (found.size() >= MAX_BLOCKS_PER_EVENT) break outer;
                    }
                }
            }

            // ── Side faces: x = ±layer AND z = ±layer (excluding y = ±layer edges) ──
            for (int dy = -(layer - 1); dy <= (layer - 1); dy++) {
                int y = oy + dy;
                if (y < minY || y > maxY) continue;

                for (int dz = -layer; dz <= layer; dz++) {
                    int z = oz + dz;

                    // x = +layer face
                    int xPos = ox + layer;
                    if (world.isChunkLoaded(xPos >> 4, z >> 4)
                            && world.getBlockAt(xPos, y, z).getType() == targetType) {
                        found.add(new Location(world, xPos, y, z));
                        if (found.size() >= MAX_BLOCKS_PER_EVENT) break outer;
                    }

                    // x = -layer face
                    int xNeg = ox - layer;
                    if (world.isChunkLoaded(xNeg >> 4, z >> 4)
                            && world.getBlockAt(xNeg, y, z).getType() == targetType) {
                        found.add(new Location(world, xNeg, y, z));
                        if (found.size() >= MAX_BLOCKS_PER_EVENT) break outer;
                    }
                }

                // z = +layer face (only for |dx| < layer, i.e. excluding x = ±layer edges)
                for (int dx = -(layer - 1); dx <= (layer - 1); dx++) {
                    int x = ox + dx;
                    int zPos = oz + layer;
                    if (world.isChunkLoaded(x >> 4, zPos >> 4)
                            && world.getBlockAt(x, y, zPos).getType() == targetType) {
                        found.add(new Location(world, x, y, zPos));
                        if (found.size() >= MAX_BLOCKS_PER_EVENT) break outer;
                    }

                    // z = -layer face
                    int zNeg = oz - layer;
                    if (world.isChunkLoaded(x >> 4, zNeg >> 4)
                            && world.getBlockAt(x, y, zNeg).getType() == targetType) {
                        found.add(new Location(world, x, y, zNeg));
                        if (found.size() >= MAX_BLOCKS_PER_EVENT) break outer;
                    }
                }
            }
        }

        return found;
    }
}
