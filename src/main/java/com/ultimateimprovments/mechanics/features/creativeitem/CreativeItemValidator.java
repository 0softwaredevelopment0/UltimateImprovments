package com.ultimateimprovments.mechanics.features.creativeitem;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Фича: блокировка предметов с чрезмерной метадатой (NBT/компоненты)
 * при попытке взять их в креативном режиме.
 *
 * Защищает сервер от "краш-предметов" — вещей с гигантским NBT,
 * включая вложенные контейнеры (shulker в shulker в shulker...),
 * которые при загрузке забивают ОЗУ и крашат сервер.
 *
 * Проверки:
 *   1. Рекурсивный обход контейнеров (shulker, bundle) — подсчёт total NBT size
 *   2. Лимит глубины вложенности (max_recursion_depth)
 *   3. Размер сериализованного предмета (serializeAsBytes)
 *   4. Длина названия и описания (lore)
 *   5. Количество зачарований
 *   6. Количество PDC-ключей (PersistentDataContainer)
 */
public class CreativeItemValidator implements Listener {

    private static CreativeItemValidator instance;
    private static boolean enabled = true;
    private static int maxItemBytes = 8192;         // 8 KB (снижено — ловим вложенные)
    private static int maxTotalNbtSize = 32768;     // 32 KB total across all containers
    private static int maxRecursionDepth = 8;       // макс глубина shulker-в-shulker
    private static int maxPdcKeys = 30;
    private static int maxLoreLines = 50;
    private static int maxLoreChars = 1000;
    private static int maxNameChars = 200;
    private static int maxEnchantments = 40;
    private static String bypassPermission = "ui.creative.bypass";
    private static String denyMessage = "<red>Этот предмет содержит слишком много данных!</red>";

    private static final long MESSAGE_COOLDOWN_MS = 2000;
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();

    // =========================
    // INIT / RELOAD
    // =========================
    public static void init(Main plugin) {
        instance = new CreativeItemValidator();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        ConsoleLogger.info("[CreativeItem] Initialized. enabled=" + enabled
                + " maxBytes=" + maxItemBytes + " maxTotalNbt=" + maxTotalNbtSize
                + " maxDepth=" + maxRecursionDepth);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.creative_item_validator");
        if (cfg == null) {
            ConsoleLogger.warn("[CreativeItem] No config section 'features.creative_item_validator' found, using defaults");
            return;
        }

        enabled = cfg.getBoolean("enabled", true);
        maxItemBytes = cfg.getInt("max_item_bytes", 8192);
        maxTotalNbtSize = cfg.getInt("max_total_nbt_size", 32768);
        maxRecursionDepth = cfg.getInt("max_recursion_depth", 8);
        maxPdcKeys = cfg.getInt("max_pdc_keys", 30);
        maxLoreLines = cfg.getInt("max_lore_lines", 50);
        maxLoreChars = cfg.getInt("max_lore_chars", 1000);
        maxNameChars = cfg.getInt("max_name_chars", 200);
        maxEnchantments = cfg.getInt("max_enchantments", 40);
        bypassPermission = cfg.getString("bypass_permission", "ui.creative.bypass");
        denyMessage = MessagesManager.getString("features.creativeitem.message",
                "<red>Этот предмет содержит слишком много данных!</red>");

        ConsoleLogger.info("[CreativeItem] Config reloaded: enabled="
                + enabled + " maxBytes=" + maxItemBytes
                + " maxTotalNbt=" + maxTotalNbtSize + " maxDepth=" + maxRecursionDepth);
    }

