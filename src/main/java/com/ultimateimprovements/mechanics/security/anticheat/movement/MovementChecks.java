package com.ultimateimprovements.mechanics.security.anticheat.movement;

import com.ultimateimprovements.mechanics.security.anticheat.core.AbstractCheck;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory — создаёт все movement-проверки.
 */
public class MovementChecks {

    public static List<AbstractCheck> createAll() {
        List<AbstractCheck> list = new ArrayList<>();
        list.add(new FlightCheck());
        list.add(new GlideCheck());
        list.add(new FastFallCheck());
        list.add(new SpeedCheck());
        list.add(new AllJumpsCheck());
        list.add(new ClipCheck());
        list.add(new TeleportCheck());
        list.add(new NoFallCheck());
        list.add(new SpiderCheck());
        list.add(new JesusCheck());
        list.add(new FastLadderCheck());
        list.add(new TimerCheck());
        list.add(new BlinkCheck());
        list.add(new StepCheck());
        list.add(new DerpCheck());
        list.add(new FastEatCheck());
        list.add(new FoodSprintCheck());
        list.add(new GroundSpoofCheck());
        list.add(new MovementSpoofCheck());
        list.add(new ElytraCheck());
        list.add(new VerticalMotionCheck());
        list.add(new EntityMovementCheck());
        return list;
    }
}
