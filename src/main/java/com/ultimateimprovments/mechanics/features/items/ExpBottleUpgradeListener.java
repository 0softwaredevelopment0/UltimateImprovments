package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 🧪 XP Bottle Upgrade — «заряженные» пузырьки опыта.
 * <p>
 * <b>Механика:</b>
 * <ul>
 *   <li>Наковальня: 2 одинаковых пузырька опыта (обычный или xN) → 1 пузырёк x(N+1).
 *       Обычный + обычный = x2, x2 + x2 = x3, x3 + x3 = x4 и т.д.</li>
 *   <li>Множитель хранится в PDC {@code EXP_BOTTLE_MULTIPLIER} (int). Обычный пузырёк — без тега (= x1).</li>
 *   <li>При броске x-пузырёк даёт в N раз больше опыта: стандартные 3–11 XP умножаются на N.</li>
 *   <li>Так как {@code ExpBottleEvent} удалён в 1.21.2+, перехват идёт через
 *       {@link ProjectileLaunchEvent} (запоминаем полёт) + {@link ProjectileHitEvent}
 *       (отменяем ванильное разбитие, спавним свои орбы опыта ×N).</li>
 * </ul>
 */
public class ExpBottleUpgradeListener implements Listener {

    // =========================
    // КОНФИГ (features.exp_bottle_upgrade)
    // =========================
    private static boolean enabled = true;
    private static int maxMultiplier = 100;
    private static int anvilCostAmount = 1;
    private static String itemNameFormat = "<yellow>Пузырёк опыта <gold>x{level}</gold></yellow>";
    private static List<String> itemLoreFormats = List.of(
            "<gray>Даёт в <aqua>{level}</aqua> раз больше опыта</gray>",
            "<dark_gray>{level} + {level} = <aqua>x{next}</aqua> на наковальне</dark_gray>"
    );

    // =========================
    // ОТСЛЕЖИВАНИЕ ПОЛЁТА: entityId → множитель
    // =========================
    private static final Map<UUID, Tracked> TRACKED = new ConcurrentHashMap<>();
    private static final long TRACK_TTL_MS = 5 * 60 * 1000L; // 5 минут — максимум жизни снаряда

    private static final class Tracked {
        final int multiplier;
        final long expiresAt;

        Tracked(int multiplier, long expiresAt) {
            this.multiplier = multiplier;
            this.expiresAt = expiresAt;
        }
    }

    // =========================
    // CONFIG
    // =========================
    public static void loadConfig(Main plugin) {
        var cfg = plugin.getConfig().getConfigurationSection("features.exp_bottle_upgrade");
        if (cfg == null) {
            // Секция отсутствует — используем дефолты
            enabled = true;
            maxMultiplier = 100;
            anvilCostAmount = 1;
            itemNameFormat = "<yellow>Пузырёк опыта <gold>x{level}</gold></yellow>";
            itemLoreFormats = List.of(
                    "<gray>Даёт в <aqua>{level}</aqua> раз больше опыта</gray>",
                    "<dark_gray>{level} + {level} = <aqua>x{next}</aqua> на наковальне</dark_gray>"
            );
            return;
        }

        enabled = cfg.getBoolean("enabled", true);
        maxMultiplier = Math.max(1, cfg.getInt("max_multiplier", 100));
        anvilCostAmount = Math.max(0, cfg.getInt("anvil_cost_amount", 1));
        itemNameFormat = cfg.getString("item_name", itemNameFormat);
        if (itemNameFormat == null || itemNameFormat.isBlank()) {
            itemNameFormat = "<yellow>Пузырёк опыта <gold>x{level}</gold></yellow>";
        }

        List<String> lore = cfg.getStringList("item_lore");
        if (lore != null && !lore.isEmpty()) {
            itemLoreFormats = new ArrayList<>(lore);
        }
    }

