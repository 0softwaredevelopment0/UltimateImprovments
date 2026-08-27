package com.ultimateimprovments.enchantment.veinminer;

import com.ultimateimprovments.listener.BlockBreakListener;
import com.ultimateimprovments.mechanics.features.integrity.ItemIntegrityAPI;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Listener: VeinMiner block breaking.
 * <p>
 * When a player breaks an ore with a VeinMiner pickaxe, the whole connected
 * vein of the SAME ore type is broken at once (6-direction flood fill).
 * <p>
 * Every extra block consumed:
 * <ul>
 *   <li>drops naturally with the tool (respects Silk Touch / Fortune);</li>
 *   <li>consumes 1 unit of tool integrity ({@link ItemIntegrityAPI#decreaseItemIntegrity})
 *       — the same cost as breaking one block by hand.</li>
 * </ul>
 * Sneaking disables VeinMiner for precise single-block mining (same as AoE).
 */
public class EnchantmentListener implements Listener {

    /** 6-direction offsets (axis-aligned neighbours). */
    private static final int[][] DIRS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Sneaking = precise single-block mining
        if (player.isSneaking()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        // VeinMiner level (real enchantment, with PDC failsafe fallback)
        if (com.ultimateimprovments.enchantment.veinminer.Enchantment.getLevel(tool) <= 0) return;

        Block origin = event.getBlock();
        Material oreType = origin.getType();
        if (!isOre(oreType)) return;

        World world = origin.getWorld();
        if (world == null) return;

        // Collect the whole connected vein (same ore type only).
        Set<Block> vein = collectVein(world, origin, oreType);
        if (vein.isEmpty()) return;

        for (Block block : vein) {
            // Skip the original block (the event already handles it — including the
            // vanilla tool-damage event chain for that one block).
            if (block.equals(origin)) continue;

            if (block.getType() != oreType) continue;

            // Check world border & chunk
            Location loc = block.getLocation();
            if (!world.getWorldBorder().isInside(loc)) continue;
            if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) continue;

            // Break naturally with the tool (respects Silk Touch, Fortune)
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
     * Flood-fills from {@code origin} collecting all blocks of {@code oreType}
     * reachable through 6-axis adjacency. Limited to a 16×16×16 cube
     * by radius around the origin (offset −7..+8 on each axis).
     */
    private @NotNull Set<Block> collectVein(World world, Block origin, Material oreType) {
        Set<Block> vein = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();

        // 🛡 Failsafe: VeinMiner does not go beyond the 16×16×16 cube around the origin
        // (by radius, not by chunks): offset −7..+8 on each axis.
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        int minX = ox - 7, maxX = ox + 8;
        int minY = oy - 7, maxY = oy + 8;
        int minZ = oz - 7, maxZ = oz + 8;

        queue.add(origin);
        vein.add(origin);

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            int x = current.getX();
            int y = current.getY();
            int z = current.getZ();

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (ny < world.getMinHeight() || ny >= world.getMaxHeight()) continue;
                // 🛡 Failsafe: 16×16×16 cube by radius around the origin
                if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ) continue;
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;

                Block neighbor = world.getBlockAt(nx, ny, nz);
                if (neighbor.getType() != oreType) continue;
                if (vein.contains(neighbor)) continue;

                vein.add(neighbor);
                queue.add(neighbor);
            }
        }

        return vein;
    }

    /**
     * Whether the material is a mineable ore (raw ore block).
     */
    private static boolean isOre(Material material) {
        if (material == null || !material.isBlock()) return false;
        String name = material.name();
        // All vanilla ores (incl. deepslate & nether variants) end with _ORE;
        // ancient debris is the only raw ore that doesn't.
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }
}
