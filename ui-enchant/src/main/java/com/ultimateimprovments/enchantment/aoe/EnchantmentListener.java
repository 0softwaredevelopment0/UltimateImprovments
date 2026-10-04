package com.ultimateimprovments.enchantment.aoe;

import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.mechanics.features.integrity.ItemDurabilityUtil;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener: Area-of-Effect (AoE) block breaking.
 * <p>
 * When a player breaks a block with an AoE tool,
 * all blocks of the same type within the radius also break.
 * <p>
 * Radius = enchantment level (max 10).
 * Sneaking disables AoE for precise single-block mining.
 */
public class EnchantmentListener implements Listener {

    // Material#isInteractable() below is deprecated as unreliable with no
    // replacement — kept deliberately to preserve the AoE block filter.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Sneaking = precise single-block mining
        if (player.isSneaking()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        // Get AoE level (real enchantment, with PDC failsafe fallback)
        int level = Enchantment.getLevel(tool);
        if (level <= 0) return;

        Block originBlock = event.getBlock();
        Material targetType = originBlock.getType();
        Location origin = LocationUtil.normalize(originBlock.getLocation());
        if (origin == null || origin.getWorld() == null) return;

        // Skip non-solids, fluids, and instant-break blocks
        // Material#isInteractable() is deprecated as unreliable in Paper 26.3
        // with no replacement — kept deliberately to preserve the AoE filter.
        if (!targetType.isBlock() || targetType.isAir() || targetType.isInteractable()) return;

        World world = origin.getWorld();
        int radius = Math.min(level, Enchantment.MAX_LEVEL); // Clamp to max level

        // Scan and collect matching blocks
        List<Location> targets = scanBlocks(world, origin, targetType, radius);

        if (targets.isEmpty()) return;

        // Break all matching blocks (the original one is handled by the event)
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
            // Remember the type BEFORE breaking — after breakNaturally() the block is already AIR
            Material brokenType = block.getType();
            block.breakNaturally(tool, true);

            // Count toward the Woodcutter timed challenge (no BlockBreakEvent fires here)
            CoreHooks.onBlockBroken(player, brokenType);

            // "Ore → stone" mechanic: leave stone instead of ore, like for
            // the block in BlockBreakEvent (otherwise holes remain in the veins)
            CoreHooks.scheduleOreStone(block, brokenType);

            // Consume integrity as from breaking 1 block (mirrors PlayerItemDamageEvent
            // which the durability system maps to 1 vanilla durability point)
            ItemDurabilityUtil.decreaseItemIntegrity(tool, 1, player);

            // Tool broke from integrity loss — stop, as vanilla would
            if (tool.getAmount() <= 0) break;
        }
    }

    /**
     * Scans a cubic area around {@code origin} for blocks matching {@code targetType}.
     * 🛡 Limited to a 21×21×21 cube by radius around the origin (not by chunks):
     * at most ±10 blocks on each axis; at level < 10 the radius is smaller (±level).
     * Only loaded chunks are scanned.
     */
    private @NotNull List<Location> scanBlocks(World world, Location origin,
                                                Material targetType, int radius) {
        List<Location> found = new ArrayList<>();

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        // 🛡 21×21×21 limit: radius no larger than 10 blocks from the origin on each axis.
        int r = Math.min(radius, 10);
        int minX = ox - r;
        int maxX = ox + r;
        int minZ = oz - r;
        int maxZ = oz + r;
        int minY = Math.max(world.getMinHeight(), oy - r);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + r);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    if (world.getBlockAt(x, y, z).getType() == targetType) {
                        found.add(new Location(world, x, y, z));
                    }
                }
            }
        }

        return found;
    }
}
