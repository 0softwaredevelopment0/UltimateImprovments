package com.ultimateimprovments.mechanics.security.anticheat.world;

import com.ultimateimprovments.mechanics.security.anticheat.core.AbstractCheck;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory — создаёт все world-проверки.
 */
public class WorldChecks {

    public static List<AbstractCheck> createAll() {
        List<AbstractCheck> list = new ArrayList<>();
        list.add(new FastPlaceCheck());
        list.add(new BlockReachCheck());
        list.add(new LiquidsCheck());
        list.add(new ScaffoldCheck());
        list.add(new TowerCheck());
        list.add(new XRayCheck());
        list.add(new GhostHandCheck());
        list.add(new FastBreakCheck());
        list.add(new BedBreakerCheck());
        list.add(new AirPlaceCheck());
        return list;
    }
}
