package com.ultimateimprovements.mechanics.features.integrity;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 🛡 Integrity Listener — перехватывает ванильный урон предметам
 * и перенаправляет его в систему целостности.
 * <p>
 * При каждой попытке нанести урон предмету (ломка блоков, атака,
 * получение урона в броне и т.д.) событие {@link PlayerItemDamageEvent}
 * отменяется, а вместо него уменьшается кастомная целостность предмета.
 */
public class IntegrityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (event.isCancelled()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Unbreakable предметы не получают урон вообще
        if (IntegrityManager.isUnbreakable(item)) {
            event.setCancelled(true);
            return;
        }

        // Проверяем, есть ли у предмета прочность
        if (IntegrityManager.getMaxDurability(item) <= 0) return;

        // Отменяем ванильный урон
        event.setCancelled(true);

        // Применяем урон через систему целостности
        Player player = event.getPlayer();
        int vanillaDamage = event.getDamage();
        IntegrityManager.decreaseIntegrity(item, vanillaDamage, player);
    }
}
