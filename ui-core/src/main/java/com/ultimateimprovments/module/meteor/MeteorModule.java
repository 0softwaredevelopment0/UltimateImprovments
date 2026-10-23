package com.ultimateimprovments.module.meteor;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.module.PluginModule;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MeteorModule — manages the meteor shower.
 * <p>
 * On a timer picks a random player in one of the configured meteor worlds,
 * finds the surface near them and launches {@link MeteorTask} — a vertical
 * meteor fall that ends with an explosion and a meteorite structure.
 * <p>
 * Target worlds come from {@code meteor.worlds} (a list); an empty list falls
 * back to the single {@code meteor.world} name. The old
 * {@code space.meteor_space_only} switch used to redirect meteors to the void
 * {@code ui_space} dimension, where there is no surface to land on, so the
 * mechanic silently spawned nothing anywhere. It is obsolete since
 * 1.8.5-alpha.19: list {@code "ui_space"} in {@code meteor.worlds} instead.
 */
public class MeteorModule extends PluginModule {

    /** Extra random surface probes before giving up on the whole spawn radius. */
    private static final int RETRY_ATTEMPTS = 5;
    /** Minimum free room above the surface for a proper fall from the sky. */
    private static final int MIN_FALL_GAP = 20;
    /** Meteor start height above the impact point, in blocks (random range). */
    private static final int FALL_HEIGHT_MIN = 80;
    private static final int FALL_HEIGHT_MAX = 120;

    private static MeteorModule instance;

    /** One-time flag for the obsolete key notice (module re-inits on every soft reload). */
    private static boolean obsoleteSpaceKeyLogged = false;

    private BukkitTask scheduler;
    private final List<MeteorTask> activeMeteors = new ArrayList<>();
    private Random random = new Random();

    public MeteorModule() {
        super("Meteor", "modules/meteor", false);
    }

