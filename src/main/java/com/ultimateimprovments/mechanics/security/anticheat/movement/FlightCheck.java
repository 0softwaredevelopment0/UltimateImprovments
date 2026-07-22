package com.ultimateimprovments.mechanics.security.anticheat.movement;

import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.mechanics.security.anticheat.AntiCheatManager;
import com.ultimateimprovments.mechanics.security.anticheat.core.*;
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
 * Flight — полёт без элитры/эффектов.
 * <p>
 * Детектирует три типа полёта:
 * 1. Hover — игрок в воздухе, Y-скорость ≈ 0 (зависание)
 * 2. Horizontal flight — горизонтальное движение в воздухе быстрее макс. возможного
 * 3. Vertical ascent — подъём в воздухе без прыжка (yDelta > 0.42)
 * <p>
 * Ground detection использует ДВОЙНУЮ проверку:
 * - server-side {@code player.isOnGround()}
 * - block-based проверка {@link #hasBlockBelow} — игрок считается на земле
 * ТОЛЬКО если под ним есть твёрдый блок в пределах 1.5 блоков.
 * Если {@code player.isOnGround()} возвращает true, но блока под игроком нет —
 * это спуф onGround, считаем что игрок в воздухе.
 */
public class FlightCheck extends AbstractCheck {

    private int maxAirTicks;
    private double maxHoverY;
    private double maxHorizontalSpeed;
    private double jumpVelocity;

    private final ConcurrentHashMap<UUID, Integer> airTickCounters = new ConcurrentHashMap<>();

    public FlightCheck() {
        super("Flight", CheckCategory.MOVEMENT);
    }

    @Override
    public void onInit() {
        loadConfig();
        maxAirTicks = getConfigInt("max_air_ticks", 20);
        maxHoverY = getConfigDouble("max_hover_y", 0.05);
        maxHorizontalSpeed = getConfigDouble("max_horizontal_speed", 0.6);
        jumpVelocity = getConfigDouble("jump_velocity", 0.42);
    }

    @Override
    public void onReload() { loadConfig(); }

    /**
     * Проверяет, есть ли твёрдый блок под/на уровне ног игрока (до blocksDown блоков вниз).
     * Использует целочисленные Y-координаты блоков, чтобы корректно обрабатывать
     * полублоки, ковры, плиты и т.д.
     * Если блока нет — игрок не может быть "на земле" (спуф onGround).
     */
    private boolean hasBlockBelow(Location loc, int blocksDown) {
        // Проверяем блок на уровне ног (feet block) — хендлит полублоки, ковры
        Block feetBlock = loc.getBlock();
        if (isSolidOrSupport(feetBlock)) return true;
        // Проверяем blocksDown блоков вниз
        for (int dy = 1; dy <= blocksDown; dy++) {
            Block below = loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - dy, loc.getBlockZ());
            if (isSolidOrSupport(below)) return true;
        }
        return false;
    }

    /**
     * Проверяет, может ли блок удерживать игрока (твёрдый, жидкость, лестница, лиана, паутина).
     */
    private boolean isSolidOrSupport(Block b) {
        return b.getType().isSolid()
                || b.getType() == Material.SCAFFOLDING
                || b.isLiquid()
                || b.getType() == Material.LADDER
                || b.getType() == Material.VINE
                || b.getType() == Material.COBWEB;
    }

    /**
     * Определяет, действительно ли игрок на земле.
     * Комбинирует server-side onGround и block-based проверку.
     * Если сервер говорит onGround=true, но под игроком нет блоков — это спуф.
     * Использует {@link Player#getLocation()} для block-based проверки,
     * потому что server-side позиция может отличаться от e.getTo().
     */
    private boolean isActuallyOnGround(Player player) {
        boolean serverOnGround = player.isOnGround();
        // Проверяем блоки под СЕРВЕРНОЙ позицией игрока (getLocation),
        // а не e.getTo() — это точнее отражает где игрок реально находится
        boolean blockBelow = hasBlockBelow(player.getLocation(), 3);
        return serverOnGround && blockBelow;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;

        // ── Double verification: server isOnGround + block check ──
        boolean actuallyOnGround = isActuallyOnGround(player);
        boolean serverOnGround = player.isOnGround();
        boolean groundSpoof = serverOnGround && !actuallyOnGround;

        // DEBUG (1% chance, or always if ground spoof detected)
        boolean debug = Math.random() < 0.01;
        if (debug || groundSpoof) {
            ConsoleLogger.info("[FlightCheck-DEBUG] " + player.getName()
                    + " | to=(" + String.format("%.2f,%.2f,%.2f", e.getTo().getX(), e.getTo().getY(), e.getTo().getZ()) + ")"
                    + " | serverOG=" + serverOnGround
                    + " | blockOG=" + hasBlockBelow(player.getLocation(), 3)
                    + (groundSpoof ? " | §cGROUND SPOOF!" : "")
                    + " | airTicks=" + airTickCounters.getOrDefault(player.getUniqueId(), 0));
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);

        // Если сервер говорит "на земле" — верим ТОЛЬКО если есть блок под игроком
        if (actuallyOnGround) {
            airTickCounters.put(player.getUniqueId(), 0);
            data.updatePosition(e.getTo(), true);
            return;
        }

        // Игрок в воздухе (или спуфит onGround)
        int airTickCount = airTickCounters.merge(player.getUniqueId(), 1, Integer::sum);
        double yDelta = e.getTo().getY() - e.getFrom().getY();

        // ── Check 1: Vertical ascent without jump ──
        double effectiveJumpVelocity = jumpVelocity;
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST)) {
            int amplifier = player.getPotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST).getAmplifier();
            effectiveJumpVelocity += (amplifier + 1) * 0.1;
        }
        if (yDelta > effectiveJumpVelocity * 1.1 && airTickCount > 1) {
            CheckResult result = flag(player, 4.0,
                    "Vertical ascent (YΔ=" + String.format("%.3f", yDelta)
                    + ", max jump=" + String.format("%.2f", effectiveJumpVelocity)
                    + (groundSpoof ? ", ON_GROUND_SPOOF" : "") + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
            data.updatePosition(e.getTo(), false);
            return;
        }

        // ── Check 2: Horizontal speed in air ──
        if (airTickCount > 3) {
            double dx = e.getTo().getX() - e.getFrom().getX();
            double dz = e.getTo().getZ() - e.getFrom().getZ();
            double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);

            if (horizontalSpeed > maxHorizontalSpeed) {
                CheckResult result = flag(player, 2.0,
                        "Horizontal flight (speed=" + String.format("%.3f", horizontalSpeed)
                        + ", max=" + String.format("%.2f", maxHorizontalSpeed)
                        + (groundSpoof ? ", ON_GROUND_SPOOF" : "") + ")");
                AntiCheatManager.getInstance().handleResult(player, this, result);
            }
        }

        // ── Check 3: Hover (in air, Y barely changes) ──
        if (airTickCount > maxAirTicks && Math.abs(yDelta) < maxHoverY) {
            CheckResult result = flag(player, 3.0,
                    "Hovering for " + airTickCount + " ticks (YΔ=" + String.format("%.3f", yDelta)
                    + (groundSpoof ? ", ON_GROUND_SPOOF" : "") + ")");
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        // ── Check 4: Long-term air (7.5+ seconds = definitely abnormal) ──
        // Порог 150 тиков (7.5 секунд) — достаточно, чтобы не флагать обычные
        // падения с высоты (Y=320 → Y=0 = ~6 секунд), но ловить реальный полёт.
        if (airTickCount > 150) {
            double vl = Math.min(10.0, airTickCount / 30.0); // 0.33 VL per second
            CheckResult result = flag(player, vl,
                    "Long-term flight for " + (airTickCount / 20) + " seconds"
                    + (groundSpoof ? ", ON_GROUND_SPOOF" : ""));
            AntiCheatManager.getInstance().handleResult(player, this, result);
        }

        data.updatePosition(e.getTo(), false);
    }
}
