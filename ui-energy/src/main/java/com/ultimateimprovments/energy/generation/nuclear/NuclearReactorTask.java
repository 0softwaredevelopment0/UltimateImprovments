package com.ultimateimprovments.energy.generation.nuclear;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * One-second pass over every assembled compact nuclear reactor.
 * <p>
 * Energy gates, in order: chunk loaded → template intact → cable under the
 * bottom port → the network has room for the output (a saturated network
 * switches the reactor off) → redstone-powered center block → fuel available.
 * Only when all of them are open is energy pushed into the network, and the
 * fuel countdown advances <b>proportionally to the accepted energy</b>: a full
 * or missing network never wastes an emerald.
 * <p>
 * Thermal model (same shape as the DFC):
 * <ul>
 *   <li>the barrel also accepts ice — one block per second while the cooling
 *       buffer is not full (plain ice 1%, packed ice 2%, blue ice 3%);</li>
 *   <li>a running reactor heats up to {@code work_temp} (512°) at
 *       {@code heat_rate} (20°/s) with passive cooling disabled;</li>
 *   <li>at the working point it burns one cooling percent per
 *       {@code cooling_drain_seconds} (8s) and holds the temperature;</li>
 *   <li>without coolant it keeps heating to {@code max_temp} (1024°) and the
 *       center block turns into lava — a 10-second detonation countdown;</li>
 *   <li>a stopped reactor passively cools down to the biome floor rolled at
 *       assembly, and a restored coolant buffer cools a running reactor back
 *       to its working point.</li>
 * </ul>
 */
public class NuclearReactorTask extends BukkitRunnable {

    /** The task runs once per second; all rates below are per-second. */
    public static final int PASS_TICKS = 20;

    @Override
    public void run() {
        Main main = Main.getInstance();
        if (main == null) return;

        FileConfiguration cfg = main.getConfig();
        if (!cfg.getBoolean("nuclear_reactor.enabled", true)) return;

        int energyPerSecond = NuclearReactorManager.energyPerSecond();
        if (energyPerSecond <= 0) return;

        int fuelIntervalSeconds = Math.max(1,
                cfg.getInt("nuclear_reactor.fuel_interval_seconds", 10));
        int fuelTicksPerItem = fuelIntervalSeconds * 20;
        Material fuel = NuclearReactorManager.fuelMaterial();
        boolean log = cfg.getBoolean("nuclear_reactor.log", false);

        // DFC-style lazy audit: this many template cells are re-read per pass
        // (27 cells / 9 per second = a full sweep every 3 seconds).
        int auditCellsPerSecond = Math.max(1,
                cfg.getInt("nuclear_reactor.audit_cells_per_second", 9));

        for (NuclearReactor reactor : NuclearReactorManager.getReactors()) {
            if (reactor == null || reactor.getAnchor() == null
                    || reactor.getAnchor().getWorld() == null) {
                continue;
            }
            if (!reactor.isChunkLoaded()) continue;

            // A legacy reactor (built before the thermal model) rolls its
            // biome limits once, now that the chunk is certainly loaded.
            if (!reactor.isThermalReady()) {
                NuclearReactorManager.initThermal(reactor);
            }

            // 0. Meltdown countdown: the center is lava and the reactor
            //    produces nothing — only the clock runs.
            if (reactor.isMeltdown()) {
                NuclearReactorManager.spawnHeatParticles(reactor);
                int left = reactor.getMeltdownSeconds() - 1;
                reactor.setMeltdownSeconds(left);
                if (left <= 0) {
                    NuclearReactorManager.detonate(reactor);
                    continue;
                }
                apply(reactor, NuclearReactor.Status.MELTDOWN, log);
                continue;
            }

            // 1. Coolant refill — 1 ice block per second while the buffer is
            //    not full, independent of the running state (the gauge is a
            //    buffer the operator fills in advance).
            refillCooling(reactor);

            // 2. Lazy structural audit (DFC-style). Player breaks/places update
            //    the cache in O(1); the rotating sweep catches event-blind
            //    changes (explosions, pistons, plugin setType). Transitions are
            //    reported through the same damage/failure alerts.
            NuclearReactorAudit.Result audit =
                    NuclearReactorAudit.audit(reactor, auditCellsPerSecond);
            NuclearReactorManager.handleStructureAudit(reactor, audit);
            if (audit.allMissing()) {
                continue; // dismantled — the marker and row are gone
            }

            // 3. Energy gates.
            boolean running = false;
            NuclearReactor.Status status;

            if (!audit.intact()) {
                // Any broken template cell stops the reactor.
                status = NuclearReactor.Status.DAMAGED;
            } else {
                // The bottom copper port must touch a cable node.
                CableNode node = NuclearReactorManager.findPortNode(reactor.getEnergyPortLocation());

                if (node == null) {
                    status = NuclearReactor.Status.NO_CABLE;
                } else if (!EnergyFlow.hasSpace(node.getLocation(), 1)) {
                    // Gate 1 — the network is saturated: switch the reactor
                    // OFF before redstone and fuel are even considered. It
                    // resumes automatically on the first second with free room.
                    status = NuclearReactor.Status.OUTPUT_BLOCKED;
                } else {
                    // Gate 2 — the reinforced deepslate in the center must be
                    // powered. A lever/button on top of the barrel strongly
                    // powers the barrel, which weakly powers the adjacent
                    // center; checking the barrel too keeps the gate working
                    // regardless of how the server maps weak power. (Glass
                    // panes cannot hold a lever.)
                    Block center = reactor.getCenterBlock();
                    Block barrel = reactor.getBarrelBlock();
                    boolean powered = center.isBlockPowered()
                            || center.isBlockIndirectlyPowered()
                            || barrel.isBlockPowered()
                            || barrel.isBlockIndirectlyPowered();

                    if (!powered) {
                        status = NuclearReactor.Status.UNPOWERED;
                    } else if (!reactor.hasFuel()
                            && !reactor.takeFuelItem(fuel, fuelTicksPerItem)) {
                        status = NuclearReactor.Status.NO_FUEL;
                    } else {
                        // On the last fraction of a cycle only that fraction of
                        // the per-second output is offered, so an emerald is
                        // never over-spent.
                        int request = energyPerSecond;
                        if (reactor.getFuelTicksLeft() < PASS_TICKS) {
                            request = Math.max(1, (int) Math.round(
                                    (double) energyPerSecond * reactor.getFuelTicksLeft() / PASS_TICKS));
                        }

                        int accepted = EnergyFlow.push(node.getLocation(), request);
                        if (accepted <= 0) {
                            status = NuclearReactor.Status.OUTPUT_BLOCKED;
                        } else {
                            int burnedTicks = (int) Math.round(
                                    (double) PASS_TICKS * accepted / energyPerSecond);
                            reactor.burnFuelTicks(Math.min(PASS_TICKS, burnedTicks));
                            running = true;
                            status = NuclearReactor.Status.RUNNING;
                        }
                    }
                }
            }

            // 4. Thermal step — heating while running, passive cooling otherwise.
            stepThermal(reactor, running);

            // 5. Heat particles and meltdown ignition at the maximum.
            NuclearReactorManager.spawnHeatParticles(reactor);
            if (reactor.getTemperature() >= NuclearReactorManager.maxTemp()) {
                NuclearReactorManager.startMeltdown(reactor);
                status = NuclearReactor.Status.MELTDOWN;
            }

            apply(reactor, status, log);
        }
    }

