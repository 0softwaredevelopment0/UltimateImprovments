package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.hooks.CoreHooks;

import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-click behaviour of the exp-split bottle created by {@code /ui expsplit}
 * (the bottle itself is owned by UI-Other, reached through
 * {@link CoreHooks#isExpBottle} / {@link CoreHooks#useExpBottle}).
 * <p>
 * The bottle is a real Bottle o' Enchanting, so vanilla would throw it and spawn
 * its own XP orbs (12–16 XP) while the bottle keeps a full bar inside. This
 * listener makes sure that <b>never</b> happens:
 * <ul>
 *   <li><b>Right-click</b> — the vanilla item use is denied
 *       ({@code useItemInHand = DENY}), so no bottle is thrown and no bottle is
 *       consumed by vanilla; the stored experience is released instead.
 *       Block interaction stays intact: a plain click on a chest still opens the
 *       chest, unpacking on a block requires sneaking (vanilla item-use rule).</li>
 *   <li><b>Projectile launch</b> — a thrown exp-split bottle (dispenser, plugin,
 *       leftover item) is cancelled, so no vanilla orb spawns.</li>
 *   <li><b>Bottle break</b> — if a projectile of ours still breaks, its vanilla
 *       XP is zeroed ({@link ExpBottleEvent#setExperience(int)}) as a last-resort
 *       guard against free XP and orb spam.</li>
 * </ul>
 * A short per-player cooldown absorbs duplicated use packets (main + off hand,
 * double clicks), which would otherwise unpack two bottles from one click.
 */
public class ExpSplitBottleListener implements Listener {

    /** Minimum time between two unpacks of the same player. */
    private static final long USE_COOLDOWN_MS = 250L;
    /** Above this many tracked players the stale entries are pruned. */
    private static final int PRUNE_THRESHOLD = 256;

    private static final Map<UUID, Long> LAST_USE = new ConcurrentHashMap<>();

    // =========================
    // RIGHT-CLICK: unpack, never throw
    // =========================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!CoreHooks.isExpBottle(item)) return;

        // Kill vanilla's bottle use (throw + XP orbs) but keep the block usable:
        // setCancelled() would also deny the interacted block (chests, doors).
        event.setUseItemInHand(Event.Result.DENY);

        // A click on a block belongs to the block (open the chest, press the
        // button, ...) — unpacking there needs sneaking, like any item use.
        if (action == Action.RIGHT_CLICK_BLOCK && !event.getPlayer().isSneaking()) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();

        // One click, one bottle: the off hand must not unpack a second bottle
        // when the main hand already holds one (the client sends both packets).
        if (hand == EquipmentSlot.OFF_HAND
                && CoreHooks.isExpBottle(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (isOnCooldown(player)) return;

        CoreHooks.useExpBottle(player, item, hand);
    }

    // =========================
    // THROW: never let the bottle leave the hand
    // =========================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownExpBottle bottle)) return;
        if (!CoreHooks.isExpBottle(bottle.getItem())) return;
        event.setCancelled(true);
    }

    // =========================
    // BREAK: last-resort guard — an exp-split bottle drops no vanilla XP
    // =========================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExpBottleBreak(ExpBottleEvent event) {
        if (!(event.getEntity() instanceof ThrownExpBottle bottle)) return;
        if (!CoreHooks.isExpBottle(bottle.getItem())) return;
        event.setShowEffect(false);
        event.setExperience(0);
    }

    // =========================
    // HELPERS
    // =========================

    /** Marks the use and returns true when the player used a bottle just now. */
    private static boolean isOnCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long previous = LAST_USE.get(player.getUniqueId());
        if (previous != null && now - previous < USE_COOLDOWN_MS) return true;

        if (LAST_USE.size() > PRUNE_THRESHOLD) {
            LAST_USE.values().removeIf(stamp -> now - stamp >= USE_COOLDOWN_MS * 40);
        }
        LAST_USE.put(player.getUniqueId(), now);
        return false;
    }
}
