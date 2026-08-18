package com.ultimateimprovments.combat.turret;

import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * End crystal turrets.
 * <p>
 * Any end crystal can be turned into a turret: shift + right-click opens a chat
 * GUI where the owner can enable/disable it (off by default) and switch between
 * a whitelist / blacklist of player nicknames and entity types.
 * <p>
 * While enabled, the crystal picks the nearest living entity in a 16x16x16 cube
 * (16 blocks in each axis), requires a clear line of sight (no solid blocks in
 * between), draws its vanilla end-crystal beam at the victim and deals normal
 * (armor-reducible) damage at up to 2 damage per second (vanilla i-frames cap
 * the rate — no immunity bypass).
 */
public final class TurretManager {

    public enum Mode {
        WHITELIST,
        BLACKLIST;

        static Mode fromDb(String value) {
            return "whitelist".equalsIgnoreCase(value) ? WHITELIST : BLACKLIST;
        }
    }

    /** Per-crystal turret settings. */
    public static final class TurretConfig {
        public String world;
        public int x;
        public int y;
        public int z;
        public volatile boolean enabled;
        public volatile Mode mode = Mode.BLACKLIST;
        public final Set<String> entries = ConcurrentHashMap.newKeySet();
        /** Owner UUID; {@code null} = unowned (legacy turret). */
        public volatile UUID owner;
    }

    /** Range in blocks along each axis (16x16x16 cube = +-16 from the crystal). */
    private static final int RANGE = 16;

    /** Damage applied every tick while the beam is on the target (i-frames cap it at 2 dmg/s). */
    private static final double DAMAGE_PER_TICK = 1.0;

    private static TurretManager instance;

    /** Crystal location key (world:x:y:z) -> settings. */
    private final Map<String, TurretConfig> configs = new ConcurrentHashMap<>();

    /** Player -> currently selected crystal (set when the GUI is opened). */
    private final Map<UUID, UUID> selected = new ConcurrentHashMap<>();

    private TurretManager() {}

    public static TurretManager getInstance() {
        if (instance == null) {
            instance = new TurretManager();
        }
        return instance;
    }

    /** Loads persisted turrets. Called once from the module init. */
    public static void init() {
        getInstance().loadAll();
    }

    /** Clears beams and resets state. Called from the module shutdown. */
    public static void shutdown() {
        TurretManager manager = instance;
        if (manager != null) {
            manager.clearAllBeams();
            manager.selected.clear();
            manager.configs.clear();
        }
        instance = null;
    }

    // =====================================================================
    // SELECTION + CHAT GUI
    // =====================================================================

    public void select(Player player, EnderCrystal crystal) {
        TurretConfig cfg = configFor(crystal);
        // First person to manage a crystal claims it as the owner.
        if (cfg.owner == null) {
            cfg.owner = player.getUniqueId();
            save(cfg);
        }
        selected.put(player.getUniqueId(), crystal.getUniqueId());
        openGui(player, crystal);
    }

    public EnderCrystal getSelected(Player player) {
        UUID id = selected.get(player.getUniqueId());
        if (id == null) return null;
        Entity entity = Bukkit.getEntity(id);
        if (entity instanceof EnderCrystal crystal && crystal.isValid()) {
            return crystal;
        }
        selected.remove(player.getUniqueId());
        return null;
    }

