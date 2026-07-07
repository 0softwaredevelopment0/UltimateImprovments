package com.mcplugin.energy.generation.reactor;

import com.mcplugin.core.Main;
import com.mcplugin.structure.StructureMarker;
import com.mcplugin.energy.transfer.cable.CableNetwork;
import com.mcplugin.energy.transfer.cable.CableNode;
import com.mcplugin.energy.transfer.cable.NodeType;
import com.mcplugin.mechanics.environment.radiation.RadiationManager;
import com.mcplugin.util.LocationUtil;
import com.mcplugin.util.ConsoleLogger;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Оркестратор реактора тёмного синтеза (Dark Fusion Core / Р.Т.С).
 * <p>
 * Управляет состоянием, симуляцией, сохранением и тиками реактора.
 * Визуальные эффекты, звуки и обновление табличек делегированы {@link ReactorDisplay}.<br>
 * Сохранение/загрузка — {@link ReactorPersistence}+{@link ReactorState}.<br>
 * Конфигурация — {@link ReactorConfig}.
 */
public class ReactorManager {

    // =========================
    // SINGLETON
    // =========================
    private static ReactorManager instance;

    private final ReactorDisplay display;

    public static ReactorManager getInstance() {
        return instance;
    }

    public static void init() {
        if (instance != null) return; // prevent double-init
        instance = new ReactorManager();
        ReactorConfig.init();
        instance.cfg = ReactorConfig.getInstance();
        instance.copyConfig();
        loadAll();
    }

    /** Clean shutdown — saves state, clears instance. Call before re-init for hot-toggle. */
    public static void shutdown() {
        if (instance != null) {
            saveAll();
            instance.setReactorLocation(null);
            instance = null;
        }
    }

    // =========================
    // CONFIG (delegated to ReactorConfig)
    // =========================
    private ReactorConfig cfg;
    private boolean enabled;
    private int tempDecayRate;
    private int heatRate;
    private int coolRate;
    private int coreTempMax;
    private int coreTempMin;
    private int coreTempCoolMin;
    private int corePressReduceRate;
    private int caseTempHeatRate;
    private int caseTempMax;
    private int caseTempCoolRate;
    private int caseTempCoolMin;
    private int caseTempDecayRate;
    private int casePressHeatRate;
    private int casePressMax;
    private int casePressDecayRate;
    private int shIntDecayTempThreshold;
    private int shellIntDecayRate;
    private int shellIntRecoveryTempMax;
    private int shellIntRecoveryRate;
    private int caseIntDecayPressThreshold;
    private int caseIntDecayTempThreshold;
    private int caseIntDecayPressRate;
    private int caseIntDecayTempRate;
    private int caseIntRecoveryPressMax;
    private int caseIntRecoveryTempMax;
    private int caseIntRecoveryRate;
    private boolean wearEnabled;
    private int wearIntervalNormal;
    private int wearIntervalDegradation;
    private int wearChatCountdown;
    private int wearFinalMeltdownAt;
    private int wearFinalMeltdownDuration;
    private int meltdownExplosionRadius;
    private int recipeTimeMax;

    public int getRecipeTimeMax() { return recipeTimeMax; }

    private void copyConfig() {
        if (cfg == null) return;
        enabled = cfg.isEnabled();
        tempDecayRate = cfg.getTempDecayRate();
        heatRate = cfg.getHeatRate();
        coolRate = cfg.getCoolRate();
        coreTempMax = cfg.getCoreTempMax();
        coreTempMin = cfg.getCoreTempMin();
        coreTempCoolMin = cfg.getCoreTempCoolMin();
        corePressReduceRate = cfg.getCorePressReduceRate();
        caseTempHeatRate = cfg.getCaseTempHeatRate();
        caseTempMax = cfg.getCaseTempMax();
        caseTempCoolRate = cfg.getCaseTempCoolRate();
        caseTempCoolMin = cfg.getCaseTempCoolMin();
        caseTempDecayRate = cfg.getCaseTempDecayRate();
        casePressHeatRate = cfg.getCasePressHeatRate();
        casePressMax = cfg.getCasePressMax();
        casePressDecayRate = cfg.getCasePressDecayRate();
        shIntDecayTempThreshold = cfg.getShIntDecayTempThreshold();
        shellIntDecayRate = cfg.getShellIntDecayRate();
        shellIntRecoveryTempMax = cfg.getShellIntRecoveryTempMax();
        shellIntRecoveryRate = cfg.getShellIntRecoveryRate();
        caseIntDecayPressThreshold = cfg.getCaseIntDecayPressThreshold();
        caseIntDecayTempThreshold = cfg.getCaseIntDecayTempThreshold();
        caseIntDecayPressRate = cfg.getCaseIntDecayPressRate();
        caseIntDecayTempRate = cfg.getCaseIntDecayTempRate();
        caseIntRecoveryPressMax = cfg.getCaseIntRecoveryPressMax();
        caseIntRecoveryTempMax = cfg.getCaseIntRecoveryTempMax();
        caseIntRecoveryRate = cfg.getCaseIntRecoveryRate();
        wearEnabled = cfg.isWearEnabled();
        wearIntervalNormal = cfg.getWearIntervalNormal();
        wearIntervalDegradation = cfg.getWearIntervalDegradation();
        wearChatCountdown = cfg.getWearChatCountdown();
        wearFinalMeltdownAt = cfg.getWearFinalMeltdownAt();
        wearFinalMeltdownDuration = cfg.getWearFinalMeltdownDuration();
        meltdownExplosionRadius = cfg.getMeltdownExplosionRadius();
        recipeTimeMax = cfg.getRecipeTimeMax();
    }

