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
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemType;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
 * the extracted pack. No entry references a {@code ui:*} tag anymore, so the datapack
 * being disabled does not affect registration.
 * <p>
 * <b>Failure policy.</b> Bootstrap exceptions abort server startup, so nothing here is
 * allowed to propagate: each enchantment is registered inside its own {@code try/catch}
 * and a failure only skips that entry with an error line. The item-set union path reads
 * vanilla tags defensively as well and degrades to a single documented tag reference
 * instead of throwing (see {@link #resolveUnion}).
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
                event -> compose(event, russian, log));
    }

    /**
     * Fills the enchantment registry. One failing entry is logged and skipped instead of
     * aborting the whole server: the bootstrap phase turns an uncaught exception into a
     * shutdown before any world loads, so a single bad item key must not take everything
     * down with it.
     */
    private static void compose(RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
                                boolean russian, ComponentLogger log) {
        int registered = 0;
        List<String> skipped = new ArrayList<>();

        for (EnchantmentDefinition definition : EnchantmentDefinition.all()) {
            try {
                register(event, definition, russian, log);
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
                                 EnchantmentDefinition definition, boolean russian, ComponentLogger log) {
        RegistryKeySet<ItemType> items = resolveItems(event, definition, log);

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

    /** Turns the definition's item-set shape into something the builder accepts. */
    private static RegistryKeySet<ItemType> resolveItems(
            RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
            EnchantmentDefinition definition, ComponentLogger log) {

        return switch (definition.items()) {
            case EnchantmentDefinition.ItemSet.Inline inline ->
                    RegistrySet.keySet(RegistryKey.ITEM, itemKeys(inline.ids()));
            case EnchantmentDefinition.ItemSet.SingleTag single ->
                    event.getOrCreateTag(single.tag());
            case EnchantmentDefinition.ItemSet.Union union ->
                    resolveUnion(event, definition.id(), union, log);
        };
    }

    /**
     * Materialises the union of several vanilla tags plus explicit items, i.e. what the old
     * {@code ui:*_enchantable} datapack tags did.
     * <p>
     * Reading tag contents at compose time is <b>not</b> covered by the API contract:
     * {@code getOrCreateTag} documents that the returned tag "is then required to be
     * filled either from the built-in or custom datapack", so its values may still be empty
     * here, and {@code Registry.getTag} throws {@code NoSuchElementException} for an
     * unknown key. Both are handled:
     * <ul>
     *   <li>every tag read is attempted through two paths and inside its own
     *       {@code try/catch} ({@link #readTagKeys});</li>
     *   <li>if <b>any</b> tag comes back empty the union is not trusted at all — a partially
     *       filled set would silently narrow the enchantment — and the documented
     *       single-tag reference {@code fallbackTag} is used instead, which the game fills
     *       later. The enchantment still registers; only a few items (elytra, books, the
     *       {@code mining_loot} extras) may be missing, and a warning says so.</li>
     * </ul>
     */
    private static RegistryKeySet<ItemType> resolveUnion(
            RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
            String id, EnchantmentDefinition.ItemSet.Union union, ComponentLogger log) {

        Set<TypedKey<ItemType>> keys = new LinkedHashSet<>(itemKeys(union.extraItems()));
        int resolvedTags = 0;
        List<String> unreadable = new ArrayList<>();

        for (TagKey<ItemType> tagKey : union.tags()) {
            List<TypedKey<ItemType>> fromTag = readTagKeys(event, tagKey);
            if (fromTag.isEmpty()) {
                unreadable.add("#" + tagKey.key());
            } else {
                keys.addAll(fromTag);
                resolvedTags++;
            }
        }

        if (!unreadable.isEmpty()) {
            log.warn("ui:" + id + ": could not read vanilla item tag(s) " + String.join(", ", unreadable)
                    + " at compose time — falling back to the single tag #" + union.fallbackTag().key()
                    + ". The enchantment still works, but its item set is narrower than the old"
                    + " datapack tag until the vanilla tags become readable here.");
            return event.getOrCreateTag(union.fallbackTag());
        }

        if (keys.isEmpty()) {
            // Defensive: every tag was "readable" but reported no values, and no explicit
            // items exist. Registering an empty supported-items set would fail validation.
            throw new IllegalStateException("resolved item set is empty for ui:" + id);
        }

        log.info("ui:" + id + " item set: " + resolvedTags + "/" + union.tags().size()
                + " vanilla tag(s) + " + union.extraItems().size() + " explicit item(s) = "
                + keys.size() + " item(s).");
        return RegistrySet.keySet(RegistryKey.ITEM, List.copyOf(keys));
    }

    /**
     * Reads the keys of a vanilla item tag, trying the compose event's tag view first (the
     * same registry view Paper hands to the builder) and the static item registry second.
     * Returns an empty list when neither path yields values — callers decide what that
     * means, this method never throws.
     */
    private static List<TypedKey<ItemType>> readTagKeys(
            RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event,
            TagKey<ItemType> tagKey) {

        try {
            Tag<ItemType> tag = event.getOrCreateTag(tagKey);
            if (!tag.isEmpty()) return List.copyOf(tag.values());
        } catch (Throwable ignored) {
            // fall through to the static registry
        }
        try {
            if (Registry.ITEM.hasTag(tagKey)) {
                return List.copyOf(Registry.ITEM.getTag(tagKey).values());
            }
        } catch (Throwable ignored) {
            // no tags available at this point of the load — caller falls back
        }
        return List.of();
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
    private record Settings(boolean enchantments, String configuredLang) {

        static Settings read(File configFile, ComponentLogger log) {
            boolean enchantments = true;
            String configuredLang = "auto";

            if (configFile.isFile()) {
                try {
                    Toml toml = new Toml().read(configFile);
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
            return new Settings(enchantments, configuredLang);
        }
    }
}
