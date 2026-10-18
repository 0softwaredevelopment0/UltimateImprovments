package com.ultimateimprovments.energy;

import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.mbs.api.MbsEnergy;
import org.bukkit.Location;

/**
 * UI-Energy's implementation of the {@link MbsEnergy.Consumer} bridge.
 * <p>
 * Registered by {@link UIEnergy} at startup so UI-MBS structure mechanics
 * (lightning cooking) can consume energy from the cable/battery network
 * without UI-MBS depending on UI-Energy.
 * <p>
 * Goes through {@link EnergyFlow}: the network behind the machine block is
 * traversed (before the rework only a directly adjacent battery qualified,
 * a plain cable next to the machine meant "no energy"), battery modes are
 * respected, and the check-then-take pattern guarantees a low network never
 * loses partial energy on a failed consume.
 */
public class MbsEnergyBridge implements MbsEnergy.Consumer {

    @Override
    public boolean tryConsume(Location energyInputLoc, int amount) {
        if (energyInputLoc == null || amount <= 0) return false;
        if (!EnergyFlow.hasEnergy(energyInputLoc, amount)) return false;
        return EnergyFlow.pull(energyInputLoc, amount) >= amount;
    }
}
