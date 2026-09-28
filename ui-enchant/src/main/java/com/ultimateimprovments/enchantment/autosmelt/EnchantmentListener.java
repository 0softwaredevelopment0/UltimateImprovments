package com.ultimateimprovments.enchantment.autosmelt;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener: AutoSmelt block breaking.
 * <p>
 * When a player breaks a block with an AutoSmelt tool, every smeltable drop rolls a
 * {@code level × 10%} chance to be smelted: raw iron → iron ingot, sand → glass,
 * cobblestone → stone, etc. The roll is PER DROP, so a single block (or a whole
 * AoE / VeinMiner / TreeCapitator area) comes out partially smelted — e.g. level 5
 * turns roughly half of the blocks into ingots and leaves the other half as raw
 * drops. If the block drops nothing, nothing is smelted.
 * <p>
 * Silk Touch is respected: with a Silk Touch tool the block itself drops,
 * so the drops are NEVER smelted (the ore block would be destroyed).
 */
public class EnchantmentListener implements Listener {

    /**
     * Cached furnace-smelt results per input material (built lazily, one recipe
     * iteration per material — avoids scanning the whole recipe list per break).
     */
    private static final Map<Material, ItemStack> SMELT_CACHE = new HashMap<>();

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;

        // AutoSmelt level (real enchantment, with PDC failsafe fallback)
        int level = com.ultimateimprovments.enchantment.autosmelt.Enchantment.getLevel(tool);
        if (level <= 0) return;

        // Silk Touch → the block drops as-is; smelting would destroy the ore block.
        if (tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) return;

        Block block = event.getBlock();
        Material blockType = block.getType();
        if (blockType == Material.AIR || !blockType.isBlock()) return;

        // Drops as they would normally fall (respects Fortune, loot tables, etc.)
        Collection<ItemStack> drops = block.getDrops(tool);
        if (drops.isEmpty()) return; // nothing dropped → nothing to smelt

        // Roll the smelt chance per drop; null = nothing smelted.
        List<ItemStack> result = processDrops(drops, level);

