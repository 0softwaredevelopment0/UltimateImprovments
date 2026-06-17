package com.mcplugin.features.integrity;

import com.mcplugin.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 🛡 Integrity Combine Listener — обрабатывает события, связанные
 * с восстановлением и объединением целостности предметов:
 * <p>
 * - Наковальня: ремонт материалом повышает целостность, объединение двух предметов суммирует
 * - Точило: объединение двух предметов суммирует целостность
 * - Верстак / инвентарь: объединение двух одинаковых предметов суммирует целостность
 * - Получение опыта: предмет с Шёлковым касанием восстанавливает целостность
 */
public class IntegrityCombineListener implements Listener {

    // Кулдаун сообщений в наковальне (чтобы не спамить)
    private final Map<UUID, Long> anvilMessageCooldowns = new HashMap<>();
    private static final long ANVIL_MSG_COOLDOWN_MS = 2000; // 2 секунды

    // =========================
    // НАКОВАЛЬНЯ — ремонт и объединение
    // =========================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!IntegrityManager.isEnabled()) return;

        AnvilInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;

        // Проверяем, есть ли у предмета в слоте 0 прочность
        if (slot0.getType().getMaxDurability() <= 0) return;

        // =========================
        // СЦЕНАРИЙ 1: Крафт материалом (предмет + материал, например алмазная кирка + алмаз)
        // Каждая единица материала даёт +N% целостности новому предмету
        // =========================
        if (isRepairMaterial(slot0.getType(), slot1.getType())) {
            if (!IntegrityManager.isAnvilRepairEnabled()) return;

            // Убеждаемся, что предмет инициализирован
            IntegrityManager.ensureInitialized(slot0);

            double currentIntegrity = IntegrityManager.getCurrentIntegrity(slot0);
            if (currentIntegrity <= 0) return;

            // ===== НОВАЯ МЕХАНИКА: крафт материалом =====
            if (IntegrityManager.isAnvilMaterialCraftEnabled()) {
                // Ванильная наковальня потребляет 1 единицу материала за операцию,
                // поэтому используем 1, а не slot1.getAmount() (чтобы избежать эксплойта)
                int materialCount = 1;
                double bonusPerMaterial = IntegrityManager.getAnvilMaterialCraftBonus();
                double totalBonus = materialCount * bonusPerMaterial;
                double newIntegrity = Math.min(100.0, currentIntegrity + totalBonus);

                ItemStack result = slot0.clone();
                IntegrityManager.setCurrentIntegrity(result, newIntegrity);
                IntegrityManager.updateItemLore(result);

                Player player = getPlayerFromAnvil(event);
                if (player != null) {
                    String msg = IntegrityManager.getAnvilMaterialCraftMessage()
                            .replace("{current}", IntegrityManager.formatPercent(newIntegrity))
                            .replace("{bonus}", IntegrityManager.formatPercent(totalBonus));
                    if (canSendAnvilMessage(player)) {
                        player.sendMessage(MessageUtil.parse(msg));
                    }
                }

                event.setResult(result);
                return;
            }

            // ===== СТАРАЯ МЕХАНИКА (fallback): ремонт фиксированным процентом =====
            if (currentIntegrity >= 100.0) return;

            double restoreAmount = 100.0 * IntegrityManager.getAnvilRepairMultiplier();

            ItemStack result = slot0.clone();
            IntegrityManager.increaseIntegrity(result, restoreAmount);
            IntegrityManager.updateItemLore(result);

            double newCurrent = IntegrityManager.getCurrentIntegrity(result);

            // Отправляем сообщение игроку (с кулдауном)
            Player player = getPlayerFromAnvil(event);
            if (player != null) {
                String msg = IntegrityManager.getAnvilRepairMessage()
                        .replace("{current}", IntegrityManager.formatPercent(newCurrent));
                if (canSendAnvilMessage(player)) {
                    player.sendMessage(MessageUtil.parse(msg));
                }
            }

            event.setResult(result);
            return;
        }

