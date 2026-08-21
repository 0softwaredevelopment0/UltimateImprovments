package com.ultimateimprovments.space;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
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
import org.bukkit.persistence.PersistentDataContainer;
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
 * When a player right-clicks with a rocket:
 * <ol>
 *   <li>Player is frozen (Slowness 255 + Jump Boost 128)</li>
 *   <li>Rocket lifts the player smoothly upward at ~20 blocks/sec</li>
 *   <li>When reaching build limit, teleported to space</li>
 * </ol>
 * During the lift, the player cannot move, look around, or be pushed.
 */
public class SpaceRocketManager implements Listener {

    private static final NamespacedKey KEY_ROCKET = new NamespacedKey(Main.getInstance(), "space_rocket");
    private static final double LIFT_SPEED = 1.5; // blocks per tick (~30 blocks/sec)
    private static final int LIFT_CHECK_INTERVAL = 1; // every tick

    /** Players currently in launch sequence (uuid → task) */
    private static final Map<UUID, BukkitTask> launching = new ConcurrentHashMap<>();

    // ════════════════════════════════════════
    // ITEM CREATION
    // ════════════════════════════════════════

    public static ItemStack createRocket() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET, 1);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.parse("<gold>🚀 Space Rocket</gold>"));
            meta.lore(java.util.List.of(
                MessageUtil.parse("<gray>Right-click to launch into space</gray>"),
                MessageUtil.parse("<dark_gray>You will be lifted to the sky limit</dark_gray>")
            ));
            meta.getPersistentDataContainer().set(KEY_ROCKET, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
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

        // Start smooth lift
        startLift(player);
    }

    // ════════════════════════════════════════
    // SMOOTH LIFT
    // ════════════════════════════════════════

    private void startLift(Player player) {
        UUID uuid = player.getUniqueId();
        int buildLimit = player.getWorld().getMaxHeight();

        // Freeze the player
        freezePlayer(player);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || SpaceManager.isInSpace(player)) {
                    cleanup(player);
                    cancel();
                    return;
                }

                double currentY = player.getLocation().getY();

                // Check if reached build limit
                if (currentY >= buildLimit) {
                    cleanup(player);
                    SpaceManager.teleportToSpace(player);
                    player.sendMessage(MessageUtil.parse(
                        "<green>✔</green> <white>You have arrived in space!</white>"));
                    cancel();
                    return;
                }

                // Lift the player upward
                player.teleport(new org.bukkit.Location(
                    player.getWorld(),
                    player.getLocation().getX(),
                    currentY + LIFT_SPEED,
                    player.getLocation().getZ(),
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
                ));

                // Show progress in action bar
                int remaining = (int) Math.ceil((buildLimit - currentY) / LIFT_SPEED / 20.0);
                player.sendActionBar(MessageUtil.parse(
                    "<yellow>🚀 Ascending... <white>" + remaining + "s</white> to space</yellow>"));
            }
        }.runTaskTimer(Main.getInstance(), 0L, LIFT_CHECK_INTERVAL);

        launching.put(uuid, task);
    }

    private void freezePlayer(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 600, 128, false, false, false));
    }

    private void cleanup(Player player) {
        BukkitTask task = launching.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
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
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
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
    }
}