package com.mcplugin.mechanics.features.world;

import com.mcplugin.core.Main;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.database.PlayerSettingsDB;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Dispenser;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Беспроводной редстоун: связывает редстоун-устройства (one-to-many).
 * <p>
 * Один блок может быть связан с НЕСКОЛЬКИМИ другими. Когда он активируется,
 * ВСЕ связанные устройства активируются. Связь двусторонняя: если A↔B,
 * то активация B также активирует A (с loop protection).
 * <p>
 * Поддерживаемые блоки: REDSTONE_LAMP, OBSERVER, PISTON/STICKY_PISTON,
 * DISPENSER/DROPPER, REDSTONE_WIRE.
 */
public class WirelessRedstoneManager implements Listener {

    private static WirelessRedstoneManager instance;
    private static boolean enabled = true;

    private final Map<UUID, BlockPos> bindingPlayers = new ConcurrentHashMap<>();

    /** One-to-many: BlockPos → Set<BlockPos> (двусторонние связи) */
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
        if (instance != null) return;
        instance = new WirelessRedstoneManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        instance.loadFromDatabase();
        instance.startObserverTask();
        ConsoleLogger.info("[WirelessRedstone] Initialized with " + countLinks() + " active links");
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

    /** Добавить двустороннюю связь A↔B */
    private void addLink(BlockPos a, BlockPos b) {
        links.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        links.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
    }

    /** Удалить двустороннюю связь A↔B (только из памяти) */
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