    // =========================
    // EVENT: проверка размера метадаты
    // =========================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreativeSlot(InventoryCreativeEvent e) {
        if (!enabled) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (player.hasPermission(bypassPermission)) return;

        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.isEmpty() || cursor.getType().isAir()) return;

        // =========================
        // МЕТОД 1: Рекурсивный обход контейнеров
        // Ловит shulker-в-shulker → экспоненциальный рост NBT
        // =========================
        NbtStats stats = countNbtRecursive(cursor, 0);
        if (stats.exceeded) {
            blockItem(e, player, cursor,
                    "recursiveNbt=" + stats.totalBytes + "/" + maxTotalNbtSize
                    + " depth=" + stats.maxDepth + "/" + maxRecursionDepth
                    + " containers=" + stats.containerCount);
            return;
        }

        // =========================
        // МЕТОД 2: Размер сериализованного предмета
        // =========================
        try {
            byte[] raw = cursor.serializeAsBytes();
            if (raw.length > maxItemBytes) {
                blockItem(e, player, cursor,
                        "bytes=" + raw.length + "/" + maxItemBytes);
                return;
            }
        } catch (Exception ex) {
            blockItem(e, player, cursor, "serialization failed: " + ex.getMessage());
            return;
        }

        // =========================
        // МЕТОД 3: Ручные проверки метадаты
        // =========================
        ItemMeta meta = cursor.getItemMeta();
        if (meta == null) return;

        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name.length() > maxNameChars) {
                blockItem(e, player, cursor, "name=" + name.length() + "/" + maxNameChars);
                return;
            }
        }

        if (meta.hasLore()) {
            var lore = meta.getLore();
            if (lore.size() > maxLoreLines) {
                blockItem(e, player, cursor, "loreLines=" + lore.size() + "/" + maxLoreLines);
                return;
            }
            int totalLoreChars = 0;
            for (String line : lore) totalLoreChars += line.length();
            if (totalLoreChars > maxLoreChars) {
                blockItem(e, player, cursor, "loreChars=" + totalLoreChars + "/" + maxLoreChars);
                return;
            }
        }

        if (meta.hasEnchants()) {
            int enchCount = meta.getEnchants().size();
            if (enchCount > maxEnchantments) {
                blockItem(e, player, cursor, "enchants=" + enchCount + "/" + maxEnchantments);
                return;
            }
        }

        int pdcKeys = meta.getPersistentDataContainer().getKeys().size();
        if (pdcKeys > maxPdcKeys) {
            blockItem(e, player, cursor, "pdcKeys=" + pdcKeys + "/" + maxPdcKeys);
            return;
        }
    }

    // =========================
    // RECURSIVE NBT SIZE CHECK
    // =========================
    private static class NbtStats {
        int totalBytes;
        int maxDepth;
        int containerCount;
        boolean exceeded;

        void add(int bytes, int depth) {
            totalBytes += bytes;
            maxDepth = Math.max(maxDepth, depth);
        }
    }

    private NbtStats countNbtRecursive(ItemStack item, int depth) {
        NbtStats stats = new NbtStats();
        if (item == null || item.isEmpty()) return stats;

        // Check depth limit
        if (depth > maxRecursionDepth) {
            stats.exceeded = true;
            return stats;
        }

        // Add this item's serialized size
        try {
            byte[] raw = item.serializeAsBytes();
            stats.add(raw.length, depth);
        } catch (Exception ignore) {
            stats.exceeded = true;
            return stats;
        }

        // Check total size
        if (stats.totalBytes > maxTotalNbtSize) {
            stats.exceeded = true;
            return stats;
        }

        // Recurse into container contents
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return stats;

        // Shulker boxes
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
            stats.containerCount++;
            for (ItemStack content : shulker.getInventory().getContents()) {
                if (content == null || content.isEmpty()) continue;
                NbtStats child = countNbtRecursive(content, depth + 1);
                stats.totalBytes += child.totalBytes;
                stats.maxDepth = Math.max(stats.maxDepth, child.maxDepth);
                stats.containerCount += child.containerCount;
                if (child.exceeded) stats.exceeded = true;
                if (stats.totalBytes > maxTotalNbtSize) stats.exceeded = true;
                if (stats.exceeded) return stats;
            }
        }

        // Bundles (1.21+)
        if (meta instanceof BundleMeta bundle) {
            stats.containerCount++;
            for (ItemStack content : bundle.getItems()) {
                if (content == null || content.isEmpty()) continue;
                NbtStats child = countNbtRecursive(content, depth + 1);
                stats.totalBytes += child.totalBytes;
                stats.maxDepth = Math.max(stats.maxDepth, child.maxDepth);
                stats.containerCount += child.containerCount;
                if (child.exceeded) stats.exceeded = true;
                if (stats.totalBytes > maxTotalNbtSize) stats.exceeded = true;
                if (stats.exceeded) return stats;
            }
        }

        return stats;
    }

    // =========================
    // BLOCK
    // =========================
    private void blockItem(InventoryCreativeEvent e, Player player,
                           ItemStack item, String reason) {
        e.setCancelled(true);

        ConsoleLogger.warn(
                "[CreativeItem] BLOCKED " + item.getType()
                        + " for " + player.getName()
                        + " reason=" + reason);

        long now = System.currentTimeMillis();
        Long lastSent = lastMessageTime.get(player.getUniqueId());
        if (lastSent == null || (now - lastSent) > MESSAGE_COOLDOWN_MS) {
            player.sendMessage(MessageUtil.parse(denyMessage));
            lastMessageTime.put(player.getUniqueId(), now);
        }
    }

    public static CreativeItemValidator getInstance() { return instance; }
}
