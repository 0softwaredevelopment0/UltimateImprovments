package com.ultimateimprovments.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AddonCatalog — static routing table of the UltimateImprovments config system.
 * <p>
 * Maps every root config key to the addon that owns it (and therefore to the
 * {@code configs/UI-<Addon>.toml} file it is stored in), and lists every addon in
 * load order. Shared/global keys stay with {@link #CORE}.
 */
public final class AddonCatalog {

    /** The core plugin name — also the owner of all shared/global keys. */
    public static final String CORE = "UI-Core";

    private static final Map<String, String> ROOT_TO_ADDON = new LinkedHashMap<>();

    private static void route(String addon, String... roots) {
        for (String r : roots) ROOT_TO_ADDON.put(r, addon);
    }

    static {
        // ── UI-MBS ──
        route("UI-MBS", "reactor");
        // ── UI-Energy ──
        route("UI-Energy", "energy", "energy_crafting");
        // ── UI-Chat ──
        route("UI-Chat", "chat", "chat_ping", "chat_filter", "ojm", "auto_broadcast");
        // ── UI-Clans ──
        route("UI-Clans", "clan");
        // ── UI-Combat ──
        route("UI-Combat", "turret");
        // ── UI-Punish ──
        route("UI-Punish", "access_control");
        // ── UI-Essentials ──
        route("UI-Essentials", "home", "spawn", "heal_feed", "report", "rtp", "near",
                "endersee", "troll");
        // ── UI-Anticheat ──
        route("UI-Anticheat", "anticheat");
        // ── UI-Datapack ──
        route("UI-Datapack", "datapack");
        // ── UI-Shared ──
        route("UI-Shared", "space", "radiation", "hazmat");
        // ── UI-Other (features umbrella + mechanics) ──
        route("UI-Other",
                "features", "brand_spoof", "vanish", "sunburn", "netherite_upgrade",
                "auth", "void_protection", "death_logger", "power", "suicide",
                "packet_guard", "proxy_server", "redstone_guard", "emergency_entity_kill",
                "server_overload_warning", "bot_protection", "changedimmension", "motd",
                "tab", "scoreboard", "bossbar", "wireless_redstone",
                "structure_integrity", "economy", "enchant", "meteor",
                "particle_accelerator", "block_friction", "protection", "codepanel");
    }

    // Message groups (first segment under messages/messages_en) routed to addons.
    private static final Map<String, String> MSG_GROUP_TO_ADDON = new LinkedHashMap<>();

    private static void routeMsg(String addon, String... groups) {
        for (String g : groups) MSG_GROUP_TO_ADDON.put(g, addon);
    }

    static {
        routeMsg("UI-Chat", "chat", "chat_ping", "chat_filter", "ojm", "broadcast");
        routeMsg("UI-Clans", "clan");
        routeMsg("UI-Combat", "turret", "combat");
        routeMsg("UI-Essentials", "home", "report", "spawn", "rtp", "near", "endersee",
                "troll", "misc", "invsee");
        routeMsg("UI-Anticheat", "anticheat", "ac");
        routeMsg("UI-Punish", "punish", "maintenance", "blacklist", "opwhitelist", "access_control");
        routeMsg("UI-Other", "auth", "check", "packet_guard", "bot_protection", "sudo",
                "codepanel", "protection", "changedimmension", "motd", "tab", "scoreboard",
                "bossbar", "vanish", "suicide", "economy", "notes", "power", "death_logger",
                "structures", "meteor", "space", "enchant", "wireless_redstone",
                "structure_integrity");
    }

    private AddonCatalog() {}

    /** @return the addon owning the given root key (never null — defaults to CORE). */
    public static String addonOfRootKey(String rootKey) {
        return ROOT_TO_ADDON.getOrDefault(rootKey, CORE);
    }

    /**
     * @return the addon owning the given message group (first segment under
     *         {@code messages.<group>} / {@code messages_en.<group>});
     *         global groups (general, reputation, help, ...) stay with CORE.
     */
    public static String addonOfMsgGroup(String group) {
        return MSG_GROUP_TO_ADDON.getOrDefault(group, CORE);
    }

    /** @return true when the root key is a shared/global core key. */
    public static boolean isCoreRoot(String rootKey) {
        return addonOfRootKey(rootKey).equals(CORE);
    }

    /** All addons with per-addon config files, in family load order (CORE excluded). */
    public static List<String> catalog() {
        return List.of(
                CORE,
                "UI-Shared", "UI-MBS", "UI-Datapack", "UI-Chat", "UI-Clans",
                "UI-Combat", "UI-Punish", "UI-Essentials", "UI-Energy",
                "UI-Anticheat", "UI-Other");
    }
}
