package com.ultimateimprovments.combat.weapons.blazing;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat listener for the Blazing Sword.
 * <p>
 * When a player lands a melee hit with the Blazing Sword, the victim takes the
 * golden sword's own hit plus 4 additional normal (armor- and protection-reducible)
 * damage, and is then BURNED for 10 seconds: 1 normal damage per second (also
 * armor-reducible), with the "burning" hurt sound played for the victim —
 * applies to ANY living entity (players and mobs alike).
 * Re-hitting the victim refreshes the burn timer.
 */
public class BlazingSwordListener implements Listener {

    /** Additional normal damage applied on top of the vanilla melee hit (4 = 2 hearts). */
    private static final double EXTRA_DAMAGE = 4.0;

    /** Burn duration in seconds. */
    private static final int BURN_SECONDS = 10;

    /** Burn damage per second (1 = half heart). */
    private static final double BURN_DAMAGE = 1.0;

    /**
     * Recursion guard — our own {@code victim.damage(...)} calls re-fire
     * {@link EntityDamageByEntityEvent} synchronously and would otherwise
     * loop forever. Main-thread only, so a simple boolean is enough.
     */
    private boolean processing = false;

    /** Guard for the periodic burn ticks (also synchronous on the main thread). */
    private boolean applyingBurn = false;

    /** Active burns per victim UUID — re-hitting refreshes the burn instead of stacking. */
    private final Map<UUID, BurnState> activeBurns = new ConcurrentHashMap<>();

    /** One running burn on a victim. */
    private static final class BurnState {
        UUID attackerId;
        int secondsLeft;
        BukkitTask task;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (processing || applyingBurn) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!victim.isValid() || victim.isDead()) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isBlazingSword(weapon)) return;

        processing = true;
        try {
            // Additional normal damage, attributed to the attacker — reduced by
            // armor and protection enchantments like a regular weapon hit.
            DamageSource extra = DamageSource.builder(DamageType.PLAYER_ATTACK)
                    .withCausingEntity(player)
                    .build();
            victim.damage(EXTRA_DAMAGE, extra);
        } finally {
            processing = false;
        }

        // Instead of igniting, the victim burns for 10 seconds (1 normal dmg/sec).
        startBurn(victim, player);
    }

    /** Starts (or refreshes) the 10-second burn on the victim. */
    private void startBurn(LivingEntity victim, Player attacker) {
        UUID victimId = victim.getUniqueId();
        BurnState existing = activeBurns.get(victimId);
        if (existing != null && existing.task != null) {
            existing.task.cancel();
        }

        BurnState state = new BurnState();
        state.attackerId = attacker.getUniqueId();
        state.secondsLeft = BURN_SECONDS;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            if (!victim.isValid() || victim.isDead() || state.secondsLeft <= 0) {
                state.task.cancel();
                activeBurns.remove(victimId, state);
                return;
            }
            state.secondsLeft--;

            Player source = Bukkit.getPlayer(state.attackerId);
            DamageSource.Builder builder = DamageSource.builder(DamageType.PLAYER_ATTACK);
            if (source != null) builder.withCausingEntity(source);
            DamageSource burn = builder.build();

            applyingBurn = true;
            try {
                victim.damage(BURN_DAMAGE, burn);
            } finally {
                applyingBurn = false;
            }

            playBurnSound(victim);
        }, 20L, 20L);

        state.task = task;
        activeBurns.put(victimId, state);
    }

    /**
     * Plays the "burning" sound for ANY entity: players hear the hurt-on-fire
     * sound personally, all other entities (mobs etc.) emit the generic burn
     * sound at their location so the burn is noticeable on every victim.
     */
    private void playBurnSound(LivingEntity victim) {
        if (victim instanceof Player player) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.0f, 1.0f);
        } else {
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_BURN, 0.6f, 1.0f);
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
