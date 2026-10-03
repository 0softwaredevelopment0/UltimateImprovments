package com.ultimateimprovments.command.subcommands;

import com.ultimateimprovments.command.CommandErrors;

import com.ultimateimprovments.command.home.HomeCommand;
import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.hooks.CoreHooks;
import com.ultimateimprovments.database.PlayerSettingsDB;
import com.ultimateimprovments.mechanics.environment.radiation.RadiationManager;
import com.ultimateimprovments.util.Materials;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;

public final class MiscSubcommand {

    private MiscSubcommand() {}

    // =========================
    // VANISH
    // =========================
    public static boolean vanish(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("ui.command.vanish")) {
            CommandErrors.noPermission(p, "ui.command.vanish"); return true;
        }
        if (args.length < 2) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_usage", "<red>❌ Usage: </red><white>/ui vanish <nick></white>"))); return true; }
        String targetName = args[1];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_player_not_found", "<red>❌ Player</red> <yellow>%player%</yellow> <red>not found!</red>").replace("%player%", targetName))); return true;
        }
        UUID uuid = target.getUniqueId();
        CoreHooks.toggleVanish(target);
        boolean isVanished = CoreHooks.isVanished(uuid);
        if (isVanished) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_enabled", "<green>✔</green> <white>Player</white> <yellow>%player%</yellow> <white>is now hidden (vanished).</white>").replace("%player%", targetName)));
            if (!target.isOnline()) sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_offline_hint", "<gray>Player is offline — vanish will apply on next login.</gray>")));
        } else {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.vanish_disabled", "<red>❌</red> <white>Player</white> <yellow>%player%</yellow> <white>is no longer hidden.</white>").replace("%player%", targetName)));
        }
        return true;
    }

    // =========================
    // NOTES
    // =========================
    public static boolean notes(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.notes_player_only", "<red>❌ Only players can use notes!</red>"))); return true; }
        if (!player.hasPermission("ui.command.notes")) { CommandErrors.noPermission(player, "ui.command.notes"); return true; }
        CoreHooks.openNotesGui(player);
        return true;
    }

    // =========================
    // SHOWSPEED — minecart speed display, /ui showspeed <on|off>
    // =========================
    private static final String PERM_SHOWSPEED = "ui.command.showspeed";

    public static boolean showSpeed(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { CommandErrors.playerOnly(sender); return true; }
        if (!player.hasPermission(PERM_SHOWSPEED)) { CommandErrors.noPermission(player, PERM_SHOWSPEED); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui showspeed"))); return true; }
        UUID uuid = player.getUniqueId();
        if (CoreHooks.isMinecartSpeedDisplayEnabled(uuid) != want) {
            CoreHooks.toggleMinecartSpeedDisplay(uuid);
        }
        if (want) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_enabled", "<green>⚡</green> <white>Speed display: </white><green>ON</green>")));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.speed_disabled", "<red>⚡</red> <white>Speed display: </white><red>OFF</red>")));
        }
        return true;
    }

    // =========================
    // ELYTRABOOST — elytra boost on jump, /ui elytraboost <on|off> (default OFF)
    // =========================
    private static final String PERM_ELYTRABOOST = "ui.command.elytraboost";

    public static boolean elytraBoost(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { CommandErrors.playerOnly(sender); return true; }
        if (!player.hasPermission(PERM_ELYTRABOOST)) { CommandErrors.noPermission(player, PERM_ELYTRABOOST); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui elytraboost"))); return true; }
        UUID uuid = player.getUniqueId();
        boolean nowEnabled = CoreHooks.isElytraBoostEnabled(uuid);
        if (nowEnabled != want) {
            CoreHooks.toggleElytraBoost(uuid);
        }
        if (want) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.elytra_enabled", "<green>✦</green> <white>Elytra boost on jump: </white><green>ON</green>")));
        } else {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("misc.elytra_disabled", "<red>✦</red> <white>Elytra boost on jump: </white><red>OFF</red>")));
        }
        return true;
    }

    // =========================
    // RADVIEW — /ui radview <on|off> (admin radiation overlay)
    // =========================
    private static final String PERM_RADVIEW = "ui.command.radview";

    public static boolean radview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { CommandErrors.playerOnly(sender); return true; }
        if (!player.hasPermission(PERM_RADVIEW)) { CommandErrors.noPermission(player, PERM_RADVIEW); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) {
            player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage",
                    "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui radview")));
            return true;
        }
        RadiationManager.setRadView(player, want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>☢</green> <white>Admin radiation view: </white><green>ON</green> <gray>(dosimeter readout overridden, marked with <red>*</red>)</gray>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>☢</red> <white>Admin radiation view: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // (toggleautocraft removed — replaced by /ui craftrecipe)
    // =========================

    // =========================
    // BOSSBAR — /ui bossbar <on|off> (default ON; config gate: bossbar.enabled)
    // =========================
    private static final String PERM_BOSSBAR = "ui.command.bossbar";

    public static boolean bossbar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { CommandErrors.playerOnly(sender); return true; }
        if (!player.hasPermission(PERM_BOSSBAR)) { CommandErrors.noPermission(player, PERM_BOSSBAR); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui bossbar"))); return true; }
        if (want && !com.ultimateimprovments.core.Main.getInstance().getConfig().getBoolean("bossbar.enabled", false)) {
            CommandErrors.moduleDisabled(player, "bossbar.enabled");
            return true;
        }
        PlayerSettingsDB.setBossbarEnabled(player.getUniqueId(), want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>BossBar: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌</red> <white>BossBar: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // PINGSOUND — /ui pingsound <on|off> (default ON; config gate: chat_ping.enabled)
    // =========================
    private static final String PERM_PINGSOUND = "ui.command.pingsound";

    public static boolean pingsound(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { CommandErrors.playerOnly(sender); return true; }
        if (!player.hasPermission(PERM_PINGSOUND)) { CommandErrors.noPermission(player, PERM_PINGSOUND); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui pingsound"))); return true; }
        if (want && !com.ultimateimprovments.core.Main.getInstance().getConfig().getBoolean("chat_ping.enabled", true)) {
            CommandErrors.moduleDisabled(player, "chat_ping.enabled");
            return true;
        }
        PlayerSettingsDB.setPingEnabled(player.getUniqueId(), want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>🔔</green> <white>Ping sound: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>🔕</red> <white>Ping sound: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // SCOREBOARD — /ui scoreboard <on|off> (default ON; config gate: scoreboard.enabled)
    // =========================
    private static final String PERM_SCOREBOARD = "ui.command.scoreboard";

    public static boolean scoreboard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { CommandErrors.playerOnly(sender); return true; }
        if (!player.hasPermission(PERM_SCOREBOARD)) { CommandErrors.noPermission(player, PERM_SCOREBOARD); return true; }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui scoreboard"))); return true; }
        if (want && !com.ultimateimprovments.core.Main.getInstance().getConfig().getBoolean("scoreboard.enabled", false)) {
            CommandErrors.moduleDisabled(player, "scoreboard.enabled");
            return true;
        }
        PlayerSettingsDB.setScoreboardEnabled(player.getUniqueId(), want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Scoreboard: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌</red> <white>Scoreboard: </white><red>OFF</red>"));
        }
        return true;
    }

    // =========================
    // WIRELESSBIND — /ui wirelessbind <on|off> (default OFF)
    // =========================
    private static final String PERM_WIRELESSBIND = "ui.command.wirelessbind";

    public static boolean wirelessbind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (!player.hasPermission(PERM_WIRELESSBIND)) {
            CommandErrors.noPermission(player, PERM_WIRELESSBIND);
            return true;
        }
        Boolean want = parseOnOff(args);
        if (want == null) { player.sendMessage(MessageUtil.parse(MessagesManager.getString("general.on_off_usage", "<red>❌ Usage: </red><white>/%cmd% <on|off></white>").replace("%cmd%", "ui wirelessbind"))); return true; }
        PlayerSettingsDB.setWirelessBindEnabled(player.getUniqueId(), want);
        if (want) {
            player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Wireless redstone binding: </white><green>ON</green>"));
        } else {
            player.sendMessage(MessageUtil.parse("<red>❌</red> <white>Wireless redstone binding: </white><red>OFF</red>"));
        }
        return true;
    }

    /** Parses args[1] as on/off. Returns null when missing/invalid. */
    private static Boolean parseOnOff(String[] args) {
        if (args.length < 2) return null;
        return switch (args[1].toLowerCase()) {
            case "on", "enable", "true", "1" -> Boolean.TRUE;
            case "off", "disable", "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    // =========================
    // HOME (delegates to HomeCommand)
    // =========================
    public static boolean home(CommandSender sender, String[] args) {
        return HomeCommand.dispatch(sender, args);
    }

    // =========================
    // FLY — enables/disables flight (even in survival)
    // /ui fly on|off [player]
    // =========================
    public static boolean fly(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui fly <on|off> [player]</white>"));
            return true;
        }

        boolean enable;
        switch (args[1].toLowerCase()) {
            case "on" -> enable = true;
            case "off" -> enable = false;
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui fly <on|off> [player]</white>"));
                return true;
            }
        }

        Player target;
        if (args.length >= 3) {
            // Apply to another player
            if (!sender.hasPermission("ui.command.fly.other")) {
                CommandErrors.noPermission(sender, "ui.command.fly.other");
                return true;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[2] + "</yellow> <red>not found!</red>"));
                return true;
            }
        } else {
            // Apply to self
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command on themselves!</red>"));
                return true;
            }
            if (!player.hasPermission("ui.command.fly")) {
                CommandErrors.noPermission(player, "ui.command.fly");
                return true;
            }
            target = player;
        }

        target.setAllowFlight(enable);
        target.setFlying(enable);

        String state = enable ? "<green>ON</green>" : "<red>OFF</red>";
        String msg = "<green>✔</green> <white>Flight for</white> <yellow>" + target.getName() + "</yellow> <white>:</white> " + state;
        sender.sendMessage(MessageUtil.parse(msg));
        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse("<white>Your flight has been toggled:</white> " + state));
        }
        return true;
    }

    // =========================
    // GOD — enables/disables invulnerability
    // /ui god on|off [player]
    // =========================
    public static boolean god(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui god <on|off> [player]</white>"));
            return true;
        }

        boolean enable;
        switch (args[1].toLowerCase()) {
            case "on" -> enable = true;
            case "off" -> enable = false;
            default -> {
                sender.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui god <on|off> [player]</white>"));
                return true;
            }
        }

        Player target;
        if (args.length >= 3) {
            // Apply to another player
            if (!sender.hasPermission("ui.command.god.other")) {
                CommandErrors.noPermission(sender, "ui.command.god.other");
                return true;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Player</red> <yellow>" + args[2] + "</yellow> <red>not found!</red>"));
                return true;
            }
        } else {
            // Apply to self
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.parse("<red>❌ Only players can use this command on themselves!</red>"));
                return true;
            }
            if (!player.hasPermission("ui.command.god")) {
                CommandErrors.noPermission(player, "ui.command.god");
                return true;
            }
            target = player;
        }

        target.setInvulnerable(enable);

        // Guarantees the flag is really gone from the player's persisted data
        // (player.dat / entity NBT — what `/data get entity <nick> Invulnerable`
        // shows), not just from the live entity object. setInvulnerable(false)
        // clears the NBT "Invulnerable" tag on the entity; saveData() persists
        // the cleared value to player.dat immediately, so a broken "god off"
        // can never leave a permanently immortal player after relogs/restarts.
        if (!enable && target instanceof org.bukkit.craftbukkit.entity.CraftPlayer craftPlayer) {
            try {
                // Paper 26.3: NMS Entity#setInvulnerable no longer exists. The
                // Bukkit setInvulnerable(enable) above already cleared the flag
                // on the entity handle (the same NBT "Invulnerable" tag);
                // saveData() persists the cleared value to player.dat
                // immediately, so a broken "god off" can never leave a
                // permanently immortal player after relogs/restarts.
                craftPlayer.saveData();
            } catch (Throwable t) {
                org.bukkit.Bukkit.getLogger().warning("[God] NBT Invulnerable reset failed for "
                        + target.getName() + ": " + t.getMessage());
            }
        }

        String state = enable ? "<green>ON</green>" : "<red>OFF</red>";
        String msg = "<green>✔</green> <white>God mode for</white> <yellow>" + target.getName() + "</yellow> <white>:</white> " + state;
        sender.sendMessage(MessageUtil.parse(msg));
        if (!sender.equals(target)) {
            target.sendMessage(MessageUtil.parse("<white>Your god mode has been toggled:</white> " + state));
        }
        return true;
    }

    // =========================
    // UNLOCK BOOK — converts a signed book into a book-and-quill
    // =========================
    public static boolean unlockBook(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (!player.hasPermission("ui.command.unlock")) {
            CommandErrors.noPermission(player, "ui.command.unlock");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            player.sendMessage(MessageUtil.parse("<red>❌ You must hold a signed book in your hand!</red>"));
            return true;
        }

        BookMeta oldMeta = (BookMeta) item.getItemMeta();
        if (oldMeta == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Failed to read book data!</red>"));
            return true;
        }

        // Copy pages from the signed book (List<String>) and create a book-and-quill
        var pages = oldMeta.pages();
        ItemStack newBook = new ItemStack(Materials.WRITABLE_BOOK, item.getAmount());
        BookMeta newMeta = (BookMeta) newBook.getItemMeta();
        if (newMeta == null) {
            player.sendMessage(MessageUtil.parse("<red>❌ Failed to create new book!</red>"));
            return true;
        }

        newMeta.pages(new java.util.ArrayList<>(pages));
        // Preserve everything a signed book can carry besides its pages
        // (anvil display name, lore, plugin PDC, enchantments, custom model
        // data) — the material changes, the data must not be lost.
        if (oldMeta.hasDisplayName()) newMeta.displayName(oldMeta.displayName());
        if (oldMeta.hasLore()) newMeta.lore(oldMeta.lore());
        oldMeta.getPersistentDataContainer().copyTo(newMeta.getPersistentDataContainer(), true);
        oldMeta.getEnchants().forEach((ench, level) -> newMeta.addEnchant(ench, level, true));
        if (oldMeta.hasCustomModelData()) newMeta.setCustomModelData(oldMeta.getCustomModelData());
        newBook.setItemMeta(newMeta);
        player.getInventory().setItemInMainHand(newBook);
        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Book unlocked! You can now edit it.</white>"));
        return true;
    }

    // =========================
    // UNLOCK SIGN — removes the waxed flag from a sign, keeping all other data
    // =========================
    public static boolean unlockSign(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            CommandErrors.playerOnly(sender);
            return true;
        }
        if (!player.hasPermission("ui.command.unlock")) {
            CommandErrors.noPermission(player, "ui.command.unlock");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR || !item.getType().name().endsWith("_SIGN")) {
            player.sendMessage(MessageUtil.parse("<red>❌ You must hold a sign in your hand!</red>"));
            return true;
        }

        // Modern vanilla keeps the sign text AND the waxed flag in the item's
        // block-state data, so the item must be edited IN PLACE: flip only the
        // waxed property. The old implementation replaced the item with a
        // freshly created one and copied just name/lore/PDC back — every other
        // component (the sign text above all) was destroyed.
        if (!(item.getItemMeta() instanceof BlockStateMeta blockMeta)
                || !(blockMeta.getBlockState() instanceof Sign signState)) {
            player.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>This sign carries no block data — it is already editable.</white>"));
            return true;
        }
        if (!signState.isWaxed()) {
            player.sendMessage(MessageUtil.parse(
                    "<yellow>⚠</yellow> <white>This sign is not waxed — nothing to unlock.</white>"));
            return true;
        }

        signState.setWaxed(false);
        blockMeta.setBlockState(signState);
        item.setItemMeta(blockMeta);
        player.getInventory().setItemInMainHand(item);
        player.sendMessage(MessageUtil.parse("<green>✔</green> <white>Sign unwaxed! You can now edit it after placing.</white>"));
        return true;
    }
}
