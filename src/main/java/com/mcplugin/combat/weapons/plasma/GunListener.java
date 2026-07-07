package com.mcplugin.combat.weapons.plasma;

import com.mcplugin.core.Keys;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.UUID;

public class GunListener implements Listener {

    // =========================
    // COOLDOWN
    // =========================
    private static final long COOLDOWN_TICKS = 80;
    private final HashMap<UUID, Long> cooldown = new HashMap<>();

    @EventHandler
    public void onUse(PlayerInteractEvent e) {

        Action action = e.getAction();

        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player p = e.getPlayer();

        ItemStack hand = p.getInventory().getItemInMainHand();

        if (hand == null
                || hand.getType() != Material.WARPED_FUNGUS_ON_A_STICK) {
            return;
        }

        // =========================
        // PDC CHECK (PLASMA ONLY)
        // =========================
        if (!isPlasma(hand)) {
            return;
        }

        // =========================
        // AMMO CHECK
        // =========================
        ItemStack ammo = p.getInventory().getItemInOffHand();

        if (ammo == null
                || ammo.getType() != Material.ECHO_SHARD
                || ammo.getAmount() <= 0) {

            p.sendActionBar("§7[§dPLASMA§7] §cNo ammo");
            return;
        }

        int ammoCount = ammo.getAmount();

        // =========================
        // COOLDOWN
        // =========================
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (cooldown.containsKey(id)) {

            long last = cooldown.get(id);
            long passedTicks = (now - last) / 50;

            if (passedTicks < COOLDOWN_TICKS) {

                double left = (COOLDOWN_TICKS - passedTicks) / 20.0;

                p.sendActionBar(
                        "§7[§6RELOAD§7] §c"
                                + String.format("%.1f", left)
                                + "s §8| §7Ammo: §e"
                                + ammoCount
                );
                return;
            }
        }

        cooldown.put(id, now);

        // =========================
        // CONSUME AMMO
        // =========================
        if (ammoCount <= 1) {
            p.getInventory().setItemInOffHand(null);
        } else {
            ammo.setAmount(ammoCount - 1);
        }

        // =========================
        // SHOOT
        // =========================
        PlasmaProjectile.spawn(p);

        p.playSound(
                p.getLocation(),
                Sound.ENTITY_BLAZE_SHOOT,
                1.2f,
                1.1f
        );

        p.sendActionBar(
                "§7[§dPLASMA§7] §fShot §8| §7Ammo: §e"
                        + (ammoCount - 1)
        );
    }

    // =========================
    // PDC CHECK (FIXED)
    // =========================
    private boolean isPlasma(ItemStack item) {

        // In Paper 1.21.4+ hasItemMeta() returns false for fresh items.
        // getItemMeta() always returns non-null for non-AIR items.
        if (item == null || item.getType() == Material.AIR) return false;

        var meta = item.getItemMeta();
        if (meta == null) return false;

        Byte val = meta.getPersistentDataContainer().get(
                Keys.PLASMA,
                PersistentDataType.BYTE
        );

        return val != null && val == (byte) 1;
    }
}