package com.ultimateimprovements.mechanics.protection;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Данные одного размещённого «Блока защиты».
 * <p>
 * Хранит:
 * <ul>
 *   <li>{@code id} — уникальный UUID (используется как PK в БД)</li>
 *   <li>{@code location} — мировые координаты центра</li>
 *   <li>{@code owner} — UUID игрока, поставившего блок</li>
 *   <li>{@code radius} — текущий радиус защиты (блоки)</li>
 *   <li>{@code integrity} — текущая целостность 0..100</li>
 *   <li>{@code points} — очки для прокачки</li>
 *   <li>{@code enabled} — включён ли блок (защищает территорию)</li>
 *   <li>{@code radiusUpgradeCount} — сколько раз радиус уже прокачан (cost *= 2^(n))</li>
 *   <li>{@code repairCount} — сколько раз целостность уже починена (cost *= 2^(n))</li>
 *   <li>{@code whitelist} — UUID игроков, имеющих доступ к территории и GUI</li>
 * </ul>
 */
public class ProtectionBlock {

    private final UUID id;
    private final World world;
    private final int x;
    private final int y;
    private final int z;

    private UUID owner;
    private int radius;
    private double integrity;
    private int points;
    private boolean enabled;

    private int radiusUpgradeCount;
    private int repairCount;

    /** Whitelist: UUID игроков, имеющих право взаимодействовать с территорией и открывать GUI.
     *  {@link CopyOnWriteArraySet} — потому что этот список читается с async-потоков
     *  (dirty-retry таск ProtectionManager.saveWhitelist итерирует его на ретрае)
     *  и пишется с main-потоков (addToWhitelist из GUI/команды). Обычный LinkedHashSet
     *  даёт ConcurrentModificationException при таком паттерне. */
    private final Set<UUID> whitelist = new CopyOnWriteArraySet<>();

    public ProtectionBlock(UUID id, Location loc, UUID owner, int radius, double integrity, int points, boolean enabled) {
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        if (loc == null || loc.getWorld() == null) throw new IllegalArgumentException("location must have a world");
        this.id = id;
        this.world = loc.getWorld();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
        this.owner = owner;
        this.radius = radius;
        this.integrity = integrity;
        this.points = points;
        this.enabled = enabled;
    }

    public UUID getId() { return id; }
    public World getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public Location getLocation() {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    public Location getBlockLocation() {
        return new Location(world, x, y, z);
    }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }

    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }

    public double getIntegrity() { return integrity; }
    public void setIntegrity(double integrity) { this.integrity = Math.max(0.0, Math.min(100.0, integrity)); }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getRadiusUpgradeCount() { return radiusUpgradeCount; }
    public void setRadiusUpgradeCount(int n) { this.radiusUpgradeCount = n; }

    public int getRepairCount() { return repairCount; }
    public void setRepairCount(int n) { this.repairCount = n; }

    public Set<UUID> getWhitelist() { return whitelist; }

    public void addToWhitelist(UUID playerId) {
        whitelist.add(playerId);
    }

    public void removeFromWhitelist(UUID playerId) {
        whitelist.remove(playerId);
    }

    public boolean isWhitelisted(UUID playerId) {
        return whitelist.contains(playerId);
    }

    /**
     * Текущая стоимость в очках за следующее улучшение радиуса.
     * Стоимость = baseCost × 2^(radiusUpgradeCount), clamped.
     * <p>
     * O(1) через побитовый сдвиг в long, со сдвигом не более 30 бит,
     * чтобы избежать переполнения даже при больших baseCost.
     */
    public int getRadiusUpgradeCost() {
        int baseCost = ProtectionConfig.getRadiusUpgradeBaseCost();
        if (baseCost <= 0) return Integer.MAX_VALUE;
        int n = Math.min(radiusUpgradeCount, 30);
        long cost = ((long) baseCost) << n;
        if (cost < 0 || cost > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) cost;
    }

    /**
     * Текущая стоимость в очках за следующий ремонт целостности.
     * Стоимость = baseCost × 2^(repairCount), clamped.
     * <p>
     * O(1) через побитовый сдвиг в long, со сдвигом не более 30 бит,
     * чтобы избежать переполнения даже при больших baseCost.
     */
    public int getRepairCost() {
        int baseCost = ProtectionConfig.getRepairBaseCost();
        if (baseCost <= 0) return Integer.MAX_VALUE;
        int n = Math.min(repairCount, 30);
        long cost = ((long) baseCost) << n;
        if (cost < 0 || cost > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) cost;
    }

    /**
     * True если блок имеет целостность > 0 И включён.
     * <p>
     * ВАЖНО: семантика — «живой И активный», строго как говорят docstring и callers
     * (findProtectingBlock, applyExplosionProtection, triggerIntruderEffects внутри).
     * Раньше здесь был только {@code integrity > 0.0}, что создавало расхождение
     * с остальным кодом и приводило к phantom-accumulation integrity loss на disabled-копиях.
     */
    public boolean isAlive() {
        return integrity > 0.0 && enabled;
    }

    /** True если блок сохранил целостность > 0 (без учёта enabled). Для HUD/GUI. */
    public boolean hasIntegrity() {
        return integrity > 0.0;
    }
}