        // Always take over the vanilla drops (even when nothing smelted): this
        // suppresses the BlockDropItemEvent for THIS block, so the area handler
        // below — which exists for breakNaturally()/AoE drops — does not roll the
        // very same block a second time (that would inflate the effective chance).
        event.setDropItems(false);
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack item : result != null ? result : drops) {
            world.dropItemNaturally(loc, item);
        }
    }

    /**
     * Handles the drops of blocks broken by the AREA enchants (AoE / VeinMiner /
     * TreeCapitator). {@code breakNaturally()} does NOT fire BlockBreakEvent, so the
     * origin-only {@link #onBlockBreak(BlockBreakEvent)} never sees those blocks.
     * <p>
     * Runs at MONITOR after the area enchants (they break at LOW): every drop rolls
     * its own {@code level × 10%} smelt chance and the freshly spawned item entities
     * are replaced with the mixed (smelted + raw) result — so a big cube comes out
     * partially smelted. The player's OWN broken block is handled by
     * {@link #onBlockBreak(BlockBreakEvent)} (which suppresses this event), so no
     * block is ever rolled twice.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAreaDrops(BlockDropItemEvent event) {
        if (event.getPlayer() == null) return;
        if (!(event.getPlayer().getInventory().getItemInMainHand() instanceof ItemStack tool) || tool.getType() == Material.AIR)
            return;
        int level = com.ultimateimprovments.enchantment.autosmelt.Enchantment.getLevel(tool);
        if (level <= 0) return;
        if (tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) return;
        if (event.getItems().isEmpty()) return;

        List<ItemStack> processed = processDrops(event.getItems().stream()
                .map(org.bukkit.entity.Item::getItemStack)
                .toList(), level);
        if (processed == null) return; // nothing smelted → leave vanilla drops

        World world = event.getBlock().getWorld();
        Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (org.bukkit.entity.Item entity : event.getItems()) {
            entity.remove();
        }
        for (ItemStack item : processed) {
            world.dropItemNaturally(loc, item);
        }
    }

    /**
     * Rolls the smelt chance for every drop STACK: each smeltable stack is smelted
     * with probability {@code level × 10%}, the rest are kept as raw drops. Returns
     * the mixed drop list, or {@code null} when NOTHING was smelted — in that case
     * the caller leaves the vanilla drops untouched.
     */
    private static @Nullable List<ItemStack> processDrops(Collection<ItemStack> drops, int level) {
        double chance = Math.min(1.0, level * 0.10);
        List<ItemStack> out = new ArrayList<>();
        boolean anySmelted = false;

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) continue;

            ItemStack smelted = smeltResult(drop.getType());
            if (smelted == null
                    || java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) {
                // No recipe, or the roll failed → keep the raw drop.
                out.add(drop);
                continue;
            }

            anySmelted = true;
            out.addAll(splitStack(smelted, drop.getAmount() * smelted.getAmount()));
        }

        return anySmelted ? out : null;
    }

    /**
     * Looks up the furnace-smelted result for the given material (cached).
     * Checks both {@code FurnaceRecipe} and {@code BlastingRecipe}.
     *
     * @return the smelted item (cloned), or {@code null} if there is no recipe
     */
    private static @Nullable ItemStack smeltResult(Material type) {
        synchronized (SMELT_CACHE) {
            if (SMELT_CACHE.containsKey(type)) {
                ItemStack cached = SMELT_CACHE.get(type);
                return cached == null ? null : cached.clone();
            }

            ItemStack result = null;
            try {
                java.util.Iterator<Recipe> it = org.bukkit.Bukkit.getServer().recipeIterator();
                while (it.hasNext()) {
                    Recipe recipe = it.next();
                    if (recipe instanceof org.bukkit.inventory.FurnaceRecipe fr) {
                        if (matchesInput(fr.getInputChoice(), fr.getInput(), type)) {
                            result = fr.getResult();
                            break;
                        }
                    } else if (recipe instanceof org.bukkit.inventory.BlastingRecipe br) {
                        if (matchesInput(br.getInputChoice(), br.getInput(), type)) {
                            result = br.getResult();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Recipe iteration must never break block mining.
            }

            SMELT_CACHE.put(type, result);
            return result == null ? null : result.clone();
        }
    }

    /**
     * Checks whether the given material is accepted by a recipe input.
     * Handles all RecipeChoice kinds used by vanilla furnace/blast recipes:
     * <ul>
     *   <li>ExactChoice — item-type membership;</li>
     *   <li>MaterialChoice (incl. tag-based inputs like {@code minecraft:logs},
     *       expanded by Bukkit into a material list) — membership of the material;</li>
     *   <li>plain {@code ItemStack} input (no choice) — type equality;</li>
     *   <li>unknown choice kinds — conservative {@code false} (never smelt wrongly).</li>
     * </ul>
     */
    private static boolean matchesInput(RecipeChoice choice, ItemStack plainInput, Material type) {
        if (choice instanceof RecipeChoice.ExactChoice exact) {
            for (ItemStack item : exact.getChoices()) {
                if (item != null && item.getType() == type) return true;
            }
            return false;
        }
        if (choice instanceof RecipeChoice.MaterialChoice material) {
            for (Material m : material.getChoices()) {
                if (m == type) return true;
            }
            return false;
        }
        // No choice available (plain item input, or an unknown choice kind)
        return plainInput != null && plainInput.getType() == type;
    }

    /**
     * Splits a total amount into one or more stacks of the item's max stack size.
     */
    private static @NotNull List<ItemStack> splitStack(ItemStack base, int total) {
        List<ItemStack> stacks = new ArrayList<>();
        int max = Math.max(1, base.getMaxStackSize());
        int remaining = Math.max(0, total);
        while (remaining > 0) {
            ItemStack part = base.clone();
            int amount = Math.min(max, remaining);
            part.setAmount(amount);
            stacks.add(part);
            remaining -= amount;
        }
        return stacks;
    }
}
