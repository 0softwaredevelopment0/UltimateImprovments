package com.mcplugin.mechanics.features.items;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.core.Main;
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
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
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
            // Заряда нет — тотем не работает
            event.setCancelled(true);
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
