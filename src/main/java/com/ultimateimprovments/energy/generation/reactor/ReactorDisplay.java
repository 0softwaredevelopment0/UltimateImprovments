package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.Materials;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.CopperBulb;

/**
 * Управление визуальными эффектами, звуками и обновлением табличек реактора.
 * <p>
 * Извлечено из {@link ReactorManager} для уменьшения размера класса (~1400 → ~700 строк).
 */
public class ReactorDisplay {

    private final ReactorManager reactor;

    // =========================
    // SMOOTHED DISPLAY VALUES (interpolated toward actual values)
    // =========================
    private double displayCoreTemp;
    private double displayCorePress;
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

    public ReactorDisplay(ReactorManager reactor) {
        this.reactor = reactor;
    }

    // =========================
    // SMOOTH DISPLAY TICK (every tick)
    // Interpolates display values toward actual values for smooth sign updates.
    // =========================
    public void tickSmoothDisplay() {
        displayCoreTemp += (reactor.getCoreTemp() - displayCoreTemp) * SMOOTHING_FACTOR;
        displayCorePress += (reactor.getCorePress() - displayCorePress) * SMOOTHING_FACTOR;
        displayCoreShInt += (reactor.getCoreShInt() - displayCoreShInt) * SMOOTHING_FACTOR;
        displayCoreCaseTemp += (reactor.getCoreCaseTemp() - displayCoreCaseTemp) * SMOOTHING_FACTOR;
        displayCoreCasePress += (reactor.getCoreCasePress() - displayCoreCasePress) * SMOOTHING_FACTOR;
        displayCoreCaseInt += (reactor.getCoreCaseInt() - displayCoreCaseInt) * SMOOTHING_FACTOR;
        displayRecipeTime += (reactor.getRecipeTime() - displayRecipeTime) * SMOOTHING_FACTOR;

        displayReactorWear += (reactor.getReactorWear() - displayReactorWear) * SMOOTHING_FACTOR;

        double rawEnergyRate = reactor.getCoreTemp() > 1000 ? (double) reactor.getCoreTemp() * 0.9 * 20.0 : 0.0;
        displayEnergyRate += (rawEnergyRate - displayEnergyRate) * SMOOTHING_FACTOR;
    }

