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
 * <p>
 * The extracted folder is <b>language-specific</b> ({@code UI-Datapack-en} or
 * {@code UI-Datapack-ru}, see {@link DatapackPackBootstrap}) and its name is used as
 * the pack id, so {@code /datapack list} shows which variant is active.
 */
public class UIDatapackBootstrap implements PluginBootstrap {

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
                // The folder name carries the language (UI-Datapack-en / UI-Datapack-ru).
                String packId = preparedPack.getFileName().toString();
                event.registrar().discoverPack(preparedPack, packId, configurer -> configurer
                        .autoEnableOnServerStart(true)
                        .position(true, Datapack.Position.TOP));
                context.getLogger().info("Registered bundled datapack '" + packId
                        + "' for discovery (auto-enabled on start).");
            } catch (Throwable t) {
                context.getLogger().error("Failed to register the bundled datapack: "
                        + t.getMessage(), t);
            }
        });
    }
}
