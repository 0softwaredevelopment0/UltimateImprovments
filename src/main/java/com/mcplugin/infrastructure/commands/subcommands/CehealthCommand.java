package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /mp cehealth — проверяет здоровье моба на которого смотрит игрок (макс. 2 блока).
 * <p>
 * Задержка: 10 секунд между использованиями.
 * Право: mcplugin.command.cehealth (по умолчанию true для всех).
 */
public class CehealthCommand {

    private CehealthCommand() {}

    private static final long COOLDOWN_MS = 10_000L;
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final int MAX_DISTANCE = 2;
    private static long lastCleanup = System.currentTimeMillis();

    /**
     * Выполняет команду /mp cehealth.
     */
    public static boolean execute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command!</red>"));
            return true;
        }

        if (!player.hasPermission("mcplugin.command.cehealth")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You don't have permission to check entity health!</red>"));
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

        // Find the entity the player is looking at (max 2 blocks)
        Entity target = player.getTargetEntity(MAX_DISTANCE);
        if (target == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ No entity found within </red><yellow>2</yellow><red> blocks!</red>"));
            // Set cooldown anyway to prevent spam
            cooldowns.put(uuid, now);
            cleanupCooldowns();
            return true;
        }

        if (!(target instanceof LivingEntity living)) {
            player.sendMessage(MessageUtil.parse("<red>❌ This entity has no health!</red>"));
            cooldowns.put(uuid, now);
            cleanupCooldowns();
            return true;
        }

        // Skip players
        if (target instanceof Player) {
            player.sendMessage(MessageUtil.parse("<red>❌ Cannot check other players' health!</red>"));
            cooldowns.put(uuid, now);
            cleanupCooldowns();
            return true;
        }

        double maxHealth = living.getMaxHealth();
        double currentHealth = living.getHealth();
        int hp = (int) Math.round(currentHealth);
        int maxHp = (int) Math.round(maxHealth);

        // Color based on health percentage
        String color;
        double pct = currentHealth / maxHealth;
        if (pct > 0.75) color = "<green>";
        else if (pct > 0.5) color = "<yellow>";
        else if (pct > 0.25) color = "<gold>";
        else color = "<red>";

        // Entity name
        String entityName = living.getType().name().toLowerCase().replace('_', ' ');
        // Capitalize first letter
        if (!entityName.isEmpty()) {
            entityName = Character.toUpperCase(entityName.charAt(0)) + entityName.substring(1);
        }
        if (living.getCustomName() != null) {
            entityName = living.getCustomName();
        }

        // Create health bar
        int barLength = 20;
        int filledBars = (int) Math.round(pct * barLength);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("<green>█</green>");
            } else {
                bar.append("<dark_gray>█</dark_gray>");
            }
        }

        // Set cooldown
        cooldowns.put(uuid, now);
        cleanupCooldowns();

        player.sendMessage(Component.empty());
        player.sendMessage(MessageUtil.parse("<gold>=== <white>Entity Health</white> ==="));
        player.sendMessage(MessageUtil.parse("<gray>Entity: </gray><white>" + entityName + "</white>"));
        player.sendMessage(MessageUtil.parse("<gray>Health: </gray>" + color + hp + "<reset><gray>/</gray>" + color + maxHp + "<reset>"));
        player.sendMessage(bar.toString());
        player.sendMessage(MessageUtil.parse("<gold>==========================="));
        return true;
    }

    private static void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < 30_000L) return;
        lastCleanup = now;
        cooldowns.entrySet().removeIf(e -> (now - e.getValue()) > 60_000L);
    }
}
