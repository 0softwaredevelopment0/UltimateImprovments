package com.ultimateimprovments.enchantment.selfdestruct;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener: Curse of Self-Destruct — the countdown engine.
 * <p>
 * While a player carries the cursed item in ANY inventory slot (storage, armor,
 * offhand, main hand or cursor), a 10-second timer runs:
 * <ul>
 *   <li>smoke particles rise from the player every tick;</li>
 *   <li>a rising "beep" ({@link Sound#BLOCK_NOTE_BLOCK_BIT}) is heard by every
 *       player within {@value #BEEP_RADIUS} blocks — pitch climbs from
 *       {@value #PITCH_MIN} to {@value #PITCH_MAX} over the 10 seconds;</li>
 *   <li>when the timer ends, an <b>invisible ignited creeper</b> (explosion
 *       radius {@value #EXPLOSION_RADIUS}, 1-tick fuse) spawns at the player,
 *       explodes, and the cursed item is destroyed.</li>
 * </ul>
 * <p>
 * The item can NOT be removed while the timer runs — {@link InventoryLockListener}
 * cancels clicks, drags, drops, hand swaps and hopper extraction. If the player
 * logs out, the timer resets (it restarts on their next login).
 * <p>
 * Uses {@link Bukkit#getCurrentTick()} for timing. The sweep runs every
 * {@value #SWEEP_INTERVAL_TICKS} tick.
 */
public final class EnchantmentListener implements Listener {

    /** Sweep interval: 1 tick — the timer must feel exact. */
    static final long SWEEP_INTERVAL_TICKS = 1L;

    /** Timer duration: 10 seconds. */
    static final long TIMER_TICKS = 200L;

    /** Radius (blocks) in which the beep is audible. */
    private static final double BEEP_RADIUS = 5.0;

    /** Beeps are replayed every N ticks (avoid sound spam). */
    private static final long BEEP_INTERVAL_TICKS = 5L;

    /** Explosion radius of the invisible creeper. */
    private static final int EXPLOSION_RADIUS = 10;

    /** Beep pitch range over the countdown (low → high). */
    private static final float PITCH_MIN = 0.5f;
    private static final float PITCH_MAX = 2.0f;

    /** player → tick when the countdown started. */
    private static final Map<UUID, Long> ACTIVE = new HashMap<>();
    /** player → last tick a beep was played. */
    private static final Map<UUID, Long> LAST_BEEP = new HashMap<>();

    private EnchantmentListener() {}

    // ─────────────────────────────────────────────────────────────
    //  SLOT TRACKING
    // ─────────────────────────────────────────────────────────────

    /** Kind constants for where the cursed item lives. */
    private static final int KIND_STORAGE = 0;
    private static final int KIND_ARMOR = 1;
    private static final int KIND_OFFHAND = 2;
    private static final int KIND_MAINHAND = 3;
    private static final int KIND_CURSOR = 4;

    /** A location of the cursed item inside a player's inventory. */
    private record CurseSlot(int kind, int index) {}

    // ─────────────────────────────────────────────────────────────
    //  SWEEP
    // ─────────────────────────────────────────────────────────────

    /** One sweep tick: update every online player's countdown. */
    private static void sweepAllPlayers() {
        long now = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                updatePlayer(player, now);
            } catch (Exception e) {
                ConsoleLogger.warn("[SelfDestruct] Sweep error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Advances the countdown for one player.
     */
    static void updatePlayer(Player player, long now) {
        UUID id = player.getUniqueId();

        // No cursed item anywhere → nothing to do (and the timer resets).
        CurseSlot slot = findCursedSlot(player);
        if (slot == null) {
            ACTIVE.remove(id);
            LAST_BEEP.remove(id);
            return;
        }

        long start = ACTIVE.computeIfAbsent(id, k -> now);

        long elapsed = now - start;
        if (elapsed >= TIMER_TICKS) {
            ACTIVE.remove(id);
            LAST_BEEP.remove(id);
            detonate(player, slot);
            return;
        }

        // ─── Smoke particles from the player ───
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.8, 0), 2, 0.3, 0.4, 0.3, 0.0);

        // ─── Rising beep, heard by everyone within BEEP_RADIUS ───
        Long lastBeep = LAST_BEEP.get(id);
        if (lastBeep == null || now - lastBeep >= BEEP_INTERVAL_TICKS) {
            LAST_BEEP.put(id, now);
            double progress = (double) elapsed / TIMER_TICKS;
            float pitch = PITCH_MIN + (float) (progress * (PITCH_MAX - PITCH_MIN));
            double radiusSq = BEEP_RADIUS * BEEP_RADIUS;
            for (Player nearby : Bukkit.getOnlinePlayers()) {
                if (nearby.getWorld().equals(player.getWorld())
                        && nearby.getLocation().distanceSquared(loc) <= radiusSq) {
                    nearby.playSound(nearby.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, pitch);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  DETONATION
    // ─────────────────────────────────────────────────────────────

    /**
     * BOOM. Removes the cursed item, then spawns an invisible ignited creeper
     * (explosion radius {@value #EXPLOSION_RADIUS}) with a 1-tick fuse — the
     * vanilla creeper tick detonates it on the very next tick.
     * <p>
     * Failsafes: if the spawn is cancelled (CreatureSpawnEvent / entity limits)
     * a direct {@link World#createExplosion} fires instead; and one tick later
     * {@link Creeper#explode()} is forced if the vanilla tick hasn't already
     * detonated it (the isValid guard makes a double explosion impossible —
     * explodeCreeper discards the entity).
     */
    private static void detonate(Player player, CurseSlot slot) {
        removeAt(player, slot);

        World world = player.getWorld();
        Location loc = player.getLocation();
        Creeper creeper = world.spawn(loc, Creeper.class, c -> {
            // Invisible for 10 seconds (longer than the explosion lingers).
            c.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 10, 0, false, false));
            c.setExplosionRadius(EXPLOSION_RADIUS);
            // 1-tick fuse: on the next entity tick swell(0)+1 >= maxSwell(1) → explodeCreeper().
            c.setMaxFuseTicks(1);
            c.setFuseTicks(0);
            c.setIgnited(true);
        });

        // Spawn failed (cancelled event / limits) — the item is already gone,
        // so make absolutely sure the explosion still happens.
        if (creeper == null || !creeper.isValid()) {
            world.createExplosion(loc, EXPLOSION_RADIUS, false, true);
            ConsoleLogger.warn("[SelfDestruct] " + player.getName()
                    + " — creeper spawn failed, direct explosion (power " + EXPLOSION_RADIUS + ").");
            return;
        }

        // Deterministic 1-tick fuse: force explode() if the vanilla tick hasn't
        // already done it (isValid() is false after explodeCreeper → no double boom).
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (creeper.isValid() && !creeper.isDead()) {
                creeper.explode();
            }
        }, 1L);

        ConsoleLogger.warn("[SelfDestruct] " + player.getName()
                + " — Curse of Self-Destruct detonated (invisible creeper, power "
                + EXPLOSION_RADIUS + ").");
    }

    // ─────────────────────────────────────────────────────────────
    //  INVENTORY HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Returns the location of the first cursed item in the player's inventory, or null. */
    private static CurseSlot findCursedSlot(Player player) {
        PlayerInventory inv = player.getInventory();

        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (Enchantment.isCursed(storage[i])) return new CurseSlot(KIND_STORAGE, i);
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (Enchantment.isCursed(armor[i])) return new CurseSlot(KIND_ARMOR, i);
        }
        if (Enchantment.isCursed(inv.getItemInOffHand())) return new CurseSlot(KIND_OFFHAND, 0);
        if (Enchantment.isCursed(inv.getItemInMainHand())) return new CurseSlot(KIND_MAINHAND, 0);
        if (Enchantment.isCursed(player.getOpenInventory().getCursor())) return new CurseSlot(KIND_CURSOR, 0);
        return null;
    }

    /** Removes the stack at the given location. */
    private static void removeAt(Player player, CurseSlot slot) {
        PlayerInventory inv = player.getInventory();
        switch (slot.kind()) {
            case KIND_STORAGE -> inv.setItem(slot.index(), null);
            case KIND_ARMOR -> {
                ItemStack[] armor = inv.getArmorContents();
                armor[slot.index()] = null;
                inv.setArmorContents(armor);
            }
            case KIND_OFFHAND -> inv.setItemInOffHand(null);
            case KIND_MAINHAND -> inv.setItemInMainHand(null);
            case KIND_CURSOR -> player.getOpenInventory().setCursor(null);
            default -> { /* unreachable */ }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  EVENTS
    // ─────────────────────────────────────────────────────────────

    /** Leaving the game resets the countdown (restarts on next login). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        ACTIVE.remove(id);
        LAST_BEEP.remove(id);
    }

    // ─────────────────────────────────────────────────────────────
    //  REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Starts the countdown sweep and registers the quit cleanup.
     */
    public static void register(Main plugin) {
        Bukkit.getPluginManager().registerEvents(new EnchantmentListener(), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, EnchantmentListener::sweepAllPlayers,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
        ConsoleLogger.info("[SelfDestruct] Listener registered (10s countdown sweep every tick).");
    }
}