    public static MeteorModule getInstance() {
        return instance;
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        instance = this;
        random = new Random();

        warnAboutObsoleteSpaceOnlyKey();
        rescheduleNext(plugin);
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        // onInit runs only at startup — re-read meteor.enabled/interval on /ui reload.
        rescheduleNext(plugin);
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        if (scheduler != null) { scheduler.cancel(); scheduler = null; }

        for (MeteorTask task : new ArrayList<>(activeMeteors)) {
            try {
                task.cancel();
                task.cleanup(); // cancel() alone does not remove the Marker/BlockDisplay entities
            } catch (Exception ignored) {}
        }
        activeMeteors.clear();

        ConsoleLogger.info("[Meteor] Module disabled.");
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private FileConfiguration cfg() {
        return Main.getInstance().getConfig();
    }

    /**
     * The old {@code space.meteor_space_only} key is ignored now. It still lives
     * in already-deployed configs (repair only adds keys, it never removes
     * them), so say it once at startup instead of leaving the admin guessing.
     */
    private void warnAboutObsoleteSpaceOnlyKey() {
        if (obsoleteSpaceKeyLogged) return;
        obsoleteSpaceKeyLogged = true;
        try {
            if (cfg().isSet("space.meteor_space_only")) {
                ConsoleLogger.info("[Meteor] 'space.meteor_space_only' is obsolete and ignored — "
                        + "list the meteor worlds in [meteor] worlds = [\"world\", \"ui_space\"] instead "
                        + "(an empty list falls back to meteor.world).");
            }
        } catch (Exception ignored) {
            // A deprecated key must never break module startup.
        }
    }

    // ========================================================================
    // SCHEDULING
    // ========================================================================

    private void rescheduleNext(JavaPlugin plugin) {
        if (scheduler != null) {
            scheduler.cancel();
        }

        var cfg = cfg();
        if (!cfg.getBoolean("meteor.enabled", false)) return;

        int intervalMin = cfg.getInt("meteor.interval.min", 30);
        int intervalMax = cfg.getInt("meteor.interval.max", 90);
        // Clamp against absurd config values: intervalMax - intervalMin + 1 can
        // overflow int and nextInt(negative) would throw, killing the scheduler.
        if (intervalMin <= 0) intervalMin = 1;
        if (intervalMin > 100_000) intervalMin = 100_000;
        if (intervalMax < intervalMin) intervalMax = intervalMin;
        if (intervalMax > 100_000) intervalMax = 100_000;

        int minutes = intervalMin + random.nextInt(intervalMax - intervalMin + 1);
        long ticks = minutes * 60L * 20L;

        scheduler = new BukkitRunnable() {
            @Override
            public void run() {
                // The scheduler is the only thing that keeps the mechanic alive:
                // a single unexpected throw must not cancel the next cycle.
                try {
                    spawnRandomMeteor();
                } catch (Throwable t) {
                    ConsoleLogger.warn("[Meteor] Scheduler cycle failed: " + describe(t));
                } finally {
                    rescheduleNext(plugin);
                }
            }
        }.runTaskLater(plugin, ticks);

        if (schedulerLogging(plugin)) {
            ConsoleLogger.info("[Meteor] Next meteor in " + minutes + " minute(s) (" + ticks + " ticks).");
        }
    }

    /** Periodic scheduler log toggle ({@code meteor.log_scheduler}, default off). */
    private static boolean schedulerLogging(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean("meteor.log_scheduler", false);
    }

    // ========================================================================
    // SPAWN
    // ========================================================================

    public void spawnRandomMeteor() {
        var cfg = cfg();
        if (!cfg.getBoolean("meteor.enabled", false)) return;

        List<World> worlds = targetWorlds();
        Player target = pickRandomPlayer(worlds);
        if (target == null) {
            if (schedulerLogging(Main.getInstance())) {
                ConsoleLogger.info("[Meteor] No players online in " + worldNames(worlds) + ", skipping.");
            }
            return;
        }

        Location playerLoc = target.getLocation();
        int spawnMeteorsCount = cfg.getInt("meteor.count_per_spawn", 1);
        if (spawnMeteorsCount < 1) spawnMeteorsCount = 1;
        if (spawnMeteorsCount > 100) spawnMeteorsCount = 100;
        for (int i = 0; i < spawnMeteorsCount; i++) {
            spawnMeteorNear(target.getWorld(),
                    playerLoc.getBlockX(), playerLoc.getBlockZ(), playerLoc.getY());
        }
    }

    public int spawnMeteors(int count) {
        var cfg = cfg();
        if (!cfg.getBoolean("meteor.enabled", false)) {
            ConsoleLogger.warn("[Meteor] Module is disabled in config!");
            return 0;
        }
        if (count < 1) count = 1;
        if (count > 100) count = 100;

        List<World> worlds = targetWorlds();
        if (worlds.isEmpty()) {
            ConsoleLogger.warn("[Meteor] No meteor worlds available — nothing to spawn in.");
            return 0;
        }

        List<Player> players = collectPlayers(worlds);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            World world;
            int centerX;
            int centerZ;
            double fallbackY;
            if (!players.isEmpty()) {
                Player p = players.get(random.nextInt(players.size()));
                Location loc = p.getLocation();
                world = p.getWorld();
                centerX = loc.getBlockX();
                centerZ = loc.getBlockZ();
                fallbackY = loc.getY();
            } else {
                // Console-initiated force spawn with nobody online: use the world
                // spawn point so admins can still test/trigger the mechanic.
                world = worlds.get(i % worlds.size());
                Location spawn = world.getSpawnLocation();
                centerX = spawn.getBlockX();
                centerZ = spawn.getBlockZ();
                fallbackY = spawn.getY();
                ConsoleLogger.info("[Meteor] No players online in " + worldNames(worlds)
                        + " — using the spawn point of '" + world.getName() + "'.");
            }
            if (spawnMeteorNear(world, centerX, centerZ, fallbackY)) {
                spawned++;
            }
        }

        ConsoleLogger.info("[Meteor] Force-spawned " + spawned + " of " + count + " meteor(s).");
        return spawned;
    }

