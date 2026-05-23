package com.mcplugin;

import org.bukkit.NamespacedKey;

public class Keys {

    public static NamespacedKey PLASMA;
    public static NamespacedKey SHOCKER;

    public static void init(Main plugin) {
        PLASMA = new NamespacedKey(plugin, "is_plasma_cannon");
        SHOCKER = new NamespacedKey(plugin, "is_shocker");
    }
}