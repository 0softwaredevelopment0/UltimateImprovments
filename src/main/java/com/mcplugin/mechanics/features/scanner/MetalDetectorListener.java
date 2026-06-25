package com.mcplugin.mechanics.features.scanner;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class MetalDetectorListener implements Listener {

    private static final long COOLDOWN_MS = 1000L;
    private static final int SCAN_RADIUS = 16;
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static long lastCleanup = System.currentTimeMillis();

    // ========================================================================
    // METAL MATERIALS
    // ========================================================================
    private static final Set<Material> METAL_BLOCKS = new HashSet<>(Set.of(
            // Ores
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,

            // Raw ore blocks
            Material.RAW_IRON_BLOCK, Material.RAW_GOLD_BLOCK, Material.RAW_COPPER_BLOCK,

            // Pure metal blocks
            Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.COPPER_BLOCK,
            Material.NETHERITE_BLOCK, Material.REDSTONE_BLOCK,

            // Metal utility blocks
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.HOPPER, Material.IRON_DOOR, Material.IRON_TRAPDOOR,
            Material.IRON_BARS, Material.CAULDRON, Material.COPPER_DOOR, Material.COPPER_TRAPDOOR,
            Material.LANTERN, Material.SOUL_LANTERN, Material.LIGHTNING_ROD,
            Material.GRINDSTONE, Material.BLAST_FURNACE,
            Material.PISTON, Material.STICKY_PISTON,
            Material.OBSERVER, Material.DISPENSER, Material.DROPPER,
            Material.LEVER, Material.TRIPWIRE_HOOK,
            Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE,

            // Rails
            Material.RAIL, Material.POWERED_RAIL,
            Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL,

            // Redstone components
            Material.REPEATER, Material.COMPARATOR,
            Material.TARGET, Material.DAYLIGHT_DETECTOR,
            Material.SMITHING_TABLE
    ));

    private static final Set<Material> METAL_ITEMS = new HashSet<>(Set.of(
            // Tools & Weapons
            Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE,
            Material.IRON_SHOVEL, Material.IRON_HOE,
            Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE,
            Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE,
            Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE,
            Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,

            // Armor
            Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,

            // Chainmail
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
            Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,

            // Ingots & Nuggets
            Material.IRON_INGOT, Material.GOLD_INGOT, Material.COPPER_INGOT,
            Material.NETHERITE_INGOT, Material.NETHERITE_SCRAP,
            Material.IRON_NUGGET, Material.GOLD_NUGGET,

            // Raw ores
            Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER,

            // Other metal items
            Material.BUCKET, Material.WATER_BUCKET, Material.LAVA_BUCKET,
            Material.MILK_BUCKET, Material.COD_BUCKET, Material.SALMON_BUCKET,
            Material.PUFFERFISH_BUCKET, Material.TROPICAL_FISH_BUCKET,
            Material.AXOLOTL_BUCKET, Material.TADPOLE_BUCKET,
            Material.COMPASS, Material.CLOCK, Material.SPYGLASS,
            Material.SHEARS, Material.FLINT_AND_STEEL, Material.SHIELD,
            Material.MINECART, Material.CHEST_MINECART,
            Material.HOPPER_MINECART, Material.FURNACE_MINECART,
            Material.TNT_MINECART,
            Material.CHAINMAIL_CHESTPLATE, // already listed

            // Scrap
            Material.IRON_HORSE_ARMOR, Material.GOLDEN_HORSE_ARMOR,
            Material.DIAMOND_HORSE_ARMOR
    ));

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (!meta.getPersistentDataContainer().has(Keys.METAL_DETECTOR, PersistentDataType.BYTE)) return;

        // Cancel always to prevent default stick behavior
        e.setCancelled(true);

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Cooldown check
        Long last = cooldowns.get(uid);
        if (last != null && (now - last) < COOLDOWN_MS) {
            long remaining = ((COOLDOWN_MS - (now - last)) / 1000) + 1;
            player.sendActionBar(MessageUtil.parse("<red>⏳ Подождите " + remaining + " сек.</red>"));
            return;
        }

        cooldowns.put(uid, now);
        cleanupCooldowns();

        handleMetalDetector(player);
    }

    // ========================================================================
    // METAL DETECTOR LOGIC
    // ========================================================================
    private void handleMetalDetector(Player player) {
        Location playerLoc = player.getLocation();
        int pX = playerLoc.getBlockX();
        int pY = playerLoc.getBlockY();
        int pZ = playerLoc.getBlockZ();

        // Results
        List<MetalResult> foundBlocks = new ArrayList<>();
        List<MetalResult> foundItems = new ArrayList<>();
        List<MetalResult> foundEntities = new ArrayList<>();

        // =========================
        // 1. SCAN BLOCKS
        // =========================
        int minY = Math.max(player.getWorld().getMinHeight(), pY - SCAN_RADIUS);
        int maxY = Math.min(player.getWorld().getMaxHeight(), pY + SCAN_RADIUS);

        for (int x = pX - SCAN_RADIUS; x <= pX + SCAN_RADIUS; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = pZ - SCAN_RADIUS; z <= pZ + SCAN_RADIUS; z++) {
                    Block block = player.getWorld().getBlockAt(x, y, z);
                    if (METAL_BLOCKS.contains(block.getType())) {
                        double dist = playerLoc.distanceSquared(block.getLocation().add(0.5, 0.5, 0.5));
                        if (dist <= SCAN_RADIUS * SCAN_RADIUS) {
                            foundBlocks.add(new MetalResult(block.getType(), block.getLocation(), Math.sqrt(dist)));
                        }
                    }
                }
            }
        }

        // =========================
        // 2. SCAN ITEMS ON GROUND
        // =========================
        for (Entity entity : player.getNearbyEntities(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
            if (!(entity instanceof Item itemEntity)) continue;

            ItemStack stack = itemEntity.getItemStack();
            if (stack == null || stack.getType() == Material.AIR) continue;

            String itemName = getItemName(stack);

            // Check if the item is metal
            if (METAL_ITEMS.contains(stack.getType())) {
                double dist = playerLoc.distance(entity.getLocation());
                foundItems.add(new MetalResult(stack.getType(), entity.getLocation(), dist, itemName));
            }
            // Also check if it's a metal block item (e.g. IRON_BLOCK as item)
            else if (stack.getType().isBlock() && METAL_BLOCKS.contains(stack.getType())) {
                double dist = playerLoc.distance(entity.getLocation());
                foundItems.add(new MetalResult(stack.getType(), entity.getLocation(), dist, itemName));
            }
        }

        // =========================
        // 3. SCAN ENTITIES WITH METAL IN INVENTORY
        // =========================
        for (Entity entity : player.getNearbyEntities(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
            if (entity == player) continue;
            if (entity instanceof Item) continue; // already scanned as items
            if (!(entity instanceof LivingEntity living)) continue;

            boolean hasMetal = false;

            // Check equipment
            EntityEquipment equip = living.getEquipment();
            if (equip != null) {
                for (ItemStack armorStack : equip.getArmorContents()) {
                    if (armorStack != null && armorStack.getType() != Material.AIR) {
                        if (METAL_ITEMS.contains(armorStack.getType())) {
                            hasMetal = true;
                            break;
                        }
                    }
                }
                if (!hasMetal) {
                    ItemStack mainHand = equip.getItemInMainHand();
                    if (mainHand != null && mainHand.getType() != Material.AIR) {
                        if (METAL_ITEMS.contains(mainHand.getType())) {
                            hasMetal = true;
                        }
                    }
                }
                if (!hasMetal) {
                    ItemStack offHand = equip.getItemInOffHand();
                    if (offHand != null && offHand.getType() != Material.AIR) {
                        if (METAL_ITEMS.contains(offHand.getType())) {
                            hasMetal = true;
                        }
                    }
                }
            }

            if (hasMetal) {
                double dist = playerLoc.distance(entity.getLocation());
                String name = living.getType().name().toLowerCase().replace('_', ' ');
                if (!name.isEmpty()) {
                    name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                }
                if (living.getCustomName() != null) {
                    name = living.getCustomName();
                }
                foundEntities.add(new MetalResult(null, entity.getLocation(), dist, name));
            }
        }

        // =========================
        // 4. SORT AND DISPLAY
        // =========================
        foundBlocks.sort(Comparator.comparingDouble(r -> r.distance));
        foundItems.sort(Comparator.comparingDouble(r -> r.distance));
        foundEntities.sort(Comparator.comparingDouble(r -> r.distance));

        int totalMetal = foundBlocks.size() + foundItems.size() + foundEntities.size();

        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gold>=== <white>Metal Detector</white> ===</gold>"));

        if (totalMetal == 0) {
            player.sendMessage(MessageUtil.parse("<gray>No metal detected within " + SCAN_RADIUS + " blocks.</gray>"));
        } else {
            player.sendMessage(MessageUtil.parse("<gray>Total metal: <white>" + totalMetal + "</white></gray>"));

            // Nearest metal overall
            List<MetalResult> all = new ArrayList<>();
            all.addAll(foundBlocks.subList(0, Math.min(1, foundBlocks.size())));
            all.addAll(foundItems.subList(0, Math.min(1, foundItems.size())));
            all.addAll(foundEntities.subList(0, Math.min(1, foundEntities.size())));
            all.sort(Comparator.comparingDouble(r -> r.distance));

            if (!all.isEmpty()) {
                MetalResult nearest = all.get(0);
                String nearestName = nearest.label != null ? nearest.label : formatMaterial(nearest.material);
                player.sendMessage(MessageUtil.parse(
                        "<green>⚡</green> <white>Nearest metal:</white> " + distanceColor(nearest.distance) +
                        nearestName + " <gray>(" + (int) nearest.distance + " blocks)</gray>"));
                player.sendActionBar(MessageUtil.parse(
                        "<green>⚡</green> <white>" + nearestName + "</white> " +
                        distanceColor(nearest.distance) + (int) nearest.distance + "м <gray>[" +
                        nearest.location.getBlockX() + ", " + nearest.location.getBlockY() + ", " +
                        nearest.location.getBlockZ() + "]</gray>"));

                // Ping sound based on distance
                float pitch = (float) Math.max(0.5f, 2.0f - (nearest.distance / SCAN_RADIUS) * 1.5f);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, pitch);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, pitch * 0.8f);
            }

            if (!foundBlocks.isEmpty()) {
                player.sendMessage(Component.empty());
                player.sendMessage(MessageUtil.parse("<gray>⛏ <white>Blocks:</white> <dark_gray>(" + foundBlocks.size() + ")</dark_gray></gray>"));
                int show = Math.min(5, foundBlocks.size());
                for (int i = 0; i < show; i++) {
                    MetalResult r = foundBlocks.get(i);
                    player.sendMessage(MessageUtil.parse(
                            "  " + distanceColor(r.distance) + "▪</" + distanceColor(r.distance).substring(1) +
                            " <white>" + formatMaterial(r.material) + "</white> " +
                            distanceColor(r.distance) + (int) r.distance + "м<reset>"));
                }
                if (foundBlocks.size() > 5) {
                    player.sendMessage(MessageUtil.parse("  <dark_gray>... and " + (foundBlocks.size() - 5) + " more</dark_gray>"));
                }
            }

            if (!foundItems.isEmpty()) {
                player.sendMessage(Component.empty());
                player.sendMessage(MessageUtil.parse("<gray>📦 <white>Items:</white> <dark_gray>(" + foundItems.size() + ")</dark_gray></gray>"));
                int show = Math.min(5, foundItems.size());
                for (int i = 0; i < show; i++) {
                    MetalResult r = foundItems.get(i);
                    player.sendMessage(MessageUtil.parse(
                            "  " + distanceColor(r.distance) + "▪</" + distanceColor(r.distance).substring(1) +
                            " <white>" + r.label + "</white> " +
                            distanceColor(r.distance) + (int) r.distance + "м<reset>"));
                }
                if (foundItems.size() > 5) {
                    player.sendMessage(MessageUtil.parse("  <dark_gray>... and " + (foundItems.size() - 5) + " more</dark_gray>"));
                }
            }

            if (!foundEntities.isEmpty()) {
                player.sendMessage(Component.empty());
                player.sendMessage(MessageUtil.parse("<gray>👾 <white>Entities:</white> <dark_gray>(" + foundEntities.size() + ")</dark_gray></gray>"));
                int show = Math.min(5, foundEntities.size());
                for (int i = 0; i < show; i++) {
                    MetalResult r = foundEntities.get(i);
                    player.sendMessage(MessageUtil.parse(
                            "  " + distanceColor(r.distance) + "▪</" + distanceColor(r.distance).substring(1) +
                            " <white>" + r.label + "</white> " +
                            distanceColor(r.distance) + (int) r.distance + "м<reset>"));
                }
                if (foundEntities.size() > 5) {
                    player.sendMessage(MessageUtil.parse("  <dark_gray>... and " + (foundEntities.size() - 5) + " more</dark_gray>"));
                }
            }
        }

        player.sendMessage(MessageUtil.parse("<gold>=======================</gold>"));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static String formatMaterial(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        // Replace " blocks", " ores" with cleaner formatting
        return name;
    }

    private static String getItemName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        return formatMaterial(stack.getType());
    }

    private static String distanceColor(double dist) {
        double ratio = dist / SCAN_RADIUS;
        if (ratio <= 0.25) return "<green>";
        if (ratio <= 0.5) return "<#66FF00>";
        if (ratio <= 0.75) return "<yellow>";
        return "<red>";
    }

    // ========================================================================
    // RESULT DATA CLASS
    // ========================================================================
    private static class MetalResult {
        final Material material;
        final Location location;
        final double distance;
        final String label;

        MetalResult(Material material, Location location, double distance) {
            this.material = material;
            this.location = location;
            this.distance = distance;
            this.label = null;
        }

        MetalResult(Material material, Location location, double distance, String label) {
            this.material = material;
            this.location = location;
            this.distance = distance;
            this.label = label;
        }
    }

    // ========================================================================
    // COOLDOWN CLEANUP
    // ========================================================================
    private static void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < 30_000L) return;
        lastCleanup = now;
        cooldowns.entrySet().removeIf(e -> (now - e.getValue()) > 60_000L);
    }
}
