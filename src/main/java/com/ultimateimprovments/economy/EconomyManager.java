package com.ultimateimprovments.economy;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.database.DatabaseManager;
import com.ultimateimprovments.util.ConsoleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ядро валютной системы.
 * <p>
 * Поддерживает несколько валют (coins, gems, tokens и т.д.).
 * Vault-интеграция использует валюту "coins" как основную.
 */
public final class EconomyManager {

    private static EconomyManager instance;

    private static final String TABLE = "economy_balances";
    private static final String PRIMARY_CURRENCY = "coins";
    private static final double DEFAULT_BALANCE = 100.0;

    private EconomyManager() {}

    // ==========================================================================
    // INIT
    // ==========================================================================

    public static void init() {
        instance = new EconomyManager();
        createTable();
        ConsoleLogger.info("[Economy] Initialized. Default: " + DEFAULT_BALANCE + " " + PRIMARY_CURRENCY);
    }

    public static EconomyManager getInstance() {
        return instance;
    }

    // ==========================================================================
    // DATABASE
    // ==========================================================================

    private static void createTable() {
        try (Connection con = DatabaseManager.getConnection();
             var st = con.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS economy_balances (
                    uuid TEXT NOT NULL,
                    currency TEXT NOT NULL DEFAULT 'coins',
                    balance REAL DEFAULT 0,
                    PRIMARY KEY (uuid, currency)
                );
            """);
            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_economy_uuid
                ON economy_balances(uuid);
            """);
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] Failed to create table: " + e.getMessage());
        }
    }

    // ==========================================================================
    // BALANCE OPERATIONS
    // ==========================================================================

    /**
     * Получить баланс игрока в указанной валюте.
     * Если записи нет — возвращает 0 (и создаёт запись с дефолтной суммой? Нет — только по ивенту).
     */
    public double getBalance(UUID uuid, String currency) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT balance FROM " + TABLE + " WHERE uuid = ? AND currency = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currency.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] getBalance error: " + e.getMessage());
        }
        return 0.0;
    }

    /** Получить баланс в основной валюте (coins). */
    public double getBalance(UUID uuid) {
        return getBalance(uuid, PRIMARY_CURRENCY);
    }

    /**
     * Установить баланс игрока в указанной валюте.
     */
    public void setBalance(UUID uuid, String currency, double amount) {
        if (amount < 0) amount = 0;
        String curr = currency.toLowerCase();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT OR REPLACE INTO " + TABLE + " (uuid, currency, balance) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, curr);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] setBalance error: " + e.getMessage());
        }
    }

    /** Установить баланс в основной валюте. */
    public void setBalance(UUID uuid, double amount) {
        setBalance(uuid, PRIMARY_CURRENCY, amount);
    }

    /**
     * Добавить сумму к балансу.
     */
    public void addBalance(UUID uuid, String currency, double amount) {
        if (amount <= 0) return;
        double current = getBalance(uuid, currency);
        setBalance(uuid, currency, current + amount);
    }

    /** Добавить в основной валюте. */
    public void addBalance(UUID uuid, double amount) {
        addBalance(uuid, PRIMARY_CURRENCY, amount);
    }

    /**
     * Снять сумму с баланса.
     *
     * @return true если снятие успешно (достаточно средств)
     */
    public boolean removeBalance(UUID uuid, String currency, double amount) {
        if (amount <= 0) return true;
        double current = getBalance(uuid, currency);
        if (current < amount) return false;
        setBalance(uuid, currency, current - amount);
        return true;
    }

    /** Снять с основной валюты. */
    public boolean removeBalance(UUID uuid, double amount) {
        return removeBalance(uuid, PRIMARY_CURRENCY, amount);
    }

    /**
     * Проверить, есть ли у игрока достаточно средств.
     */
    public boolean has(UUID uuid, String currency, double amount) {
        return getBalance(uuid, currency) >= amount;
    }

    public boolean has(UUID uuid, double amount) {
        return has(uuid, PRIMARY_CURRENCY, amount);
    }

    // ==========================================================================
    // INITIAL BALANCE (first join)
    // ==========================================================================

    /**
     * Выдаёт дефолтную сумму при первом входе. Вызывается из PlayerJoinListener.
     *
     * @return true если баланс был только что создан (первый вход)
     */
    public boolean ensureDefaultBalance(UUID uuid) {
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT balance FROM " + TABLE + " WHERE uuid = ? AND currency = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, PRIMARY_CURRENCY);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return false; // уже есть запись
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] ensureDefaultBalance check error: " + e.getMessage());
        }

        // Создаём запись с дефолтной суммой
        setBalance(uuid, PRIMARY_CURRENCY, DEFAULT_BALANCE);
        ConsoleLogger.info("[Economy] Created default balance (" + DEFAULT_BALANCE + ") for " + uuid);
        return true;
    }

    // ==========================================================================
    // QUERIES
    // ==========================================================================

    /**
     * Получить балансы игрока по всем валютам.
     */
    public Map<String, Double> getAllBalances(UUID uuid) {
        Map<String, Double> result = new LinkedHashMap<>();
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT currency, balance FROM " + TABLE + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("currency"), rs.getDouble("balance"));
            }
        } catch (Exception e) {
            ConsoleLogger.error("[Economy] getAllBalances error: " + e.getMessage());
        }
        return result;
    }

    public String getPrimaryCurrency() {
        return PRIMARY_CURRENCY;
    }

    public double getDefaultBalance() {
        return DEFAULT_BALANCE;
    }

    /**
     * Сбрасывает баланс игрока в указанной валюте до 0.
     */
    public void resetBalance(UUID uuid, String currency) {
        setBalance(uuid, currency, 0.0);
    }

    public void resetBalance(UUID uuid) {
        resetBalance(uuid, PRIMARY_CURRENCY);
    }
}
