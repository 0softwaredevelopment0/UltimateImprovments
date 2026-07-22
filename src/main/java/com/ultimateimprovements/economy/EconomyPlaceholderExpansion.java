package com.ultimateimprovements.economy;

import com.ultimateimprovements.util.ConsoleLogger;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;

/**
 * PlaceholderAPI-расширение для валютной системы.
 * <p>
 * Плейсхолдеры:
 * <ul>
 *   <li>{@code %mcplugin_money%} — баланс в основной валюте</li>
 *   <li>{@code %mcplugin_money_<currency>%} — баланс в указанной валюте</li>
 *   <li>{@code %mcplugin_money_formatted%} — отформатированный баланс (с названием валюты)</li>
 *   <li>{@code %mcplugin_money_<currency>_formatted%} — отформатированный баланс в указанной валюте</li>
 *   <li>{@code %mcplugin_default_balance%} — дефолтная сумма с которой заходит игрок</li>
 * </ul>
 */
public class EconomyPlaceholderExpansion extends PlaceholderExpansion {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.##");

    private final EconomyManager manager;

    public EconomyPlaceholderExpansion() {
        this.manager = EconomyManager.getInstance();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mcplugin";
    }

    @Override
    public @NotNull String getAuthor() {
        return "rizer001";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // не выгружать при /reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "0";

        String lower = params.toLowerCase();

        // %mcplugin_money%
        if (lower.equals("money")) {
            return FMT.format(manager.getBalance(player.getUniqueId()));
        }

        // %mcplugin_money_formatted%
        if (lower.equals("money_formatted")) {
            double bal = manager.getBalance(player.getUniqueId());
            return FMT.format(bal) + " " + (bal == 1 ? manager.getPrimaryCurrency() : manager.getPrimaryCurrency() + "s");
        }

        // %mcplugin_money_<currency>% и %mcplugin_money_<currency>_formatted%
        if (lower.startsWith("money_")) {
            String rest = lower.substring(6); // после "money_"

            boolean formatted = false;
            String currency;

            if (rest.endsWith("_formatted")) {
                formatted = true;
                currency = rest.substring(0, rest.length() - 10); // убираем "_formatted"
            } else if (rest.equals("formatted")) {
                formatted = true;
                currency = "";
            } else {
                currency = rest;
            }

            if (currency.isEmpty()) currency = manager.getPrimaryCurrency();
            double bal = manager.getBalance(player.getUniqueId(), currency);

            if (formatted) {
                return FMT.format(bal) + " " + currency;
            }
            return FMT.format(bal);
        }

        // %mcplugin_default_balance%
        if (lower.equals("default_balance")) {
            return FMT.format(manager.getDefaultBalance());
        }

        return null;
    }
}
