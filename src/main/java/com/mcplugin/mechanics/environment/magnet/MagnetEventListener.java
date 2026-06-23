package com.mcplugin.mechanics.environment.magnet;

import com.mcplugin.infrastructure.core.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Обрабатывает событие выбрасывания предмета игроком.
 * Если выброшен металлический предмет — помечает игрока как "dirty"
 * в MagnetManager, чтобы в следующем тике магнита его инвентарь
 * был перепроверен, а скорость сброшена, если металла больше нет.
 */
public class MagnetEventListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();

        // Если выброшенный предмет не металлический — ничего не делаем
        if (!isMetallic(dropped)) return;

        // Отмечаем, что игрок выбросил металлический предмет,
        // чтобы MagnetManager перепроверил его инвентарь в следующем тике
        MagnetManager.markPlayerDirty(player.getUniqueId());
    }

    /**
     * Проверяет, является ли предмет металлическим (магнитится).
     * Дублирует логику MagnetManager.isMetallic(), но вызывается
     * из event listener, где статический доступ удобнее.
     */
    public static boolean isMetallic(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material mat = item.getType();
        String name = mat.name();

        if (name.contains("IRON")) return true;
        if (name.startsWith("GOLD_") || name.equals("GOLDEN_SWORD") || name.equals("GOLDEN_SHOVEL")
                || name.equals("GOLDEN_PICKAXE") || name.equals("GOLDEN_AXE") || name.equals("GOLDEN_HOE")
                || name.equals("GOLDEN_HELMET") || name.equals("GOLDEN_CHESTPLATE")
                || name.equals("GOLDEN_LEGGINGS") || name.equals("GOLDEN_BOOTS")
                || name.equals("GOLDEN_HORSE_ARMOR") || name.equals("GOLD_BLOCK")
                || name.equals("GOLD_INGOT") || name.equals("GOLD_NUGGET")
                || name.equals("RAW_GOLD") || name.equals("RAW_GOLD_BLOCK")) return true;
        if (name.contains("NETHERITE")) return true;
        if (name.contains("COPPER")) return true;
        if (name.contains("CHAINMAIL")) return true;
        if (mat == Material.BUCKET || mat == Material.WATER_BUCKET || mat == Material.LAVA_BUCKET
                || mat == Material.MILK_BUCKET || mat == Material.COD_BUCKET
                || mat == Material.SALMON_BUCKET || mat == Material.PUFFERFISH_BUCKET
                || mat == Material.TROPICAL_FISH_BUCKET || mat == Material.AXOLOTL_BUCKET
                || mat == Material.TADPOLE_BUCKET) return true;
        if (mat == Material.SHEARS) return true;
        if (mat == Material.COMPASS) return true;
        if (mat == Material.RECOVERY_COMPASS) return true;
        if (name.contains("MINECART")) return true;
        if (name.contains("ANVIL")) return true;
        if (mat == Material.CAULDRON) return true;
        if (mat == Material.HOPPER) return true;
        if (mat == Material.RAIL || mat == Material.POWERED_RAIL || mat == Material.DETECTOR_RAIL
                || mat == Material.ACTIVATOR_RAIL) return true;
        if (mat == Material.PISTON || mat == Material.STICKY_PISTON) return true;
        if (mat == Material.STONECUTTER) return true;
        if (mat == Material.GRINDSTONE) return true;
        if (mat == Material.LANTERN || mat == Material.SOUL_LANTERN) return true;
        if (mat == Material.NAUTILUS_SHELL) return true;
        if (mat == Material.HEAVY_CORE) return true;
        return false;
    }
}
