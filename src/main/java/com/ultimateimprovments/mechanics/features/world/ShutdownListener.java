package com.ultimateimprovments.mechanics.features.world;

import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * We're shutting down! — grants the {@code ui:datapack/were_shutting_down}
 * achievement to every player who is online when the server initiates a
 * shutdown. The Bukkit/Paper API has no shutdown event, so this is hooked
 * from {@link com.ultimateimprovments.core.Main#onDisable()} (which the
 * server calls when it starts shutting down, while players are still online).
 */
public final class ShutdownListener {

    /** The datapack advancement key (parent: {@code ui:datapack/server_overload}). */
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("ui", "datapack/were_shutting_down");

    private ShutdownListener() {}

    /** Grants the achievement to every currently online player. Idempotent. */
    public static void grantToAllOnline() {
        Advancement adv = Bukkit.getAdvancement(ADVANCEMENT);
        if (adv == null) return; // datapack not loaded yet

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                var progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.awardCriteria("1");
                }
            } catch (Exception e) {
                ConsoleLogger.warn("[ShutdownListener] Award error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }
}
