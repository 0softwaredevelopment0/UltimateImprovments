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
 * Flight — flying without an elytra/effects.
 * <p>
 * Detects three types of flight:
 * 1. Hover — the player is airborne with Y velocity ≈ 0 (hovering)
 * 2. Horizontal flight — horizontal air movement faster than the max possible
 * 3. Vertical ascent — climbing in the air without a jump (yDelta > 0.42)
 * <p>
 * Ground detection uses a DOUBLE check:
 * - server-side {@code player.isOnGround()}
 * - block-based check {@link #hasBlockBelow} — the player counts as on the ground
 * ONLY if there is a solid block below within 1.5 blocks.
 * If {@code player.isOnGround()} returns true but there is no block below —
 * that's an onGround spoof; the player counts as airborne.
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
     * Checks whether there is a solid block below/at the player's feet level (up to blocksDown blocks down).
     * Uses integer block Y coordinates to correctly handle slabs, carpets, plates, etc.
     * If there is no block — the player cannot be "on the ground" (onGround spoof).
     */
    private boolean hasBlockBelow(Location loc, int blocksDown) {
        // Check the block at feet level — handles slabs, carpets
        Block feetBlock = loc.getBlock();
        if (isSolidOrSupport(feetBlock)) return true;
        // Check blocksDown blocks below
        for (int dy = 1; dy <= blocksDown; dy++) {
            Block below = loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - dy, loc.getBlockZ());
            if (isSolidOrSupport(below)) return true;
        }
        return false;
    }

    /**
     * Checks whether a block can support the player (solid, liquid, ladder, vine, cobweb).
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
     * Determines whether the player is really on the ground.
     * Combines the server-side onGround flag and a block-based check.
     * If the server says onGround=true but there are no blocks below — that's a spoof.
     * Uses {@link Player#getLocation()} for the block-based check,
     * because the server-side position may differ from e.getTo().
     */
    private boolean isActuallyOnGround(Player player) {
        boolean serverOnGround = player.isOnGround();
        // Check blocks below the SERVER-side player position (getLocation),
        // not e.getTo() — this reflects where the player actually is more accurately
        boolean blockBelow = hasBlockBelow(player.getLocation(), 3);
        return serverOnGround && blockBelow;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();
        if (!isEnabled() || isExempted(player)) return;
        // Fully off when the anti-cheat is disabled globally (anticheat.enabled: false)
        if (!AntiCheatManager.getInstance().isGlobalEnabled()) return;

        // ── Double verification: server isOnGround + block check ──
        boolean actuallyOnGround = isActuallyOnGround(player);
        boolean serverOnGround = player.isOnGround();
        boolean groundSpoof = serverOnGround && !actuallyOnGround;

        // DEBUG (1% chance, or always if ground spoof detected)
        boolean debug = Math.random() < 0.01;
        if (debug || groundSpoof) {
            ConsoleLogger.raw("<white>[FlightCheck-DEBUG] " + player.getName()
                    + " | to=(" + String.format("%.2f,%.2f,%.2f", e.getTo().getX(), e.getTo().getY(), e.getTo().getZ()) + ")"
                    + " | serverOG=" + serverOnGround
                    + " | blockOG=" + hasBlockBelow(player.getLocation(), 3)
                    + (groundSpoof ? " | <red>GROUND SPOOF!</red>" : "")
                    + " | airTicks=" + airTickCounters.getOrDefault(player.getUniqueId(), 0)
                    + "</white>");
        }

        PlayerData data = AntiCheatManager.getInstance().getOrCreatePlayerData(player);

        // If the server says "on the ground" — believe it ONLY if there is a block below
        if (actuallyOnGround) {
            airTickCounters.put(player.getUniqueId(), 0);
            data.updatePosition(e.getTo(), true);
            return;
        }

        // The player is airborne (or spoofing onGround)
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
        // Threshold of 150 ticks (7.5 seconds) — enough not to flag normal
        // falls from height (Y=320 → Y=0 = ~6 seconds), but to catch real flight.
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