    /**
     * Resolves the worlds meteors may fall in: {@code meteor.worlds} first, then
     * the single {@code meteor.world} name. Missing world names are reported; if
     * none of the configured worlds exist the first loaded world is used instead
     * of silently disabling the whole mechanic (a renamed {@code level-name} used
     * to make meteors dead with a single console warning).
     */
    private List<World> targetWorlds() {
        var cfg = cfg();
        List<String> names = cfg.getStringList("meteor.worlds");
        boolean explicitList = !names.isEmpty();
        if (!explicitList) {
            String single = cfg.getString("meteor.world", "world");
            names = List.of(single == null || single.isBlank() ? "world" : single.trim());
        }

        List<World> worlds = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            World world = name == null || name.isBlank() ? null : Bukkit.getWorld(name.trim());
            if (world == null) {
                missing.add(String.valueOf(name));
            } else if (!worlds.contains(world)) {
                worlds.add(world);
            }
        }
        if (!missing.isEmpty()) {
            ConsoleLogger.warn("[Meteor] Configured world(s) not found: " + String.join(", ", missing));
        }
        if (worlds.isEmpty() && explicitList) {
            // An explicit list is a deliberate choice — never substitute another
            // world behind the admin's back (e.g. "only ui_space" must not turn
            // into overworld meteors).
            ConsoleLogger.warn("[Meteor] None of the meteor.worlds entries exist — meteor spawning is inactive.");
            return List.of();
        }
        if (worlds.isEmpty()) {
            List<World> loaded = Bukkit.getWorlds();
            if (!loaded.isEmpty()) {
                worlds.add(loaded.get(0));
                ConsoleLogger.warn("[Meteor] Falling back to the first loaded world '"
                        + loaded.get(0).getName() + "'.");
            }
        }
        return worlds;
    }

    private Player pickRandomPlayer(List<World> worlds) {
        List<Player> players = collectPlayers(worlds);
        return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
    }

    private List<Player> collectPlayers(List<World> worlds) {
        List<Player> players = new ArrayList<>();
        for (World world : worlds) {
            players.addAll(world.getPlayers());
        }
        return players;
    }

    private static String worldNames(List<World> worlds) {
        if (worlds.isEmpty()) return "the configured meteor worlds";
        List<String> names = new ArrayList<>();
        for (World world : worlds) names.add("'" + world.getName() + "'");
        return String.join(", ", names);
    }

    private boolean spawnMeteorNear(World world, int centerX, int centerZ, double fallbackY) {
        var cfg = cfg();

        // Cap on simultaneously active meteors (lag/crash guard)
        int maxActive = cfg.getInt("meteor.max_active", 10);
        if (maxActive < 1) maxActive = 1;
        if (maxActive > 1000) maxActive = 1000;
        if (activeMeteors.size() >= maxActive) {
            ConsoleLogger.warn("[Meteor] Active meteor limit reached (" + maxActive + "), skipping.");
            return false;
        }

        int spawnRadiusMin = cfg.getInt("meteor.spawn_radius.min", 30);
        int spawnRadiusMax = cfg.getInt("meteor.spawn_radius.max", 120);
        // Clamp min first: clamping only max could leave max < min and make
        // nextInt(max - min + 1) throw on every cycle.
        if (spawnRadiusMin <= 0) spawnRadiusMin = 1;
        if (spawnRadiusMin > 1000) spawnRadiusMin = 1000;
        if (spawnRadiusMax < spawnRadiusMin) spawnRadiusMax = spawnRadiusMin;
        if (spawnRadiusMax > 1000) spawnRadiusMax = 1000;

        int heightMax = cfg.getInt("meteor.height.max", world.getMaxHeight() - 1);
        int heightMin = cfg.getInt("meteor.height.min", world.getMinHeight());
        // Config values may be tuned for a different world (e.g. the overworld
        // values in a world with a lower build limit) — never leave this world.
        heightMax = Math.min(heightMax, world.getMaxHeight() - 2);
        heightMin = Math.max(heightMin, world.getMinHeight());
        if (heightMax < heightMin) {
            heightMax = world.getMaxHeight() - 2;
            heightMin = world.getMinHeight();
        }

        int targetX = centerX;
        int targetZ = centerZ;
        int targetY = -1;
        int lastSurface = -1;

        for (int attempt = 0; attempt < 1 + RETRY_ATTEMPTS; attempt++) {
            int dx = spawnRadiusMin + random.nextInt(spawnRadiusMax - spawnRadiusMin + 1);
            int dz = spawnRadiusMin + random.nextInt(spawnRadiusMax - spawnRadiusMin + 1);
            if (random.nextBoolean()) dx = -dx;
            if (random.nextBoolean()) dz = -dz;

            int x = centerX + dx;
            int z = centerZ + dz;
            int surface = findSurfaceY(world, x, z, heightMax, heightMin);
            if (surface < 0) continue;

            targetX = x;
            targetZ = z;
            lastSurface = surface;

            // A meteor must fall from the sky: require enough room above the surface.
            if (world.getMaxHeight() - 1 - surface >= MIN_FALL_GAP) {
                targetY = surface;
                break;
            }
        }
        // No headroom anywhere (e.g. a build at the build limit) — still land on
        // the surface, the fall will just be shorter.
        if (targetY < 0 && lastSurface >= 0) targetY = lastSurface;

        if (targetY < 0) {
            // No surface inside the configured height window. Probe at a random
            // point in the spawn radius — never on the player's own column: a
            // misconfigured height window must not drop the explosion at their feet.
            int dx = spawnRadiusMin + random.nextInt(spawnRadiusMax - spawnRadiusMin + 1);
            int dz = spawnRadiusMin + random.nextInt(spawnRadiusMax - spawnRadiusMin + 1);
            if (random.nextBoolean()) dx = -dx;
            if (random.nextBoolean()) dz = -dz;
            targetX = centerX + dx;
            targetZ = centerZ + dz;

            // Distinguish "the window excludes real terrain" from "there is no
            // terrain at all".
            int fullRangeSurface = findSurfaceY(world, targetX, targetZ,
                    world.getMaxHeight() - 2, world.getMinHeight());
            if (fullRangeSurface >= 0) {
                targetY = fullRangeSurface;
                ConsoleLogger.warn("[Meteor] meteor.height range (" + heightMin + ".." + heightMax
                        + ") excludes the surface at " + targetX + " " + targetY + " " + targetZ
                        + " — using it anyway.");
            } else {
                // Genuinely no ground (void/sky world such as the space dimension).
                // Aim at the caller's height at this random XZ instead of aborting.
                targetY = clamp((int) Math.floor(fallbackY) - 1,
                        world.getMinHeight() + 1, world.getMaxHeight() - 2);
                ConsoleLogger.warn("[Meteor] No surface found in '" + world.getName()
                        + "' near " + centerX + " " + centerZ
                        + " — using fallback height " + targetY + " (void world?).");
            }
        }

        // Parameters
        int fallDuration = cfg.getInt("meteor.fall_duration_ticks", 60);
        if (fallDuration < 1) fallDuration = 1;
        if (fallDuration > 20 * 60 * 10) fallDuration = 20 * 60 * 10; // 10 min hard cap
        int sphereRadius = cfg.getInt("meteor.sphere_radius", 2);
        if (sphereRadius < 1) sphereRadius = 1;
        if (sphereRadius > 5) sphereRadius = 5;

        // Explosion power — configurable, clamped to a sane range (TNT = 4.0)
        float explosionPower = (float) cfg.getDouble("meteor.explosion_power", 8.0);
        if (explosionPower < 0) explosionPower = 0;
        if (explosionPower > 30) explosionPower = 30;

        List<BlockData> blockDataList = loadShellBlocks(cfg);
        Map<Material, Double> coreOres = loadCoreOres(cfg);

        // Start the fall high in the sky, but keep the whole visual sphere inside
        // the world build limits: the displays reach sphereRadius blocks above the
        // marker, so the marker's maximum start height is maxHeight-1-radius.
        int maxStartY = world.getMaxHeight() - 1 - sphereRadius;
        int fallHeight = FALL_HEIGHT_MIN + random.nextInt(FALL_HEIGHT_MAX - FALL_HEIGHT_MIN + 1);
        double startY = Math.min(targetY + (double) fallHeight, maxStartY);
        if (startY <= targetY + 1.0) {
            ConsoleLogger.warn("[Meteor] Not enough room above " + targetX + " " + targetY + " " + targetZ
                    + " in '" + world.getName() + "' for a fall (build limit "
                    + world.getMaxHeight() + "), skipping.");
            return false;
        }

        // The meteor is rendered by entities (Marker + BlockDisplay). Entities
        // spawned into an unloaded chunk are silently dropped, while getChunkAt()
        // forces the chunk to load synchronously.
        world.getChunkAt(targetX >> 4, targetZ >> 4);

        MeteorTask task;
        try {
            task = new MeteorTask(
                    Main.getInstance(),
                    world,
                    startY,
                    targetX + 0.5, targetY + 1.0, targetZ + 0.5,
                    fallDuration,
                    sphereRadius,
                    explosionPower,
                    blockDataList,
                    coreOres
            );
        } catch (Exception e) {
            ConsoleLogger.warn("[Meteor] Failed to create the falling meteor at "
                    + targetX + " " + targetY + " " + targetZ + " in '" + world.getName()
                    + "': " + describe(e));
            return false;
        }

        try {
            task.setOnComplete(() -> activeMeteors.remove(task));
            activeMeteors.add(task);
            task.runTaskTimer(Main.getInstance(), 1L, 1L);
        } catch (Exception e) {
            activeMeteors.remove(task);
            task.cleanup();
            ConsoleLogger.warn("[Meteor] Failed to start the meteor task: " + describe(e));
            return false;
        }

        ConsoleLogger.info("[Meteor] Spawned meteor in '" + world.getName() + "' → ("
                + targetX + ", " + targetY + ", " + targetZ + ") radius=" + sphereRadius
                + " power=" + explosionPower + " startY=" + (int) startY);
        return true;
    }

    private List<BlockData> loadShellBlocks(FileConfiguration cfg) {
        List<String> blockNames = cfg.getStringList("meteor.display_blocks");
        if (blockNames.isEmpty()) {
            blockNames = List.of("CRACKED_DEEPSLATE_BRICKS", "CRACKED_DEEPSLATE_TILES",
                    "CHISELED_DEEPSLATE", "MAGMA_BLOCK");
        }
        List<BlockData> blockDataList = new ArrayList<>();
        for (String name : blockNames) {
            try {
                Material mat = Material.valueOf(name.toUpperCase());
                if (!mat.isBlock()) {
                    ConsoleLogger.warn("[Meteor] Display block is not a block material: " + name);
                    continue;
                }
                blockDataList.add(mat.createBlockData());
            } catch (IllegalArgumentException e) {
                ConsoleLogger.warn("[Meteor] Invalid display block: " + name);
            }
        }
        if (blockDataList.isEmpty()) {
            blockDataList.add(Material.CRACKED_DEEPSLATE_BRICKS.createBlockData());
        }
        return blockDataList;
    }

    /**
     * Loads the ore list with chances from the config.
     * Default: all deepslate ores with equal chances (1.0).
     */
    private Map<Material, Double> loadCoreOres(FileConfiguration cfg) {
        Map<Material, Double> ores = new HashMap<>();

        if (cfg.contains("meteor.core_ores") && cfg.isConfigurationSection("meteor.core_ores")) {
            var section = cfg.getConfigurationSection("meteor.core_ores");
            for (String key : section.getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    if (!mat.isBlock()) {
                        ConsoleLogger.warn("[Meteor] Core ore is not a block material: " + key);
                        continue;
                    }
                    double weight = section.getDouble(key, 1.0);
                    if (weight > 0) {
                        ores.put(mat, weight);
                    }
                } catch (IllegalArgumentException e) {
                    ConsoleLogger.warn("[Meteor] Invalid core ore: " + key);
                }
            }
        }

        // Default: all deepslate ores
        if (ores.isEmpty()) {
            ores.put(Material.DEEPSLATE_DIAMOND_ORE, 1.0);
            ores.put(Material.DEEPSLATE_IRON_ORE, 1.0);
            ores.put(Material.DEEPSLATE_GOLD_ORE, 1.0);
            ores.put(Material.DEEPSLATE_EMERALD_ORE, 1.0);
            ores.put(Material.DEEPSLATE_LAPIS_ORE, 1.0);
            ores.put(Material.DEEPSLATE_REDSTONE_ORE, 1.0);
            ores.put(Material.DEEPSLATE_COAL_ORE, 1.0);
            ores.put(Material.DEEPSLATE_COPPER_ORE, 1.0);
        }

        return ores;
    }

    private int findSurfaceY(World world, int x, int z, int maxY, int minY) {
        // Leave one block inside the world for the "block above" check.
        int top = Math.min(maxY, world.getMaxHeight() - 2);
        int bottom = Math.max(minY, world.getMinHeight());

        for (int y = top; y >= bottom; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.isEmpty() || block.isLiquid()) continue;
            if (block.getType().isSolid()) {
                Block above = world.getBlockAt(x, y + 1, z);
                if (above.isEmpty() || !above.getType().isSolid()) {
                    return y;
                }
            }
        }
        // Integer.MIN_VALUE, not -1: a legitimate surface can exist at y = -1 in
        // custom worlds, and the old sentinel made such a surface unusable.
        return Integer.MIN_VALUE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** {@code ClassName: message} for log lines — message-only logging hid root causes. */
    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank()
                ? t.getClass().getSimpleName()
                : t.getClass().getSimpleName() + ": " + message;
    }

    public void removeMeteor(MeteorTask task) {
        activeMeteors.remove(task);
    }
}
