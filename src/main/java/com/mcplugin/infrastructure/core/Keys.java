package com.mcplugin.infrastructure.core;

import org.bukkit.NamespacedKey;

public class Keys {

    public static NamespacedKey PLASMA;
    public static NamespacedKey SHOCKER;

    // Feature keys (из датапака)
    public static NamespacedKey ANTIMATTER;
    public static NamespacedKey LOCATOR;

    // Auth GUI items
    public static NamespacedKey AUTH_GUI;
    public static NamespacedKey CHGDIM_GUI;
    public static NamespacedKey LEAD_SHIELD;

    // Integrity System
    public static NamespacedKey INTEGRITY_TAG;
    public static NamespacedKey INTEGRITY_MAX;
    public static NamespacedKey INTEGRITY_CURRENT;
    public static NamespacedKey INTEGRITY_LAST_SEEN;
    public static NamespacedKey INTEGRITY_WARN_FLAGS;
    public static NamespacedKey INTEGRITY_VERSION;

    public static void init(Main plugin) {
        PLASMA = new NamespacedKey(plugin, "is_plasma_cannon");
        SHOCKER = new NamespacedKey(plugin, "is_shocker");
        ANTIMATTER = new NamespacedKey(plugin, "isAntimatter");
        LOCATOR = new NamespacedKey(plugin, "isLocator");
        LEAD_SHIELD = new NamespacedKey(plugin, "isLeadShield");
        AUTH_GUI = new NamespacedKey(plugin, "auth_gui");
        CHGDIM_GUI = new NamespacedKey(plugin, "chgdim_gui");

        INTEGRITY_TAG = new NamespacedKey(plugin, "integrity_tag");
        INTEGRITY_MAX = new NamespacedKey(plugin, "integrity_max");
        INTEGRITY_CURRENT = new NamespacedKey(plugin, "integrity_current");
        INTEGRITY_LAST_SEEN = new NamespacedKey(plugin, "integrity_last_seen");
        INTEGRITY_WARN_FLAGS = new NamespacedKey(plugin, "integrity_warn_flags");
        INTEGRITY_VERSION = new NamespacedKey(plugin, "integrity_version");
    }
}