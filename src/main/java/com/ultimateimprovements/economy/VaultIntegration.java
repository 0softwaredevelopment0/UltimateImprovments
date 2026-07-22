package com.ultimateimprovements.economy;

import com.ultimateimprovements.util.ConsoleLogger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vault-интеграция для Economy.
 * <p>
 * Регистрируется как {@link Economy} в Vault при старте модуля.
 * Использует валюту "coins" как единственную (Vault поддерживает только одну).<br>
 * Множественные валюты доступны только через {@link EconomyManager} напрямую.
 */
public final class VaultIntegration implements Economy {

    private static final String NAME = "UltimateImprovements Economy";
    private static final String CURRENCY_SINGULAR = "coin";
    private static final String CURRENCY_PLURAL = "coins";
    private static final int DECIMAL_DIGITS = 2;

    private final EconomyManager manager;

    public VaultIntegration(JavaPlugin plugin) {
        this.manager = EconomyManager.getInstance();

        try {
            var vault = Bukkit.getServicesManager();
            vault.register(Economy.class, this, plugin, ServicePriority.Normal);
            ConsoleLogger.info("[Economy] Vault integration registered as primary economy provider.");
        } catch (NoClassDefFoundError | Exception e) {
            ConsoleLogger.info("[Economy] Vault not found — integration skipped.");
        }
    }

    // ==========================================================================
    // Economy interface
    // ==========================================================================

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return DECIMAL_DIGITS;
    }

    @Override
    public String format(double amount) {
        return String.format("%." + DECIMAL_DIGITS + "f %s", amount, amount == 1 ? CURRENCY_SINGULAR : CURRENCY_PLURAL);
    }

    @Override
    public String currencyNamePlural() {
        return CURRENCY_PLURAL;
    }

    @Override
    public String currencyNameSingular() {
        return CURRENCY_SINGULAR;
    }

    // ==========================================================================
    // Player accounts
    // ==========================================================================

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true; // каждый игрок имеет "теневой" счёт (создаётся при первом запросе)
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return manager.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return manager.has(player.getUniqueId(), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }
        UUID uuid = player.getUniqueId();
        if (!manager.removeBalance(uuid, amount)) {
            double bal = manager.getBalance(uuid);
            return new EconomyResponse(amount, bal, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        return new EconomyResponse(amount, manager.getBalance(uuid), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }
        manager.addBalance(player.getUniqueId(), amount);
        return new EconomyResponse(amount, manager.getBalance(player.getUniqueId()), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    // ==========================================================================
    // CREATE PLAYER ACCOUNT
    // ==========================================================================

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        // Счёт создаётся лениво при первом запросе
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // ==========================================================================
    // BANK (not supported)
    // ==========================================================================

    @Override
    public boolean hasAccount(String playerName) {
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(playerName);
        return p != null;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(playerName);
        return p != null ? getBalance(p) : 0;
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(String playerName, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(playerName);
        return p != null && has(p, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(playerName);
        return p != null ? withdrawPlayer(p, amount) :
                new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(playerName);
        return p != null ? depositPlayer(p, amount) :
                new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    // ==========================================================================
    // BANK METHODS (not supported — stub)
    // ==========================================================================

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank not supported");
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }
}
