package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.core.Keys;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 🧊 Manages the "Concrete Bucket" mechanic.
 * <p>
 * When a Concrete Bucket is poured:
 * <ol>
 *   <li>The water is tinted gray by setting the biome to {@code PALE_GARDEN}</li>
 *   <li>The original biome is saved for restoration</li>
 *   <li>If the water stays &gt; 60 seconds → it turns into cobblestone (concrete)</li>
 *   <li>If the water disappears earlier → the biome is restored</li>
 * </ol>
 */
public class ConcreteBucketManager extends BukkitRunnable implements Listener {

    private static ConcreteBucketManager instance;
    private static Main plugin;
    private static final long CONCRETE_DELAY_MS = 60_000; // 60 seconds

    // Tracked water blocks: location -> {originalBiome, placeTime}
    private static final Map<Location, ConcreteWater> trackedWater = new HashMap<>();
    // Pending biome sets (the water appears after 1 tick)
    private static final Map<Location, Biome> pendingBiomes = new HashMap<>();

    private record ConcreteWater(Biome originalBiome, long placeTime, UUID groupId) {}

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        ConcreteBucketManager.plugin = plugin;
        instance = new ConcreteBucketManager();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        // Check every tick (smooth conversion)
        instance.runTaskTimer(plugin, 20L, 20L);
        ConsoleLogger.info("[ConcreteBucket] Initialized");
    }

    /**
     * Checks whether an item is a Concrete Bucket.
     */
    private static boolean isConcreteBucket(ItemStack item) {
        if (item == null || item.getType() != Material.WATER_BUCKET) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.CONCRETE_BUCKET, PersistentDataType.BYTE);
    }

    // =========================
    // 🪣 POURING — the player uses the bucket
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (e.getBucket() != Material.WATER_BUCKET) return;

        // Check the PDC on the item in the player's hand
        ItemStack mainHand = e.getPlayer().getInventory().getItemInMainHand();
        if (!isConcreteBucket(mainHand)) {
            ItemStack offHand = e.getPlayer().getInventory().getItemInOffHand();
            if (!isConcreteBucket(offHand)) return;
        }

        // Position where the water is placed (blockClicked + blockFace)
        Block clicked = e.getBlockClicked();
        if (clicked == null) return;

        Block waterBlock = clicked.getRelative(e.getBlockFace());
        Location waterLoc = waterBlock.getLocation();
        if (waterLoc.getWorld() == null) return;

        // Save the original biome
        Biome originalBiome = waterLoc.getWorld().getBiome(waterLoc.getBlockX(), waterLoc.getBlockY(), waterLoc.getBlockZ());
        // If the location was already tracked — reuse the original biome (otherwise PALE_GARDEN would count as the "original")
        ConcreteWater existing = trackedWater.get(waterLoc);
        if (existing != null) {
            originalBiome = existing.originalBiome();
        }

        // The water is NOT placed yet — defer the biome set by 1 tick
        pendingBiomes.put(waterLoc, Biome.PALE_GARDEN);
        // Track the water block with a unique groupId for this pour
        UUID groupId = UUID.randomUUID();
        trackedWater.put(waterLoc, new ConcreteWater(originalBiome, System.currentTimeMillis(), groupId));

        // 🪣 Paper itself replaces WATER_BUCKET with a plain BUCKET after this event.
        // The PDC is lost on replacement, so the bucket becomes a normal one.
    }

    // =========================
    // 💧 WATER FLOW — new water also gets the PALE_GARDEN biome
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        Block toBlock = e.getToBlock();
        Location toLoc = toBlock.getLocation();

        // If the water flows onto an already-tracked block — that's our new water block
        Block fromBlock = e.getBlock();
        Location fromLoc = fromBlock.getLocation();

        ConcreteWater fromData = trackedWater.get(fromLoc);
        if (fromData != null && toBlock.getType() == Material.WATER) {
            // A new water batch — inherit the groupId from the source
            pendingBiomes.put(toLoc, Biome.PALE_GARDEN);
            trackedWater.put(toLoc, new ConcreteWater(fromData.originalBiome(), System.currentTimeMillis(), fromData.groupId()));
        }

        // If the destination block was tracked water but is now replaced by something
        // else (e.g. lava) — restore the biome
        ConcreteWater toData = trackedWater.remove(toLoc);
        if (toData != null && toBlock.getType() != Material.WATER) {
            restoreBiome(toLoc, toData.originalBiome());
        }
    }

    // =========================
    // 🔥 WATER DISAPPEARANCE — the block physics changed
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent e) {
        Location loc = e.getBlock().getLocation();
        ConcreteWater data = trackedWater.get(loc);
        if (data == null) return;

        if (e.getBlock().getType() != Material.WATER) {
            trackedWater.remove(loc);
            restoreBiome(loc, data.originalBiome());
        }
    }

    // =========================
    // 🔨 BLOCK BROKEN — if water is broken (with a bucket or by hand)
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        ConcreteWater data = trackedWater.remove(loc);
        if (data == null) return;
        restoreBiome(loc, data.originalBiome());
    }

    // =========================
    // ⏱ TICK — applying deferred biomes + concrete check
    // =========================
    @Override
    public void run() {
        // ════════════════════════════════════════
        // Process the deferred biomes
        // ════════════════════════════════════════
        if (!pendingBiomes.isEmpty()) {
            Iterator<Map.Entry<Location, Biome>> pit = pendingBiomes.entrySet().iterator();
            while (pit.hasNext()) {
                Map.Entry<Location, Biome> entry = pit.next();
                Location loc = entry.getKey();
                if (loc.getBlock().getType() == Material.WATER) {
                    // World.setBiome() sends an update to the client (unlike Block.setBiome())
                    loc.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), entry.getValue());
                    pit.remove();
                }
                // If the water hasn't appeared yet — wait for the next tick
            }
        }

        // ════════════════════════════════════════
        // Check for concrete conversion after 60 seconds
        // ════════════════════════════════════════
        if (trackedWater.isEmpty()) return;

        long now = System.currentTimeMillis();
        // Collect the groupIds of blocks ready to become concrete
        Set<UUID> readyGroups = new HashSet<>();
        Iterator<Map.Entry<Location, ConcreteWater>> it = trackedWater.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Location, ConcreteWater> entry = it.next();
            Location loc = entry.getKey();
            ConcreteWater data = entry.getValue();

            // Still water?
            if (loc.getBlock().getType() != Material.WATER) {
                it.remove();
                restoreBiome(loc, data.originalBiome());
                continue;
            }

            // 60 seconds passed? Add the WHOLE group to readyGroups
            if (now - data.placeTime() >= CONCRETE_DELAY_MS) {
                readyGroups.add(data.groupId());
            }
        }

        // Convert ALL water in readyGroups to concrete at once
        if (!readyGroups.isEmpty()) {
            it = trackedWater.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Location, ConcreteWater> entry = it.next();
                if (readyGroups.contains(entry.getValue().groupId())) {
                    Location loc = entry.getKey();
                    if (loc.getBlock().getType() == Material.WATER) {
                        loc.getBlock().setType(Material.GRAY_CONCRETE);
                    }
                    it.remove();
                    // Do NOT restore the biome — the concrete tinted the ground
                }
            }
        }
    }

    // =========================
    // 🔄 BIOME RESTORATION
    // =========================
    private static void restoreBiome(Location loc, Biome biome) {
        if (biome != null && biome != Biome.PALE_GARDEN && loc.getWorld() != null) {
            loc.getWorld().setBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), biome);
        }
    }

    // =========================
    // 🧹 SHUTDOWN
    // =========================
    public static void shutdown() {
        if (instance != null) {
            instance.cancel();
            instance = null;
        }
        trackedWater.clear();
        pendingBiomes.clear();
    }
}
