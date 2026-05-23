package com.mcplugin.guns;

import com.mcplugin.Keys;
import com.mcplugin.guns.projectile.ProjectileType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class WeaponResolver {

    public static ProjectileType resolve(ItemStack item) {

        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        var pdc = meta.getPersistentDataContainer();

        // =========================
        // PLASMA
        // =========================
        if (pdc.has(Keys.PLASMA, PersistentDataType.BYTE)) {
            return ProjectileType.PLASMA;
        }

        // =========================
        // SHOCKER
        // =========================
        if (pdc.has(Keys.SHOCKER, PersistentDataType.BYTE)) {
            return ProjectileType.SHOCKER;
        }

        return null;
    }
}