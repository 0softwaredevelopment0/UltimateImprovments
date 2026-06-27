package com.mcplugin.infrastructure.listeners;

import com.mcplugin.infrastructure.core.Main;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ServerBrandListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        FileConfiguration config = Main.getInstance().getConfig();

        // =========================
        // FEATURE TOGGLE
        // =========================
        if (!config.getBoolean("brand_spoof.enabled", false)) {
            return;
        }

        Player player = event.getPlayer();

        // =========================
        // PERMISSION CHECK — skip if player can see the real brand
        // =========================
        if (player.hasPermission("mcplugin.show.brand")) {
            return;
        }

        try {
            CraftPlayer craftPlayer = (CraftPlayer) player;

            // Read custom brand string from config, default to empty string
            String customBrand = config.getString("brand_spoof.custom_brand", "");
            if (customBrand == null) customBrand = "";

            ClientboundCustomPayloadPacket packet =
                    new ClientboundCustomPayloadPacket(
                            new BrandPayload(customBrand)
                    );

            craftPlayer.getHandle().connection.send(packet);

        } catch (Exception e) {
            Main.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                    "[MCPLUGIN] Failed to spoof brand for " + player.getName(), e);
        }
    }
}