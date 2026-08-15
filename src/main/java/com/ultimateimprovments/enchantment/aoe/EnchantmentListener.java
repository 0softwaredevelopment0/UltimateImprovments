package com.ultimateimprovments.enchantment.aoe;

import com.ultimateimprovments.listener.BlockBreakListener;
import com.ultimateimprovments.mechanics.features.integrity.ItemIntegrityAPI;
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
 * Radius = enchantment level (max 255).
 * Sneaking disables AoE for precise single-block mining.
 */
public class EnchantmentListener implements Listener {

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
        if (!targetType.isBlock() || targetType.isAir() || targetType.isInteractable()) return;

        World world = origin.getWorld();
        int radius = Math.min(level, 255); // Clamp to max level

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

            // "Ore → stone" mechanic: leave stone instead of ore, like for
            // the block in BlockBreakEvent (otherwise holes remain in the veins)
            BlockBreakListener.scheduleStoneReplacement(block, brokenType);

            // Consume integrity as from breaking 1 block (mirrors PlayerItemDamageEvent
            // which IntegrityListener redirects to decreaseItemIntegrity(item, 1, player))
            ItemIntegrityAPI.decreaseItemIntegrity(tool, 1, player);

            // Tool broke from integrity loss — stop, as vanilla would
            if (tool.getAmount() <= 0) break;
        }
    }

    /**
     * Scans a cubic area around {@code origin} for blocks matching {@code targetType}.
     * 🛡 Limited to a 16×16×16 cube by radius around the origin (not by chunks):
     * offset −7..+8 on each axis; at level < 8 the radius is smaller (±level).
     * Only loaded chunks are scanned.
     */
    private @NotNull List<Location> scanBlocks(World world, Location origin,
                                                Material targetType, int radius) {
        List<Location> found = new ArrayList<>();

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        // 🛡 16×16×16 limit: radius no larger than the origin−7..origin+8 cube on each axis.
        int r = Math.min(radius, 8);
        int minX = Math.max(ox - r, ox - 7);
        int maxX = Math.min(ox + r, ox + 8);
        int minZ = Math.max(oz - r, oz - 7);
        int maxZ = Math.min(oz + r, oz + 8);
        int minY = Math.max(world.getMinHeight(), Math.max(oy - r, oy - 7));
        int maxY = Math.min(world.getMaxHeight() - 1, Math.min(oy + r, oy + 8));

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
