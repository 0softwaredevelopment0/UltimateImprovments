package com.ultimateimprovments.mechanics.features.collapse;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.LocationUtil;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🏗 Block Collapse — система липкости/тяжести блоков.
 * <p>
 * Каждый поставленный игроком твёрдый блок получает атрибуты:
 * <ul>
 *   <li><b>Липкость</b> — 0..100 (double). Пока блок имеет опору (твёрдый блок
 *       прямо под ним) — липкость не тратится. Без опоры блок «расползается»:
 *       липкость убывает со скоростью {@code heaviness × rate} в секунду.</li>
 *   <li><b>Тяжесть</b> — вес блока (из таблицы материалов + overrides в конфиге).</li>
 * </ul>
 * Когда липкость достигает 0% — блок <b>обрушается</b>: ломается и падает
 * предметом, как будто его добыли незеритовым инструментом. При обрушении
 * блока блоки над ним теряют опору и падают следом (каскад вверх).
 * Попытка поставить блок на грань блока с липкостью 0% тоже приводит к обрушению.
 * <p>
 * В описании (lore) блочного предмета в инвентаре показываются
 * «Липкость: 100» и «Тяжесть: X».
 * <p>
 * 💾 Состояние хранится в ОЗУ, периодически сбрасывается в БД плагина
 * (таблица {@code block_collapse}), чтобы не потерять данные при шатдауне.
 */
public class BlockCollapseManager extends BukkitRunnable {

    private static final String DB_TABLE = "block_collapse";

    private static BlockCollapseManager instance;

    private final Main plugin;

    // ===== КОНФИГ =====
    private boolean enabled = true;
    /** Скорость траты липкости: липкость -= heaviness × rate (в секунду). */
    private double rate = 1.0;
    private int intervalTicks = 20;
    private int saveIntervalSeconds = 60;
    private boolean loreEnabled = true;
    private int loreIntervalTicks = 100;
    private double defaultHeaviness = 5.0;
    private final Map<String, Double> overrides = new HashMap<>();

    // ===== СОСТОЯНИЕ (ОЗУ) =====
    /** world name → (block key → текущая липкость 0..100). Все поставленные игроками твёрдые блоки. */
    private final Map<String, Map<Long, Double>> tracked = new ConcurrentHashMap<>();
    /** ключи, изменившиеся с прошлого сохранения в БД. */
    private final Set<DirtyKey> dirtyKeys = ConcurrentHashMap.newKeySet();

    private int tickCounter = 0;
    private boolean taskScheduled = false;

    private BlockCollapseManager(Main plugin) {
        this.plugin = plugin;
    }

    // =========================
    // INIT / SHUTDOWN / RELOAD
    // =========================

    public static void init(Main plugin) {
        if (instance != null) {
            instance.cancel();
        }
        instance = new BlockCollapseManager(plugin);
        instance.taskScheduled = false;
        instance.reloadConfig();
        instance.loadFromDb();
        instance.runTaskTimer(plugin, 40L, instance.intervalTicks);
        instance.taskScheduled = true;
        ConsoleLogger.info("[BLOCK_COLLAPSE] Initialized (enabled=" + instance.enabled
                + ", rate=" + instance.rate + ", interval=" + instance.intervalTicks + "t)");
    }

    public static BlockCollapseManager getInstance() {
        return instance;
    }

    public static void reload() {
        if (instance != null) {
            instance.reloadConfig();
            ConsoleLogger.info("[BLOCK_COLLAPSE] Config reloaded (enabled=" + instance.enabled + ")");
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.saveDirty();
            instance.cancel();
            instance = null;
            ConsoleLogger.info("[BLOCK_COLLAPSE] Shutdown: state flushed to DB.");
        }
    }

