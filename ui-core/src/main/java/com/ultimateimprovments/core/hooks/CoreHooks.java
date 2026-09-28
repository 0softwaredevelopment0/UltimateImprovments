package com.ultimateimprovments.core.hooks;

import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * CoreHooks — tiny indirection points shared by every addon, so addons never
 * depend on each other's classes. UI-Core owns the contract; the addon that
 * provides a behaviour installs its implementation at ITS own startup. An unset
 * hook is a safe no-op.
 * <p>
 * This is what lets e.g. the enchantment addon react to auth state, challenge
 * counters, or the ore→stone mechanic without importing the auth / world / core
 * feature code.
 */
public final class CoreHooks {

    /** Counter of player-broken blocks (used by timed challenges). */
    public interface BlockBreakCounter {
        void onBlockBroken(Player player, Material type);
    }

    private static volatile Predicate<UUID> pendingAuth = id -> false;
    private static volatile Predicate<UUID> vanished = id -> false;
    private static volatile BlockBreakCounter blockBreakCounter;

    /** Ore → replacement block left behind when an ore is mined. */
    private static final Map<Material, Material> ORE_TO_STONE = Map.ofEntries(
            // Stone ores -> STONE
            Map.entry(Material.COAL_ORE, Material.STONE),
            Map.entry(Material.IRON_ORE, Material.STONE),
            Map.entry(Material.COPPER_ORE, Material.STONE),
            Map.entry(Material.GOLD_ORE, Material.STONE),
            Map.entry(Material.REDSTONE_ORE, Material.STONE),
            Map.entry(Material.LAPIS_ORE, Material.STONE),
            Map.entry(Material.DIAMOND_ORE, Material.STONE),
            Map.entry(Material.EMERALD_ORE, Material.STONE),
            // Deepslate ores -> DEEPSLATE
            Map.entry(Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE),
            // Nether ores -> NETHERRACK
            Map.entry(Material.NETHER_QUARTZ_ORE, Material.NETHERRACK),
            Map.entry(Material.NETHER_GOLD_ORE, Material.NETHERRACK)
    );

    private CoreHooks() {}

    // ── Auth ─────────────────────────────────────────────────────────────

    /** Installed by the auth addon. Null resets to "nobody pending". */
    public static void setPendingAuth(Predicate<UUID> predicate) {
        pendingAuth = predicate != null ? predicate : id -> false;
    }

    /** @return true while the player is frozen awaiting authentication. */
    public static boolean isPendingAuth(UUID playerId) {
        return playerId != null && pendingAuth.test(playerId);
    }

    /** Installed by the vanish feature. Null resets to "nobody vanished". */
    public static void setVanishedPredicate(Predicate<UUID> predicate) {
        vanished = predicate != null ? predicate : id -> false;
    }

    /** @return true while the player is hidden (vanish). */
    public static boolean isVanished(UUID playerId) {
        return playerId != null && vanished.test(playerId);
    }

    // ── Block-break counters (timed challenges) ──────────────────────────

    /** Installed by the world/challenge addon. */
    public static void setBlockBreakCounter(BlockBreakCounter counter) {
        blockBreakCounter = counter;
    }

    /** Reports one broken block to whichever challenge is listening. */
    public static void onBlockBroken(Player player, Material type) {
        BlockBreakCounter c = blockBreakCounter;
        if (c != null && player != null && type != null) {
            c.onBlockBroken(player, type);
        }
    }

    // ── Ore → stone ──────────────────────────────────────────────────────

    /**
     * Leaves the ore's replacement block (stone/deepslate/netherrack) behind
     * after mining, deferred one tick and only when the block is still AIR.
     *
     * @param block   the broken block (may already be AIR at call time)
     * @param oreType the ore type the block was before breaking
     */
    public static void scheduleOreStone(Block block, Material oreType) {
        if (block == null || oreType == null) return;
        Material replacement = ORE_TO_STONE.get(oreType);
        if (replacement == null) return;
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (block.getType() == Material.AIR) {
                block.setType(replacement, false);
            }
        });
    }
}
