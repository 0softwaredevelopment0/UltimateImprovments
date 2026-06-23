package com.mcplugin.infrastructure.listeners;

import com.mcplugin.infrastructure.core.Main;
import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;

import org.bukkit.Bukkit;

import org.bukkit.craftbukkit.entity.CraftPlayer;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ServerBrandListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        // =========================
        // PERMISSION CHECK
        // =========================
        if (player.hasPermission("mcplugin.show.brand")) {
            return;
        }

        try {

            CraftPlayer craftPlayer =
                    (CraftPlayer) player;

            ClientboundCustomPayloadPacket packet =
                    new ClientboundCustomPayloadPacket(
                            new BrandPayload("")
                    );

            craftPlayer.getHandle()
                    .connection
                    .send(packet);

        } catch (Exception e) {
            Main.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                    "[MCPLUGIN] Failed to spoof brand for " + player.getName(), e);
        }
    }
}