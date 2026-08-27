package com.ultimateimprovments.listener;

import com.ultimateimprovments.core.Main;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Hides/replaces the server brand (Leaf) for players without the ui.show.brand permission.
 * <p>
 * The brand is sent by the server during the configuration phase (before PlayerJoinEvent).
 * We send the replacement after a 1-tick delay so the packet arrives AFTER
 * the client processed all configuration packets.
 * Also re-sent on world change and respawn (some clients reset the brand).
 */
public class ServerBrandListener implements Listener {

    private static final String DEFAULT_SPOOFED_BRAND = "Paper";

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleBrandSpoof(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleBrandSpoof(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleBrandSpoof(event.getPlayer());
    }

    /**
     * Schedules the brand replacement after 1 tick if the player lacks the show permission.
     */
    private void scheduleBrandSpoof(Player player) {
        FileConfiguration config = Main.getInstance().getConfig();

        // =========================
        // FEATURE TOGGLE
        // =========================
        if (!config.getBoolean("brand_spoof.enabled", false)) {
            return;
        }

        // =========================
        // PERMISSION CHECK — skip if player can see the real brand
        // =========================
        if (player.hasPermission("ui.show.brand")) {
            return;
        }

        // 1-tick delay — so the client definitely processed all configuration packets
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!player.isOnline()) return;
            sendBrandPacket(player);
        }, 1L);
    }

    /**
     * Sends the brand replacement packet directly to the player.
     */
    private void sendBrandPacket(Player player) {
        try {
            FileConfiguration config = Main.getInstance().getConfig();
            CraftPlayer craftPlayer = (CraftPlayer) player;

            // Read custom brand string from config, default to "Paper"
            String customBrand = config.getString("brand_spoof.custom_brand", DEFAULT_SPOOFED_BRAND);
            if (customBrand == null || customBrand.isEmpty()) {
                customBrand = DEFAULT_SPOOFED_BRAND;
            }

            ClientboundCustomPayloadPacket packet =
                    new ClientboundCustomPayloadPacket(
                            new BrandPayload(customBrand)
                    );

            craftPlayer.getHandle().connection.send(packet);

        } catch (Exception e) {
            Main.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                    "[ULTIMATEIMPROVMENTS] Failed to spoof brand for " + player.getName(), e);
        }
    }
}