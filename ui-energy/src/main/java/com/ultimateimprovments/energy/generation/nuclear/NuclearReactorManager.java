package com.ultimateimprovments.energy.generation.nuclear;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.NodeType;
import com.ultimateimprovments.structure.StructureMarker;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.MultimeterApi;
import com.ultimateimprovments.util.StructureTemplate;
import com.ultimateimprovments.util.StructuresMessages;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry, assembly, persistence and player-facing events of the compact
 * nuclear reactors.
 * <p>
 * A reactor is a 3×3×3 NBT structure ({@code nuclearreactor.nbt}) assembled
 * with an item frame, like every other multi-block structure. One reactor
 * instance exists per anchor (the top-center fuel barrel), so several reactors
 * can coexist in the same world; the anchor is stored in the structure marker
 * registry (SQLite) and restored on startup, while the thermal state lives in
 * the {@code nuclear_reactors} table keyed by the same anchor.
 * <p>
 * This class owns the structure-level events (damage reports, detonation) and
 * the config accessors; {@link NuclearReactorTask} does the per-second tick.
 */
public final class NuclearReactorManager {

    /** NBT template name (file name without extension). */
    public static final String TEMPLATE_NAME = "nuclearreactor";

    /** Structure marker type written to the SQLite registry. */
    public static final String MARKER_TYPE = "nuclearreactor";

    /**
     * Cooling blocks accepted by the barrel, cheapest first — the refill eats
     * plain ice before packed ice before blue ice, so a nearly full buffer
     * never wastes the strongest coolant.
     */
    private static final Material[] COOLING_BLOCKS = {
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE
    };

    /** Particle thresholds and counts (see the reactor spec). */
    private static final int PARTICLE_SMOKE_TEMP = 640;
    private static final int PARTICLE_SMOKE_COUNT = 8;
    private static final int PARTICLE_LARGE_SMOKE_TEMP = 860;
    private static final int PARTICLE_LARGE_SMOKE_COUNT = 8;
    private static final int PARTICLE_LAVA_TEMP = 960;
    private static final int PARTICLE_LAVA_COUNT = 8;
    /** Emission speed (the {@code extra} argument) — same for every stage. */
    private static final double PARTICLE_SPEED = 0.1;

    private static final Map<Location, NuclearReactor> reactors = new ConcurrentHashMap<>();

    private NuclearReactorManager() {}

    // =========================
    // LIFECYCLE
    // =========================

