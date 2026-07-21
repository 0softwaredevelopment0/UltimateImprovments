package com.mcplugin.mechanics.protection;

import com.mcplugin.core.Main;
import com.mcplugin.core.Keys;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.MessageUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер «Блоков защиты».
 * <p>
 * Кэширует состояние всех размещённых блоков в памяти; для быстрого
 * event-фильтра используется индекс по chunk-key (см. {@link #byChunkKey}).
 * <p>
 * Также отвечает за голограммы (TextDisplay) над блоками и расчёт
 * очков по предмету-топливу (через {@code Bukkit.getUnsafe().getBurnTime}).
 */
public class ProtectionManager {

    private static ProtectionManager instance;

    /** Все зарегистрированные блоки защиты: UUID блока → данные. */
    private final Map<UUID, ProtectionBlock> blocks = new ConcurrentHashMap<>();

    /** Индекс для быстрого lookup по chunk-key: chunk-key → список UUID блоков в этом чанке. */
    private final Map<Long, List<UUID>> byChunkKey = new ConcurrentHashMap<>();

    /** UUID TextDisplay-голограмм для каждого блока. BlockId → entity UUID. */
    private final Map<UUID, UUID> blockHolograms = new ConcurrentHashMap<>();

    /** Материал блока защиты (кешируется). */
    private Material cachedMaterial;

    private final MiniMessage MM = MiniMessage.miniMessage();
    private HologramUpdateTask hologramTask;

    private ProtectionManager() {}

    public static ProtectionManager getInstance() {
        if (instance == null) {
            instance = new ProtectionManager();
        }
        return instance;
    }

    // =========================
    // INIT / SHUTDOWN
    // =========================
    public void init() {
        ProtectionDatabase.initTables();
        cacheMaterial();
        loadFromDb();

        // Spawn holograms for blocks in loaded chunks (chunk-load listener will spawn the rest)
        for (ProtectionBlock block : blocks.values()) {
            ChunkLoadSpawner.scheduleSpawn(block);
        }

        hologramTask = new HologramUpdateTask();
        hologramTask.runTaskTimer(Main.getInstance(), 20L, 20L);
        ConsoleLogger.info("[ProtectionBlock] Manager initialized: " + blocks.size() + " block(s).");
    }

    public void shutdown() {
        if (hologramTask != null) hologramTask.cancel();
        // Save all blocks
        for (ProtectionBlock b : blocks.values()) {
            ProtectionDatabase.saveBlock(b);
            ProtectionDatabase.saveWhitelist(b);
        }
        // Despawn holograms
        for (UUID hid : new ArrayList<>(blockHolograms.values())) {
            Entity e = Bukkit.getEntity(hid);
            if (e != null) e.remove();
        }
        blockHolograms.clear();
        blocks.clear();
        byChunkKey.clear();
    }

    private void cacheMaterial() {
        try {
            this.cachedMaterial = Material.valueOf(ProtectionConfig.getBlockMaterial());
        } catch (Exception e) {
            this.cachedMaterial = Material.LODESTONE;
            ConsoleLogger.warn("[ProtectionBlock] Invalid material '" + ProtectionConfig.getBlockMaterial()
                    + "' in config — using LODESTONE.");
        }
    }

    private void loadFromDb() {
        for (ProtectionDatabase.LoadedBlock lb : ProtectionDatabase.loadAllBlocks()) {
            World world = Bukkit.getWorld(lb.worldName());
            if (world == null) {
                ConsoleLogger.warn("[ProtectionBlock] World " + lb.worldName() + " missing — skipping block " + lb.id());
                continue;
            }
            Location loc = new Location(world, lb.x(), lb.y(), lb.z());
            ProtectionBlock block = new ProtectionBlock(lb.id(), loc, lb.owner(),
                    lb.radius(), lb.integrity(), lb.points(), lb.enabled());
            block.setRadiusUpgradeCount(lb.radiusUpgradeCount());
            block.setRepairCount(lb.repairCount());
            for (UUID pid : lb.whitelist()) block.addToWhitelist(pid);
            registerBlock(block, false);
        }
    }

    // =========================
    // BLOCK REGISTRATION
    // =========================
    /** Привязывает блок к кэшу. Если save = true — синхронно пишет в БД. */
    public void registerBlock(ProtectionBlock block, boolean save) {
        blocks.put(block.getId(), block);
        long chunkKey = LocationUtil.toKey(block.getX() >> 4, 0, block.getZ() >> 4);
        byChunkKey.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(block.getId());
        if (save) {
            ProtectionDatabase.saveBlock(block);
            ProtectionDatabase.saveWhitelist(block);
            ChunkLoadSpawner.scheduleSpawn(block);
        }
    }

    /** Удаляет блок из кэша и БД. Не удаляет сам Minecraft-блок (это делает вызывающий код). */
    public void unregisterBlock(UUID id, boolean saveDelete) {
        ProtectionBlock block = blocks.remove(id);
        if (block == null) return;
        long chunkKey = LocationUtil.toKey(block.getX() >> 4, 0, block.getZ() >> 4);
        List<UUID> list = byChunkKey.get(chunkKey);
        if (list != null) list.remove(id);
        // Despawn hologram
        UUID hid = blockHolograms.remove(id);
        if (hid != null) {
            Entity e = Bukkit.getEntity(hid);
            if (e != null) e.remove();
        }
        if (saveDelete) ProtectionDatabase.deleteBlock(id);
    }

    // =========================
    // LOOKUPS
    // =========================
    public ProtectionBlock getBlock(UUID id) {
        return blocks.get(id);
    }

    public ProtectionBlock getBlockAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Location block = LocationUtil.normalize(location);
        for (ProtectionBlock pb : blocks.values()) {
            if (pb.getWorld().equals(block.getWorld())
                    && pb.getX() == block.getBlockX()
                    && pb.getY() == block.getBlockY()
                    && pb.getZ() == block.getBlockZ()) {
                return pb;
            }
        }
        return null;
    }

    /** True если location находится внутри радиуса какого-либо включённого и живого блока. */
    public ProtectionBlock findProtectingBlock(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Location block = LocationUtil.normalize(location);
        for (ProtectionBlock pb : blocks.values()) {
            if (!pb.isEnabled() || !pb.isAlive()) continue;
            if (!pb.getWorld().equals(block.getWorld())) continue;
            int dx = pb.getX() - block.getBlockX();
            int dz = pb.getZ() - block.getBlockZ();
            int dy = pb.getY() - block.getBlockY();
            // Простая «кубическая» метрика — блок защищает куб со стороной 2*radius+1.
            // Граница блока (центральный блок) не входит.
            if (Math.abs(dx) > pb.getRadius() || Math.abs(dz) > pb.getRadius()) continue;
            if (Math.abs(dy) > pb.getRadius()) continue;
            if (dx == 0 && dy == 0 && dz == 0) continue; // сам блок защиты не считается
            return pb;
        }
        return null;
    }

    /** Сколько блоков из list попадают в радиус защиты block. */
    public int countBlocksInRadius(ProtectionBlock block, List<Block> list) {
        int count = 0;
        if (block == null || list == null) return 0;
        int r = block.getRadius();
        for (Block b : list) {
            if (b == null || b.getWorld() == null) continue;
            if (!b.getWorld().equals(block.getWorld())) continue;
            int dx = b.getX() - block.getX();
            int dy = b.getY() - block.getY();
            int dz = b.getZ() - block.getZ();
            if (Math.abs(dx) <= r && Math.abs(dy) <= r && Math.abs(dz) <= r) count++;
        }
        return count;
    }

    public Collection<ProtectionBlock> allBlocks() {
        return blocks.values();
    }

    public Material getBlockMaterial() { return cachedMaterial; }

    // =========================
    // PLACE / BREAK
    // =========================
    /**
     * Создаёт новый блок защиты при первой установке предмета.
     * @return созданный блок (уже зарегистрирован и сохранён в БД)
     */
    public ProtectionBlock createBlock(Location placed, UUID owner) {
        UUID id = UUID.randomUUID();
        ProtectionBlock block = new ProtectionBlock(id, placed, owner,
                ProtectionConfig.getDefaultRadius(),
                ProtectionConfig.getStartingIntegrity(),
                0, false); // start with 0 points and disabled
        block.addToWhitelist(owner); // auto-whitelist owner
        registerBlock(block, true);
        return block;
    }

    /**
     * Подсчёт очков от предмета-топлива. Используется встроенный справочник
     * «burn-ticks» для ванильных видов топлива (Paper API getBurnTime
     * нестабилен между версиями).
     */
    public int computePointsFromFuel(Material material, int amount) {
        if (material == null || amount <= 0) return 0;
        int burnTicks = FUEL_BURN_TICKS.getOrDefault(material, 0);
        if (burnTicks <= 0) return 0;
        double mult = ProtectionConfig.getFuelPointsMultiplier();
        return Math.max(0, (int) Math.floor(burnTicks * mult * amount));
    }

    /**
     * Встроенный справочник «burn-ticks» для ванильных видов топлива.
     * Значения соответствуют ванильным furnace-рецептам Minecraft 1.21.x.
     */
    private static final Map<Material, Integer> FUEL_BURN_TICKS;
    static {
        Map<Material, Integer> m = new HashMap<>();
        m.put(Material.COAL, 1600);
        m.put(Material.CHARCOAL, 1600);
        m.put(Material.COAL_BLOCK, 16000);
        m.put(Material.LAVA_BUCKET, 20000);
        m.put(Material.OAK_LOG, 300);
        m.put(Material.SPRUCE_LOG, 300);
        m.put(Material.BIRCH_LOG, 300);
        m.put(Material.JUNGLE_LOG, 300);
        m.put(Material.ACACIA_LOG, 300);
        m.put(Material.DARK_OAK_LOG, 300);
        m.put(Material.MANGROVE_LOG, 300);
        m.put(Material.CHERRY_LOG, 300);
        m.put(Material.OAK_PLANKS, 300);
        m.put(Material.SPRUCE_PLANKS, 300);
        m.put(Material.BIRCH_PLANKS, 300);
        m.put(Material.JUNGLE_PLANKS, 300);
        m.put(Material.ACACIA_PLANKS, 300);
        m.put(Material.DARK_OAK_PLANKS, 300);
        m.put(Material.MANGROVE_PLANKS, 300);
        m.put(Material.CHERRY_PLANKS, 300);
        m.put(Material.STICK, 100);
        m.put(Material.WOODEN_PICKAXE, 200);
        m.put(Material.WOODEN_AXE, 200);
        m.put(Material.WOODEN_SHOVEL, 200);
        m.put(Material.WOODEN_HOE, 200);
        m.put(Material.WOODEN_SWORD, 200);
        // Саплинги в Paper 1.21.x — отдельные Material.* на каждый тип
        for (Material sap : new Material[]{
                Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
                Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
                Material.CHERRY_SAPLING, Material.MANGROVE_PROPAGULE,
                Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS,
                Material.FLOWERING_AZALEA, Material.AZALEA}) {
            m.put(sap, 100);
        }
        m.put(Material.DRIED_KELP, 200);
        m.put(Material.DRIED_KELP_BLOCK, 4000);
        m.put(Material.BAMBOO_BLOCK, 30000);
        m.put(Material.SCAFFOLDING, 50);
        m.put(Material.WEEPING_VINES, 100);
        m.put(Material.TWISTING_VINES, 100);
        m.put(Material.CRIMSON_STEM, 300);
        m.put(Material.WARPED_STEM, 300);
        m.put(Material.NETHER_WART_BLOCK, 30000);
        m.put(Material.BLAZE_ROD, 2400);
        m.put(Material.PHANTOM_MEMBRANE, 0); // not fuel; placeholder so explicit 0
        FUEL_BURN_TICKS = Collections.unmodifiableMap(m);
    }

    // =========================
    // HOLOGRAM (TextDisplay)
    // =========================
    public void spawnHologram(ProtectionBlock block) {
        if (block == null) return;
        UUID existing = blockHolograms.get(block.getId());
        if (existing != null) {
            Entity e = Bukkit.getEntity(existing);
            if (e != null && e.isValid() && !e.isDead()) return; // already spawned
        }
        Location holoLoc = block.getLocation().add(0, 1.85, 0);
        World world = holoLoc.getWorld();
        if (world == null) return;

        TextDisplay display = world.spawn(holoLoc, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setShadowed(true);
            e.setSeeThrough(false);
            e.setLineWidth(120);
            try {
                e.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            } catch (Throwable ignored) {
                // Paper 1.21.4+ requires ARGB form; older API fallback
                try { e.setBackgroundColor(org.bukkit.Color.fromRGB(0, 0, 0)); }
                catch (Throwable ignored2) { /* ignore */ }
            }
            // Default marker via PDC so we can find it on chunk reload
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            pdc.set(Keys.PROTECTION_BLOCK, PersistentDataType.STRING, block.getId().toString());
            e.customName(Component.text("Protection Block Hologram"));
            e.setCustomNameVisible(false);
            updateHologramText(e, block);
        });
        if (display != null) {
            blockHolograms.put(block.getId(), display.getUniqueId());
        }
    }

    public void despawnHologram(UUID blockId) {
        UUID hid = blockHolograms.remove(blockId);
        if (hid != null) {
            Entity e = Bukkit.getEntity(hid);
            if (e != null) e.remove();
        }
    }

    private void updateHologramText(TextDisplay display, ProtectionBlock block) {
        String title = block.isEnabled()
                ? ProtectionConfig.getMessage("hologram.title_enabled",
                        "<white>Блок защиты <green>✔</green></white>")
                : ProtectionConfig.getMessage("hologram.title_disabled",
                        "<white>Блок защиты <red>❌</red></white>");
        String radiusLine = ProtectionConfig.getMessage("hologram.radius",
                        "<gray>Радиус: <white>%radius%</white></gray>")
                .replace("%radius%", String.valueOf(block.getRadius()));
        String integrityLine = ProtectionConfig.getMessage("hologram.integrity",
                        "<gray>Целостность: <white>%value%%</white></gray>")
                .replace("%value%", String.format("%.1f", block.getIntegrity()));
        String pointsLine = ProtectionConfig.getMessage("hologram.points",
                        "<gray>Очки: <gold>%value%</gold></gray>")
                .replace("%value%", String.valueOf(block.getPoints()));
        String combined = title + "\n" + radiusLine + "\n" + integrityLine + "\n" + pointsLine;
        display.text(MM.deserialize(combined));
    }

    private void updateAllHolograms() {
        for (ProtectionBlock block : blocks.values()) {
            UUID hid = blockHolograms.get(block.getId());
            if (hid == null) continue;
            Entity e = Bukkit.getEntity(hid);
            if (e instanceof TextDisplay td) {
                updateHologramText(td, block);
            } else if (e == null || e.isDead()) {
                // chunk unloaded → hologram despawned; respawn when chunk reloads
                blockHolograms.remove(block.getId());
                ChunkLoadSpawner.scheduleSpawn(block);
            }
        }
    }

    /** Background task that periodically refreshes hologram text + respawns holograms after chunk unloading. */
    private class HologramUpdateTask extends BukkitRunnable {
        @Override
        public void run() {
            updateAllHolograms();
        }
    }

    // =========================
    // CHUNK LOAD HOOKS
    // =========================
    /** Called by ProtectionListener on ChunkLoadEvent. */
    public void onChunkLoad(int chunkX, int chunkZ, World world) {
        for (Map.Entry<Long, List<UUID>> entry : byChunkKey.entrySet()) {
            int cx = LocationUtil.getX(entry.getKey());
            int cz = LocationUtil.getZ(entry.getKey());
            if (cx == chunkX && cz == chunkZ) {
                for (UUID id : entry.getValue()) {
                    ProtectionBlock block = blocks.get(id);
                    if (block != null && block.getWorld().equals(world)) {
                        // Проверяем, что блок физически всё ещё на месте
                        Material m = block.getBlockLocation().getBlock().getType();
                        if (m == cachedMaterial) {
                            spawnHologram(block);
                        } else {
                            // Блок заменили/уничтожили — удаляем из кэша
                            unregisterBlock(id, true);
                            ConsoleLogger.info("[ProtectionBlock] Block at " + block.getBlockLocation()
                                    + " no longer exists (" + m + "). Removed from cache.");
                        }
                    }
                }
            }
        }
    }

    /** Spawn hologram via chunk load */
    private static class ChunkLoadSpawner {
        static void scheduleSpawn(ProtectionBlock block) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (ProtectionManager.getInstance().blocks.containsKey(block.getId())) {
                        ProtectionManager.getInstance().spawnHologram(block);
                    }
                }
            }.runTask(Main.getInstance());
        }
    }

    // =========================
    // DAMAGE BLOCK
    // =========================
    public void applyIntegrityDamage(ProtectionBlock block, double amount) {
        if (block == null) return;
        double newVal = block.getIntegrity() - amount;
        block.setIntegrity(newVal);
        saveBlockState(block);
        if (newVal <= 0.0) {
            // BOOM, и удаление блока
            destroyBlock(block, true);
        }
    }

    /**
     * Уничтожает блок защиты: взрыв + удаление + удаление из БД.
     * Если physicalBlockRemove = true, также удаляет Minecraft-блок.
     */
    public void destroyBlock(ProtectionBlock block, boolean physicalBlockRemove) {
        if (block == null) return;
        Location loc = block.getBlockLocation();
        World world = loc.getWorld();
        if (world != null) {
            world.spawnParticle(org.bukkit.Particle.EXPLOSION, loc.clone().add(0.5, 0.5, 0.5), 4);
            world.playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        }
        if (physicalBlockRemove) {
            Block mb = loc.getBlock();
            if (mb.getType() == cachedMaterial) {
                mb.setType(org.bukkit.Material.AIR, false);
            }
        }
        unregisterBlock(block.getId(), true);
        ConsoleLogger.info("[ProtectionBlock] Destroyed block " + block.getId() + " at " + loc);
    }

    /** Save state (after radius/integrity/points changes). */
    public void saveBlockState(ProtectionBlock block) {
        if (block != null) ProtectionDatabase.saveBlock(block);
    }

    /** Save whitelist to DB (helper for listeners). */
    public void saveWhitelistToDb(ProtectionBlock block) {
        if (block != null) ProtectionDatabase.saveWhitelist(block);
    }

    // =========================
    // ADMIN OPS
    // =========================
    public boolean giveItemTo(org.bukkit.entity.Player player, int amount) {
        org.bukkit.inventory.ItemStack stack = ProtectionItem.createProtectionItem(amount);
        if (stack == null) return false;
        var overflow = player.getInventory().addItem(stack);
        for (var extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        return true;
    }
}
