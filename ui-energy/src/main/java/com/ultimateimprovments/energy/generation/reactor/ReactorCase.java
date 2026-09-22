package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;

/**
 * DFC case system — the outer glass protection of the reactor.
 * <p>
 * The case has three parameters:
 * <ul>
 *   <li><b>Temperature (T)</b>, C*: −273..10 000. Rises only while fusion is
 *       running (+15 C* per sec at 150% speed, scaled by the speed), no
 *       passive cooling below the fusion minimum temperature.</li>
 *   <li><b>Pressure (P)</b>, MPa: 0..15, follows the case temperature the same
 *       way the shield pressure follows the core temperature. At 15 MPa the
 *       glass shatters and the pressure vents to ~0 (jitters around zero).</li>
 *   <li><b>Integrity (I)</b>, %: degrades while over-temperature/over-pressure
 *       (like the shield); at 0% the glass shatters. Does NOT recover
 *       passively — repairing the glass restores the integrity.</li>
 * </ul>
 * Broken glass is replaced with air; repairing (right-click a broken glass
 * position with glass in hand, or the repair command) restores the blocks and
 * resets the integrity to 100%. While broken, the temperature jitters around
 * ~10 000 and the pressure jitters around ~0.
 */
public class ReactorCase {

    public enum State { OK, BROKEN }

    /** Glass wall offsets relative to the anchor (parsed from darkfusionreactor.nbt). */
    public static final int[][] GLASS = {
            { -3, -9, -4 }, { -3, -9, 4 }, { -2, -9, -4 }, { -2, -9, 4 },
            { -1, -9, -4 }, { -1, -9, 4 }, { 0, -9, -4 }, { 0, -9, 4 },
            { 1, -9, -4 }, { 1, -9, 4 }, { 2, -9, -4 }, { 2, -9, 4 },
            { 3, -9, -4 }, { 3, -9, 4 },
            { -3, -8, -4 }, { -3, -8, 4 }, { -2, -8, -4 }, { -2, -8, 4 },
            { -1, -8, -4 }, { -1, -8, 4 }, { 0, -8, -4 }, { 0, -8, 4 },
            { 1, -8, -4 }, { 1, -8, 4 }, { 2, -8, -4 }, { 2, -8, 4 },
            { 3, -8, -4 }, { 3, -8, 4 },
            { -3, -7, -4 }, { -3, -7, 4 }, { -2, -7, -4 }, { -2, -7, 4 },
            { -1, -7, -4 }, { -1, -7, 4 }, { 0, -7, -4 }, { 0, -7, 4 },
            { 1, -7, -4 }, { 1, -7, 4 }, { 2, -7, -4 }, { 2, -7, 4 },
            { 3, -7, -4 }, { 3, -7, 4 },
            { -3, -6, -4 }, { -3, -6, 4 }, { -2, -6, -4 }, { -2, -6, 4 },
            { -1, -6, -4 }, { -1, -6, 4 }, { 0, -6, -4 }, { 0, -6, 4 },
            { 1, -6, -4 }, { 1, -6, 4 }, { 2, -6, -4 }, { 2, -6, 4 },
            { 3, -6, -4 }, { 3, -6, 4 },
            { -3, -5, -4 }, { -3, -5, 4 }, { -2, -5, -4 }, { -2, -5, 4 },
            { -1, -5, -4 }, { -1, -5, 4 }, { 0, -5, -4 }, { 0, -5, 4 },
            { 1, -5, -4 }, { 1, -5, 4 }, { 2, -5, -4 }, { 2, -5, 4 },
            { 3, -5, -4 }, { 3, -5, 4 },
            { -3, -4, -4 }, { -3, -4, 4 }, { -2, -4, -4 }, { -2, -4, 4 },
            { -1, -4, -4 }, { -1, -4, 4 }, { 0, -4, -4 }, { 0, -4, 4 },
            { 1, -4, -4 }, { 1, -4, 4 }, { 2, -4, -4 }, { 2, -4, 4 },
            { 3, -4, -4 }, { 3, -4, 4 },
            { -3, -3, -4 }, { -3, -3, 4 }, { -2, -3, -4 }, { -2, -3, 4 },
            { -1, -3, -4 }, { -1, -3, 4 }, { 0, -3, -4 }, { 0, -3, 4 },
            { 1, -3, -4 }, { 1, -3, 4 }, { 2, -3, -4 }, { 2, -3, 4 },
            { 3, -3, -4 }, { 3, -3, 4 }
    };

    private final ReactorManager reactor;
    private final Random random = new Random();

    private State state = State.OK;
    private int temp = -273;          // C*, -273..10000
    private double press;             // MPa, 0..15
    private int integrity = 100;      // 0..100 %
    private int brokenWarnTick;
    private double jitterPress;      // smoothed jitter value for the venting display

