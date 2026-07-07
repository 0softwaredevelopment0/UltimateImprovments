package com.mcplugin.mechanics.features.integrity;

import com.mcplugin.core.Main;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 🎯 PiercingListener — обработчик зачарования PIERCING (Пробитие).
 * <p>
 * В ванилле PIERCING на арбалетах пробивает сущностей насквозь,
 * но НЕ игнорирует броню. Данный слушатель:
 * <ul>
 *   <li>НЕ даёт PIERCING игнорировать броню (защита работает как обычно)</li>
 *   <li>Добавляет +extraCost% к трате целостности брони цели при ударе</li>
 *   <li>Unbreaking проверяется на итоговую стоимость (не игнорируется)</li>
 * </ul>
 */
public class PiercingListener implements Listener {

    private static boolean enabled = true;

    public static void init(Main plugin) {
        var listener = new PiercingListener();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public static void reloadConfig() {
        enabled = IntegrityManager.isPiercingEnabled();
    }

    /**
     * При ударе игрока оружием с зачарованием PIERCING:
     * - Броня НЕ игнорируется (защита работает как в ванилле)
     * - Устанавливается флаг, что следующий урон броне должен получить +extraCost%
     * - В decreaseIntegrity() проверяется флаг и добавляется extraCost ДО Unbreaking
     * <p>
     * Если удар БЕЗ PIERCING — флаг сбрасывается, чтобы обычные удары
     * не получали бонус от предыдущего PIERCING-удара в этом же тике.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (!enabled) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        // Проверяем, есть ли у атакующего PIERCING на оружии
        ItemStack weapon = getWeapon(e.getDamager());
        if (weapon == null || weapon.getType() == Material.AIR) {
            // Без оружия — не PIERCING, сбрасываем флаг
            IntegrityManager.setPiercingActive(false);
            return;
        }

        if (weapon.containsEnchantment(Enchantment.PIERCING)) {
            // Устанавливаем флаг — следующий урон броне получит +extraCost%
            IntegrityManager.setPiercingActive(true);
        } else {
            // Оружие без PIERCING — сбрасываем флаг
            IntegrityManager.setPiercingActive(false);
        }
    }

    /**
     * Получает оружие атакующего (если атакующий — игрок).
     */
    private ItemStack getWeapon(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player attacker) {
            return attacker.getInventory().getItemInMainHand();
        }
        // Для мобов/projectiles не проверяем PIERCING (ванилла сама обрабатывает)
        return null;
    }
}
