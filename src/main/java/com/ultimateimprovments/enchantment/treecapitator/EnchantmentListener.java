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

import java.util.HashSet;
import java.util.Set;

/**
 * Listener: TreeCapitator block breaking.
 * <p>
 * When a player breaks a log with a TreeCapitator axe, the TRUNK COLUMN is felled:
 * logs directly above and below the broken block (same X/Z column), up to the
 * first non-log block in each direction. Branches and horizontal connections are
 * NOT broken — only the column. Stripped logs are NOT part of a living tree and
 * are left alone (prevents accidentally felling player-built log structures).
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

        // Collect the trunk column (logs straight up/down from the broken block).
        Set<Block> tree = collectTree(world, origin);
        if (tree.isEmpty()) return;

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
            Material brokenType = block.getType();
            block.breakNaturally(tool, true);

            // Count toward the Woodcutter timed challenge (no BlockBreakEvent fires here)
            com.ultimateimprovments.mechanics.features.world.WoodcutterChallenge.countBroken(player, brokenType);

            // Consume integrity as from breaking 1 block (mirrors PlayerItemDamageEvent
            // which IntegrityListener redirects to decreaseItemIntegrity(item, 1, player))
            ItemIntegrityAPI.decreaseItemIntegrity(tool, 1, player);

            // Tool broke from integrity loss — stop, as vanilla would
            if (tool.getAmount() <= 0) break;
        }
    }

    /**
     * Collects the trunk column of the tree: logs straight up and down from
     * {@code origin} in the same X/Z column, up to the first non-log block in
     * each direction. 🛡 Keeps the 16×16×16 cube failsafe around the origin:
     * the scan only follows the column (X/Z = origin), Y limited to origin−7..origin+8.
     */
    private @NotNull Set<Block> collectTree(World world, Block origin) {
        Set<Block> tree = new HashSet<>();

        // 🛡 16×16×16 failsafe: the column is limited in Y to origin−7..origin+8
        // (in X/Z the scan stays in the origin column, so the cube is not violated).
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        int minY = Math.max(world.getMinHeight(), y - 7);
        int maxY = Math.min(world.getMaxHeight() - 1, y + 8);

        tree.add(origin);

        // Up the column — until the first non-log block (canopy/branches untouched)
        for (int ny = y + 1; ny <= maxY; ny++) {
            Block block = world.getBlockAt(x, ny, z);
            if (!isLog(block.getType())) break;
            tree.add(block);
        }

        // Down the column — until the first non-log block
        for (int ny = y - 1; ny >= minY; ny--) {
            Block block = world.getBlockAt(x, ny, z);
            if (!isLog(block.getType())) break;
            tree.add(block);
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
