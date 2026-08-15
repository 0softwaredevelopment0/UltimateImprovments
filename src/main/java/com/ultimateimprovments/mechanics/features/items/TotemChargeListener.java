package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * ⚡ Totem Charge System
 * <p>
 * <b>Mechanics:</b>
 * <ul>
 *   <li>Totem + netherite scrap on an anvil → +1 charge</li>
 *   <li>Using the totem spends 1 charge instead of the item</li>
 *   <li>If charge = 0 — the totem does not work</li>
 *   <li>Without PDC {@code TOTEM_CHARGE} — vanilla behavior (item is consumed)</li>
 *   <li>On fatal damage the totem triggers BEFORE death (via EntityDamageEvent)</li>
 *   <li>Totem lore is auto-added on any acquisition path (pickup, click, join, inventory open + a 5s task)</li>
 * </ul>
 */
public class TotemChargeListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    // =========================
    // ANVIL: totem + netherite scrap → +1 charge
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

        ItemStack result = slot0.clone();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) return;

        var resultPdc = resultMeta.getPersistentDataContainer();
        resultPdc.set(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, newCharge);

        updateChargeLore(resultMeta, newCharge);
        result.setItemMeta(resultMeta);

        event.setResult(result);
        setAnvilCost(inv, 0, 1);
    }

    // =========================
    // ⚡ FATAL DAMAGE: intercept BEFORE death
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFatalDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;

        double finalDamage = event.getFinalDamage();
        double health = player.getHealth();

        if (health - finalDamage > 0) return;

        ItemStack totem = findUsableTotem(player);
        if (totem == null) return;

        event.setCancelled(true);
        activateTotem(player, totem);
    }

    private static ItemStack findUsableTotem(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isUsableTotem(offhand)) return offhand;

        ItemStack mainhand = player.getInventory().getItemInMainHand();
        if (isUsableTotem(mainhand)) return mainhand;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isUsableTotem(item)) return item;
        }

        return null;
    }

    private static boolean isUsableTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return false;
        if (!item.hasItemMeta()) return true;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER)) return true;
        int charge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 0);
        return charge > 0;
    }

    // =========================
    // TOTEM USE: spend a charge instead of the item
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

        if (!pdc.has(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER)) return;

        int charge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 0);

        if (charge <= 0) {
            event.setCancelled(true);
            updateChargeLore(meta, charge);
            totem.setItemMeta(meta);
            return;
        }

        event.setCancelled(true);

        charge--;
        pdc.set(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, charge);

        updateChargeLore(meta, charge);
        totem.setItemMeta(meta);

        applyTotemEffects(player);
    }

    // =========================
    // TOTEM ACTIVATION: consume a charge + effects
    // =========================
    private static void activateTotem(Player player, ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();

        if (meta != null && meta.getPersistentDataContainer()
                .has(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER)) {

            var pdc = meta.getPersistentDataContainer();
            int charge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 0);
            charge--;
            pdc.set(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, charge);

            updateChargeLore(meta, charge);
            totem.setItemMeta(meta);
        } else {
            if (totem.getAmount() > 1) {
                totem.setAmount(totem.getAmount() - 1);
            } else {
                totem.setAmount(0);
            }
        }

        applyTotemEffects(player);
    }

    // =========================
    // TOTEM EFFECTS
    // =========================
    private static void applyTotemEffects(Player player) {
        player.setHealth(1.0);

        for (var effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));

        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0.5
        );
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
    }

    // =========================
    // LORE
    // =========================
    private static void updateChargeLore(ItemMeta meta, int charge) {
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        lore.removeIf(c -> PLAIN.serialize(c).contains("Charge:"));

        lore.add(MessageUtil.parse("<!italic><white>Charge: <gray>" + charge));

        meta.lore(lore);
    }

    // =========================
    // ITEM PICKUP
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack item = event.getItem().getItemStack();
        if (fixSingleTotemLore(item)) {
            event.getItem().setItemStack(item);
        }
    }

    // =========================
    // INVENTORY OPEN
    // =========================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // When any inventory opens, check totems in the player's inventory
        ensureTotemLore(player);
    }

    // =========================
    // INVENTORY CLICK
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        if (current != null && fixSingleTotemLore(current)) {
            event.setCurrentItem(current);
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && fixSingleTotemLore(cursor)) {
            event.setCursor(cursor);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack oldCursor = event.getOldCursor();
        if (oldCursor != null && fixSingleTotemLore(oldCursor)) {
        }
    }

    // =========================
    // PLAYER JOIN
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ensureTotemLore(event.getPlayer());
    }

    // =========================
    // UTILITIES
    // =========================

    private static boolean fixSingleTotemLore(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();

        if (!pdc.has(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER)) return false;

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
                if (hasChargeLine) return false;
            }
        }

        int charge = pdc.getOrDefault(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 0);
        updateChargeLore(meta, charge);
        item.setItemMeta(meta);
        return true;
    }

    private static void ensureTotemLore(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            fixSingleTotemLore(item);
        }
        for (ItemStack item : player.getInventory().getExtraContents()) {
            fixSingleTotemLore(item);
        }
    }

    // =========================
    // PERIODIC TASK (every 5s — defence-in-depth)
    // =========================
    public static void startPeriodicLoreCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ensureTotemLore(player);
                }
            }
        }.runTaskTimer(Main.getInstance(), 100L, 100L); // every 5 seconds
    }

    // =========================
    // ANVIL COST
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
