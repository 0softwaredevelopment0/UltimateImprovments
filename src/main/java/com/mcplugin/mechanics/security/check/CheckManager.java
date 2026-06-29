package com.mcplugin.mechanics.security.check;

import com.mcplugin.infrastructure.core.Main;
import com.mcplugin.infrastructure.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер проверки на читы.
 * <p>
 * Хранит пары (проверяющий → проверяемый).
 * При выходе проверяющего — автоматически завершает проверку.
 * Проверяемый игрок заморожен (не может двигаться, взаимодействовать и т.д.).
 */
public class CheckManager {

    private static CheckManager instance;

    // inspector UUID → suspect UUID
    private final Map<UUID, UUID> activeChecks = new ConcurrentHashMap<>();

    // suspect UUID → inspector UUID (обратный индекс)
    private final Map<UUID, UUID> suspectToInspector = new ConcurrentHashMap<>();

    private CheckManager() {}

    public static void init() {
        instance = new CheckManager();
        Main.getInstance().getLogger().info("[CheckManager] ✔ Initialized.");
    }

    public static CheckManager getInstance() {
        return instance;
    }

    // =========================
    // START CHECK
    // =========================
    public static boolean startCheck(Player inspector, Player suspect) {
        if (instance == null) return false;
        if (inspector.equals(suspect)) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ You cannot check yourself!</red>"));
            return false;
        }
        if (instance.activeChecks.containsKey(inspector.getUniqueId())) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ You already have an active check!</red>"));
            return false;
        }
        if (instance.suspectToInspector.containsKey(suspect.getUniqueId())) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ This player is already being checked!</red>"));
            return false;
        }

        UUID inspectorId = inspector.getUniqueId();
        UUID suspectId = suspect.getUniqueId();

        instance.activeChecks.put(inspectorId, suspectId);
        instance.suspectToInspector.put(suspectId, inspectorId);

        // Freeze suspect
        freezePlayer(suspect);

        // Title (используем legacy строки, т.к. sendTitle() принимает String)
        suspect.sendTitle(
                "§4❌ §c§lПРОВЕРКА НА ЧИТЫ",
                "§fВсе инструкции находятся в чате.",
                10, 70, 20
        );

        // Chat instructions
        suspect.sendMessage("");
        suspect.sendMessage("§4❌ §f§lВЫ ВЫЗВАНЫ НА ПРОВЕРКУ!");
        suspect.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
        suspect.sendMessage("§fПроверяющий: §e" + inspector.getName());
        suspect.sendMessage("");
        suspect.sendMessage("§7Вы не можете двигаться или взаимодействовать");
        suspect.sendMessage("§7до завершения проверки.");
        suspect.sendMessage("");
        suspect.sendMessage("§7Если у вас есть запрещённые модификации —");
        suspect.sendMessage("§7отключите их. Это в ваших интересах.");
        suspect.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
        suspect.sendMessage("");

        // Notify inspector
        inspector.sendMessage(MessageUtil.parse("<green>✔</green> <white>Check started for</white> <yellow>" + suspect.getName() + "</yellow><white>.</white>"));
        inspector.sendMessage(MessageUtil.parse("<gray>Use </gray><white>/mp uncheck " + suspect.getName() + "</white><gray> when finished.</gray>"));

        Main.getInstance().getLogger().info(
                "[CheckManager] " + inspector.getName() + " started checking " + suspect.getName());
        return true;
    }

    // =========================
    // END CHECK
    // =========================
    public static boolean endCheck(Player inspector, Player suspect) {
        if (instance == null) return false;

        UUID inspectorId = inspector.getUniqueId();
        UUID suspectId = suspect.getUniqueId();

        UUID storedSuspect = instance.activeChecks.get(inspectorId);
        if (storedSuspect == null || !storedSuspect.equals(suspectId)) {
            inspector.sendMessage(MessageUtil.parse("<red>❌ You are not checking this player!</red>"));
            return false;
        }

        instance.activeChecks.remove(inspectorId);
        instance.suspectToInspector.remove(suspectId);

        // Unfreeze suspect (если он ещё онлайн)
        if (suspect.isOnline()) {
            unfreezePlayer(suspect);
            suspect.sendTitle(" ", " ", 0, 1, 0);
            suspect.sendMessage("");
            suspect.sendMessage("§a✔ §f§lПРОВЕРКА ЗАВЕРШЕНА!");
            suspect.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
            suspect.sendMessage("§fПроверяющий: §e" + inspector.getName());
            suspect.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
            suspect.sendMessage("");
        }

        inspector.sendMessage(MessageUtil.parse("<green>✔</green> <white>Check ended for</white> <yellow>" + suspect.getName() + "</yellow><white>.</white>"));

        Main.getInstance().getLogger().info(
                "[CheckManager] " + inspector.getName() + " ended check for " + suspect.getName());
        return true;
    }

    // =========================
    // CLEANUP BY INSPECTOR QUIT
    // =========================
    public static void cleanupByInspector(UUID inspectorId) {
        if (instance == null) return;

        UUID suspectId = instance.activeChecks.remove(inspectorId);
        if (suspectId == null) return;

        instance.suspectToInspector.remove(suspectId);

        Player suspect = Bukkit.getPlayer(suspectId);
        if (suspect != null && suspect.isOnline()) {
            unfreezePlayer(suspect);
            suspect.sendTitle(" ", " ", 0, 1, 0);
            suspect.sendMessage("");
            suspect.sendMessage("§e⚠ §f§lПРОВЕРКА ПРЕРВАНА!");
            suspect.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
            suspect.sendMessage("§fПроверяющий вышел с сервера.");
            suspect.sendMessage("§7━━━━━━━━━━━━━━━━━━━━━");
            suspect.sendMessage("");
        }

        Main.getInstance().getLogger().info(
                "[CheckManager] Auto-cleaned check (inspector disconnected): " + suspectId);
    }

    // =========================
    // CLEANUP BY SUSPECT QUIT
    // =========================
    public static void cleanupBySuspect(UUID suspectId) {
        if (instance == null) return;

        UUID inspectorId = instance.suspectToInspector.remove(suspectId);
        if (inspectorId == null) return;

        instance.activeChecks.remove(inspectorId);

        Player inspector = Bukkit.getPlayer(inspectorId);
        if (inspector != null && inspector.isOnline()) {
            inspector.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>Checked player</white> <yellow>" +
                    (Bukkit.getOfflinePlayer(suspectId).getName() != null ?
                     Bukkit.getOfflinePlayer(suspectId).getName() : suspectId.toString()) +
                    "</yellow> <white>disconnected. Check ended.</white>"));
        }

        Main.getInstance().getLogger().info(
                "[CheckManager] Auto-cleaned check (suspect disconnected): " + suspectId);
    }

    // =========================
    // QUERY
    // =========================
    public static boolean isBeingChecked(Player player) {
        if (instance == null) return false;
        return instance.suspectToInspector.containsKey(player.getUniqueId());
    }

    public static boolean isInspector(Player player) {
        if (instance == null) return false;
        return instance.activeChecks.containsKey(player.getUniqueId());
    }

    public static Player getInspector(Player suspect) {
        if (instance == null) return null;
        UUID inspectorId = instance.suspectToInspector.get(suspect.getUniqueId());
        if (inspectorId == null) return null;
        return Bukkit.getPlayer(inspectorId);
    }

    public static Player getSuspect(Player inspector) {
        if (instance == null) return null;
        UUID suspectId = instance.activeChecks.get(inspector.getUniqueId());
        if (suspectId == null) return null;
        return Bukkit.getPlayer(suspectId);
    }

    // =========================
    // FREEZE / UNFREEZE (аналогично AuthAuthenticator)
    // =========================
    private static void freezePlayer(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);
    }

    private static void unfreezePlayer(Player player) {
        if (!player.isOnline()) return;
        player.setGameMode(GameMode.SURVIVAL);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);
    }

    // =========================
    // SHUTDOWN — разморозить всех
    // =========================
    public static void shutdown() {
        if (instance == null) return;
        for (UUID suspectId : instance.suspectToInspector.keySet()) {
            Player suspect = Bukkit.getPlayer(suspectId);
            if (suspect != null && suspect.isOnline()) {
                unfreezePlayer(suspect);
                suspect.sendTitle(" ", " ", 0, 1, 0);
                suspect.sendMessage("§e⚠ Проверка прервана (перезагрузка плагина).");
            }
        }
        instance.activeChecks.clear();
        instance.suspectToInspector.clear();
        instance = null;
    }
}
