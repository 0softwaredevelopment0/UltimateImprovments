package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.StructuresMessages;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.CopperBulb;

/**
 * Manages the reactor's visual effects, sounds and sign updates for the
 * Dark Fusion Reactor (DFC, 10×11×9).
 * <p>
 * The stats wall (front glass, x=−5 relative to the anchor) holds 7 signs:
 * Power Stats, Shield Stats, Fuel Stats, Fusion Stats (row y=−8),
 * Shield Stress, Core Stats, Case Stats (row y=−7).
 * The plugin rewrites their value lines every second; titles come from the
 * config ({@code structures.signs.*}, RU/EN tabs).
 */
public class ReactorDisplay {

    private final ReactorManager reactor;

    // =========================
    // SIGN OFFSETS (relative to the anchor — frame cell above the central bulb)
    // =========================
    private static final int[] SIGN_POWER  = { -5, -8, -2 };
    private static final int[] SIGN_SHIELD = { -5, -8, -1 };
    private static final int[] SIGN_FUEL   = { -5, -8,  0 };
    private static final int[] SIGN_FUSION = { -5, -8,  1 };
    private static final int[] SIGN_STRESS = { -5, -7, -1 };
    private static final int[] SIGN_CORE   = { -5, -7,  0 };
    private static final int[] SIGN_CASE   = { -5, -7,  1 };

    // =========================
    // SMOOTHED DISPLAY VALUES (interpolated toward actual values)
    // =========================
    private double displayCoreTemp;
    private double displayShieldPress;
    private double displaySpin;
    private double displayCoreShInt = 100;
    private double displayCoreCaseTemp;
    private double displayCoreCasePress;
    private double displayCoreCaseInt = 100;
    private double displayRecipeTime;
    private double displayReactorWear;
    private double displayEnergyRate;

    private static final double SMOOTHING_FACTOR = 0.35;

    // =========================
    // TICK COUNTERS
    // =========================
    private int displayTick;
    private boolean prevHeating;
    private boolean prevCooling;
    private int integrityWarnTick;
    private int soundTick;

    // Cached sign text — signs are only rewritten when the content changes
    private final String[][] signCache = new String[16][4];

    public ReactorDisplay(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // SMOOTH DISPLAY TICK (every tick)
    // =========================
    public void tickSmoothDisplay() {
        displayCoreTemp += (reactor.getCoreTemp() - displayCoreTemp) * SMOOTHING_FACTOR;
        displayShieldPress += (reactor.getShieldPress() - displayShieldPress) * SMOOTHING_FACTOR;
        displaySpin += (reactor.getCoreSpin() - displaySpin) * SMOOTHING_FACTOR;
        displayCoreShInt += (reactor.getCoreShInt() - displayCoreShInt) * SMOOTHING_FACTOR;
        displayCoreCaseTemp += (reactor.getCoreCaseTemp() - displayCoreCaseTemp) * SMOOTHING_FACTOR;
        displayCoreCasePress += (reactor.getCoreCasePress() - displayCoreCasePress) * SMOOTHING_FACTOR;
        displayCoreCaseInt += (reactor.getCoreCaseInt() - displayCoreCaseInt) * SMOOTHING_FACTOR;
        displayRecipeTime += (reactor.getRecipeTime() - displayRecipeTime) * SMOOTHING_FACTOR;
        displayReactorWork();
    }

    private void displayReactorWork() {
        displayReactorWear += (reactor.getReactorWear() - displayReactorWear) * SMOOTHING_FACTOR;

        double workMult = reactor.getCoreWorkTemp() > 0
                ? (double) reactor.getCoreTemp() / reactor.getCoreWorkTemp() : 0.0;
        double rawEnergyRate = workMult > 0.0001
                ? workMult * reactor.getEnergyRate() * 20.0 : 0.0;
        displayEnergyRate += (rawEnergyRate - displayEnergyRate) * SMOOTHING_FACTOR;
    }

    // =========================
    // SOUND TICK (every 10 ticks)
    // =========================
    public void tickSound() {
        Location base = reactor.getReactorLocation();
        if (base == null) return;

        if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) {
            base.getWorld().playSound(
                    base, Sound.BLOCK_NOTE_BLOCK_PLING,
                    SoundCategory.MASTER, 1.0f, 1.5f
            );
        }
    }

