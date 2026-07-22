package com.ultimateimprovements.listener;

import com.ultimateimprovements.core.Main;
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
 * Скрывает/подменяет brand сервера (Leaf) для игроков без права mcplugin.show.brand.
 * <p>
 * Brand отправляется сервером на этапе конфигурации (до PlayerJoinEvent).
 * Отправляем подмену с задержкой в 1 тик, чтобы пакет пришёл ПОСЛЕ того,
 * как клиент обработал все пакеты конфигурации.
 * Также переотправляем при смене мира и респавне (некоторые клиенты сбрасывают brand).
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
     * Планирует отправку подмены brand через 1 тик, если игрок не имеет права на показ.
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
        if (player.hasPermission("mcplugin.show.brand")) {
            return;
        }

        // Задержка 1 тик — чтобы клиент точно обработал все пакеты конфигурации
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!player.isOnline()) return;
            sendBrandPacket(player);
        }, 1L);
    }

    /**
     * Отправляет пакет с подменой brand напрямую игроку.
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
                    "[MCPLUGIN] Failed to spoof brand for " + player.getName(), e);
        }
    }
}