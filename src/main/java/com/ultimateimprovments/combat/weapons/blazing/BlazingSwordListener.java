package com.ultimateimprovments.combat.weapons.blazing;

import com.ultimateimprovments.core.Keys;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Combat listener for the Blazing Sword.
 * <p>
 * When a player lands a melee hit with the Blazing Sword, the victim takes
 * 4 additional points of LAVA-type damage (on top of the normal hit) and is
 * set on fire for 10 seconds.
 */
public class BlazingSwordListener implements Listener {

    /** Additional lava damage applied on top of the vanilla melee hit. */
    private static final double LAVA_DAMAGE = 4.0;

    /** Burn duration: 10 seconds = 200 ticks. */
    private static final int FIRE_TICKS = 200;

    /**
     * Recursion guard — our own {@code victim.damage(...)} call re-fires
     * {@link EntityDamageByEntityEvent} synchronously and would otherwise
     * loop forever. Main-thread only, so a simple boolean is enough.
     */
    private boolean processing = false;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (processing) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!victim.isValid() || victim.isDead()) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isBlazingSword(weapon)) return;

        // Secondary guard: the original swing arrives as PLAYER_ATTACK, our own
        // added hit arrives as LAVA — never re-trigger on it.
        DamageSource incoming = event.getDamageSource();
        if (incoming != null && incoming.getDamageType() == DamageType.LAVA) return;

        processing = true;
        try {
            // Ignite for 10 seconds (never shorten an existing longer burn).
            victim.setFireTicks(Math.max(victim.getFireTicks(), FIRE_TICKS));

            // Additional lava-type damage, attributed to the attacker.
            DamageSource lava = DamageSource.builder(DamageType.LAVA)
                    .withCausingEntity(player)
                    .build();
            victim.damage(LAVA_DAMAGE, lava);
        } finally {
            processing = false;
        }
    }

    // =========================
    // PDC CHECK
    // =========================
    private boolean isBlazingSword(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        var meta = item.getItemMeta();
        if (meta == null) return false;

        Byte val = meta.getPersistentDataContainer().get(
                Keys.BLAZING_SWORD,
                PersistentDataType.BYTE
        );

        return val != null && val == (byte) 1;
    }
}
