package com.mcplugin.mechanics.features.items;

import com.mcplugin.core.Main;
import com.mcplugin.util.ConsoleLogger;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Фича: Прогрессивное ломание неразрушимых блоков с удержанием кнопки.
 *
 * Позволяет ломать блоки, которые в ванилле нельзя сломать (бедрок, барьер и т.д.),
 * через накопление "урона" инструментом с анимацией трещин.
 *
 * Каждый блок настраивается отдельно в config.yml → features.unbreakable_breaker.blocks.
 *
 * Как работает:
 * 1. PlayerInteractEvent(LEFT_CLICK_BLOCK) — создаёт сессию ломания,
 *    наносит первый урон (мгновенная обратная связь).
 * 2. BukkitRunnable (каждые hitIntervalTicks) — пока игрок держит ЛКМ и
 *    смотрит на тот же блок — продолжает наносить урон.
 * 3. Если игрок отвёл взгляд, отпустил ЛКМ, сменил инструмент или вышел —
 *    сессия удаляется.
 */
public class UnbreakableBreakerManager extends BukkitRunnable implements Listener {

    private static UnbreakableBreakerManager instance;

    // ========== НАСТРОЙКИ БЛОКА (из конфига) ==========

    /**
     * Параметры для каждого настраиваемого блока.
     * Загружаются из features.unbreakable_breaker.blocks.<MATERIAL>
     */
    private record BlockConfig(
            boolean enabled,
            double maxDamage,
            String minToolTier,
            boolean requireHaste,
            boolean dropBlock,
            boolean breakNaturally,
            boolean playEffects,
            Map<String, Double> toolDamage,  // "NETHERITE_PICKAXE" → damage
            double defaultDamage,
            double efficiencyMultiplier,
            double hasteMultiplier
    ) {}

    // Загруженные конфиги блоков: Material → BlockConfig
    private Map<Material, BlockConfig> blockConfigs = new HashMap<>();

    // ========== ОБЩИЕ НАСТРОЙКИ ==========
    private static boolean featureEnabled = true;
    private static int hitIntervalTicks = 4;

    // ========== СЕССИЯ ЛОМАНИЯ ==========

    private static class ActiveBreak {
        Location blockLoc;          // нормализованная локация блока
        double currentDamage;       // накопленный урон
        BlockConfig config;         // конфиг конкретного блока

        int lastDamageTick;  // tick последнего нанесения урона

        ActiveBreak(Location blockLoc, BlockConfig config) {
            this.blockLoc = blockLoc;
            this.config = config;
            this.currentDamage = 0.0;
            this.lastDamageTick = Bukkit.getCurrentTick();
        }
    }

    // UUID игрока → активная сессия
    private final Map<UUID, ActiveBreak> activeBreaks = new HashMap<>();

    // Обратная карта: Location → UUID для O(1) доступа в getProgress()
    private final Map<Location, UUID> locationToPlayer = new HashMap<>();

    // UUID игрока → tick последнего взаимодействия с блоком (для детекта отпускания ЛКМ)
    // Обновляется в onBlockInteract при каждом клике. Если прошло > 30 тиков без клика — сессия сбрасывается.
    private final Map<UUID, Long> lastInteractTick = new HashMap<>();

    // ========== ЖИЗНЕННЫЙ ЦИКЛ ==========

    public static void init(Main plugin) {
        instance = new UnbreakableBreakerManager();
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        instance.runTaskTimer(plugin, 20L, 1L); // каждый тик — для плавной анимации трещин
    }

    public static void reloadConfig() {
        if (instance == null) instance = new UnbreakableBreakerManager();
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.unbreakable_breaker");
        if (cfg == null) {
            featureEnabled = false;
            return;
        }

        featureEnabled    = cfg.getBoolean("enabled", true);
        hitIntervalTicks  = cfg.getInt("hit_interval_ticks", 4);

        // Загружаем конфиги блоков
        Map<Material, BlockConfig> newConfigs = new HashMap<>();
        ConfigurationSection blocksSection = cfg.getConfigurationSection("blocks");
        if (blocksSection != null) {
            for (String key : blocksSection.getKeys(false)) {
                ConfigurationSection bc = blocksSection.getConfigurationSection(key);
                if (bc == null) continue;

                Material material;
                try {
                    material = Material.valueOf(key.toUpperCase());
                } catch (IllegalArgumentException e) {
                    ConsoleLogger.warn("[UnbreakableBreaker] Unknown material: " + key);
                    continue;
                }

                // Загружаем урон по инструментам
                Map<String, Double> toolDmg = new HashMap<>();
                ConfigurationSection dmgSec = bc.getConfigurationSection("damage");
                if (dmgSec != null) {
                    for (String toolKey : dmgSec.getKeys(false)) {
                        toolDmg.put(toolKey.toUpperCase(), dmgSec.getDouble(toolKey, 0.0));
                    }
                }

                BlockConfig config = new BlockConfig(
                        bc.getBoolean("enabled", true),
                        bc.getDouble("max_damage", 80.0),
                        bc.getString("min_tool_tier", "DIAMOND").toUpperCase(),
                        bc.getBoolean("require_haste", false),
                        bc.getBoolean("drop_block", true),
                        bc.getBoolean("break_naturally", true),
                        bc.getBoolean("play_effects", true),
                        Collections.unmodifiableMap(toolDmg),
                        bc.getDouble("damage.default", 0.3),
                        bc.getDouble("efficiency_multiplier", 0.5),
                        bc.getDouble("haste_multiplier", 0.3)
                );

                newConfigs.put(material, config);
            }
        }

        instance.blockConfigs = newConfigs;

        ConsoleLogger.info("[UnbreakableBreaker] Loaded " + newConfigs.size() + " breakable block(s)");
    }

