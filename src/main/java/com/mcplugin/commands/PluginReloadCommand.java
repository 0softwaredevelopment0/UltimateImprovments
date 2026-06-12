package com.mcplugin.commands;

import com.mcplugin.Main;
import com.mcplugin.cable.CableNetwork;
import com.mcplugin.core1.ReactorManager;
import com.mcplugin.features.FeaturesManager;
import com.mcplugin.main.TaskManager;
import com.mcplugin.server.RedstoneGuard;
import com.mcplugin.energy.crafting.EnergyWorkbenchManager;
import com.mcplugin.crafting.MultimeterCraftListener;
import com.mcplugin.cp.CodePanelCommand;
import com.mcplugin.database.DatabaseManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
            sender.sendMessage("§e/mp structures dfc stats");
            sender.sendMessage(" §7└ Статистика реактора");
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
            sender.sendMessage(" §7└ Телепорт в указанный мир");
            sender.sendMessage("");
            sender.sendMessage("§e/mp cp");
            sender.sendMessage(" §7└ Открыть кодовую панель");
            sender.sendMessage("");
            sender.sendMessage("§6═══════════════════════════════════");
            sender.sendMessage("");
            return true;
        }

        // =========================
        // CHG DIMENSION SUBCOMMAND
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
            // ЕСТЬ ТОЧКА ВОЗВРАТА → телепорт обратно
            // =========================
            if (DimensionManager.hasReturnLocation(player)) {
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
                cooldowns.put(playerUuid, now);

                player.sendMessage(Main.getInstance().getConfig()
                        .getString("changedimmension.messages.return_success",
                                "§a✅ §fВы вернулись в исходную точку!"));
                return true;
            }

            // =========================
            // НЕТ ТОЧКИ ВОЗВРАТА → сохранить позицию и телепортировать
            // =========================
            FileConfiguration config = Main.getInstance().getConfig();
            String worldName = config.getString("changedimmension.default_world", "");

            if (worldName.isEmpty()) {
                player.sendMessage(Main.getInstance().getConfig()
                        .getString("changedimmension.messages.no_default_world",
                                "§4❌ §cМир по умолчанию не настроен в конфиге!"));
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

            ConfigurationSection worldsSection = config.getConfigurationSection("changedimmension.worlds");
            ConfigurationSection worldConfig = worldsSection != null
                    ? worldsSection.getConfigurationSection(worldName)
                    : null;

            double teleportX, teleportY, teleportZ;
            float teleportYaw = 0.0f;
            float teleportPitch = 0.0f;

            if (worldConfig != null) {
                // Координаты из конфига
                teleportX = worldConfig.getDouble("x", 0);
                teleportY = worldConfig.getDouble("y", 64);
                teleportZ = worldConfig.getDouble("z", 0);
                teleportYaw = (float) worldConfig.getDouble("yaw", 0.0);
                teleportPitch = (float) worldConfig.getDouble("pitch", 0.0);
            } else {
                // Спавн мира, если нет конфига
                teleportX = world.getSpawnLocation().getX();
                teleportY = world.getSpawnLocation().getY();
                teleportZ = world.getSpawnLocation().getZ();
                teleportYaw = world.getSpawnLocation().getYaw();
                teleportPitch = world.getSpawnLocation().getPitch();
            }

            // =========================
            // СОХРАНЯЕМ ТЕКУЩУЮ ПОЗИЦИЮ В БД
            // =========================
            DimensionManager.saveReturnLocation(player);

            Location targetLocation = new Location(world, teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
            player.teleportAsync(targetLocation);
            cooldowns.put(playerUuid, now);

            player.sendMessage(Main.getInstance().getConfig()
                    .getString("changedimmension.messages.success",
                            "§a✅ §fТелепортация в мир §e{world}§f завершена!")
                    .replace("{world}", worldName));

            player.sendMessage(Main.getInstance().getConfig()
                    .getString("changedimmension.messages.return_info",
                            "§eℹ §7Чтобы вернуться, просто напишите §f/mp chgdim§7 ещё раз."));

            return true;
        }

        // =========================
        // CP SUBCOMMAND (Code Panel)
        // =========================
        if (args[0].equalsIgnoreCase("cp")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cТолько игрок может использовать эту команду.");
                return true;
            }

            return CodePanelCommand.handleCommand(player);
        }

        // =========================
        // STRUCTURES SUBCOMMAND
        // =========================
        if (args[0].equalsIgnoreCase("structures")) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4❌ §cError: §7Only players can use this command.");
                return true;
            }

            if (args.length < 3 || !args[1].equalsIgnoreCase("dfc") || !args[2].equalsIgnoreCase("stats")) {
                player.sendMessage("§4❌ §cError: §7Usage: /mp structures dfc stats");
                return true;
            }

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
        // POWER SUBCOMMAND
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

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§4❌ §cError: §7Только игрок может запросить выключение сервера.");
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

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§4❌ §cError: §7Только игрок может запросить перезапуск сервера.");
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

            sender.sendMessage("§4❌ §cError: §7Usage: /mcplugin power off|reboot|confirm|undo");
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
            completions.add("power");
            completions.add("chgdim");
            completions.add("cp");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("structures")) {
            completions.add("dfc");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("power")) {
            completions.add("off");
            completions.add("reboot");
            completions.add("confirm");
            completions.add("undo");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("structures") && args[1].equalsIgnoreCase("dfc")) {
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
}