    /** Rebuilds the in-memory reactor list from the structure marker registry. */
    public static void init() {
        reactors.clear();

        for (Map.Entry<String, StructureMarker.StructureData> entry
                : StructureMarker.getAllEntries()) {
            StructureMarker.StructureData data = entry.getValue();
            if (data == null || !MARKER_TYPE.equals(data.type())) continue;

            String fk = entry.getKey();
            World world;
            try {
                world = Bukkit.getWorld(UUID.fromString(StructureMarker.parseWorldUid(fk)));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (world == null) continue;

            Location anchor;
            try {
                anchor = LocationUtil.normalize(new Location(
                        world,
                        StructureMarker.parseX(fk),
                        StructureMarker.parseY(fk),
                        StructureMarker.parseZ(fk)));
            } catch (RuntimeException e) {
                continue;
            }
            if (anchor == null) continue;

            reactors.put(anchor, new NuclearReactor(anchor));
        }

        // Thermal state (temperature, cooling, meltdown, fuel) per anchor.
        NuclearReactorPersistence.loadAll();

        ConsoleLogger.info("[NuclearReactor] Restored " + reactors.size() + " reactor(s)");
    }

    public static void shutdown() {
        NuclearReactorPersistence.saveAll();
        reactors.clear();
    }

    public static Collection<NuclearReactor> getReactors() {
        return reactors.values();
    }

    public static NuclearReactor getAt(Location anchor) {
        Location norm = LocationUtil.normalize(anchor);
        return norm == null ? null : reactors.get(norm);
    }

    /**
     * Removes the reactor and its persisted state — used when every template
     * block is gone (or the reactor detonated), so a dismantled reactor does
     * not leave a ghost marker / stale row behind forever.
     */
    public static void disassemble(Location anchor) {
        Location norm = LocationUtil.normalize(anchor);
        if (norm == null) return;

        releaseOutputNode(norm);

        NuclearReactor removed = reactors.remove(norm);
        StructureMarker.removeAt(norm);
        NuclearReactorPersistence.delete(norm);
        NuclearReactorAudit.reset(norm);
        if (removed != null) {
            ConsoleLogger.info("[NuclearReactor] Disassembled at " + coords(norm));
        }
    }

    /**
     * Drops the reactor's RAM-only output buffer when the structure goes away.
     * <p>
     * Explosions never fire {@code BlockBreakEvent}, so without this the port
     * node would keep its charge after a detonation and a rebuilt reactor — or
     * any consumer still connected to that spot — would inherit it. The node is
     * drained always; when the copper port block is still standing (a meltdown
     * sealed with a wrong block, a dismantled-but-not-destroyed cube) the node
     * is kept as a plain cable, otherwise it is removed together with its
     * registry row.
     */
    private static void releaseOutputNode(Location anchor) {
        Location port = LocationUtil.normalize(anchor.clone().add(0, -2, 0));
        if (port == null || port.getWorld() == null) return;

        CableNode node = CableNetwork.getNode(port);
        if (node == null) return;

        node.setEnergy(0);
        boolean portPresent = port.getWorld()
                .isChunkLoaded(port.getBlockX() >> 4, port.getBlockZ() >> 4)
                && port.getBlock().getType() == Materials.WAXED_CHISELED_COPPER;
        if (portPresent) {
            node.setType(NodeType.CABLE);
        } else {
            CableNetwork.removeNode(port);
        }
    }

    // =========================
    // LOOKUP BY STRUCTURE BLOCK / FRAME
    // =========================

    /**
     * Finds the reactor whose template occupies {@code loc}. Every cell of the
     * 3×3×3 cube is a template block, so the bounds check is exact.
     */
    public static NuclearReactor getByBlockLocation(Location loc) {
        Location norm = LocationUtil.normalize(loc);
        if (norm == null) return null;

        for (NuclearReactor reactor : reactors.values()) {
            Location anchor = reactor.getAnchor();
            if (anchor == null || anchor.getWorld() == null
                    || !anchor.getWorld().equals(norm.getWorld())) {
                continue;
            }
            int dx = norm.getBlockX() - anchor.getBlockX();
            int dy = norm.getBlockY() - anchor.getBlockY();
            int dz = norm.getBlockZ() - anchor.getBlockZ();
            if (dx >= -1 && dx <= 1 && dy >= -2 && dy <= 0 && dz >= -1 && dz <= 1) {
                return reactor;
            }
        }
        return null;
    }

    /** @return {@code true} when both locations point at the same block. */
    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null
                && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /**
     * Breaks the trigger item frame and drops it (plus any displayed item).
     * The frame is a one-shot assembly trigger, exactly like on the other
     * structures — it must not stay behind and block the barrel.
     */
    public static void dropTriggerFrame(ItemFrame frame) {
        if (frame == null || frame.isDead() || !frame.isValid()) return;

        Location loc = frame.getLocation();
        World world = frame.getWorld();
        if (world == null) return;

        ItemStack displayed = frame.getItem();
        if (displayed != null && !displayed.getType().isAir()) {
            world.dropItemNaturally(loc, displayed);
        }
        Material frameItem = frame instanceof org.bukkit.entity.GlowItemFrame
                ? Material.GLOW_ITEM_FRAME
                : Material.ITEM_FRAME;
        world.dropItemNaturally(loc, new ItemStack(frameItem));
        frame.remove();
    }

    /**
     * First cable node in the six neighbours of the bottom energy port.
     * The port itself is the waxed chiseled copper at {@code (0,−2,0)}; its
     * bottom face is the only exposed one, so the cable sits directly below
     * the structure.
     */
    public static CableNode findPortNode(Location port) {
        if (port == null) return null;

        for (Location near : LocationUtil.getNeighbors(port)) {
            Location norm = LocationUtil.normalize(near);
            if (norm == null) continue;

            CableNode node = CableNetwork.getNode(norm);
            if (node != null && LocationUtil.isFullyConnected(port, norm)) {
                return node;
            }
        }
        return null;
    }

    /**
     * The reactor's own output buffer, living on the bottom copper port.
     * <p>
     * {@link com.ultimateimprovments.energy.transfer.cable.EnergyFlow#push}
     * settles energy only at batteries, while every consumer in the family
     * (electrical lamp, electric furnace, accelerator, DFC case junctions)
     * <b>pulls</b> — and a pull can only drain a node that actually stores
     * energy. Without a battery in the network the reactor therefore produced
     * nothing at all (the space gate reported the network as full). The coal
     * generator solves the same problem with its {@code GENERATOR}-typed
     * leftover buffer; the reactor does the same here.
     * <p>
     * The port is safe as a storage node: it is the template's single waxed
     * chiseled copper cell and a network leaf (its bottom face is the only
     * exposed one and the cable attaches there), so it never routes energy
     * between two cable runs. A player-placed port — or one rebuilt from its
     * {@code cable} marker row after a restart — starts out as a plain
     * {@code CABLE} node, so the type is re-asserted on every pass. The node
     * itself is RAM-only, exactly like the generator's buffer.
     * <p>
     * Unlike the generator's leftover buffer, this one is deliberately
     * <b>not</b> re-offered to the batteries: with a battery in the network the
     * reactor pushes to it first anyway, so the buffer only fills when the
     * batteries are full, and keeping the charge there makes it a local reserve
     * that the nearest consumers drain first (breadth-first pull order).
     *
     * @return the buffer node, or {@code null} when the port block is gone
     *         (damaged structure) or the world is unavailable
     */
    public static CableNode ensureOutputNode(NuclearReactor reactor) {
        if (reactor == null) return null;

        Location port = LocationUtil.normalize(reactor.getEnergyPortLocation());
        if (port == null || port.getWorld() == null) return null;
        if (port.getBlock().getType() != Materials.WAXED_CHISELED_COPPER) return null;

        CableNetwork.ensureNode(port, NodeType.GENERATOR);
        CableNode node = CableNetwork.getNode(port);
        if (node == null) return null;

        if (node.getType() != NodeType.GENERATOR) node.setType(NodeType.GENERATOR);
        node.setMaxEnergy(bufferCapacity());
        return node;
    }

    /**
     * Leftover output buffer of one reactor (config:
     * {@code nuclear_reactor.buffer_capacity}). Whatever the cable network
     * cannot take waits here until a consumer pulls it, so a direct
     * reactor → consumer setup works without a battery.
     */
    public static int bufferCapacity() {
        return Math.max(1, cfgInt("buffer_capacity", 2000));
    }

    // =========================
    // ASSEMBLY
    // =========================

    /**
     * Registers a reactor at {@code center} (the template origin). The caller
     * has already matched the NBT template; this method re-checks it and
     * refuses duplicates at the same anchor.
     *
     * @return the reactor (existing or newly created), or {@code null} when the
     *         structure does not match or the template is unavailable
     */
    public static NuclearReactor assemble(Location center, Player player) {
        Location anchor = LocationUtil.normalize(center);
        if (anchor == null || anchor.getWorld() == null) return null;

        StructureTemplate template = StructureTemplate.get(TEMPLATE_NAME);
        if (template == null) {
            if (player != null) {
                player.sendMessage(MessageUtil.parse(msg("no_template",
                        "<dark_red>❌ <red>The nuclear reactor template is not loaded — check the console!")));
            }
            return null;
        }

        if (!template.checkAt(anchor).matched()) {
            return null;
        }

        NuclearReactor existing = reactors.get(anchor);
        if (existing != null) {
            if (player != null) {
                // Localized structure name — never a hardcoded "nuclear reactor"
                // word; the global UI prefix is part of every refusal.
                player.sendMessage(MessageUtil.parse(MessageUtil.PREFIX
                        + StructuresMessages.get("structure_already_assembled",
                                "<yellow>A structure is already assembled here: <white>%structure%</white><yellow>! <dark_gray>(%coords%)")
                        .replace("%structure%", template.getDisplayName())
                        .replace("%coords%", coords(anchor))));
            }
            return existing;
        }

        NuclearReactor reactor = new NuclearReactor(anchor);
        reactors.put(anchor, reactor);
        StructureMarker.place(anchor, MARKER_TYPE, UUID.randomUUID());

        // Biome climate is rolled once, at build time: the temperature limit
        // becomes the cold floor, the humidity multiplier scales passive
        // cooling. A disassemble + rebuild rolls a fresh pair.
        initThermal(reactor);

        ConsoleLogger.info("[NuclearReactor] Assembled at " + coords(anchor));
        if (player != null) {
            player.sendMessage(MessageUtil.parse(msg("assembled",
                            "<green>✔ <white>Nuclear reactor assembled! <dark_gray>(%coords%)")
                    .replace("%coords%", coords(anchor))));
        }
        return reactor;
    }

    /**
     * Rolls the build-site climate into the reactor: the temperature lower
     * limit (biome temperature, like the DFC) and the passive-cooling
     * multiplier (biome humidity). A fresh reactor starts at its cold floor
     * with an empty cooling buffer.
     */
    public static void initThermal(NuclearReactor reactor) {
        if (reactor == null) return;
        Location anchor = reactor.getAnchor();
        if (anchor == null) return;

        reactor.setTempMin(rollTempLimit(anchor));
        reactor.setNaturalCoolMult(rollNaturalCoolMultiplier(anchor));
        reactor.setTemperature(reactor.getTempMin());
        reactor.setCooling(0);
        reactor.setCoolDrainSeconds(0);
        reactor.setCoolRemainder(0);
        reactor.setMeltdownSeconds(0);
        reactor.setDamaged(false);
        reactor.setThermalReady(true);
        NuclearReactorPersistence.save(reactor);
    }

    /**
     * Rolls the dynamic lower temperature limit from the build-site biome:
     * biome temperature × {@code nuclear_reactor.temp_limit_biome_multiplier}
     * + {@code temp_limit_offset}, rounded to whole degrees. Read live from the
     * biome holder, so datapack/custom biomes work; a biome without a value
     * falls back to {@code temp_limit_biome_fallback}.
     */
    private static int rollTempLimit(Location base) {
        double biomeTemp;
        try {
            double t = base.getBlock().getTemperature();
            biomeTemp = Double.isFinite(t) ? t : cfgDouble("temp_limit_biome_fallback", 1.0);
        } catch (Exception e) {
            biomeTemp = cfgDouble("temp_limit_biome_fallback", 1.0);
        }
        double raw = biomeTemp * cfgDouble("temp_limit_biome_multiplier", 10.0)
                + cfgDouble("temp_limit_offset", 0.0);
        return Math.max(0, (int) Math.round(raw));
    }

    /**
     * Rolls the passive-cooling multiplier from the build-site biome:
     * biome humidity + {@code passive_cool_multiplier_offset}, clamped to
     * [{@code passive_cool_multiplier_min} .. {@code _max}]. The rate itself
     * ({@code passive_cool_rate}, degrees per second) stays live-tunable —
     * this multiplier only scales it.
     */
    private static double rollNaturalCoolMultiplier(Location base) {
        double humidity;
        try {
            double h = base.getBlock().getHumidity();
            humidity = Double.isFinite(h) ? h : cfgDouble("passive_cool_biome_fallback", 0.5);
        } catch (Exception e) {
            humidity = cfgDouble("passive_cool_biome_fallback", 0.5);
        }
        double mult = humidity + cfgDouble("passive_cool_multiplier_offset", 0.0);
        return Math.max(cfgDouble("passive_cool_multiplier_min", 0.0),
                Math.min(cfgDouble("passive_cool_multiplier_max", 1.0), mult));
    }

    // =========================
    // STRUCTURE DAMAGE / REPAIR
    // =========================

    /**
     * Handles a player breaking one block of an active reactor.
     * <ul>
     *   <li>the redstone center block detonates the reactor (charged creeper,
     *       explosion power configured, instant fuse);</li>
     *   <li>the last remaining template block disassembles the reactor and
     *       announces the failure;</li>
     *   <li>any other block flips the structure into the damaged state once
     *       (the repair flips it back once).</li>
     * </ul>
     *
     * @return {@code true} when the block belonged to a reactor
     */
    public static boolean handleBlockBreak(Location loc) {
        NuclearReactor reactor = getByBlockLocation(loc);
        if (reactor == null) return false;

        if (sameBlock(loc, reactor.getCenterBlockLocation())) {
            detonate(reactor);
            return true;
        }

        // The event path updates the audit cache in O(1), so the player action
        // is reflected instantly — the rotating audit is the bypass net.
        NuclearReactorAudit.noteBroken(reactor, loc);

        if (NuclearReactorAudit.presentCount(reactor) <= 0) {
            broadcast(reactor, msg("failure_detected",
                    "<dark_gray>[<yellow>CER<dark_gray>] <gold>Attention! <white>CER structure failure detected!"));
            disassemble(reactor.getAnchor());
            return true;
        }

        if (!reactor.isDamaged()) {
            reactor.setDamaged(true);
            broadcast(reactor, msg("damage_detected",
                    "<dark_gray>[<yellow>CER<dark_gray>] <gold>Attention! <white>CER structure damage detected!"));
            NuclearReactorPersistence.save(reactor);
        }
        return true;
    }

    /**
     * Handles a player placing a block inside / on an active reactor.
     * <p>
     * The meltdown rescue comes first: replacing the lava center with
     * reinforced deepslate prevents the detonation and resets the reactor to
     * its working temperature. Any other block seals the meltdown (the blast
     * is cancelled) but ruins the reactor — it is dismantled and can be
     * rebuilt later. Otherwise a completed template ends the damaged state.
     */
    public static void handleBlockPlace(Block block) {
        if (block == null) return;
        Location loc = block.getLocation();

        for (NuclearReactor reactor : reactors.values()) {
            if (!reactor.isMeltdown()) continue;
            if (!sameBlock(loc, reactor.getCenterBlockLocation())) continue;

            if (block.getType() == Material.REINFORCED_DEEPSLATE) {
                preventDetonation(reactor);
            } else {
                abandonMeltdown(reactor);
            }
            return;
        }

        NuclearReactor reactor = getByBlockLocation(loc);
        if (reactor == null) return;

        NuclearReactorAudit.noteRepaired(reactor, loc);

        if (NuclearReactorAudit.allPresent(reactor) && reactor.isDamaged()) {
            reactor.setDamaged(false);
            broadcast(reactor, msg("damage_restored",
                    "<dark_gray>[<yellow>CER<dark_gray>] <gold>Attention! <white>CER structure damage restored!"));
            NuclearReactorPersistence.save(reactor);
        }
    }

    /**
     * Bypass-proof structure transitions from the rotating audit: a missing
     * cell flips the reactor into the damaged state once, a fully repaired
     * template flips it back once, and an empty structure is dismantled with
     * the failure alert — so explosions, pistons and plugin block changes are
     * handled exactly like player breaks.
     */
    public static void handleStructureAudit(NuclearReactor reactor, NuclearReactorAudit.Result result) {
        if (reactor == null || result == null) return;

        if (result.allMissing()) {
            broadcast(reactor, msg("failure_detected",
                    "<dark_gray>[<yellow>CER<dark_gray>] <gold>Attention! <white>CER structure failure detected!"));
            disassemble(reactor.getAnchor());
            return;
        }

        boolean damaged = !result.intact();
        if (damaged && !reactor.isDamaged()) {
            reactor.setDamaged(true);
            broadcast(reactor, msg("damage_detected",
                    "<dark_gray>[<yellow>CER<dark_gray>] <gold>Attention! <white>CER structure damage detected!"));
            NuclearReactorPersistence.save(reactor);
        } else if (!damaged && reactor.isDamaged()) {
            reactor.setDamaged(false);
            broadcast(reactor, msg("damage_restored",
                    "<dark_gray>[<yellow>CER<dark_gray>] <gold>Attention! <white>CER structure damage restored!"));
            NuclearReactorPersistence.save(reactor);
        }
    }

    // =========================
    // MELTDOWN / DETONATION
    // =========================

    /**
     * Turns the center block into lava and starts the detonation countdown.
     * Called by the tick when the temperature reaches the maximum; the
     * countdown is owned by the tick, so nothing explodes right away.
     */
    public static void startMeltdown(NuclearReactor reactor) {
        if (reactor == null || reactor.isMeltdown()) return;

        Block center = reactor.getCenterBlock();
        if (center.getType() != Material.LAVA) {
            center.setType(Material.LAVA, false);
        }

        int seconds = Math.max(1, cfgInt("meltdown_seconds", 10));
        reactor.setMeltdownSeconds(seconds);
        reactor.setTemperature(Math.max(reactor.getTemperature(), maxTemp()));
        broadcast(reactor, msg("meltdown_detected",
                "<dark_gray>[<yellow>CER<dark_gray>] <red>Danger! <white>CER meltdown detected, structure detonation will occur in T-%sec%s.")
                .replace("%sec%", String.valueOf(seconds)));
        NuclearReactorPersistence.save(reactor);
    }

    /**
     * The player replaced the lava center with reinforced deepslate in time:
     * the detonation is cancelled, the reactor drops to its working
     * temperature and keeps running.
     */
    public static void preventDetonation(NuclearReactor reactor) {
        if (reactor == null) return;

        reactor.setMeltdownSeconds(0);
        reactor.setTemperature(workTemp());
        reactor.setDamaged(false);
        // The lava center may have been seeded as a missing cell (a break
        // during the countdown seeds the audit) — re-seed so the restored
        // reinforced deepslate counts as present immediately.
        NuclearReactorAudit.reset(reactor.getAnchor());
        broadcast(reactor, msg("detonation_prevented",
                "<dark_gray>[<yellow>CER<dark_gray>] <gold>Attention! <white>CER structure detonation prevented!"));
        NuclearReactorPersistence.save(reactor);
    }

    /**
     * The player sealed the lava center with a wrong block: the blast is
     * cancelled (same alert) but the reactor is ruined — it is dismantled so
     * the player can rebuild it after restoring the reinforced deepslate.
     */
    public static void abandonMeltdown(NuclearReactor reactor) {
        if (reactor == null) return;

        reactor.setMeltdownSeconds(0);
        broadcast(reactor, msg("detonation_prevented",
                "<dark_gray>[<yellow>CER<dark_gray>] <gold>Attention! <white>CER structure detonation prevented!"));
        disassemble(reactor.getAnchor());
    }

    /**
     * Instant detonation: a charged creeper with a zero fuse and the configured
     * explosion power (same shot as the DFC meltdown). The reactor is
     * dismantled before the blast so the explosion cannot re-enter the damage
     * handlers.
     */
    public static void detonate(NuclearReactor reactor) {
        if (reactor == null) return;

        Location center = reactor.getCenterBlockLocation().add(0.5, 0.5, 0.5);
        World world = center.getWorld();
        if (world != null) {
            Creeper creeper = world.spawn(center, Creeper.class);
            creeper.setPowered(true);
            creeper.setExplosionRadius(Math.max(1, cfgInt("explosion_power", 5)));
            creeper.setMaxFuseTicks(0);
            creeper.setIgnited(true);
        }

        ConsoleLogger.info("[NuclearReactor] Detonated at " + coords(reactor.getAnchor()));
        disassemble(reactor.getAnchor());
    }

    /**
     * Threshold particles above the center block (1.5 blocks up):
     * signal smoke above 640°, large smoke above 860°, lava sparks above 960°.
     * Every stage emits the same count (8) with the same speed (0.1), so the
     * plume stays readable instead of turning into a fast jet.
     */
    public static void spawnHeatParticles(NuclearReactor reactor) {
        if (reactor == null) return;

        int temp = reactor.getTemperature();
        if (temp <= PARTICLE_SMOKE_TEMP) return;

        Location loc = reactor.getCenterBlockLocation().add(0.5, 1.5, 0.5);
        World world = loc.getWorld();
        if (world == null) return;

        if (temp > PARTICLE_LAVA_TEMP) {
            world.spawnParticle(Particle.LAVA, loc, PARTICLE_LAVA_COUNT,
                    0.0, 0.0, 0.0, PARTICLE_SPEED);
        }
        if (temp > PARTICLE_LARGE_SMOKE_TEMP) {
            world.spawnParticle(Particle.LARGE_SMOKE, loc, PARTICLE_LARGE_SMOKE_COUNT,
                    0.0, 0.0, 0.0, PARTICLE_SPEED);
        }
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, PARTICLE_SMOKE_COUNT,
                0.0, 0.0, 0.0, PARTICLE_SPEED);
    }

