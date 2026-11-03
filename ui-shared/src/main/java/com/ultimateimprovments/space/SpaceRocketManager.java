package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.core.Permissions;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.ultimateimprovments.mechanics.crafting.RecipeRegistry;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rocket item manager.
 * <p>
 * Right-click with a rocket starts a <b>levitation</b> launch:
 * <ol>
 *   <li>requires {@code ui.command.space} — the same node as {@code /ui space}
 *       (OP by default), so the rocket is not a free second entrance to space;</li>
 *   <li>sets {@code allowFlight} for the duration of the ride — otherwise the client
 *       reports "flying" while the server has flight disabled and the player gets
 *       kicked with "Flying is not enabled on this server";</li>
 *   <li>lifts the player with a real LEVITATION effect instead of teleporting every
 *       tick. The old implementation called {@link Player#teleport} each tick, and
 *       repeated teleports mid-air are exactly what made the server kick;</li>
 *   <li>tops the effect up when it nears its end (remaining-time check), and on
 *       reaching the arrival height removes every effect, restores the previous
 *       flight flags and performs a single teleport into the space world.</li>
 * </ol>
 * Vanilla levitation lifts roughly {@code amplifier + 1} blocks per second, so the
 * horizontal freeze (SLOWNESS + JUMP_BOOST) only has to stop drifting.
 */
public class SpaceRocketManager implements Listener {

    private static final NamespacedKey KEY_ROCKET = new NamespacedKey(Main.getInstance(), "space_rocket");

    /** Re-apply the launch effects when the remaining levitation time drops to this. */
    private static final int EFFECT_TOP_UP_THRESHOLD = 60;
    /** How often the launch is checked for arrival / effect timeout. */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /** Players currently in the launch sequence (uuid → task). */
    private static final Map<UUID, BukkitTask> launching = new ConcurrentHashMap<>();
    /** Snapshot of the state that has to be restored after the ride. */
    private static final Map<UUID, LaunchState> launchStates = new ConcurrentHashMap<>();

    private record LaunchState(boolean previousAllowFlight, boolean previousFlying,
                               double targetY, double blocksPerSecond, int amplifier) {}

    // ════════════════════════════════════════
    // ITEM CREATION
    // ════════════════════════════════════════

    public static ItemStack createRocket() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET, 1);
        item.editMeta(meta -> {
            meta.displayName(MessageUtil.parse("<white>Space Rocket</white>"));
            meta.lore(java.util.List.of(
                MessageUtil.parse("<gray>Right-click to launch into space</gray>"),
                MessageUtil.parse("<dark_gray>You will be lifted to the sky limit</dark_gray>")
            ));
            meta.getPersistentDataContainer().set(KEY_ROCKET, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean isRocket(ItemStack item) {
        if (item == null || item.getType() != Material.FIREWORK_ROCKET) return false;
        if (item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_ROCKET, PersistentDataType.BYTE);
    }

    // ════════════════════════════════════════
    // RIGHT-CLICK HANDLER
    // ════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!SpaceManager.isEnabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isRocket(item)) return;

        // The rocket is a second entrance to space — same gate as /ui space.
        if (!player.hasPermission(Permissions.CMD_SPACE)) {
            player.sendMessage(MessageUtil.parse(
                    "<red>❌ You don't have permission to launch into space.</red>"));
            return;
        }

        if (SpaceManager.isInSpace(player)) {
            player.sendMessage(MessageUtil.parse("<red>❌ You are already in space!</red>"));
            return;
        }

        if (launching.containsKey(player.getUniqueId())) {
            player.sendMessage(MessageUtil.parse("<yellow>✦</yellow> <white>Launch already in progress!</white>"));
            return;
        }

        event.setCancelled(true);

        // Consume one rocket
        item.setAmount(item.getAmount() - 1);

        startLift(player);
    }

    // ════════════════════════════════════════
    // LEVITATION LIFT
    // ════════════════════════════════════════

    private void startLift(Player player) {
        UUID uuid = player.getUniqueId();

        int amplifier = Math.max(0, Main.getInstance().getConfig()
                .getInt("space.rocket_levitation_amplifier", 19));
        int margin = Math.max(0, Main.getInstance().getConfig()
                .getInt("space.rocket_arrival_margin", 8));

        // Vanilla levitation raises the player by ~0.05 * (amplifier + 1) blocks/tick.
        double blocksPerSecond = Math.max(1.0, amplifier + 1.0);
        double startY = player.getLocation().getY();
        double targetY = Math.max(startY + 8.0, player.getWorld().getMaxHeight() - margin);

        int travelTicks = (int) Math.ceil(Math.max(0.0, targetY - startY) / blocksPerSecond * 20.0);
        int effectDuration = Math.max(100, travelTicks + 200);

        launchStates.put(uuid, new LaunchState(
                player.getAllowFlight(), player.isFlying(), targetY, blocksPerSecond, amplifier));

        // Flight must be allowed for the ride: with flight disabled the client's
        // "flying" state gets the player kicked ("Flying is not enabled").
        player.setAllowFlight(true);
        player.setFlying(false);
        applyLaunchEffects(player, amplifier, effectDuration);

        player.sendMessage(MessageUtil.parse(
                "<yellow>🚀</yellow> <white>Launching into space — about </white><yellow>"
                        + Math.max(1, travelTicks / 20) + "s</yellow><white> to go.</white>"));

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || SpaceManager.isInSpace(player)) {
                    cleanup(player);
                    cancel();
                    return;
                }

                LaunchState state = launchStates.get(uuid);
                if (state == null) {
                    cancel();
                    return;
                }

                double currentY = player.getLocation().getY();

                // Arrived at the arrival height: teleport once, then drop the effects
                // and restore the flight flags (teleport first so the player is not
                // left in mid-air with flight disabled if the teleport fails).
                if (currentY >= state.targetY()) {
                    boolean arrived = SpaceManager.teleportToSpace(player);
                    cleanup(player);
                    player.sendMessage(MessageUtil.parse(arrived
                            ? "<green>✔</green> <white>You have arrived in space!</white>"
                            : "<red>❌ Space teleport failed — try again shortly.</red>"));
                    cancel();
                    return;
                }

                // Remaining-time check: top the effects up instead of letting the
                // levitation run out and dropping the player mid-flight.
                PotionEffect levitation = player.getPotionEffect(PotionEffectType.LEVITATION);
                if (levitation == null || levitation.getDuration() <= EFFECT_TOP_UP_THRESHOLD) {
                    applyLaunchEffects(player, state.amplifier(), 600);
                }

                int secondsLeft = (int) Math.ceil(
                        Math.max(0.0, state.targetY() - currentY) / state.blocksPerSecond());
                player.sendActionBar(MessageUtil.parse(
                        "<yellow>🚀 Ascending... <white>" + secondsLeft + "s</white> to space</yellow>"));
            }
        }.runTaskTimer(Main.getInstance(), CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);

        launching.put(uuid, task);
    }

    private void applyLaunchEffects(Player player, int amplifier, int durationTicks) {
        int duration = Math.max(20, durationTicks);
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, duration, amplifier, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 128, false, false, false));
    }

    private void cleanup(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask task = launching.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        LaunchState state = launchStates.remove(uuid);

        if (!player.isOnline()) return;

        player.removePotionEffect(PotionEffectType.LEVITATION);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.setFallDistance(0.0f);

        if (state != null) {
            player.setFlying(false);
            player.setAllowFlight(state.previousAllowFlight());
            if (state.previousAllowFlight() && state.previousFlying()) {
                player.setFlying(true);
            }
        }
    }

    // ===== RECIPE =====

    public static void registerRecipe(Main plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "space_rocket_recipe");
        // Remove if already registered
        if (plugin.getServer().getRecipe(key) != null) {
            plugin.getServer().removeRecipe(key);
        }
        ShapedRecipe recipe = new ShapedRecipe(key, createRocket());
        recipe.shape("NFN", "IRI", "TCT");
        recipe.setIngredient('N', Material.NETHERITE_SCRAP);
        recipe.setIngredient('F', Material.FIREWORK_STAR);
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('T', Material.TNT);
        recipe.setIngredient('C', Material.CLOCK);
        plugin.getServer().addRecipe(recipe);
        RecipeRegistry.registerRecipe(key);
    }

    public static void shutdown() {
        for (BukkitTask task : launching.values()) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        launching.clear();

        // Drop the launch effects of online players so a reload cannot leave them
        // stuck floating with flight enabled.
        for (UUID uuid : launchStates.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            player.removePotionEffect(PotionEffectType.LEVITATION);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
        launchStates.clear();
    }
}
