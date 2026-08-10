package com.ultimateimprovments.enchantment.treecapitator;

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
 * Listener: TreeCapitator block breaking.
 * <p>
 * When a player breaks a log with a TreeCapitator axe, the whole tree is felled:
 * every connected log/stem/branch of ANY wood type is broken at once
 * (6-direction flood fill). Stripped logs are NOT part of a living tree and are
 * left alone (prevents accidentally felling player-built log structures).
 * <p>
 * Every extra log consumed:
 * <ul>
 *   <li>drops naturally with the tool;</li>
 *   <li>consumes 1 unit of tool integrity ({@link ItemIntegrityAPI#decreaseItemIntegrity})
 *       — the same cost as breaking one block by hand.</li>
 * </ul>
 * Sneaking disables TreeCapitator for precise single-block cutting (same as AoE).
 */
public class EnchantmentListener implements Listener {

    /** Maximum blocks to break in one event to prevent server lag. */
    private static final int MAX_BLOCKS_PER_EVENT = 500;

    /** 6-direction offsets (axis-aligned neighbours). */
    private static final int[][] DIRS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Sneaking = precise single-block cutting
        if (player.isSneaking()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        // TreeCapitator level (real enchantment, with PDC failsafe fallback)
        if (com.ultimateimprovments.enchantment.treecapitator.Enchantment.getLevel(tool) <= 0) return;

        Block origin = event.getBlock();
        Material originType = origin.getType();
        if (!isLog(originType)) return;

        World world = origin.getWorld();
        if (world == null) return;

        // Collect the whole connected tree (logs of any wood type, incl. branches).
        Set<Block> tree = collectTree(world, origin);
        if (tree.isEmpty()) return;

        int brokenCount = 0;
        for (Block block : tree) {
            // Skip the original block (the event already handles it — including the
            // vanilla tool-damage event chain for that one block).
            if (block.equals(origin)) continue;

            if (!isLog(block.getType())) continue;

            // Check world border & chunk
            Location loc = block.getLocation();
            if (!world.getWorldBorder().isInside(loc)) continue;
            if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) continue;

            // Break naturally with the tool (respects Silk Touch, Fortune)
            block.breakNaturally(tool, true);

            // Consume integrity as from breaking 1 block (mirrors PlayerItemDamageEvent
            // which IntegrityListener redirects to decreaseItemIntegrity(item, 1, player))
            ItemIntegrityAPI.decreaseItemIntegrity(tool, 1, player);

            brokenCount++;

            // Tool broke from integrity loss — stop, as vanilla would
            if (tool.getAmount() <= 0) break;

            if (brokenCount >= MAX_BLOCKS_PER_EVENT) break;
        }
    }

    /**
     * Flood-fills from {@code origin} collecting every connected log/stem block
     * (any wood type, non-stripped). Stops early once the limit is reached.
     */
    private @NotNull Set<Block> collectTree(World world, Block origin) {
        Set<Block> tree = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();

        queue.add(origin);
        tree.add(origin);

        while (!queue.isEmpty() && tree.size() < MAX_BLOCKS_PER_EVENT) {
            Block current = queue.poll();
            int x = current.getX();
            int y = current.getY();
            int z = current.getZ();

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (ny < world.getMinHeight() || ny >= world.getMaxHeight()) continue;
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;

                Block neighbor = world.getBlockAt(nx, ny, nz);
                if (!isLog(neighbor.getType())) continue;
                if (tree.contains(neighbor)) continue;

                tree.add(neighbor);
                queue.add(neighbor);

                if (tree.size() >= MAX_BLOCKS_PER_EVENT) break;
            }
        }

        return tree;
    }

    /**
     * Whether the material is a tree log/stem (non-stripped).
     * Covers {@code *_LOG}, {@code *_WOOD}, {@code *_STEM}, {@code *_HYPHAE}
     * (oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, pale oak,
     * crimson, warped). Stripped variants are excluded — they are player-built,
     * not part of a living tree.
     */
    private static boolean isLog(Material material) {
        if (material == null || !material.isBlock()) return false;
        String name = material.name();
        if (name.contains("STRIPPED")) return false;
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE");
    }
}