    // =========================
    // VISUAL TICK (every tick — particles)
    // Core chamber center: (0.5, −5.5, 0.5) — middle of the twin towers.
    // =========================
    public void tickVisual() {
        Location base = reactor.getReactorLocation();
        if (base == null) return;

        Location coreCenter = base.clone().add(0.5, -5.5, 0.5);

        double workMult = reactor.getCoreWorkTemp() > 0
                ? (double) reactor.getCoreTemp() / reactor.getCoreWorkTemp() : 0.0;
        boolean meltdown = reactor.isMeltdownCountdown();
        int meltdownTimer = reactor.getMeltdownTimer();

        // =========================
        // CORE TEMPERATURE PARTICLES (color by the ten-million multiplier)
        // =========================
        Particle.DustOptions color;

        if (workMult <= 0.0001) {
            color = new Particle.DustOptions(Color.fromRGB(128, 128, 128), 1.25f);
        } else if (workMult <= 0.15) {
            color = new Particle.DustOptions(Color.fromRGB(128, 0, 0), 1.25f);
        } else if (workMult <= 0.3) {
            color = new Particle.DustOptions(Color.RED, 1.25f);
        } else if (workMult <= 0.6) {
            color = new Particle.DustOptions(Color.ORANGE, 1.25f);
        } else if (workMult <= 1.0) {
            color = new Particle.DustOptions(Color.YELLOW, 1.25f);
        } else {
            color = new Particle.DustOptions(Color.WHITE, 1.25f);
        }

        base.getWorld().spawnParticle(
                Particle.DUST, coreCenter, 16, 0, 0, 0, 0, color
        );

        // =========================
        // HIGH TEMP EFFECTS (near the working point and above)
        // =========================
        if (workMult > 0.0001 && workMult <= 0.5) {
            base.getWorld().spawnParticle(
                    Particle.END_ROD,
                    coreCenter.clone().add(0, 0, 1),
                    1, 0, 0, -1.5, 0.1
            );
            base.getWorld().spawnParticle(
                    Particle.LAVA,
                    coreCenter.clone().add(0, 0, -0.4),
                    1, 0, 0, 0, 0
            );
            base.getWorld().spawnParticle(
                    Particle.COPPER_FIRE_FLAME,
                    coreCenter.clone().add(0, 0, 2.4),
                    1, 0, 0, 0, 0.01f
            );
        }

        // =========================
        // BEACON HUM SOUND AT HIGH TEMP
        // =========================
        if (workMult > 0.0001 && !meltdown) {
            base.getWorld().playSound(
                    coreCenter,
                    Sound.BLOCK_BEACON_POWER_SELECT,
                    SoundCategory.MASTER, 0.5f, 1
            );
        }

        // =========================
        // MELTDOWN COUNTDOWN EFFECTS
        // =========================
        if (meltdown) {
            float progress = 1.0f - (meltdownTimer / 200.0f);
            if (progress < 0) progress = 0;
            if (progress > 1) progress = 1;

            int smokeCount = 8 + (int) (progress * 56);
            int fireCount = 2 + (int) (progress * 14);

            Location smokePos = coreCenter.clone().add(0, 2.5, 0);
            base.getWorld().spawnParticle(
                    Particle.CAMPFIRE_SIGNAL_SMOKE,
                    smokePos, smokeCount, 0.5, 0.5, 0.5, 0.15
            );

            base.getWorld().spawnParticle(
                    Particle.LAVA, coreCenter, fireCount, 0.3, 0.3, 0.3, 0
            );
            base.getWorld().spawnParticle(
                    Particle.FLAME, coreCenter, fireCount, 0.3, 0.3, 0.3, 0.05
            );

            float volume = 0.5f + progress * 4.0f;
            float pitch = 0.5f + progress * 0.8f;
            base.getWorld().playSound(
                    coreCenter,
                    Sound.BLOCK_BEACON_AMBIENT,
                    SoundCategory.MASTER, volume, pitch
            );

            int sparkCount = 2 + (int) (progress * 8);
            base.getWorld().spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    coreCenter, sparkCount, 1.0, 1.0, 1.0, 0
            );
        }
    }

    // =========================
    // UPDATE DISPLAYS (7 stats signs, every second)
    // =========================
    public void updateDisplays() {
        Location base = reactor.getReactorLocation();
        if (base == null) return;

        displayTick++;

        // Sign rewrite once per second (20 ticks) — smooth values keep ticking
        if (displayTick % 20 != 0) return;

        boolean selfDestruct = reactor.isSelfDestructActive() || reactor.isMeltdownCountdown();
        boolean meltdownCdown = reactor.isMeltdownCountdown();

        int tInt = (int) Math.round(displayCoreTemp);
        String press = String.format("%.3f", displayShieldPress);
        String spin = String.format("%.2f", displaySpin);
        int shIntInt = (int) Math.round(displayCoreShInt);
        int caseTempInt = (int) Math.round(displayCoreCaseTemp);
        String casePress = String.format("%.3f", displayCoreCasePress / 1000.0);
        int caseIntInt = (int) Math.round(displayCoreCaseInt);
        int recipeInt = (int) Math.round(displayRecipeTime);

        // Flash red-white when any integrity is below 100%
        boolean flashing = shIntInt < 100 || caseIntInt < 100;
        String color = (flashing && (displayTick % 10 < 5)) ? "<red>" : "<white>";

        if (selfDestruct) {
            String blank = " ";
            String noSignal = "<red>NO SIGNAL";
            String meltdownLine = meltdownCdown
                    ? "<red>DETONATION!"
                    : noSignal;

            int[][] all = { SIGN_POWER, SIGN_SHIELD, SIGN_FUEL, SIGN_FUSION,
                    SIGN_STRESS, SIGN_CORE, SIGN_CASE };
            for (int i = 0; i < all.length; i++) {
                setSignLine(base, all[i], 0, blank, i);
                setSignLine(base, all[i], 1, meltdownLine, i);
                setSignLine(base, all[i], 2, blank, i);
                setSignLine(base, all[i], 3, blank, i);
            }
            return;
        }

        // =========================
        // CORE STATS — T (C*), P (MPa), S (RPS)
        // =========================
        setSign(base, SIGN_CORE, 0, msg("signs.core_stats_title", "=| Core Stats |="), 3);
        setSign(base, SIGN_CORE, 1, color + msg("signs.core_stats_temp", "T: %temp% C*")
                .replace("%temp%", String.valueOf(tInt)), 3);
        setSign(base, SIGN_CORE, 2, color + msg("signs.core_stats_press", "P: %press% mPa")
                .replace("%press%", press), 3);
        setSign(base, SIGN_CORE, 3, color + msg("signs.core_stats_spin", "S: %spin% RPS")
                .replace("%spin%", spin), 3);

        // =========================
        // CASE STATS — case T, case P (MPa), case integrity
        // =========================
        setSign(base, SIGN_CASE, 0, msg("signs.case_stats_title", "=| Case Stats |="), 4);
        setSign(base, SIGN_CASE, 1, color + msg("signs.case_stats_temp", "T: %temp% C*")
                .replace("%temp%", String.valueOf(caseTempInt)), 4);
        setSign(base, SIGN_CASE, 2, color + msg("signs.case_stats_press", "P: %press% mPa")
                .replace("%press%", casePress), 4);
        setSign(base, SIGN_CASE, 3, color + msg("signs.case_stats_int", "I: %int%%")
                .replace("%int%", String.valueOf(caseIntInt)), 4);

        // =========================
        // SHIELD STATS — magnet status, shell integrity, shield status
        // =========================
        String shieldStatus = shIntInt >= 100
                ? msg("signs.status_stable", "Stable")
                : msg("signs.status_unstable", "Unstable");
        setSign(base, SIGN_SHIELD, 0, msg("signs.shield_stats_title", "=| Shield Stats |="), 1);
        setSign(base, SIGN_SHIELD, 1, color + msg("signs.shield_stats_magnet", "M: %status%")
                .replace("%status%", msg("signs.status_offline", "Offline")), 1);
        setSign(base, SIGN_SHIELD, 2, color + msg("signs.shield_stats_int", "I: %int%%")
                .replace("%int%", String.valueOf(shIntInt)), 1);
        setSign(base, SIGN_SHIELD, 3, color + msg("signs.shield_stats_status", "S: %status%")
                .replace("%status%", shieldStatus), 1);

        // =========================
        // POWER STATS — laser powers (live from ReactorLasers) + spin %
        // =========================
        String spinPct = String.valueOf(Math.min(100, (int) Math.round(displaySpin / 0.95 * 100)));
        int p1 = (int) Math.round(reactor.getLasers().getPower(ReactorLasers.LASER_P1));
        int p2 = (int) Math.round(reactor.getLasers().getPower(ReactorLasers.LASER_P2));
        int stab = (int) Math.round(reactor.getLasers().getPower(ReactorLasers.LASER_STAB));
        setSign(base, SIGN_POWER, 0, msg("signs.power_stats_title", "=| Power Stats |="), 0);
        setSign(base, SIGN_POWER, 1, color + msg("signs.power_stats_p1p2", "P1/P2: %p1%/%p2%%")
                .replace("%p1%", String.valueOf(p1)).replace("%p2%", String.valueOf(p2)), 0);
        setSign(base, SIGN_POWER, 2, color + msg("signs.power_stats_stab", "Stab: %stab%%")
                .replace("%stab%", String.valueOf(stab)), 0);
        setSign(base, SIGN_POWER, 3, color + msg("signs.power_stats_spin", "S: %spin%%")
                .replace("%spin%", spinPct), 0);

        // =========================
        // FUEL STATS — status + fill of the two side fuel barrels
        // =========================
        boolean fuel = reactor.hasBarrelFuelPublic();
        setSign(base, SIGN_FUEL, 0, msg("signs.fuel_stats_title", "=| Fuel Stats |="), 2);
        setSign(base, SIGN_FUEL, 1, color + msg("signs.fuel_stats_status", "S: %status%")
                .replace("%status%", fuel
                        ? msg("signs.status_fueled", "Fueled")
                        : msg("signs.status_empty", "Empty")), 2);
        setSign(base, SIGN_FUEL, 2, color + msg("signs.fuel_stats_f", "F: %f%%")
                .replace("%f%", fuel ? "100" : "0"), 2);
        setSign(base, SIGN_FUEL, 3, color + msg("signs.fuel_stats_m", "M: %m%%")
                .replace("%m%", "0"), 2);

        // =========================
        // FUSION STATS — recipe status + progress
        // =========================
        String fusionStatus;
        if (recipeInt <= 0) fusionStatus = msg("signs.status_idle", "Idle");
        else if (recipeInt < reactor.getRecipeTimeMax()) fusionStatus = msg("signs.status_running", "Running");
        else fusionStatus = msg("signs.status_done", "Done");
        setSign(base, SIGN_FUSION, 0, msg("signs.fusion_stats_title", "=| Fusion Stats |="), 5);
        setSign(base, SIGN_FUSION, 1, color + msg("signs.fusion_stats_status", "S: %status%")
                .replace("%status%", fusionStatus), 5);
        setSign(base, SIGN_FUSION, 2, color + msg("signs.fusion_stats_p", "P: %p%%")
                .replace("%p%", String.valueOf(recipeInt)), 5);
        setSign(base, SIGN_FUSION, 3, color + msg("signs.fusion_stats_f", "F: %f%%")
                .replace("%f%", fuel ? "100" : "0"), 5);

        // =========================
        // SHIELD STRESS — heat %, pressure %, spin %
        // =========================
        String heatPct = String.valueOf(Math.min(100,
                (int) Math.round(Math.max(0, displayCoreTemp) * 100.0 / Math.max(1, reactor.getCoreWorkTemp()))));
        String pressPct = String.valueOf(Math.min(100,
                (int) Math.round(displayShieldPress * 100.0 / 10.01)));
        setSign(base, SIGN_STRESS, 0, msg("signs.stress_title", "=| Shield Stress |="), 6);
        setSign(base, SIGN_STRESS, 1, color + msg("signs.stress_h", "H: %h%%")
                .replace("%h%", heatPct), 6);
        setSign(base, SIGN_STRESS, 2, color + msg("signs.stress_p", "P: %p%%")
                .replace("%p%", pressPct), 6);
        setSign(base, SIGN_STRESS, 3, color + msg("signs.stress_s", "S: %s%%")
                .replace("%s%", spinPct), 6);

        updateRoofLaserSigns(base, p1, p2, stab);
    }

    // =========================
    // ROOF LASER SIGNS (9 standing signs next to the roof lamps)
    // Sign line 1 keeps the template title ("Power Laser #1" etc.);
    // line 2 shows the live power. Absorber shows the valve opening.
    // =========================
    private void updateRoofLaserSigns(Location base, int p1, int p2, int stab) {
        var lasers = reactor.getLasers();

        // [row (−4 | −2), sign offset z] — matches LAMP_PLUS/LAMP_MINUS rows in ReactorLasers
        int[][] powerSigns = {
                { -4, -4 }, { -4, -2 }, { -4, 0 }, { -4, 2 },
                { -2, -4 }, { -2, -2 }, { -2, 0 }, { -2, 2 }
        };
        String[] powerText = { p1 + " %", p2 + " %", stab + " %",
                (int) Math.round(lasers.getPower(ReactorLasers.LASER_ABSORBER)) + " %" };

        // +5% row (x=−4) — Power #1, Power #2, Stab, Absorber
        for (int i = 0; i < 4; i++) {
            setRoofSignPower(base, powerSigns[i][0], powerSigns[i][1], powerText[i],
                    7 + i);
        }
        // −5% row (x=−2) — mirrors the same powers
        for (int i = 0; i < 4; i++) {
            setRoofSignPower(base, powerSigns[4 + i][0], powerSigns[4 + i][1], powerText[i],
                    11 + i);
        }

        // Startup sign (x=−4, z=4)
        String startupText = lasers.isStarted()
                ? msg("signs.status_running", "Running")
                : msg("signs.status_no_startup", "No startup");
        setRoofSignPower(base, -4, 4, startupText, 15);
    }

    private void setRoofSignPower(Location base, int dx, int dz, String powerText, int cacheIdx) {
        String text = "<aqua>" + powerText;
        if (signCache[cacheIdx][2] != null && signCache[cacheIdx][2].equals(text)) return;
        signCache[cacheIdx][2] = text;

        Block block = base.clone().add(dx, 1, dz).getBlock();
        var state = block.getState();
        if (state instanceof Sign signState) {
            signState.line(2, MessageUtil.parse(text));
            signState.update(true, false);
        }
    }

    // =========================
    // SIGN WRITE WITH CACHE
    // =========================
    private void setSign(Location base, int[] off, int line, String text, int cacheIdx) {
        setSignLine(base, off, line, text, cacheIdx);
    }

    private void setSignLine(Location base, int[] off, int line, String text, int cacheIdx) {
        if (signCache[cacheIdx][line] != null && signCache[cacheIdx][line].equals(text)) return;
        signCache[cacheIdx][line] = text;

        Block block = base.clone().add(off[0], off[1], off[2]).getBlock();
        var state = block.getState();
        if (state instanceof Sign signState) {
            signState.line(line, MessageUtil.parse(text));
            signState.update(true, false);
        }
    }

    // =========================
    // LOCALIZED MESSAGE
    // =========================
    private static String msg(String key, String def) {
        return StructuresMessages.get(key, def);
    }

    // =========================
    // HELPER: IS BULB POWERED
    // =========================
    public boolean isBulbPowered(Location base, int dx, int dy, int dz) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() != Materials.WAXED_COPPER_BULB) return false;
        CopperBulb bulbData = (CopperBulb) block.getBlockData();
        return bulbData.isPowered();
    }

    // =========================
    // HELPER: SET BULB LIT
    // =========================
    public void setBulbLit(Location base, int dx, int dy, int dz, boolean lit) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() != Materials.WAXED_COPPER_BULB) return;
        CopperBulb bulbData = (CopperBulb) block.getBlockData();
        if (bulbData.isLit() != lit) {
            bulbData.setLit(lit);
            block.setBlockData(bulbData);
        }
    }

    // =========================
    // RESET DISPLAY VALUES
    // =========================
    public void resetDisplay() {
        displayCoreTemp = 0;
        displayShieldPress = 0;
        displaySpin = 0;
        displayCoreShInt = 100;
        displayCoreCaseTemp = 0;
        displayCoreCasePress = 0;
        displayCoreCaseInt = 100;
        displayRecipeTime = 0;
        displayReactorWear = 0;
        displayEnergyRate = 0;
        displayTick = 0;
        prevHeating = false;
        prevCooling = false;
        integrityWarnTick = 0;
        soundTick = 0;
        for (int i = 0; i < signCache.length; i++) {
            signCache[i] = new String[4];
        }
    }

    // =========================
    // INTEGRITY INDICATOR UPDATE — side barrels glow bulbs
    // (old positions were part of the legacy geometry; kept as no-op-safe)
    // =========================
    public void updateIntegrityBulbs(Location base) {
        setBulbLit(base, -3, -5, 0, reactor.getCoreShInt() < 100);
        setBulbLit(base, 3, -5, 0, reactor.getCoreCaseInt() < 100);
    }

    // =========================
    // GETTERS
    // =========================
    public int getDisplayTick() { return displayTick; }

    // Smoothed display values for command output
    public int getDisplayCoreTemp() { return (int) Math.round(displayCoreTemp); }
    public double getDisplayShieldPress() { return displayShieldPress; }
    public double getDisplayCoreSpin() { return displaySpin; }
    public int getDisplayCoreShInt() { return (int) Math.round(displayCoreShInt); }
    public int getDisplayCoreCaseTemp() { return (int) Math.round(displayCoreCaseTemp); }
    public int getDisplayCoreCasePress() { return (int) Math.round(displayCoreCasePress); }
    public int getDisplayCoreCaseInt() { return (int) Math.round(displayCoreCaseInt); }
    public int getDisplayRecipeTime() { return (int) Math.round(displayRecipeTime); }
    public int getDisplayReactorWear() { return (int) Math.round(displayReactorWear); }
    public int getDisplayEnergyRate() { return (int) Math.round(displayEnergyRate); }

    // Broadcast state tracking
    public boolean wasHeating() { return prevHeating; }
    public boolean wasCooling() { return prevCooling; }
    public void setHeating(boolean val) { prevHeating = val; }
    public void setCooling(boolean val) { prevCooling = val; }

    // Integrity warn tick
    public int getIntegrityWarnTick() { return integrityWarnTick; }
    public void setIntegrityWarnTick(int val) { integrityWarnTick = val; }
}
