package com.mcplugin.mechanics.features.items;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * ⚡ Totem Charge System
 * <p>
 * <b>Механика:</b>
 * <ul>
 *   <li>Тотем + незеритовый скрап на наковальне → +1 заряд (Charge)</li>
 *   <li>При использовании тотема тратится 1 заряд вместо предмета</li>
 *   <li>Если заряд = 0 — тотем не работает</li>
 *   <li>Без PDC {@code TOTEM_CHARGE} — ванильное поведение (предмет потребляется)</li>
 *   <li>При фатальном уроне тотем срабатывает ДО смерти (через EntityDamageEvent)</li>
 *   <li>Лор тотема авто-добавляется, если отсутствует (даже при charge=0)</li>
 * </ul>
 */
public class TotemChargeListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    // =========================
    // НАКОВАЛЬНЯ: тотем + незеритовый скрап → +1 заряд
    // =========================
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() != Material.TOTEM_OF_UNDYING) return;
        if (slot1 == null || slot1.getType() != Material.NETHERITE_SCRAP) return;

        ItemMeta meta = slot0.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        int currentCharge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 0);
        int newCharge = currentCharge + 1;

        // Clone and apply charge
        ItemStack result = slot0.clone();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) return;

        var resultPdc = resultMeta.getPersistentDataContainer();
        resultPdc.set(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, newCharge);

        // Update lore
        updateChargeLore(resultMeta, newCharge);
        result.setItemMeta(resultMeta);

        event.setResult(result);
        setAnvilCost(inv, 0, 1);
    }

    // =========================
    // ⚡ ФАТАЛЬНЫЙ УРОН: перехватываем ДО смерти
    // =========================
    /**
     * Перехватывает фатальный урон ДО того, как игрок умрёт.
     * Если у игрока есть тотем с зарядом > 0 — отменяем урон и активируем тотем.
     * Это предотвращает лимбо, взрывы эндер-сундуков и другой фатальный урон.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFatalDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        // Игнорируем VOID — void_protection обрабатывает отдельно
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;

        // Расчёт финального урона (с учётом брони, резистенса и т.д.)
        double finalDamage = event.getFinalDamage();
        double health = player.getHealth();

        // Урон не фатальный — пропускаем
        if (health - finalDamage > 0) return;

        // Ищем тотем в инвентаре (offhand → mainhand → остальные слоты)
        ItemStack totem = findChargedTotem(player);
        if (totem == null) return;

        // Отменяем урон — смерти не будет
        event.setCancelled(true);

        // Активируем тотем (списываем заряд, эффекты)
        activateTotem(player, totem);
    }

    /**
     * Ищет тотем с зарядом > 0 в инвентаре игрока.
     * Приоритет: offhand → mainhand → остальные слоты.
     */
    private static ItemStack findChargedTotem(Player player) {
        // Сначала offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isChargedTotem(offhand)) return offhand;

        // Потом mainhand
        ItemStack mainhand = player.getInventory().getItemInMainHand();
        if (isChargedTotem(mainhand)) return mainhand;

        // Остальные слоты (hotbar + storage)
        for (ItemStack item : player.getInventory().getContents()) {
            if (isChargedTotem(item)) return item;
        }

        return null;
    }

    /**
     * Проверяет, является ли предмет тотемом с зарядом > 0.
     */
    private static boolean isChargedTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return false;
        if (!item.hasItemMeta()) return false;

        var pdc = item.getItemMeta().getPersistentDataContainer();
        int charge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, -1);

        // charge = -1 означает, что PDC нет — ванильный тотем, пусть работает как обычно
        if (charge == -1) return false;
        return charge > 0;
    }

    // =========================
    // ИСПОЛЬЗОВАНИЕ ТОТЕМА: тратим заряд вместо предмета
    // =========================
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        var hand = event.getHand();
        if (hand == null) return;

        ItemStack totem = player.getInventory().getItem(hand);
        if (totem == null || totem.getType() != Material.TOTEM_OF_UNDYING) return;

        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        // Нет PDC — ванильное поведение (предмет потребляется)
        if (!pdc.has(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER)) return;

        int charge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 0);

        if (charge <= 0) {
            // Заряда нет — тотем не работает, НО обновляем лор
            event.setCancelled(true);
            updateChargeLore(meta, charge);
            totem.setItemMeta(meta);
            return;
        }

        // Заряд есть — отменяем ванильное потребление, обрабатываем сами
        event.setCancelled(true);

        // Уменьшаем заряд (всегда сохраняем PDC, даже 0 — чтобы при charge=0
        // тотем НЕ потреблялся, а просто не срабатывал)
        charge--;
        pdc.set(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, charge);

        // Обновляем лор
        updateChargeLore(meta, charge);
        totem.setItemMeta(meta);

        // Ручное применение эффектов тотема
        applyTotemEffects(player);
    }

    // =========================
    // АКТИВАЦИЯ ТОТЕМА: списать заряд + эффекты
    // =========================
    /**
     * Активирует тотем: списывает 1 заряд, обновляет мету и применяет эффекты.
     * Используется из onFatalDamage и onEntityResurrect.
     */
    private static void activateTotem(Player player, ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        int charge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 0);
        charge--;
        pdc.set(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, charge);

        updateChargeLore(meta, charge);
        totem.setItemMeta(meta);

        applyTotemEffects(player);
    }

    // =========================
    // ЭФФЕКТЫ ТОТЕМА (как у ванильного)
    // =========================
    private static void applyTotemEffects(Player player) {
        // Здоровье
        player.setHealth(1.0);

        // Очистка негативных эффектов
        for (var effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Баффы
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));   // Regeneration II, 45 сек
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));      // Absorption II, 5 сек
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0)); // Fire Resistance I, 40 сек

        // Партиклы и звук
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0.5
        );
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
    }

    // =========================
    // LORE — обновление строки заряда
    // =========================
    private static void updateChargeLore(ItemMeta meta, int charge) {
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        // Удаляем старые строки Charge:
        lore.removeIf(c -> PLAIN.serialize(c).contains("Charge:"));

        // Добавляем новую
        lore.add(MessageUtil.parse("<!italic><white>Charge: <gray>" + charge));

        meta.lore(lore);
    }

    // =========================
    // АВТО-ЛОР ПРИ ВХОДЕ В ИГРУ
    // =========================
    /**
     * Сканирует инвентарь игрока при входе и добавляет лор тотемам,
     * у которых есть PDC TOTEM_CHARGE, но нет строки "Charge:" в лоре.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ensureTotemLore(event.getPlayer());
    }

    // =========================
    // АВТО-ЛОР ПРИ КЛИКЕ В ИНВЕНТАРЕ
    // =========================
    /**
     * При клике по тотему в инвентаре — проверяем и фиксим лор.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Проверяем кликнутый предмет
        ItemStack current = event.getCurrentItem();
        if (current != null && fixSingleTotemLore(current)) {
            event.setCurrentItem(current);
        }

        // Проверяем предмет на курсоре
        ItemStack cursor = event.getCursor();
        if (cursor != null && fixSingleTotemLore(cursor)) {
            event.setCursor(cursor);
        }
    }

    /**
     * При drag-перемещении — проверяем предмет.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack oldCursor = event.getOldCursor();
        if (oldCursor != null && fixSingleTotemLore(oldCursor)) {
            // Old cursor не меняем — он уже не в руке
        }
    }

    // =========================
    // УТИЛИТЫ: проверка/фикс лора тотема
    // =========================

    /**
     * Проверяет, нужно ли обновить лор тотема, и если да — обновляет.
     * Возвращает true, если лор был изменён.
     */
    private static boolean fixSingleTotemLore(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();

        if (!pdc.has(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER)) return false;

        // Проверяем, есть ли уже строка Charge: в лоре
        if (meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                boolean hasChargeLine = false;
                for (Component c : lore) {
                    if (PLAIN.serialize(c).contains("Charge:")) {
                        hasChargeLine = true;
                        break;
                    }
                }
                if (hasChargeLine) return false; // Лор уже есть — ничего не делаем
            }
        }

        // Лора нет — добавляем
        int charge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 0);
        updateChargeLore(meta, charge);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Сканирует весь инвентарь игрока и фиксит лор у всех тотемов.
     */
    private static void ensureTotemLore(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            fixSingleTotemLore(item);
        }
        // Also check armor/offhand
        for (ItemStack item : player.getInventory().getExtraContents()) {
            fixSingleTotemLore(item);
        }
    }

    // =========================
    // СТОИМОСТЬ НАКОВАЛЬНИ
    // =========================
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
