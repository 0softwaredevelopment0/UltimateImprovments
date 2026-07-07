package com.mcplugin.mechanics.security.anticheat.core;

import com.mcplugin.core.Main;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExemptionManager — определяет, должен ли игрок быть освобождён от проверок.
 * <p>
 * Проверяет:
 * - GameMode (CREATIVE, SPECTATOR)
 * - Permission bypass
 * - Active potion effects (SPEED, JUMP, LEVITATION, SLOW_FALLING, DOLPHINS_GRACE)
 * - Plugin attributes (speed boost, elytra boost, magnet, etc.)
 * - Being on check (CheckManager)
 * - Riptide/Trident spin attack
 * - Elytra flight
 * - Vehicle riding
 * - World border / end portal
 * - Custom exemption requests from other plugin modules
 */
public class ExemptionManager {

    private static ExemptionManager instance;

    // Custom exemptions: UUID → Set of check names to exempt
    private final ConcurrentHashMap<UUID, Set<String>> customExemptions = new ConcurrentHashMap<>();

    private ExemptionManager() {}

    public static void init() {
        instance = new ExemptionManager();
    }

    public static ExemptionManager getInstance() {
        return instance;
    }

    /**
     * Проверяет, освобождён ли игрок от ВСЕХ проверок.
     */
    public boolean isExemptedAll(Player player) {
        if (player == null || !player.isOnline()) return true;

        // GameMode exemptions
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;

        // Permission bypass
        if (player.hasPermission("mcplugin.anticheat.bypass")) return true;

        // Being checked by CheckManager
        if (com.mcplugin.mechanics.security.check.CheckManager.isBeingChecked(player)) return true;

        // Riding a vehicle
        if (player.isInsideVehicle()) return true;

        // Dead / dying
        if (player.isDead() || player.getHealth() <= 0) return true;

        return false;
    }

    /**
     * Проверяет, освобождён ли игрок от конкретной проверки.
     *
     * @param player    игрок
     * @param checkName имя проверки (например "KillAura", "Speed")
     * @return true если игрок освобождён
     */
    public boolean isExempted(Player player, String checkName) {
        if (player == null || !player.isOnline()) return true;
        if (isExemptedAll(player)) return true;

        // Custom per-check exemptions
        Set<String> exemptedChecks = customExemptions.get(player.getUniqueId());
        if (exemptedChecks != null && (exemptedChecks.contains(checkName) || exemptedChecks.contains("*"))) {
            return true;
        }

        // Check-specific exemptions
        switch (checkName) {
            case "Speed", "Flight", "Glide", "FastFall", "Step", "Spider", "Jesus", "FastLadder", "Clip", "Teleport":
                return isMovementExempted(player, checkName);
            case "Reach", "BlockReach":
                return isReachExempted(player);
            case "Criticals":
                return isCombatExempted(player);
            case "NoFall":
                return isNoFallExempted(player);
            case "Velocity", "AntiKnockback":
                return isVelocityExempted(player);
            case "Elytra":
                // НЕ exempt'им gliding и riptide — ElytraCheck сам регулирует пороги
                // для riptide (повышенный лимит), но не пропускает хак-флай
                return player.getGameMode() == GameMode.CREATIVE;
            case "InventoryMove":
                return player.getGameMode() == GameMode.CREATIVE;
            case "FastBreak", "FastPlace", "Nuker":
                return player.getGameMode() == GameMode.CREATIVE;
            case "XRay":
                return player.hasPermission("mcplugin.admin");
            default:
                return false;
        }
    }

