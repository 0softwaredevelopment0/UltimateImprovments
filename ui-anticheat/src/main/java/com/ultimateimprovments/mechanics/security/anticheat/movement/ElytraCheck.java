package com.ultimateimprovments.mechanics.security.anticheat.movement;

// ElytraBoostManager is a soft dependency (UI-Other) — accessed via reflection
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Elytra — elytra flight check.
 * <p>
 * Detection: the player ascends on elytra without a firework in hand
 * and without a boost from ElytraBoostManager → flag (anti-fly without fireworks).
 * Also detects abnormal flight speed.
 */
public class ElytraCheck extends AbstractCheck {

    private double maxElytraSpeed;
    private double maxGlideUpSpeed;
    private long boostCheckWindowMs;
    private double riptideMaxSpeed;
    private double riptideClimbLimit;

    public ElytraCheck() {
        super("Elytra", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxElytraSpeed = getConfigDouble("max_elytra_speed", 2.0);
        maxGlideUpSpeed = getConfigDouble("max_glide_up_speed", 0.1);
        boostCheckWindowMs = getConfigInt("boost_check_window_ms", 1000);
        riptideMaxSpeed = getConfigDouble("riptide_max_speed", 3.5);
        riptideClimbLimit = getConfigDouble("riptide_climb_limit", 2.0);
    }

    @Override
    public void onReload() {
        loadConfig();
        maxElytraSpeed = getConfigDouble("max_elytra_speed", 2.0);
        maxGlideUpSpeed = getConfigDouble("max_glide_up_speed", 0.1);
        boostCheckWindowMs = getConfigInt("boost_check_window_ms", 1000);
        riptideMaxSpeed = getConfigDouble("riptide_max_speed", 3.5);
        riptideClimbLimit = getConfigDouble("riptide_climb_limit", 2.0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;
        if (!player.isGliding()) return;

        double xDelta = e.getTo().getX() - e.getFrom().getX();
        double yDelta = e.getTo().getY() - e.getFrom().getY();
        double zDelta = e.getTo().getZ() - e.getFrom().getZ();
        double totalSpeed = Math.sqrt(xDelta * xDelta + yDelta * yDelta + zDelta * zDelta);

        boolean isRiptiding = player.isRiptiding();

        // Has potion effects that affect speed?
        boolean hasSpeedEffect = player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED);

        // Abnormal speed (accounting for the SPEED effect and riptide)
        double speedLimit = maxElytraSpeed;
        if (isRiptiding) speedLimit = Math.max(speedLimit, riptideMaxSpeed);
        if (hasSpeedEffect) {
            int amp = player.getPotionEffect(org.bukkit.potion.PotionEffectType.SPEED).getAmplifier();
            speedLimit *= (1.0 + (amp + 1) * 0.2); // +20% per level
        }
        if (totalSpeed > speedLimit) {
            CheckResult result = flag(player, 2.0,
                    "Elytra speed: " + String.format("%.3f", totalSpeed) + " (max: " + String.format("%.2f", speedLimit) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        // Going up without fireworks or plugin boost → flag
        // Riptide: higher climb limit (~1.5 blk/tick), but not infinite
        double climbLimit = maxGlideUpSpeed;
        if (isRiptiding) climbLimit = riptideClimbLimit;
        if (yDelta > climbLimit) {
            boolean hasFirework = player.getInventory().getItemInMainHand().getType() == Material.FIREWORK_ROCKET
                    || player.getInventory().getItemInOffHand().getType() == Material.FIREWORK_ROCKET;
            boolean wasBoosted = false;
            try {
                Class<?> ebm = Class.forName("com.ultimateimprovments.mechanics.features.player.ElytraBoostManager");
                wasBoosted = (boolean) ebm.getMethod("isRecentlyBoosted", java.util.UUID.class, long.class).invoke(null, player.getUniqueId(), boostCheckWindowMs);
            } catch (Exception ignored) {}

            if (!hasFirework && !wasBoosted) {
                CheckResult result = flag(player, 2.5,
                        "Elytra climb: YΔ=" + String.format("%.3f", yDelta)
                                + " noFirework=" + !hasFirework
                                + " noBoost=" + !wasBoosted);
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        data.updatePosition(e.getTo(), player.isOnGround());
    }
}