    public ReactorCase(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // CASE TICK (every server tick)
    // =========================
    public void tick(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();

        // =========================
        // TEMPERATURE — rises only from fusion (speed-scaled)
        // =========================
        if (state == State.OK) {
            double fusionSpeed = reactor.getFusion().getSpeedPct();
            if (fusionSpeed > 0) {
                // 150% speed → +15 C*/sec (case_heat_rate), scaled by speed
                double perTick = fusionSpeed / 150.0 * cfg.getCaseHeatRate() / 20.0;
                int whole = (int) perTick;
                caseTempRemainder += perTick - whole;
                if (caseTempRemainder >= 1) { whole += 1; caseTempRemainder -= 1; }
                temp = Math.min(cfg.getCaseTempMax(), temp + whole);
            }
        } else {
            // Broken: temperature jitters around ~10 000
            temp = cfg.getCaseTempMax() - 40 + random.nextInt(80);
        }

        // =========================
        // PRESSURE — follows the case temperature (like the shield follows T)
        // =========================
        double target = pressureTarget(temp, cfg);
        if (state == State.OK) {
            press = Math.max(0, press + (target - press) * cfg.getCasePressFollowRate());
        } else {
            // Broken: pressure vented — jitters around ~0
            jitterPress += (0 - jitterPress) * 0.3;
            press = Math.max(0, random.nextDouble() * 0.15 + jitterPress);
        }

        // =========================
        // INTEGRITY — decays above the safe thresholds (like the shield)
        // =========================
        if (state == State.OK) {
            boolean over = temp >= cfg.getCaseIntDecayTemp() || press >= cfg.getCaseIntDecayPress();
            if (over && integrity > 0) {
                double ratePerTick = cfg.getCaseIntDecayRate() / 20.0;
                caseIntRemainder += ratePerTick;
                int whole = (int) caseIntRemainder;
                if (whole > 0) {
                    caseIntRemainder -= whole;
                    integrity = Math.max(0, integrity - whole);
                    if (integrity <= 0) {
                        shatterGlass(base, cfg, "integrity");
                        return;
                    }
                }
            }
        }

        // =========================
        // BROKEN STATE — periodic reminder
        // =========================
        if (state == State.BROKEN) {
            brokenWarnTick++;
            if (brokenWarnTick >= 600) {
                brokenWarnTick = 0;
                ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(
                        "case_broken_reminder",
                        "<dark_red>⚠ <red>Стекло корпуса разбито! Почините его (ПКМ стеклом)."));
            }
        }
    }

    /** Case pressure target, MPa — scales with the case temperature. */
    static double pressureTarget(int temp, ReactorConfig cfg) {
        double maxTemp = Math.max(1, cfg.getCaseTempMax());
        double t = Math.max(0, temp) / maxTemp;
        return t * cfg.getCasePressMax();
    }

    // =========================
    // SHATTER / REPAIR
    // =========================
    /** Breaks all case glass (or a single pane for testing). Reason: temp/pressure/integrity. */
    private void shatterGlass(Location base, ReactorConfig cfg, String reason) {
        state = State.BROKEN;
        integrity = 0;
        press = 0;
        jitterPress = 0;

        int broken = 0;
        for (int[] off : GLASS) {
            Block block = base.clone().add(off[0], off[1], off[2]).getBlock();
            if (block.getType() == Material.GLASS) {
                block.setType(Material.AIR);
                broken++;
            }
        }

        Location core = base.clone().add(0.5, -5.5, 0.5);
        World world = base.getWorld();
        world.playSound(core, Sound.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 3.0f, 0.8f);
        world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1.5f, 1.4f);

        String msgKey = switch (reason) {
            case "temp" -> "case_broken_temp";
            case "pressure" -> "case_broken_pressure";
            default -> "case_broken_integrity";
        };
        String def = switch (reason) {
            case "temp" -> "<dark_red>💥 <red>Стекло корпуса расплавлено! (T = 10 000 C*)";
            case "pressure" -> "<dark_red>💥 <red>Стекло корпуса лопнуло от давления! (15 МПа)";
            default -> "<dark_red>💥 <red>Стекло корпуса разбито! Целостность 0%";
        };
        ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(msgKey, def));

        if (broken > 0) {
            ReactorManager.getInstance().saveToDb();
        }
    }

    /** Repairs the case: restores all glass and resets integrity. */
    public boolean repair(Location base) {
        ReactorConfig cfg = ReactorConfig.getInstance();
        if (state != State.BROKEN) return false;

        for (int[] off : GLASS) {
            Block block = base.clone().add(off[0], off[1], off[2]).getBlock();
            if (block.getType() == Material.AIR) {
                block.setType(Material.GLASS);
            }
        }

        state = State.OK;
        integrity = 100;
        temp = Math.min(temp, cfg.getCaseTempMax() - 1);
        brokenWarnTick = 0;

        Location core = base.clone().add(0.5, -5.5, 0.5);
        base.getWorld().playSound(core, Sound.BLOCK_GLASS_PLACE, SoundCategory.MASTER, 2.0f, 1.0f);
        ReactorManager.getInstance().broadcastRaw(StructuresMessages.get(
                "case_repaired", "<green>✔ <white>Стекло корпуса восстановлено! Целостность 100%"));

        ReactorManager.getInstance().saveToDb();
        return true;
    }

    /** Attempts to repair from a player right-click with glass in hand. True when consumed. */
    public boolean tryRepairByPlayer(Location base, org.bukkit.entity.Player player) {
        if (state != State.BROKEN) return false;
        if (base == null) return false;
        // The click must be inside the structure bounds (checked by the caller)
        return repair(base);
    }

    // =========================
    // RESET
    // =========================
    public void reset() {
        state = State.OK;
        temp = -273;
        press = 0;
        integrity = 100;
        brokenWarnTick = 0;
        jitterPress = 0;
        caseTempRemainder = 0;
        caseIntRemainder = 0;
    }

    // =========================
    // GETTERS / SETTERS (persistence)
    // =========================
    public State getState() { return state; }
    public void setState(State val) { state = val; }

    public int getTemp() { return temp; }
    public void setTemp(int val) {
        temp = Math.max(-273, Math.min(10000, val));
    }

    public double getPress() { return press; }
    public void setPress(double val) { press = Math.max(0, Math.min(15, val)); }

    public int getIntegrity() { return integrity; }
    public void setIntegrity(int val) {
        integrity = Math.max(0, Math.min(100, val));
        if (integrity <= 0) state = State.BROKEN;
    }

    public boolean isBroken() { return state == State.BROKEN; }

    private double caseTempRemainder;
    private double caseIntRemainder;
}
