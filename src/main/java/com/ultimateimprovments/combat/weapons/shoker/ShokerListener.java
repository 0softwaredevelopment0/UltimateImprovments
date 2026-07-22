package com.ultimateimprovments.combat.weapons.shoker;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.util.MessageUtil;
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

public class ShokerListener implements Listener {

    // =========================
    // COOLDOWN
    // =========================
    private static final long COOLDOWN_TICKS = 80;
    private static final HashMap<UUID, Long> cooldown = new HashMap<>();

    @EventHandler
    public void onUse(PlayerInteractEvent e) {

        Action action = e.getAction();

        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player p = e.getPlayer();

        ItemStack hand = p.getInventory().getItemInMainHand();

        if (hand == null || hand.getType() != Material.WARPED_FUNGUS_ON_A_STICK) {
            return;
        }

        // =========================
        // PDC CHECK (SHOCKER ONLY)
        // =========================
        if (!isShoker(hand)) {
            return;
        }

        // =========================
        // AMMO CHECK
        // =========================
        ItemStack ammo = p.getInventory().getItemInOffHand();

        if (ammo == null
                || ammo.getType() != Material.BREEZE_ROD
                || ammo.getAmount() <= 0) {

            p.sendActionBar(MessageUtil.parse("<gray>[<dark_red>SHOCKER</dark_red>] <red>No ammo</red></gray>"));
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

                p.sendActionBar(MessageUtil.parse(
                        "<gray>[<gold>RELOAD</gold>] <red>"
                                + String.format("%.1f", left)
                                + "s</red> <dark_gray>|</dark_gray> <gray>Ammo: </gray><yellow>"
                                + ammoCount
                                + "</yellow></gray>"
                ));
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
        ShokerProjectile.spawn(p);

        p.getWorld().playSound(
                p.getLocation(),
                Sound.ENTITY_WITHER_SHOOT,
                1f,
                1f
        );

        p.sendActionBar(MessageUtil.parse(
                "<gray>[<aqua>SHOCKER</aqua>] <white>Shot</white> <dark_gray>|</dark_gray> <gray>Ammo: </gray><yellow>"
                        + (ammoCount - 1)
                        + "</yellow></gray>"
        ));
    }

    // =========================
    // PDC CHECK (FIXED)
    // =========================
    private boolean isShoker(ItemStack item) {

        // In Paper 1.21.4+ hasItemMeta() returns false for fresh items.
        // getItemMeta() always returns non-null for non-AIR items.
        if (item == null || item.getType() == Material.AIR) return false;

        var meta = item.getItemMeta();
        if (meta == null) return false;

        Byte val = meta.getPersistentDataContainer().get(
                Keys.SHOCKER,
                PersistentDataType.BYTE
        );

        return val != null && val == (byte) 1;
    }
}