package com.mcplugin.core1;

import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.cable.NodeType;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Управляет генерацией и распределением энергии реактора по кабельной сети.
 */
public class ReactorEnergyManager {

    private static final double ENERGY_PER_TEMP_MULTIPLIER = 0.9;

    /**
     * Выполняет тик генерации энергии.
     * @return количество сгенерированной энергии за этот тик
     */
    public static int tickEnergy(Location baseLocation, int coreTemp, double energyRemainder) {
        if (coreTemp <= 1000 || baseLocation == null) return 0;

        double energyPerTick = (double) coreTemp * ENERGY_PER_TEMP_MULTIPLIER;
        double remainder = energyRemainder + energyPerTick;
        int toGenerate = (int) remainder;
        if (toGenerate <= 0) return 0;

        // Распределение энергии по кабелям рядом с реактором
        List<CableNode> nearbyCables = new ArrayList<>();
        for (CableNode node : CableNetwork.getAllNodes()) {
            Location nLoc = node.getLocation();
            if (!nLoc.getWorld().equals(baseLocation.getWorld())) continue;
            int dx = Math.abs(nLoc.getBlockX() - baseLocation.getBlockX());
            int dy = Math.abs(nLoc.getBlockY() - baseLocation.getBlockY());
            int dz = Math.abs(nLoc.getBlockZ() - baseLocation.getBlockZ());
            if (dx <= 3 && dy <= 5 && dz <= 3) {
                nearbyCables.add(node);
            }
        }

        int remaining = toGenerate;
        if (!nearbyCables.isEmpty()) {
            int perNode = toGenerate / nearbyCables.size();
            for (CableNode node : nearbyCables) {
                int space = node.getMaxEnergy() - node.getEnergy();
                int give = Math.min(perNode, space);
                if (give <= 0) continue;
                node.addEnergy(give);
                remaining -= give;
                CableNetwork.saveNode(node);
            }
        }

        // Остаток в GENERATOR-буфер
        if (remaining > 0) {
            Location coreLoc = baseLocation.clone().add(0, -1, 0);
            CableNode genNode = CableNetwork.getNode(coreLoc);
            if (genNode == null) {
                CableNetwork.addNode(coreLoc);
                genNode = CableNetwork.getNode(coreLoc);
            }
            if (genNode != null) {
                genNode.setType(NodeType.GENERATOR);
                genNode.setMaxEnergy(ReactorConfig.getInstance().getCoreTempMax() * 10);
                genNode.addEnergy(remaining);
                CableNetwork.saveNode(genNode);
            }
        }

        return toGenerate;
    }

    /**
     * Вычисляет "сырую" скорость генерации энергии в E/сек.
     */
    public static double calculateEnergyRate(int coreTemp) {
        return coreTemp > 1000 ? (double) coreTemp * ENERGY_PER_TEMP_MULTIPLIER * 20.0 : 0.0;
    }
}
