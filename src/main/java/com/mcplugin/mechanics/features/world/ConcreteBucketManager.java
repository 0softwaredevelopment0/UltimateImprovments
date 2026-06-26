package com.mcplugin.mechanics.features.world;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 🧊 Управляет механикой "Ведра бетона".
 * <p>
 * При выливании Concrete Bucket:
 * <ol>
 *   <li>Вода окрашивается в серый цвет через установку биома {@code PALE_GARDEN}</li>
 *   <li>Исходный биом сохраняется для восстановления</li>
 *   <li>Если вода стоит &gt; 60 секунд → превращается в булыжник (бетон)</li>
 *   <li>Если вода исчезает раньше → биом восстанавливается</li>
 * </ol>
 */
public class ConcreteBucketManager extends BukkitRunnable implements Listener {

    private static ConcreteBucketManager instance;
    private static final long CONCRETE_DELAY_MS = 60_000; // 60 секунд

    // Отслеживаемые блоки воды: location -> {originalBiome, placeTime}
    private static final Map<Location, ConcreteWater> trackedWater = new HashMap<>();

    private record ConcreteWater(Biome originalBiome, long placeTime) {}

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        instance = new ConcreteBucketManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        // Проверяем каждый тик (плавное превращение)
        instance.runTaskTimer(plugin, 20L, 20L);
        plugin.getLogger().info("[ConcreteBucket] Initialized");
    }

    /**
     * Проверяет, является ли предмет Concrete Bucket.
     */
    private static boolean isConcreteBucket(ItemStack item) {
        if (item == null || item.getType() != Material.WATER_BUCKET) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.CONCRETE_BUCKET, PersistentDataType.BYTE);
    }

    // =========================
    // 🪣 ВЫЛИВАНИЕ — игрок использует ведро
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        // В Paper 26.x getBucket() возвращает Material, проверяем PDC из предмета в руке
        if (e.getBucket() != Material.WATER_BUCKET) return;

        // Проверяем PDC на предмете в руке игрока
        ItemStack mainHand = e.getPlayer().getInventory().getItemInMainHand();
        if (!isConcreteBucket(mainHand)) {
            ItemStack offHand = e.getPlayer().getInventory().getItemInOffHand();
            if (!isConcreteBucket(offHand)) return;
        }

        // Позиция, куда ставится вода (blockClicked + blockFace)
        Block clicked = e.getBlockClicked();
        if (clicked == null) return;

        Block waterBlock = clicked.getRelative(e.getBlockFace());
        Location waterLoc = waterBlock.getLocation();

        if (waterLoc.getWorld() == null) return;

        // Сохраняем исходный биом
        Biome originalBiome = waterBlock.getBiome();

        // Устанавливаем биом PALE_GARDEN — вода становится серой
        waterBlock.setBiome(Biome.PALE_GARDEN);

        // Отслеживаем блок воды
        trackedWater.put(waterLoc, new ConcreteWater(originalBiome, System.currentTimeMillis()));

        // 🪣 Сервер Paper сам заменяет WATER_BUCKET на обычный BUCKET после этого события.
        // PDC теряется при замене, поэтому ведро становится обычным.
        // Комментарий оставлен намеренно, чтобы не добавлять ручную замену (это приведёт к дублированию).
    }

    // =========================
    // 💧 РАСТЕКАНИЕ ВОДЫ — новая вода тоже получает PALE_GARDEN биом
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        Block toBlock = e.getToBlock();
        Location toLoc = toBlock.getLocation();

        // Если вода течёт на блок, который уже был отслежен — это наш новый блок воды
        Block fromBlock = e.getBlock();
        Location fromLoc = fromBlock.getLocation();

        ConcreteWater fromData = trackedWater.get(fromLoc);
        if (fromData != null && toBlock.getType() == Material.WATER) {
            // Новая порция воды тоже получает серый биом
            toBlock.setBiome(Biome.PALE_GARDEN);
            trackedWater.put(toLoc, new ConcreteWater(fromData.originalBiome(), System.currentTimeMillis()));
        }

        // Если блок-назначение был отслеженной водой, но теперь заменяется чем-то другим
        // (например, лавой) — восстанавливаем биом
        ConcreteWater toData = trackedWater.remove(toLoc);
        if (toData != null && toBlock.getType() != Material.WATER) {
            restoreBiome(toLoc, toData.originalBiome());
        }
    }

    // =========================
    // 🔥 ИСЧЕЗНОВЕНИЕ ВОДЫ — физика блока изменилась
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent e) {
        Location loc = e.getBlock().getLocation();
        ConcreteWater data = trackedWater.get(loc);
        if (data == null) return;

        if (e.getBlock().getType() != Material.WATER) {
            trackedWater.remove(loc);
            restoreBiome(loc, data.originalBiome());
        }
    }

    // =========================
    // 🔨 БЛОК РАЗРУШЕН — если ломают воду (ведром или руками)
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        ConcreteWater data = trackedWater.remove(loc);
        if (data == null) return;
        restoreBiome(loc, data.originalBiome());
    }

    // =========================
    // ⏱ ТИК — проверка на превращение в бетон через 60 секунд
    // =========================
    @Override
    public void run() {
        if (trackedWater.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Location, ConcreteWater>> it = trackedWater.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Location, ConcreteWater> entry = it.next();
            Location loc = entry.getKey();
            ConcreteWater data = entry.getValue();

            // Всё ещё вода?
            if (loc.getBlock().getType() != Material.WATER) {
                it.remove();
                restoreBiome(loc, data.originalBiome());
                continue;
            }

            // Прошло 60 секунд?
            if (now - data.placeTime() >= CONCRETE_DELAY_MS) {
                // Превращаем в булыжник (бетон!)
                loc.getBlock().setType(Material.COBBLESTONE);
                it.remove();
                // Биом НЕ восстанавливаем — бетон окрасил землю
            }
        }
    }

    // =========================
    // 🔄 ВОССТАНОВЛЕНИЕ БИОМА
    // =========================
    private static void restoreBiome(Location loc, Biome biome) {
        if (biome != null && biome != Biome.PALE_GARDEN) {
            loc.getBlock().setBiome(biome);
        }
    }

    // =========================
    // 🧹 SHUTDOWN
    // =========================
    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
        trackedWater.clear();
    }
}