    public void openGui(Player player, EnderCrystal crystal) {
        TurretConfig cfg = configs.get(keyFor(crystal));
        boolean enabled = cfg != null && cfg.enabled;
        Mode mode = cfg != null ? cfg.mode : Mode.BLACKLIST;
        Set<String> entries = cfg != null ? cfg.entries : Set.of();

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━ Turret ━━━━━━━", NamedTextColor.GOLD));

        player.sendMessage(Component.text()
                .append(Component.text("Status: ", NamedTextColor.GRAY))
                .append(Component.text(enabled ? "ENABLED" : "DISABLED",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .build());

        player.sendMessage(Component.text()
                .append(Component.text("Mode: ", NamedTextColor.GRAY))
                .append(Component.text(mode.name(), NamedTextColor.AQUA))
                .build());

        player.sendMessage(Component.text()
                .append(Component.text("Targets: ", NamedTextColor.GRAY))
                .append(Component.text(entries.isEmpty() ? "(empty)" : String.join(", ", entries),
                        NamedTextColor.WHITE))
                .build());

        player.sendMessage(Component.empty());

        player.sendMessage(button(
                enabled ? "✖ Disable" : "✔ Enable",
                enabled ? NamedTextColor.RED : NamedTextColor.GREEN,
                "/ui turret toggle",
                "Click to " + (enabled ? "disable" : "enable") + " this turret"));

        player.sendMessage(button(
                "⇄ Switch mode: " + (mode == Mode.WHITELIST ? "WHITELIST" : "BLACKLIST"),
                NamedTextColor.AQUA,
                "/ui turret mode",
                "Whitelist = only listed targets. Blacklist = everything except listed."));

        player.sendMessage(suggestButton(
                "+ Add target (nick or mob type)",
                NamedTextColor.YELLOW,
                "/ui turret add ",
                "Click, then type a nickname or entity type (e.g. zombie, player)"));

        player.sendMessage(suggestButton(
                "- Remove target (nick or mob type)",
                NamedTextColor.YELLOW,
                "/ui turret remove ",
                "Click, then type a nickname or entity type to remove"));

        player.sendMessage(button(
                "☰ View target list",
                NamedTextColor.GRAY,
                "/ui turret list",
                "Show all whitelisted/blacklisted targets"));

        player.sendMessage(button(
                "✖ Clear target list",
                NamedTextColor.RED,
                "/ui turret clear",
                "Remove every target from the list"));

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    public void showList(Player player, EnderCrystal crystal) {
        TurretConfig cfg = configs.get(keyFor(crystal));
        if (cfg == null || cfg.entries.isEmpty()) {
            player.sendMessage(Component.text("Target list is empty.", NamedTextColor.GRAY));
            return;
        }
        List<String> sorted = new ArrayList<>(cfg.entries);
        Collections.sort(sorted);
        player.sendMessage(Component.text("Targets (" + cfg.mode.name() + "): ", NamedTextColor.GOLD)
                .append(Component.text(String.join(", ", sorted), NamedTextColor.WHITE)));
    }

    // =====================================================================
    // ACTIONS (each persists immediately)
    // =====================================================================

    public void toggle(EnderCrystal crystal) {
        TurretConfig cfg = configFor(crystal);
        cfg.enabled = !cfg.enabled;
        save(cfg);
        if (!cfg.enabled) {
            clearBeam(crystal);
        }
    }

    public void toggleMode(EnderCrystal crystal) {
        TurretConfig cfg = configFor(crystal);
        cfg.mode = cfg.mode == Mode.WHITELIST ? Mode.BLACKLIST : Mode.WHITELIST;
        save(cfg);
    }

    public void addEntry(EnderCrystal crystal, String raw) {
        String norm = normalize(raw);
        if (norm.isEmpty()) return;
        TurretConfig cfg = configFor(crystal);
        cfg.entries.add(norm);
        save(cfg);
    }

    public void removeEntry(EnderCrystal crystal, String raw) {
        String norm = normalize(raw);
        if (norm.isEmpty()) return;
        TurretConfig cfg = configFor(crystal);
        cfg.entries.remove(norm);
        save(cfg);
    }

    public void clearEntries(EnderCrystal crystal) {
        TurretConfig cfg = configFor(crystal);
        cfg.entries.clear();
        save(cfg);
    }

    // =====================================================================
    // TICK
    // =====================================================================

    /** Sweeps every loaded end crystal and processes managed turrets. */
    public void tick() {
        for (World world : Bukkit.getWorlds()) {
            for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
                process(crystal);
            }
        }
    }

    private void process(EnderCrystal crystal) {
        TurretConfig cfg = configs.get(keyFor(crystal));
        if (cfg == null) {
            // Unmanaged crystal (e.g. a dragon-fight crystal) — leave its beam alone.
            return;
        }
        if (!cfg.enabled) {
            clearBeam(crystal);
            return;
        }

        LivingEntity target = findTarget(crystal, cfg);
        if (target == null) {
            clearBeam(crystal);
            return;
        }

        // Beam follows the victim (client renders the vanilla end crystal ray).
        crystal.setBeamTarget(target.getEyeLocation());

        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
                .withCausingEntity(crystal)
                .build();
        target.damage(DAMAGE_PER_TICK, source);
        // No i-frame bypass: the vanilla 10-tick immunity caps the rate at one
        // landed hit per 0.5s → 2 damage per second, reduced by armor/protection.
    }

    private LivingEntity findTarget(EnderCrystal crystal, TurretConfig cfg) {
        Location origin = crystal.getLocation();
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Entity entity : crystal.getWorld().getNearbyEntities(origin, RANGE, RANGE, RANGE)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.isDead() || !living.isValid()) continue;

            if (living instanceof Player player) {
                GameMode gm = player.getGameMode();
                if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) continue;
            }

            if (!matchesFilter(cfg, living)) continue;

            double distSq = living.getLocation().distanceSquared(origin);
            if (distSq > bestDistSq) continue;
            if (!hasLineOfSight(crystal, living)) continue;

            best = living;
            bestDistSq = distSq;
        }
        return best;
    }

    private boolean matchesFilter(TurretConfig cfg, LivingEntity entity) {
        boolean inList = cfg.entries.contains(entity.getType().name().toLowerCase(Locale.ROOT));
        if (entity instanceof Player player) {
            inList = inList || cfg.entries.contains(player.getName().toLowerCase(Locale.ROOT));
        }
        return cfg.mode == Mode.WHITELIST ? inList : !inList;
    }

    /** True when no solid block lies between the crystal and the target. */
    private boolean hasLineOfSight(EnderCrystal crystal, LivingEntity target) {
        World world = crystal.getWorld();
        Location start = crystal.getLocation().add(0.5, 0.5, 0.5);
        Location end = target.getEyeLocation();

        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance < 0.5) return true;

        direction.multiply(1.0 / distance);
        double maxDistance = distance - 0.5;
        if (maxDistance <= 0) return true;

        return world.rayTraceBlocks(start, direction, maxDistance,
                FluidCollisionMode.NEVER, true) == null;
    }

    private void clearBeam(EnderCrystal crystal) {
        if (crystal.getBeamTarget() != null) {
            crystal.setBeamTarget(null);
        }
    }

    private void clearAllBeams() {
        for (World world : Bukkit.getWorlds()) {
            for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
                if (configs.containsKey(keyFor(crystal))) {
                    clearBeam(crystal);
                }
            }
        }
    }

    // =====================================================================
    // CONFIG + PERSISTENCE
    // =====================================================================

    private TurretConfig configFor(EnderCrystal crystal) {
        String key = keyFor(crystal);
        TurretConfig cfg = configs.get(key);
        if (cfg == null) {
            cfg = new TurretConfig();
            cfg.world = crystal.getWorld().getName();
            cfg.x = crystal.getLocation().getBlockX();
            cfg.y = crystal.getLocation().getBlockY();
            cfg.z = crystal.getLocation().getBlockZ();
            TurretConfig existing = configs.putIfAbsent(key, cfg);
            if (existing != null) {
                cfg = existing;
            }
        }
        return cfg;
    }

    private static String keyFor(EnderCrystal crystal) {
        return crystal.getWorld().getName() + ":" + crystal.getLocation().getBlockX()
                + ":" + crystal.getLocation().getBlockY() + ":" + crystal.getLocation().getBlockZ();
    }

    private static String key(TurretConfig cfg) {
        return cfg.world + ":" + cfg.x + ":" + cfg.y + ":" + cfg.z;
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private void loadAll() {
        configs.clear();
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT world, x, y, z, enabled, mode, entries, owner FROM turrets")) {
            while (rs.next()) {
                TurretConfig cfg = new TurretConfig();
                cfg.world = rs.getString("world");
                cfg.x = rs.getInt("x");
                cfg.y = rs.getInt("y");
                cfg.z = rs.getInt("z");
                cfg.enabled = rs.getInt("enabled") == 1;
                cfg.mode = Mode.fromDb(rs.getString("mode"));
                String entries = rs.getString("entries");
                if (entries != null && !entries.isEmpty()) {
                    for (String part : entries.split(",")) {
                        String norm = normalize(part);
                        if (!norm.isEmpty()) cfg.entries.add(norm);
                    }
                }
                String ownerStr = rs.getString("owner");
                if (ownerStr != null && !ownerStr.isEmpty()) {
                    try {
                        cfg.owner = UUID.fromString(ownerStr);
                    } catch (IllegalArgumentException ignored) {
                        // Corrupt owner UUID — leave unowned.
                    }
                }
                configs.put(key(cfg), cfg);
            }
        } catch (SQLException e) {
            ConsoleLogger.warn("[Turret] Failed to load turrets: " + e.getMessage());
        }
    }

    private void save(TurretConfig cfg) {
        Connection con = DatabaseManager.getConnection();
        if (con == null) return;

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO turrets (world, x, y, z, enabled, mode, entries, owner) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(world, x, y, z) DO UPDATE SET "
                        + "enabled = excluded.enabled, mode = excluded.mode, entries = excluded.entries, "
                        + "owner = excluded.owner")) {
            ps.setString(1, cfg.world);
            ps.setInt(2, cfg.x);
            ps.setInt(3, cfg.y);
            ps.setInt(4, cfg.z);
            ps.setInt(5, cfg.enabled ? 1 : 0);
            ps.setString(6, cfg.mode.name().toLowerCase(Locale.ROOT));
            ps.setString(7, String.join(",", cfg.entries));
            ps.setString(8, cfg.owner == null ? "" : cfg.owner.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            ConsoleLogger.warn("[Turret] Failed to save turret: " + e.getMessage());
        }
    }

    // =====================================================================
    // CHAT GUI HELPERS
    // =====================================================================

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private Component suggestButton(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }
}
