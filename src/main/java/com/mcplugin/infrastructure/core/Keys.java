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

    // Scanner items
    public static NamespacedKey HEALTH_METER;
    public static NamespacedKey ORE_FINDER;
    public static NamespacedKey MOB_FINDER;
    public static NamespacedKey RADAR;

    // Materials
    public static NamespacedKey LEAD_INGOT;
    public static NamespacedKey METAL_DETECTOR;
    public static NamespacedKey CONCRETE_BUCKET;

    // Chestplate Flight
    public static NamespacedKey CHESTPLATE_FLIGHT;

    // Netherite Upgrade
    public static NamespacedKey NETHERITE_UPGRADE;

    // Omniscanner
    public static NamespacedKey OMNISCANNER;
    public static NamespacedKey OMNISCANNER_BLOCKS;
    public static NamespacedKey OMNISCANNER_ITEMS;
    public static NamespacedKey OMNISCANNER_ENTITIES;
    public static NamespacedKey OMNISCANNER_RADIUS;

    // GUI Protection
    public static NamespacedKey GUI_PROTECTED;

    // Multimeter
    public static NamespacedKey MULTIMETER;

    // Chunk Loader
    public static NamespacedKey CHUNK_LOADER;

    // Totem Charge
    public static NamespacedKey TOTEM_CHARGE;

    // Integrity System
    public static NamespacedKey INTEGRITY_TAG;
    public static NamespacedKey INTEGRITY_MAX;
    public static NamespacedKey INTEGRITY_CURRENT;
    public static NamespacedKey INTEGRITY_LAST_SEEN;
    public static NamespacedKey INTEGRITY_WARN_FLAGS;
    public static NamespacedKey INTEGRITY_VERSION;
    public static NamespacedKey INTEGRITY_UNBREAKABLE;

    public static void init(Main plugin) {
        PLASMA = new NamespacedKey(plugin, "is_plasma_cannon");
        SHOCKER = new NamespacedKey(plugin, "is_shocker");
        ANTIMATTER = new NamespacedKey(plugin, "isAntimatter");
        LOCATOR = new NamespacedKey(plugin, "isLocator");
        HEALTH_METER = new NamespacedKey(plugin, "isHealthMeter");
        ORE_FINDER = new NamespacedKey(plugin, "isOreFinder");
        MOB_FINDER = new NamespacedKey(plugin, "isMobFinder");
        RADAR = new NamespacedKey(plugin, "isRadar");
        METAL_DETECTOR = new NamespacedKey(plugin, "isMetalDetector");
        LEAD_INGOT = new NamespacedKey(plugin, "isLeadIngot");
        LEAD_SHIELD = new NamespacedKey(plugin, "isLeadShield");
        CONCRETE_BUCKET = new NamespacedKey(plugin, "is_concrete_bucket");
        CHESTPLATE_FLIGHT = new NamespacedKey(plugin, "chestplate_flight");
        NETHERITE_UPGRADE = new NamespacedKey(plugin, "netherite_upgrade");
        AUTH_GUI = new NamespacedKey(plugin, "auth_gui");
        CHGDIM_GUI = new NamespacedKey(plugin, "chgdim_gui");

        OMNISCANNER = new NamespacedKey(plugin, "omniscanner");
        OMNISCANNER_BLOCKS = new NamespacedKey(plugin, "omniscanner_blocks");
        OMNISCANNER_ITEMS = new NamespacedKey(plugin, "omniscanner_items");
        OMNISCANNER_ENTITIES = new NamespacedKey(plugin, "omniscanner_entities");
        OMNISCANNER_RADIUS = new NamespacedKey(plugin, "omniscanner_radius");

        GUI_PROTECTED = new NamespacedKey(plugin, "gui_protected");

        MULTIMETER = new NamespacedKey(plugin, "is_multimeter");
        CHUNK_LOADER = new NamespacedKey(plugin, "is_chunk_loader");

        TOTEM_CHARGE = new NamespacedKey(plugin, "totem_charge");

        INTEGRITY_TAG = new NamespacedKey(plugin, "integrity_tag");
        INTEGRITY_MAX = new NamespacedKey(plugin, "integrity_max");
        INTEGRITY_CURRENT = new NamespacedKey(plugin, "integrity_current");
        INTEGRITY_LAST_SEEN = new NamespacedKey(plugin, "integrity_last_seen");
        INTEGRITY_WARN_FLAGS = new NamespacedKey(plugin, "integrity_warn_flags");
        INTEGRITY_VERSION = new NamespacedKey(plugin, "integrity_version");
        INTEGRITY_UNBREAKABLE = new NamespacedKey(plugin, "integrity_unbreakable");
    }
}