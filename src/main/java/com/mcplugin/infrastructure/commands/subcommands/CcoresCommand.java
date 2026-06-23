package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /mp ccores — проверяет сколько руды каждого типа в чанке игрока.
 * <p>
 * Задержка: 10 секунд между использованиями.
 * Право: mcplugin.command.ccores (по умолчанию true для всех).
 */
public class CcoresCommand {

    private CcoresCommand() {}

    /** Cooldown в миллисекундах. */
    private static final long COOLDOWN_MS = 10_000L;
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static long lastCleanup = System.currentTimeMillis();

    /** Материалы, считающиеся рудой, и их отображаемые имена. */
    private static final Map<Material, String> ORE_MATERIALS = new EnumMap<>(Map.ofEntries(
        Map.entry(Material.COAL_ORE,          "Coal"),
        Map.entry(Material.DEEPSLATE_COAL_ORE, "Deepslate Coal"),
        Map.entry(Material.IRON_ORE,           "Iron"),
        Map.entry(Material.DEEPSLATE_IRON_ORE,  "Deepslate Iron"),
        Map.entry(Material.COPPER_ORE,          "Copper"),
        Map.entry(Material.DEEPSLATE_COPPER_ORE,"Deepslate Copper"),
        Map.entry(Material.GOLD_ORE,            "Gold"),
        Map.entry(Material.DEEPSLATE_GOLD_ORE,  "Deepslate Gold"),
        Map.entry(Material.REDSTONE_ORE,        "Redstone"),
        Map.entry(Material.DEEPSLATE_REDSTONE_ORE, "Deepslate Redstone"),
        Map.entry(Material.LAPIS_ORE,           "Lapis Lazuli"),
        Map.entry(Material.DEEPSLATE_LAPIS_ORE,  "Deepslate Lapis"),
        Map.entry(Material.DIAMOND_ORE,         "Diamond"),
        Map.entry(Material.DEEPSLATE_DIAMOND_ORE,"Deepslate Diamond"),
        Map.entry(Material.EMERALD_ORE,         "Emerald"),
        Map.entry(Material.DEEPSLATE_EMERALD_ORE,"Deepslate Emerald"),
        Map.entry(Material.NETHER_GOLD_ORE,     "Nether Gold"),
        Map.entry(Material.NETHER_QUARTZ_ORE,   "Nether Quartz"),
        Map.entry(Material.ANCIENT_DEBRIS,      "Ancient Debris")
    ));

    /**
     * Выполняет команду /mp ccores.
     *
     * @param sender отправитель команды (должен быть игроком)
     * @return true если команда обработана
     */
    public static boolean execute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }

        // Permission check
        if (!player.hasPermission("mcplugin.command.ccores")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to check chunk ores!</red>"));
            return true;
        }

        // Cooldown check
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUsed = cooldowns.get(uuid);
        if (lastUsed != null && (now - lastUsed) < COOLDOWN_MS) {
            long remaining = ((COOLDOWN_MS - (now - lastUsed)) / 1000) + 1;
            player.sendMessage(MessageUtil.parse(
                    "<red>⏳ Please wait </red><yellow>" + remaining + "</yellow><red> sec before using this again.</red>"));
            return true;
        }

        // Scan chunk
        int chunkX = player.getChunk().getX();
        int chunkZ = player.getChunk().getZ();
        String worldName = player.getWorld().getName();

        player.sendMessage(MessageUtil.parse(
                "<gray>⛏ Scanning chunk [</gray><yellow>" + chunkX + ", " + chunkZ + "</yellow><gray>] in </gray><yellow>" + worldName + "</yellow><gray>...</gray>"));

        // Scan asynchronously on the main thread (world access must be sync)
        // Count ores
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        int totalOres = 0;
        int minY = player.getWorld().getMinHeight();
        int maxY = player.getWorld().getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = player.getWorld().getBlockAt(chunkX * 16 + x, y, chunkZ * 16 + z);
                    Material type = block.getType();
                    if (ORE_MATERIALS.containsKey(type)) {
                        counts.merge(type, 1, Integer::sum);
                        totalOres++;
                    }
                }
            }
        }

        // Set cooldown
        cooldowns.put(uuid, now);
        cleanupCooldowns();

        // Build result message
        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gold>=== <white>Chunk Ores</white> [<yellow>" + chunkX + ", " + chunkZ + "</yellow>] <gray>(" + worldName + ")</gray> ==="));
        player.sendMessage(MessageUtil.parse("<gray>Total ores found: <white>" + totalOres + "</white></gray>"));

        if (totalOres > 0) {
            player.sendMessage(Component.empty());
            for (Map.Entry<Material, String> entry : ORE_MATERIALS.entrySet()) {
                int count = counts.getOrDefault(entry.getKey(), 0);
                if (count > 0) {
                    player.sendMessage(MessageUtil.parse(
                            "  <gray>▪</gray> <white>" + entry.getValue() + "</white><gray>: <yellow>" + count + "</yellow></gray>"));
                }
            }
        }

        player.sendMessage(MessageUtil.parse("<gold>========================================"));
        return true;
    }

    /** Очищает устаревшие записи кулдауна (старее 1 минуты). */
    private static void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        // Очищаем раз в 30 секунд, чтобы не дёргать каждый раз
        if (now - lastCleanup < 30_000L) return;
        lastCleanup = now;
        cooldowns.entrySet().removeIf(e -> (now - e.getValue()) > 60_000L);
    }
}
