package com.mcplugin.mechanics.features.scanner;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ScannerItemListener implements Listener {

    // 1 second cooldown (anti-spam)
    private static final long COOLDOWN_MS = 1000L;
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static long lastCleanup = System.currentTimeMillis();

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Global cooldown check
        Long last = cooldowns.get(uid);
        if (last != null && (now - last) < COOLDOWN_MS) {
            long remaining = ((COOLDOWN_MS - (now - last)) / 1000) + 1;
            player.sendActionBar(MessageUtil.parse("<red>⏳ Подождите " + remaining + " сек.</red>"));
            return;
        }

        // Check which item
        if (pdc.has(Keys.HEALTH_METER, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            cooldowns.put(uid, now);
            cleanupCooldowns();
            handleHealthMeter(player);
        } else if (pdc.has(Keys.ORE_FINDER, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            cooldowns.put(uid, now);
            cleanupCooldowns();
            handleOreFinder(player);
        } else if (pdc.has(Keys.MOB_FINDER, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            cooldowns.put(uid, now);
            cleanupCooldowns();
            handleMobFinder(player);
        } else if (pdc.has(Keys.RADAR, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            cooldowns.put(uid, now);
            cleanupCooldowns();
            handleRadar(player);
        }
    }

    // ========================================================================
    // HEALTH METER
    // ========================================================================
    private void handleHealthMeter(Player player) {
        Entity target = player.getTargetEntity(5);
        if (target == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Нет существа в пределах 5 блоков!</red>"));
            return;
        }
        if (!(target instanceof LivingEntity living)) {
            player.sendMessage(MessageUtil.parse("<red>❌ Это существо не имеет здоровья!</red>"));
            return;
        }
        if (target instanceof Player) {
            player.sendMessage(MessageUtil.parse("<red>❌ Нельзя проверить здоровье игрока!</red>"));
            return;
        }

        double maxHealth = living.getMaxHealth();
        double currentHealth = living.getHealth();
        String hpFormatted = String.format("%.1f", currentHealth);
        String maxHpFormatted = String.format("%.1f", maxHealth);

        // Color based on health %
        String colorTag = healthColor(currentHealth / maxHealth);

        String entityName = living.getType().name().toLowerCase().replace('_', ' ');
        if (!entityName.isEmpty()) {
            entityName = Character.toUpperCase(entityName.charAt(0)) + entityName.substring(1);
        }
        if (living.getCustomName() != null) {
            entityName = living.getCustomName();
        }

        // Health bar (100 chars)
        double pct = currentHealth / maxHealth;
        int filledBars = (int) Math.round(pct * 100);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i < filledBars) {
                double blockPct = (double) (i + 1) / 100;
                bar.append(healthColor(blockPct)).append("|</").append(healthColor(blockPct).substring(1));
            } else {
                bar.append("<dark_gray>|</dark_gray>");
            }
        }

        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gold>=== <white>Здоровье существа</white> ==="));
        player.sendMessage(MessageUtil.parse("<gray>Существо: </gray><white>" + entityName + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Здоровье: </gray>" + colorTag + hpFormatted + "<reset><gray>/</gray>" + colorTag + maxHpFormatted + "<reset>"));
        player.sendMessage(MessageUtil.parse(bar.toString()));
        player.sendMessage(MessageUtil.parse("<gold>======================="));
    }

    private static final int[][] GRADIENT_WAYPOINTS = {
        {0x8B, 0x00, 0x00},
        {0xFF, 0x00, 0x00},
        {0xFF, 0x8C, 0x00},
        {0xFF, 0xFF, 0x00},
        {0x00, 0xFF, 0x00},
        {0x00, 0x66, 0x00},
    };

    private static String healthColor(double pct) {
        if (pct <= 0.0) return formatHex(GRADIENT_WAYPOINTS[0]);
        if (pct >= 1.0) return formatHex(GRADIENT_WAYPOINTS[5]);

        double scaled = pct * 5.0;
        int segment = (int) scaled;
        double t = scaled - segment;
        if (segment >= 4) { segment = 4; t = Math.min(t, 1.0); }

        int[] from = GRADIENT_WAYPOINTS[segment];
        int[] to = GRADIENT_WAYPOINTS[segment + 1];
        int r = (int) Math.round(from[0] + (to[0] - from[0]) * t);
        int g = (int) Math.round(from[1] + (to[1] - from[1]) * t);
        int b = (int) Math.round(from[2] + (to[2] - from[2]) * t);
        return formatHex(new int[]{r, g, b});
    }

    private static String formatHex(int[] rgb) {
        return String.format("<#%02X%02X%02X>",
            Math.max(0, Math.min(255, rgb[0])),
            Math.max(0, Math.min(255, rgb[1])),
            Math.max(0, Math.min(255, rgb[2])));
    }

    // ========================================================================
    // ORE FINDER
    // ========================================================================
    private static final Map<Material, String> ORE_MATERIALS = new EnumMap<>(Map.ofEntries(
        Map.entry(Material.COAL_ORE, "Coal"),
        Map.entry(Material.DEEPSLATE_COAL_ORE, "Deepslate Coal"),
        Map.entry(Material.IRON_ORE, "Iron"),
        Map.entry(Material.DEEPSLATE_IRON_ORE, "Deepslate Iron"),
        Map.entry(Material.COPPER_ORE, "Copper"),
        Map.entry(Material.DEEPSLATE_COPPER_ORE, "Deepslate Copper"),
        Map.entry(Material.GOLD_ORE, "Gold"),
        Map.entry(Material.DEEPSLATE_GOLD_ORE, "Deepslate Gold"),
        Map.entry(Material.REDSTONE_ORE, "Redstone"),
        Map.entry(Material.DEEPSLATE_REDSTONE_ORE, "Deepslate Redstone"),
        Map.entry(Material.LAPIS_ORE, "Lapis Lazuli"),
        Map.entry(Material.DEEPSLATE_LAPIS_ORE, "Deepslate Lapis"),
        Map.entry(Material.DIAMOND_ORE, "Diamond"),
        Map.entry(Material.DEEPSLATE_DIAMOND_ORE, "Deepslate Diamond"),
        Map.entry(Material.EMERALD_ORE, "Emerald"),
        Map.entry(Material.DEEPSLATE_EMERALD_ORE, "Deepslate Emerald"),
        Map.entry(Material.NETHER_GOLD_ORE, "Nether Gold"),
        Map.entry(Material.NETHER_QUARTZ_ORE, "Nether Quartz"),
        Map.entry(Material.ANCIENT_DEBRIS, "Ancient Debris")
    ));

    private void handleOreFinder(Player player) {
        int chunkX = player.getChunk().getX();
        int chunkZ = player.getChunk().getZ();
        String worldName = player.getWorld().getName();

        player.sendMessage(MessageUtil.parse(
                "<gray>⛏ Сканирование чанка [</gray><yellow>" + chunkX + ", " + chunkZ + "</yellow><gray>] в </gray><yellow>" + worldName + "</yellow><gray>...</gray>"));

        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        int totalOres = 0;
        int minY = player.getWorld().getMinHeight();
        int maxY = player.getWorld().getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = player.getWorld().getBlockAt(chunkX * 16 + x, y, chunkZ * 16 + z);
                    Material type = block.getType();
                    if (ORE_MATERIALS.containsKey(type)) {
                        counts.merge(type, 1, Integer::sum);
                        totalOres++;
                    }
                }
            }
        }

        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gold>=== <white>Руды в чанке</white> [<yellow>" + chunkX + ", " + chunkZ + "</yellow>] <gray>(" + worldName + ")</gray> ==="));
        player.sendMessage(MessageUtil.parse("<gray>Всего руд: <white>" + totalOres + "</white></gray>"));

        if (totalOres > 0) {
            player.sendMessage(Component.empty());
            ORE_MATERIALS.entrySet().stream()
                .filter(e -> counts.getOrDefault(e.getKey(), 0) > 0)
                .sorted(Comparator.<Map.Entry<Material, String>>comparingInt(
                    e -> counts.get(e.getKey())).reversed()
                    .thenComparing(Map.Entry.comparingByValue()))
                .forEach(e -> {
                    int count = counts.get(e.getKey());
                    player.sendMessage(MessageUtil.parse(
                            "  <gray>▪</gray> <white>" + e.getValue() + "</white><gray>: <yellow>" + count + "</yellow></gray>"));
                });
        }

        player.sendMessage(MessageUtil.parse("<gold>================================"));
    }

    // ========================================================================
    // MOB FINDER
    // ========================================================================
    private void handleMobFinder(Player player) {
        int chunkX = player.getChunk().getX();
        int chunkZ = player.getChunk().getZ();
        String worldName = player.getWorld().getName();

        player.sendMessage(MessageUtil.parse(
                "<gray>🔍 Сканирование чанка [</gray><yellow>" + chunkX + ", " + chunkZ + "</yellow><gray>] в </gray><yellow>" + worldName + "</yellow><gray>...</gray>"));

        Map<String, Integer> counts = new HashMap<>();
        int totalEntities = 0;

        for (Entity entity : player.getChunk().getEntities()) {
            if (entity == player) continue;

            String typeName = getEntityCategory(entity);
            if (typeName == null) continue;

            counts.merge(typeName, 1, Integer::sum);
            totalEntities++;
        }

        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gold>=== <white>Сущности в чанке</white> [<yellow>" + chunkX + ", " + chunkZ + "</yellow>] <gray>(" + worldName + ")</gray> ==="));
        player.sendMessage(MessageUtil.parse("<gray>Всего сущностей: <white>" + totalEntities + "</white></gray>"));

        if (totalEntities > 0) {
            player.sendMessage(Component.empty());
            counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> player.sendMessage(MessageUtil.parse(
                        "  <gray>▪</gray> <white>" + e.getKey() + "</white><gray>: <yellow>" + e.getValue() + "</yellow></gray>")));
        }

        player.sendMessage(MessageUtil.parse("<gold>==================================="));
    }

    private static String getEntityCategory(Entity entity) {
        if (entity instanceof org.bukkit.entity.Item) return null;
        if (entity instanceof org.bukkit.entity.ThrownPotion) return null;

        var type = entity.getType();
        // Skip players, armor stands, item frames, etc.
        return switch (type) {
            case PLAYER, ARMOR_STAND, ITEM_FRAME, GLOW_ITEM_FRAME,
                 LEASH_KNOT, MARKER, INTERACTION, TEXT_DISPLAY,
                 BLOCK_DISPLAY, ITEM_DISPLAY, FALLING_BLOCK,
                 AREA_EFFECT_CLOUD, LIGHTNING_BOLT, PAINTING,
                 EVOKER_FANGS, SHULKER_BULLET, WIND_CHARGE,
                 BREEZE_WIND_CHARGE, EXPERIENCE_ORB,
                 ARROW, SPECTRAL_ARROW, TRIDENT,
                 FIREBALL, SMALL_FIREBALL, DRAGON_FIREBALL,
                 FIREWORK_ROCKET, EGG, SNOWBALL, ENDER_PEARL,
                 LLAMA_SPIT, FISHING_BOBBER -> null;
            default -> {
                String name = type.name().toLowerCase().replace('_', ' ');
                // Capitalize
                String[] words = name.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    if (!w.isEmpty()) {
                        sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
                    }
                }
                yield sb.toString().trim();
            }
        };
    }

    // ========================================================================
    // PORTABLE RADAR
    // ========================================================================
    private void handleRadar(Player player) {
        int radius = 64;
        Location playerLoc = player.getLocation();

        Entity nearest = null;
        double nearestDist = radius + 1;

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player) continue;
            if (e instanceof org.bukkit.entity.Item) continue;
            if (!e.getWorld().equals(player.getWorld())) continue;

            double dist = playerLoc.distance(e.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = e;
            }
        }

        if (nearest == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Нет сущностей в радиусе 64 блоков!</red>"));
            return;
        }

        // Entity info
        String entityName = nearest.getType().name().toLowerCase().replace('_', ' ');
        if (!entityName.isEmpty()) {
            entityName = Character.toUpperCase(entityName.charAt(0)) + entityName.substring(1);
        }
        if (nearest.getCustomName() != null) {
            entityName = nearest.getCustomName();
        }

        Location loc = nearest.getLocation();
        int dx = Math.abs(loc.getBlockX() - playerLoc.getBlockX());
        int dy = Math.abs(loc.getBlockY() - playerLoc.getBlockY());
        int dz = Math.abs(loc.getBlockZ() - playerLoc.getBlockZ());

        // Direction indicator
        String dirX = loc.getBlockX() >= playerLoc.getBlockX() ? "восток" : "запад";
        String dirZ = loc.getBlockZ() >= playerLoc.getBlockZ() ? "юг" : "север";
        String dirY = loc.getBlockY() >= playerLoc.getBlockY() ? "↑" : "↓";

        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gold>=== <white>Портативный радар</white> ==="));
        player.sendMessage(MessageUtil.parse("<gray>Существо: </gray><white>" + entityName + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Координаты: </gray><white>" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Мир: </gray><yellow>" + loc.getWorld().getName() + "</yellow>"));
        player.sendMessage(MessageUtil.parse("<gray>Расстояние: </gray><white>" + String.format("%.1f", nearestDist) + "</white> <gray>блоков</gray>"));
        player.sendMessage(MessageUtil.parse("<gray>Направление: </gray><white>" + dirX + " " + dirZ + " " + dirY + "</white>"));
        player.sendMessage(MessageUtil.parse("<gold>==========================="));

        // Ping sound to indicate scan result
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 1.5f);
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
