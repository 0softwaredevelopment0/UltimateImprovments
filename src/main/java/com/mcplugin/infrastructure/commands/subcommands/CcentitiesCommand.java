package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.entity.EntityType;

import java.util.*;

/**
 * /mp ccentities — проверяет сколько сущностей каждого типа в чанке игрока.
 * <p>
 * Задержка: 10 секунд между использованиями.
 * Право: mcplugin.command.ccentities (по умолчанию true для всех).
 */
public class CcentitiesCommand {

    private CcentitiesCommand() {}

    private static final long COOLDOWN_MS = 10_000L;
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static long lastCleanup = System.currentTimeMillis();

    /**
     * Выполняет команду /mp ccentities.
     */
    public static boolean execute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }

        if (!player.hasPermission("mcplugin.command.ccentities")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to check chunk entities!</red>"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUsed = cooldowns.get(uuid);
        if (lastUsed != null && (now - lastUsed) < COOLDOWN_MS) {
            long remaining = ((COOLDOWN_MS - (now - lastUsed)) / 1000) + 1;
            player.sendMessage(MessageUtil.parse(
                    "<red>⏳ Please wait </red><yellow>" + remaining + "</yellow><red> sec before using this again.</red>"));
            return true;
        }

        int chunkX = player.getChunk().getX();
        int chunkZ = player.getChunk().getZ();
        String worldName = player.getWorld().getName();

        player.sendMessage(MessageUtil.parse(
                "<gray>🔍 Scanning chunk [</gray><yellow>" + chunkX + ", " + chunkZ + "</yellow><gray>] in </gray><yellow>" + worldName + "</yellow><gray>...</gray>"));

        // Count entities in chunk
        Map<String, Integer> counts = new TreeMap<>();
        int totalEntities = 0;

        for (Entity entity : player.getChunk().getEntities()) {
            if (entity == player) continue; // skip self

            String typeName = getEntityCategory(entity);
            if (typeName == null) continue; // skip unknown

            counts.merge(typeName, 1, Integer::sum);
            totalEntities++;
        }

        // Set cooldown
        cooldowns.put(uuid, now);
        cleanupCooldowns();

        // Build result
        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gold>=== <white>Chunk Entities</white> [<yellow>" + chunkX + ", " + chunkZ + "</yellow>] <gray>(" + worldName + ")</gray> ==="));
        player.sendMessage(MessageUtil.parse("<gray>Total entities: <white>" + totalEntities + "</white></gray>"));

        if (totalEntities > 0) {
            player.sendMessage(Component.empty());
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                player.sendMessage(MessageUtil.parse(
                        "  <gray>▪</gray> <white>" + entry.getKey() + "</white><gray>: <yellow>" + entry.getValue() + "</yellow></gray>"));
            }
        }

        player.sendMessage(MessageUtil.parse("<gold>==========================================="));
        return true;
    }

    /**
     * Возвращает категорию для сущности, или null если её не стоит показывать.
     */
    private static String getEntityCategory(Entity entity) {
        // Handle problematic entity types via instanceof first
        // (their EntityType enum names vary between Paper API versions)
        if (entity instanceof Item) return null;
        if (entity instanceof org.bukkit.entity.ThrownPotion) return null;
        if (entity instanceof ChestBoat) return "Chest Boat";
        if (entity instanceof Boat) return "Boat";

        EntityType type = entity.getType();
        return switch (type) {
            // ===== Passive / Neutral Mobs =====
            case COW -> "Cow";
            case SHEEP -> "Sheep";
            case PIG -> "Pig";
            case CHICKEN -> "Chicken";
            case RABBIT -> "Rabbit";
            case WOLF -> "Wolf";
            case CAT -> "Cat";
            case HORSE -> "Horse";
            case DONKEY -> "Donkey";
            case MULE -> "Mule";
            case LLAMA -> "Llama";
            case FOX -> "Fox";
            case PANDA -> "Panda";
            case BEE -> "Bee";
            case GOAT -> "Goat";
            case FROG -> "Frog";
            case AXOLOTL -> "Axolotl";
            case TURTLE -> "Turtle";
            case DOLPHIN -> "Dolphin";
            case SQUID -> "Squid";
            case GLOW_SQUID -> "Glow Squid";
            case PARROT -> "Parrot";
            case OCELOT -> "Ocelot";
            case STRIDER -> "Strider";
            case CAMEL -> "Camel";
            case SNIFFER -> "Sniffer";
            case ARMADILLO -> "Armadillo";

            // ===== Hostile Mobs =====
            case ZOMBIE -> "Zombie";
            case SKELETON -> "Skeleton";
            case CREEPER -> "Creeper";
            case SPIDER -> "Spider";
            case ENDERMAN -> "Enderman";
            case WITCH -> "Witch";
            case SLIME -> "Slime";
            case MAGMA_CUBE -> "Magma Cube";
            case BLAZE -> "Blaze";
            case GHAST -> "Ghast";
            case ZOMBIFIED_PIGLIN -> "Zombified Piglin";
            case PIGLIN -> "Piglin";
            case PIGLIN_BRUTE -> "Piglin Brute";
            case HOGLIN -> "Hoglin";
            case ZOGLIN -> "Zoglin";
            case WITHER_SKELETON -> "Wither Skeleton";
            case STRAY -> "Stray";
            case HUSK -> "Husk";
            case DROWNED -> "Drowned";
            case PHANTOM -> "Phantom";
            case VEX -> "Vex";
            case VINDICATOR -> "Vindicator";
            case EVOKER -> "Evoker";
            case RAVAGER -> "Ravager";
            case PILLAGER -> "Pillager";
            case GUARDIAN -> "Guardian";
            case ELDER_GUARDIAN -> "Elder Guardian";
            case SHULKER -> "Shulker";
            case SILVERFISH -> "Silverfish";
            case ENDERMITE -> "Endermite";
            case CAVE_SPIDER -> "Cave Spider";
            case ZOMBIE_VILLAGER -> "Zombie Villager";
            case BREEZE -> "Breeze";

            // ===== Bosses =====
            case WITHER -> "Wither";
            case ENDER_DRAGON -> "Ender Dragon";

            // ===== Utility / Other =====
            case IRON_GOLEM -> "Iron Golem";
            case SNOW_GOLEM -> "Snow Golem";
            case ALLAY -> "Allay";

            // ===== Items / XP / Projectiles (skip) =====
            case EXPERIENCE_ORB -> null;
            case ARROW, SPECTRAL_ARROW, TRIDENT -> null;
            case FIREBALL, SMALL_FIREBALL, DRAGON_FIREBALL -> null;
            case FIREWORK_ROCKET -> null;
            case EGG, SNOWBALL, ENDER_PEARL -> null;
            case LLAMA_SPIT -> null;
            case FISHING_BOBBER -> null;

            // ===== Vehicles =====
            case MINECART -> "Minecart";
            case CHEST_MINECART -> "Chest Minecart";
            case HOPPER_MINECART -> "Hopper Minecart";
            case FURNACE_MINECART -> "Furnace Minecart";

            // ===== Players (skip all players) =====
            case PLAYER -> null;

            default -> {
                // Skip decorative/temp entities
                String name = type.name();
                if (name.contains("ARMOR_STAND") || name.contains("ITEM_FRAME")
                        || name.contains("LEASH") || name.contains("MARKER")
                        || name.contains("INTERACTION") || name.contains("DISPLAY")
                        || name.contains("TEXT_DISPLAY") || name.contains("BLOCK_DISPLAY")
                        || name.contains("ITEM_DISPLAY") || name.contains("FALLING_BLOCK")
                        || name.contains("AREA_EFFECT_CLOUD") || name.contains("LIGHTNING")
                        || name.contains("PAINTING") || name.contains("EVOKER_FANGS")
                        || name.contains("SHULKER_BULLET") || name.contains("WIND_CHARGE")
                        || name.contains("BREEZE_WIND_CHARGE")) {
                    yield null;
                }
                yield capitalize(name);
            }
        };
    }

    private static String capitalize(String name) {
        String lower = name.toLowerCase().replace('_', ' ');
        String[] words = lower.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < 30_000L) return;
        lastCleanup = now;
        cooldowns.entrySet().removeIf(e -> (now - e.getValue()) > 60_000L);
    }
}