    // =========================
    // THERMAL MODEL
    // =========================

    /**
     * Advances the temperature by one second.
     * <p>
     * A running reactor heats to the working point and then holds it while the
     * cooling buffer lasts; the buffer drains one percent every
     * {@code cooling_drain_seconds}. Without coolant it keeps heating toward
     * the maximum. Once the coolant is restored above the working point, the
     * passive rate pulls the temperature back down to the working point. A
     * stopped reactor always passively cools to the biome floor.
     */
    private static void stepThermal(NuclearReactor reactor, boolean running) {
        int temp = reactor.getTemperature();
        int maxTemp = NuclearReactorManager.maxTemp();
        int workTemp = NuclearReactorManager.workTemp();
        int heatRate = NuclearReactorManager.heatRate();

        if (running) {
            if (temp < workTemp) {
                temp = Math.min(workTemp, temp + heatRate);
            } else if (temp == workTemp) {
                if (reactor.getCooling() > 0) {
                    // Working-point equilibrium: hold the temperature and
                    // drain the coolant buffer.
                    int need = NuclearReactorManager.coolingDrainSeconds();
                    int acc = reactor.getCoolDrainSeconds() + 1;
                    if (acc >= need) {
                        reactor.setCooling(reactor.getCooling() - 1);
                        acc = 0;
                    }
                    reactor.setCoolDrainSeconds(acc);
                } else {
                    temp = Math.min(maxTemp, temp + heatRate);
                }
            } else {
                if (reactor.getCooling() > 0) {
                    // Coolant restored: cool back to the working point at the
                    // passive rate (the buffer is not being drained while the
                    // temperature is above the working point).
                    temp = passiveCool(reactor, workTemp);
                } else {
                    temp = Math.min(maxTemp, temp + heatRate);
                }
            }
        } else {
            temp = passiveCool(reactor, reactor.getTempMin());
        }

        reactor.setTemperature(Math.min(maxTemp, Math.max(0, temp)));
    }

    /**
     * Passive cooling: {@code passive_cool_rate} degrees per second scaled by
     * the build-site humidity multiplier, never below {@code floor}. The
     * fractional carry lives in the reactor, because the temperature itself
     * has no fractions.
     */
    private static int passiveCool(NuclearReactor reactor, int floor) {
        double mult = reactor.getNaturalCoolMult() < 0 ? 1.0 : reactor.getNaturalCoolMult();
        double value = NuclearReactorManager.passiveCoolRate() * mult + reactor.getCoolRemainder();
        int whole = (int) value;
        reactor.setCoolRemainder(value - whole);
        return Math.max(floor, reactor.getTemperature() - whole);
    }

    /**
     * Converts up to {@code cooling_blocks_per_second} ice blocks from the
     * barrel into cooling percent, cheapest ice first, and never above the
     * full buffer (a full gauge does not eat ice).
     */
    private static void refillCooling(NuclearReactor reactor) {
        int max = NuclearReactorManager.coolingMax();
        int perPass = NuclearReactorManager.coolingBlocksPerSecond();

        for (int i = 0; i < perPass && reactor.getCooling() < max; i++) {
            Material chosen = null;
            int value = 0;

            for (Material material : NuclearReactorManager.coolingBlocks()) {
                int amount = NuclearReactorManager.coolingValue(material);
                if (amount > 0 && reactor.countItem(material) > 0) {
                    chosen = material;
                    value = amount;
                    break;
                }
            }

            if (chosen == null) return;
            if (!reactor.takeOne(chosen)) return;
            reactor.setCooling(Math.min(max, reactor.getCooling() + value));
        }
    }

    // =========================
    // LOG
    // =========================

    private static void apply(NuclearReactor reactor, NuclearReactor.Status status, boolean log) {
        if (reactor.setStatus(status) && log) {
            ConsoleLogger.info("[NuclearReactor] " + reactor.getAnchor().getBlockX() + " "
                    + reactor.getAnchor().getBlockY() + " "
                    + reactor.getAnchor().getBlockZ() + " -> " + status);
        }
    }
}
