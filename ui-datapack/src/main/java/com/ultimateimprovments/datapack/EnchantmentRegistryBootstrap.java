package com.ultimateimprovments.datapack;

import com.moandjiezana.toml.Toml;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * EnchantmentRegistryBootstrap — registers the 18 custom enchantments through Paper's
 * experimental {@code RegistryEvents} API instead of shipping
 * {@code data/ui/enchantment/*.json} in the bundled datapack.
 * <p>
 * Why: the datapack is extracted and discovered at server start, so a malformed
 * enchantment only showed up as a warning in the log and a silently missing entry,
 * while plugin code kept referencing {@code ui:<id>}. Registering in the bootstrap phase
 * makes the server validate every builder before it accepts a world, and the registry is
 * the same one the client receives — no file copying involved.
 * <p>
 * Two consequences worth knowing:
 * <ul>
 *   <li><b>The display name is baked in per server, not per player.</b> A tooltip line
 *       comes from the registry entry, which the server sends once for everyone. Only a
 *       resource pack could localise it per player, so the name follows the server-wide
 *       {@code datapack.lang} / {@code messages.lang} resolution in {@link PackLanguage} —
 *       exactly the language the old extracted pack was picked for. Changing it needs a
 *       restart, since bootstrap runs once.</li>
 *   <li><b>Keys are unchanged</b> ({@code ui:aoe}, {@code ui:veinminer}, ...), so items
 *       already carrying these enchantments keep working; no migration pass over
 *       inventories, ender chests or stored player data is needed.</li>
 * </ul>
 * Registration is skipped entirely when {@code datapack.modules.enchantments} is false,
 * mirroring the old behaviour where the module toggle kept the enchantment files out of
 * the extracted pack. The three entries that still reference a {@code ui:*} item tag are
 * skipped (with a warning) when {@code datapack.enabled} is false, because those tag
 * files live in the pack.
 */
final class EnchantmentRegistryBootstrap {

    private EnchantmentRegistryBootstrap() {}

    /** Registers the compose handler; must be called from {@link UIDatapackBootstrap}. */
    static void register(BootstrapContext context) {
        ComponentLogger log = context.getLogger();
        File shared = new File(new File("plugins"), "UltimateImprovments");
        Settings settings = Settings.read(new File(new File(shared, "configs"), "UI-Datapack.toml"), log);

        if (!settings.enchantments()) {
            log.info("datapack.modules.enchantments=false — custom enchantments are not registered.");
            return;
        }

        String lang = PackLanguage.resolve(settings.configuredLang(), shared, log);
        boolean russian = PackLanguage.RU.equals(lang);
        log.info("Custom enchantments will be registered through RegistryEvents (language: "
                + lang + ", " + EnchantmentDefinition.all().size() + " entries).");

        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose(),
                event -> compose(event, russian, settings.datapackEnabled(), log));
    }

    /**
     * Fills the enchantment registry. One failing entry is logged and skipped instead of
     * aborting the whole server: the bootstrap phase turns an uncaught exception into a
     * shutdown before any world loads, so a single bad item key must not take everything
     * down with it.
     */
    private static void compose(RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
                                boolean russian, boolean datapackEnabled, ComponentLogger log) {
        int registered = 0;
        List<String> skipped = new ArrayList<>();

        for (EnchantmentDefinition definition : EnchantmentDefinition.all()) {
            if (definition.itemTag() != null && !datapackEnabled) {
                // Its item tag ships inside the bundled datapack, which is switched off.
                skipped.add(definition.id());
                continue;
            }
            try {
                register(event, definition, russian);
                registered++;
            } catch (Throwable t) {
                log.error("Failed to register the 'ui:" + definition.id()
                        + "' enchantment — skipping it.", t);
                skipped.add(definition.id());
            }
        }

        String message = "Registered " + registered + " custom enchantment(s) through RegistryEvents";
        if (skipped.isEmpty()) {
            log.info(message + ".");
        } else {
            log.warn(message + "; skipped: " + String.join(", ", skipped) + ".");
        }
    }

    private static void register(RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
                                 EnchantmentDefinition definition, boolean russian) {
        RegistryKeySet<ItemType> items = definition.itemTag() != null
                ? event.getOrCreateTag(definition.itemTag())
                : RegistrySet.keySet(RegistryKey.ITEM, itemKeys(definition.items()));

        event.registry().register(EnchantmentKeys.create(Key.key("ui", definition.id())),
                builder -> builder
                        .description(Component.text(russian ? definition.nameRu() : definition.nameEn()))
                        .supportedItems(items)
                        .primaryItems(items)
                        .weight(EnchantmentDefinition.WEIGHT)
                        .maxLevel(definition.maxLevel())
                        .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(
                                EnchantmentDefinition.MIN_COST_BASE,
                                EnchantmentDefinition.MIN_COST_PER_LEVEL))
                        .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(
                                EnchantmentDefinition.MAX_COST_BASE,
                                EnchantmentDefinition.MAX_COST_PER_LEVEL))
                        .anvilCost(definition.anvilCost())
                        .activeSlots(definition.activeSlots()));
    }

    private static List<TypedKey<ItemType>> itemKeys(List<String> ids) {
        List<TypedKey<ItemType>> keys = new ArrayList<>(ids.size());
        for (String id : ids) keys.add(TypedKey.create(RegistryKey.ITEM, Key.key(id)));
        return keys;
    }

    /**
     * The subset of {@code UI-Datapack.toml} this class cares about. Read directly with
     * toml4j because the bootstrap runs long before UI-Core (and its config layer) exists.
     */
    private record Settings(boolean datapackEnabled, boolean enchantments, String configuredLang) {

        static Settings read(File configFile, ComponentLogger log) {
            boolean datapackEnabled = true;
            boolean enchantments = true;
            String configuredLang = "auto";

            if (configFile.isFile()) {
                try {
                    Toml toml = new Toml().read(configFile);
                    datapackEnabled = !Boolean.FALSE.equals(toml.getBoolean("datapack.enabled", true));
                    configuredLang = toml.getString("datapack.lang", "auto");
                    Toml modules = toml.getTable("datapack.modules");
                    if (modules != null) {
                        enchantments = !Boolean.FALSE.equals(modules.getBoolean("enchantments", true));
                    }
                } catch (Exception e) {
                    log.warn("Could not read " + configFile + " (" + e.getMessage()
                            + ") — registering every custom enchantment (defaults).");
                }
            }
            return new Settings(datapackEnabled, enchantments, configuredLang);
        }
    }
}
