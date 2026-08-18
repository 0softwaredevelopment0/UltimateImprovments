package com.ultimateimprovments.command.subcommands;

/**
 * /ui endersee &lt;player&gt; — ender chest viewer/editor.
 *
 * <p>Online players open the real ender chest (Bukkit API), offline players open a
 * snapshot GUI backed by the {@code EnderItems} tag of their {@code <uuid>.dat} file.
 * All logic lives in {@link InvseeSubcommand}; this class only supplies the command
 * name, permission and the ender-chest flag.</p>
 */
public final class EnderseeSubcommand extends InvseeSubcommand {

    public EnderseeSubcommand() {
        super("endersee", "ui.command.endersee", true);
    }
}