    /**
     * Освобождение для movement-проверок.
     */
    private boolean isMovementExempted(Player player, String checkName) {
        // Potion effects that affect movement
        // SPEED и JUMP_BOOST не exemptят Flight — скорость и прыжок не дают летать
        if (hasEffect(player, PotionEffectType.SPEED)) {
            if (!"Flight".equals(checkName)) return true;
        }
        if (hasEffect(player, PotionEffectType.JUMP_BOOST)) {
            if (!"Flight".equals(checkName)) return true;
        }
        if (hasEffect(player, PotionEffectType.LEVITATION)) return true;
        if (hasEffect(player, PotionEffectType.SLOW_FALLING)) return true;
        if (hasEffect(player, PotionEffectType.DOLPHINS_GRACE)) return true;

        // Riptide (trident spin attack)
        if (player.isRiptiding()) return true;

        // Elytra
        if (player.isGliding()) return true;

        // Plugin: ElytraBoostModule — elytra flight is already exempted above
        // (player.isGliding() check handles this)

        // Plugin: AttributesModule sneak_speed — only exempt Speed check for sneaking players
        // (not all movement checks, since sneaking doesn't prevent fly/clip/etc.)
        if ("Speed".equals(checkName)) {
            if (Main.getInstance().getConfig().getBoolean("features.attributes.enabled", false)
                    && player.isSneaking()) {
                return true;
            }
        }

        Block feet = player.getLocation().getBlock();
        if (feet.getType() == Material.WATER || feet.getType() == Material.LAVA) {
            if (!"Jesus".equals(checkName) && !"WaterWalk".equals(checkName)) return true;
        }

        // On climbable (ladder/vine)
        if (feet.getType() == Material.LADDER || feet.getType() == Material.VINE
                || feet.getType() == Material.SCAFFOLDING) {
            if (!"FastLadder".equals(checkName) && !"FastClimb".equals(checkName)) return true;
        }

        // World border
        if (player.getWorld().getWorldBorder() != null) {
            Location loc = player.getLocation();
            if (!player.getWorld().getWorldBorder().isInside(loc)) return true;
        }

        return false;
    }

    private boolean isReachExempted(Player player) {
        // High ping players get more reach tolerance
        return player.getPing() > 500;
    }

    private boolean isCombatExempted(Player player) {
        if (player.isRiptiding()) return true;
        return false;
    }

    private boolean isNoFallExempted(Player player) {
        if (hasEffect(player, PotionEffectType.SLOW_FALLING)) return true;
        if (player.isGliding()) return true;
        Block below = player.getLocation().subtract(0, 0.1, 0).getBlock();
        // Slime blocks negate fall damage
        if (below.getType() == Material.SLIME_BLOCK) return true;
        // Hay bales
        if (below.getType() == Material.HAY_BLOCK) return true;
        return false;
    }

    private boolean isVelocityExempted(Player player) {
        if (hasEffect(player, PotionEffectType.SLOWNESS)) return true;
        // Plugin: ShieldSlownessModule — shield slows, affects knockback
        if (Main.getInstance().getConfig().getBoolean("features.shieldslowness.enabled", false)) {
            if (player.isBlocking()) return true;
        }
        return false;
    }

    private boolean isInWater(Player player) {
        Block feet = player.getLocation().getBlock();
        return feet.getType() == Material.WATER;
    }

    private boolean hasEffect(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        return effect != null && effect.getAmplifier() >= 0;
    }

    // =========================
    // CUSTOM EXEMPTION API
    // =========================

    /**
     * Освободить игрока от конкретной проверки.
     */
    public void exempt(UUID uuid, String checkName) {
        customExemptions.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(checkName);
    }

    /**
     * Освободить игрока от всех проверок.
     */
    public void exemptAll(UUID uuid) {
        customExemptions.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add("*");
    }

    /**
     * Снять освобождение с игрока для конкретной проверки.
     */
    public void unexempt(UUID uuid, String checkName) {
        Set<String> set = customExemptions.get(uuid);
        if (set != null) set.remove(checkName);
    }

    /**
     * Снять все освобождения с игрока.
     */
    public void unexemptAll(UUID uuid) {
        customExemptions.remove(uuid);
    }

    public static void shutdown() {
        if (instance != null) {
            instance.customExemptions.clear();
            instance = null;
        }
    }
}
