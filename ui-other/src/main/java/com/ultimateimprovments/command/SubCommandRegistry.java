package com.ultimateimprovments.command;

import com.ultimateimprovments.config.MessagesManager;
import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/*** Registry of /ui subcommands.
     * <p>
     * Stores a name → SubCommand map and provides methods for dispatch and tab-complete.
     * When adding a new subcommand it is enough to register it here —
     * execute and tabComplete are picked up automatically.
 */
public class SubCommandRegistry {

    private static SubCommandRegistry instance;
    private final Map<String, SubCommand> commands = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public static SubCommandRegistry getInstance() {
        if (instance == null) {
            instance = new SubCommandRegistry();
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    /**
     * Registers a subcommand.
     */
    public void register(SubCommand cmd) {
        String name = cmd.getName().toLowerCase();
        commands.put(name, cmd);
        for (String alias : cmd.getAliases()) {
            aliases.put(alias.toLowerCase(), name);
        }
    }

    /**
     * Returns true if at least one command is registered.
     */
    public boolean isEmpty() {
        return commands.isEmpty();
    }

    /**
     * Returns ALL command names (canonical + aliases) in registration order.
     * Used by /ui help for the full list.
     */
    public Set<String> getAllCommandNames() {
        Set<String> names = new LinkedHashSet<>(commands.keySet());
        names.addAll(aliases.keySet());
        return names;
    }

    /**
     * Returns the canonical name for a command (or its alias).
     *
     * @return the canonical name, or null if the command was not found
     */
    public String resolveName(String name) {
        String lower = name.toLowerCase();
        if (commands.containsKey(lower)) return lower;
        String resolved = aliases.get(lower);
        if (resolved != null) return resolved;
        return null;
    }

    /**
     * Dispatches a subcommand.
     *
     * @param sender the sender
     * @param args   the arguments (args[0] — the subcommand name)
     * @return true if the command was handled
     */
    public boolean dispatch(CommandSender sender, String[] args) {
        // Each subcommand checks its own ui.command.<name> permission itself.
        // There is no global ui gate anymore — otherwise player-granted point permissions would not work.
        if (args.length == 0) {
            String version = Main.getInstance().getDescription().getVersion();
            String msg = MessagesManager.getString("general.no_args",
                    "<white>Running <yellow>UltimateImprovments <gray>v<white>%version%\n"
                            + "<white>Type <yellow>/ui help <white> to view commands list.");
            sender.sendMessage(MessageUtil.parse(msg.replace("%version%", version)));
            return true;
        }

        String sub = args[0].toLowerCase();
        SubCommand cmd = findCommand(sub);

        if (cmd == null) {
            sender.sendMessage(MessageUtil.parse(MessagesManager.getString(
                    "general.unknown_command",
                    "<red>❌ Unknown command! </red><gray>Use </gray><white>/ui help</white><gray> for the command list.</gray>")));
            return true;
        }

        return cmd.execute(sender, args);
    }

    /**
     * Returns tab-complete suggestions.
     * <p>
     * First tries the subcommand's custom tabComplete().
     * If the subcommand suggested nothing — falls back to online player names.
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            // First level — names of all registered commands
            String partial = args.length == 0 ? "" : args[0].toLowerCase();
            return commands.keySet().stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        // Then — delegate to the specific subcommand
        String sub = args[0].toLowerCase();
        SubCommand cmd = findCommand(sub);
        if (cmd != null) {
            List<String> result = cmd.tabComplete(sender, args);
            if (result != null && !result.isEmpty()) {
                String last = args[args.length - 1].toLowerCase();
                return result.stream()
                        .filter(s -> s.toLowerCase().startsWith(last))
                        .collect(Collectors.toList());
            }
        }

        // Fallback: online player names (covers 80% of old cases)
        String last = args[args.length - 1].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(last))
                .collect(Collectors.toList());
    }

    private SubCommand findCommand(String name) {
        String lower = name.toLowerCase();
        SubCommand cmd = commands.get(lower);
        if (cmd != null) return cmd;

        String resolved = aliases.get(lower);
        if (resolved != null) return commands.get(resolved);

        return null;
    }
}
