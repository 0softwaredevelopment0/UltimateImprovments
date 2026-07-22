package com.ultimateimprovements.energy.transfer.cable;

import com.ultimateimprovements.util.Materials;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class CableBlock {

    // =========================
    // 💥 ALL CABLE TYPES
    // =========================
    public static boolean isCable(Block block) {
        if (block == null) return false;

        Material type = block.getType();
        return type == Materials.WAXED_LIGHTNING_ROD
                || type == Materials.WAXED_CHISELED_COPPER
                || type == Materials.WAXED_COPPER_GRATE;
    }

    // =========================
    // ➖ STRAIGHT CABLE
    // =========================
    public static boolean isStraightCable(Block block) {

        if (block == null) {
            return false;
        }

        return block.getType() == Materials.WAXED_LIGHTNING_ROD;
    }

    // =========================
    // 🔄 CORNER CABLE
    // =========================
    public static boolean isCorner(Block block) {
        if (block == null) return false;
        return block.getType() == Materials.WAXED_CHISELED_COPPER;
    }

    // =========================
    // 🔋 BATTERY
    // =========================
    public static boolean isBattery(Block block) {
        if (block == null) return false;
        return block.getType() == Materials.WAXED_COPPER_GRATE;
    }
}