package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.core1.ReactorCommand;
import com.mcplugin.core1.ReactorManager;
import com.mcplugin.features.FeaturesManager;
import com.mcplugin.features.magnet.MagnetManager;
import com.mcplugin.main.TaskManager;
import com.mcplugin.server.RedstoneGuard;
import com.mcplugin.energy.crafting.EnergyWorkbenchManager;
import com.mcplugin.crafting.MultimeterCraftListener;
import com.mcplugin.auth.AuthDatabase;
import com.mcplugin.auth.AuthGUI;
import com.mcplugin.auth.AuthManager;
import com.mcplugin.cp.CodePanelClick;
import com.mcplugin.cp.CodePanelCommand;
import com.mcplugin.database.DatabaseManager;

import org.bukkit.Bukkit;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.ChatColor;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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
            sender.sendMessage("§e/mp chgdim");
            sender.sendMessage(" §7└ Открыть меню телепортации между мирами");
            sender.sendMessage("");
            sender.sendMessage("§e/mp codepane");
            sender.sendMessage(" §7└ Открыть кодовую панель");
            sender.sendMessage("");
            sender.sendMessage("§e/mp auth forcelogin <ник>");
            sender.sendMessage(" §7└ Принудительно авторизовать игрока");
            sender.sendMessage("§e/mp auth resetauth <ник>");
            sender.sendMessage(" §7└ Полностью удалить регистрацию игрока");
            sender.sendMessage("§e/mp auth showpass <ник>");
            sender.sendMessage(" §7└ Посмотреть пароль игрока");
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

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду.");
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
        // AUTH SUBCOMMAND
        // =========================
        if (args[0].equalsIgnoreCase("auth")) {

            // Check if auth system is enabled
            if (!Main.getInstance().getConfig().getBoolean("auth.enabled", true)) {
                sender.sendMessage("§4❌ §cСистема авторизации отключена в конфиге!");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§4❌ §cИспользование: §f/mp auth forcelogin|resetauth|showpass|chgpass|delsession|logout §7<ник>");
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
            // AUTH SHOWPASS
            // =========================
            if (args[1].equalsIgnoreCase("showpass")) {

                if (!sender.hasPermission("mcplugin.command.auth.showpass")) {
                    sender.sendMessage("§4❌ §cУ вас нет прав на просмотр паролей!");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§4❌ §cИспользование: §f/mp auth showpass §7<ник>");
                    return true;
                }

                String targetName = args[2];
                UUID targetUuid = getOfflineUuid(targetName);

                if (!AuthDatabase.isRegistered(targetUuid)) {
                    sender.sendMessage("§4❌ §cИгрок §e" + targetName + "§c не зарегистрирован в системе авторизации!");
                    return true;
                }

                String password = AuthDatabase.getPasswordPlain(targetUuid);
                if (password == null || password.isEmpty()) {
                    sender.sendMessage("§4❌ §cПароль игрока §e" + targetName + "§c не найден в базе!");
                    return true;
                }

                sender.sendMessage("§6✦ §fПароль игрока §e" + targetName + "§f: §a" + password);
                sender.sendMessage("§8┃ §7Будьте осторожны — не сообщайте пароль посторонним!");

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
            sender.sendMessage("§cИспользование: §f/mp auth forcelogin|resetauth|showpass|chgpass §7<ник>");
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
            completions.add("auth");
            completions.add("chgdim");
            completions.add("chgdim_teleport");
            completions.add("chgdim_return");
            completions.add("codepane");
            completions.add("pane_click");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("auth")) {
            completions.add("forcelogin");
            completions.add("resetauth");
            completions.add("showpass");
            completions.add("chgpass");
            completions.add("delsession");
            completions.add("logout");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("auth")
                && (args[1].equalsIgnoreCase("forcelogin")
                || args[1].equalsIgnoreCase("resetauth")
                || args[1].equalsIgnoreCase("showpass")
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
}