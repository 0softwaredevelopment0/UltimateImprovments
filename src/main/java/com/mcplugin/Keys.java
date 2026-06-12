package com.mcplugin;

import org.bukkit.NamespacedKey;

public class Keys {

    public static NamespacedKey PLASMA;
    public static NamespacedKey SHOCKER;

    // Feature keys (из датапака)
    public static NamespacedKey ANTIMATTER;
    public static NamespacedKey LOCATOR;
    public static NamespacedKey HP_METER;
    public static NamespacedKey LEAD_SHIELD;
    public static NamespacedKey DOSIMETER;

    public static void init(Main plugin) {
        PLASMA = new NamespacedKey(plugin, "is_plasma_cannon");
        SHOCKER = new NamespacedKey(plugin, "is_shocker");
        ANTIMATTER = new NamespacedKey(plugin, "isAntimatter");
        LOCATOR = new NamespacedKey(plugin, "isLocator");
        HP_METER = new NamespacedKey(plugin, "isHpMeter");
        LEAD_SHIELD = new NamespacedKey(plugin, "isLeadShield");
        DOSIMETER = new NamespacedKey(plugin, "isDosimeter");
    }
}