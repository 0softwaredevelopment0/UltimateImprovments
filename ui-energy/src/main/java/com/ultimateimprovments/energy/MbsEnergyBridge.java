package com.ultimateimprovments.energy;

import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.mbs.api.MbsEnergy;
import com.ultimateimprovments.util.LocationUtil;
import org.bukkit.Location;

/**
 * UI-Energy's implementation of the {@link MbsEnergy.Consumer} bridge.
 * <p>
 * Registered by {@link UIEnergy} at startup so UI-MBS structure mechanics
 * (lightning cooking) can consume energy from the cable/battery network
 * without UI-MBS depending on UI-Energy.
 */
public class MbsEnergyBridge implements MbsEnergy.Consumer {

    @Override
    public boolean tryConsume(Location energyInputLoc, int amount) {
        if (energyInputLoc == null) return false;

        for (Location near : LocationUtil.getNeighbors(energyInputLoc)) {
            Location norm = LocationUtil.normalize(near);
            if (norm == null) continue;
            CableNode node = CableNetwork.getNode(norm);
            if (node != null && node.getEnergy() >= amount
                    && LocationUtil.isFullyConnected(energyInputLoc, norm)) {
                // Respect the battery mode: take only from DISCHARGE/CHARGE_DISCHARGE
                BatteryManager.BatteryCluster bc = BatteryManager.getCluster(node.getLocation());
                if (bc != null && !bc.canDischarge()) continue;
                node.removeEnergy(amount);
                return true;
            }
        }
        return false;
    }
}
