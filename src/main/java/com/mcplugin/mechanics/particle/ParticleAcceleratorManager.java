package com.mcplugin.mechanics.particle;

import com.mcplugin.core.Main;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.energy.machines.assembler.ItemCreatorRecipe;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.transfer.cable.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ParticleAcceleratorManager implements Listener {

    // =========================
    // BLOCK TYPES
    // =========================
    public static final Material RING = Material.GLASS;
    public static final Material RING_OLD = Material.CHISELED_TUFF;
    public static final Material ENGINE = Material.TUFF_BRICKS;
    public static final Material SENSOR = Material.POLISHED_DIORITE;
    public static final Material INJECTOR = Material.REINFORCED_DEEPSLATE;

    public static final Set<Material> ACCELERATOR_BLOCKS = Set.of(RING, RING_OLD, ENGINE, SENSOR, INJECTOR);

    // =========================
    // PDC KEYS
    // =========================
    public static final NamespacedKey PARTICLE_ID_KEY = new NamespacedKey("mcplugin", "particle_id");
    /** PDC key на предмете блока ускорителя — хранит строку типа блока ("particle_ring", "particle_engine" и т.д.). */
    public static final NamespacedKey PARTICLE_BLOCK_KEY = new NamespacedKey("mcplugin", "particle_block");
    /** PDC key на Marker'е датчика — хранит последнюю скорость частицы. */
    private static final NamespacedKey SENSOR_LAST_SPEED_KEY = new NamespacedKey("mcplugin", "sensor_last_speed");

    // =========================
    // ENGINE CONFIG
    // =========================
    /** Максимальная энергия двигателя — 1000⚡. Должен быть полностью заряжен, чтобы ускорить частицу. */
    private static final int ENGINE_MAX_ENERGY = 1000;
    /** Сколько энергии потребляет одно ускорение — весь буфер (1000). */
    private static final int ENGINE_COST_PER_USE = 1000;
    /** Скорость зарядки от кабелей за тик. */
    private static final int ENGINE_CHARGE_RATE = 20;
    public static final double SPEED_INCREMENT = 0.5; // разовый скачок при ускорении
    public static final double MAX_SPEED = 5.0;
    public static final double INITIAL_SPEED = 0.1;

    /** World+position key для engineEnergy */
    public record EnginePos(UUID worldUid, long blockKey) {}

    /** Engine energy buffers: EnginePos → energy amount. */
    private static final Map<EnginePos, Integer> engineEnergy = new ConcurrentHashMap<>();

    /** Sensor last speed: Location → speed in blocks/tick */
    private static final Map<Location, Double> sensorLastSpeed = new ConcurrentHashMap<>();

    // Active particles
    private static final Map<UUID, ParticleData> activeParticles = new ConcurrentHashMap<>();

    private static boolean enabled = true;

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        enabled = plugin.getConfig().getBoolean("particle_accelerator.enabled", true);
        Bukkit.getPluginManager().registerEvents(new ParticleAcceleratorManager(), plugin);
        ParticleCollisionHandler.loadConfig(plugin);
        // Сначала готовим таблицу для буферов энергии двигателей, затем загружаем
        // ранее накопленный прогресс из БД в in-memory кэш (иначе при рестарте
        // все буферы будут сброшены в 0, и игрок потеряет progress по зарядке).
        ParticleEnergyDatabase.initTables();
        loadEngineEnergyFromDb();
        scanExistingAccelerators();

        ConsoleLogger.info("[ParticleAccelerator] Manager initialized with "
                + engineEnergy.size() + " engine buffer(s) loaded from DB.");
    }

    /**
     * Подтягивает буферы энергии из SQLite. Вызывается из init() до
     * scanExistingAccelerators() — последний потом дополняет кэш для двигателей,
     * которые есть в мире, но ещё не имели буфера (новые установки).
     */
    private static void loadEngineEnergyFromDb() {
        Map<UUID, Map<Long, Integer>> all = ParticleEnergyDatabase.loadAll();
        if (all.isEmpty()) return;
        int count = 0;
        for (Map.Entry<UUID, Map<Long, Integer>> w : all.entrySet()) {
            for (Map.Entry<Long, Integer> e : w.getValue().entrySet()) {
                engineEnergy.put(new EnginePos(w.getKey(), e.getKey()), e.getValue());
                count++;
            }
        }
        if (count > 0) {
            ConsoleLogger.info("[ParticleAccelerator] Loaded " + count
                    + " engine buffer(s) from DB across " + all.size() + " world(s).");
        }
    }

    // =========================
    // SCAN EXISTING
    // =========================
    private static void scanExistingAccelerators() {
        for (var entry : StructureMarker.getAllEntries()) {
            String type = entry.getValue().type();
            if (!"accelerator".equals(type)) continue;

            String fk = entry.getKey();
            String worldUid = StructureMarker.parseWorldUid(fk);
            int x = StructureMarker.parseX(fk), y = StructureMarker.parseY(fk), z = StructureMarker.parseZ(fk);

            World world = Bukkit.getWorld(UUID.fromString(worldUid));
            if (world == null) continue;
            Location loc = new Location(world, x, y, z);
            Material mat = loc.getBlock().getType();
            if (!ACCELERATOR_BLOCKS.contains(mat)) {
                StructureMarker.removeAt(loc);
                continue;
            }
            if (mat == ENGINE) {
                EnginePos pos = new EnginePos(world.getUID(), LocationUtil.toKey(x, y, z));
                engineEnergy.putIfAbsent(pos, 0);
            }
            // Sensor speed is transient — loaded from Marker PDC on first particle pass
        }
        ConsoleLogger.info("[ParticleAccelerator] Scanned existing accelerators.");
    }

    // =========================
    // PARTICLE DATA
    // =========================
    public static class ParticleData {
        public final UUID id;
        public Marker entity;
        public Location location;
        public final String itemName;
        public final Material sourceMaterial;
        public List<Location> path;
        public int pathIndex;
        public double speed;
        public boolean dead = false;

        public ParticleData(UUID id, Location start, Material source, List<Location> path) {
            this.id = id;
            this.location = start.clone();
            this.sourceMaterial = source;
            this.itemName = source.name();
            this.path = path;
            this.pathIndex = 0;
            this.speed = INITIAL_SPEED;
        }
    }

    // =========================
    // PUBLIC API
    // =========================
    public static Collection<ParticleData> getActiveParticles() {
        return activeParticles.values();
    }

    public static void removeParticle(UUID id) {
        ParticleData data = activeParticles.remove(id);
        if (data != null && data.entity != null && !data.entity.isDead()) {
            data.entity.remove();
        }
    }

    // =========================
    // ENGINE ENERGY
    // =========================
    public static int getEngineEnergy(Location loc) {
        if (loc == null || loc.getWorld() == null) return 0;
        EnginePos pos = new EnginePos(loc.getWorld().getUID(), LocationUtil.toKey(loc));
        return engineEnergy.getOrDefault(pos, 0);
    }

    /** Проверяет, может ли двигатель ускорить частицу: buffer == 1000 И есть редстоун-сигнал. */
    public static boolean canEngineAccelerate(Location engineLoc) {
        if (engineLoc == null) return false;
        // Buffer must be full (1000)
        EnginePos pos = new EnginePos(engineLoc.getWorld().getUID(), LocationUtil.toKey(engineLoc));
        if (engineEnergy.getOrDefault(pos, 0) < ENGINE_MAX_ENERGY) return false;
        // Engine block must be powered by redstone
        Block block = engineLoc.getBlock();
        return block.isBlockPowered() || block.isBlockIndirectlyPowered();
    }

    /** Ускоряет частицу: потребляет весь буфер (1000→0). Должен вызываться ТОЛЬКО после canEngineAccelerate(). */
    public static boolean consumeEngineEnergy(Location engineLoc) {
        if (engineLoc == null || engineLoc.getWorld() == null) return false;
        EnginePos pos = new EnginePos(engineLoc.getWorld().getUID(), LocationUtil.toKey(engineLoc));
        if (engineEnergy.getOrDefault(pos, 0) < ENGINE_MAX_ENERGY) return false;
        engineEnergy.put(pos, 0); // consume ALL buffer (1000→0)
        ParticleEnergyDatabase.upsertOne(pos.worldUid(), pos.blockKey(), 0);
        return true;
    }

    /** Запоминает скорость частицы на датчике. */
    public static void setSensorLastSpeed(Location sensorLoc, double speed) {
        if (sensorLoc == null) return;
        sensorLastSpeed.put(LocationUtil.normalize(sensorLoc), speed);
    }

    /** Возвращает последнюю зарегистрированную скорость датчика. */
    public static double getSensorLastSpeed(Location sensorLoc) {
        if (sensorLoc == null) return 0;
        return sensorLastSpeed.getOrDefault(LocationUtil.normalize(sensorLoc), 0.0);
    }

    // =========================
    // ADD PARTICLE
    // =========================
    public static ParticleData createParticle(Location injectorLoc, ItemStack item) {
        if (!enabled) return null;
        Location norm = LocationUtil.normalize(injectorLoc);
        if (norm == null) return null;

        List<Location> path = findPath(norm);
        if (path.isEmpty()) return null;

        return createParticleWithPath(norm, item, path);
    }

    public static ParticleData createParticleWithPath(Location normLoc, ItemStack item, List<Location> path) {
        World world = normLoc.getWorld();
        if (world == null || path.isEmpty()) return null;

        UUID id = UUID.randomUUID();
        Material sourceMat = item.getType();

        Location spawnLoc = normLoc.clone().add(0.5, 0.5, 0.5);
        Marker marker = world.spawn(spawnLoc, Marker.class);
        marker.setPersistent(false);

        PersistentDataContainer pdc = marker.getPersistentDataContainer();
        pdc.set(PARTICLE_ID_KEY, PersistentDataType.STRING, id.toString());

        ParticleData data = new ParticleData(id, spawnLoc, sourceMat, path);
        data.entity = marker;
        activeParticles.put(id, data);

        ConsoleLogger.info("[ParticleAccelerator] Created particle " + id.toString().substring(0, 8)
                + " from " + sourceMat.name() + " at " + normLoc.getBlockX() + " " + normLoc.getBlockY() + " " + normLoc.getBlockZ());

        return data;
    }

    // =========================
    // PATH FINDING
    // =========================
    private static List<Location> findPath(Location start) {
        List<Location> path = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Location current = LocationUtil.normalize(start);
        if (current == null) return path;

        visited.add(current);

        Location next = findNextAcceleratorBlock(current, null);
        while (next != null) {
            if (visited.contains(next)) {
                path.add(next);
                break;
            }
            path.add(next);
            visited.add(next);
            Location prev = current;
            current = next;
            next = findNextAcceleratorBlock(current, prev);
        }

        return path;
    }

    private static boolean pathsOverlap(List<Location> pathA, List<Location> pathB) {
        Set<Location> setA = new HashSet<>(pathA);
        for (Location loc : pathB) {
            if (setA.contains(loc)) return true;
        }
        return false;
    }

    private static Location findNextAcceleratorBlock(Location from, Location exclude) {
        if (from == null || from.getWorld() == null) return null;
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : dirs) {
            int nx = from.getBlockX() + d[0];
            int ny = from.getBlockY() + d[1];
            int nz = from.getBlockZ() + d[2];
            Location neighbor = new Location(from.getWorld(), nx, ny, nz);
            if (exclude != null && neighbor.equals(exclude)) continue;
            if (ACCELERATOR_BLOCKS.contains(neighbor.getBlock().getType())) {
                return neighbor;
            }
        }
        return null;
    }

    // =========================
    // CHARGE ENGINES
    // =========================
    public static void chargeEngines() {
        for (Map.Entry<EnginePos, Integer> entry : engineEnergy.entrySet()) {
            EnginePos pos = entry.getKey();
            World world = Bukkit.getWorld(pos.worldUid());
            if (world == null) continue;
            int current = entry.getValue();
            if (current >= ENGINE_MAX_ENERGY) continue;

            Location loc = LocationUtil.toLocation(pos.blockKey(), world);
            if (loc == null) continue;

            int pulled = pullFromCables(loc, ENGINE_CHARGE_RATE);
            int newEnergy = Math.min(ENGINE_MAX_ENERGY, current + pulled);
            entry.setValue(newEnergy);
            // Персистим каждый успешный pull в БД — маленький INSERT OR REPLACE.
            // Альтернатива (периодический flush) рискует потерять progress в
            // crash'е между flush'ами.
            ParticleEnergyDatabase.upsertOne(pos.worldUid(), pos.blockKey(), newEnergy);
        }
    }

    private static final int PULL_BFS_DEPTH_CAP = 512;

    private static int pullFromCables(Location engineLoc, int maxAmount) {
        if (maxAmount <= 0) return 0;
        if (engineLoc == null || engineLoc.getWorld() == null) return 0;

        String worldUid = engineLoc.getWorld().getUID().toString();
        long engineKey = LocationUtil.toKey(engineLoc);

        // BFS через кабельную сеть от двигателя.
        // В этой архитектуре CABLE-узлы сами НЕ хранят энергию:
        // CableNode.addEnergy/removeEnergy возвращают no-op для NodeType.CABLE
        // (см. методы и комментарии в CableLossTask / CableTickTask).
        // Они лишь «провода»-роутеры между источниками (BATTERY/GENERATOR)
        // и потребителями (наш двигатель ускорителя). Поэтому идём через сеть
        // и снимаем энергию напрямую с BATTERY/GENERATOR.
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new ArrayDeque<>();
        for (long nearKey : LocationUtil.getNeighborKeys(engineKey)) {
            CableNode n = CableNetwork.getNodeByKey(worldUid, nearKey);
            if (n == null) continue;
            visited.add(nearKey);
            queue.add(nearKey);
        }

        int totalPulled = 0;
        int depth = 0;

        while (!queue.isEmpty() && totalPulled < maxAmount && depth < PULL_BFS_DEPTH_CAP) {
            depth++;
            long curKey = queue.poll();
            CableNode cur = CableNetwork.getNodeByKey(worldUid, curKey);
            if (cur == null) continue;

            // Источник энергии — BATTERY или GENERATOR с энергией
            NodeType type = cur.getType();
            if ((type == NodeType.BATTERY || type == NodeType.GENERATOR) && cur.getEnergy() > 0) {
                int available = cur.getEnergy();
                int toTake = Math.min(available, maxAmount - totalPulled);
                cur.removeEnergy(toTake);
                totalPulled += toTake;
                CableNetwork.markFlowingKey(worldUid, curKey);
                cur.addTransferred(toTake);
                // Не заходим в этот источник повторно через другие пути —
                // батарею/генератор «обслужили» за этот тик.
            }

            // Дальше идём и через CABLE-роутеры, и через конечные точки (BATTERY/GENERATOR),
            // чтобы добраться до любых источников энергии в сети за один обход.
            // BATTERY↔BATTERY соединения запрещены на уровне CableNetwork,
            // так что цепочки батарей всё равно пройдут через кабели.
            for (long connKey : cur.getConnectionKeys()) {
                if (visited.contains(connKey)) continue;
                CableNode next = CableNetwork.getNodeByKey(worldUid, connKey);
                if (next == null) continue;
                visited.add(connKey);
                NodeType nt = next.getType();
                if (nt == NodeType.CABLE
                        || nt == NodeType.BATTERY
                        || nt == NodeType.GENERATOR) {
                    queue.add(connKey);
                }
            }
        }

        if (depth >= PULL_BFS_DEPTH_CAP && !queue.isEmpty()) {
            com.mcplugin.util.ConsoleLogger.warn(
                    "[ParticleAccelerator] BFS depth cap hit (" + PULL_BFS_DEPTH_CAP
                            + ") while charging engine at " + engineLoc + " — pulled "
                            + totalPulled + "/" + maxAmount);
        }

        return totalPulled;
    }

    // =========================
    // BLOCK PLACE
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        Material type = block.getType();
        if (!ACCELERATOR_BLOCKS.contains(type)) return;

        Location loc = LocationUtil.normalize(block.getLocation());
        if (loc == null) return;

        // Check if the player placed a PDC-tagged accelerator block item
        ItemStack item = e.getItemInHand();
        if (item != null && item.hasItemMeta()) {
            var pdc = item.getItemMeta().getPersistentDataContainer();
            // Check if it's a legitimate particle block (crafted in Item Creator or from /mp menu)
            if (pdc.has(PARTICLE_BLOCK_KEY, PersistentDataType.STRING)) {
                String blockType = pdc.get(PARTICLE_BLOCK_KEY, PersistentDataType.STRING);
                ConsoleLogger.info("[ParticleAccelerator] Placed " + blockType + " (" + type.name() + ") at "
                        + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
            }
            // If no PDC tag, the player placed a vanilla block that happens to match. Don't register.
            // This prevents accidental registration of vanilla TUFF_BRICKS as engine blocks.
            if (!pdc.has(PARTICLE_BLOCK_KEY, PersistentDataType.STRING)) {
                ConsoleLogger.info("[ParticleAccelerator] Vanilla " + type.name() + " placed at "
                        + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + " — NOT registered as accelerator block.");
                return;
            }
        } else {
            // No PDC tag — vanilla block, don't register
            return;
        }

        // Create Marker entity
        StructureMarker.place(loc, "accelerator", UUID.randomUUID());

        // Initialize engine energy buffer
        if (type == ENGINE) {
            EnginePos pos = new EnginePos(loc.getWorld().getUID(), LocationUtil.toKey(loc));
            engineEnergy.putIfAbsent(pos, 0);
        }
    }

    // =========================
    // BLOCK BREAK
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Material type = block.getType();
        if (!ACCELERATOR_BLOCKS.contains(type)) return;

        Location loc = LocationUtil.normalize(block.getLocation());
        if (loc == null) return;

        // Only handle if this is actually a registered accelerator block (has Marker)
        if (!StructureMarker.existsAt(loc)) return;

        // Remove Marker
        StructureMarker.removeAt(loc);

        // Clean up engine energy buffer
        if (type == ENGINE) {
            EnginePos pos = new EnginePos(loc.getWorld().getUID(), LocationUtil.toKey(loc));
            engineEnergy.remove(pos);
            // Удаляем из БД, иначе при следующем load этот ключ снова появится в кэше
            // и будет медленно дрейфовать к устаревшим координатам.
            ParticleEnergyDatabase.deleteOne(pos.worldUid(), pos.blockKey());
        }

        // Clean up sensor data
        if (type == SENSOR) {
            sensorLastSpeed.remove(loc);
        }

        // Cancel default drops — we drop the PDC-tagged custom item instead
        e.setDropItems(false);

        // Drop the correct custom item for this block type
        ItemStack drop = createParticleBlockItem(type);
        if (drop != null && loc.getWorld() != null) {
            loc.getWorld().dropItemNaturally(
                    loc.clone().add(0.5, 0.5, 0.5),
                    drop);
        }

        // Clean up any particles that are passing through this block
        activeParticles.values().removeIf(p -> {
            if (p.dead) return true;
            Location pLoc = LocationUtil.normalize(p.location);
            if (pLoc != null && pLoc.equals(loc)) {
                p.dead = true;
                if (p.entity != null && !p.entity.isDead()) p.entity.remove();
                return true;
            }
            return false;
        });

        ConsoleLogger.info("[ParticleAccelerator] Broken " + type.name() + " at "
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    /**
     * Создаёт PDC-тегированный предмет для блока ускорителя.
     * Используется для дропа при ломании и для выдачи в админке.
     */
    public static ItemStack createParticleBlockItem(Material material) {
        return switch (material) {
            case GLASS -> createPdcItem(material,
                    "<white>Particle Ring *</white>",
                    "<gray>Guides particles along the accelerator path.</gray>",
                    "particle_ring");
            case CHISELED_TUFF -> createPdcItem(material,
                    "<white>Particle Ring (old) *</white>",
                    "<gray>Old ring type for backward compatibility.</gray>",
                    "particle_ring_old");
            case TUFF_BRICKS -> createPdcItem(material,
                    "<white>Particle Engine *</white>",
                    "<gray>Accelerates particles. Requires 1000⚡ buffer and redstone.</gray>",
                    "particle_engine");
            case POLISHED_DIORITE -> createPdcItem(material,
                    "<white>Particle Speed Sensor *</white>",
                    "<gray>Measures particle speed (0-99.999% light speed).</gray>",
                    "particle_sensor");
            case REINFORCED_DEEPSLATE -> createPdcItem(material,
                    "<white>Particle Injector *</white>",
                    "<gray>Right-click with any item to inject it as a particle.</gray>",
                    "particle_injector");
            default -> null;
        };
    }

    private static ItemStack createPdcItem(Material mat, String name, String lore, String blockType) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<!italic>" + name));
            meta.lore(List.of(MessageUtil.parse("<!italic>" + lore)));
            meta.getPersistentDataContainer().set(PARTICLE_BLOCK_KEY, PersistentDataType.STRING, blockType);
            item.setItemMeta(meta);
        }
        return item;
    }

    // =========================
    // INJECTOR INTERACTION
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onInjectorInteract(PlayerInteractEvent e) {
        if (!enabled) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null || clicked.getType() != INJECTOR) return;

        Location clickLoc = LocationUtil.normalize(clicked.getLocation());
        if (clickLoc == null || !StructureMarker.existsAt(clickLoc)) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(MessageUtil.parse("<gray>[<blue>⚠</blue>] Hold an item to inject it as a particle!</gray>"));
            return;
        }

        e.setCancelled(true);
        Location loc = clicked.getLocation();
        Location normLoc = LocationUtil.normalize(loc);
        List<Location> newPath = findPath(normLoc);
        if (newPath.isEmpty()) {
            player.sendMessage(MessageUtil.parse("<gray>[<red>✗</red>] <red>No accelerator path found!</red> Place rings/engines/sensors in a line."));
            return;
        }

        boolean alreadyRunning = activeParticles.values().stream()
                .anyMatch(p -> !p.dead && pathsOverlap(p.path, newPath));
        if (alreadyRunning) {
            player.sendMessage(MessageUtil.parse("<gray>[<blue>⚠</blue>] A particle is already running in this accelerator!</gray>"));
            return;
        }

        ParticleData data = createParticleWithPath(normLoc, hand, newPath);
        if (data == null) {
            player.sendMessage(MessageUtil.parse("<gray>[<red>✗</red>] <red>No accelerator path found!</red> Place rings/engines/sensors in a line."));
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        loc.getWorld().spawnParticle(Particle.END_ROD,
                loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.01);

        String matName = formatMaterialName(data.sourceMaterial.name());
        double speedPct = data.speed / MAX_SPEED * 100.0;
        player.sendMessage(MessageUtil.parse("<gray>[<green>✓</green>] <white>" + matName
                + "</white> particle injected! Speed: <aqua>" + String.format("%.1f", data.speed)
                + "</aqua> <gray>(" + String.format("%.3f", speedPct) + "% light speed)</gray>"));
    }

    // =========================
    // SHUTDOWN
    // =========================
    public static void shutdown() {
        for (ParticleData p : activeParticles.values()) {
            if (p.entity != null && !p.entity.isDead()) {
                p.entity.remove();
            }
        }
        activeParticles.clear();
        engineEnergy.clear();
        sensorLastSpeed.clear();
        ConsoleLogger.info("[ParticleAccelerator] Shutdown complete.");
    }

    // =========================
    // UTILITY
    // =========================
    private static String formatMaterialName(String name) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (c == '_') { nextUpper = true; continue; }
            result.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c));
            nextUpper = false;
        }
        return result.toString();
    }

    public static boolean isEnabled() { return enabled; }
}
