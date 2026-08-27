package com.ultimateimprovments.mechanics.security.anticheat.misc;

import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiHunger — detection of a hack that modifies movement packets
 * to spend less hunger (anti-hunger hack).
 * <p>
 * Principle: the hack sends onGround=true in move packets even when the player
 * jumps/sprints, so the server doesn't add exhaustion.
 * <p>
 * Detection:
 * 1. If the player sprints + jumps (yDelta > 0.4) but the packet says onGround=true — flag.
 * 2. If the player sprints for a long time without the food level changing — flag.
 * 3. If the player jumps many times in a row (spends 0.8 exhaustion per jump)
 *    but the food level doesn't change — flag.
 */
public class AntiHungerCheck extends AbstractCheck {

    private int maxSprintTicksWithoutHungerLoss;
    private int maxJumpsWithoutHungerLoss;
    private double minJumpHeightForExhaustion;

    // How many ticks the player sprints without a food change
    private final ConcurrentHashMap<UUID, SprintData> sprintData = new ConcurrentHashMap<>();
    // How many jumps without a food change
    private final ConcurrentHashMap<UUID, JumpData> jumpData = new ConcurrentHashMap<>();

    public AntiHungerCheck() {
        super("AntiHunger", CheckCategory.MISC);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxSprintTicksWithoutHungerLoss = getConfigInt("max_sprint_ticks_without_hunger_loss", 100);
        maxJumpsWithoutHungerLoss = getConfigInt("max_jumps_without_hunger_loss", 5);
        minJumpHeightForExhaustion = getConfigDouble("min_jump_height_for_exhaustion", 0.4);
    }

    @Override
    public void onReload() { loadConfig(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        double yDelta = e.getTo().getY() - e.getFrom().getY();
        boolean onGround = player.isOnGround();

        // ── Detection 1: Sprint-jump with onGround=true ──
        // If the player jumps (yDelta > 0.4) BUT the packet says onGround=true
        // → that's an anti-hunger hack
        if (player.isSprinting() && yDelta > minJumpHeightForExhaustion && onGround) {
            CheckResult result = flag(player, 3.0,
                    "Sprint-jump with onGround=true (YΔ=" + String.format("%.2f", yDelta) + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
            return;
        }

        // ── Detection 2: Sprint without hunger loss ──
        if (player.isSprinting()) {
            SprintData sd = sprintData.computeIfAbsent(player.getUniqueId(), k -> new SprintData(player.getFoodLevel()));
            sd.sprintTicks++;

            // If the food level changed — reset the counter
            int currentFood = player.getFoodLevel();
            if (currentFood != sd.initialFoodLevel) {
                sd.initialFoodLevel = currentFood;
                sd.sprintTicks = 0;
                return;
            }

            if (sd.sprintTicks >= maxSprintTicksWithoutHungerLoss) {
                CheckResult result = flag(player, 2.0,
                        "Sprinting for " + sd.sprintTicks + " ticks without hunger loss");
                AntiCheatManager.getInstance().handleResult(player, this, result);
                sd.sprintTicks = 0;
            }
        } else {
            SprintData sd = sprintData.get(player.getUniqueId());
            if (sd != null) {
                sd.sprintTicks = 0;
                sd.initialFoodLevel = player.getFoodLevel();
            }
        }

        // ── Detection 3: Multiple jumps without hunger loss ──
        if (yDelta > minJumpHeightForExhaustion && !onGround) {
            JumpData jd = jumpData.computeIfAbsent(player.getUniqueId(), k -> new JumpData(player.getFoodLevel()));
            jd.jumpCount++;

            int currentFood = player.getFoodLevel();
            if (currentFood != jd.initialFoodLevel) {
                // Food changed — that's fine, reset
                jd.initialFoodLevel = currentFood;
                jd.jumpCount = 0;
                return;
            }

            if (jd.jumpCount >= maxJumpsWithoutHungerLoss) {
                CheckResult result = flag(player, 3.0,
                        jd.jumpCount + " jumps without hunger loss");
                AntiCheatManager.getInstance().handleResult(player, this, result);
                jd.jumpCount = 0;
            }
        } else if (onGround) {
            JumpData jd = jumpData.get(player.getUniqueId());
            if (jd != null) {
                jd.jumpCount = 0;
                jd.initialFoodLevel = player.getFoodLevel();
            }
            SprintData sd = sprintData.get(player.getUniqueId());
            if (sd != null) {
                sd.sprintTicks = 0;
                sd.initialFoodLevel = player.getFoodLevel();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        // Reset hunger tracking when player eats — food level resets
        sprintData.remove(e.getPlayer().getUniqueId());
        jumpData.remove(e.getPlayer().getUniqueId());
    }

    // =========================
    // DATA CLASSES
    // =========================

    private static class SprintData {
        int sprintTicks = 0;
        int initialFoodLevel;

        SprintData(int foodLevel) {
            this.initialFoodLevel = foodLevel;
        }
    }

    private static class JumpData {
        int jumpCount = 0;
        int initialFoodLevel;

        JumpData(int foodLevel) {
            this.initialFoodLevel = foodLevel;
        }
    }
}
