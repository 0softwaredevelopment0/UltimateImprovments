package com.ultimateimprovments.mechanics.security.anticheat.misc;

import com.ultimateimprovments.mechanics.security.anticheat.core.AbstractCheck;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory — creates all miscellaneous checks.
 */
public class MiscChecks {

    public static List<AbstractCheck> createAll() {
        List<AbstractCheck> list = new ArrayList<>();
        list.add(new RegenCheck());
        list.add(new NoSwingCheck());
        list.add(new AutoRespawnCheck());
        list.add(new InventoryMoveCheck());
        list.add(new AutoLootCheck());
        list.add(new PortalInventoryCheck());
        list.add(new ExtraInventoryCheck());
        list.add(new AntiHungerCheck());
        return list;
    }
}
