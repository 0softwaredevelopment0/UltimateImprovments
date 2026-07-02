package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.config.MessagesManager;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class ExpSplitSubcommand {

    private static final NamespacedKey EXP_SPLIT_KEY = new NamespacedKey("mcplugin", "exp_split_amount");

    private ExpSplitSubcommand() {}

    /**
     * /mp expsplit — забирает весь опыт из уровней, отдаёт бутылку с опытом.
     */
    public static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("general.player_only",
                    "<red>❌ Only players can use this command!</red>")));
            return true;
        }

        int level = player.getLevel();
        float progress = player.getExp();

        if (level == 0 && progress == 0) {
            player.sendMessage(MessageUtil.parse("<red>❌ У вас нет опыта для разделения!</red>"));
            return true;
        }

        // Calculate total XP from levels
        int totalXp = getXpFromLevels(level);
        // Add partial level progress
        totalXp += (int) (getXpForLevel(level) * progress);

        // Check inventory space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(MessageUtil.parse("<red>❌ Инвентарь полон! Освободите место.</red>"));
            return true;
        }

        // Reset player XP
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);

        // Create XP bottle
        ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
        ItemMeta meta = bottle.getItemMeta();
        if (meta == null) return true;

        meta.displayName(MessageUtil.parse("<i:false><gold>✦ Колба опыта ✦</gold>"));
        meta.lore(List.of(
                MessageUtil.parse("<i:false><gray>Содержит: <yellow>" + totalXp + "</yellow> XP</gray>"),
                MessageUtil.parse("<i:false><gray>ПКМ — забрать опыт</gray>")
        ));
        meta.getPersistentDataContainer().set(EXP_SPLIT_KEY, PersistentDataType.INTEGER, totalXp);
        bottle.setItemMeta(meta);

        player.getInventory().addItem(bottle);

        player.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Весь опыт (<yellow>" + totalXp + "</yellow> XP) помещён в колбу!</white>"));
        return true;
    }

    /**
     * Обрабатывает использование XP-бутылки — забирает опыт.
     */
    public static boolean useBottle(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.GLASS_BOTTLE) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        if (!meta.getPersistentDataContainer().has(EXP_SPLIT_KEY, PersistentDataType.INTEGER)) return false;

        Integer amountBoxed = meta.getPersistentDataContainer().get(EXP_SPLIT_KEY, PersistentDataType.INTEGER);
        if (amountBoxed == null || amountBoxed <= 0) return false;
        int amount = amountBoxed;

        // Give XP
        player.giveExp(amount);

        // Consume one bottle
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage(MessageUtil.parse(
                "<green>✔</green> <white>Получено <yellow>" + amount + "</yellow> XP из колбы!</white>"));
        return true;
    }

    // =========================
    // XP CALCULATION (Minecraft formula)
    // =========================

    public static int getXpForLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    public static int getXpFromLevels(int level) {
        int total = 0;
        for (int i = 0; i < level; i++) {
            total += getXpForLevel(i);
        }
        return total;
    }

    public static NamespacedKey getExpSplitKey() {
        return EXP_SPLIT_KEY;
    }
}