    // =========================
    // BROADCAST (30 blocks by default)
    // =========================

    /**
     * Sends one localized alert to every player within
     * {@code nuclear_reactor.alert_radius} blocks of the reactor. The message
     * body already carries the {@code [CER]} tag; the global plugin prefix is
     * prepended here.
     */
    public static void broadcast(NuclearReactor reactor, String body) {
        if (reactor == null || body == null) return;
        Location anchor = reactor.getAnchor();
        if (anchor == null || anchor.getWorld() == null) return;

        double radius = Math.max(1, cfgInt("alert_radius", 30));
        double radiusSq = radius * radius;

        for (Player player : anchor.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(anchor) <= radiusSq) {
                player.sendMessage(MessageUtil.parse(MessageUtil.PREFIX + body));
            }
        }
    }

    // =========================
    // MULTIMETER READOUT (SHIFT+RMB on any reactor block)
    // =========================

    /**
     * Full CER data pack. Requires a multimeter in either hand; without it the
     * player gets the localized "multimeter required" hint, exactly like the
     * coal generator and the DFC control signs.
     */
    public static void sendReadout(Player player, NuclearReactor reactor) {
        if (player == null || reactor == null) return;

        if (!MultimeterApi.holdsMultimeter(player)) {
            player.sendMessage(MessageUtil.parse(prefix() + msg("multimeter_required",
                    "<red>A multimeter is required to read the data!")));
            return;
        }

        String p = prefix();
        Location anchor = reactor.getAnchor();

        player.sendMessage(MessageUtil.parse(p + msg("meter_header",
                        "<gold>=== %prefix% ===</gold>")
                .replace("%prefix%", prefixWord())));

        player.sendMessage(MessageUtil.parse(p + msg("meter_position",
                        "<aqua>Position: </aqua><white>%coords%</white>")
                .replace("%coords%", coords(anchor))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_status",
                        "<aqua>Status: </aqua>%status%")
                .replace("%status%", statusLabel(reactor))));

        Block center = reactor.getCenterBlock();
        Block barrel = reactor.getBarrelBlock();
        boolean powered = center.isBlockPowered()
                || center.isBlockIndirectlyPowered()
                || barrel.isBlockPowered()
                || barrel.isBlockIndirectlyPowered();
        player.sendMessage(MessageUtil.parse(p + msg("meter_power",
                        "<aqua>Redstone: </aqua>%power%")
                .replace("%power%", powered
                        ? msg("power_on", "<green>✔ On</green>")
                        : msg("power_off", "<red>✘ Off</red>"))));

        Material fuel = fuelMaterial();
        player.sendMessage(MessageUtil.parse(p + msg("meter_fuel",
                        "<aqua>Fuel: </aqua><white>%fuel%</white> <gray>%item%</gray>")
                .replace("%fuel%", String.valueOf(reactor.countItem(fuel)))
                .replace("%item%", materialName(fuel))));

        int burnSeconds = (int) Math.ceil(reactor.getFuelTicksLeft() / 20.0);
        player.sendMessage(MessageUtil.parse(p + msg("meter_burn",
                        "<aqua>Current fuel: </aqua><white>%sec%s left</white>")
                .replace("%sec%", String.valueOf(burnSeconds))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_temp",
                        "<aqua>Temperature: </aqua><white>%temp% / %max% C*</white>")
                .replace("%temp%", String.valueOf(reactor.getTemperature()))
                .replace("%max%", String.valueOf(maxTemp()))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_cooling",
                        "<aqua>Cooling: </aqua><white>%cool%%</white> <dark_gray>(ice: %ice%)</dark_gray>")
                .replace("%cool%", String.valueOf(reactor.getCooling()))
                .replace("%ice%", String.valueOf(countCoolingBlocks(reactor)))));

        boolean hasCable = findPortNode(reactor.getEnergyPortLocation()) != null;
        player.sendMessage(MessageUtil.parse(p + msg("meter_cable",
                        "<aqua>Cable: </aqua>%state%")
                .replace("%state%", hasCable
                        ? msg("cable_connected", "<green>✔ Connected</green>")
                        : msg("cable_missing", "<red>✘ Not connected</red>"))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_output",
                        "<aqua>Output: </aqua><white>%energy% ⚡/s</white>")
                .replace("%energy%", String.valueOf(energyPerSecond()))));

        // The output buffer is where the energy the network cannot take waits
        // for a direct consumer — the same "Buffer: n/max" line as the coal
        // generator's readout.
        CableNode output = ensureOutputNode(reactor);
        player.sendMessage(MessageUtil.parse(p + msg("meter_buffer",
                        "<aqua>Buffer: </aqua><white>%energy%/%max% ⚡</white>")
                .replace("%energy%", String.valueOf(output != null ? output.getEnergy() : 0))
                .replace("%max%", String.valueOf(output != null ? output.getMaxEnergy() : 0))));

        player.sendMessage(MessageUtil.parse(p + msg("meter_structure",
                        "<aqua>Structure: </aqua>%state%")
                .replace("%state%", reactor.getStatus() == NuclearReactor.Status.DAMAGED
                                || reactor.getStatus() == NuclearReactor.Status.MELTDOWN
                        ? msg("structure_damaged", "<dark_red>❌ Damaged</dark_red>")
                        : msg("structure_ok", "<green>✔ Intact</green>"))));

        if (reactor.isMeltdown()) {
            player.sendMessage(MessageUtil.parse(p + msg("meter_meltdown",
                            "<dark_red>❌ Detonation in </dark_red><white>%sec%s</white>")
                    .replace("%sec%", String.valueOf(reactor.getMeltdownSeconds()))));
        }

        player.sendMessage(MessageUtil.parse(p + msg("meter_hint",
                "<dark_gray>Emeralds and ice go into the barrel, a lever on top of the barrel, cable under the bottom port</dark_gray>")));

        // Tell the shared API that this click got its parameters, so the
        // generic MultimeterListener does not append its "can't measure" error.
        MultimeterApi.markMeasured(player);
    }

    /** {@code [CER]} chat prefix — the word is configurable. */
    private static String prefix() {
        return "<dark_gray>[<yellow>" + prefixWord() + "<dark_gray>] ";
    }

    // Changed the prefix word from "CER" to "Compact Energy Reactor" for a better understanding.
    private static String prefixWord() {
        return msg("prefix", "Compact Energy Reactor");
    }

    /** How many cooling blocks (any ice type) the barrel currently holds. */
    private static int countCoolingBlocks(NuclearReactor reactor) {
        int total = 0;
        for (Material material : COOLING_BLOCKS) {
            total += reactor.countItem(material);
        }
        return total;
    }

    // Also changed the red cross to "❌" because that's better.
    private static String statusLabel(NuclearReactor reactor) {
        NuclearReactor.Status status = reactor.getStatus();
        if (status == null) return "?";
        return switch (status) {
            case RUNNING -> msg("status_running", "<green>✔ Running");
            case UNPOWERED -> msg("status_unpowered", "<red>❌ No redstone signal on the center block");
            case NO_FUEL -> msg("status_no_fuel", "<yellow>⚠ No emeralds in the barrel");
            case NO_CABLE -> msg("status_no_cable", "<yellow>⚠ No cable under the bottom port");
            case OUTPUT_BLOCKED -> msg("status_output_blocked",
                    "<yellow>⚠ The network and the output buffer are full — the reactor is switched off");
            case DAMAGED -> msg("status_damaged", "<dark_red>❌ Structure damaged");
            case MELTDOWN -> msg("status_meltdown",
                            "<dark_red>❌ MELTDOWN — detonation in %sec%s</dark_red>")
                    .replace("%sec%", String.valueOf(reactor.getMeltdownSeconds()));
        };
    }

    private static String materialName(Material material) {
        if (material == null) return "?";
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // =========================
    // CONFIG HELPERS
    // =========================

    private static int cfgInt(String key, int def) {
        Main main = Main.getInstance();
        if (main == null) return def;
        return main.getConfig().getInt("nuclear_reactor." + key, def);
    }

    private static double cfgDouble(String key, double def) {
        Main main = Main.getInstance();
        if (main == null) return def;
        return main.getConfig().getDouble("nuclear_reactor." + key, def);
    }

    /** Configured fuel item ({@code nuclear_reactor.fuel_item}, EMERALD fallback). */
    public static Material fuelMaterial() {
        Main main = Main.getInstance();
        if (main == null) return Material.EMERALD;

        String raw = main.getConfig().getString("nuclear_reactor.fuel_item", "EMERALD");
        Material material = raw == null ? null : Material.matchMaterial(raw.trim());
        if (material == null || material.isAir() || !material.isItem()) {
            return Material.EMERALD;
        }
        return material;
    }

    /** Configured output rate in energy per second. */
    public static int energyPerSecond() {
        return Math.max(0, cfgInt("energy_per_second", 100));
    }

    /** Working temperature — the point where the reactor starts burning coolant. */
    public static int workTemp() {
        return Math.max(1, cfgInt("work_temp", 512));
    }

    /** Maximum temperature — a meltdown starts here. */
    public static int maxTemp() {
        return Math.max(workTemp(), cfgInt("max_temp", 1024));
    }

    /** Full cooling buffer in percent. */
    public static int coolingMax() {
        return Math.max(1, cfgInt("cooling_max", 100));
    }

    /**
     * Cooling percent one block of the given ice type adds. Anything else
     * (including {@code null}) adds nothing.
     */
    public static int coolingValue(Material material) {
        if (material == null) return 0;
        return switch (material) {
            case ICE -> Math.max(0, cfgInt("cooling_ice", 1));
            case PACKED_ICE -> Math.max(0, cfgInt("cooling_packed_ice", 2));
            case BLUE_ICE -> Math.max(0, cfgInt("cooling_blue_ice", 3));
            default -> 0;
        };
    }

    /** Cooling blocks accepted by the barrel, cheapest first. */
    public static Material[] coolingBlocks() {
        return COOLING_BLOCKS.clone();
    }

    /** Cooling blocks the barrel is allowed to convert per second. */
    public static int coolingBlocksPerSecond() {
        return Math.max(0, cfgInt("cooling_blocks_per_second", 1));
    }

    /** Seconds one cooling percent lasts at the working temperature. */
    public static int coolingDrainSeconds() {
        return Math.max(1, cfgInt("cooling_drain_seconds", 8));
    }

    /** Passive cooling in degrees per second (before the humidity multiplier). */
    public static double passiveCoolRate() {
        Main main = Main.getInstance();
        if (main == null) return 1.0;
        return Math.max(0.0, main.getConfig().getDouble("nuclear_reactor.passive_cool_rate", 1.0));
    }

    /** Heating rate in degrees per second while the reactor runs. */
    public static int heatRate() {
        return Math.max(0, cfgInt("heat_rate", 20));
    }

    // =========================
    // MESSAGE + COORDS HELPERS
    // =========================

    private static String msg(String key, String def) {
        return StructuresMessages.get("nuclear_reactor." + key, def);
    }

    private static String coords(Location loc) {
        if (loc == null) return "? ? ?";
        return loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }
}

// Review: OK