    // =========================
    // STATE
    // =========================
    private Location reactorLocation;
    private boolean valid;
    private String reactorId;

    // Core parameters
    private int coreTemp;
    private int corePress;
    private int coreShInt = 100;    // Shell integrity (0-100%)
    private int coreCaseTemp;
    private int coreCasePress;
    private int coreCaseInt = 100;  // Case integrity (0-100%)

    // Recipe
    private int recipeTime;
    private boolean rcDone;

    // Self-destruct
    private boolean selfDestruct;
    private int sdText;

    // =========================
    // ENERGY GENERATION (output to cable network)
    // =========================
    private long energyGenerated;
    private double energyRemainder;

    // =========================
    // WEAR SYSTEM
    // =========================
    private int reactorWear;
    private int wearTickCounter;
    private boolean prevWearDegraded;
    private boolean selfDestructActive;
    private int selfDestructChatTimer;
    private boolean finalMeltdownActive;

    // Meltdown countdown (10s before explosion)
    private boolean meltdownCountdown;
    private int meltdownTimer;

    // Previous integrity values for threshold detection
    private int prevShInt = 100;
    private int prevCaseInt = 100;

    // Tick counters
    private int pressTick;
    private int recipeTick;
    private int intensityDownTick;
    private int intensityUpTick;
    private int soundTick;
    private int noFuelWarnTick;

    // =========================
    // PENDING ASSEMBLY
    // =========================
    // Advancement tracking (one-time grants)
    private boolean advStartDfcGranted = false;
    private boolean advDfcUnstableGranted = false;
    private boolean advDfcSelfDestructGranted = false;
    private boolean advExplodeDfcGranted = false;
    private boolean advCompletedRecipeGranted = false;
    private final Set<UUID> advInsideDfcGranted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> advBurnInsideDfcGranted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> advOneTimeHeaterGranted = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Map<String, PendingAssembly>> pendingAssemblies = new HashMap<>();

    public record PendingAssembly(Location center, org.bukkit.entity.ItemFrame frame, String type) {}

    public static void setPendingAssembly(Player player, Location center, org.bukkit.entity.ItemFrame frame, String type) {
        pendingAssemblies.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(type, new PendingAssembly(center, frame, type));
    }

    public static PendingAssembly getPendingAssembly(Player player, String type) {
        var map = pendingAssemblies.get(player.getUniqueId());
        return map != null ? map.get(type) : null;
    }

    public static void clearPendingAssembly(Player player) {
        pendingAssemblies.remove(player.getUniqueId());
    }

    // =========================
    // GRANT ADVANCEMENT HELPER
    // =========================
    private void grantAdvancement(Player player, String key) {
        try {
            var adv = Bukkit.getAdvancement(new org.bukkit.NamespacedKey("minecraft", key));
            if (adv != null) {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            }
        } catch (Exception e) {
            ConsoleLogger.warn("[Reactor] grantAdvancement error: " + e.getMessage());
        }
    }

    private void grantAdvancementAll(String key) {
        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        for (Player player : online) {
            if (reactorLocation != null
                    && player.getWorld().equals(reactorLocation.getWorld())
                    && player.getLocation().distanceSquared(reactorLocation) <= 225) {
                grantAdvancement(player, key);
            }
        }
    }

    // =========================
    // CONSTRUCTOR
    // =========================
    private ReactorManager() {
        this.display = new ReactorDisplay(this);
    }

    // =========================
    // DATABASE PERSISTENCE
    // =========================
    public static void saveAll() {
        ReactorManager r = instance;
        if (r == null) return;
        ReactorState state = buildState(r);
        ReactorPersistence.saveAll(state);
    }

    public void saveToDb() {
        ReactorState state = buildState(this);
        ReactorPersistence.saveToDb(state);
    }