    // ========== ДЕТЕКТ УДЕРЖАНИЯ ЛКМ ЧЕРЕЗ PAPI ==========
    //
    // BlockBreakProgressUpdateEvent (Paper 1.21+) срабатывает каждый раз, когда
    // клиент присылает обновление прогресса ломания блока. Это происходит непрерывно
    // (~5 раз/сек) пока игрок держит ЛКМ на блоке.
    //
    // Используем его чтобы:
    // 1. Обновить lastInteractTick — чтобы сессия не сбрасывалась при удержании
    // 2. Отменить широковещательную рассылку анимации другим игрокам
    //
    // Progress = -1.0f означает, что игрок отпустил ЛКМ → не обновляем lastInteractTick

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreakProgress(BlockBreakProgressUpdateEvent e) {
        if (!featureEnabled) return;

        Entity entity = e.getEntity();
        if (!(entity instanceof Player player)) return;
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) return;

        Block block = e.getBlock();
        BlockConfig config = blockConfigs.get(block.getType());
        if (config == null || !config.enabled()) return;

        // -1.0 = игрок отпустил ЛКМ → чистим сессию
        if (e.getProgress() < 0) {
            cleanup(player.getUniqueId());
            return;
        }

        // Обновляем тик последнего взаимодействия — сессия не умрёт от таймаута
        // пока игрок держит ЛКМ (даже если сервер сбрасывает прогресс через ~50%)
        lastInteractTick.put(player.getUniqueId(), (long) Bukkit.getCurrentTick());
    }

    // ========== ПОВТОРНЫЙ УРОН ПРИ УДЕРЖАНИИ (каждый тик) ==========

    @Override
    public void run() {
        if (!featureEnabled) return;

        long currentTick = Bukkit.getCurrentTick();
        long interactTimeout = 30L; // 1.5 секунды без клика = сброс сессии

        var iterator = activeBreaks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID uuid = entry.getKey();
            ActiveBreak brk = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                cleanup(uuid);
                continue;
            }

            // ─── ДЕТЕКТ ОТПУСКАНИЯ ЛКМ (по последнему клику) ───
            Long lastInteract = lastInteractTick.get(uuid);
            if (lastInteract == null || (currentTick - lastInteract) > interactTimeout) {
                // Игрок отпустил ЛКМ — не чистим сессию, а просто не наносим урон.
                // Когда клиент начнёт новый цикл ломания (после сброса сервера на ~50%),
                // onBlockBreakProgress снова начнёт обновлять lastInteractTick,
                // и мы продолжим с того же места, не теряя прогресс.
                sendCrackProgress(player, brk.blockLoc, 0.0f);
                continue;
            }

            // Смотрит ли игрок на тот же блок?
            Block target = player.getTargetBlockExact(5);
            if (target == null || !isBreakable(target.getType())) {
                sendCrackProgress(player, brk.blockLoc, 0.0f);
                cleanup(uuid);
                continue;
            }
            if (!normalizeLoc(target.getLocation()).equals(brk.blockLoc)) {
                sendCrackProgress(player, brk.blockLoc, 0.0f);
                cleanup(uuid);
                continue;
            }

            // Держит ли игрок подходящий инструмент?
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (!isValidTool(tool, brk.config)) {
                sendCrackProgress(player, brk.blockLoc, 0.0f);
                cleanup(uuid);
                continue;
            }

            // ─── НАНОСИМ УРОН (раз в hitIntervalTicks) ───
            if (currentTick - brk.lastDamageTick >= hitIntervalTicks) {
                double increment = getToolDamage(tool, brk.config);
                increment *= getEfficiencyBoost(tool, brk.config);
                increment *= getHasteBoost(player, brk.config);
                brk.currentDamage += increment;
                brk.lastDamageTick = (int) currentTick;

                if (brk.config.playEffects()) {
                    Location center = brk.blockLoc.clone().add(0.5, 0.5, 0.5);
                    float soundProgress = (float) Math.min(1.0, brk.currentDamage / brk.config.maxDamage());
                    target.getWorld().playSound(center, Sound.BLOCK_STONE_HIT, 0.3f,
                            0.5f + soundProgress * 0.5f);
                    target.getWorld().spawnParticle(Particle.CRIT, center, 2, 0.3, 0.3, 0.3, 0.01);
                }

                // ─── БЛОК РАЗРУШЕН ───
                if (brk.currentDamage >= brk.config.maxDamage()) {
                    finishBreaking(player, target, tool, brk.blockLoc, brk.config);
                    cleanup(uuid);
                    continue;
                }
            }

            // ─── ОТПРАВЛЯЕМ ПРОГРЕСС ТРЕЩИН КАЖДЫЙ ТИК ───
            // Это перекрывает возможные сбросы от сервера (из-за setCancelled)
            float progress = (float) Math.min(1.0, brk.currentDamage / brk.config.maxDamage());
            sendCrackProgress(player, brk.blockLoc, progress);
        }
    }

    // ========== ОБРАБОТЧИК ПЕРВОГО КЛИКА ==========

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockInteract(PlayerInteractEvent e) {
        if (!featureEnabled) return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = e.getClickedBlock();
        if (block == null) return;

        // Проверяем, есть ли конфиг для этого блока
        BlockConfig config = blockConfigs.get(block.getType());
        if (config == null || !config.enabled()) return;

        Player player = e.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isValidTool(tool, config)) {
            player.sendActionBar("§c❌ Неподходящий инструмент для этого блока!");
            e.setCancelled(true);
            return;
        }

        if (config.requireHaste() && getHasteLevel(player) <= 0) {
            player.sendActionBar("§c❌ Нужна спешка (Haste) чтобы ломать этот блок!");
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        Location loc = normalizeLoc(block.getLocation());

        lastInteractTick.put(player.getUniqueId(), (long) Bukkit.getCurrentTick());

        // Обновляем обратную карту (старая запись будет перезаписана)
        locationToPlayer.put(loc, player.getUniqueId());

        // Создаём или обновляем сессию
        ActiveBreak brk = activeBreaks.computeIfAbsent(
                player.getUniqueId(), k -> new ActiveBreak(loc, config));
        if (!brk.blockLoc.equals(loc)) {
            sendCrackProgress(player, brk.blockLoc, 0.0f);
            locationToPlayer.remove(brk.blockLoc); // убираем старую локацию
            brk.blockLoc = loc;
            brk.config = config;
            brk.currentDamage = 0.0;
            locationToPlayer.put(loc, player.getUniqueId()); // обновляем на новую
        }

        // ─── НАНОСИМ ПЕРВЫЙ УРОН ───
        double increment = getToolDamage(tool, config);
        increment *= getEfficiencyBoost(tool, config);
        increment *= getHasteBoost(player, config);
        brk.currentDamage += increment;
        brk.lastDamageTick = Bukkit.getCurrentTick();

        float progress = (float) Math.min(1.0, brk.currentDamage / config.maxDamage());
        sendCrackProgress(player, loc, progress);

        if (config.playEffects()) {
            Location center = loc.clone().add(0.5, 0.5, 0.5);
            block.getWorld().playSound(center, Sound.BLOCK_STONE_HIT, 0.3f,
                    0.5f + progress * 0.5f);
            block.getWorld().spawnParticle(Particle.CRIT, center, 2, 0.3, 0.3, 0.3, 0.01);
        }

        if (brk.currentDamage >= config.maxDamage()) {
            finishBreaking(player, block, tool, loc, config);
            activeBreaks.remove(player.getUniqueId());
        }
    }

    // ========== ОЧИСТКА ==========

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        cleanup(e.getPlayer().getUniqueId());
    }

    private void cleanup(UUID uuid) {
        ActiveBreak brk = activeBreaks.remove(uuid);
        if (brk != null) {
            locationToPlayer.remove(brk.blockLoc);
        }
        lastInteractTick.remove(uuid);
    }

    // ========== РАЗРУШЕНИЕ БЛОКА ==========

    private void finishBreaking(Player player, Block block, ItemStack tool, Location loc, BlockConfig config) {
        sendCrackProgress(player, loc, 0.0f);

        Location center = loc.clone().add(0.5, 0.5, 0.5);

        // Сохраняем тип блока ДО его изменения, чтобы dropBlock работал корректно
        Material blockType = block.getType();

        if (config.playEffects()) {
            block.getWorld().playSound(center, Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);
            block.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);

            block.getWorld().spawnParticle(Particle.BLOCK,
                    center, 60, 0.5, 0.5, 0.5,
                    block.getBlockData());
            block.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
            block.getWorld().spawnParticle(Particle.CRIT, center, 30, 0.5, 0.5, 0.5, 0.1);
        }

        // Ванильный дроп (если включено)
        if (config.breakNaturally()) {
            block.breakNaturally(tool);
        } else {
            block.setType(Material.AIR);
        }

        // Дропаем сам блок (только если он не выпал через breakNaturally)
        if (config.dropBlock()) {
            block.getWorld().dropItemNaturally(center, new ItemStack(blockType));
        }

        player.sendActionBar("§a✔ Блок разрушен!");
    }

    // ========== АНИМАЦИЯ ТРЕЩИН ==========

    private void sendCrackProgress(Player player, Location loc, float progress) {
        player.sendBlockDamage(loc, Math.min(1.0f, Math.max(0.0f, progress)));
    }

    // ========== ИНСТРУМЕНТЫ ==========

    private boolean isBreakable(Material type) {
        BlockConfig config = blockConfigs.get(type);
        return config != null && config.enabled();
    }

    private boolean isValidTool(ItemStack tool, BlockConfig config) {
        if (tool == null || tool.getType() == Material.AIR) return false;
        String name = tool.getType().name();
        if (!name.endsWith("_PICKAXE")) return false;
        if ("-".equals(config.minToolTier())) return true;
        return getToolTier(name) >= getToolTier(config.minToolTier());
    }

    private int getToolTier(String name) {
        if (name.startsWith("NETHERITE")) return 5;
        if (name.startsWith("DIAMOND"))   return 4;
        if (name.startsWith("IRON"))      return 3;
        if (name.startsWith("STONE"))     return 2;
        if (name.startsWith("GOLD"))      return 1;
        if (name.startsWith("WOODEN"))    return 1;
        return 0;
    }

    private double getToolDamage(ItemStack tool, BlockConfig config) {
        if (tool == null) return config.defaultDamage();
        String name = tool.getType().name();

        // Ищем точное совпадение для инструмента
        for (var entry : config.toolDamage().entrySet()) {
            if (name.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        // Пробуем без _PICKAXE
        for (var entry : config.toolDamage().entrySet()) {
            String key = entry.getKey().replace("_PICKAXE", "");
            if (name.startsWith(key)) {
                return entry.getValue();
            }
        }

        return config.defaultDamage();
    }

    private double getEfficiencyBoost(ItemStack tool, BlockConfig config) {
        if (tool == null || tool.getType() == Material.AIR) return 1.0;
        // In Paper 1.21.4+ hasItemMeta() returns false for fresh items.
        // getItemMeta() always returns non-null for non-AIR items.
        var meta = tool.getItemMeta();
        if (meta == null) return 1.0;
        int level = meta.getEnchantLevel(Enchantment.EFFICIENCY);
        if (level <= 0) return 1.0;
        return 1.0 + level * config.efficiencyMultiplier();
    }

    private double getHasteBoost(Player player, BlockConfig config) {
        if (!config.requireHaste()) return 1.0;
        int level = getHasteLevel(player);
        if (level <= 0) return 1.0;
        return 1.0 + level * config.hasteMultiplier();
    }

    private int getHasteLevel(Player player) {
        for (var effect : player.getActivePotionEffects()) {
            var type = effect.getType();
            String key = type.getKey().getKey();
            if ("haste".equals(key) || "conduit_power".equals(key)) {
                return effect.getAmplifier() + 1;
            }
        }
        return 0;
    }

    // ========== УТИЛИТЫ ==========

    private static Location normalizeLoc(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    // ========== API ДЛЯ ВНЕШНИХ ВЫЗОВОВ ==========

    public static UnbreakableBreakerManager getInstance() {
        return instance;
    }

    public static double getProgress(Location loc) {
        if (instance == null) return 0.0;
        Location norm = normalizeLoc(loc);
        UUID uuid = instance.locationToPlayer.get(norm);
        if (uuid == null) return 0.0;
        ActiveBreak brk = instance.activeBreaks.get(uuid);
        if (brk == null || !brk.blockLoc.equals(norm)) return 0.0;
        return Math.min(1.0, brk.currentDamage / brk.config.maxDamage());
    }

    public static void resetProgress(Location loc) {
        if (instance == null) return;
        Location norm = normalizeLoc(loc);
        instance.activeBreaks.values().removeIf(brk -> brk.blockLoc.equals(norm));
    }

    public static void resetAll() {
        if (instance != null) {
            instance.activeBreaks.clear();
            instance.lastInteractTick.clear();
        }
    }
}
