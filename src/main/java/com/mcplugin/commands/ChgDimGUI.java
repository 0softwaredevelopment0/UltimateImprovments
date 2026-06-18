package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Anvil GUI для выбора мира при /mp chgdim.
 * <p>
 * Левый слот (0) — информационный предмет со списком миров.
 * Поле переименования — ввод названия мира.
 * Результат (слот 2) — подтверждение телепортации.
 * Центр (слот 1) — пустой.
 * Escape — закрывает GUI без последствий.
 */
public class ChgDimGUI implements Listener {

    private static final Map<UUID, String> openMenus = new HashMap<>();
    private static boolean registered = false;

    /**
     * Открывает Anvil GUI для выбора мира телепортации.
     */
    public static void open(Player player) {
        register();

        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null || worldsSection.getKeys(false).isEmpty()) {
            player.sendMessage("§4❌ §cМиры не настроены в конфиге!");
            return;
        }

        AnvilInventory inv = (AnvilInventory) Bukkit.createInventory(null, InventoryType.ANVIL, "§8✦ Смена измерения");

        // ===== SLOT 0: информационный предмет со списком миров =====
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta meta = infoItem.getItemMeta();
        meta.setDisplayName("§f⬇ Введите название мира");

        List<String> lore = new ArrayList<>();
        lore.add("§8┌────────────────────────────┐");

        Set<String> worldNames = worldsSection.getKeys(false);
        int count = 0;

        for (String worldName : worldNames) {
            count++;
            ConfigurationSection wc = worldsSection.getConfigurationSection(worldName);
            double x = wc != null ? wc.getDouble("x", 0) : 0;
            double y = wc != null ? wc.getDouble("y", 64) : 64;
            double z = wc != null ? wc.getDouble("z", 0) : 0;
            lore.add("§8│ §e" + worldName);
            lore.add("§8│ §8» §7" + Math.round(x) + " " + Math.round(y) + " " + Math.round(z));
        }

        lore.add("§8│");
        lore.add("§8│ §7Всего миров: §f" + count);
        lore.add("§8└────────────────────────────┘");
        lore.add("");
        lore.add("§e💡 Введите название мира в поле");
        lore.add("§eи нажмите на появившийся предмет");

        meta.setLore(lore);
        infoItem.setItemMeta(meta);

        inv.setItem(0, infoItem);
        // Slot 1 — пустой (центр)
        // Slot 2 — автоматом появляется после ввода текста (переименованный PAPER)

        openMenus.put(player.getUniqueId(), "chgdim");
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getInventory().getType() != InventoryType.ANVIL) return;
        if (!openMenus.containsKey(player.getUniqueId())) return;

        // Блокируем клики по любым слотам, кроме результата (слот 2)
        if (e.getSlot() != 2) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        AnvilInventory anvil = (AnvilInventory) e.getInventory();
        String worldName = anvil.getRenameText();

        if (worldName == null || worldName.trim().isEmpty()) {
            player.sendMessage("§4❌ §cВведите название мира в поле переименования!");
            player.closeInventory();
            return;
        }

        worldName = worldName.trim();

        // ===== CHECK PER-WORLD PERMISSION =====
        if (!player.hasPermission("mcplugin.command.chgdim." + worldName)) {
            player.sendMessage(MessageUtil.parse(Main.getInstance().getConfig()
                    .getString("changedimmension.messages.no_permission",
                            "<dark_red>❌</dark_red> <red>У вас нет прав на эту команду!</red>")));
            player.closeInventory();
            return;
        }

        // ===== TELEPORT (обрабатывает cooldown, world not found, success) =====
        ChgDimCommand.teleport(player, worldName);
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        openMenus.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Регистрирует слушатель один раз при первом вызове open().
     */
    private static void register() {
        if (registered) return;
        registered = true;
        Bukkit.getPluginManager().registerEvents(new ChgDimGUI(), Main.getInstance());
    }
}