    private static ReactorState buildState(ReactorManager r) {
        ReactorState s = new ReactorState();
        s.setReactorLocation(r.reactorLocation);
        s.setCoreTemp(r.coreTemp);
        s.setCorePress(r.corePress);
        s.setCoreShInt(r.coreShInt);
        s.setCoreCaseTemp(r.coreCaseTemp);
        s.setCoreCasePress(r.coreCasePress);
        s.setCoreCaseInt(r.coreCaseInt);
        s.setRecipeTime(r.recipeTime);
        s.setSelfDestruct(r.selfDestruct);
        s.setReactorWear(r.reactorWear);
        s.setEnergyGenerated(r.energyGenerated);
        return s;
    }

    public static void loadAll() {
        if (instance == null) return;
        ReactorState state = new ReactorState();
        if (ReactorPersistence.loadFromDb(state)) {
            instance.reactorLocation = state.getReactorLocation();
            instance.valid = state.isValid();
            instance.reactorId = state.getReactorId();
            instance.coreTemp = state.getCoreTemp();
            instance.corePress = state.getCorePress();
            instance.coreShInt = state.getCoreShInt();
            instance.coreCaseTemp = state.getCoreCaseTemp();
            instance.coreCasePress = state.getCoreCasePress();
            instance.coreCaseInt = state.getCoreCaseInt();
            instance.recipeTime = state.getRecipeTime();
            instance.selfDestruct = state.isSelfDestruct();
            instance.reactorWear = state.getReactorWear();
            instance.energyGenerated = state.getEnergyGenerated();
        }
    }

    public static void deleteFromDb(String reactorId) {
        ReactorPersistence.deleteFromDb(reactorId);
    }

    // =========================
    // GET LOCATION
    // =========================
    public Location getReactorLocation() { return reactorLocation; }
    public boolean isValid() { return valid && reactorLocation != null; }
    public String getReactorId() { return reactorId; }

    // =========================
    // SET REACTOR LOCATION
    // =========================
    public void setReactorLocation(Location loc) {
        if (loc != null) {
            Location normalized = LocationUtil.normalize(loc);
            this.reactorLocation = normalized;
            this.valid = true;
            this.reactorId = "REACTOR-" + normalized.getBlockX()
                    + "-" + normalized.getBlockY()
                    + "-" + normalized.getBlockZ();
            // Marker entity для идентификации реактора
            StructureMarker.place(normalized, "reactor", UUID.randomUUID());
            saveToDb();
        } else {
            if (reactorLocation != null) {
                StructureMarker.removeAt(reactorLocation);

                // Удаляем кабельный узел, созданный реактором для выдачи энергии
                Location coreLoc = reactorLocation.clone().add(0, -1, 0);
                if (CableNetwork.exists(coreLoc)) {
                    CableNetwork.removeNode(coreLoc);
                }
            }
            if (reactorId != null) {
                deleteFromDb(reactorId);
            }
            this.reactorLocation = null;
            this.valid = false;
            this.reactorId = null;
            resetAll();
        }
    }

    // =========================
    // VALIDATE STRUCTURE
    // =========================
    public void validateStructure() {
        if (reactorLocation == null) return;
        boolean wasValid = valid;
        valid = ReactorStructure.isValid(reactorLocation, false);
        if (!valid && wasValid) {
            setReactorLocation(null);
        }
    }

    // =========================
    // MAIN TICK (every server tick)
    // =========================
    public void tick() {
        if (!enabled || !valid || reactorLocation == null) return;

        Location base = reactorLocation;

        boolean heating = display.isBulbPowered(base, -1, 0, -2);
        boolean cooling = display.isBulbPowered(base, 1, 0, -2);

        // =========================
        // BROADCAST STATE CHANGES
        // =========================
        if (heating != display.wasHeating()) {
            broadcast(heating ? "§6🔥 §eНагрев включён" : "§7🔥 §fНагрев выключен");
            display.setHeating(heating);

            // 🏆 Достижение: start_dfc — реактор запущен
            if (heating && !advStartDfcGranted) {
                advStartDfcGranted = true;
                Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                    grantAdvancementAll("datapack/start_dfc"));
            }
        }
        if (cooling != display.wasCooling()) {
            broadcast(cooling ? "§b❄ §3Охлаждение включено" : "§7❄ §fОхлаждение выключено");
            display.setCooling(cooling);
        }

