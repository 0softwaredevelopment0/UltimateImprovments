package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.core1.ReactorCommand;
import com.mcplugin.core1.ReactorManager;
import com.mcplugin.features.FeaturesManager;
import com.mcplugin.features.integrity.IntegrityManager;
import com.mcplugin.features.magnet.MagnetManager;
import com.mcplugin.main.TaskManager;
import com.mcplugin.server.EmergencyEntitiesKill;
import com.mcplugin.server.RedstoneGuard;
import com.mcplugin.server.ServerOverloadWarning;
import com.mcplugin.energy.crafting.EnergyWorkbenchManager;
import com.mcplugin.crafting.MultimeterCraftListener;
import com.mcplugin.auth.AuthDatabase;
import com.mcplugin.auth.AuthGUI;
import com.mcplugin.auth.AuthManager;
import com.mcplugin.cp.CodePanelClick;
import com.mcplugin.cp.CodePanelCommand;
import com.mcplugin.cp.CodePanelDatabase;
import com.mcplugin.database.DatabaseManager;

import org.bukkit.Bukkit;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.ChatColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PluginReloadCommand implements CommandExecutor, TabCompleter {

    // =========================
    // COOLDOWN TRACKING
    // =========================
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    // =========================
    // SUICIDE: подтверждение + таймеры + кулдаун
    // =========================
    // Игроки, которые подтвердили суицид (ждут таймера)
    private final HashMap<UUID, Boolean> suicideConfirmed = new HashMap<>();
    private final HashMap<UUID, BukkitRunnable> suicideTasks = new HashMap<>();
    private final HashMap<UUID, Long> suicideCooldowns = new HashMap<>();

    @Override
    @SuppressWarnings("deprecation")
    public boolean onCommand(
            CommandSender sender,
            Command cmd,
            String label,
            String[] args
    ) {

        // =========================
        // CHECK ARGUMENTS
        // =========================
        if (args.length == 0) {
            sender.sendMessage("§4❌ §cНеверная команда! §7Используйте §f/mp help§7 для списка команд.");
            return true;
        }

        // =========================
        // BASE PERMISSION CHECK
        // Только для игроков — консоль пропускаем
        // =========================
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (!p.hasPermission("mcplugin")) {
                p.sendMessage("§4❌ §cУ вас нет прав на использование MC-Plugin команд!");
                return true;
            }
        }

        // =========================
        // HELP SUBCOMMAND
        // =========================
        if (args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("");
            sender.sendMessage("§6═══════════════════════════════════");
            sender.sendMessage("§6  ✦ §fMC-Plugin §7— Доступные команды");
            sender.sendMessage("§6═══════════════════════════════════");
            sender.sendMessage("");
            sender.sendMessage("§e/mp help");
            sender.sendMessage(" §7└ Список команд");
            sender.sendMessage("");
            sender.sendMessage("§e/mp reload");
            sender.sendMessage(" §7└ Перезагрузить плагин");
            sender.sendMessage("");
            sender.sendMessage("§e/mp str dfc stats");
            sender.sendMessage(" §7└ Статистика реактора");
            sender.sendMessage("§e/mp str dfc assemble");
            sender.sendMessage(" §7└ Собрать реактор тёмного синтеза");
            sender.sendMessage("§e/mp str magnet assemble");
            sender.sendMessage(" §7└ Собрать магнит");
            sender.sendMessage("§e/mp str magnet stats");
            sender.sendMessage(" §7└ Статистика магнита");
            sender.sendMessage("");
            sender.sendMessage("§e/mp power off");
            sender.sendMessage(" §7└ Запросить выключение сервера");
            sender.sendMessage("§e/mp power reboot");
            sender.sendMessage(" §7└ Запросить перезагрузку сервера");
            sender.sendMessage("§e/mp power confirm");
            sender.sendMessage(" §7└ Подтвердить запрос (консоль)");
            sender.sendMessage("§e/mp power undo");
            sender.sendMessage(" §7└ Отменить запрос");
            sender.sendMessage("");
            sender.sendMessage("§e/mp suicide");
            sender.sendMessage(" §7└ Совершить суицид (с подтверждением, без отмены)");
            sender.sendMessage("");
            sender.sendMessage("§e/mp chgdim");
            sender.sendMessage(" §7└ Открыть меню телепортации между мирами");
            sender.sendMessage("");
            sender.sendMessage("§e/mp codepane");
            sender.sendMessage(" §7└ Открыть кодовую панель");
            sender.sendMessage("§e/mp codepane key add §7<название> <код> [флаги]");
            sender.sendMessage(" §7└ Добавить ключ кодовой панели");
            sender.sendMessage("§e/mp codepane key list");
            sender.sendMessage(" §7└ Список ключей");
            sender.sendMessage("§e/mp codepane key remove §7<название>");
            sender.sendMessage(" §7└ Удалить ключ");
            sender.sendMessage("§e/mp codepane key modify §7<название> <новый_код> [флаги]");
            sender.sendMessage(" §7└ Изменить ключ");
            sender.sendMessage("");
            sender.sendMessage("§e/mp auth forcelogin <ник>");
            sender.sendMessage(" §7└ Принудительно авторизовать игрока");
            sender.sendMessage("§e/mp auth resetauth <ник>");
            sender.sendMessage(" §7└ Полностью удалить регистрацию игрока");
            sender.sendMessage("§e/mp auth chgpass <ник> <пароль>");
            sender.sendMessage(" §7└ Принудительно сменить пароль");
            sender.sendMessage("§e/mp auth delsession <ник>");
            sender.sendMessage(" §7└ Сбросить сессию (регистрация остаётся)");
            sender.sendMessage("");
            sender.sendMessage("§e/mp auth logout");
            sender.sendMessage(" §7└ Выйти из аккаунта (logout)");
            sender.sendMessage("");
            sender.sendMessage("§6═══════════════════════════════════");
            sender.sendMessage("");
            return true;
        }

        // =========================
        // CHGDIM — MENU
        // =========================
        if (args[0].equalsIgnoreCase("chgdim")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage(Main.getInstance().getConfig()
                        .getString("changedimmension.messages.player_only",
                                "§4❌ §cТолько игрок может использовать эту команду!"));
                return true;
            }

            if (!player.hasPermission("mcplugin.command.chgdim")) {
                player.sendMessage(Main.getInstance().getConfig()
                        .getString("changedimmension.messages.no_permission",
                                "§4❌ §cУ вас нет прав на эту команду!"));
                return true;
            }

            showChgdimMenu(player);
            return true;
        }

        // =========================
        // CHGDIM_TELEPORT
        // =========================
        if (args[0].equalsIgnoreCase("chgdim_teleport")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!");
                return true;
            }

            if (!player.hasPermission("mcplugin.command.chgdim")) {
                player.sendMessage("§4❌ §cУ вас нет прав на эту команду!");
                return true;
            }

            if (args.length < 2) return true;

            String worldName = args[1];

            // =========================
            // COOLDOWN CHECK
            // =========================
            UUID playerUuid = player.getUniqueId();
            long now = System.currentTimeMillis() / 1000;
            int cooldownSecs = Main.getInstance().getConfig()
                    .getInt("changedimmension.cooldown_seconds", 10);

            if (cooldowns.containsKey(playerUuid)) {
                long lastUse = cooldowns.get(playerUuid);
                long elapsed = now - lastUse;
                if (elapsed < cooldownSecs) {
                    long remaining = cooldownSecs - elapsed;
                    player.sendMessage(Main.getInstance().getConfig()
                            .getString("changedimmension.messages.cooldown",
                                    "§4❌ §cПодождите §e{seconds}§c сек перед повторным использованием!")
                            .replace("{seconds}", String.valueOf(remaining)));
                    return true;
                }
            }

            // =========================
            // GET WORLD CONFIG
            // =========================
            FileConfiguration config = Main.getInstance().getConfig();
            ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

            if (worldsSection == null || !worldsSection.contains(worldName)) {
                player.sendMessage("§4❌ §cМир §e" + worldName + "§c не настроен в конфиге!");
                return true;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                player.sendMessage(Main.getInstance().getConfig()
                        .getString("changedimmension.messages.world_not_found",
                                "§4❌ §cМир §e{world}§c не найден!")
                        .replace("{world}", worldName));
                return true;
            }

            ConfigurationSection worldConfig = worldsSection.getConfigurationSection(worldName);

            double teleportX = worldConfig != null ? worldConfig.getDouble("x", 0) : 0;
            double teleportY = worldConfig != null ? worldConfig.getDouble("y", 64) : 64;
            double teleportZ = worldConfig != null ? worldConfig.getDouble("z", 0) : 0;
            float teleportYaw = worldConfig != null ? (float) worldConfig.getDouble("yaw", 0.0) : 0.0f;
            float teleportPitch = worldConfig != null ? (float) worldConfig.getDouble("pitch", 0.0) : 0.0f;

            // =========================
            // СОХРАНЯЕМ ТЕКУЩУЮ ПОЗИЦИЮ В БД
            // =========================
            if (!DimensionManager.hasReturnLocation(player)) {
                DimensionManager.saveReturnLocation(player);
            }

            Location targetLocation = new Location(world, teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
            player.teleportAsync(targetLocation);
            cooldowns.put(playerUuid, now);

            player.sendMessage(Main.getInstance().getConfig()
                    .getString("changedimmension.messages.success",
                            "§a✅ §fТелепортация в мир §e{world}§f завершена!")
                    .replace("{world}", worldName));

            return true;
        }

        // =========================
        // CHGDIM_RETURN — телепорт назад
        // =========================
        if (args[0].equalsIgnoreCase("chgdim_return")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!");
                return true;
            }

            if (!player.hasPermission("mcplugin.command.chgdim")) {
                player.sendMessage("§4❌ §cУ вас нет прав на эту команду!");
                return true;
            }

            if (!DimensionManager.hasReturnLocation(player)) {
                player.sendMessage("§4❌ §cНет сохранённой точки для возврата!");
                return true;
            }

            Location returnLoc = DimensionManager.getReturnLocation(player);
            if (returnLoc == null) {
                player.sendMessage(Main.getInstance().getConfig()
                        .getString("changedimmension.messages.return_error",
                                "§4❌ §cОшибка: точка возврата повреждена!"));
                DimensionManager.removeReturnLocation(player);
                return true;
            }

            player.teleportAsync(returnLoc);
            DimensionManager.removeReturnLocation(player);

            player.sendMessage(Main.getInstance().getConfig()
                    .getString("changedimmension.messages.return_success",
                            "§a✅ §fВы вернулись в исходную точку!"));

            return true;
        }

        // =========================
        // CODEPANE SUBCOMMAND (Code Panel)
        // =========================
        if (args[0].equalsIgnoreCase("codepane")) {

            // Key management subcommands (console or player)
            if (args.length >= 2 && args[1].equalsIgnoreCase("key")) {
                return handleCodePaneKey(sender, args);
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cТолько игрок может открыть кодовую панель.");
                return true;
            }

            if (!player.hasPermission("mcplugin.command.codepane")) {
                player.sendMessage("§4❌ §cУ вас нет прав на использование кодовой панели!");
                return true;
            }

            return CodePanelCommand.handleCommand(player);
        }

        // =========================
        // PANE_CLICK SUBCOMMAND (Code Panel button clicks)
        // =========================
        if (args[0].equalsIgnoreCase("pane_click")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду.");
                return true;
            }

            if (!player.hasPermission("mcplugin.command.codepane")) {
                player.sendMessage("§4❌ §cУ вас нет прав на использование кодовой панели!");
                return true;
            }

            if (args.length < 2) return true;

            return CodePanelClick.handleClick(player, args[1]);
        }

        // =========================
        // STRUCTURES / STR SUBCOMMAND
        // =========================
        if (args[0].equalsIgnoreCase("structures") || args[0].equalsIgnoreCase("str")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cError: §7Only players can use this command.");
                return true;
            }

            // =========================
            // BASE PERMISSION
            // =========================
            if (!player.hasPermission("mcplugin.command.structures")) {
                player.sendMessage("§4❌ §cУ вас нет прав на управление структурами!");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§4❌ §cUsage: /mp structures <dfc|magnet> <stats|assemble>");
                return true;
            }

            // =========================
            // DFC (Dark Fusion Core — Reactor)
            // =========================
            if (args[1].equalsIgnoreCase("dfc")) {

                if (args.length < 3) {
                    player.sendMessage("§4❌ §cUsage: /mp structures dfc <stats|assemble>");
                    return true;
                }

                // =========================
                // DFC STATS
                // =========================
                if (args[2].equalsIgnoreCase("stats")) {

                    ReactorManager reactor = ReactorManager.getInstance();

                    if (reactor == null || !reactor.isValid()) {
                        player.sendMessage("§4❌ §cError: §7Активных реакторов не найдено.");
                        return true;
                    }

                    Location playerLoc = player.getLocation();
                    Location reactorLoc = reactor.getReactorLocation();

                    if (reactorLoc == null || !playerLoc.getWorld().equals(reactorLoc.getWorld())) {
                        player.sendMessage("§4❌ §cError: §7Рядом нет активного реактора.");
                        return true;
                    }

                    double distance = playerLoc.distance(reactorLoc);
                    if (distance > 50) {
                        player.sendMessage("§4❌ §cError: §7Рядом нет активного реактора (ближайший в §f"
                                + String.format("%.1f", distance) + "§7 м).");
                        return true;
                    }

                    String status;

                    if (reactor.isMeltdownCountdown()) {
                        status = "§4!!! §cВзрыв неизбежен §4!!!";
                    } else if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) {
                        status = "§eДеградация";
                    } else {
                        status = "§aНормальный";
                    }

                    int meltdownSecs = reactor.isMeltdownCountdown()
                            ? (reactor.getMeltdownTimer() / 20)
                            : 0;

                    player.sendMessage("§8┌────────────────────────────────┐");
                    player.sendMessage("§8│ §4Р.Т.С §8» §fСтатистика реактора");
                    player.sendMessage("§8├────────────────────────────────┤");

                    player.sendMessage("§8│ §7ID: §f" + reactor.getReactorId());
                    player.sendMessage("§8│ §7Статус: " + status);
                    if (reactor.isMeltdownCountdown()) {
                        player.sendMessage("§8│ §7Детонация: §c" + meltdownSecs + " сек");
                    }
                    player.sendMessage("§8│ §7Дист: §f" + String.format("%.1f", distance) + " м");

                    player.sendMessage("§8│ §6═[ §eДанные ядра §6]═");
                    player.sendMessage("§8│ §7Температура:  §f"
                            + reactor.getDisplayCoreTemp() + " C*");
                    player.sendMessage("§8│ §7Давление:    §f"
                            + reactor.getDisplayCorePress() + " kPa");
                    player.sendMessage("§8│ §7Целостность: §f"
                            + reactor.getDisplayCoreShInt() + " %");
                    player.sendMessage("§8│ §6═[═══════════]═");

                    player.sendMessage("§8│ §3═[ §bДанные корпуса §3]═");
                    player.sendMessage("§8│ §7Температура:  §f"
                            + reactor.getDisplayCoreCaseTemp() + " C*");
                    player.sendMessage("§8│ §7Давление:    §f"
                            + reactor.getDisplayCoreCasePress() + " kPa");
                    player.sendMessage("§8│ §7Целостность: §f"
                            + reactor.getDisplayCoreCaseInt() + " %");
                    player.sendMessage("§8│ §3═[═══════════]═");

                    player.sendMessage("§8│ §5═[ §dДанные рецепта §5]═");
                    int recipePct = reactor.getDisplayRecipeTime();
                    player.sendMessage("§8│ §7Прогресс:   §f"
                            + recipePct + " %");
                    String recipeStatus;
                    if (recipePct <= 0) {
                        recipeStatus = "§7Бездействует";
                    } else if (recipePct < 100) {
                        recipeStatus = "§eГотовится";
                    } else {
                        recipeStatus = "§aЗавершён";
                    }
                    player.sendMessage("§8│ §7Статус:     " + recipeStatus);

                    // Износ
                    player.sendMessage("§8│ §7Износ:      §f"
                            + reactor.getDisplayReactorWear() + " %");

                    // Энерговыработка (текущая, E/сек)
                    player.sendMessage("§8│ §7Выработка:  §f"
                            + reactor.getDisplayEnergyRate() + " E/сек");

                    if (reactor.isSelfDestruct() && !reactor.isMeltdownCountdown()) {
                        player.sendMessage("§8│ §7Самоликвид: §cАктивен");
                    }
                    player.sendMessage("§8│ §5═[═══════════]═");

                    player.sendMessage("§8│ §7Позиция: §f"
                            + reactorLoc.getBlockX() + " "
                            + reactorLoc.getBlockY() + " "
                            + reactorLoc.getBlockZ()
                            + " §7(мир: §f" + reactorLoc.getWorld().getName() + "§7)");

                    player.sendMessage("§8└────────────────────────────────┘");
                    player.sendMessage("");

                    return true;
                }

                // =========================
                // DFC ASSEMBLE (Reactor assembly)
                // =========================
                if (args[2].equalsIgnoreCase("assemble")) {

                    if (!player.hasPermission("mcplugin.command.structures.dfc")) {
                        player.sendMessage("§4❌ §cУ вас нет прав на сборку реактора!");
                        return true;
                    }

                    ReactorCommand.assembleDarkSynthesis(player);
                    return true;
                }

                player.sendMessage("§4❌ §cUsage: /mp structures dfc <stats|assemble>");
                return true;
            }

            // =========================
            // MAGNET
            // =========================
            if (args[1].equalsIgnoreCase("magnet")) {

                if (args.length < 3) {
                    player.sendMessage("§4❌ §cUsage: /mp str magnet <stats|assemble>");
                    return true;
                }

                // =========================
                // MAGNET STATS
                // =========================
                if (args[2].equalsIgnoreCase("stats")) {

                    if (!player.hasPermission("mcplugin.command.structures.magnet")) {
                        player.sendMessage("§4❌ §cУ вас нет прав на просмотр статистики магнита!");
                        return true;
                    }

                    // Find nearest magnet cluster within 50 blocks
                    Location playerLoc = player.getLocation();
                    MagnetManager.MagnetCluster nearest = null;
                    double nearestDist = Double.MAX_VALUE;

                    for (MagnetManager.MagnetCluster cluster : MagnetManager.getClusters()) {
                        if (cluster.center == null || !cluster.center.getWorld().equals(playerLoc.getWorld())) continue;
                        double dist = playerLoc.distance(cluster.center);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = cluster;
                        }
                    }

                    if (nearest == null) {
                        player.sendMessage("§4❌ §cАктивных магнитов не найдено!");
                        return true;
                    }

                    if (nearestDist > 50) {
                        player.sendMessage("§4❌ §cРядом нет активного магнита (ближайший в §f"
                                + String.format("%.1f", nearestDist) + "§c м).");
                        return true;
                    }

                    int power = nearest.blockKeys.size();
                    int radius = MagnetManager.getClusterRadiusForPower(power);
                    String tierName = ReactorCommand.getMagnetPowerTierStatic(power);

                    player.sendMessage("§8┌────────────────────────────────┐");
                    player.sendMessage("§8│ §bМагнит §8» §fСтатистика");
                    player.sendMessage("§8├────────────────────────────────┤");
                    player.sendMessage("§8│ §7Блоков: §f" + power + " §7шт");
                    player.sendMessage("§8│ §7Сила: " + tierName);
                    player.sendMessage("§8│ §7Радиус: §f" + radius + " §7блоков");
                    player.sendMessage("§8│ §7Центр: §f"
                            + nearest.center.getBlockX() + " "
                            + nearest.center.getBlockY() + " "
                            + nearest.center.getBlockZ()
                            + " §7(мир: §f" + nearest.center.getWorld().getName() + "§7)");
                    player.sendMessage("§8│ §7Дистанция: §f" + String.format("%.1f", nearestDist) + " м");
                    player.sendMessage("§8│ §7Партиклы: §fEND_ROD=" + MagnetManager.getParticleCenterMax()
                            + " §7| §fCRIT=" + MagnetManager.getParticleCritMax()
                            + " §7| §fPORTAL=" + MagnetManager.getParticlePortalMax());
                    player.sendMessage("§8│ §7Кривая: " + ("smoothstep".equalsIgnoreCase(MagnetManager.getDistanceCurveType())
                            ? "§bsmoothstep" : "§elinear")
                            + " §7| эксп. §f" + MagnetManager.getPowerExponent());
                    player.sendMessage("§8└────────────────────────────────┘");
                    return true;
                }

                // =========================
                // MAGNET ASSEMBLE
                // =========================
                if (args[2].equalsIgnoreCase("assemble")) {
                    if (!player.hasPermission("mcplugin.command.structures.magnet")) {
                        player.sendMessage("§4❌ §cУ вас нет прав на сборку магнита!");
                        return true;
                    }
                    ReactorCommand.assembleMagnet(player);
                    return true;
                }

                player.sendMessage("§4❌ §cUsage: /mp str magnet <stats|assemble>");
                return true;
            }

            player.sendMessage("§4❌ §cНеизвестный тип структуры: §f" + args[1] + "§c. Используйте §fdfc§c или §fmagnet");
            return true;
        }

        // =========================
        // ITEM SUBCOMMAND — управление целостностью предметов
        // =========================
        if (args[0].equalsIgnoreCase("item")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!");
                return true;
            }

            if (!player.hasPermission("mcplugin.command.item")) {
                player.sendMessage("§4❌ §cУ вас нет прав на управление предметами!");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§4❌ §cИспользование: §f/mp item int <set|add|list> [значение]");
                return true;
            }

            if (args[1].equalsIgnoreCase("int")) {
                if (args.length < 3) {
                    player.sendMessage("§4❌ §cИспользование: §f/mp item int set|add|list");
                    return true;
                }

                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem == null || heldItem.getType() == Material.AIR) {
                    player.sendMessage("§4❌ §cВы должны держать предмет в руке!");
                    return true;
                }

                if (!IntegrityManager.hasIntegrity(heldItem)) {
                    // Пробуем инициализировать
                    IntegrityManager.ensureInitialized(heldItem);
                    if (!IntegrityManager.hasIntegrity(heldItem)) {
                        player.sendMessage("§4❌ §cЭтот предмет не имеет системы целостности!");
                        return true;
                    }
                }

                switch (args[2].toLowerCase()) {
                    case "list" -> {
                        double current = IntegrityManager.getCurrentIntegrity(heldItem);
                        double max = IntegrityManager.getMaxIntegrity(heldItem);
                        String itemName = heldItem.hasItemMeta() && heldItem.getItemMeta().hasDisplayName()
                                ? heldItem.getItemMeta().getDisplayName()
                                : heldItem.getType().name().toLowerCase().replace("_", " ");
                        if (itemName.length() > 0) {
                            itemName = itemName.substring(0, 1).toUpperCase() + itemName.substring(1);
                        }
                        player.sendMessage("§6═══════════════════════════════════");
                        player.sendMessage("§6  ✦ §fИнформация о целостности предмета");
                        player.sendMessage("§6═══════════════════════════════════");
                        player.sendMessage("§7Предмет: §f" + itemName);
                        player.sendMessage("§7Текущая: §a" + IntegrityManager.formatPercent(current) + "%");
                        player.sendMessage("§7Макс:    §a" + IntegrityManager.formatPercent(max) + "%");
                        player.sendMessage("§6═══════════════════════════════════");
                    }
                    case "set" -> {
                        if (args.length < 4) {
                            player.sendMessage("§4❌ §cИспользование: §f/mp item int set §7<значение>");
                            return true;
                        }
                        try {
                            double value = Double.parseDouble(args[3]);
                            if (value < 0 || value > 100) {
                                player.sendMessage("§4❌ §cЗначение должно быть от 0 до 100!");
                                return true;
                            }
                            IntegrityManager.setCurrentIntegrity(heldItem, value);
                            player.sendMessage("§a✅ §fЦелостность предмета установлена на §e" + IntegrityManager.formatPercent(value) + "%");
                        } catch (NumberFormatException e) {
                            player.sendMessage("§4❌ §cНеверный формат числа! Используйте дробное число (например: 75.500)");
                        }
                    }
                    case "add" -> {
                        if (args.length < 4) {
                            player.sendMessage("§4❌ §cИспользование: §f/mp item int add §7<значение>");
                            return true;
                        }
                        try {
                            double value = Double.parseDouble(args[3]);
                            if (value <= 0) {
                                player.sendMessage("§4❌ §cЗначение должно быть больше 0!");
                                return true;
                            }
                            double current = IntegrityManager.getCurrentIntegrity(heldItem);
                            double newVal = Math.min(100.0, current + value);
                            IntegrityManager.setCurrentIntegrity(heldItem, newVal);
                            player.sendMessage("§a✅ §fДобавлено §e" + IntegrityManager.formatPercent(value) + "%§f. Текущая: §e" + IntegrityManager.formatPercent(newVal) + "%");
                        } catch (NumberFormatException e) {
                            player.sendMessage("§4❌ §cНеверный формат числа! Используйте дробное число (например: 25.500)");
                        }
                    }
                    default -> {
                        player.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + args[2]);
                        player.sendMessage("§cИспользование: §f/mp item int set|add|list");
                    }
                }
                return true;
            }

            player.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + args[1]);
            player.sendMessage("§cИспользование: §f/mp item int set|add|list");
            return true;
        }

        // =========================
        // AUTH SUBCOMMAND
        // =========================
        if (args[0].equalsIgnoreCase("auth")) {

            // Check if auth system is enabled
            if (!Main.getInstance().getConfig().getBoolean("auth.enabled", true)) {
                sender.sendMessage("§4❌ §cСистема авторизации отключена в конфиге!");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§4❌ §cИспользование: §f/mp auth forcelogin|resetauth|chgpass|delsession|logout §7<ник>");
                return true;
            }

            // =========================
            // AUTH FORCELOGIN
            // =========================
            if (args[1].equalsIgnoreCase("forcelogin")) {

                if (!sender.hasPermission("mcplugin.command.auth.forcelogin")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на принудительную авторизацию!");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§4❌ §cИспользование: §f/mp auth forcelogin §7<ник>");
                    return true;
                }

                String targetName = args[2];
                UUID targetUuid = getOfflineUuid(targetName);

                if (!AuthDatabase.isRegistered(targetUuid)) {
                    sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
                    return true;
                }

                AuthManager manager = AuthManager.getInstance();
                if (manager == null) {
                    sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!");
                    return true;
                }

                if (manager.forceLogin(targetUuid)) {
                    sender.sendMessage("§a✅ §fИгрок §e" + targetName + "§f принудительно авторизован.");
                } else {
                    sender.sendMessage("§4❌ §cНе удалось авторизовать игрока §e" + targetName);
                }

                return true;
            }

            // =========================
            // AUTH RESETAUTH
            // =========================
            if (args[1].equalsIgnoreCase("resetauth")) {

                if (!sender.hasPermission("mcplugin.command.auth.resetauth")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на сброс авторизации!");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§4❌ §cИспользование: §f/mp auth resetauth §7<ник>");
                    return true;
                }

                String targetName = args[2];
                UUID targetUuid = getOfflineUuid(targetName);

                if (!AuthDatabase.isRegistered(targetUuid)) {
                    sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
                    return true;
                }

                AuthManager manager = AuthManager.getInstance();
                if (manager == null) {
                    sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!");
                    return true;
                }

                if (manager.resetAuth(targetUuid)) {
                    sender.sendMessage("§a✅ §fРегистрация игрока §e" + targetName + "§f полностью удалена.");
                } else {
                    sender.sendMessage("§4❌ §cНе удалось удалить регистрацию игрока §e" + targetName);
                }

                return true;
            }

            // =========================
            // AUTH DELSESSION — сбрасывает сессию, НЕ удаляет регистрацию
            // =========================
            if (args[1].equalsIgnoreCase("delsession")) {

                if (!sender.hasPermission("mcplugin.command.auth.delsession")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на сброс сессии!");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§4❌ §cИспользование: §f/mp auth delsession §7<ник>");
                    return true;
                }

                String targetName = args[2];
                UUID targetUuid = getOfflineUuid(targetName);

                if (!AuthDatabase.isRegistered(targetUuid)) {
                    sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
                    return true;
                }

                AuthManager manager = AuthManager.getInstance();
                if (manager == null) {
                    sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!");
                    return true;
                }

                if (manager.deleteSession(targetUuid)) {
                    sender.sendMessage("§a✅ §fСессия игрока §e" + targetName + "§f сброшена (logout).");
                    sender.sendMessage("§8┃ §7При следующем входе нужно будет снова ввести пароль.");
                } else {
                    sender.sendMessage("§4❌ §cНе удалось сбросить сессию игрока §e" + targetName);
                }

                return true;
            }

            // =========================
            // AUTH LOGOUT (self-service)
            // =========================
            if (args[1].equalsIgnoreCase("logout")) {

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!");
                    return true;
                }

                if (!AuthManager.getInstance().isAuthenticated(player.getUniqueId())) {
                    player.sendMessage("§c❌ Вы не авторизованы!");
                    return true;
                }

                AuthGUI.openLogout(player);
                return true;
            }

            // =========================
            // AUTH CHGPASS
            // =========================
            if (args[1].equalsIgnoreCase("chgpass")) {

                if (!sender.hasPermission("mcplugin.command.auth.chgpass")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на смену пароля!");
                    return true;
                }

                if (args.length < 4) {
                    sender.sendMessage("§4❌ §cИспользование: §f/mp auth chgpass §7<ник> <новый пароль>");
                    return true;
                }

                String targetName = args[2];
                String newPassword = args[3];

                if (newPassword.length() < 4) {
                    sender.sendMessage("§4❌ §cПароль должен быть не менее 4 символов!");
                    return true;
                }

                UUID targetUuid = getOfflineUuid(targetName);

                if (!AuthDatabase.isRegistered(targetUuid)) {
                    sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
                    return true;
                }

                AuthManager manager = AuthManager.getInstance();
                if (manager == null) {
                    sender.sendMessage("§4❌ §cСистема авторизации не инициализирована!");
                    return true;
                }

                if (manager.changePassword(targetUuid, newPassword)) {
                    sender.sendMessage("§a✅ §fПароль игрока §e" + targetName + "§f успешно изменён на §a" + newPassword + "§f.");
                    sender.sendMessage("§8┃ §7Сессия сброшена — игроку нужно заново войти.");
                } else {
                    sender.sendMessage("§4❌ §cНе удалось сменить пароль игрока §e" + targetName);
                }

                return true;
            }

            sender.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + args[1]);
            sender.sendMessage("§cИспользование: §f/mp auth forcelogin|resetauth|chgpass §7<ник>");
            return true;
        }

        // =========================
        // POWER SUBCOMMAND
        // =========================
        // =========================
        if (args[0].equalsIgnoreCase("power")) {

            if (args.length < 2) {
                sender.sendMessage("§4❌ §cError: §7Usage: /mp power off|reboot|confirm|undo");
                return true;
            }

            PowerManager pm = PowerManager.getInstance();

            // =========================
            // POWER OFF (запрос выключения)
            // =========================
            if (args[1].equalsIgnoreCase("off")) {

                // Console → execute directly
                if (!(sender instanceof Player)) {
                    pm.executeDirect(false);
                    return true;
                }

                Player player = (Player) sender;

                if (!player.hasPermission("mcplugin.command.power.off")) {
                    player.sendMessage("§4❌ §cУ вас нет прав на выключение сервера!");
                    return true;
                }

                if (pm.hasPendingRequest()) {
                    sender.sendMessage("§8[§4⚠§8] §cУже есть активный запрос на управление питанием сервера.");
                    return true;
                }

                pm.requestStop(player.getName(), player.getUniqueId());

                player.sendMessage("§8[§4⚠§8] §eВыключение сервера инициировано, ожидание подтверждения консоли.");

                Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eВыключение сервера запрошено игроком §f"
                        + player.getName() + "§e. Подтвердите командой: §f/mp power confirm");
                Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eОтменить: §f/mp power undo");
                Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eЗапрос будет автоматически отменён через §f30 §eсекунд.");

                return true;
            }

            // =========================
            // POWER REBOOT (запрос перезапуска)
            // =========================
            if (args[1].equalsIgnoreCase("reboot")) {

                // Console → execute directly
                if (!(sender instanceof Player)) {
                    pm.executeDirect(true);
                    return true;
                }

                Player player = (Player) sender;

                if (!player.hasPermission("mcplugin.command.power.reboot")) {
                    player.sendMessage("§4❌ §cУ вас нет прав на перезагрузку сервера!");
                    return true;
                }

                if (pm.hasPendingRequest()) {
                    sender.sendMessage("§8[§4⚠§8] §cУже есть активный запрос на управление питанием сервера.");
                    return true;
                }

                pm.requestRestart(player.getName(), player.getUniqueId());

                player.sendMessage("§8[§4⚠§8] §eПерезапуск сервера инициирован, ожидание подтверждения консоли.");

                Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eПерезапуск сервера запрошен игроком §f"
                        + player.getName() + "§e. Подтвердите командой: §f/mp power confirm");
                Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eОтменить: §f/mp power undo");
                Bukkit.getConsoleSender().sendMessage("§8[§4⚠§8] §eЗапрос будет автоматически отменён через §f30 §eсекунд.");

                return true;
            }

            // =========================
            // POWER CONFIRM
            // =========================
            if (args[1].equalsIgnoreCase("confirm")) {

                // Only console can confirm
                if (sender instanceof Player) {
                    sender.sendMessage("§4❌ §cError: §7Только консоль может подтвердить запрос.");
                    return true;
                }

                if (!pm.hasPendingRequest()) {
                    sender.sendMessage("§8[§4⚠§8] §cНет активных запросов на выключение/перезапуск.");
                    return true;
                }

                String action = pm.getCurrentRequestType() == PowerManager.RequestType.STOP ? "Выключение" : "Перезапуск";
                String requester = pm.getRequesterName();

                if (pm.confirmRequest()) {
                    sender.sendMessage("§8[§2✔§8] §a" + action + " подтверждён (запрос от " + requester + ").");
                    Bukkit.broadcastMessage("§8[§4⚠§8] §c" + action + " сервера подтверждён консолью.");
                } else {
                    sender.sendMessage("§4❌ §cОшибка при подтверждении.");
                }

                return true;
            }

            // =========================
            // POWER UNDO
            // =========================
            if (args[1].equalsIgnoreCase("undo")) {

                if (!pm.hasPendingRequest()) {
                    sender.sendMessage("§8[§4⚠§8] §cНет активных запросов на выключение/перезапуск.");
                    return true;
                }

                // Permission check for players (console can always undo)
                if (sender instanceof Player player) {
                    if (!player.hasPermission("mcplugin.command.power.undo")) {
                        player.sendMessage("§4❌ §cУ вас нет прав на отмену запроса!");
                        return true;
                    }
                }

                String undoerName = sender instanceof Player
                        ? ((Player) sender).getName()
                        : "Консоль";

                String action = pm.undoRequest(undoerName);

                if (action != null) {
                    sender.sendMessage("§8[§2✔§8] §a" + action + " сервера отменён.");
                } else {
                    sender.sendMessage("§4❌ §cОшибка при отмене.");
                }

                return true;
            }

            sender.sendMessage("§4❌ §cError: §7Usage: /mp power off|reboot|confirm|undo");
            return true;
        }

        // =========================
        // SUICIDE — двухэтапное подтверждение, затем таймер без отмены
        // =========================
        if (args[0].equalsIgnoreCase("suicide")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду!");
                return true;
            }

            if (!player.hasPermission("mcplugin")) {
                player.sendMessage("§4❌ §cУ вас нет прав!");
                return true;
            }

            UUID uuid = player.getUniqueId();
            FileConfiguration cfg = Main.getInstance().getConfig();

            // =========================
            // НАСТРОЙКИ ИЗ КОНФИГА
            // =========================
            int countdownDuration = cfg.getInt("suicide.countdown_duration", 10);
            int cooldownSeconds = cfg.getInt("suicide.cooldown_seconds", 10);
            int confirmTimeout = cfg.getInt("suicide.confirm_timeout", 30);

            // =========================
            // КУЛДАУН
            // =========================
            if (suicideCooldowns.containsKey(uuid)) {
                long remaining = (suicideCooldowns.get(uuid) - System.currentTimeMillis()) / 1000;
                if (remaining > 0) {
                    String msg = cfg.getString("suicide.messages.cooldown_message",
                            "§4❌ §cПодождите §e{seconds}§c сек перед повторным использованием!");
                    player.sendMessage(msg.replace("{seconds}", String.valueOf(remaining)));
                    return true;
                } else {
                    suicideCooldowns.remove(uuid);
                }
            }

            // =========================
            // ПРОВЕРКА: уже есть активный таймер
            // =========================
            if (suicideTasks.containsKey(uuid)) {
                String msg = cfg.getString("suicide.messages.already_running",
                        "§4❌ §cУ вас уже запущен обратный отсчёт!");
                player.sendMessage(msg);
                return true;
            }

            // =========================
            // ЭТАП 1: ПОДТВЕРЖДЕНИЕ
            // =========================
            if (!suicideConfirmed.getOrDefault(uuid, false)) {
                suicideConfirmed.put(uuid, true);

                String warningTitle = cfg.getString("suicide.messages.warning_title", "§4☠ §cПРЕДУПРЕЖДЕНИЕ!");
                String warningText = cfg.getString("suicide.messages.warning_text", "§fВы собираетесь совершить суицид!");
                String warningNoCancel = cfg.getString("suicide.messages.warning_no_cancel", "§c⚠ После подтверждения отмена невозможна!");
                String warningConfirmHint = cfg.getString("suicide.messages.warning_confirm_hint", "§eВведите §f/mp suicide§e ещё раз чтобы подтвердить и запустить отсчёт.");
                String warningCancelHint = cfg.getString("suicide.messages.warning_cancel_hint", "§7Если передумаете — просто подождите §e{timeout}§7 сек, и запрос сбросится.")
                        .replace("{timeout}", String.valueOf(confirmTimeout));

                player.sendMessage("");
                player.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
                player.sendMessage("§8┃ " + warningTitle);
                player.sendMessage("§8┃");
                player.sendMessage("§8┃ " + warningText);
                player.sendMessage("§8┃");
                player.sendMessage("§8┃ " + warningNoCancel);
                player.sendMessage("§8┃");

                // =========================
                // КЛИКАБЕЛЬНАЯ КНОПКА ПОДТВЕРЖДЕНИЯ
                // =========================
                TextComponent confirmButton = new TextComponent("§8┃     §2[§a✔ Подтвердить суицид§2]");
                confirmButton.setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/mp suicide"
                ));
                confirmButton.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("§aНажмите чтобы подтвердить и запустить отсчёт\n")
                                .append("§c⚠ Отмена после нажатия невозможна!")
                                .create()
                ));
                player.spigot().sendMessage(confirmButton);

                player.sendMessage("§8┃   §7или введите §f/mp suicide§7 снова");
                player.sendMessage("§8┃");
                player.sendMessage("§8┃ " + warningCancelHint);
                player.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
                player.sendMessage("");

                String warningSound = cfg.getString("suicide.sounds.warning", "BLOCK_NOTE_BLOCK_PLING");
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(warningSound), 1.0f, 0.5f);
                } catch (IllegalArgumentException ignored) { }

                // Автосброс подтверждения
                long confirmTimeoutTicks = confirmTimeout * 20L;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (suicideConfirmed.remove(uuid) != null) {
                            String timeoutMsg = cfg.getString("suicide.messages.timeout_message",
                                    "§eℹ §fЗапрос на суицид сброшен (время вышло).");
                            player.sendMessage(timeoutMsg);
                        }
                    }
                }.runTaskLater(Main.getInstance(), confirmTimeoutTicks);

                return true;
            }

            // =========================
            // ЭТАП 2: ЗАПУСК ТАЙМЕРА (подтверждено, без отмены)
            // =========================

            // Сбрасываем флаг подтверждения
            suicideConfirmed.remove(uuid);

            // =========================
            // BOSSBAR
            // =========================
            String bossColorStr = cfg.getString("suicide.bossbar.color", "RED");
            String bossStyleStr = cfg.getString("suicide.bossbar.style", "SOLID");
            String bossTitle = cfg.getString("suicide.bossbar.title", "§4☠ §cСуицид через §e{seconds}§c сек");

            BarColor bossColor;
            try {
                bossColor = BarColor.valueOf(bossColorStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                bossColor = BarColor.RED;
            }
            BarStyle bossStyle;
            try {
                bossStyle = BarStyle.valueOf(bossStyleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                bossStyle = BarStyle.SOLID;
            }

            BossBar bossBar = Bukkit.createBossBar(
                    bossTitle.replace("{seconds}", String.valueOf(countdownDuration)),
                    bossColor,
                    bossStyle
            );
            bossBar.addPlayer(player);
            bossBar.setProgress(1.0);

            // =========================
            // ЧАТ: начальное сообщение
            // =========================
            String confirmedTitle = cfg.getString("suicide.messages.confirmed_title", "§4☠ §cЗапущен обратный отсчёт!");
            String confirmedNoCancel = cfg.getString("suicide.messages.confirmed_no_cancel", "§cОтмена невозможна!");

            player.sendMessage("");
            player.sendMessage("§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
            player.sendMessage("§8┃ " + confirmedTitle);
            player.sendMessage("§8┃");
            player.sendMessage("§8┃ " + confirmedNoCancel);
            player.sendMessage("§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
            player.sendMessage("");

            playSuicideBeep(player, 1.2f);

            // =========================
            // ТАЙМЕР ОБРАТНОГО ОТСЧЁТА (каждый тик)
            // =========================
            int duration = countdownDuration;
            int totalTicks = duration * 20;
            String tickSoundName = cfg.getString("suicide.sounds.tick", "BLOCK_NOTE_BLOCK_PLING");
            String finishSoundName = cfg.getString("suicide.sounds.finish", "ENTITY_LIGHTNING_BOLT_THUNDER");
            String timerActionbar = cfg.getString("suicide.messages.timer_actionbar", "§c§l☠ §fСуицид через §e§l{seconds} §fсек");
            String timerChat = cfg.getString("suicide.messages.timer_chat", "§8[§4☠§8] §cСуицид через §e{seconds} §cсек...");
            String deathMsg = cfg.getString("suicide.messages.death_message", "§8[§4☠§8] §cВы совершили суицид...");

            // Эффективно финальные (effectively final) для использования внутри BukkitRunnable
            final Sound tickSound = parseSound(tickSoundName, Sound.BLOCK_NOTE_BLOCK_PLING);
            final Sound finishSound = parseSound(finishSoundName, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);

            BukkitRunnable task = new BukkitRunnable() {
                int tick = 0;
                int beepCounter = 0;
                int lastDisplaySecond = -1;

                @Override
                public void run() {
                    int currentSecond = duration - (tick / 20);

                    // =========================
                    // ПОСЛЕДНИЙ ТИК — выполнить
                    // =========================
                    if (currentSecond < 0) {
                        bossBar.removeAll();
                        suicideTasks.remove(uuid);
                        suicideCooldowns.put(uuid, System.currentTimeMillis() + cooldownSeconds * 1000L);

                        player.sendMessage(deathMsg);
                        player.playSound(player.getLocation(), finishSound, 1.0f, 1.0f);
                        player.setHealth(0);
                        cancel();
                        return;
                    }

                    // =========================
                    // ОБНОВЛЕНИЕ РАЗ В СЕКУНДУ
                    // =========================
                    if (currentSecond != lastDisplaySecond) {
                        lastDisplaySecond = currentSecond;

                        // Чат (последние 5 секунд)
                        if (currentSecond <= 5 && currentSecond > 0) {
                            player.sendMessage(timerChat.replace("{seconds}", String.valueOf(currentSecond)));
                        }

                        // ActionBar
                        player.sendActionBar(timerActionbar.replace("{seconds}", String.valueOf(currentSecond)));

                        // BossBar title
                        bossBar.setTitle(bossTitle.replace("{seconds}", String.valueOf(currentSecond)));
                    }

                    // =========================
                    // BOSSBAR ПРОГРЕСС (каждый тик)
                    // =========================
                    double progress = (double) (totalTicks - tick) / totalTicks;
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

                    // =========================
                    // ЗВУК: ускорение пиканья
                    // =========================
                    int interval = Math.max(4, 20 - (tick * 16 / totalTicks));
                    if (beepCounter >= interval) {
                        double p = (double) tick / totalTicks;
                        float pitch = (float) (1.2 + 1.3 * Math.min(1.0, p));
                        player.playSound(player.getLocation(), tickSound, 1.0f, pitch);
                        beepCounter = 0;
                    }
                    beepCounter++;

                    tick++;
                }
            };

            task.runTaskTimer(Main.getInstance(), 0L, 1L);
            suicideTasks.put(uuid, task);

            return true;
        }

        // =========================
        // RELOAD SUBCOMMAND
        // =========================
        if (!args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§4❌ §cНеверная команда! §7Используйте §f/mp help§7 для списка команд.");
            return true;
        }

        // =========================
        // ONLY PLAYERS
        // =========================
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§4❌ §cError: §7Only players can use this command.");
            return true;
        }

        // =========================
        // PERMISSION CHECK
        // =========================
        if (!player.hasPermission("mcplugin.command.reload")) {
            player.sendMessage("§4❌ §cError: §7You don't have permission!");
            return true;
        }

        player.sendMessage("§eReloading MC-Plugin...");

        Main plugin = Main.getInstance();

        // =========================
        // SAFE ASYNC-LIKE RELOAD TASK (sync runnable)
        // =========================
        new BukkitRunnable() {

            @Override
            public void run() {

                try {

                    long start = System.currentTimeMillis();

                    // =========================
                    // STOP TASKS
                    // =========================
                    TaskManager.getInstance().stopAll();

                    // =========================
                    // SAVE DATA
                    // =========================
                    CableNetwork.save();

                    // =========================
                    // CLOSE DB
                    // =========================
                    DatabaseManager.close();

                    // =========================
                    // RELOAD CONFIG
                    // =========================
                    plugin.reloadConfig();
                    RedstoneGuard.reload();
                    EmergencyEntitiesKill.reload();
                    ServerOverloadWarning.reload();
                    FeaturesManager.reloadConfig();
                    PowerManager.reloadConfig();
                    com.mcplugin.listeners.PowerInterceptListener.reloadConfigStatic();
                    com.mcplugin.listeners.ChatFilterManager.reloadConfigStatic();

                    // =========================
                    // RECONNECT DB
                    // =========================
                    DatabaseManager.connect();

                    // =========================
                    // REINIT SYSTEMS
                    // =========================
                    CableNetwork.init();
                    EnergyWorkbenchManager.init();
                    MultimeterCraftListener.init();

                    // =========================
                    // RESTART TASKS
                    // =========================
                    TaskManager.getInstance().startAll(plugin);

                    long time = System.currentTimeMillis() - start;

                    player.sendMessage("§2✔ §aSuccess: §7Reload complete.");
                    player.sendMessage("§2✔ §aSuccess: §7Reload time: §e" + time + "ms");

                    plugin.getLogger().info("[MCPLUGIN] Reload complete in " + time + "ms");

                } catch (Exception e) {

                    player.sendMessage("§4❌ §cError: §7Reload failed! Check console.");

                    plugin.getLogger().severe("[MCPLUGIN] Reload failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        }.runTask(plugin);

        return true;
    }

    // =========================
    // UUID resolver (offline players by name)
    // =========================
    @SuppressWarnings("deprecation")
    private UUID getOfflineUuid(String playerName) {
        return Bukkit.getOfflinePlayer(playerName).getUniqueId();
    }

    // =========================
    // TAB COMPLETER
    // =========================
    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command cmd,
            String label,
            String[] args
    ) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");
            completions.add("reload");
            completions.add("structures");
            completions.add("str");
            completions.add("power");
            completions.add("suicide");
            completions.add("auth");
            completions.add("chgdim");
            completions.add("chgdim_teleport");
            completions.add("chgdim_return");
            completions.add("codepane");
            completions.add("pane_click");
            completions.add("item");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("auth")) {
            completions.add("forcelogin");
            completions.add("resetauth");
            completions.add("chgpass");
            completions.add("delsession");
            completions.add("logout");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("auth")
                && (args[1].equalsIgnoreCase("forcelogin")
                || args[1].equalsIgnoreCase("resetauth")
                || args[1].equalsIgnoreCase("chgpass")
                || args[1].equalsIgnoreCase("delsession"))) {
            // Suggest online + registered offline players
            tabCompletePlayerNames(completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("chgdim_teleport"))) {
            ConfigurationSection worldsSection = Main.getInstance().getConfig()
                    .getConfigurationSection("changedimmension.worlds");
            if (worldsSection != null) {
                completions.addAll(worldsSection.getKeys(false));
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("structures") || args[0].equalsIgnoreCase("str"))) {
            completions.add("dfc");
            completions.add("magnet");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("codepane")) {
            completions.add("key");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("codepane") && args[1].equalsIgnoreCase("key")) {
            completions.add("add");
            completions.add("list");
            completions.add("remove");
            completions.add("modify");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("codepane") && args[1].equalsIgnoreCase("key")
                && (args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("modify"))) {
            // Suggest existing key names from database
            List<String> keyNames = CodePanelDatabase.getAllKeyNames();
            completions.addAll(keyNames);
        } else if (args.length >= 5 && args[0].equalsIgnoreCase("codepane") && args[1].equalsIgnoreCase("key")
                && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("modify"))) {
            // Suggest flags for add/modify after code arg
            // Определяем, какие флаги уже использованы (чтобы не дублировать)
            java.util.Set<String> usedFlags = new java.util.HashSet<>();
            for (int i = 5; i < args.length - 1; i++) {
                String a = args[i].toLowerCase();
                if (a.startsWith("attempts:")) usedFlags.add("attempts:");
                else if (a.startsWith("time:")) usedFlags.add("time:");
                else if (a.startsWith("whitelist:")) usedFlags.add("whitelist:");
                else if (a.startsWith("blacklist:")) usedFlags.add("blacklist:");
                else if (a.startsWith("command:")) usedFlags.add("command:");
            }

            // Если последний аргумент уже похож на флаг — не предлагаем ничего,
            // чтобы пользователь закончил ввод текущего флага
            String last = args[args.length - 1].toLowerCase();
            boolean lastIsFlag = last.startsWith("attempts:") || last.startsWith("time:")
                    || last.startsWith("whitelist:") || last.startsWith("blacklist:")
                    || last.startsWith("command:");

            if (!lastIsFlag) {
                // Предлагаем только те флаги, которые ещё не использованы
                if (!usedFlags.contains("attempts:")) completions.add("attempts:");
                if (!usedFlags.contains("time:")) completions.add("time:");
                if (!usedFlags.contains("whitelist:")) completions.add("whitelist:");
                if (!usedFlags.contains("blacklist:")) completions.add("blacklist:");
                if (!usedFlags.contains("command:")) completions.add("command:");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("power")) {
            completions.add("off");
            completions.add("reboot");
            completions.add("confirm");
            completions.add("undo");
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("structures") || args[0].equalsIgnoreCase("str")) && args[1].equalsIgnoreCase("dfc")) {
            completions.add("stats");
            completions.add("assemble");
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("structures") || args[0].equalsIgnoreCase("str")) && args[1].equalsIgnoreCase("magnet")) {
            completions.add("assemble");
            completions.add("stats");
        }

        List<String> result = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase();
        for (String s : completions) {
            if (s.toLowerCase().startsWith(last)) {
                result.add(s);
            }
        }
        return result;
    }

    // =========================
    // CODEPANE KEY MANAGEMENT (БД вместо config.yml)
    // =========================
    private boolean handleCodePaneKey(CommandSender sender, String[] args) {

        // =========================
        // 🔑 PERMISSION CHECK — базовое право на управление ключами
        // =========================
        if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key")) {
            sender.sendMessage("§4❌ §cУ вас нет прав на управление ключами кодовой панели!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§6=== §fУправление ключами кодовой панели §6===");
            sender.sendMessage("");
            sender.sendMessage("§e/mp codepane key add §7<название> <код> [флаги]");
            sender.sendMessage(" §7└ Добавить новый ключ");
            sender.sendMessage("§e/mp codepane key list");
            sender.sendMessage(" §7└ Список всех ключей");
            sender.sendMessage("§e/mp codepane key remove §7<название>");
            sender.sendMessage(" §7└ Удалить ключ");
            sender.sendMessage("§e/mp codepane key modify §7<название> <новый_код> [флаги]");
            sender.sendMessage(" §7└ Изменить ключ");
            sender.sendMessage("");
            sender.sendMessage("§7Необходимые права:");
            sender.sendMessage("§7mcplugin.command.codepane.key — базовое");
            sender.sendMessage(" §7mcplugin.command.codepane.key.add — добавление");
            sender.sendMessage(" §7mcplugin.command.codepane.key.list — список");
            sender.sendMessage(" §7mcplugin.command.codepane.key.remove — удаление");
            sender.sendMessage(" §7mcplugin.command.codepane.key.modify — изменение");
            sender.sendMessage("");
            sender.sendMessage("§7Флаги:");
            sender.sendMessage(" §7attempts:<N>     — удалить ключ после N успешных использований");
            sender.sendMessage(" §7time:<N>s|m|h|d  — удалить ключ через N секунд/минут/часов/дней");            sender.sendMessage("§7whitelist:<ник1,ник2...>  — разрешить только этим игрокам");
            sender.sendMessage("§7whitelist:(<ник1,ник2...>)  — то же, но в скобках");
            sender.sendMessage("§7blacklist:<ник1,ник2...>  — запретить этим игрокам");
            sender.sendMessage("§7blacklist:(<ник1,ник2...>)  — то же, но в скобках");
            sender.sendMessage("§7command:(<команда с пробелами>),(<команда 2>)  — команды через запятую, пробелы в скобках");
            sender.sendMessage(" §7  %entity% — заменится на ник игрока");
            sender.sendMessage("");
            sender.sendMessage("§7Примеры:");
            sender.sendMessage(" §f/mp codepane key add mydoor 1234 attempts:3 time:1h");
            sender.sendMessage(" §f/mp codepane key add admin 7777 whitelist:Steve,Alex");
            sender.sendMessage(" §f/mp codepane key add warp 4321 command:(say %entity% got access)");
            sender.sendMessage(" §f/mp codepane key add warp 4321 command:(say %entity%),(mvwarp spawn)");
            return true;
        }

        String subCmd = args[2].toLowerCase();

        switch (subCmd) {

            // =========================
            // KEY ADD
            // =========================
            case "add" -> {
                if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key.add")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на добавление ключей!");
                    return true;
                }
                if (args.length < 5) {
                    sender.sendMessage("§4❌ §cИспользование: §f/mp codepane key add §7<название> <код> [флаги]");
                    return true;
                }

                String keyName = args[3];
                String code = args[4];

                // Parse flags
                int maxAttempts = -1;
                long expiresAt = 0;
                String whitelistStr = "";
                String blacklistStr = "";
                String commandStr = "say $entity used code: " + keyName;
                java.util.Set<String> seenFlags = new java.util.HashSet<>();

                for (int i = 5; i < args.length; i++) {
                    String flag = args[i];
                    String flagType = null;

                    if (flag.startsWith("attempts:")) {
                        flagType = "attempts";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        try {
                            maxAttempts = Integer.parseInt(flag.substring(9));
                            if (maxAttempts < 1) {
                                sender.sendMessage("§4❌ §cattempts должен быть >= 1");
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage("§4❌ §cНеверный формат attempts: " + flag);
                            return true;
                        }
                    } else if (flag.startsWith("time:")) {
                        flagType = "time";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        expiresAt = parseTimeFlag(flag.substring(5));
                        if (expiresAt == 0) {
                            sender.sendMessage("§4❌ §cНеверный формат time: §7" + flag.substring(5) + " §c(используйте Ns, Nm, Nh, Nd)");
                            return true;
                        }
                    } else if (flag.startsWith("whitelist:")) {
                        flagType = "whitelist";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        whitelistStr = parseListFlag(args, i, "whitelist:");
                        if (whitelistStr == null) whitelistStr = "";
                        // If multi-arg, skip consumed args
                        int consumed = countListFlagArgs(args, i, "whitelist:");
                        if (consumed > 0) i += consumed;
                    } else if (flag.startsWith("blacklist:")) {
                        flagType = "blacklist";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        blacklistStr = parseListFlag(args, i, "blacklist:");
                        if (blacklistStr == null) blacklistStr = "";
                        int consumed = countListFlagArgs(args, i, "blacklist:");
                        if (consumed > 0) i += consumed;
                    } else if (flag.startsWith("command:")) {
                        flagType = "command";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        if (flag.startsWith("command:(")) {
                            // Parenthesized syntax: command:(cmd1 with spaces),(cmd2)
                            StringBuilder joined = new StringBuilder(flag);
                            int depth = 0;
                            for (int k = 0; k < flag.length(); k++) {
                                char ch = flag.charAt(k);
                                if (ch == '(') depth++;
                                else if (ch == ')') depth--;
                            }
                            int j = i;
                            while (depth > 0 && j + 1 < args.length) {
                                j++;
                                String nextArg = args[j];
                                joined.append(" ").append(nextArg);
                                for (int k = 0; k < nextArg.length(); k++) {
                                    char ch = nextArg.charAt(k);
                                    if (ch == '(') depth++;
                                    else if (ch == ')') depth--;
                                }
                            }
                            commandStr = extractCommandsFromParentheses(joined.toString());
                            if (commandStr == null) commandStr = "";
                            i = j; // skip consumed args
                        } else {
                            commandStr = flag.substring(8);
                        }
                    } else {
                        sender.sendMessage("§e⚠ §7Неизвестный флаг: §f" + flag);
                    }
                }

                // Check if key already exists in DB
                if (CodePanelDatabase.keyExists(keyName)) {
                    sender.sendMessage("§4❌ §cКлюч §e" + keyName + "§c уже существует!");
                    return true;
                }

                // Create key in DB
                boolean success = CodePanelDatabase.addKey(
                        keyName, code,
                        commandStr,
                        maxAttempts, expiresAt,
                        whitelistStr, blacklistStr
                );

                if (!success) {
                    sender.sendMessage("§4❌ §cОшибка при добавлении ключа в БД!");
                    return true;
                }

                sender.sendMessage("§a✅ §fКлюч §e" + keyName + "§f добавлен в БД!");
                sender.sendMessage("§8┃ §7Код: §f" + code);
                if (maxAttempts > 0) {
                    sender.sendMessage("§8┃ §7Макс. использований: §f" + maxAttempts);
                }
                if (expiresAt > 0) {
                    String dateStr = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                            .format(new java.util.Date(expiresAt));
                    sender.sendMessage("§8┃ §7Истекает: §f" + dateStr);
                }
                if (!whitelistStr.isEmpty()) {
                    sender.sendMessage("§8┃ §7Whitelist: §f" + whitelistStr);
                }
                if (!blacklistStr.isEmpty()) {
                    sender.sendMessage("§8┃ §7Blacklist: §f" + blacklistStr);
                }
                if (!commandStr.isEmpty()) {
                    sender.sendMessage("§8┃ §7Command: §f" + commandStr);
                }
                return true;
            }

            // =========================
            // KEY LIST — показать все ключи с флагами
            // =========================
            case "list" -> {
                if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key.list")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на просмотр списка ключей!");
                    return true;
                }

                List<CodePanelDatabase.CodePanelKey> keys = CodePanelDatabase.getAllKeys();

                if (keys.isEmpty()) {
                    sender.sendMessage("§eℹ §fВ базе нет ни одного ключа.");
                    return true;
                }

                sender.sendMessage("");
                sender.sendMessage("§6══════════════════════════════════");
                sender.sendMessage("§6  ✦ §fСписок ключей кодовой панели §7(" + keys.size() + ")");
                sender.sendMessage("§6══════════════════════════════════");

                for (CodePanelDatabase.CodePanelKey key : keys) {
                    sender.sendMessage("");
                    sender.sendMessage("§8┌─ §e" + key.keyName);
                    sender.sendMessage("§8│ §7Код: §f" + key.code);

                    if (key.command != null && !key.command.isEmpty()) {
                        sender.sendMessage("§8│ §7Команды: §f" + key.command);
                    }

                    if (!key.whitelist.isEmpty()) {
                        sender.sendMessage("§8│ §7Whitelist: §a" + String.join("§7, §a", key.whitelist));
                    }
                    if (!key.blacklist.isEmpty()) {
                        sender.sendMessage("§8│ §7Blacklist: §c" + String.join("§7, §c", key.blacklist));
                    }

                    if (key.maxAttempts > 0) {
                        int left = key.maxAttempts - key.attemptsUsed;
                        String color = left <= 1 ? "§c" : left <= 3 ? "§e" : "§a";
                        sender.sendMessage("§8│ §7Попытки: " + color + left + "§7/" + key.maxAttempts);
                    }

                    if (key.expiresAt > 0) {
                        long remain = key.expiresAt - System.currentTimeMillis();
                        if (remain <= 0) {
                            sender.sendMessage("§8│ §7Истекает: §cпросрочен");
                        } else {
                            String remainStr = formatDuration(remain);
                            sender.sendMessage("§8│ §7Истекает: §f" + remainStr);
                        }
                    }
                }

                sender.sendMessage("");
                sender.sendMessage("§6══════════════════════════════════");
                sender.sendMessage("");
                return true;
            }

            // =========================
            // KEY REMOVE
            // =========================
            case "remove" -> {
                if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key.remove")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на удаление ключей!");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§4❌ §cИспользование: §f/mp codepane key remove §7<название>");
                    return true;
                }

                String keyName = args[3];

                if (!CodePanelDatabase.keyExists(keyName)) {
                    sender.sendMessage("§4❌ §cКлюч §e" + keyName + "§c не найден!");
                    return true;
                }

                CodePanelDatabase.removeKey(keyName);
                sender.sendMessage("§a✅ §fКлюч §e" + keyName + "§f удалён из БД.");
                return true;
            }

            // =========================
            // KEY MODIFY
            // =========================
            case "modify" -> {
                if (sender instanceof Player p && !p.hasPermission("mcplugin.command.codepane.key.modify")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на изменение ключей!");
                    return true;
                }
                if (args.length < 5) {
                    sender.sendMessage("§4❌ §cИспользование: §f/mp codepane key modify §7<название> <новый_код> [флаги]");
                    return true;
                }

                String keyName = args[3];
                String newCode = args[4];
                String commandStrOverride = null;

                if (!CodePanelDatabase.keyExists(keyName)) {
                    sender.sendMessage("§4❌ §cКлюч §e" + keyName + "§c не найден в БД!");
                    return true;
                }

                // Get existing key to preserve flags if not overridden
                CodePanelDatabase.CodePanelKey existing = CodePanelDatabase.getKey(keyName);

                int maxAttempts = existing != null ? existing.maxAttempts : -1;
                long expiresAt = existing != null ? existing.expiresAt : 0;
                String whitelistStr = existing != null ? String.join(",", existing.whitelist) : "";
                String blacklistStr = existing != null ? String.join(",", existing.blacklist) : "";

                boolean hasCommandFlag = false;
                java.util.Set<String> seenFlags = new java.util.HashSet<>();

                for (int i = 5; i < args.length; i++) {
                    String flag = args[i];
                    String flagType = null;

                    if (flag.startsWith("attempts:")) {
                        flagType = "attempts";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        try {
                            maxAttempts = Integer.parseInt(flag.substring(9));
                            if (maxAttempts < 1) {
                                sender.sendMessage("§4❌ §cattempts должен быть >= 1");
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage("§4❌ §cНеверный формат attempts: " + flag);
                            return true;
                        }
                    } else if (flag.startsWith("time:")) {
                        flagType = "time";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        expiresAt = parseTimeFlag(flag.substring(5));
                        if (expiresAt == 0) {
                            sender.sendMessage("§4❌ §cНеверный формат time: §7" + flag.substring(5) + " §c(используйте Ns, Nm, Nh, Nd)");
                            return true;
                        }
                    } else if (flag.startsWith("whitelist:")) {
                        flagType = "whitelist";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        whitelistStr = parseListFlag(args, i, "whitelist:");
                        if (whitelistStr == null) whitelistStr = "";
                        int consumed = countListFlagArgs(args, i, "whitelist:");
                        if (consumed > 0) i += consumed;
                    } else if (flag.startsWith("blacklist:")) {
                        flagType = "blacklist";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        blacklistStr = parseListFlag(args, i, "blacklist:");
                        if (blacklistStr == null) blacklistStr = "";
                        int consumed = countListFlagArgs(args, i, "blacklist:");
                        if (consumed > 0) i += consumed;
                    } else if (flag.startsWith("command:")) {
                        flagType = "command";
                        if (seenFlags.contains(flagType)) {
                            sender.sendMessage("§4❌ §cДублирование флага: §f" + flagType + "§c! Используйте каждый флаг только один раз.");
                            return true;
                        }
                        seenFlags.add(flagType);
                        hasCommandFlag = true;
                        if (flag.startsWith("command:(")) {
                            // Parenthesized syntax
                            StringBuilder joined = new StringBuilder(flag);
                            int depth = 0;
                            for (int k = 0; k < flag.length(); k++) {
                                char ch = flag.charAt(k);
                                if (ch == '(') depth++;
                                else if (ch == ')') depth--;
                            }
                            int j = i;
                            while (depth > 0 && j + 1 < args.length) {
                                j++;
                                String nextArg = args[j];
                                joined.append(" ").append(nextArg);
                                for (int k = 0; k < nextArg.length(); k++) {
                                    char ch = nextArg.charAt(k);
                                    if (ch == '(') depth++;
                                    else if (ch == ')') depth--;
                                }
                            }
                            String parsed = extractCommandsFromParentheses(joined.toString());
                            if (parsed != null) commandStrOverride = parsed;
                            i = j; // skip consumed args
                        }
                    } else {
                        sender.sendMessage("§e⚠ §7Неизвестный флаг: §f" + flag);
                    }
                }

                // Determine the command: if command: flag provided, use it; otherwise keep existing
                String commandStr;
                if (commandStrOverride != null) {
                    commandStr = commandStrOverride;
                } else if (hasCommandFlag) {
                    // Old syntax without parens — find the flag
                    commandStr = "";
                    for (int i = 5; i < args.length; i++) {
                        if (args[i].startsWith("command:")) {
                            commandStr = args[i].substring(8);
                            break;
                        }
                    }
                } else {
                    commandStr = existing != null ? existing.command : "say $entity used code: " + keyName;
                }

                // Update in DB
                CodePanelDatabase.updateKey(
                        keyName, newCode,
                        commandStr,
                        maxAttempts, expiresAt,
                        whitelistStr, blacklistStr
                );

                sender.sendMessage("§a✅ §fКлюч §e" + keyName + "§f изменён в БД.");
                sender.sendMessage("§8┃ §7Новый код: §f" + newCode);
                if (maxAttempts > 0) {
                    sender.sendMessage("§8┃ §7Макс. использований: §f" + maxAttempts);
                }
                if (expiresAt > 0) {
                    String dateStr = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                            .format(new java.util.Date(expiresAt));
                    sender.sendMessage("§8┃ §7Истекает: §f" + dateStr);
                }
                if (!whitelistStr.isEmpty()) {
                    sender.sendMessage("§8┃ §7Whitelist: §f" + whitelistStr);
                }
                if (!blacklistStr.isEmpty()) {
                    sender.sendMessage("§8┃ §7Blacklist: §f" + blacklistStr);
                }
                if (!commandStr.isEmpty()) {
                    sender.sendMessage("§8┃ §7Command: §f" + commandStr);
                }
                return true;
            }

            default -> {
                sender.sendMessage("§4❌ §cНеизвестная подкоманда: §f" + subCmd);
                sender.sendMessage("§cИспользование: §f/mp codepane key add|list|remove|modify");
                return true;
            }
        }
    }

    // =========================
    // PARSE A LIST FLAG (whitelist:/blacklist:) WITH PARENS SUPPORT
    // Handles both:
    //   whitelist:player1,player2    — old syntax
    //   whitelist:(player1,player2)  — new syntax with parens
    // Returns the value without prefix and without wrapping parens
    // =========================
    private String parseListFlag(String[] args, int startIndex, String prefix) {
        String flag = args[startIndex];

        if (flag.startsWith(prefix + "(")) {
            // Parenthesized syntax: join all args until )
            StringBuilder joined = new StringBuilder(flag);
            int depth = 0;
            for (int k = 0; k < flag.length(); k++) {
                char ch = flag.charAt(k);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
            }
            int j = startIndex;
            while (depth > 0 && j + 1 < args.length) {
                j++;
                joined.append(" ").append(args[j]);
                for (int k = 0; k < args[j].length(); k++) {
                    char ch = args[j].charAt(k);
                    if (ch == '(') depth++;
                    else if (ch == ')') depth--;
                }
            }
            // Extract content between prefix( and the last )
            String total = joined.toString();
            int openIdx = total.indexOf('(');
            int closeIdx = total.lastIndexOf(')');
            if (openIdx != -1 && closeIdx != -1 && closeIdx > openIdx) {
                return total.substring(openIdx + 1, closeIdx);
            }
            return total.substring(prefix.length()); // fallback
        } else {
            // Old syntax without parens
            return flag.substring(prefix.length());
        }
    }

    // =========================
    // COUNT HOW MANY ARGS A PARENTHESIZED LIST FLAG CONSUMES
    // Returns number of extra args consumed (0 if no parens)
    // =========================
    private int countListFlagArgs(String[] args, int startIndex, String prefix) {
        String flag = args[startIndex];
        if (!flag.startsWith(prefix + "(")) return 0;

        int depth = 0;
        for (int k = 0; k < flag.length(); k++) {
            char ch = flag.charAt(k);
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
        }
        if (depth == 0) return 0; // closed in same arg

        int count = 0;
        for (int j = startIndex + 1; j < args.length && depth > 0; j++) {
            count++;
            for (int k = 0; k < args[j].length(); k++) {
                char ch = args[j].charAt(k);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
            }
        }
        return count;
    }

    // =========================
    // EXTRACT COMMANDS FROM PARENTHESIZED SYNTAX
    // format: command:(cmd1 with spaces),(cmd with (nested) parens)
    // returns commands joined by comma, or null if no commands found
    // =========================
    private String extractCommandsFromParentheses(String input) {
        // Find the first ( after command:
        int start = input.indexOf('(');
        if (start == -1) return null;
        start++; // position past the opening (

        StringBuilder result = new StringBuilder();
        StringBuilder current = new StringBuilder();
        int depth = 1; // we're inside the opening (

        for (int i = start; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '(') {
                if (depth > 0) current.append(c);
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    // End of current command
                    String trimmed = current.toString().trim();
                    if (!trimmed.isEmpty()) {
                        if (result.length() > 0) result.append(",");
                        result.append(trimmed);
                    }
                    current.setLength(0);

                    // Check for comma after ) → next command
                    if (i + 1 < input.length() && input.charAt(i + 1) == ',') {
                        i++; // skip comma
                    }
                } else {
                    if (depth > 0) current.append(c);
                }
            } else {
                if (depth > 0) current.append(c);
            }
        }

        return result.length() > 0 ? result.toString() : null;
    }

    // =========================
    // FORMAT DURATION (ms -> human readable)
    // =========================
    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "д " + (hours % 24) + "ч";
        if (hours > 0) return hours + "ч " + (minutes % 60) + "м";
        if (minutes > 0) return minutes + "м " + (seconds % 60) + "с";
        return seconds + "с";
    }

    // =========================
    // PARSE TIME FLAG
    // =========================
    private long parseTimeFlag(String value) {
        if (value == null || value.isEmpty()) return 0;

        char suffix = value.charAt(value.length() - 1);
        String numStr = value.substring(0, value.length() - 1);

        try {
            long amount = Long.parseLong(numStr);
            long multiplier;

            switch (suffix) {
                case 's' -> multiplier = 1000L;
                case 'm' -> multiplier = 60L * 1000L;
                case 'h' -> multiplier = 60L * 60L * 1000L;
                case 'd' -> multiplier = 24L * 60L * 60L * 1000L;
                default -> {
                    // No suffix — treat as seconds
                    amount = Long.parseLong(value);
                    multiplier = 1000L;
                }
            }

            return System.currentTimeMillis() + (amount * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // =========================
    // CHGDIM MENU
    // =========================
    private void showChgdimMenu(Player player) {

        FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");

        if (worldsSection == null || worldsSection.getKeys(false).isEmpty()) {
            player.sendMessage("§4❌ §cВ конфиге не настроено ни одного мира для телепортации!");
            return;
        }

        player.sendMessage("");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6  ✦ §fТелепортация в миры");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("");

        for (String worldKey : worldsSection.getKeys(false)) {
            ConfigurationSection wc = worldsSection.getConfigurationSection(worldKey);
            if (wc == null) continue;

            double x = wc.getDouble("x", 0);
            double y = wc.getDouble("y", 64);
            double z = wc.getDouble("z", 0);

            TextComponent btn = new TextComponent("§e[§f" + worldKey + "§e]");
            btn.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/mp chgdim_teleport " + worldKey
            ));
            btn.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§eНажмите чтобы телепортироваться\n")
                            .append("§7Мир: §f" + worldKey + "\n")
                            .append("§7Координаты: §f" + (int)x + ", " + (int)y + ", " + (int)z)
                            .create()
            ));

            // Build the line: [world] → x, y, z
            ComponentBuilder line = new ComponentBuilder("")
                    .append(btn)
                    .append(" §7→ §f" + (int)x + ", " + (int)y + ", " + (int)z);

            player.spigot().sendMessage(line.create());
        }

        // Return button (only if has return location)
        if (DimensionManager.hasReturnLocation(player)) {
            player.sendMessage("");

            TextComponent returnBtn = new TextComponent("§a[↩ Вернуться назад §a]");
            returnBtn.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/mp chgdim_return"
            ));
            returnBtn.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§aНажмите чтобы вернуться\n")
                            .append("§7Телепорт на сохранённую точку")
                            .create()
            ));

            player.spigot().sendMessage(returnBtn);
        }

        player.sendMessage("");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("");
    }

    // =========================
    // TAB COMPLETE — подсказки ников (онлайн + зарегистрированные)
    // =========================
    @SuppressWarnings("deprecation")
    private void tabCompletePlayerNames(List<String> completions) {
        // Online players — всегда актуальные ники
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (!completions.contains(name)) completions.add(name);
        }
        // Зарегистрированные (offline) — из БД auth
        for (UUID uuid : AuthDatabase.getAllRegisteredUuids()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null && !name.isEmpty() && !completions.contains(name)) {
                completions.add(name);
            }
        }
    }

    // =========================
    // SUICIDE HELPERS
    // =========================
    private void playSuicideBeep(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
    }

    private Sound parseSound(String name, Sound defaultSound) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            return defaultSound;
        }
    }
}