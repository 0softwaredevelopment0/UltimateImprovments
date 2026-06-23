package com.mcplugin.combat.weapons.core;

import com.mcplugin.infrastructure.core.Keys;
import com.mcplugin.combat.weapons.core.ProjectileType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class WeaponResolver {

    public static ProjectileType resolve(ItemStack item) {

        if (item == null || item.getType() == Material.AIR) return null;

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