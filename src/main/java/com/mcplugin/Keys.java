package com.mcplugin;

import org.bukkit.NamespacedKey;

public class Keys {

    public static NamespacedKey PLASMA;
    public static NamespacedKey SHOCKER;

    // Feature keys (из датапака)
    public static NamespacedKey ANTIMATTER;
    public static NamespacedKey LOCATOR;

    // Auth GUI items
    public static NamespacedKey AUTH_GUI;
    public static NamespacedKey HP_METER;
    public static NamespacedKey LEAD_SHIELD;
    public static NamespacedKey DOSIMETER;

    // Integrity System
    public static NamespacedKey INTEGRITY_TAG;
    public static NamespacedKey INTEGRITY_MAX;
    public static NamespacedKey INTEGRITY_CURRENT;
    public static NamespacedKey INTEGRITY_LAST_SEEN;

    public static void init(Main plugin) {
        PLASMA = new NamespacedKey(plugin, "is_plasma_cannon");
        SHOCKER = new NamespacedKey(plugin, "is_shocker");
        ANTIMATTER = new NamespacedKey(plugin, "isAntimatter");
        LOCATOR = new NamespacedKey(plugin, "isLocator");
        HP_METER = new NamespacedKey(plugin, "isHpMeter");
        LEAD_SHIELD = new NamespacedKey(plugin, "isLeadShield");
        DOSIMETER = new NamespacedKey(plugin, "isDosimeter");
        AUTH_GUI = new NamespacedKey(plugin, "auth_gui");

        INTEGRITY_TAG = new NamespacedKey(plugin, "integrity_tag");
        INTEGRITY_MAX = new NamespacedKey(plugin, "integrity_max");
        INTEGRITY_CURRENT = new NamespacedKey(plugin, "integrity_current");
        INTEGRITY_LAST_SEEN = new NamespacedKey(plugin, "integrity_last_seen");
    }
}