    // =========================
    // SOUND TICK (every 10 ticks)
    // =========================
    public void tickSound() {
        Location base = reactor.getReactorLocation();
        if (base == null) return;

        // Beep when any integrity is damaged
        if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) {
            base.getWorld().playSound(
                    base, Sound.BLOCK_NOTE_BLOCK_PLING,
                    SoundCategory.MASTER, 1.0f, 1.5f
            );
        }
    }

    // =========================
    // VISUAL TICK (every tick — particles)
    // Core chamber center: Y=-3 (midpoint between upper WAXED_CHISELED_COPPER at Y=-1
    // and lower WAXED_CHISELED_COPPER at Y=-5), X=0, Z=0.
    // =========================
    public void tickVisual() {
        Location base = reactor.getReactorLocation();
        if (base == null) return;

        // Core center: (0.5, -2.5, 0.5) — midpoint between upper core (Y=-1) and lower core (Y=-5)
        Location coreCenter = base.clone().add(0.5, -2.5, 0.5);

        int coreTemp = reactor.getCoreTemp();
        boolean meltdown = reactor.isMeltdownCountdown();
        int meltdownTimer = reactor.getMeltdownTimer();

        // =========================
        // CORE TEMPERATURE PARTICLES
        // =========================
        Particle.DustOptions color;

        if (coreTemp <= 999) {
            color = new Particle.DustOptions(Color.fromRGB(128, 128, 128), 1.25f);
        } else if (coreTemp <= 1499) {
            color = new Particle.DustOptions(Color.fromRGB(128, 0, 0), 1.25f);
        } else if (coreTemp <= 1999) {
            color = new Particle.DustOptions(Color.RED, 1.25f);
        } else if (coreTemp <= 2999) {
            color = new Particle.DustOptions(Color.ORANGE, 1.25f);
        } else if (coreTemp <= 3999) {
            color = new Particle.DustOptions(Color.YELLOW, 1.25f);
        } else {
            color = new Particle.DustOptions(Color.WHITE, 1.25f);
        }

        base.getWorld().spawnParticle(
                Particle.DUST, coreCenter, 16, 0, 0, 0, 0, color
        );

        // =========================
        // HIGH TEMP EFFECTS
        // =========================
        if (coreTemp >= 1000 && coreTemp <= 4999) {
            Location diamondLoc = base.clone().add(0, -2, -2);
            if (diamondLoc.getBlock().getType() == Material.DIAMOND_BLOCK) {
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
                        Particle.SCRAPE,
                        coreCenter.clone().add(0, 0, 1),
                        1, 0, 0, 2, 1
                );
                base.getWorld().spawnParticle(
                        Particle.COPPER_FIRE_FLAME,
                        coreCenter.clone().add(0, 0, 2.4),
                        1, 0, 0, 0, 0.01f
                );
            }
        }

        // =========================
        // BEACON HUM SOUND AT HIGH TEMP
        // =========================
        if (coreTemp >= 1000 && !meltdown) {
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

            int smokeCount = 8 + (int)(progress * 56);     // 8 → 64
            int fireCount  = 2 + (int)(progress * 14);     // 2 → 16

            // Escalating smoke (at normal smoke position, 2.5 blocks above core)
            Location smokePos = coreCenter.clone().add(0, 2.5, 0);
            base.getWorld().spawnParticle(
                    Particle.CAMPFIRE_SIGNAL_SMOKE,
                    smokePos, smokeCount, 0.5, 0.5, 0.5, 0.15
            );

            // Escalating fire
            base.getWorld().spawnParticle(
                    Particle.LAVA, coreCenter, fireCount, 0.3, 0.3, 0.3, 0
            );
            base.getWorld().spawnParticle(
                    Particle.FLAME, coreCenter, fireCount, 0.3, 0.3, 0.3, 0.05
            );

            // Escalating hum
            float volume = 0.5f + progress * 4.0f;
            float pitch  = 0.5f + progress * 0.8f;
            base.getWorld().playSound(
                    coreCenter,
                    Sound.BLOCK_BEACON_AMBIENT,
                    SoundCategory.MASTER, volume, pitch
            );

            // =========================
            // SPARK PARTICLES AROUND CORE
            // =========================
            int sparkCount = 2 + (int)(progress * 8);  // 2 → 10
            base.getWorld().spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    coreCenter, sparkCount, 1.0, 1.0, 1.0, 0
            );
        }
    }

    // =========================
    // UPDATE DISPLAYS (signs)
    // =========================
    public void updateDisplays() {
        Location base = reactor.getReactorLocation();
        if (base == null) return;

        displayTick++;

        boolean selfDestruct = reactor.isSelfDestructActive() || reactor.isMeltdownCountdown();
        boolean meltdownCdown = reactor.isMeltdownCountdown();

        // =========================
        // САМОУНИЧТОЖЕНИЕ: все строки пустые, на 2-й строке "Нет сигнала"
        // =========================
        if (selfDestruct) {
            String blank = " ";
            String noSignal = "§cНЕТ СИГНАЛА";
            String meltdownLine = meltdownCdown
                    ? "§cВзрыв неизбежен!"
                    : noSignal;

            setSignText(base, 0, -4, -3, 0, blank);
            setSignText(base, 0, -4, -3, 1, meltdownLine);
            setSignText(base, 0, -4, -3, 2, blank);
            setSignText(base, 0, -4, -3, 3, blank);

            setSignText(base, -1, -4, -3, 0, blank);
            setSignText(base, -1, -4, -3, 1, meltdownLine);
            setSignText(base, -1, -4, -3, 2, blank);
            setSignText(base, -1, -4, -3, 3, blank);

            setSignText(base, 1, -4, -3, 0, blank);
            setSignText(base, 1, -4, -3, 1, meltdownLine);
            setSignText(base, 1, -4, -3, 2, blank);
            setSignText(base, 1, -4, -3, 3, blank);
            return;
        }

        int displayCoreTempInt = (int) Math.round(displayCoreTemp);
        int displayCorePressInt = (int) Math.round(displayCorePress);
        int displayCoreShIntInt = (int) Math.round(displayCoreShInt);
        int displayCoreCaseTempInt = (int) Math.round(displayCoreCaseTemp);
        int displayCoreCasePressInt = (int) Math.round(displayCoreCasePress);
        int displayCoreCaseIntInt = (int) Math.round(displayCoreCaseInt);
        int displayRecipeTimeInt = (int) Math.round(displayRecipeTime);

        // Flash all text red-white when any integrity is below 100%
        boolean flashing = displayCoreShIntInt < 100 || displayCoreCaseIntInt < 100;
        String color = (flashing && (displayTick % 10 < 5)) ? "§c" : "§f";

        // =========================
        // CENTER SIGN — CORE DATA
        // =========================
        setSignText(base, 0, -4, -3, 0, color + "Данные ядра");
        setSignText(base, 0, -4, -3, 1, color + "T: " + displayCoreTempInt + " C*");
        setSignText(base, 0, -4, -3, 2, color + "P: " + displayCorePressInt + " kPa");
        setSignText(base, 0, -4, -3, 3, color + "I: " + displayCoreShIntInt + " %");

        // =========================
        // LEFT SIGN — CASE DATA
        // =========================
        setSignText(base, -1, -4, -3, 0, color + "Данные корпуса");
        setSignText(base, -1, -4, -3, 1, color + "T: " + displayCoreCaseTempInt + " C*");
        setSignText(base, -1, -4, -3, 2, color + "P: " + displayCoreCasePressInt + " kPa");
        setSignText(base, -1, -4, -3, 3, color + "I: " + displayCoreCaseIntInt + " %");

        // =========================
        // RIGHT SIGN — RECIPE DATA
        // =========================
        setSignText(base, 1, -4, -3, 0, color + "Данные рецепта");
        setSignText(base, 1, -4, -3, 1, color + "P: " + displayRecipeTimeInt + " %");

        if (displayRecipeTimeInt <= 0) {
            setSignText(base, 1, -4, -3, 2, color + "S: Бездействует");
        } else if (displayRecipeTimeInt < reactor.getRecipeTimeMax()) {
            setSignText(base, 1, -4, -3, 2, color + "S: Готовится");
        } else {
            setSignText(base, 1, -4, -3, 2, color + "S: Завершён");
        }

        // Износ реактора
        int displayWear = (int) Math.round(displayReactorWear);
        setSignText(base, 1, -4, -3, 3, color + "W: " + displayWear + " %");
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
    // HELPER: SET SIGN TEXT
    // =========================
    private void setSignText(Location base, int dx, int dy, int dz, int line, String text) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        var state = block.getState();
        if (state instanceof org.bukkit.block.Sign signState) {
            signState.setLine(line, text);
            signState.update(true, false);
        }
    }

    // =========================
    // RESET DISPLAY VALUES
    // =========================
    public void resetDisplay() {
        displayCoreTemp = 0;
        displayCorePress = 0;
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
    }

    // =========================
    // INTEGRITY INDICATOR UPDATE
    // =========================
    public void updateIntegrityBulbs(Location base) {
        setBulbLit(base, -1, 0, 2, reactor.getCoreShInt() < 100);
        setBulbLit(base, 1, 0, 2, reactor.getCoreCaseInt() < 100);
    }

    // =========================
    // GETTERS
    // =========================
    public int getDisplayTick() { return displayTick; }

    // Smoothed display values for command output
    public int getDisplayCoreTemp() { return (int) Math.round(displayCoreTemp); }
    public int getDisplayCorePress() { return (int) Math.round(displayCorePress); }
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
