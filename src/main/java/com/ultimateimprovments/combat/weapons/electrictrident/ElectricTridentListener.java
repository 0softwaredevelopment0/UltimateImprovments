package com.ultimateimprovments.combat.weapons.electrictrident;

import com.ultimateimprovments.core.Keys;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Combat listener for the Electric Trident.
 * <p>
 * Only a THROWN Electric Trident that lands on an entity strikes a real
 * lightning bolt at the victim's location — exactly one bolt per impact, on
 * top of the trident's own normal damage. Melee swings deal the vanilla
 * trident damage with no lightning.
 */
public class ElectricTridentListener implements Listener {

    /**
     * Thrown hit: the trident projectile lands on an entity.
     * The thrown trident's own damage still applies — we only add one lightning bolt.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrownHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Trident trident)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!victim.isValid() || victim.isDead()) return;

        if (!isElectricTrident(trident.getItem())) return;

        // Real lightning bolt at the victim's position (deals its own damage + fire).
        victim.getWorld().strikeLightning(victim.getLocation());
    }

    // =========================
    // PDC CHECK
    // =========================
    private boolean isElectricTrident(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        var meta = item.getItemMeta();
        if (meta == null) return false;

        Byte val = meta.getPersistentDataContainer().get(
                Keys.ELECTRIC_TRIDENT,
                PersistentDataType.BYTE
        );

        return val != null && val == (byte) 1;
    }
}