    /** Разорвать ВСЕ связи блока */
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
        new org.bukkit.scheduler.BukkitRunnable() {
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
    // ACTIVATE ALL PARTNERS — активировать ВСЕ связанные устройства
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
            case OBSERVER -> triggerObserver(block, powered);
            case PISTON, STICKY_PISTON -> setPistonExtended(block, powered);
            case DISPENSER, DROPPER -> triggerDispenser(block, powered);
            case REDSTONE_WIRE -> setWirePowered(block, powered);
        }
    }

    private void setLampLit(Block block, boolean lit) {
        if (!(block.getBlockData() instanceof Lightable lightable)) return;
        if (lightable.isLit() == lit) return;

        lightable.setLit(lit);
        // ⚠ applyPhysics=false — критично! Лампа не имеет реального редстоун-сигнала
        // (беспроводное питание), поэтому applyPhysics=true заставит сервер перепроверить
        // сигнал и погасить лампу обратно.
        block.setBlockData(lightable, false);

        // Обновляем компараторы и повторители вручную
        forceComparatorUpdate(block);

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
     * Обновить состояние всех компараторов и повторителей,
     * которые находятся в радиусе 1 блок от указанного блока.
     */
    private static void forceComparatorUpdate(Block block) {
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN,
                BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST)) {
            Block adj = block.getRelative(face);
            Material type = adj.getType();
            if (type == Material.COMPARATOR || type == Material.REPEATER) {
                // Повторно применяем блок-дату с физикой — компаратор пересчитает сигнал
                adj.getState().update(true);
            }
        }
    }

    private void triggerObserver(Block block, boolean activate) {
        if (!(block.getBlockData() instanceof Observer obsData)) return;
        if (!activate) return;
        BlockFace facing = obsData.getFacing();
        Block inFront = block.getRelative(facing);
        Material origType = inFront.getType();
        Location loc = inFront.getLocation().clone();

        if (origType == Material.AIR || origType == Material.CAVE_AIR || origType == Material.VOID_AIR) {
            inFront.setType(Material.STONE, true);
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                Block b = loc.getBlock();
                if (b.getType() == Material.STONE) b.setType(Material.AIR, true);
            }, 2L);
        } else {
            BlockData origData = inFront.getBlockData().clone();
            inFront.setType(Material.STONE, true);
            Material finalOrig = origType;
            BlockData finalData = origData;
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                Block b = loc.getBlock();
                b.setType(finalOrig, false);
                b.setBlockData(finalData, true);
            }, 2L);
        }
    }

    private void setPistonExtended(Block block, boolean extended) {
        if (!(block.getBlockData() instanceof Piston piston)) return;
        if (piston.isExtended() == extended) return;
        BlockPos pistonPos = BlockPos.fromLocation(block.getLocation());

        if (extended) {
            BlockFace facing = piston.getFacing();
            Block behind = block.getRelative(facing.getOppositeFace());
            Material prevType = behind.getType();
            behind.setType(Material.REDSTONE_BLOCK, true);
            pistonPowerBlocks.put(pistonPos, new RestoreData(BlockPos.fromLocation(behind.getLocation()), prevType));
        } else {
            RestoreData data = pistonPowerBlocks.remove(pistonPos);
            if (data != null) {
                Location powerLoc = data.powerBlockPos().toLocation();
                if (powerLoc != null && powerLoc.getBlock().getType() == Material.REDSTONE_BLOCK) {
                    powerLoc.getBlock().setType(data.originalType(), true);
                }
            }
            piston.setExtended(false);
            block.setBlockData(piston, true);
        }
    }

    private void triggerDispenser(Block block, boolean activate) {
        if (!(block.getBlockData() instanceof Dispenser dispenser)) return;
        if (dispenser.isTriggered() == activate) return;
        dispenser.setTriggered(activate);
        block.setBlockData(dispenser, false);
        if (activate) {
            Location loc = block.getLocation().clone();
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                Block b = loc.getBlock();
                Material t = b.getType();
                if ((t == Material.DISPENSER || t == Material.DROPPER) && b.getBlockData() instanceof Dispenser d) {
                    if (d.isTriggered()) { d.setTriggered(false); b.setBlockData(d, false); }
                }
            }, 1L);
        }
    }

    private void setWirePowered(Block block, boolean powered) {
        if (!(block.getBlockData() instanceof RedstoneWire wire)) return;
        int target = powered ? 15 : 0;
        if (wire.getPower() == target) return;
        wire.setPower(target);
        block.setBlockData(wire, true);
        // Cascade: активировать партнёров этой пыли
        BlockPos pos = BlockPos.fromLocation(block.getLocation());
        activateAllPartners(pos, powered);
    }

    // ════════════════════════════════════════
    // MOVE LINKED BLOCKS — поршень двигает блоки со связями
    // ════════════════════════════════════════
    private void moveLinkedBlocks(List<Block> blocks, BlockFace direction) {
        // Первый проход: oldPos → newPos для всех сдвигаемых блоков
        Map<BlockPos, BlockPos> movedPositions = new HashMap<>();
        for (Block b : blocks) {
            BlockPos oldP = BlockPos.fromLocation(b.getLocation());
            BlockPos newP = new BlockPos(oldP.worldUid(),
                    oldP.x() + direction.getModX(),
                    oldP.y() + direction.getModY(),
                    oldP.z() + direction.getModZ());
            movedPositions.put(oldP, newP);
        }

        // Второй проход: для каждого сдвигаемого блока со связями — обновить все связи
        Set<BlockPos> processed = new HashSet<>();
        for (Block b : blocks) {
            BlockPos oldPos = BlockPos.fromLocation(b.getLocation());
            if (!processed.add(oldPos)) continue;

            Set<BlockPos> partners = getPartners(oldPos);
            if (partners.isEmpty()) continue;

            BlockPos newPos = movedPositions.get(oldPos);
            if (newPos == null) continue;

            // Собираем всех партнёров (некоторые могут тоже сдвигаться)
            List<BlockPos> allPartners = new ArrayList<>(partners);

            // Удаляем старые связи (все сразу)
            removeChunkTicket(oldPos);
            for (BlockPos p : allPartners) {
                removeLink(oldPos, p);
                removeLinkDb(oldPos, p);
            }
            observerPrevPowered.remove(oldPos);
            skipUntilTick.remove(oldPos);

            // Создаём новые на новых позициях
            for (BlockPos p : allPartners) {
                BlockPos actualPartner = movedPositions.getOrDefault(p, p);
                addLink(newPos, actualPartner);
                saveLinkDb(newPos, actualPartner);
            }
            addChunkTicket(newPos);
        }

        // Третий проход: chunk tickets для обработанных блоков и их партнёров
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
    // EVENT: SHIFT+RMB — привязка (one-to-many)
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
                player.sendActionBar(ChatColor.RED + "✕ Binding cancelled");
                e.setCancelled(true);
                return;
            }

            Location firstLoc = first.toLocation();
            if (firstLoc == null || !firstLoc.getWorld().equals(clicked.getWorld())) {
                bindingPlayers.remove(uuid);
                player.sendActionBar(ChatColor.RED + "✕ Devices must be in the same world!");
                e.setCancelled(true);
                return;
            }

            // Добавляем связь к существующим (one-to-many)
            BlockPos second = clickedPos;
            addLink(first, second);
            addChunkTicket(first);
            addChunkTicket(second);
            saveLinkDb(first, second);

            int count = getPartners(first).size();
            player.sendActionBar(ChatColor.GREEN + "✓ "
                    + blockName(firstLoc.getBlock()) + " ↔ " + name + " linked! ("
                    + count + " connection" + (count > 1 ? "s" : "") + ")");
            e.setCancelled(true);
            return;
        }

        // Новая привязка или просмотр существующих
        Set<BlockPos> existing = getPartners(clickedPos);
        if (!existing.isEmpty()) {
            int count = existing.size();
            // Показываем первого партнёра для примера
            BlockPos firstPartner = existing.iterator().next();
            Location ploc = firstPartner.toLocation();
            String firstInfo = "";
            if (ploc != null) {
                firstInfo = " e.g. " + blockName(ploc.getBlock())
                        + " at [" + ploc.getBlockX() + " " + ploc.getBlockY() + " " + ploc.getBlockZ() + "]";
            }
            player.sendActionBar(ChatColor.GOLD + "⚡ " + name + " has " + count + " connection"
                    + (count > 1 ? "s" : "") + firstInfo
                    + ". Shift+RMB another device to add.");
            bindingPlayers.put(uuid, clickedPos);
            e.setCancelled(true);
            return;
        }

        bindingPlayers.put(uuid, clickedPos);
        player.sendActionBar(ChatColor.AQUA + "🔗 Binding: Shift+RMB any redstone device to link, "
                + "or same block to cancel.");
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
    // EVENT: BLOCK BREAK — разорвать ВСЕ связи
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
        player.sendActionBar(ChatColor.RED + "☠ " + blockName(block) + " broken — "
                + count + " wireless connection" + (count > 1 ? "s" : "") + " removed!");
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