        // =========================
        // СЦЕНАРИЙ 2: Объединение двух одинаковых предметов (кирка + кирка)
        // =========================
        if (slot0.getType() == slot1.getType() && slot1.getType().getMaxDurability() > 0) {
            if (!IntegrityManager.isAnvilCombineEnabled()) return;
            if (!IntegrityManager.isCombineEnabled()) return;

            IntegrityManager.ensureInitialized(slot0);
            IntegrityManager.ensureInitialized(slot1);

            double cur0 = IntegrityManager.getCurrentIntegrity(slot0);
            double cur1 = IntegrityManager.getCurrentIntegrity(slot1);

            if (cur0 < 0 || cur1 < 0) return;

            // Суммируем целостность + бонус, кап — 100.0%
            double combined = cur0 + cur1;
            double bonus = 100.0 * IntegrityManager.getAnvilCombineBonus();
            combined += bonus;

            double newCurrent = Math.min(100.0, combined);

            ItemStack result = slot0.clone();
            IntegrityManager.setCurrentIntegrity(result, newCurrent);
            IntegrityManager.updateItemLore(result);

            Player player = getPlayerFromAnvil(event);
            if (player != null) {
                String msg = IntegrityManager.getAnvilCombineMessage()
                        .replace("{current}", IntegrityManager.formatPercent(newCurrent));
                if (canSendAnvilMessage(player)) {
                    player.sendMessage(MessageUtil.parse(msg));
                }
            }

            event.setResult(result);
        }
    }

    // =========================
    // ТОЧИЛО — объединение двух предметов (снимает зачарования)
    // =========================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!IntegrityManager.isEnabled()) return;
        if (!IntegrityManager.isCombineEnabled()) return;

        GrindstoneInventory inv = event.getInventory();
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        if (slot0 == null || slot0.getType() == Material.AIR) return;
        if (slot1 == null || slot1.getType() == Material.AIR) return;

        // Проверяем, что это два одинаковых предмета с прочностью
        if (slot0.getType() != slot1.getType()) return;
        if (slot0.getType().getMaxDurability() <= 0) return;

        IntegrityManager.ensureInitialized(slot0);
        IntegrityManager.ensureInitialized(slot1);

        double cur0 = IntegrityManager.getCurrentIntegrity(slot0);
        double cur1 = IntegrityManager.getCurrentIntegrity(slot1);

        if (cur0 < 0 || cur1 < 0) return;

        double combined = cur0 + cur1;
        double lossRate = IntegrityManager.getCombineLossRate();
        if (lossRate > 0) {
            combined *= (1.0 - lossRate);
        }

        double newCurrent = Math.min(100.0, combined);

        // Создаём результат и снимаем зачарования (как в ванильном точиле)
        ItemStack result = slot0.clone();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null && resultMeta.hasEnchants()) {
            // Снимаем все зачарования через копию ключей (избегаем CME)
            java.util.Map<Enchantment, Integer> enchants = new java.util.HashMap<>(resultMeta.getEnchants());
            for (Enchantment ench : enchants.keySet()) {
                resultMeta.removeEnchant(ench);
            }
            result.setItemMeta(resultMeta);
        }

        IntegrityManager.setCurrentIntegrity(result, newCurrent);
        IntegrityManager.updateItemLore(result);

        event.setResult(result);
    }

    // =========================
    // ВЕРСТАК / ИНВЕНТАРЬ — объединение при крафте (ровно 2 предмета)
    // =========================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!IntegrityManager.isEnabled()) return;
        if (!IntegrityManager.isCombineEnabled()) return;

        ItemStack[] matrix = event.getInventory().getMatrix();

        // Ищем ровно 2 одинаковых предмета с прочностью и больше ничего
        ItemStack item1 = null;
        ItemStack item2 = null;
        int itemCount = 0;
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType() == Material.AIR) continue;

            if (stack.getType().getMaxDurability() > 0) {
                if (item1 == null) {
                    item1 = stack;
                    itemCount = 1;
                } else if (stack.getType() == item1.getType() && itemCount == 1) {
                    item2 = stack;
                    itemCount = 2;
                } else {
                    // Третий предмет с прочностью — не комбинируем
                    return;
                }
            } else {
                // Есть предмет без прочности — не комбинируем
                return;
            }
        }

        // Нужно ровно 2 одинаковых предмета с прочностью в матрице
        if (itemCount != 2 || item1 == null || item2 == null) return;

        IntegrityManager.ensureInitialized(item1);
        IntegrityManager.ensureInitialized(item2);

        double cur0 = IntegrityManager.getCurrentIntegrity(item1);
        double cur1 = IntegrityManager.getCurrentIntegrity(item2);

        if (cur0 < 0 || cur1 < 0) return;

        double combined = cur0 + cur1;
        double lossRate = IntegrityManager.getCombineLossRate();
        if (lossRate > 0) {
            combined *= (1.0 - lossRate);
        }

        double newCurrent = Math.min(100.0, combined);

        // Создаём результат как клон первого предмета
        ItemStack result = item1.clone();
        IntegrityManager.setCurrentIntegrity(result, newCurrent);
        IntegrityManager.updateItemLore(result);

        event.getInventory().setResult(result);
    }

    // =========================
    // XP → ЦЕЛОСТНОСТЬ ВСЕХ ПРЕДМЕТОВ
    // При сборе опыта каждый предмет в инвентаре получает +integrityPerXp% за 1 XP
    // =========================
    @EventHandler(priority = EventPriority.NORMAL)
    public void onXpToAllIntegrity(PlayerExpChangeEvent event) {
        if (!IntegrityManager.isEnabled()) return;
        if (!IntegrityManager.isXpIntegrityEnabled()) return;

        Player player = event.getPlayer();
        int xpAmount = event.getAmount();
        if (xpAmount <= 0) return;

        double integrityPerXp = IntegrityManager.getXpIntegrityPerXp();
        double gain = xpAmount * integrityPerXp;
        if (gain <= 0) return;

        PlayerInventory inv = player.getInventory();
        boolean applied = false;

        // Обходим все слоты: 0–35 = инвентарь + хотбар, 36–39 = броня, 40 = оффхенд
        for (int i = 0; i <= 40; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType().getMaxDurability() <= 0) continue;

            double before = IntegrityManager.getCurrentIntegrity(item);
            IntegrityManager.increaseIntegrity(item, gain);
            double after = IntegrityManager.getCurrentIntegrity(item);
            if (after > before) {
                applied = true;
            }
        }

        if (applied) {
            String msg = IntegrityManager.getXpIntegrityMessage()
                    .replace("{amount}", IntegrityManager.formatPercent(gain));
            player.sendMessage(MessageUtil.parse(msg));
        }
    }

    // =========================
    // XP + ШЁЛКОВОЕ КАСАНИЕ
    // =========================
    @EventHandler(priority = EventPriority.HIGH)
    public void onExpChange(PlayerExpChangeEvent event) {
        if (!IntegrityManager.isEnabled()) return;
        if (!IntegrityManager.isSilkTouchXpEnabled()) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (mainHand == null || mainHand.getType() == Material.AIR) return;
        if (mainHand.getType().getMaxDurability() <= 0) return;

        // Проверяем, есть ли Шёлковое касание
        ItemMeta meta = mainHand.getItemMeta();
        if (meta == null) return;
        if (!meta.hasEnchant(Enchantment.SILK_TOUCH)) return;

        int xpAmount = event.getAmount();
        if (xpAmount <= 0) return;

        IntegrityManager.ensureInitialized(mainHand);

        double maxIntegrity = IntegrityManager.getMaxIntegrity(mainHand);
        double currentIntegrity = IntegrityManager.getCurrentIntegrity(mainHand);

        if (currentIntegrity >= maxIntegrity) return;

        // Восстанавливаем целостность пропорционально XP
        double restore = xpAmount * IntegrityManager.getSilkTouchXpMultiplier();
        IntegrityManager.increaseIntegrity(mainHand, restore);

        // Отправляем сообщение
        String msg = IntegrityManager.getSilkTouchMessage()
                .replace("{amount}", IntegrityManager.formatPercent(restore));
        player.sendMessage(MessageUtil.parse(msg));
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Кулдаун сообщений в наковальне (чтобы не спамить при перемещении предметов).
     */
    private boolean canSendAnvilMessage(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = anvilMessageCooldowns.get(uuid);
        if (last != null && (now - last) < ANVIL_MSG_COOLDOWN_MS) {
            return false;
        }
        anvilMessageCooldowns.put(uuid, now);
        return true;
    }

    /**
     * Проверяет, является ли предмет ремонтным материалом для данного инструмента.
     * Пример: DIAMOND_PICKAXE + DIAMOND, IRON_SWORD + IRON_INGOT и т.д.
     */
    private boolean isRepairMaterial(Material tool, Material material) {
        String toolName = tool.name();
        String matName = material.name();

        // Материалы для ремонта в майнкрафте
        return switch (matName) {
            case "DIAMOND" -> toolName.startsWith("DIAMOND_");
            case "IRON_INGOT" -> toolName.startsWith("IRON_") || toolName.startsWith("CHAINMAIL_");
            case "GOLD_INGOT" -> toolName.startsWith("GOLD_");
            case "NETHERITE_INGOT" -> toolName.startsWith("NETHERITE_");
            case "LEATHER" -> toolName.contains("LEATHER");
            case "COPPER_INGOT" -> toolName.startsWith("COPPER_");
            case "PHANTOM_MEMBRANE" -> toolName.equals("ELYTRA");
            default -> {
                // Проверка на дерево (доски, палки)
                boolean isWoodMaterial = matName.endsWith("_PLANKS") || matName.equals("STICK");
                // Деревянные инструменты + доски/палки
                if (isWoodMaterial && toolName.startsWith("WOODEN_")) {
                    yield true;
                }
                // Камень для каменных инструментов
                if ((matName.equals("COBBLESTONE") || matName.equals("BLACKSTONE") || matName.equals("COBBLED_DEEPSLATE"))
                        && toolName.startsWith("STONE_")) {
                    yield true;
                }
                // Черепаший панцирь + скальные чешуйки
                if (matName.equals("SCUTE") && toolName.equals("TURTLE_HELMET")) {
                    yield true;
                }
                yield false;
            }
        };
    }

    /**
     * Пытается получить игрока из события наковальни через инвентарь.
     */
    private Player getPlayerFromAnvil(PrepareAnvilEvent event) {
        var view = event.getView();
        if (view.getPlayer() instanceof Player player) {
            return player;
        }
        return null;
    }


}