    public void reloadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("features.block_collapse");
        if (sec == null) {
            enabled = false;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        rate = sec.getDouble("rate", 1.0);
        saveIntervalSeconds = Math.max(1, sec.getInt("save_interval_seconds", 60));
        loreEnabled = sec.getBoolean("lore_enabled", true);
        loreIntervalTicks = Math.max(1, sec.getInt("lore_interval_ticks", 100));
        defaultHeaviness = sec.getDouble("default_heaviness", 5.0);

        overrides.clear();
        ConfigurationSection ov = sec.getConfigurationSection("heaviness_overrides");
        if (ov != null) {
            for (String key : ov.getKeys(false)) {
                try {
                    overrides.put(key.toUpperCase(Locale.ROOT), ov.getDouble(key));
                } catch (Exception ignored) {
                    // невалидное значение — пропускаем
                }
            }
        }

        int newInterval = Math.max(1, sec.getInt("interval_ticks", 20));
        if (newInterval != intervalTicks) {
            intervalTicks = newInterval;
            if (instance != null && taskScheduled) {
                try {
                    instance.cancel();
                    instance.runTaskTimer(plugin, 40L, intervalTicks);
                } catch (Exception e) {
                    ConsoleLogger.warn("[BLOCK_COLLAPSE] Failed to restart task: " + e.getMessage());
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // =========================
    // СВОЙСТВА БЛОКОВ
    // =========================

    /** Участвует ли блок в механике: твёрдый блок (цветы и т.п. — нет). */
    public static boolean isStructural(Material mat) {
        return mat != null && mat != Material.AIR && mat.isBlock() && mat.isSolid();
    }

    /** Тяжесть блока: override из конфига или таблица по названию материала. */
    public double heaviness(Material mat) {
        Double ov = overrides.get(mat.name());
        if (ov != null) {
            return ov;
        }
        return defaultHeaviness(mat);
    }

    /**
     * Таблица тяжести по названию материала (чем «тяжелее» блок — тем быстрее
     * тратится липкость). Самое специфичное — раньше.
     */
    private double defaultHeaviness(Material mat) {
        String n = mat.name();

        if (n.startsWith("NETHERITE")) return 20;
        if (n.contains("ANCIENT_DEBRIS")) return 18;
        if (n.contains("DIAMOND")) return 16;
        if (n.contains("EMERALD")) return 15;
        if (n.contains("GOLD")) return 14;
        if (n.contains("IRON")) return 13;
        if (n.contains("COPPER")) return 12;
        if (n.contains("OBSIDIAN")) return 12;
        if (n.contains("CONCRETE")) return 10;
        if (n.contains("BRICK")) return 9;
        // Важно: SANDSTONE / END_STONE / REDSTONE содержат подстроку «STONE»,
        // поэтому 7-группа проверяется ДО правила STONE (8).
        if (n.contains("SANDSTONE") || n.contains("QUARTZ") || n.contains("TUFF")
                || n.contains("BASALT") || n.contains("BLACKSTONE") || n.contains("PRISMARINE")
                || n.contains("PURPUR") || n.contains("END_STONE") || n.contains("GRANITE")
                || n.contains("DIORITE") || n.contains("ANDESITE") || n.contains("AMETHYST")
                || n.contains("CALCITE") || n.contains("DRIPSTONE")) return 7;
        if (n.contains("DEEPSLATE") || n.contains("COBBLESTONE") || n.contains("STONE")) return 8;
        if (n.contains("TERRACOTTA")) return 6;
        if (n.contains("PLANKS") || n.contains("LOG") || n.contains("WOOD") || n.contains("FENCE")
                || n.contains("GATE") || n.contains("BARREL") || n.contains("BOOKSHELF")
                || n.contains("CRAFTING_TABLE")) return 4;
        if (n.contains("GRAVEL") || n.contains("CLAY")) return 5;
        if (n.contains("DIRT") || n.contains("GRASS_BLOCK") || n.contains("MUD") || n.contains("SAND")
                || n.contains("PODZOL") || n.contains("MYCELIUM") || n.contains("NETHERRACK")) return 4;
        if (n.contains("GLASS")) return 2;
        if (n.contains("ICE")) return 2;
        if (n.contains("WOOL")) return 2;
        if (n.contains("LEAVES") || n.contains("MOSS") || n.contains("HAY") || n.contains("SPONGE")
                || n.contains("SLIME") || n.contains("HONEY")) return 1;

        return defaultHeaviness;
    }

    // =========================
    // СОБЫТИЯ (вызываются из BlockCollapseListener)
    // =========================

    /** Блок поставлен игроком. */
    public void onBlockPlaced(Block block) {
        if (!enabled || block == null) return;
        if (!isStructural(block.getType())) return;
        // Функциональные блоки (сундуки, печи, маяки...) не участвуют —
        // их обрушение потеряло бы инвентарь/данные тил-эннити.
        if (block.getState() instanceof TileState) return;

        String worldName = block.getWorld().getName();
        long key = LocationUtil.toKey(block.getLocation());
        tracked.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>()).put(key, 100.0);
        dirtyKeys.add(new DirtyKey(worldName, key));

        // Правило: постановка на грань блока с липкостью 0% → обрушение.
        Block below = block.getWorld().getBlockAt(block.getX(), block.getY() - 1, block.getZ());
        long belowKey = LocationUtil.toKey(below.getLocation());
        Map<Long, Double> map = tracked.get(worldName);
        if (map != null) {
            Double belowStick = map.get(belowKey);
            if (belowStick != null && belowStick <= 0) {
                collapseColumn(block.getWorld(), block.getX(), block.getY() - 1, block.getZ());
            }
        }
    }

    /** Блок сломан (игроком, взрывом, поршнем и т.п.). */
    public void onBlockBroken(Block block) {
        if (!enabled || block == null) return;
        removeTracked(block.getWorld().getName(), LocationUtil.toKey(block.getLocation()));
    }

    /** Пакет блоков уничтожен взрывом. */
    public void onBlocksDestroyed(List<Block> blocks) {
        if (!enabled || blocks == null) return;
        for (Block b : blocks) {
            if (b != null) onBlockBroken(b);
        }
    }

    private void removeTracked(String worldName, long key) {
        Map<Long, Double> map = tracked.get(worldName);
        if (map != null && map.remove(key) != null) {
            deleteFromDb(worldName, key);
            dirtyKeys.remove(new DirtyKey(worldName, key));
        }
    }

    // =========================
    // TICK — трата липкости + обрушения
    // =========================

    @Override
    public void run() {
        if (!enabled) return;

        List<String> collapseWorlds = new ArrayList<>();
        List<long[]> collapsePos = new ArrayList<>();

        for (Map.Entry<String, Map<Long, Double>> we : tracked.entrySet()) {
            String worldName = we.getKey();
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            for (Map.Entry<Long, Double> e : we.getValue().entrySet()) {
                long key = e.getKey();
                int x = LocationUtil.getX(key);
                int y = LocationUtil.getY(key);
                int z = LocationUtil.getZ(key);

                // Не загружаем чанки ради механики — пропускаем выгруженные
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type == Material.AIR || !type.isSolid()) {
                    // блок исчез без BlockBreakEvent (взрыв и т.п.) — самоочистка
                    we.getValue().remove(key);
                    deleteFromDb(worldName, key);
                    dirtyKeys.remove(new DirtyKey(worldName, key));
                    continue;
                }

                // Опора: твёрдый блок прямо снизу (вертикальная, как у подмостков)
                Block below = world.getBlockAt(x, y - 1, z);
                if (below.getType().isSolid()) {
                    continue; // есть опора — липкость не тратится
                }

                double heavy = heaviness(type);
                double drain = heavy * rate * (intervalTicks / 20.0);
                double next = e.getValue() - drain;
                if (next <= 0) {
                    collapseWorlds.add(worldName);
                    collapsePos.add(new long[]{x, y, z});
                } else {
                    e.setValue(next);
                    dirtyKeys.add(new DirtyKey(worldName, key));
                }
            }
        }

        // Обрабатываем обрушения ПОСЛЕ итерации (избегаем ConcurrentModification)
        for (int i = 0; i < collapsePos.size(); i++) {
            World world = Bukkit.getWorld(collapseWorlds.get(i));
            if (world == null) continue;
            long[] c = collapsePos.get(i);
            collapseColumn(world, (int) c[0], (int) c[1], (int) c[2]);
        }

        // Периодический лор-скан инвентарей (контент-осознанный: только когда надо)
        tickCounter++;
        int loreEvery = Math.max(1, loreIntervalTicks / Math.max(1, intervalTicks));
        if (loreEnabled && tickCounter % loreEvery == 0) {
            scanLore();
        }

        // Периодическое сохранение в БД (чтобы не потерять при шатдауне)
        int saveEvery = Math.max(1, (saveIntervalSeconds * 20) / Math.max(1, intervalTicks));
        if (tickCounter % saveEvery == 0) {
            saveDirty();
        }
    }

    /**
     * Обрушение вертикальной колонны вверх: блок и все поставленные блоки
     * над ним падают предметами (как будто добыты незеритовым инструментом).
     */
    private void collapseColumn(World world, int x, int y, int z) {
        String worldName = world.getName();
        Map<Long, Double> map = tracked.get(worldName);
        int cy = y;
        int guard = 0;

        while (map != null && guard++ < 512) {
            long key = LocationUtil.toKey(x, cy, z);
            if (!map.containsKey(key)) {
                break; // не наш блок — не трогаем (например, натуральный камень)
            }

            Block block = world.getBlockAt(x, cy, z);
            if (block.getType() == Material.AIR || !block.getType().isSolid()) {
                map.remove(key);
                deleteFromDb(worldName, key);
                break;
            }

            for (ItemStack drop : collapseDrops(block)) {
                world.dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
            }
            world.playSound(block.getLocation().add(0.5, 0.5, 0.5),
                    block.getSoundGroup().getBreakSound(), 1.0f, 0.8f);

            block.setType(Material.AIR);
            map.remove(key);
            deleteFromDb(worldName, key);
            dirtyKeys.remove(new DirtyKey(worldName, key));
            cy++;
        }
    }

    /** Дропы при обрушении: как будто блок добыли незеритовым инструментом. */
    private List<ItemStack> collapseDrops(Block block) {
        Material type = block.getType();
        try {
            java.util.Collection<ItemStack> drops = block.getState().getDrops(netheriteTool(type), null);
            if (drops != null && !drops.isEmpty()) {
                return new ArrayList<>(drops);
            }
        } catch (Throwable ignored) {
            // API недоступен — fallback ниже
        }
        // Если блок ничего не дропает без шёлкового касания (стекло и т.п.) —
        // отдаём сам блок.
        return List.of(new ItemStack(type, 1));
    }

    private ItemStack netheriteTool(Material mat) {
        String n = mat.name();
        if (n.contains("WOOD") || n.contains("PLANK") || n.contains("LOG") || n.contains("FENCE")
                || n.contains("GATE") || n.contains("DOOR") || n.contains("TRAPDOOR")
                || n.contains("SIGN") || n.contains("BAMBOO") || n.contains("STEM")
                || n.contains("PUMPKIN") || n.contains("MELON")) {
            return new ItemStack(Material.NETHERITE_AXE);
        }
        if (n.contains("DIRT") || n.contains("SAND") || n.contains("GRAVEL") || n.contains("CLAY")
                || n.contains("MUD") || n.contains("SNOW") || n.contains("SOUL")
                || n.contains("GRASS") || n.contains("PODZOL") || n.contains("MYCELIUM")
                || n.contains("FARMLAND") || n.contains("PATH")) {
            return new ItemStack(Material.NETHERITE_SHOVEL);
        }
        return new ItemStack(Material.NETHERITE_PICKAXE);
    }

    // =========================
    // LORE — «Липкость/Тяжесть» в описании блочного предмета
    // =========================

    private void scanLore() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i <= 40; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() == Material.AIR) continue;
                Material type = item.getType();
                if (!isStructural(type)) continue;

                ItemMeta meta = item.getItemMeta();
                if (meta == null) continue;

                var pdc = meta.getPersistentDataContainer();
                if (pdc.has(Keys.BLOCK_COLLAPSE_TAG, PersistentDataType.DOUBLE)) continue;
                // Не трогаем кастомные предметы плагина (у них есть PDC-теги)
                if (!pdc.isEmpty()) continue;

                double heavy = heaviness(type);
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(MessageUtil.parse("<gray>Липкость: <white>100</white></gray>"));
                lore.add(MessageUtil.parse("<gray>Тяжесть: <yellow>" + formatHeaviness(heavy) + "</yellow></gray>"));
                meta.lore(lore);
                pdc.set(Keys.BLOCK_COLLAPSE_TAG, PersistentDataType.DOUBLE, heavy);
                item.setItemMeta(meta);
            }
        }
    }

    private static String formatHeaviness(double h) {
        if (h == Math.floor(h)) {
            return String.valueOf((long) h);
        }
        return String.format(Locale.ROOT, "%.1f", h);
    }

    // =========================
    // 💾 БД (периодический сброс + при шатдауне)
    // =========================

    private void loadFromDb() {
        if (!DatabaseManager.isConnected()) return;
        String sql = "SELECT world, x, y, z, stickiness FROM " + DB_TABLE;
        int count = 0;
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {

            while (rs.next()) {
                String worldName = rs.getString("world");
                long key = LocationUtil.toKey(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                tracked.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                        .put(key, rs.getDouble("stickiness"));
                count++;
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Failed to load state from DB: " + e.getMessage());
        }
        if (count > 0) {
            ConsoleLogger.info("[BLOCK_COLLAPSE] Loaded " + count + " tracked block(s) from DB.");
        }
    }

    private void saveDirty() {
        if (!DatabaseManager.isConnected() || dirtyKeys.isEmpty()) return;

        String sql = "INSERT OR REPLACE INTO " + DB_TABLE
                + " (world, x, y, z, stickiness) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {

            for (DirtyKey dk : dirtyKeys) {
                Map<Long, Double> map = tracked.get(dk.world());
                if (map == null) continue;
                Double stick = map.get(dk.key());
                if (stick == null) continue; // блок удалён за это время

                st.setString(1, dk.world());
                st.setInt(2, LocationUtil.getX(dk.key()));
                st.setInt(3, LocationUtil.getY(dk.key()));
                st.setInt(4, LocationUtil.getZ(dk.key()));
                st.setDouble(5, stick);
                st.addBatch();
            }
            st.executeBatch();
            dirtyKeys.clear();

        } catch (Exception e) {
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Failed to save state to DB: " + e.getMessage());
        }
    }

    private void deleteFromDb(String worldName, long key) {
        if (!DatabaseManager.isConnected()) return;
        String sql = "DELETE FROM " + DB_TABLE + " WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, worldName);
            st.setInt(2, LocationUtil.getX(key));
            st.setInt(3, LocationUtil.getY(key));
            st.setInt(4, LocationUtil.getZ(key));
            st.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.warn("[BLOCK_COLLAPSE] Failed to delete from DB: " + e.getMessage());
        }
    }

    /** Ключ «изменился с прошлого сохранения»: мир + координаты блока. */
    private record DirtyKey(String world, long key) {}
}
