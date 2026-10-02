package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.database.PlayerSettingsDB;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Observer;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wireless redstone: links redstone devices together (one-to-many).
 * <p>
 * One block can be linked to SEVERAL others. When it activates,
 * ALL linked devices activate. The link is bidirectional: if A↔B,
 * activating B also activates A (with loop protection).
 * <p>
 * Supported blocks: REDSTONE_LAMP, OBSERVER, PISTON/STICKY_PISTON,
 * DISPENSER/DROPPER, REDSTONE_WIRE — every wirelessly activated device
 * performs its real vanilla action: the dispenser/dropper dispenses via the
 * block-state API, the piston extends/retracts through a real power block
 * (vanilla movement, no ghost heads), the observer pulses its output for
 * real, and the lamp additionally emits signal (see below).
 * <p>
 * A wirelessly activated lamp is also a usable SIGNAL SOURCE: the lit
 * property is applied with applyPhysics=false (otherwise the server would
 * immediately turn it back off — the lamp has no real redstone input), and
 * vanilla lamps never emit power anyway. So {@link #setLampLit} additionally
 * powers adjacent redstone dust (a real current the rest of the vanilla
 * redstone can read) and pulses adjacent observers that watch the lamp.
 */
public class WirelessRedstoneManager implements Listener {

    private static WirelessRedstoneManager instance;
    private static boolean enabled = true;

    /** Watcher task — stored so it can be cancelled properly on shutdown/reload. */
    private org.bukkit.scheduler.BukkitTask observerTask;

    private final Map<UUID, BlockPos> bindingPlayers = new ConcurrentHashMap<>();

    /** One-to-many: BlockPos → Set<BlockPos> (bidirectional links) */
    private final Map<BlockPos, Set<BlockPos>> links = new ConcurrentHashMap<>();

    private final Map<BlockPos, Integer> skipUntilTick = new ConcurrentHashMap<>();
    private final Map<BlockPos, Boolean> observerPrevPowered = new ConcurrentHashMap<>();
    private final Map<BlockPos, RestoreData> pistonPowerBlocks = new ConcurrentHashMap<>();

    private record RestoreData(BlockPos powerBlockPos, Material originalType) {}
    private record MoveEntry(BlockPos oldPos, BlockPos newPos) {}

    private static final Set<Material> LINKABLE_MATERIALS = Collections.unmodifiableSet(EnumSet.of(
            Material.REDSTONE_LAMP, Material.OBSERVER,
            Material.PISTON, Material.STICKY_PISTON,
            Material.DISPENSER, Material.DROPPER,
            Material.REDSTONE_WIRE
    ));

    /** The six neighbour faces, for signal emission and comparator updates. */
    private static final List<BlockFace> SIX_FACES = List.of(
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST);

    /** Observers currently pulsed by {@link #pulseObserver} (pulse guard). */
    private final Set<BlockPos> pulsingObservers = ConcurrentHashMap.newKeySet();

    private WirelessRedstoneManager() {}

    // ════════════════════════════════════════
    // RECORD
    // ════════════════════════════════════════
    public record BlockPos(String worldUid, int x, int y, int z) {
        public static BlockPos fromLocation(Location loc) {
            return new BlockPos(loc.getWorld().getUID().toString(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
        public Location toLocation() {
            World w = Bukkit.getWorld(UUID.fromString(worldUid));
            return w != null ? new Location(w, x, y, z) : null;
        }
    }

    // ════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════
    public static void init(Main plugin) {
        // /ui reload: the old instance could have survived onDisable (the module didn't call
        // shutdown), and its task is cancelled by the global cancelTasks(). Without this
        // guard, init() would return early and the watcher would never restart.
        if (instance != null) {
            shutdown();
        }
        instance = new WirelessRedstoneManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        instance.loadFromDatabase();
        instance.startObserverTask();
        ConsoleLogger.info("[WirelessRedstone] Initialized with " + countLinks() + " active links");
    }

    /**
     * Stops the watcher and resets the singleton. Called when the module is
     * disabled and before a repeated init (e.g. /ui reload).
     */
    public static void shutdown() {
        if (instance == null) return;
        if (instance.observerTask != null) {
            instance.observerTask.cancel();
            instance.observerTask = null;
        }
        instance.observerPrevPowered.clear();
        instance = null;
    }

    private static int countLinks() {
        if (instance == null) return 0;
        int total = 0;
        for (Set<BlockPos> set : instance.links.values()) {
            total += set.size();
        }
        return total / 2; // each link stored twice (A→B and B→A)
    }

    // ════════════════════════════════════════
    // LINK HELPERS
    // ════════════════════════════════════════
    private Set<BlockPos> getPartners(BlockPos pos) {
        return links.getOrDefault(pos, Collections.emptySet());
    }

    /** Add a bidirectional link A↔B */
    private void addLink(BlockPos a, BlockPos b) {
        links.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        links.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
    }

    /** Remove a bidirectional link A↔B (memory only) */
    private void removeLink(BlockPos a, BlockPos b) {
        Set<BlockPos> setA = links.get(a);
        if (setA != null) {
            setA.remove(b);
            if (setA.isEmpty()) links.remove(a);
        }
        Set<BlockPos> setB = links.get(b);
        if (setB != null) {
            setB.remove(a);
            if (setB.isEmpty()) links.remove(b);
        }
    }

    /** Break ALL links of a block */
    private void breakAllLinks(BlockPos pos) {
        Set<BlockPos> partners = links.remove(pos);
        if (partners == null || partners.isEmpty()) return;

        for (BlockPos partner : partners) {
            Set<BlockPos> partnerSet = links.get(partner);
            if (partnerSet != null) {
                partnerSet.remove(pos);
                if (partnerSet.isEmpty()) links.remove(partner);
            }
            removeChunkTicket(partner);
            removeLinkDb(pos, partner);
        }
        removeChunkTicket(pos);
        observerPrevPowered.remove(pos);
        skipUntilTick.remove(pos);

        // Piston power blocks cleanup
        RestoreData rd = pistonPowerBlocks.remove(pos);
        if (rd != null) {
            Location ploc = rd.powerBlockPos().toLocation();
            if (ploc != null && ploc.getBlock().getType() == Material.REDSTONE_BLOCK) {
                ploc.getBlock().setType(rd.originalType(), false);
            }
        }
    }

    // ════════════════════════════════════════
    // OBSERVER TASK
    // ════════════════════════════════════════
    private void startObserverTask() {
        observerTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<BlockPos, Set<BlockPos>> entry : links.entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (isSkipping(pos)) continue;
                    Location loc = pos.toLocation();
                    if (loc == null) continue;
                    Block block = loc.getBlock();
                    if (block.getType() != Material.OBSERVER) continue;
                    if (!(block.getBlockData() instanceof Powerable powerable)) continue;
                    boolean current = powerable.isPowered();
                    Boolean previous = observerPrevPowered.get(pos);
                    if (previous != null && previous != current) {
                        activateAllPartners(pos, current);
                    }
                    observerPrevPowered.put(pos, current);
                }
            }
        }.runTaskTimer(Main.getInstance(), 0L, 2L);
    }

    // ════════════════════════════════════════
    // DATABASE
    // ════════════════════════════════════════
    private void loadFromDatabase() {
        links.clear();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "SELECT world, x1, y1, z1, x2, y2, z2 FROM wireless_links");
             ResultSet rs = st.executeQuery()) {

            while (rs.next()) {
                String w = rs.getString("world");
                BlockPos a = new BlockPos(w, rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"));
                BlockPos b = new BlockPos(w, rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2"));
                addLink(a, b);
                addChunkTicket(a);
                addChunkTicket(b);
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[WirelessRedstone] Failed to load from DB: " + e.getMessage());
        }
    }

    private void saveLinkDb(BlockPos a, BlockPos b) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "INSERT OR IGNORE INTO wireless_links (world, x1, y1, z1, x2, y2, z2) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            st.setString(1, a.worldUid());
            st.setInt(2, a.x()); st.setInt(3, a.y()); st.setInt(4, a.z());
            st.setInt(5, b.x()); st.setInt(6, b.y()); st.setInt(7, b.z());
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[WirelessRedstone] Failed to save link: " + e.getMessage());
        }
    }

    private void removeLinkDb(BlockPos a, BlockPos b) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(
                     "DELETE FROM wireless_links WHERE world = ? AND x1 = ? AND y1 = ? AND z1 = ? AND x2 = ? AND y2 = ? AND z2 = ?")) {
            String w = a.worldUid();
            for (BlockPos[] pair : new BlockPos[][]{{a, b}, {b, a}}) {
                st.setString(1, w);
                st.setInt(2, pair[0].x()); st.setInt(3, pair[0].y()); st.setInt(4, pair[0].z());
                st.setInt(5, pair[1].x()); st.setInt(6, pair[1].y()); st.setInt(7, pair[1].z());
                st.executeUpdate();
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[WirelessRedstone] Failed to remove link: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════
    // CHUNK TICKETS
    // ════════════════════════════════════════
    private void addChunkTicket(BlockPos pos) {
        World world = Bukkit.getWorld(UUID.fromString(pos.worldUid()));
        if (world == null) return;
        world.addPluginChunkTicket(pos.x() >> 4, pos.z() >> 4, Main.getInstance());
    }

    private void removeChunkTicket(BlockPos pos) {
        World world = Bukkit.getWorld(UUID.fromString(pos.worldUid()));
        if (world == null) return;
        world.removePluginChunkTicket(pos.x() >> 4, pos.z() >> 4, Main.getInstance());
    }

    // ════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════
    private static boolean isLinkable(Block block) {
        return LINKABLE_MATERIALS.contains(block.getType());
    }

    private static String blockName(Block block) {
        return switch (block.getType()) {
            case REDSTONE_LAMP -> "Redstone Lamp";
            case OBSERVER -> "Observer";
            case PISTON -> "Piston";
            case STICKY_PISTON -> "Sticky Piston";
            case DISPENSER -> "Dispenser";
            case DROPPER -> "Dropper";
            case REDSTONE_WIRE -> "Redstone Wire";
            default -> "Block";
        };
    }

    private boolean isSkipping(BlockPos pos) {
        Integer until = skipUntilTick.get(pos);
        return until != null && Bukkit.getCurrentTick() < until;
    }

    private void markSkipping(BlockPos pos) {
        skipUntilTick.put(pos, Bukkit.getCurrentTick() + 2);
    }

    // ════════════════════════════════════════
    // ACTIVATE ALL PARTNERS — activate ALL linked devices
    // ════════════════════════════════════════
    private void activateAllPartners(BlockPos sourcePos, boolean powered) {
        Set<BlockPos> partners = getPartners(sourcePos);
        if (partners.isEmpty()) return;

        for (BlockPos partnerPos : partners) {
            if (isSkipping(partnerPos)) continue;

            Location loc = partnerPos.toLocation();
            if (loc == null) continue;
            Block block = loc.getBlock();
            if (!isLinkable(block)) {
                removeLink(sourcePos, partnerPos);
                removeChunkTicket(partnerPos);
                removeLinkDb(sourcePos, partnerPos);
                continue;
            }

            markSkipping(partnerPos);
            activateDevice(block, powered);
        }
    }

    private void activateDevice(Block block, boolean powered) {
        switch (block.getType()) {
            case REDSTONE_LAMP -> setLampLit(block, powered);
            case OBSERVER -> {
                if (powered) pulseObserverNow(block);
            }
            case PISTON, STICKY_PISTON -> setPistonExtended(block, powered);
            case DISPENSER, DROPPER -> {
                if (powered) triggerDispenser(block);
            }
            case REDSTONE_WIRE -> setWirePowered(block, powered);
        }
    }

    private void setLampLit(Block block, boolean lit) {
        if (!(block.getBlockData() instanceof Lightable lightable)) return;
        if (lightable.isLit() == lit) return;

        lightable.setLit(lit);
        // ⚠ applyPhysics=false — critical! The lamp has no real redstone signal
        // (wireless power), so applyPhysics=true would make the server re-check
        // the signal and turn the lamp back off.
        block.setBlockData(lightable, false);

        // Update comparators and repeaters manually
        forceComparatorUpdate(block);

        // Make the lamp usable as a signal source: adjacent dust gets a real
        // current and adjacent observers get a real pulse — otherwise the
        // lamp would only glow with nothing reacting to it.
        emitWirelessPower(block, lit);

        Location loc = block.getLocation().clone();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            Block b = loc.getBlock();
            if (b.getType() != Material.REDSTONE_LAMP) return;
            if (b.getBlockData() instanceof Lightable l) {
                if (l.isLit() == lit) return;
                l.setLit(lit);
                b.setBlockData(l, false);
            }
            forceComparatorUpdate(b);
        });

        // Cascade to partners
        BlockPos pos = BlockPos.fromLocation(block.getLocation());
        activateAllPartners(pos, lit);
    }

    /**
     * Signal emission of a wirelessly changed lamp (see the class javadoc):
     * <ul>
     *   <li>adjacent REDSTONE_WIRE is driven to 15/0 — a real current the
     *       rest of the vanilla redstone (repeaters, comparators, blocks)
     *       can read and propagate;</li>
     *   <li>adjacent OBSERVERS that watch the lamp get a real 2-tick pulse
     *       so they register the state change.</li>
     * </ul>
     * Turning off drives adjacent dust to 0 even when another source powers
     * it — the physics update makes the wire recompute and restore its real
     * value, so foreign signals are only interrupted for a moment.
     */
    private void emitWirelessPower(Block block, boolean powered) {
        for (BlockFace face : SIX_FACES) {
            Block adj = block.getRelative(face);
            switch (adj.getType()) {
                case REDSTONE_WIRE -> setWirePowered(adj, powered);
                case OBSERVER -> {
                    if (powered && !isSkipping(BlockPos.fromLocation(adj.getLocation()))) {
                        pulseObserver(adj, block);
                    }
                }
                default -> { }
            }
        }
    }

    /**
     * Pulses an observer as if it had detected a block change — but only when
     * the observer actually watches the given block: its eyes side (facing)
     * or its output side points at it. Delegates to {@link #pulseObserverNow}.
     */
    private void pulseObserver(Block observerBlock, Block watched) {
        if (!(observerBlock.getBlockData() instanceof Observer obs)) return;
        BlockFace facing = obs.getFacing();
        if (!observerBlock.getRelative(facing).equals(watched)
                && !observerBlock.getRelative(facing.getOppositeFace()).equals(watched)) {
            return;
        }
        pulseObserverNow(observerBlock);
    }

    /**
     * Updates the state of all comparators and repeaters
     * within a 1-block radius of the given block.
     */
    private static void forceComparatorUpdate(Block block) {
        for (BlockFace face : SIX_FACES) {
            Block adj = block.getRelative(face);
            Material type = adj.getType();
            if (type == Material.COMPARATOR || type == Material.REPEATER) {
                // Re-apply the block data with physics — the comparator recalculates the signal
                adj.getState().update(true);
            }
        }
    }

    /**
     * Drives an observer's powered property directly for the vanilla 2-tick
     * pulse length (physics=true, so the output side really powers and third
     * observers watching this one detect the state change). Replaces the old
     * stone-flicker trick: no world mutation and exactly one pulse instead of
     * two (place + remove of the probe block). The manager's watcher task
     * sees the powered flip and propagates it to the observer's own wireless
     * partners as usual.
     */
    private void pulseObserverNow(Block observerBlock) {
        if (!(observerBlock.getBlockData() instanceof Observer obs)) return;
        BlockPos oPos = BlockPos.fromLocation(observerBlock.getLocation());
        if (!pulsingObservers.add(oPos)) return; // a pulse is already running

        obs.setPowered(true);
        observerBlock.setBlockData(obs, true);
        Location oloc = observerBlock.getLocation().clone();
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            pulsingObservers.remove(oPos);
            Block b = oloc.getBlock();
            if (b.getType() == Material.OBSERVER && b.getBlockData() instanceof Observer o && o.isPowered()) {
                o.setPowered(false);
                b.setBlockData(o, true);
            }
        }, 2L);
    }

    private void setPistonExtended(Block block, boolean extended) {
        if (!(block.getBlockData() instanceof Piston piston)) return;
        BlockPos pistonPos = BlockPos.fromLocation(block.getLocation());

        if (extended) {
            if (pistonPowerBlocks.containsKey(pistonPos)) return; // already powered by us
            // Power the piston for real: place a redstone block on an adjacent
            // air block (never on the face it pushes towards), so the vanilla
            // extension — pushing the blocks in front — runs on its own.
            Location powerLoc = findPowerSpot(block, piston);
            Material prevType = powerLoc.getBlock().getType();
            powerLoc.getBlock().setType(Material.REDSTONE_BLOCK, true);
            pistonPowerBlocks.put(pistonPos, new RestoreData(BlockPos.fromLocation(powerLoc), prevType));
        } else {
            RestoreData data = pistonPowerBlocks.remove(pistonPos);
            if (data == null) return; // not powered by us — real redstone holds it
            Location powerLoc = data.powerBlockPos().toLocation();
            if (powerLoc != null && powerLoc.getBlock().getType() == Material.REDSTONE_BLOCK) {
                // Removing the power block with physics lets the vanilla piston
                // retract (a sticky piston pulls its block back). The extended
                // property must NOT be set manually — that leaves a ghost
                // piston head in the world.
                powerLoc.getBlock().setType(data.originalType(), true);
            }
        }
    }

    /** Adjacent air block to place a redstone block on, excluding the piston's pushing face. */
    private static Location findPowerSpot(Block piston, Piston data) {
        BlockFace front = data.getFacing();
        for (BlockFace face : SIX_FACES) {
            if (face == front) continue;
            Block side = piston.getRelative(face);
            if (side.getType().isAir()) {
                return side.getLocation();
            }
        }
        // Legacy fallback: overwrite the block behind the piston (restored on retract).
        return piston.getRelative(front.getOppositeFace()).getLocation();
    }

    /**
     * Performs a REAL dispense via the block-state API — setting the
     * triggered property alone never fired the vanilla dispense behavior, so
     * a wirelessly triggered dispenser/dropper only "clicked" without shooting.
     * The dispense fires BlockDispenseEvent, so the existing listener sees it
     * exactly like a vanilla dispense (skipped here — this device was just
     * marked as skipping by the activation).
     */
    private static void triggerDispenser(Block block) {
        var state = block.getState();
        if (state instanceof org.bukkit.block.Dispenser dispenser) {
            dispenser.dispense();
        } else if (state instanceof org.bukkit.block.Dropper dropper) {
            dispenseDropper(dropper);
        }
    }

    /**
     * Dropper has no {@code dispense()} in the Bukkit API — simulate the
     * vanilla behavior: eject the first available stack toward the block's
     * facing. Fires BlockDispenseEvent first, so protection plugins can
     * cancel it just like a vanilla dispense.
     */
    private static void dispenseDropper(org.bukkit.block.Dropper dropper) {
        org.bukkit.inventory.Inventory inv = dropper.getInventory();
        int slot = -1;
        org.bukkit.inventory.ItemStack stack = null;
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                slot = i;
                stack = item;
                break;
            }
        }
        if (stack == null) return; // nothing to dispense

        Block block = dropper.getBlock();
        // Dropper shares the Dispenser block data type (Directional + triggered).
        BlockFace face = ((org.bukkit.block.data.type.Dispenser) block.getBlockData()).getFacing();
        Vector velocity = new Vector(face.getModX(), face.getModY(), face.getModZ()).multiply(0.3);

        BlockDispenseEvent event = new BlockDispenseEvent(block, stack.clone(), velocity);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            inv.setItem(slot, null);
        }
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        Location spawnAt = center.add(face.getModX() * 0.7D, face.getModY() * 0.7D, face.getModZ() * 0.7D);
        org.bukkit.entity.Item dropped = block.getWorld().dropItem(spawnAt, event.getItem());
        dropped.setVelocity(event.getVelocity());
    }

    private void setWirePowered(Block block, boolean powered) {
        if (!(block.getBlockData() instanceof RedstoneWire wire)) return;
        int target = powered ? 15 : 0;
        if (wire.getPower() == target) return;
        wire.setPower(target);
        block.setBlockData(wire, true);
        // Cascade: activate the partners of this dust
        BlockPos pos = BlockPos.fromLocation(block.getLocation());
        activateAllPartners(pos, powered);
    }

    // ════════════════════════════════════════
    // MOVE LINKED BLOCKS — piston moves blocks with links
    // ════════════════════════════════════════
    private void moveLinkedBlocks(List<Block> blocks, BlockFace direction) {
        // First pass: oldPos → newPos for all moved blocks
        Map<BlockPos, BlockPos> movedPositions = new HashMap<>();
        for (Block b : blocks) {
            BlockPos oldP = BlockPos.fromLocation(b.getLocation());
            BlockPos newP = new BlockPos(oldP.worldUid(),
                    oldP.x() + direction.getModX(),
                    oldP.y() + direction.getModY(),
                    oldP.z() + direction.getModZ());
            movedPositions.put(oldP, newP);
        }

        // Second pass: for each moved block with links — update all its links
        Set<BlockPos> processed = new HashSet<>();
        for (Block b : blocks) {
            BlockPos oldPos = BlockPos.fromLocation(b.getLocation());
            if (!processed.add(oldPos)) continue;

            Set<BlockPos> partners = getPartners(oldPos);
            if (partners.isEmpty()) continue;

            BlockPos newPos = movedPositions.get(oldPos);
            if (newPos == null) continue;

            // Collect all partners (some may also be moving)
            List<BlockPos> allPartners = new ArrayList<>(partners);

            // Remove the old links (all at once)
            removeChunkTicket(oldPos);
            for (BlockPos p : allPartners) {
                removeLink(oldPos, p);
                removeLinkDb(oldPos, p);
            }
            observerPrevPowered.remove(oldPos);
            skipUntilTick.remove(oldPos);

            // Create new ones at the new positions
            for (BlockPos p : allPartners) {
                BlockPos actualPartner = movedPositions.getOrDefault(p, p);
                addLink(newPos, actualPartner);
                saveLinkDb(newPos, actualPartner);
            }
            addChunkTicket(newPos);
        }

        // Third pass: chunk tickets for the processed blocks and their partners
        for (BlockPos oldPos : processed) {
            BlockPos newPos = movedPositions.get(oldPos);
            if (newPos == null) continue;
            addChunkTicket(newPos);
            for (BlockPos p : getPartners(newPos)) {
                addChunkTicket(p);
            }
        }
    }

    // ════════════════════════════════════════
    // EVENT: SHIFT+RMB — binding (one-to-many)
    // ════════════════════════════════════════
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        if (!player.isSneaking()) return;
        if (!PlayerSettingsDB.isWirelessBindEnabled(player.getUniqueId())) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        if (!isLinkable(clicked)) return;

        UUID uuid = player.getUniqueId();
        BlockPos clickedPos = BlockPos.fromLocation(clicked.getLocation());
        String name = blockName(clicked);

        BlockPos first = bindingPlayers.get(uuid);
        if (first != null) {
            if (first.equals(clickedPos)) {
                bindingPlayers.remove(uuid);
                player.sendActionBar(MessageUtil.parse("<red>✕ Binding cancelled</red>"));
                e.setCancelled(true);
                return;
            }

            Location firstLoc = first.toLocation();
            if (firstLoc == null || !firstLoc.getWorld().equals(clicked.getWorld())) {
                bindingPlayers.remove(uuid);
                player.sendActionBar(MessageUtil.parse("<red>✕ Devices must be in the same world!</red>"));
                e.setCancelled(true);
                return;
            }

            // Add the link to existing ones (one-to-many)
            BlockPos second = clickedPos;
            addLink(first, second);
            addChunkTicket(first);
            addChunkTicket(second);
            saveLinkDb(first, second);

            int count = getPartners(first).size();
            player.sendActionBar(MessageUtil.parse("<green>✓ " + blockName(firstLoc.getBlock()) + " ↔ " + name + " linked! (" + count + " connection" + (count > 1 ? "s" : "") + ")</green>"));
            e.setCancelled(true);
            return;
        }

        // New binding or view existing ones
        Set<BlockPos> existing = getPartners(clickedPos);
        if (!existing.isEmpty()) {
            int count = existing.size();
            // Show the first partner as an example
            BlockPos firstPartner = existing.iterator().next();
            Location ploc = firstPartner.toLocation();
            String firstInfo = "";
            if (ploc != null) {
                firstInfo = " e.g. " + blockName(ploc.getBlock())
                        + " at [" + ploc.getBlockX() + " " + ploc.getBlockY() + " " + ploc.getBlockZ() + "]";
            }
            player.sendActionBar(MessageUtil.parse("<gold>⚡ " + name + " has " + count + " connection" + (count > 1 ? "s" : "") + firstInfo + ". Shift+RMB another device to add.</gold>"));
            bindingPlayers.put(uuid, clickedPos);
            e.setCancelled(true);
            return;
        }

        bindingPlayers.put(uuid, clickedPos);
        player.sendActionBar(MessageUtil.parse("<aqua>🔗 Binding: Shift+RMB any redstone device to link, or same block to cancel.</aqua>"));
        e.setCancelled(true);
    }

    // ════════════════════════════════════════
    // EVENT: BLOCK REDSTONE
    // ════════════════════════════════════════
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockRedstone(BlockRedstoneEvent e) {
        Block block = e.getBlock();
        Material type = block.getType();
        if (type != Material.REDSTONE_LAMP && type != Material.REDSTONE_WIRE) return;
        BlockPos pos = BlockPos.fromLocation(block.getLocation());
        if (isSkipping(pos)) return;
        boolean nowPowered = e.getNewCurrent() > 0;
        boolean wasPowered = e.getOldCurrent() > 0;
        if (nowPowered == wasPowered) return;
        activateAllPartners(pos, nowPowered);
    }

    // ════════════════════════════════════════
    // EVENT: PISTON
    // ════════════════════════════════════════
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Block block = e.getBlock();
        BlockPos pos = BlockPos.fromLocation(block.getLocation());
        moveLinkedBlocks(e.getBlocks(), e.getDirection());
        if (isSkipping(pos)) return;
        activateAllPartners(pos, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Block block = e.getBlock();
        BlockPos pos = BlockPos.fromLocation(block.getLocation());
        if (e.isSticky()) moveLinkedBlocks(e.getBlocks(), e.getDirection());
        if (isSkipping(pos)) return;
        activateAllPartners(pos, false);
    }

    // ════════════════════════════════════════
    // EVENT: DISPENSER
    // ════════════════════════════════════════
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        Block block = e.getBlock();
        Material type = block.getType();
        if (type != Material.DISPENSER && type != Material.DROPPER) return;
        BlockPos pos = BlockPos.fromLocation(block.getLocation());
        if (isSkipping(pos)) return;
        activateAllPartners(pos, true);
    }

    // ════════════════════════════════════════
    // EVENT: BLOCK BREAK — break ALL links
    // ════════════════════════════════════════
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!isLinkable(block)) return;
        BlockPos brokenPos = BlockPos.fromLocation(block.getLocation());
        Set<BlockPos> partners = getPartners(brokenPos);
        if (partners.isEmpty()) return;

        Player player = e.getPlayer();
        int count = partners.size();
        player.sendActionBar(MessageUtil.parse("<red>☠ " + blockName(block) + " broken — " + count + " wireless connection" + (count > 1 ? "s" : "") + " removed!</red>"));
        breakAllLinks(brokenPos);
    }

    // ════════════════════════════════════════
    // EVENT: PLAYER QUIT
    // ════════════════════════════════════════
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        bindingPlayers.remove(e.getPlayer().getUniqueId());
    }

    // ════════════════════════════════════════
    // RELOAD
    // ════════════════════════════════════════
    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("wireless_redstone");
        enabled = cfg == null || cfg.getBoolean("enabled", true);
    }

    public static boolean isEnabled() { return enabled; }
    public static WirelessRedstoneManager getInstance() { return instance; }

    public static void restoreAllPowerBlocks() {
        if (instance == null) return;
        // Pistons
        for (Map.Entry<BlockPos, RestoreData> entry : instance.pistonPowerBlocks.entrySet()) {
            Location loc = entry.getValue().powerBlockPos().toLocation();
            if (loc != null && loc.getBlock().getType() == Material.REDSTONE_BLOCK) {
                loc.getBlock().setType(entry.getValue().originalType(), false);
            }
        }
        instance.pistonPowerBlocks.clear();
    }
}
