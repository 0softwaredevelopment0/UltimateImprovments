package com.ultimateimprovments.energy.generation.reactor;

import com.ultimateimprovments.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Chat output of the reactor statistics box.
 * <p>
 * Shown when a player right-clicks the reactor status sign (ReactorListener)
 * and replaces the legacy {@code /ui str dfc stats} command.
 */
public final class ReactorStatsDisplay {

    private ReactorStatsDisplay() {}

    /** Sends the reactor stats box to the player if an active reactor is nearby. */
    public static void sendStats(Player player) {
        ReactorManager reactor = ReactorManager.getInstance();
        if (reactor == null || ReactorManager.getReactors().isEmpty()) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>No active reactors found."));
            return;
        }

        Location playerLoc = player.getLocation();
        // Nearest valid reactor (multi-reactor support)
        ReactorManager nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (ReactorManager r : ReactorManager.getReactors()) {
            if (!r.isValid() || r.getReactorLocation() == null) continue;
            if (!playerLoc.getWorld().equals(r.getReactorLocation().getWorld())) continue;
            double d = playerLoc.distance(r.getReactorLocation());
            if (d < bestDist) { bestDist = d; nearest = r; }
        }
        if (nearest == null) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>No active reactor nearby."));
            return;
        }
        reactor = nearest;

        Location reactorLoc = reactor.getReactorLocation();
        if (reactorLoc == null || !playerLoc.getWorld().equals(reactorLoc.getWorld())) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>No active reactor nearby."));
            return;
        }

        double distance = playerLoc.distance(reactorLoc);
        if (distance > 50) {
            player.sendMessage(MessageUtil.parse("<dark_red>❌ <red>Error: <gray>No active reactor nearby (closest one is <white>"
                    + String.format("%.1f", distance) + "<gray>m)."));
            return;
        }

        String status;
        if (reactor.isMeltdownCountdown()) status = "<dark_red>!!! <red>EXPLOSION IMMINENT <dark_red>!!!";
        else if (reactor.getCoreShInt() < 100 || reactor.getCoreCaseInt() < 100) status = "<yellow>Degradation";
        else status = "<green>Normal";

        int meltdownSecs = reactor.isMeltdownCountdown() ? (reactor.getMeltdownTimer() / 20) : 0;

        String press = String.format("%.3f", reactor.getDisplayShieldPress());
        String spin = String.format("%.2f", reactor.getDisplayCoreSpin());

        player.sendMessage(MessageUtil.parse("<dark_gray>┌────────────────────────────────┐"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_red>D.F.C <dark_gray>» <white>Reactor statistics"));
        player.sendMessage(MessageUtil.parse("<dark_gray>├────────────────────────────────┤"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>ID: <white>" + reactor.getReactorId()));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Status: " + status));
        if (reactor.isMeltdownCountdown()) player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Detonation: <red>" + meltdownSecs + " s"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Distance: <white>" + String.format("%.1f", distance) + " m"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gold>═[ <yellow>Core data <gold>]═"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Temperature: <white>" + reactor.getDisplayCoreTemp() + " C*"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Pressure:    <white>" + press + " MPa"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Spin:        <white>" + spin + " RPS"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Integrity:   <white>" + reactor.getDisplayCoreShInt() + " %"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_aqua>═[ <aqua>Case data <dark_aqua>]═"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Temperature: <white>" + reactor.getDisplayCoreCaseTemp() + " C*"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Pressure:    <white>" + String.format("%.3f", reactor.getDisplayCoreCasePress() / 1000.0) + " MPa"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Integrity:   <white>" + reactor.getDisplayCoreCaseInt() + " %"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <dark_purple>═[ <light_purple>Fusion data <dark_purple>]═"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Speed:      <white>" + String.format("%.0f", reactor.getFusion().getSpeedPct()) + " %"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Particles:  <white>" + (int) reactor.getFusion().getParticles()));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Wear:       <white>" + reactor.getDisplayReactorWear() + " %"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Output:     <white>" + reactor.getDisplayEnergyRate() + " E/s"));
        player.sendMessage(MessageUtil.parse("<dark_gray>│ <gray>Position: <white>" + reactorLoc.getBlockX() + " " + reactorLoc.getBlockY() + " " + reactorLoc.getBlockZ()));
        player.sendMessage(MessageUtil.parse("<dark_gray>└────────────────────────────────┘"));
    }
}
