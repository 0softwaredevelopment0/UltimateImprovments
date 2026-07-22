package com.ultimateimprovements.mechanics.security.anticheat.combat;

import com.ultimateimprovements.mechanics.security.anticheat.core.AbstractCheck;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory — создаёт все combat-проверки.
 */
public class CombatChecks {

    public static List<AbstractCheck> createAll() {
        List<AbstractCheck> list = new ArrayList<>();
        list.add(new KillAuraCheck());
        list.add(new AimbotCheck());
        list.add(new FightBotCheck());
        list.add(new ForceFieldCheck());
        list.add(new AimAssistCheck());
        list.add(new FastBowCheck());
        list.add(new VelocityCheck());
        list.add(new ReachCheck());
        list.add(new CriticalsCheck());
        list.add(new AutoClickerCheck());
        list.add(new BackTrackCheck());
        list.add(new ArrowDMGCheck());
        return list;
    }
}
