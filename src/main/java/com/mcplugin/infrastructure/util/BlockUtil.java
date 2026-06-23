package com.mcplugin.infrastructure.util;

import com.mcplugin.energy.transfer.cable.CableBlock;
import org.bukkit.block.Block;

public class BlockUtil {

    // =========================
    // ⚡ IS ENERGY CABLE
    // Делегирует проверку в CableBlock
    // =========================
    public static boolean isCable(Block block) {
        return CableBlock.isCable(block);
    }

    // =========================
    // 🔌 CAN CONNECT IN NETWORK
    // =========================
    public static boolean isConnectable(Block block) {
        return CableBlock.isCable(block);
    }

    // =========================
    // ⚡ IS STRAIGHT CABLE (ROD ONLY)
    // =========================
    public static boolean isStraightCable(Block block) {
        return CableBlock.isStraightCable(block);
    }

    // =========================
    // 🔁 IS JUNCTION / TURN
    // =========================
    public static boolean isJunction(Block block) {
        return CableBlock.isCorner(block);
    }
}