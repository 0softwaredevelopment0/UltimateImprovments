package com.ultimateimprovments.mechanics.features.items;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.ConsoleLogger;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Фича: Прогрессивное ломание неразрушимых блоков ПО КЛИКАМ.
 *
 * Позволяет ломать блоки, которые в ванилле нельзя сломать (бедрок, барьер и т.д.),
 * через накопление "урона" инструментом с анимацией трещин.
 *
 * Каждый блок настраивается отдельно в config.yml → features.unbreakable_breaker.blocks.
 *
 * Как работает:
 * 1. Каждый клик ЛКМ по блоку (PlayerInteractEvent LEFT_CLICK_BLOCK) = ОДИН удар.
 *    Событие отменяется — клиент сбрасывает анимацию, и чтобы нанести следующий
 *    удар, игрок обязан кликнуть снова.
 * 2. За каждый клик в акшенбаре показывается % ломания блока.
 * 3. Урон копится между кликами, пока игрок смотрит на тот же блок с подходящим
 *    инструментом. Отвёл взгляд / сменил инструмент / вышел — сессия сбрасывается.
 * 4. При достижении max_damage блок разрушается с эффектами.
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

    // ========== СЕССИЯ ЛОМАНИЯ ==========

    private static class ActiveBreak {
        Location blockLoc;          // нормализованная локация блока
        double currentDamage;       // накопленный урон
        BlockConfig config;         // конфиг конкретного блока
        int lastDamageTick;         // тик последнего принятого удара (анти-автокликер)

        ActiveBreak(Location blockLoc, BlockConfig config) {
            this.blockLoc = blockLoc;
            this.config = config;
            this.currentDamage = 0.0;
            this.lastDamageTick = 0;
        }
    }

    /** Минимальный интервал между ударами (в тиках) — защита от автокликеров. */
    private static final int MIN_CLICK_INTERVAL_TICKS = 3;

    // UUID игрока → активная сессия
    private final Map<UUID, ActiveBreak> activeBreaks = new HashMap<>();

    // Обратная карта: Location → UUID для O(1) доступа в getProgress()
    private final Map<Location, UUID> locationToPlayer = new HashMap<>();

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

    // ========== ПОДДЕРЖАНИЕ ТРЕЩИН + ОЧИСТКА СЕССИЙ (каждый тик) ==========

    @Override
    public void run() {
        if (!featureEnabled) return;

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

            // ─── ПОДДЕРЖИВАЕМ ТРЕЩИНЫ НА ТЕКУЩЕМ ПРОЦЕНТЕ КАЖДЫЙ ТИК ───
            // Урон наносится ТОЛЬКО по кликам (см. onBlockInteract).
            float progress = (float) Math.min(1.0, brk.currentDamage / brk.config.maxDamage());
            sendCrackProgress(player, brk.blockLoc, progress);
        }
    }

    // ========== ОБРАБОТЧИК КЛИКА ==========

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

        // ─── ОДИН КЛИК = ОДИН УДАР ───
        // Анти-автокликер: клики чаще чем MIN_CLICK_INTERVAL_TICKS игнорируются.
        if (Bukkit.getCurrentTick() - brk.lastDamageTick < MIN_CLICK_INTERVAL_TICKS) {
            e.setCancelled(true);
            return;
        }

        double increment = getToolDamage(tool, config);
        increment *= getEfficiencyBoost(tool, config);
        increment *= getHasteBoost(player, config);
        brk.currentDamage += increment;
        brk.lastDamageTick = Bukkit.getCurrentTick();

        float progress = (float) Math.min(1.0, brk.currentDamage / config.maxDamage());
        sendCrackProgress(player, loc, progress);

        // 📊 Процент ломания в акшенбаре за каждый клик
        int percent = (int) Math.round(progress * 100);
        player.sendActionBar(MessageUtil.parse(
                "<yellow>🔨</yellow> <white>Ломание:</white> <aqua>" + percent + "%</aqua>"
        ));

        if (config.playEffects()) {
            Location center = loc.clone().add(0.5, 0.5, 0.5);
            block.getWorld().playSound(center, Sound.BLOCK_STONE_HIT, 0.3f,
                    0.5f + progress * 0.5f);
            block.getWorld().spawnParticle(Particle.CRIT, center, 2, 0.3, 0.3, 0.3, 0.01);
        }

        if (brk.currentDamage >= config.maxDamage()) {
            finishBreaking(player, block, tool, loc, config);
            cleanup(player.getUniqueId());
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

        player.sendActionBar(MessageUtil.parse("<green>✔</green> <white>Блок разрушен!</white>"));
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
        instance.locationToPlayer.remove(norm);
    }

    public static void resetAll() {
        if (instance != null) {
            instance.activeBreaks.clear();
            instance.locationToPlayer.clear();
        }
    }
}
