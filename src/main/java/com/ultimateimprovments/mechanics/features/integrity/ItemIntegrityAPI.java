package com.ultimateimprovments.mechanics.features.integrity;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 🧰 ItemIntegrityAPI — единый фасад для работы с целостностью предметов.
 * <p>
 * Единицы измерения зашиты прямо в имена методов, чтобы исключить путаницу
 * и случайное редактирование не той системы (ванильная прочность vs целостность):
 * <ul>
 *   <li>{@code setItemIntegrity} — установка точного % целостности (0.0–100.0)</li>
 *   <li>{@code decreaseItemIntegrity / increaseItemIntegrity} — изменение
 *       на «X использований» (int): столько целостности, сколько потратилось бы
 *       за X действий, расходующих прочность (ломка блока, удар и т.п.)</li>
 *   <li>{@code decreaseItemIntegrityPercent / increaseItemIntegrityPercent} —
 *       изменение ровно на X% (double)</li>
 * </ul>
 * <p>
 * <b>Важно для корректного вывода и синхронизации:</b>
 * <ul>
 *   <li>Все write-методы возвращают <b>фактическую</b> целостность предмета
 *       (0.0–100.0) <i>после</i> операции — источник истины для сообщений,
 *       не нужно пересчитывать значение на стороне команды.</li>
 *   <li>Все write-методы сразу обновляют лор предмета, чтобы тултип
 *       показывал актуальное значение без ожидания следующего тика.</li>
 * </ul>
 * Вся низкоуровневая работа с PDC и ванильной прочностью остаётся в
 * {@link IntegrityManager} — здесь только понятные операции.
 */
public final class ItemIntegrityAPI {

    private ItemIntegrityAPI() {}

    // =========================
    // ЧТЕНИЕ
    // =========================

    /** Зарегистрирован ли предмет в системе целостности. */
    public static boolean hasItemIntegrity(ItemStack item) {
        return IntegrityManager.hasIntegrity(item);
    }

    /**
     * Текущая целостность предмета в % (0.0–100.0),
     * или -1, если предмет не в системе целостности.
     */
    public static double getItemIntegrityPercent(ItemStack item) {
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Максимальная целостность предмета в % (всегда 100.0),
     * или -1, если предмет не в системе целостности.
     */
    public static double getItemMaxIntegrityPercent(ItemStack item) {
        return IntegrityManager.getMaxIntegrity(item);
    }

    /** Гарантирует инициализацию предмета в системе (100%) и сразу обновляет лор. */
    public static void initializeItemIntegrity(ItemStack item) {
        IntegrityManager.ensureInitialized(item);
        refreshLore(item);
    }

    // =========================
    // ЗАПИСЬ
    // =========================

    /**
     * Устанавливает целостность предмета на указанный процент (0.0 – 100.0).
     * Возвращает фактическую целостность после установки.
     */
    public static double setItemIntegrity(ItemStack item, double percent) {
        IntegrityManager.setCurrentIntegrity(item, percent);
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Уменьшает целостность так, как если бы предметом сделали {@code iterations}
     * действий, расходующих прочность (1 итерация = 1 использование).
     * Если предмет сломался раньше — остальные итерации пропускаются.
     * Возвращает фактическую целостность после списания.
     */
    public static double decreaseItemIntegrity(ItemStack item, int iterations, Player owner) {
        if (item == null || iterations <= 0) return IntegrityManager.getCurrentIntegrity(item);
        for (int i = 0; i < iterations; i++) {
            if (item.getAmount() <= 0) break; // предмет сломался — дальше нечего тратить
            IntegrityManager.decreaseIntegrity(item, 1, owner);
        }
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Увеличивает целостность на столько, сколько потратилось бы за {@code iterations}
     * действий, расходующих прочность (зеркально {@link #decreaseItemIntegrity}).
     * Результат не может превысить 100%. Возвращает фактическую целостность после ремонта.
     */
    public static double increaseItemIntegrity(ItemStack item, int iterations) {
        if (item == null || iterations <= 0) return IntegrityManager.getCurrentIntegrity(item);
        int maxDura = IntegrityManager.getMaxDurability(item);
        if (maxDura <= 0) return IntegrityManager.getCurrentIntegrity(item);
        double costPerUse = 100.0 * IntegrityManager.getCostMultiplier() / maxDura;
        IntegrityManager.increaseIntegrity(item, costPerUse * iterations);
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Уменьшает целостность ровно на указанный процент (double, 0.0 – 100.0).
     * При достижении 0 предмет ломается как обычно.
     * Возвращает фактическую целостность после списания (0, если сломался).
     */
    public static double decreaseItemIntegrityPercent(ItemStack item, double percent, Player owner) {
        IntegrityManager.decreaseIntegrityPercent(item, percent, owner);
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    /**
     * Увеличивает целостность ровно на указанный процент (double, 0.0 – 100.0).
     * Результат не может превысить 100%. Возвращает фактическую целостность после ремонта.
     */
    public static double increaseItemIntegrityPercent(ItemStack item, double percent) {
        IntegrityManager.increaseIntegrity(item, percent);
        refreshLore(item);
        return IntegrityManager.getCurrentIntegrity(item);
    }

    // =========================
    // HELPERS
    // =========================

    /**
     * Обновляет лор целостности сразу после изменения (не дожидаясь тика).
     * <p>
     * Само обновление — контент-осознанное: {@link IntegrityManager#updateItemLore}
     * переписывает meta предмета только если лор реально отличается (сверка значения
     * + содержимого лора), поэтому при неизменных данных записи не происходит.
     * В тиковом сканере {@code IntegrityManager.run()} meta переписывается только
     * при фактическом изменении лора.
     */
    private static void refreshLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getAmount() <= 0) return; // сломанный предмет — лор не нужен
        IntegrityManager.updateItemLore(item);
    }
}
