package com.mcplugin.features.savedhotbar;

import com.mcplugin.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Фича: блокировка предметов с чрезмерной метадатой (NBT/компоненты)
 * при попытке взять их в креативном режиме.
 *
 * Некоторые игроки создают "краш-сундуки" — предметы с гигантским NBT,
 * при загрузке которых сервер зависает или крашится.
 *
 * Мы проверяем:
 *   1. Размер сериализованного предмета (serializeAsBytes)
 *   2. Длину названия и описания (lore)
 *   3. Количество зачарований
 *   4. Количество PDC-ключей (PersistentDataContainer)
 *
 * Если превышен лимит — пакет отменяется.
 */
public class CreativeItemValidator implements Listener {

    private static CreativeItemValidator instance;
    private static boolean enabled = true;
    private static int maxItemBytes = 10240;        // 10 KB
    private static int maxPdcKeys = 30;             // макс PDC ключей
    private static int maxLoreLines = 50;           // макс строк lore
    private static int maxLoreChars = 1000;          // макс символов в lore
    private static int maxNameChars = 200;           // макс символов в названии
    private static int maxEnchantments = 40;         // макс зачарований
    private static String bypassPermission = "mcplugin.creative.bypass";
    private static String denyMessage = "§cЭтот предмет содержит слишком много данных!";

    // Cooldown сообщения
    private static final long MESSAGE_COOLDOWN_MS = 2000;
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();

    // =========================
    // INIT / RELOAD
    // =========================
    public static void init(Main plugin) {
        instance = new CreativeItemValidator();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        plugin.getLogger().info("[CreativeValidator] Initialized. enabled=" + enabled
                + " maxBytes=" + maxItemBytes);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.creative_item_validator");
        if (cfg == null) return;

        enabled = cfg.getBoolean("enabled", true);
        maxItemBytes = cfg.getInt("max_item_bytes", 10240);
        maxPdcKeys = cfg.getInt("max_pdc_keys", 30);
        maxLoreLines = cfg.getInt("max_lore_lines", 50);
        maxLoreChars = cfg.getInt("max_lore_chars", 1000);
        maxNameChars = cfg.getInt("max_name_chars", 200);
        maxEnchantments = cfg.getInt("max_enchantments", 40);
        bypassPermission = cfg.getString("bypass_permission", "mcplugin.creative.bypass");
        denyMessage = cfg.getString("message",
                "§cЭтот предмет содержит слишком много данных!");

        Main.getInstance().getLogger().info("[CreativeValidator] Config reloaded: enabled="
                + enabled + " maxBytes=" + maxItemBytes);
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
        if (cursor == null || cursor.isEmpty()) return;
        if (cursor.getType().isAir()) return;

        // =========================
        // МЕТОД 1: Размер сериализованного предмета
        // Самая надёжная проверка — ловит ЛЮБЫЕ предметы
        // с аномально большим объёмом данных.
        // =========================
        try {
            byte[] raw = cursor.serializeAsBytes();
            if (raw.length > maxItemBytes) {
                blockItem(e, player, cursor,
                        "bytes=" + raw.length + "/" + maxItemBytes);
                return;
            }
        } catch (Exception ex) {
            // Если сериализация упала — предмет битый, блокируем
            blockItem(e, player, cursor, "serialization failed: " + ex.getMessage());
            return;
        }

        // =========================
        // МЕТОД 2: Ручные проверки
        // Дополнительные проверки на случай, если
        // serializeAsBytes() пропустит что-то аномальное.
        // =========================
        ItemMeta meta = cursor.getItemMeta();
        if (meta == null) return;

        // Имя предмета
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name.length() > maxNameChars) {
                blockItem(e, player, cursor,
                        "name=" + name.length() + "/" + maxNameChars);
                return;
            }
        }

        // Описание
        if (meta.hasLore()) {
            var lore = meta.getLore();
            if (lore.size() > maxLoreLines) {
                blockItem(e, player, cursor,
                        "loreLines=" + lore.size() + "/" + maxLoreLines);
                return;
            }
            int totalLoreChars = 0;
            for (String line : lore) totalLoreChars += line.length();
            if (totalLoreChars > maxLoreChars) {
                blockItem(e, player, cursor,
                        "loreChars=" + totalLoreChars + "/" + maxLoreChars);
                return;
            }
        }

        // Зачарования
        if (meta.hasEnchants()) {
            int enchCount = meta.getEnchants().size();
            if (enchCount > maxEnchantments) {
                blockItem(e, player, cursor,
                        "enchants=" + enchCount + "/" + maxEnchantments);
                return;
            }
        }

        // PDC ключи
        int pdcKeys = meta.getPersistentDataContainer().getKeys().size();
        if (pdcKeys > maxPdcKeys) {
            blockItem(e, player, cursor,
                    "pdcKeys=" + pdcKeys + "/" + maxPdcKeys);
            return;
        }
    }

    // =========================
    // BLOCK
    // =========================
    private void blockItem(InventoryCreativeEvent e, Player player,
                           ItemStack item, String reason) {
        e.setCancelled(true);

        Main.getInstance().getLogger().warning(
                "[CreativeValidator] §§§ BLOCKED " + item.getType()
                        + " for " + player.getName()
                        + " reason=" + reason);

        long now = System.currentTimeMillis();
        Long lastSent = lastMessageTime.get(player.getUniqueId());
        if (lastSent == null || (now - lastSent) > MESSAGE_COOLDOWN_MS) {
            player.sendMessage(denyMessage);
            lastMessageTime.put(player.getUniqueId(), now);
        }
    }

    public static CreativeItemValidator getInstance() {
        return instance;
    }
}
