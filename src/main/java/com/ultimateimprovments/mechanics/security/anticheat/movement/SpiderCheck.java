package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spider / WallClimb — подъём по стенам без лестницы/лианы.
 * <p>
 * Детекция: вертикальное движение вверх у сплошной стены без climbable блока.
 * Использует block-based верификацию onGround (спуф-защита) и проверяет
 * стены на двух уровнях (ноги + голова) без требования isOccluding().
 */
public class SpiderCheck extends AbstractCheck {

    private double minClimbSpeed;
    private int minClimbTicks;
    private final ConcurrentHashMap<UUID, Integer> climbTickCounters = new ConcurrentHashMap<>();

    public SpiderCheck() {
        super("Spider", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        minClimbSpeed = getConfigDouble("min_climb_speed", 0.05);
        minClimbTicks = getConfigInt("min_climb_ticks", 5);
    }

    @Override
    public void onReload() {
        loadConfig();
        minClimbSpeed = getConfigDouble("min_climb_speed", 0.05);
        minClimbTicks = getConfigInt("min_climb_ticks", 5);
    }

    /**
     * Проверяет, есть ли твёрдый блок под игроком (спуф-защита).
     * Если сервер говорит на земле, но блока нет — это спуф.
     */
    private boolean hasBlockBelow(Location loc, int blocksDown) {
        Block feetBlock = loc.getBlock();
        if (isSolidOrSupport(feetBlock)) return true;
        for (int dy = 1; dy <= blocksDown; dy++) {
            Block below = loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - dy, loc.getBlockZ());
            if (isSolidOrSupport(below)) return true;
        }
        return false;
    }

    private boolean isSolidOrSupport(Block b) {
        return b.getType().isSolid()
                || b.isLiquid()
                || b.getType() == Material.LADDER
                || b.getType() == Material.VINE
                || b.getType() == Material.SCAFFOLDING
                || b.getType() == Material.COBWEB;
    }

    /**
     * Находит стену рядом с игроком на двух уровнях (ноги + голова).
     * Возвращает блок стены, если найден, или null.
     * Использует ТОЛЬКО isSolid() — не требует isOccluding().
     */
    private Block findAdjacentWall(Location loc) {
        for (double yOff = 0; yOff <= 1.0; yOff += 1.0) {
            Block[] blocks = {
                    loc.clone().add(0.3, yOff, 0).getBlock(),
                    loc.clone().add(-0.3, yOff, 0).getBlock(),
                    loc.clone().add(0, yOff, 0.3).getBlock(),
                    loc.clone().add(0, yOff, -0.3).getBlock()
            };
            for (Block b : blocks) {
                if (b.getType().isSolid()) return b;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        double yDelta = e.getTo().getY() - e.getFrom().getY();

        // ── Double verification: server isOnGround + block check (спуф-защита) ──
        boolean serverOnGround = player.isOnGround();
        boolean blockBelow = hasBlockBelow(player.getLocation(), 3);
        boolean actuallyOnGround = serverOnGround && blockBelow;
        boolean groundSpoof = serverOnGround && !blockBelow;

        if (actuallyOnGround) {
            climbTickCounters.put(player.getUniqueId(), 0);
            return;
        }

        // Check if there's a climbable block at player position (legitimate climbing)
        Block feet = player.getLocation().getBlock();
        Block head = player.getLocation().add(0, 1, 0).getBlock();
        boolean onClimbable = isClimbable(feet) || isClimbable(head);
        if (onClimbable) {
            climbTickCounters.put(player.getUniqueId(), 0);
            return;
        }

        // Check for adjacent solid wall — find WHICH block
        Block wallBlock = findAdjacentWall(player.getLocation());
        String wallName = wallBlock != null
                ? wallBlock.getType().name().toLowerCase().replace('_', ' ')
                : "none";

        int climbTickCount = climbTickCounters.getOrDefault(player.getUniqueId(), 0);
        if (yDelta > minClimbSpeed && wallBlock != null) {
            climbTickCount++;
            climbTickCounters.put(player.getUniqueId(), climbTickCount);
            if (climbTickCount >= minClimbTicks) {
                // Флаг + VL
                CheckResult result = flag(player, 3.0,
                        "WallClimb: " + wallName + " YΔ=" + String.format("%.3f", yDelta)
                        + " ticks=" + climbTickCount
                        + (groundSpoof ? " ON_GROUND_SPOOF" : ""));
                AntiCheatManager.getInstance().handleResult(player, this, result);

                // ── НЕМЕДЛЕННЫЙ SETBACK ──
                // Телепортируем на последнюю землю + обнуляем скорость
                PlayerData data = AntiCheatManager.getInstance().getPlayerData(player);
                if (data != null) {
                    Location safe = data.getLastGroundLocation();
                    if (safe != null) {
                        Location safeCopy = safe.clone();
                        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                            if (player.isOnline()) {
                                player.teleport(safeCopy);
                                player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                                player.sendMessage(MessageUtil.parse(
                                        "<red>⛔ WallClimb detected!</red> <gray>Climbing</gray> <yellow>" + wallName + "</yellow>"));
                            }
                        });
                    }
                }
            }
        } else {
            climbTickCount = Math.max(0, climbTickCount - 1);
            climbTickCounters.put(player.getUniqueId(), climbTickCount);
        }

        // Debug log (1% chance or on spoof)
        if (groundSpoof || Math.random() < 0.01) {
            ConsoleLogger.info("[SpiderCheck-DEBUG] " + player.getName()
                    + " | yΔ=" + String.format("%.3f", yDelta)
                    + " | serverOG=" + serverOnGround + " blockOG=" + blockBelow
                    + " | wall=" + wallName + " climb=" + climbTickCount
                    + (groundSpoof ? " | §cGROUND SPOOF!" : ""));
        }

        PlayerData pd = AntiCheatManager.getInstance().getOrCreatePlayerData(player);
        pd.updatePosition(e.getTo(), serverOnGround);
    }

    private boolean isClimbable(Block b) {
        Material t = b.getType();
        return t == Material.LADDER || t == Material.VINE
                || t == Material.SCAFFOLDING || t == Material.TWISTING_VINES
                || t == Material.WEEPING_VINES;
    }
}
