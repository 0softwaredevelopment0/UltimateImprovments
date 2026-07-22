package com.ultimateimprovments.mechanics.security.anticheat.world;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XRay — статистическая детекция рентгена (игрок находит руду слишком часто).
 * Детекция: соотношение сломанных руд к сломанному камню аномально высокое.
 */
public class XRayCheck extends AbstractCheck {

    private double minOreRatio;
    private int minTotalBlocks;

    private static final Set<Material> ORES = EnumSet.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE
    );

    private final ConcurrentHashMap<UUID, OreStats> stats = new ConcurrentHashMap<>();

    public XRayCheck() {
        super("XRay", CheckCategory.WORLD);
    }

    @Override
    public void onInit() {
        loadConfig();
        minOreRatio = getConfigDouble("min_ore_ratio", 0.3);
        minTotalBlocks = getConfigInt("min_total_blocks", 50);
    }

    @Override
    public void onReload() {
        loadConfig();
        minOreRatio = getConfigDouble("min_ore_ratio", 0.3);
        minTotalBlocks = getConfigInt("min_total_blocks", 50);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        Material mat = e.getBlock().getType();
        OreStats s = stats.computeIfAbsent(player.getUniqueId(), k -> new OreStats());

        if (ORES.contains(mat)) {
            s.oreCount++;
        } else if (mat == Material.STONE || mat == Material.DEEPSLATE
                || mat == Material.NETHERRACK || mat == Material.TUFF
                || mat == Material.GRANITE || mat == Material.DIORITE || mat == Material.ANDESITE) {
            s.stoneCount++;
        }

        s.totalCount = s.oreCount + s.stoneCount;
        if (s.totalCount >= minTotalBlocks) {
            double ratio = (double) s.oreCount / s.totalCount;
            if (ratio > minOreRatio) {
                CheckResult result = flag(player, 3.0,
                        "XRay: ore ratio " + String.format("%.2f", ratio)
                                + " (" + s.oreCount + "/" + s.totalCount + " blocks)");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }
    }

    private static class OreStats {
        int oreCount = 0;
        int stoneCount = 0;
        int totalCount = 0;
    }
}
