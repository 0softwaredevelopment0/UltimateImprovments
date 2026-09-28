package com.ultimateimprovments.datapack;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.nio.file.Path;

/**
 * UIDatapackBootstrap — registers the bundled UI-Datapack through the Paper
 * {@code DATAPACK_DISCOVERY} lifecycle event, i.e. <b>before the worlds (and their
 * datapacks) are loaded</b>.
 * <p>
 * This replaces the old "copy into {@code world/datapacks} then /datapack enable +
 * reload/restart" flow: the pack is extracted fresh from the plugin JAR on every
 * server start and discovered with {@code autoEnableOnServerStart(true)}, so it is
 * active from the first world load with no restart.
 */
public class UIDatapackBootstrap implements PluginBootstrap {

    /** Datapack id; Paper combines it with the plugin for the display name. */
    public static final String PACK_ID = "UI-Datapack";

    /**
     * The discovery event can fire several times (each time the server scans for
     * datapacks). The pack is extracted exactly once and the same path is
     * re-registered afterwards, so in-use datapack files are never rewritten.
     */
    private Path preparedPack;

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                if (preparedPack == null) {
                    preparedPack = DatapackPackBootstrap.prepare(context.getLogger());
                }
                if (preparedPack == null) {
                    return; // master toggle off — nothing to register
                }
                event.registrar().discoverPack(preparedPack, PACK_ID, configurer -> configurer
                        .autoEnableOnServerStart(true)
                        .position(true, Datapack.Position.TOP));
                context.getLogger().info("[UI-Datapack] Registered bundled datapack '" + PACK_ID
                        + "' for discovery (auto-enabled on start).");
            } catch (Throwable t) {
                context.getLogger().error("[UI-Datapack] Failed to register the bundled datapack: "
                        + t.getMessage(), t);
            }
        });
    }
}