    // =========================
    // НАКОВАЛЬНЯ: пузырёк + такой же пузырёк → пузырёк x(N+1)
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!enabled) return;

        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() != Material.EXPERIENCE_BOTTLE) return;
        if (slot1 == null || slot1.getType() != Material.EXPERIENCE_BOTTLE) return;

        // Рецепт строго из 2 единиц (не стаки)
        if (slot0.getAmount() != 1 || slot1.getAmount() != 1) return;

        int level0 = getMultiplier(slot0);
        int level1 = getMultiplier(slot1);

        // Комбинируются только пузырьки ОДНОГО уровня: x1+x1→x2, x2+x2→x3, ...
        if (level0 != level1) return;
        if (level0 >= maxMultiplier) return; // достигнут потолок

        int newLevel = level0 + 1;

        ItemStack result = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(
                Keys.EXP_BOTTLE_MULTIPLIER, PersistentDataType.INTEGER, newLevel
        );
        meta.displayName(MessageUtil.parse(
                itemNameFormat.replace("{level}", String.valueOf(newLevel))
        ));

        List<Component> lore = new ArrayList<>();
        boolean canUpgradeFurther = newLevel < maxMultiplier;
        for (String line : itemLoreFormats) {
            // Строку-подсказку про крафт показываем только если апгрейд ещё возможен
            if (line.contains("{next}") && !canUpgradeFurther) continue;
            lore.add(MessageUtil.parse(line
                    .replace("{level}", String.valueOf(newLevel))
                    .replace("{next}", String.valueOf(newLevel + 1))));
        }
        meta.lore(lore);
        result.setItemMeta(meta);

        event.setResult(result);
        setAnvilCost(inv, 0, anvilCostAmount);
    }

    // =========================
    // БРОСОК: запоминаем x-пузырёк в полёте
    // =========================
    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof ThrownExpBottle bottle)) return;

        purgeExpired();

        int multiplier = getMultiplier(bottle.getItem());
        if (multiplier > 1) {
            TRACKED.put(bottle.getUniqueId(),
                    new Tracked(multiplier, System.currentTimeMillis() + TRACK_TTL_MS));
        }
    }

    // =========================
    // РАЗБИТИЕ: отменяем ванильное, спавним орбы опыта ×N
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ThrownExpBottle bottle)) return;

        Tracked tracked = TRACKED.remove(bottle.getUniqueId());
        if (tracked == null) return;

        event.setCancelled(true);
        bottle.remove();

        Location loc = bottle.getLocation();

        // Ванильная формула: 3 + random(5) + random(5) = 3..11 XP
        int base = 3 + ThreadLocalRandom.current().nextInt(5) + ThreadLocalRandom.current().nextInt(5);
        long xpLong = (long) base * tracked.multiplier;
        int xp = (int) Math.min(Integer.MAX_VALUE, Math.max(1, xpLong));

        bottle.getWorld().spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));

        // Эффект разбития стекла (1.21.4: ITEM_BREAK переименован в ITEM)
        bottle.getWorld().spawnParticle(
                Particle.ITEM,
                loc.clone().add(0, 0.2, 0),
                12, 0.2, 0.2, 0.2, 0.05,
                new ItemStack(Material.EXPERIENCE_BOTTLE)
        );
        bottle.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.0f);
    }

    // =========================
    // УДАЛЕНИЕ СНАРЯДА: сразу чистим карту (защита от утечек)
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof ThrownExpBottle) {
            TRACKED.remove(event.getEntity().getUniqueId());
        }
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Возвращает множитель пузырька: 1 для обычного, N для xN.
     */
    public static int getMultiplier(ItemStack item) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE) return 1;
        if (!item.hasItemMeta()) return 1;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return Math.max(1, pdc.getOrDefault(Keys.EXP_BOTTLE_MULTIPLIER, PersistentDataType.INTEGER, 1));
    }

    /** Вычищает протухшие записи полёта (защита от утечек памяти). */
    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Tracked>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Tracked> e = it.next();
            if (e.getValue().expiresAt < now) {
                it.remove();
            }
        }
    }

    /**
     * Устанавливает стоимость наковальни через API (с fallback на reflection).
     */
    private static void setAnvilCost(AnvilInventory inv, int repairCost, int repairCostAmount) {
        try {
            inv.setRepairCost(repairCost);
            inv.setRepairCostAmount(repairCostAmount);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            try {
                Field costField = inv.getClass().getDeclaredField("repairCost");
                costField.setAccessible(true);
                costField.set(inv, repairCost);
            } catch (Exception ignored) {}
        }
    }
}