        // =========================
        // TEMPERATURE CONTROL
        // =========================
        if (heating && coreTemp < coreTempMax) {
            if (hasBarrelFuel()) {
                coreTemp += heatRate;
                noFuelWarnTick = 0;

                // 🏆 Достижение: start_dfc — реактор запущен (если ещё не выдано)
                if (!advStartDfcGranted) {
                    advStartDfcGranted = true;
                    Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                        grantAdvancementAll("datapack/start_dfc"));
                }
            } else if (noFuelWarnTick == 0) {
                broadcast("§e⚠ §7Нет топлива! В левую бочку поместите алмазные блоки, в правую — золотые блоки.");
                noFuelWarnTick++;
            } else {
                noFuelWarnTick++;
            }
        }
        if (cooling && coreTemp > coreTempCoolMin) {
            coreTemp -= coolRate;
        }

        // =========================
        // CASE REACTION
        // =========================
        if (heating && hasBarrelFuel()) {
            coreCaseTemp = Math.min(coreCaseTemp + caseTempHeatRate, caseTempMax);
            coreCasePress = Math.min(coreCasePress + casePressHeatRate, casePressMax);
        }
        if (cooling) {
            coreCaseTemp = Math.max(coreCaseTemp - caseTempCoolRate, caseTempCoolMin);
        }

        // =========================
        // INTEGRITY INDICATOR BULBS
        // =========================
        display.updateIntegrityBulbs(base);

        // =========================
        // INTEGRITY WARNING (every 10 seconds)
        // =========================
        int warnTick = display.getIntegrityWarnTick() + 1;
        display.setIntegrityWarnTick(warnTick);
        if (warnTick >= 200) {
            display.setIntegrityWarnTick(0);
            if (coreShInt < 100) broadcast("§4⚠ §cЦелостность оболочки ядра нарушена!");
            if (coreCaseInt < 100) broadcast("§4⚠ §cЦелостность корпуса реактора нарушена!");
        }

        // =========================
        // PRESSURE
        // =========================
        corePress += coreTemp;
        if (corePress < 0) corePress = 0;
        if (coreTemp >= 1000 && coreTemp <= 5000) {
            corePress = Math.max(0, corePress - corePressReduceRate);
        }

        // =========================
        // RADIATION INSIDE CORE CHAMBER
        // =========================
        if (coreTemp >= 1000) {
            int radiationAmount = Math.min(coreTemp / 500, 10);
            int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
            Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            for (Player player : online) {
                if (!player.getWorld().equals(base.getWorld())) continue;
                Location ploc = player.getLocation();
                int px = ploc.getBlockX(), py = ploc.getBlockY(), pz = ploc.getBlockZ();
                if (px >= bx - 2 && px <= bx + 2
                        && py >= by - 5 && py <= by
                        && pz >= bz - 2 && pz <= bz + 2) {
                    RadiationManager.addRadiation(player, radiationAmount);

                    // 🏆 Достижение: inside_dfc — игрок внутри реактора
                    if (advInsideDfcGranted.add(player.getUniqueId())) {
                        Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                            grantAdvancement(player, "datapack/inside_dfc"));
                    }

                    // 🏆 Достижение: burn_inside_dfc — смертельная доза внутри реактора
                    if (RadiationManager.getRadiation(player) >= 6400) {
                        if (advBurnInsideDfcGranted.add(player.getUniqueId())) {
                            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                                grantAdvancement(player, "datapack/burn_inside_dfc"));
                        }
                    }
                }
            }
        }

        // =========================
        // NATURAL TEMP DECAY
        // =========================
        if (coreTemp > coreTempMin) {
            coreTemp = Math.max(coreTempMin, coreTemp - tempDecayRate);
        }

        // =========================
        // CASE PRESSURE DECAY
        // =========================
        if (coreCasePress > 0) {
            coreCasePress -= casePressDecayRate;
            if (coreCasePress < 0) coreCasePress = 0;
        }

        // =========================
        // CASE TEMP DECAY
        // =========================
        if (coreCaseTemp > caseTempCoolMin) {
            coreCaseTemp -= caseTempDecayRate;
        }

        // =========================
        // RECIPE TIME
        // =========================
        if (rcDone) {
            completeRecipe();
        }

        // =========================
        // INTEGRITY THRESHOLD WARNINGS (75%, 50%, 25%)
        // =========================
        checkIntegrityThreshold(prevShInt, coreShInt, "оболочки ядра");
        checkIntegrityThreshold(prevCaseInt, coreCaseInt, "корпуса");
        prevShInt = coreShInt;
        prevCaseInt = coreCaseInt;

        // =========================
        // ENERGY GENERATION
        // =========================
        if (coreTemp > 1000) {
            double energyPerTick = (double) coreTemp * 0.9;
            energyRemainder += energyPerTick;
            int toGenerate = (int) energyRemainder;
            if (toGenerate > 0) {
                energyRemainder -= toGenerate;
                energyGenerated += toGenerate;

                // Optimized: iterate only nodes in the same world, not ALL worlds
                java.util.Collection<CableNode> worldNodes = CableNetwork.getWorldNodes(base.getWorld().getUID().toString());
                java.util.List<CableNode> nearbyCables = new java.util.ArrayList<>();
                for (CableNode node : worldNodes) {
                    int dx = Math.abs(node.getBlockX() - base.getBlockX());
                    int dy = Math.abs(node.getBlockY() - base.getBlockY());
                    int dz = Math.abs(node.getBlockZ() - base.getBlockZ());
                    if (dx <= 3 && dy <= 5 && dz <= 3) {
                        nearbyCables.add(node);
                    }
                }

                if (!nearbyCables.isEmpty()) {
                    int remaining = toGenerate;
                    int perNode = toGenerate / nearbyCables.size();
                    for (CableNode node : nearbyCables) {
                        int space = node.getMaxEnergy() - node.getEnergy();
                        int give = Math.min(perNode, space);
                        if (give <= 0) continue;
                        node.addEnergy(give);
                        remaining -= give;
                        CableNetwork.saveNode(node);
                    }
                    if (remaining > 0) {
                        Location coreLoc = base.clone().add(0, -1, 0);
                        CableNode genNode = CableNetwork.getNode(coreLoc);
                        if (genNode == null) {
                            CableNetwork.addNode(coreLoc);
                            genNode = CableNetwork.getNode(coreLoc);
                        }
                        if (genNode != null) {
                            genNode.setType(NodeType.GENERATOR);
                            genNode.setMaxEnergy(coreTempMax * 10);
                            genNode.addEnergy(remaining);
                            CableNetwork.saveNode(genNode);
                        }
                    }
                } else {
                    Location coreLoc = base.clone().add(0, -1, 0);
                    CableNode genNode = CableNetwork.getNode(coreLoc);
                    if (genNode == null) {
                        CableNetwork.addNode(coreLoc);
                        genNode = CableNetwork.getNode(coreLoc);
                    }
                    if (genNode != null) {
                        genNode.setType(NodeType.GENERATOR);
                        genNode.setMaxEnergy(coreTempMax * 10);
                        genNode.addEnergy(toGenerate);
                        CableNetwork.saveNode(genNode);
                    }
                }
            }
        }

        // =========================
        // MELTDOWN COUNTDOWN START
        // =========================
        if ((coreShInt <= 0 || coreCaseInt <= 0) && !meltdownCountdown && !selfDestructActive) {
            meltdownCountdown = true;
            meltdownTimer = 200; // 10 seconds
            selfDestruct = true;
            broadcast("§4☠ §cЦелостность разрушена! §f10§c секунд до детонации...");
        }
    }

    // =========================
    // PRESSURE TICK (every 5s)
    // =========================
    public void tickPressure() {
        if (!enabled || !valid || reactorLocation == null) return;

        Location base = reactorLocation;
        Location coreCenter = base.clone().add(0.5, -2.5, 0.5);

        int particleCount;
        int radAmount;

        if (corePress >= 500000)      { particleCount = 512; radAmount = 600; }
        else if (corePress >= 400000) { particleCount = 256; radAmount = 500; }
        else if (corePress >= 300000) { particleCount = 128; radAmount = 400; }
        else if (corePress >= 200000) { particleCount = 64;  radAmount = 300; }
        else if (corePress >= 100000) { particleCount = 32;  radAmount = 200; }
        else                          { particleCount = 0;   radAmount = 0;   }

        if (particleCount > 0) {
            Location smokePos = coreCenter.clone().add(0, 2.5, 0);
            base.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE,
                    smokePos, particleCount, 0, 0, 0, 0.1);
            if (radAmount > 0) {
                RadiationManager.addRadiationNear(base, 4.0, radAmount);
            }
        }

        // Pressure division
        if (coreTemp != 0) {
            corePress = corePress / coreTemp;
        } else {
            corePress = 0;
        }
    }

    // =========================
    // INTENSITY DECAY TICK (every 1s)
    // =========================
    public void tickIntensityDown() {
        if (!enabled || !valid) return;

        if (coreTemp >= shIntDecayTempThreshold && coreShInt > 0) {
            coreShInt = Math.max(0, coreShInt - shellIntDecayRate);
        }
        if (coreCasePress >= caseIntDecayPressThreshold && coreCaseInt > 0) {
            coreCaseInt = Math.max(0, coreCaseInt - caseIntDecayPressRate);
        }
        if (coreCaseTemp >= caseIntDecayTempThreshold && coreCaseInt > 0) {
            coreCaseInt = Math.max(0, coreCaseInt - caseIntDecayTempRate);
        }

        // 🏆 Достижение: dfc_unstable — первая деградация целостности
        Bukkit.getScheduler().runTask(Main.getInstance(), this::checkDfcUnstable);
    }

    // =========================
    // INTENSITY RECOVERY TICK (every 3s)
    // =========================
    public void tickIntensityUp() {
        if (!enabled || !valid) return;

        if (coreTemp <= shellIntRecoveryTempMax && coreShInt < 100) {
            coreShInt = Math.min(100, coreShInt + shellIntRecoveryRate);
        }
        if (coreCasePress <= caseIntRecoveryPressMax && coreCaseTemp <= caseIntRecoveryTempMax && coreCaseInt < 100) {
            coreCaseInt = Math.min(100, coreCaseInt + caseIntRecoveryRate);
        }
    }

    // =========================
    // RECIPE TICK (every 5s)
    // =========================
    public void tickRecipe() {
        if (!enabled || !valid) return;

        if (coreTemp < 1000 && recipeTime > 0) {
            recipeTime--;
        }
        if (coreTemp >= 1000 && coreTemp <= 5000 && recipeTime < recipeTimeMax) {
            recipeTime++;
        }
        if (recipeTime >= recipeTimeMax && hasBarrelFuel()) {
            rcDone = true;
        }
    }

    // =========================
    // WEAR TICK (every second)
    // =========================
    public void tickWear() {
        if (!enabled || !valid || reactorLocation == null) return;

        if (wearEnabled && !selfDestructActive) {
            boolean isDegraded = coreShInt < 100 || coreCaseInt < 100;
            if (isDegraded != prevWearDegraded) {
                wearTickCounter = 0;
                prevWearDegraded = isDegraded;
            }
            wearTickCounter++;

            if (isDegraded) {
                if (wearTickCounter >= wearIntervalDegradation) {
                    wearTickCounter = 0;
                    if (reactorWear > 0) reactorWear--;
                }
            } else {
                if (wearTickCounter >= wearIntervalNormal) {
                    wearTickCounter = 0;
                    if (reactorWear < 100) {
                        reactorWear++;
                        if (reactorWear >= 100 && !selfDestructActive) {
                            startSelfDestruct();
                        }
                    }
                }
            }
        }

        if (selfDestructActive && !meltdownCountdown) {
            selfDestructChatTimer--;
            if (selfDestructChatTimer <= wearFinalMeltdownAt) {
                finalMeltdownActive = true;
                meltdownCountdown = true;
                meltdownTimer = wearFinalMeltdownDuration * 20;
                broadcast("§4☠ §cВзрыв неизбежен! §f" + wearFinalMeltdownDuration + "§c сек до детонации...");
            } else if (selfDestructChatTimer > 0) {
                broadcast("§4☠ §cДетонация через §f" + selfDestructChatTimer + "§c сек...");
            }
        }
    }

    // =========================
    // MELTDOWN COUNTDOWN TICK (final 10s)
    // =========================
    public void tickMeltdownCountdown() {
        if (!meltdownCountdown || !enabled || !valid || reactorLocation == null) return;

        meltdownTimer--;
        if (meltdownTimer > 0 && meltdownTimer % 20 == 0) {
            broadcast("§4☠ §cВзрыв неизбежен! §f" + (meltdownTimer / 20) + "§c сек...");
        }
        if (meltdownTimer <= 0) {
            meltdownCountdown = false;
            finalMeltdownActive = false;
            selfDestructActive = false;
            meltdown();
        }
    }

    // =========================
    // SOUND TICK (every 10 ticks)
    // =========================
    public void tickSound() {
        display.tickSound();
    }

    // =========================
    // SMOOTH DISPLAY TICK (every tick)
    // =========================
    public void tickSmoothDisplay() {
        display.tickSmoothDisplay();
    }

    // =========================
    // VISUAL TICK (every tick - particles)
    // =========================
    public void tickVisual() {
        display.tickVisual();
    }

    // =========================
    // UPDATE DISPLAYS (signs)
    // =========================
    public void updateDisplays() {
        display.updateDisplays();
    }

    // =========================
    // START SELF-DESTRUCT
    // =========================
    private void startSelfDestruct() {
        selfDestruct = true;
        selfDestructActive = true;
        selfDestructChatTimer = wearChatCountdown;
        finalMeltdownActive = false;
        broadcast("§4☠ §cКритический износ реактора! §f" + wearChatCountdown + "§c сек до детонации...");
        broadcast("§4☠ §cПротокол самоуничтожения инициирован.");

        // 🏆 Достижение: dfc_self_destruct — самоуничтожение
        if (!advDfcSelfDestructGranted) {
            advDfcSelfDestructGranted = true;
            grantAdvancementAll("datapack/dfc_self_destruct");
        }
    }

    // =========================
    // INTEGRITY THRESHOLD CHECK
    // =========================
    private void checkIntegrityThreshold(int prevVal, int currVal, String name) {
        if (currVal < prevVal) {
            if (currVal == 75 || currVal == 50 || currVal == 25) {
                broadcast("§4⚠ §cЦелостность " + name + ": §f" + currVal + "%");
            }
        }
    }

    // =========================
    // INTEGRITY DECAY — dfc_unstable achievement
    // =========================
    private void checkDfcUnstable() {
        if (!advDfcUnstableGranted && (coreShInt < 100 || coreCaseInt < 100)) {
            advDfcUnstableGranted = true;
            Bukkit.getScheduler().runTask(Main.getInstance(), () ->
                grantAdvancementAll("datapack/dfc_unstable"));
        }
    }

    // =========================
    // RECIPE COMPLETION
    // =========================
    private void completeRecipe() {
        if (reactorLocation == null) return;
        Location base = reactorLocation;

        consumeBarrelFuel(base, 0, -3, -2, Material.DIAMOND_BLOCK);
        consumeBarrelFuel(base, 0, -3, 2, Material.GOLD_BLOCK);

        Location dropLoc = base.clone().add(0.5, -2.5, 0.5);
        dropLoc.getWorld().dropItemNaturally(dropLoc, new ItemStack(Material.ANCIENT_DEBRIS, 1));

        World world = dropLoc.getWorld();
        world.spawnParticle(Particle.FLAME, dropLoc, 120, 2.5, 2.5, 2.5, 0.15);
        world.spawnParticle(Particle.LAVA, dropLoc, 40, 1.5, 1.5, 1.5, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, dropLoc, 60, 2.0, 2.0, 2.0, 0.1);
        world.spawnParticle(Particle.ASH, dropLoc, 80, 3.0, 3.0, 3.0, 0.05);
        world.spawnParticle(Particle.SMALL_FLAME, dropLoc, 50, 1.8, 1.8, 1.8, 0.08);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, dropLoc, 40, 1.0, 1.0, 1.0, 0.02);

        world.playSound(dropLoc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 2.0f, 0.5f);
        world.playSound(dropLoc, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.MASTER, 1.5f, 0.8f);

        recipeTime = 0;
        rcDone = false;
        coreShInt = 100;
        coreTemp = 0;
        coreCaseInt = 100;
        coreCaseTemp = 0;
        coreCasePress = 0;
        corePress = 0;
        selfDestruct = false;
        sdText = 0;
        meltdownCountdown = false;
        meltdownTimer = 0;
        prevShInt = 100;
        prevCaseInt = 100;
        reactorWear = 0;
        wearTickCounter = 0;
        prevWearDegraded = false;
        selfDestructActive = false;
        selfDestructChatTimer = 0;
        finalMeltdownActive = false;
        energyGenerated = 0;
        energyRemainder = 0;
        noFuelWarnTick = 0;

        display.resetDisplay();

        saveToDb();
        broadcast("§4☢ §cРецепт слияния готов! Древний обломок выброшен в центре реактора.");

        // 🏆 Достижение: complete_dfc_recipe — рецепт завершён
        if (!advCompletedRecipeGranted) {
            advCompletedRecipeGranted = true;
            grantAdvancementAll("datapack/complete_dfc_recipe");
        }
    }

    // =========================
    // MELTDOWN
    // =========================
    private void meltdown() {
        energyRemainder = 0;
        energyGenerated = 0;

        if (reactorLocation == null) return;

        Location base = reactorLocation;
        Location coreCenter = base.clone().add(0, -2, 0);

        RadiationManager.addRadiationNear(coreCenter, 1.0, 6400);
        RadiationManager.addRadiationNear(coreCenter, 20.0, 3200);

        base.clone().add(0, -1, 0).getBlock().setType(Material.AIR);
        base.clone().add(0, -5, 0).getBlock().setType(Material.AIR);

        coreCenter.getWorld().spawn(coreCenter, org.bukkit.entity.Creeper.class, creeper -> {
            creeper.setPowered(true);
            creeper.setExplosionRadius(meltdownExplosionRadius);
            creeper.setMaxFuseTicks(0);
            creeper.setIgnited(true);
        });

        base.getWorld().strikeLightning(coreCenter);
        base.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, coreCenter, 100, 3.0, 3.0, 3.0, 0.5);
        base.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, coreCenter, 64, 0, 0, 0, 0.1);

        broadcast("§4☠ §cРасплавление! Ядро реактора разрушено!");

        // 🏆 Достижение: explode_dfc — взрыв реактора
        if (!advExplodeDfcGranted) {
            advExplodeDfcGranted = true;
            grantAdvancementAll("datapack/explode_dfc");
        }

        // 🏆 Достижение: one_time_heater — игрок внутри реактора во время взрыва
        if (reactorLocation != null) {
            int bx = reactorLocation.getBlockX(), by = reactorLocation.getBlockY(), bz = reactorLocation.getBlockZ();
            Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            for (Player player : online) {
                if (!player.getWorld().equals(base.getWorld())) continue;
                if (advOneTimeHeaterGranted.contains(player.getUniqueId())) continue;
                Location ploc = player.getLocation();
                int px = ploc.getBlockX(), py = ploc.getBlockY(), pz = ploc.getBlockZ();
                if (px >= bx - 2 && px <= bx + 2
                        && py >= by - 5 && py <= by
                        && pz >= bz - 2 && pz <= bz + 2) {
                    advOneTimeHeaterGranted.add(player.getUniqueId());
                    grantAdvancement(player, "datapack/one_time_heater");
                }
            }
        }

        setReactorLocation(null);
    }

    // =========================
    // RESET ALL PARAMETERS
    // =========================
    private void resetAll() {
        coreTemp = 0;
        corePress = 0;
        coreShInt = 100;
        coreCaseTemp = 0;
        coreCasePress = 0;
        coreCaseInt = 100;
        recipeTime = 0;
        rcDone = false;
        selfDestruct = false;
        sdText = 0;
        reactorWear = 0;
        wearTickCounter = 0;
        prevWearDegraded = false;
        selfDestructActive = false;
        selfDestructChatTimer = 0;
        finalMeltdownActive = false;
        energyGenerated = 0;
        energyRemainder = 0;
        pressTick = 0;
        recipeTick = 0;
        intensityDownTick = 0;
        intensityUpTick = 0;
        soundTick = 0;
        noFuelWarnTick = 0;
        meltdownCountdown = false;
        meltdownTimer = 0;
        prevShInt = 100;
        prevCaseInt = 100;

        display.resetDisplay();
    }

    // =========================
    // BARREL FUEL HELPERS
    // =========================
    private boolean checkBarrelForFuel(Location base, int dx, int dy, int dz, Material fuelType, int minCount) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() != Material.BARREL) return false;
        Barrel barrel = (Barrel) block.getState();
        Inventory inv = barrel.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == fuelType && item.getAmount() >= minCount) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBarrelFuel() {
        if (reactorLocation == null) return false;
        Location base = reactorLocation;
        return checkBarrelForFuel(base, 0, -3, -2, Material.DIAMOND_BLOCK, 1)
            && checkBarrelForFuel(base, 0, -3, 2, Material.GOLD_BLOCK, 1);
    }

    private boolean consumeBarrelFuel(Location base, int dx, int dy, int dz, Material fuelType) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() != Material.BARREL) return false;
        Barrel barrel = (Barrel) block.getState(false);
        Inventory inv = barrel.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == fuelType) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    inv.setItem(i, item);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    // =========================
    // GETTERS
    // =========================
    public int getCoreTemp() { return coreTemp; }
    public int getCorePress() { return corePress; }
    public int getCoreShInt() { return coreShInt; }
    public int getCoreCaseTemp() { return coreCaseTemp; }
    public int getCoreCasePress() { return coreCasePress; }
    public int getCoreCaseInt() { return coreCaseInt; }
    public int getRecipeTime() { return recipeTime; }
    public boolean isSelfDestruct() { return selfDestruct; }
    public boolean isMeltdownCountdown() { return meltdownCountdown; }
    public int getMeltdownTimer() { return meltdownTimer; }
    public boolean isSelfDestructActive() { return selfDestructActive; }
    public boolean isFinalMeltdownActive() { return finalMeltdownActive; }
    public int getReactorWear() { return reactorWear; }
    public long getEnergyGenerated() { return energyGenerated; }

    // Smoothed display values (delegated to ReactorDisplay)
    public int getDisplayCoreTemp() { return display.getDisplayCoreTemp(); }
    public int getDisplayCorePress() { return display.getDisplayCorePress(); }
    public int getDisplayCoreShInt() { return display.getDisplayCoreShInt(); }
    public int getDisplayCoreCaseTemp() { return display.getDisplayCoreCaseTemp(); }
    public int getDisplayCoreCasePress() { return display.getDisplayCoreCasePress(); }
    public int getDisplayCoreCaseInt() { return display.getDisplayCoreCaseInt(); }
    public int getDisplayRecipeTime() { return display.getDisplayRecipeTime(); }
    public int getDisplayReactorWear() { return display.getDisplayReactorWear(); }
    public int getDisplayEnergyRate() { return display.getDisplayEnergyRate(); }

    // =========================
    // HELPER: BROADCAST
    // =========================
    private void broadcast(String message) {
        String prefix = "§4Р.Т.С §8» §f";
        Player[] online = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        for (Player player : online) {
            if (reactorLocation != null
                    && player.getWorld().equals(reactorLocation.getWorld())
                    && player.getLocation().distanceSquared(reactorLocation) <= 225) {
                player.sendMessage(prefix + message);
            }
        }
    }
}
