package com.ultimateimprovements.listener;

import com.ultimateimprovements.core.Main;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Предотвращает наложение эффекта левитации от пуль шалкера на игроков.
 * Шалкеры всё ещё наносят урон, но эффект левитации сразу снимается.
 * Включается/выключается через config.yml → features.shulker_protection.enabled
 */
public class ShulkerBulletListener implements Listener {

    @EventHandler
    public void onShulkerDamage(EntityDamageByEntityEvent e) {
        // Проверяем: включена ли защита в конфиге
        if (!Main.getInstance().getConfig()
                .getBoolean("features.shulker_protection.enabled", true)) return;

        // Проверяем: пуля шалкера попадает в игрока
        if (!(e.getDamager() instanceof ShulkerBullet)) return;
        if (!(e.getEntity() instanceof Player player)) return;

        // Снимаем левитацию на следующем тике (эффект накладывается после события урона)
        Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () -> {
            if (player.isValid() && !player.isDead()) {
                player.removePotionEffect(PotionEffectType.LEVITATION);
            }
        });
    }
}
