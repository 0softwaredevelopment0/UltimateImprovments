package com.mcplugin.core1;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.cable.CableNode;
import com.mcplugin.cable.NodeType;
import com.mcplugin.database.DatabaseManager;
import com.mcplugin.radiation.RadiationManager;
import com.mcplugin.util.LocationUtil;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class ReactorManager {

    // =========================
    // SINGLETON
    // =========================
    private static ReactorManager instance;

    public static ReactorManager getInstance() {
        return instance;
    }

    public static void init() {
        instance = new ReactorManager();
        ReactorConfig.init();
        instance.cfg = ReactorConfig.getInstance();
        instance.copyConfig();
        loadAll();
    }
    
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

    // =========================
    // STATE
    // =========================
    private Location reactorLocation;
    private boolean valid;
    private String reactorId; // Unique ID derived from structure location

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
    private int sdText; // 0 or 1 - message display flag

    // =========================
    // ENERGY GENERATION (output to cable network)
    // 1 temp unit = +1 energy/sec
    // =========================
    private long energyGenerated;
    private double energyRemainder; // fractional remainder for smooth per-tick generation

    // =========================
    // WEAR SYSTEM (износ реактора)
    // =========================
    private int reactorWear;          // 0-100%
    private int wearTickCounter;      // тики до следующего +1%
    private boolean prevWearDegraded; // предыдущее состояние целостности (для сброса counter при переключении)
    private boolean selfDestructActive; // true когда wear >= 100%
    private int selfDestructChatTimer;  // тики чат-таймера (20 тиков = 1 сек)
    private boolean finalMeltdownActive; // true когда чат-таймер дошёл до отметки

    // Meltdown countdown (10s before explosion)
    private boolean meltdownCountdown;
    private int meltdownTimer;

    // Previous integrity values for threshold detection
    private int prevShInt = 100;
    private int prevCaseInt = 100;

    // =========================
    // SMOOTHED DISPLAY VALUES
    // (interpolated toward actual values for smooth sign updates)
    // =========================
    private double displayCoreTemp;
    private double displayCorePress;
    private double displayCoreShInt = 100;
    private double displayCoreCaseTemp;
    private double displayCoreCasePress;
    private double displayCoreCaseInt = 100;
    private double displayRecipeTime;
    private double displayReactorWear;
    private double displayEnergyRate;

    private static final double SMOOTHING_FACTOR = 0.35;

    // =========================
    // PENDING ASSEMBLY (chat menu → command) — per type
    // =========================
    private static final Map<UUID, Map<String, PendingAssembly>> pendingAssemblies = new HashMap<>();

    public record PendingAssembly(Location center, ItemFrame frame, String type) {}

    public static void setPendingAssembly(Player player, Location center, ItemFrame frame, String type) {
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
    // TICK COUNTERS
    // =========================
    private int pressTick;
    private int recipeTick;
    private int intensityDownTick;
    private int intensityUpTick;
    private int soundTick;
    private int displayTick;
    private boolean prevHeating;
    private boolean prevCooling;
    private int integrityWarnTick;
    private int noFuelWarnTick; // prevents spam when no fuel

    // =========================
    // DATABASE PERSISTENCE (delegated to ReactorPersistence)
    // =========================

    public static void saveAll() {
        ReactorManager r = instance;
        if (r == null) return;
        ReactorState state = ReactorManager.buildState(r);
        ReactorPersistence.saveAll(state);
    }

    public void saveToDb() {
        ReactorState state = ReactorManager.buildState(this);
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
            // Apply loaded state to instance fields
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
    public Location getReactorLocation() {
        return reactorLocation;
    }

    public boolean isValid() {
        return valid && reactorLocation != null;
    }

    public String getReactorId() {
        return reactorId;
    }

    // =========================
    // SET REACTOR LOCATION
    // =========================
    public void setReactorLocation(Location loc) {
        if (loc != null) {
            Location normalized = LocationUtil.normalize(loc);
            this.reactorLocation = normalized;
            this.valid = true;
            // Generate unique ID from structure coordinates
            this.reactorId = "REACTOR-" + normalized.getBlockX()
                    + "-" + normalized.getBlockY()
                    + "-" + normalized.getBlockZ();
            // Save to DB
            saveToDb();
        } else {
            // Delete old reactor from DB if exists
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
        // Skip item frame check — frame is only needed for initial assembly
        valid = ReactorStructure.isValid(reactorLocation, false);

        if (!valid && wasValid) {
            setReactorLocation(null);
        }
    }

    // =========================
    // MAIN TICK (every server tick)
    // =========================
    public void tick() {

        if (!enabled || !valid || reactorLocation == null) {
            return;
        }

        // =========================
        // READ BLOCK STATES
        // (Check redstone power on bulbs, levers are optional)
        // =========================
        Location base = reactorLocation;

        boolean heating = isBulbPowered(base, -1, 0, -2);
        boolean cooling = isBulbPowered(base, 1, 0, -2);

        // =========================
        // BROADCAST STATE CHANGES (single messages)
        // =========================
        if (heating != prevHeating) {
            broadcast(heating
                    ? "§6🔥 §eНагрев включён"
                    : "§7🔥 §fНагрев выключен");
            prevHeating = heating;
        }
        if (cooling != prevCooling) {
            broadcast(cooling
                    ? "§b❄ §3Охлаждение включено"
                    : "§7❄ §fОхлаждение выключено");
            prevCooling = cooling;
        }

        // =========================
        // TEMPERATURE CONTROL (only with fuel)
        // =========================
        if (heating && coreTemp < coreTempMax) {
            if (hasBarrelFuel()) {
                coreTemp += heatRate;
                noFuelWarnTick = 0;
            } else {
                noFuelWarnTick++;
                if (noFuelWarnTick == 1) {
                    broadcast("§e⚠ §7Нет топлива! В левую бочку поместите алмазные блоки, в правую — золотые блоки.");
                }
            }
        }
        if (cooling && coreTemp > coreTempCoolMin) {
            coreTemp -= coolRate;
        }

        // =========================
        // CASE REACTION (only with fuel — без топлива корпус не греется)
        // =========================
        if (heating && hasBarrelFuel()) {
            coreCaseTemp = Math.min(coreCaseTemp + caseTempHeatRate, caseTempMax);
            coreCasePress = Math.min(coreCasePress + casePressHeatRate, casePressMax);
        }
        if (cooling) {
            coreCaseTemp = Math.max(coreCaseTemp - caseTempCoolRate, caseTempCoolMin);
        }

        // =========================
        // FLASH TICK FOR SIGNS
        // =========================
        displayTick++;

        // =========================
        // INTEGRITY INDICATOR BULBS
        // (opposite heat/cool bulbs at Z=+2)
        // =========================
        setBulbLit(base, -1, 0, 2, coreShInt < 100);
        setBulbLit(base, 1, 0, 2, coreCaseInt < 100);

        // =========================
        // INTEGRITY WARNING (every 10 seconds)
        // =========================
        integrityWarnTick++;
        if (integrityWarnTick >= 200) {
            integrityWarnTick = 0;
            if (coreShInt < 100) {
                broadcast("§4⚠ §cЦелостность оболочки ядра нарушена!");
            }
            if (coreCaseInt < 100) {
                broadcast("§4⚠ §cЦелостность корпуса реактора нарушена!");
            }
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
        // Игроки внутри камеры (5×6×6) получают радиацию от активного ядра.
        // Камера: X -2..2, Y -5..0, Z -2..2 относительно центра реактора.
        // =========================
        if (coreTemp >= 1000) {
            int radiationAmount = Math.min(coreTemp / 500, 10);
            int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().equals(base.getWorld())) continue;
                Location ploc = player.getLocation();
                int px = ploc.getBlockX(), py = ploc.getBlockY(), pz = ploc.getBlockZ();
                if (px >= bx - 2 && px <= bx + 2
                        && py >= by - 5 && py <= by
                        && pz >= bz - 2 && pz <= bz + 2) {
                    RadiationManager.addRadiation(player, radiationAmount);
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
        // Генерация только при coreTemp > 1000.
        // Выработка = 90% от текущей температуры ядра за тик.
        // Энергия распределяется по кабелям в радиусе структуры (±3 X/Z, ±5 Y).
        // =========================
        if (coreTemp > 1000) {
            double energyPerTick = (double) coreTemp * 0.9;
            energyRemainder += energyPerTick;
            int toGenerate = (int) energyRemainder;
            if (toGenerate > 0) {
                energyRemainder -= toGenerate;
                energyGenerated += toGenerate;

                // Распределение энергии по кабелям рядом с реактором
                java.util.List<CableNode> nearbyCables = new java.util.ArrayList<>();
                for (CableNode node : CableNetwork.getAllNodes()) {
                    Location nLoc = node.getLocation();
                    if (!nLoc.getWorld().equals(base.getWorld())) continue;
                    int dx = Math.abs(nLoc.getBlockX() - base.getBlockX());
                    int dy = Math.abs(nLoc.getBlockY() - base.getBlockY());
                    int dz = Math.abs(nLoc.getBlockZ() - base.getBlockZ());
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
                    // Остаток энергии → GENERATOR-буфер
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
                    // Нет кабелей рядом → вся энергия в GENERATOR-буфер
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
        // MELTDOWN COUNTDOWN START (когда integrity достигает 0%)
        // =========================
        if ((coreShInt <= 0 || coreCaseInt <= 0) && !meltdownCountdown && !selfDestructActive) {
            meltdownCountdown = true;
            meltdownTimer = 200; // 10 секунд
            selfDestruct = true;
            broadcast("§4☠ §cЦелостность разрушена! §f10§c секунд до детонации...");
        }
    }

    // =========================
    // PRESSURE TICK (every 5s)
    // =========================
    public void tickPressure() {

        if (!enabled || !valid || reactorLocation == null) {
            return;
        }

        Location base = reactorLocation;
        Location coreCenter = base.clone().add(0.5, -2.5, 0.5);

        // =========================
        // PRESSURE PARTICLES & RADIATION
        // =========================
        int particleCount;
        int radAmount;

        if (corePress >= 500000) {
            particleCount = 512;
            radAmount = 600;
        } else if (corePress >= 400000) {
            particleCount = 256;
            radAmount = 500;
        } else if (corePress >= 300000) {
            particleCount = 128;
            radAmount = 400;
        } else if (corePress >= 200000) {
            particleCount = 64;
            radAmount = 300;
        } else if (corePress >= 100000) {
            particleCount = 32;
            radAmount = 200;
        } else {
            particleCount = 0;
            radAmount = 0;
        }

        if (particleCount > 0) {
            // Smoke 2.5 blocks above core center (Y = -2.5 + 2.5 = 0)
            Location smokePos = coreCenter.clone().add(0, 2.5, 0);
            base.getWorld().spawnParticle(
                    Particle.CAMPFIRE_SIGNAL_SMOKE,
                    smokePos, particleCount, 0, 0, 0, 0.1
            );

            // Радиация от давления через RadiationManager
            if (radAmount > 0) {
                RadiationManager.addRadiationNear(base, 4.0, radAmount);
            }
        }

        // =========================
        // PRESSURE DIVISION
        // =========================
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

        // Shell integrity decay at high temp
        if (coreTemp >= shIntDecayTempThreshold && coreShInt > 0) {
            coreShInt = Math.max(0, coreShInt - shellIntDecayRate);
        }

        // Case integrity decay at high pressure
        if (coreCasePress >= caseIntDecayPressThreshold && coreCaseInt > 0) {
            coreCaseInt = Math.max(0, coreCaseInt - caseIntDecayPressRate);
        }

        // Case integrity decay at high temp
        if (coreCaseTemp >= caseIntDecayTempThreshold && coreCaseInt > 0) {
            coreCaseInt = Math.max(0, coreCaseInt - caseIntDecayTempRate);
        }
    }

    // =========================
    // INTENSITY RECOVERY TICK (every 3s)
    // =========================
    public void tickIntensityUp() {

        if (!enabled || !valid) return;

        // Shell integrity recovery
        if (coreTemp <= shellIntRecoveryTempMax && coreShInt < 100) {
            coreShInt = Math.min(100, coreShInt + shellIntRecoveryRate);
        }

        // Case integrity recovery
        if (coreCasePress <= caseIntRecoveryPressMax && coreCaseTemp <= caseIntRecoveryTempMax && coreCaseInt < 100) {
            coreCaseInt = Math.min(100, coreCaseInt + caseIntRecoveryRate);
        }
    }

    // =========================
    // RECIPE TICK (every 5s)
    // =========================
    public void tickRecipe() {

        if (!enabled || !valid) return;

        // Recipe decreases when cold
        if (coreTemp < 1000 && recipeTime > 0) {
            recipeTime--;
        }

        // Recipe progresses when hot
        if (coreTemp >= 1000 && coreTemp <= 5000 && recipeTime < recipeTimeMax) {
            recipeTime++;
        }

        // Recipe completion (only if there's fuel)
        if (recipeTime >= recipeTimeMax) {
            if (hasBarrelFuel()) {
                rcDone = true;
            } else {
                // Без топлива — ждём. Сообщение уже было показано в tick() при попытке нагрева.
                // Не сбрасываем recipeTime — рецепт "заморожен" до добавления топлива
            }
        }
    }

    // =========================
    // START SELF-DESTRUCT (вызывается при wear >= 100%)
    // =========================
    private void startSelfDestruct() {

        selfDestruct = true;
        selfDestructActive = true;
        selfDestructChatTimer = wearChatCountdown; // в секундах
        finalMeltdownActive = false;

        broadcast("§4☠ §cКритический износ реактора! §f" + wearChatCountdown + "§c сек до детонации...");
        broadcast("§4☠ §cПротокол самоуничтожения инициирован.");
    }

    // =========================
    // WEAR TICK (каждую секунду)
    // =========================
    public void tickWear() {

        if (!enabled || !valid || reactorLocation == null) return;

        // =========================
        // НАКОПЛЕНИЕ/УМЕНЬШЕНИЕ ИЗНОСА
        // =========================
        if (wearEnabled && !selfDestructActive) {

            // Сброс счётчика при переключении режима (норма ↔ деградация)
            boolean isDegraded = coreShInt < 100 || coreCaseInt < 100;
            if (isDegraded != prevWearDegraded) {
                wearTickCounter = 0;
                prevWearDegraded = isDegraded;
            }

            wearTickCounter++;

            if (isDegraded) {
                // Целостность нарушена → износ уменьшается на 1% за интервал
                if (wearTickCounter >= wearIntervalDegradation) {
                    wearTickCounter = 0;
                    if (reactorWear > 0) {
                        reactorWear--;
                    }
                }
            } else {
                // Целостность в норме → износ накапливается
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

        // =========================
        // ЧАТ-ТАЙМЕР САМОУНИЧТОЖЕНИЯ (в секундах)
        // =========================
        if (selfDestructActive && !meltdownCountdown) {

            selfDestructChatTimer--;

            // Сначала проверяем, не пора ли начать финальный взрыв
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
    // MELTDOWN COUNTDOWN TICK (финальный отсчёт 10 сек)
    // =========================
    public void tickMeltdownCountdown() {

        if (!meltdownCountdown || !enabled || !valid || reactorLocation == null) return;

        meltdownTimer--;

        // Broadcast remaining seconds
        if (meltdownTimer > 0 && meltdownTimer % 20 == 0) {
            int seconds = meltdownTimer / 20;
            broadcast("§4☠ §cВзрыв неизбежен! §f" + seconds + "§c сек...");
        }

        // Trigger explosion at 0
        if (meltdownTimer <= 0) {
            meltdownCountdown = false;
            finalMeltdownActive = false;
            selfDestructActive = false;
            meltdown();
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
    // SOUND TICK (every 10 ticks)
    // =========================
    public void tickSound() {

        if (!enabled || !valid || reactorLocation == null) return;

        Location base = reactorLocation;

        // Beep when any integrity is damaged
        if (coreShInt < 100 || coreCaseInt < 100) {
            base.getWorld().playSound(
                    base, Sound.BLOCK_NOTE_BLOCK_PLING,
                    SoundCategory.MASTER, 1.0f, 1.5f
            );
        }
    }

    // =========================
    // SMOOTH DISPLAY TICK (every tick)
    // Interpolates display values toward actual values for smooth sign updates.
    // =========================
    public void tickSmoothDisplay() {
        displayCoreTemp += (coreTemp - displayCoreTemp) * SMOOTHING_FACTOR;
        displayCorePress += (corePress - displayCorePress) * SMOOTHING_FACTOR;
        displayCoreShInt += (coreShInt - displayCoreShInt) * SMOOTHING_FACTOR;
        displayCoreCaseTemp += (coreCaseTemp - displayCoreCaseTemp) * SMOOTHING_FACTOR;
        displayCoreCasePress += (coreCasePress - displayCoreCasePress) * SMOOTHING_FACTOR;
        displayCoreCaseInt += (coreCaseInt - displayCoreCaseInt) * SMOOTHING_FACTOR;
        displayRecipeTime += (recipeTime - displayRecipeTime) * SMOOTHING_FACTOR;

        displayReactorWear += (reactorWear - displayReactorWear) * SMOOTHING_FACTOR;

        double rawEnergyRate = coreTemp > 1000 ? (double) coreTemp * 0.9 * 20.0 : 0.0;
        displayEnergyRate += (rawEnergyRate - displayEnergyRate) * SMOOTHING_FACTOR;
    }

    // =========================
    // VISUAL TICK (every tick — particles)
    // Core chamber center: Y=-3 (midpoint between upper WAXED_CHISELED_COPPER at Y=-1
    // and lower WAXED_CHISELED_COPPER at Y=-5), X=0, Z=0.
    // =========================
    public void tickVisual() {

        if (!enabled || !valid || reactorLocation == null) return;

        Location base = reactorLocation;
        // Core center: (0.5, -2.5, 0.5) — midpoint between upper core (Y=-1) and lower core (Y=-5)
        Location coreCenter = base.clone().add(0.5, -2.5, 0.5);

        // =========================
        // CORE TEMPERATURE PARTICLES
        // =========================
        Particle.DustOptions color;

        if (coreTemp <= 999) {
            color = new Particle.DustOptions(Color.fromRGB(128, 128, 128), 1.25f);
        } else if (coreTemp <= 1499) {
            color = new Particle.DustOptions(Color.fromRGB(128, 0, 0), 1.25f);
        } else if (coreTemp <= 1999) {
            color = new Particle.DustOptions(Color.RED, 1.25f);
        } else if (coreTemp <= 2999) {
            color = new Particle.DustOptions(Color.ORANGE, 1.25f);
        } else if (coreTemp <= 3999) {
            color = new Particle.DustOptions(Color.YELLOW, 1.25f);
        } else {
            color = new Particle.DustOptions(Color.WHITE, 1.25f);
        }

        base.getWorld().spawnParticle(
                Particle.DUST, coreCenter, 16, 0, 0, 0, 0, color
        );

        // =========================
        // HIGH TEMP EFFECTS
        // =========================
        if (coreTemp >= 1000 && coreTemp <= 4999) {
            Location diamondLoc = base.clone().add(0, -2, -2);
            if (diamondLoc.getBlock().getType() == Material.DIAMOND_BLOCK) {
                base.getWorld().spawnParticle(
                        Particle.END_ROD,
                        coreCenter.clone().add(0, 0, 1),
                        1, 0, 0, -1.5, 0.1
                );
                base.getWorld().spawnParticle(
                        Particle.LAVA,
                        coreCenter.clone().add(0, 0, -0.4),
                        1, 0, 0, 0, 0
                );
                base.getWorld().spawnParticle(
                        Particle.SCRAPE,
                        coreCenter.clone().add(0, 0, 1),
                        1, 0, 0, 2, 1
                );
                base.getWorld().spawnParticle(
                        Particle.COPPER_FIRE_FLAME,
                        coreCenter.clone().add(0, 0, 2.4),
                        1, 0, 0, 0, 0.01f
                );
            }
        }

        // =========================
        // BEACON HUM SOUND AT HIGH TEMP
        // =========================
        if (coreTemp >= 1000 && !meltdownCountdown) {
            base.getWorld().playSound(
                    coreCenter,
                    Sound.BLOCK_BEACON_POWER_SELECT,
                    SoundCategory.MASTER, 0.5f, 1
            );
        }

        // =========================
        // MELTDOWN COUNTDOWN EFFECTS
        // =========================
        if (meltdownCountdown) {
            float progress = 1.0f - (meltdownTimer / 200.0f);
            if (progress < 0) progress = 0;
            if (progress > 1) progress = 1;

            int smokeCount = 8 + (int)(progress * 56);     // 8 → 64
            int fireCount  = 2 + (int)(progress * 14);     // 2 → 16

            // Escalating smoke (at normal smoke position, 2.5 blocks above core)
            Location smokePos = coreCenter.clone().add(0, 2.5, 0);
            base.getWorld().spawnParticle(
                    Particle.CAMPFIRE_SIGNAL_SMOKE,
                    smokePos, smokeCount, 0.5, 0.5, 0.5, 0.15
            );

            // Escalating fire
            base.getWorld().spawnParticle(
                    Particle.LAVA, coreCenter, fireCount, 0.3, 0.3, 0.3, 0
            );
            base.getWorld().spawnParticle(
                    Particle.FLAME, coreCenter, fireCount, 0.3, 0.3, 0.3, 0.05
            );

            // Escalating hum
            float volume = 0.5f + progress * 4.0f;
            float pitch  = 0.5f + progress * 0.8f;
            base.getWorld().playSound(
                    coreCenter,
                    Sound.BLOCK_BEACON_AMBIENT,
                    SoundCategory.MASTER, volume, pitch
            );

            // =========================
            // SPARK PARTICLES AROUND CORE
            // (within 1 block radius of core center, escalating count)
            // =========================
            int sparkCount = 2 + (int)(progress * 8);  // 2 → 10
            base.getWorld().spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    coreCenter, sparkCount, 1.0, 1.0, 1.0, 0
            );
        }
    }

    // =========================
    // UPDATE DISPLAYS (sensors)
    // =========================
    public void updateDisplays() {

        if (!enabled || !valid || reactorLocation == null) return;

        Location base = reactorLocation;

        // =========================
        // САМОУНИЧТОЖЕНИЕ: все строки пустые, на 2-й строке "Нет сигнала"
        // =========================
        if (selfDestructActive || meltdownCountdown) {
            String blank = " ";
            String noSignal = "§cНЕТ СИГНАЛА";
            String meltdownLine = meltdownCountdown
                    ? "§cВзрыв неизбежен!"
                    : noSignal;

            setSignText(base, 0, -4, -3, 0, blank);
            setSignText(base, 0, -4, -3, 1, meltdownLine);
            setSignText(base, 0, -4, -3, 2, blank);
            setSignText(base, 0, -4, -3, 3, blank);

            setSignText(base, -1, -4, -3, 0, blank);
            setSignText(base, -1, -4, -3, 1, meltdownLine);
            setSignText(base, -1, -4, -3, 2, blank);
            setSignText(base, -1, -4, -3, 3, blank);

            setSignText(base, 1, -4, -3, 0, blank);
            setSignText(base, 1, -4, -3, 1, meltdownLine);
            setSignText(base, 1, -4, -3, 2, blank);
            setSignText(base, 1, -4, -3, 3, blank);
            return;
        }

        int displayCoreTempInt = (int) Math.round(displayCoreTemp);
        int displayCorePressInt = (int) Math.round(displayCorePress);
        int displayCoreShIntInt = (int) Math.round(displayCoreShInt);
        int displayCoreCaseTempInt = (int) Math.round(displayCoreCaseTemp);
        int displayCoreCasePressInt = (int) Math.round(displayCoreCasePress);
        int displayCoreCaseIntInt = (int) Math.round(displayCoreCaseInt);
        int displayRecipeTimeInt = (int) Math.round(displayRecipeTime);

        // Flash all text red-white when any integrity is below 100%
        boolean flashing = displayCoreShIntInt < 100 || displayCoreCaseIntInt < 100;
        String color = (flashing && (displayTick % 10 < 5)) ? "§c" : "§f";

        // =========================
        // CENTER SIGN — CORE DATA
        // Wall signs are on the SOUTH face at Y=-5 (rel Z=-3, Y=-5)
        // =========================
        setSignText(base, 0, -4, -3, 0, color + "Данные ядра");
        setSignText(base, 0, -4, -3, 1, color + "T: " + displayCoreTempInt + " C*");
        setSignText(base, 0, -4, -3, 2, color + "P: " + displayCorePressInt + " kPa");
        setSignText(base, 0, -4, -3, 3, color + "I: " + displayCoreShIntInt + " %");

        // =========================
        // LEFT SIGN — CASE DATA
        // =========================
        setSignText(base, -1, -4, -3, 0, color + "Данные корпуса");
        setSignText(base, -1, -4, -3, 1, color + "T: " + displayCoreCaseTempInt + " C*");
        setSignText(base, -1, -4, -3, 2, color + "P: " + displayCoreCasePressInt + " kPa");
        setSignText(base, -1, -4, -3, 3, color + "I: " + displayCoreCaseIntInt + " %");

        // =========================
        // RIGHT SIGN — RECIPE DATA
        // =========================
        setSignText(base, 1, -4, -3, 0, color + "Данные рецепта");
        setSignText(base, 1, -4, -3, 1, color + "P: " + displayRecipeTimeInt + " %");

        if (displayRecipeTimeInt <= 0) {
            setSignText(base, 1, -4, -3, 2, color + "S: Бездействует");
        } else if (displayRecipeTimeInt < recipeTimeMax) {
            setSignText(base, 1, -4, -3, 2, color + "S: Готовится");
        } else {
            setSignText(base, 1, -4, -3, 2, color + "S: Завершён");
        }

        // Износ реактора
        int displayWear = (int) Math.round(displayReactorWear);
        setSignText(base, 1, -4, -3, 3, color + "W: " + displayWear + " %");
    }

    // =========================
    // RECIPE COMPLETION
    // =========================
    private void completeRecipe() {

        if (reactorLocation == null) return;

        Location base = reactorLocation;

        // =========================
        // ПОТРЕБЛЕНИЕ ТОПЛИВА ИЗ БОЧЕК
        // (tickRecipe() уже проверил, что топливо есть)
        // =========================
        consumeBarrelFuel(base, 0, -3, -2, Material.DIAMOND_BLOCK);
        consumeBarrelFuel(base, 0, -3, 2, Material.GOLD_BLOCK);

        // =========================
        // ВЫБРОС РЕЗУЛЬТАТА В ЦЕНТРЕ ЯДРА
        // Древний обломок выпадает как предмет в центре ядра (0.5, -3.5, 0.5)
        // =========================
        Location dropLoc = base.clone().add(0.5, -2.5, 0.5);
        dropLoc.getWorld().dropItemNaturally(dropLoc, new ItemStack(Material.ANCIENT_DEBRIS, 1));

        // =========================
        // ИМПУЛЬС ОГНЯ ИЗ ЯДРА
        // Вспышка пламени, лавы и искр во все стороны
        // =========================
        World world = dropLoc.getWorld();
        world.spawnParticle(Particle.FLAME, dropLoc, 120, 2.5, 2.5, 2.5, 0.15);
        world.spawnParticle(Particle.LAVA, dropLoc, 40, 1.5, 1.5, 1.5, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, dropLoc, 60, 2.0, 2.0, 2.0, 0.1);
        world.spawnParticle(Particle.ASH, dropLoc, 80, 3.0, 3.0, 3.0, 0.05);
        world.spawnParticle(Particle.SMALL_FLAME, dropLoc, 50, 1.8, 1.8, 1.8, 0.08);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, dropLoc, 40, 1.0, 1.0, 1.0, 0.02);

        // Звуковой эффект вспышки
        world.playSound(dropLoc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 2.0f, 0.5f);
        world.playSound(dropLoc, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.MASTER, 1.5f, 0.8f);

        // =========================
        // СБРОС ПАРАМЕТРОВ (реактор остаётся активным)
        // =========================
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
        displayReactorWear = 0;
        displayEnergyRate = 0;
        noFuelWarnTick = 0;

        saveToDb();

        broadcast("§4☢ §cРецепт слияния готов! Древний обломок выброшен в центре реактора.");
    }

    // =========================
    // GET WEAR
    // =========================
    public int getReactorWear() { return reactorWear; }

    // =========================
    // MELTDOWN
    // =========================
    private void meltdown() {

        // Reset energy generation on meltdown
        energyRemainder = 0;
        energyGenerated = 0;
        displayReactorWear = 0;
        displayEnergyRate = 0;

        if (reactorLocation == null) return;

        Location base = reactorLocation;
        Location coreCenter = base.clone().add(0, -2, 0);

        // Радиация от взрыва через RadiationManager
        RadiationManager.addRadiationNear(coreCenter, 1.0, 6400);
        RadiationManager.addRadiationNear(coreCenter, 20.0, 3200);

        // Destroy core blocks
        base.clone().add(0, -1, 0).getBlock().setType(Material.AIR);
        base.clone().add(0, -5, 0).getBlock().setType(Material.AIR);

        // Explosion (charged creeper)
        coreCenter.getWorld().spawn(coreCenter, org.bukkit.entity.Creeper.class, creeper -> {
            creeper.setPowered(true);
            creeper.setExplosionRadius(meltdownExplosionRadius);
            creeper.setMaxFuseTicks(0);
            creeper.setIgnited(true);
        });

        // =========================
        // МОЛНИЯ В ЦЕНТР ЯДРА
        // Бьёт прямо в то место, где было ядро реактора
        // =========================
        base.getWorld().strikeLightning(coreCenter);

        // Дополнительные искры от молнии
        base.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                coreCenter, 100, 3.0, 3.0, 3.0, 0.5
        );

        // Smoke particles
        base.getWorld().spawnParticle(
                Particle.CAMPFIRE_SIGNAL_SMOKE,
                coreCenter, 64, 0, 0, 0, 0.1
        );

        broadcast("§4☠ §cРасплавление! Ядро реактора разрушено!");

        // Reset everything — clear location to allow building a new reactor
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

        // Reset display values to match
        displayCoreTemp = 0;
        displayCorePress = 0;
        displayCoreShInt = 100;
        displayCoreCaseTemp = 0;
        displayCoreCasePress = 0;
        displayCoreCaseInt = 100;
        displayRecipeTime = 0;
        displayReactorWear = 0;
        displayEnergyRate = 0;
        displayTick = 0;
        prevHeating = false;
        prevCooling = false;
        integrityWarnTick = 0;
        meltdownCountdown = false;
        meltdownTimer = 0;
        prevShInt = 100;
        prevCaseInt = 100;
    }

    // =========================
    // BARREL FUEL HELPERS
    // =========================

    /**
     * Проверяет, есть ли в бочке достаточно топлива.
     */
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

    /**
     * Проверяет наличие топлива в обеих бочках.
     */
    private boolean hasBarrelFuel() {
        if (reactorLocation == null) return false;
        Location base = reactorLocation;
        // Проверяем алмазы в левой бочке (0, -4, -2) и золото в правой бочке (0, -4, 2)
        return checkBarrelForFuel(base, 0, -3, -2, Material.DIAMOND_BLOCK, 1)
            && checkBarrelForFuel(base, 0, -3, 2, Material.GOLD_BLOCK, 1);
    }

    /**
     * Забирает 1 единицу топлива из бочки.
     * Использует block.getState(false) (Paper API) — не снимок, а живой tile entity.
     * Изменения применяются сразу, update() не нужен.
     */
    private boolean consumeBarrelFuel(Location base, int dx, int dy, int dz, Material fuelType) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        if (block.getType() != Material.BARREL) return false;

        // Paper API: getState(false) = не снимок, изменения сразу в tile entity
        Barrel barrel = (Barrel) block.getState(false);
        Inventory inv = barrel.getInventory();

        // Ищем первый стак с нужным материалом
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == fuelType) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    inv.setItem(i, item);
                } else {
                    inv.setItem(i, null);
                }
                // Живой tile entity — изменения уже применены, update() не нужен
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

    // Smoothed display values for command output
    public int getDisplayCoreTemp() { return (int) Math.round(displayCoreTemp); }
    public int getDisplayCorePress() { return (int) Math.round(displayCorePress); }
    public int getDisplayCoreShInt() { return (int) Math.round(displayCoreShInt); }
    public int getDisplayCoreCaseTemp() { return (int) Math.round(displayCoreCaseTemp); }
    public int getDisplayCoreCasePress() { return (int) Math.round(displayCoreCasePress); }
    public int getDisplayCoreCaseInt() { return (int) Math.round(displayCoreCaseInt); }
    public int getDisplayRecipeTime() { return (int) Math.round(displayRecipeTime); }
    public int getDisplayReactorWear() { return (int) Math.round(displayReactorWear); }
    public long getEnergyGenerated() { return energyGenerated; }
    public int getDisplayEnergyRate() { return (int) Math.round(displayEnergyRate); }

    // =========================
    // HELPER: IS BULB POWERED
    // =========================
    private boolean isBulbPowered(Location base, int dx, int dy, int dz) {

        Block block = base.clone().add(dx, dy, dz).getBlock();

        if (block.getType() != Material.WAXED_COPPER_BULB) {
            return false;
        }

        org.bukkit.block.data.type.CopperBulb bulbData =
                (org.bukkit.block.data.type.CopperBulb) block.getBlockData();

        return bulbData.isPowered();
    }

    // =========================
    // HELPER: SET BULB LIT
    // =========================
    private void setBulbLit(Location base, int dx, int dy, int dz, boolean lit) {

        Block block = base.clone().add(dx, dy, dz).getBlock();

        if (block.getType() != Material.WAXED_COPPER_BULB) {
            return;
        }

        org.bukkit.block.data.type.CopperBulb bulbData =
                (org.bukkit.block.data.type.CopperBulb) block.getBlockData();

        if (bulbData.isLit() != lit) {
            bulbData.setLit(lit);
            block.setBlockData(bulbData);
        }
    }


    // =========================
    // HELPER: SET SIGN TEXT
    // =========================
    private void setSignText(
            Location base,
            int dx, int dy, int dz,
            int line, String text
    ) {
        Block block = base.clone().add(dx, dy, dz).getBlock();
        var state = block.getState();

        if (state instanceof org.bukkit.block.Sign signState) {
            signState.setLine(line, text);
            signState.update(true, false);
        }
    }

    // =========================
    // HELPER: BROADCAST
    // =========================
    private void broadcast(String message) {

        String prefix = "§4Р.Т.С §8» §f";

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (reactorLocation != null
                    && player.getWorld().equals(reactorLocation.getWorld())
                    && player.getLocation().distanceSquared(reactorLocation) <= 225) {
                player.sendMessage(prefix + message);
            }
        }
    }
}
