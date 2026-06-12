package com.mcplugin.cable;

import org.bukkit.Material;
import org.bukkit.block.Block;

public class CableBlock {

    // =========================
    // 💥 ALL CABLE TYPES
    // =========================
    public static boolean isCable(Block block) {
        if (block == null) return false;

        Material type = block.getType();
        return type == Material.WAXED_LIGHTNING_ROD
                || type == Material.WAXED_CHISELED_COPPER
                || type == Material.WAXED_COPPER_GRATE;
    }

    // =========================
    // ➖ STRAIGHT CABLE
    // =========================
    public static boolean isStraightCable(Block block) {

        if (block == null) {
            return false;
        }

        return block.getType() == Material.WAXED_LIGHTNING_ROD;
    }

    // =========================
    // 🔄 CORNER CABLE
    // =========================
    public static boolean isCorner(Block block) {
        if (block == null) return false;
        return block.getType() == Material.WAXED_CHISELED_COPPER;
    }

    // =========================
    // 🔋 BATTERY
    // =========================
    public static boolean isBattery(Block block) {
        if (block == null) return false;
        return block.getType() == Material.WAXED_COPPER_GRATE;
    }
}