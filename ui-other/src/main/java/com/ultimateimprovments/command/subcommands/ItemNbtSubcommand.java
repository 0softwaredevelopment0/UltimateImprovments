package com.ultimateimprovments.command.subcommands;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.ultimateimprovments.command.CommandErrors;
import com.ultimateimprovments.command.SubCommand;
import com.ultimateimprovments.mechanics.features.integrity.IntegrityManager;
import com.ultimateimprovments.util.MessageUtil;
import com.ultimateimprovments.util.PlayerDataIO;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.item.BannerPatternLayers;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ChargedProjectiles;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.datacomponent.item.DeathProtection;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.Enchantable;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.Fireworks;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.JukeboxPlayable;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import io.papermc.paper.datacomponent.item.MapDecorations;
import io.papermc.paper.datacomponent.item.MapId;
import io.papermc.paper.datacomponent.item.MapItemColor;
import io.papermc.paper.datacomponent.item.OminousBottleAmplifier;
import io.papermc.paper.datacomponent.item.PotDecorations;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.Repairable;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.SeededContainerLoot;
import io.papermc.paper.datacomponent.item.SuspiciousStewEffects;
import io.papermc.paper.datacomponent.item.UseCooldown;
import io.papermc.paper.datacomponent.item.UseRemainder;
import io.papermc.paper.datacomponent.item.WritableBookContent;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.item.MapPostProcessing;
import io.papermc.paper.potion.SuspiciousEffectEntry;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.JukeboxSong;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockType;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.map.MapCursor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /ui itemnbt — edit item data components (NBT-level) of the item in your main hand.
 *
 * <p>Works through the Paper DataComponent API, so every change is fully
 * vanilla-compatible and survives restarts / conversions.</p>
 *
 * <pre>
 *   /ui itemnbt setname "&lt;miniMessage&gt;"                     — set the display name
 *   /ui itemnbt lore add [index] "&lt;line&gt;"                 — append / insert a lore line
 *   /ui itemnbt lore set &lt;index&gt; "&lt;line&gt;"              — overwrite a lore line
 *   /ui itemnbt lore remove &lt;index&gt;                       — remove a lore line
 *   /ui itemnbt hide &lt;flag|all&gt;                           — hide tooltip data
 *   /ui itemnbt unbreakable &lt;true|false&gt;                  — make the item unbreakable
 *   /ui itemnbt repaircost &lt;value&gt;                       — set anvil repair cost
 *   /ui itemnbt equipment entities &lt;entity&gt; &lt;true|false&gt; — allow/deny equipping on an entity type
 *   /ui itemnbt equipment cameraoverlay &lt;key|clear&gt;        — camera overlay texture
 *   /ui itemnbt equipment dispensable &lt;true|false&gt;         — dispensable by dispenser
 *   /ui itemnbt equipment equipinteract &lt;true|false&gt;       — equippable on interact
 *   /ui itemnbt equipment equipsound &lt;sound|clear&gt;         — equip sound
 *   /ui itemnbt equipment slot &lt;slot&gt; &lt;true|false&gt;        — equip slot on/off
 *   /ui itemnbt food addeffect &lt;effect&gt; &lt;ticks|-1&gt; &lt;level 1-255&gt; &lt;true|false&gt;
 *   /ui itemnbt food removeeffect &lt;effect&gt;
 *   /ui itemnbt food animation &lt;type&gt;
 *   /ui itemnbt food canalwayseat &lt;true|false&gt;
 *   /ui itemnbt food clear
 *   /ui itemnbt food consumeparticles &lt;particle|true|false|clear&gt;
 *   /ui itemnbt food eatticks &lt;ticks&gt;
 *   /ui itemnbt food info
 *   /ui itemnbt food nutrition &lt;int&gt;
 *   /ui itemnbt food saturation &lt;int&gt;
 *   /ui itemnbt food sound &lt;sound|clear&gt;
 * </pre>
 *
 * <p>Permission: {@code ui.command.itemnbt} (registered in {@code Permissions} — in code, not plugin.yml).</p>
 */
public final class ItemNbtSubcommand implements SubCommand {

    private static final String PERMISSION = "ui.command.itemnbt";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** -1 duration means "infinity" — stored as the maximum tick count. */
    private static final int INFINITE_TICKS = Integer.MAX_VALUE;

    @Override
    public String getName() {
        return "itemnbt";
    }

    // ══════════════════════════════════════════════════════════════
    // EXECUTE
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.parse("<red>❌ This command can only be used by players.</red>"));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            CommandErrors.noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        // Optional target player: /ui itemnbt <ник> <subcommand> ...
        // Without a nick the sender's own main-hand item is edited (backward compatible).
        Player target = player;
        String[] effArgs = args;
        if (args.length >= 3) {
            @SuppressWarnings("deprecation")
            Player named = Bukkit.getPlayerExact(args[1]);
            if (named != null) {
                target = named;
                effArgs = new String[args.length - 1];
                effArgs[0] = args[0];
                System.arraycopy(args, 2, effArgs, 1, args.length - 2);
            }
        }
        if (effArgs.length < 2) {
            sendUsage(player);
            return true;
        }

        if (target.isOnline()) {
            ItemStack item = target.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                player.sendMessage(MessageUtil.parse("<red>❌ " + target.getName() + " is not holding an item in their main hand!</red>"));
                return true;
            }
            boolean handled = dispatch(player, item, effArgs);
            if (handled) {
                target.getInventory().setItemInMainHand(item);
            }
            return true;
        }

        // Offline target: edit the main-hand slot in the .dat file (backed up automatically).
        try {
            java.io.File file = PlayerDataIO.locate(target.getUniqueId());
            if (file == null) {
                player.sendMessage(MessageUtil.parse("<red>❌ No player data file found for </red><yellow>" + target.getName() + "</yellow><red>.</red>"));
                return true;
            }
            net.minecraft.nbt.CompoundTag root = PlayerDataIO.readData(file);
            int selected = root.contains("SelectedItemSlot") ? root.getInt("SelectedItemSlot").orElse(0) : 0;
            if (selected < 0 || selected > 8) selected = 0;
            org.bukkit.inventory.ItemStack[] main = PlayerDataIO.readMain(root);
            ItemStack item = main[selected];
            if (item == null || item.getType().isAir()) {
                player.sendMessage(MessageUtil.parse("<red>❌ " + target.getName() + " is not holding an item in their main hand!</red>"));
                return true;
            }
            boolean handled = dispatch(player, item, effArgs);
            if (handled) {
                main[selected] = item;
                PlayerDataIO.writeMain(root, main);
                PlayerDataIO.writeData(file, root);
                player.sendMessage(MessageUtil.parse("<gray>Written back to </gray><white>" + file.getName() + "</white><gray> (backup created).</gray>"));
            }
        } catch (Exception e) {
            player.sendMessage(MessageUtil.parse("<red>❌ Could not edit offline player data: </red><yellow>" + e.getMessage() + "</yellow>"));
        }
        return true;
    }

    /** Routes a subcommand to its handler. All handlers read/write {@code item}. */
    private static boolean dispatch(Player player, ItemStack item, String[] args) {
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "setname" -> setName(player, item, args);
            case "lore" -> lore(player, item, args);
            case "hide" -> hide(player, item, args);
            case "unbreakable" -> unbreakable(player, item, args);
            case "repaircost" -> repairCost(player, item, args);
            case "equipment" -> equipment(player, item, args);
            case "food" -> food(player, item, args);
            case "maxstacksize" -> maxStackSize(player, item, args);
            case "maxdurability" -> maxDurability(player, item, args);
            case "fireresistent" -> fireResistant(player, item, args);
            case "glow" -> glow(player, item, args);
            case "glider" -> glider(player, item, args);
            case "rarity" -> rarity(player, item, args);
            case "amout", "amount" -> amount(player, item, args);
            case "durability" -> durability(player, item, args);
            case "banner" -> banner(player, item, args);
            case "color" -> color(player, item, args);
            case "skullowner" -> skullOwner(player, item, args);
            case "fireworkpower" -> fireworkPower(player, item, args);
            case "potioneffect" -> potionEffect(player, item, args);
            case "bookauthor" -> bookAuthor(player, item, args);
            case "booktype" -> bookType(player, item, args);
            case "attribute", "attrubite" -> attribute(player, item, args);
            case "cmdata" -> cmData(player, item, args);
            case "itemmodel" -> itemModel(player, item, args);
            case "compass" -> compass(player, item, args);
            case "axltype" -> axolotlType(player, item, args);
            case "ghsound" -> goatHornSound(player, item, args);
            case "armortrim" -> armorTrim(player, item, args);
            case "material" -> material(player, item, args);
            case "tooltipstyle" -> tooltipStyle(player, item, args);
            case "itemname" -> itemName(player, item, args);
            case "hidetooltip" -> hideTooltip(player, item, args);
            case "usecooldown" -> useCooldown(player, item, args);
            case "useremainder" -> useRemainder(player, item, args);
            case "canbreak" -> canBreakPlace(player, item, args, true);
            case "canplaceon" -> canBreakPlace(player, item, args, false);
            case "enchantable" -> enchantable(player, item, args);
            case "mapid" -> mapId(player, item, args);
            case "mapcolor" -> mapColor(player, item, args);
            case "mappost" -> mapPost(player, item, args);
            case "mapdeco" -> mapDeco(player, item, args);
            case "writablebook" -> writableBook(player, item, args);
            case "suspiciousstew" -> suspiciousStew(player, item, args);
            case "deathprotection" -> deathProtection(player, item, args);
            case "jukeboxplayable" -> jukeboxPlayable(player, item, args);
            case "noteblocksound" -> noteBlockSound(player, item, args);
            case "bundle" -> bundle(player, item, args);
            case "potdecorations" -> potDecorations(player, item, args);
            case "containerloot" -> containerLoot(player, item, args);
            case "ominousbottle" -> ominousBottle(player, item, args);
            case "intangibleprojectile" -> intangibleProjectile(player, item, args);
            case "firework" -> firework(player, item, args);
            case "chargedprojectiles" -> chargedProjectiles(player, item, args);
            case "container" -> containerContents(player, item, args);
            case "recipes" -> recipes(player, item, args);
            case "repairable" -> repairable(player, item, args);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    // ══════════════════════════════════════════════════════════════
    // NAME + LORE
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt setname "&lt;miniMessage&gt;" (or "clear" to remove the custom name). */
    private static boolean setName(Player p, ItemStack item, String[] args) {
        String text = joinFrom(args, 2);
        if (text.isEmpty() || text.equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.CUSTOM_NAME);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Custom name removed.</white>"));
            return true;
        }
        Component name = MM.deserialize(text);
        item.setData(DataComponentTypes.CUSTOM_NAME, name);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Name set to</white> <yellow>" + text + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt lore add [index] "&lt;line&gt;" | set &lt;index&gt; "&lt;line&gt;" | remove &lt;index&gt; */
    private static boolean lore(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt lore add [index] \"<line>\" | set <index> \"<line>\" | remove <index></white>"));
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> loreAdd(p, item, args);
            case "set" -> loreSet(p, item, args);
            case "remove" -> loreRemove(p, item, args);
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown lore action: </red><yellow>" + args[2]
                        + "</yellow><gray>. Use add, set or remove.</gray>"));
                yield true;
            }
        };
    }

    private static boolean loreAdd(Player p, ItemStack item, String[] args) {
        List<Component> lore = existingLore(item);

        int insertAt = -1; // -1 = append at the end
        int textStart = 3;
        if (args.length >= 4) {
            Integer idx = parseInt(args[3]);
            if (idx != null) {
                insertAt = Math.max(0, Math.min(idx, lore.size()));
                textStart = 4;
            }
        }
        if (textStart >= args.length) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt lore add [index] \"<line>\"</white>"));
            return true;
        }

        String line = joinFrom(args, textStart);
        Component component = MM.deserialize(line);
        if (insertAt < 0) {
            lore.add(component);
        } else {
            lore.add(insertAt, component);
        }
        item.lore(lore);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Lore line added</white>"
                + (insertAt < 0 ? "<white> at the end</white>" : " <gray>at index</gray> <yellow>" + insertAt + "</yellow>")
                + "<white>.</white>"));
        return true;
    }

    private static boolean loreSet(Player p, ItemStack item, String[] args) {
        if (args.length < 5) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt lore set <index> \"<line>\"</white>"));
            return true;
        }
        Integer idx = parseInt(args[3]);
        if (idx == null || idx < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid lore index: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        List<Component> lore = existingLore(item);
        if (idx >= lore.size()) {
            p.sendMessage(MessageUtil.parse("<red>❌ Lore index </red><yellow>" + idx
                    + "</yellow><red> is out of range (0-</red><yellow>" + (lore.size() - 1) + "</yellow><red>).</red>"));
            return true;
        }
        String line = joinFrom(args, 4);
        lore.set(idx, MM.deserialize(line));
        item.lore(lore);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Lore line</white> <yellow>" + idx
                + "</yellow> <white>overwritten.</white>"));
        return true;
    }

    private static boolean loreRemove(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt lore remove <index></white>"));
            return true;
        }
        Integer idx = parseInt(args[3]);
        if (idx == null || idx < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid lore index: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        List<Component> lore = existingLore(item);
        if (idx >= lore.size()) {
            p.sendMessage(MessageUtil.parse("<red>❌ Lore index </red><yellow>" + idx
                    + "</yellow><red> is out of range (0-</red><yellow>" + (lore.size() - 1) + "</yellow><red>).</red>"));
            return true;
        }
        lore.remove((int) idx);
        item.lore(lore);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Lore line</white> <yellow>" + idx
                + "</yellow> <white>removed.</white>"));
        return true;
    }

    private static List<Component> existingLore(ItemStack item) {
        List<Component> lore = item.lore();
        return lore == null ? new ArrayList<>() : new ArrayList<>(lore);
    }

    // ══════════════════════════════════════════════════════════════
    // HIDE / UNBREAKABLE / REPAIR COST
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt hide &lt;flag|all&gt; — adds an ItemFlag (hide tooltip data). */
    private static boolean hide(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt hide <flag|all></white>"
                    + "<gray> — armor_trim, attributes, destroys, dye, enchants, placed_on, stored_enchants, unbreakable, additional_tooltip</gray>"));
            return true;
        }
        List<ItemFlag> flags = flagsFor(args[2]);
        if (flags.isEmpty()) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown flag: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use armor_trim, attributes, destroys, dye, enchants, placed_on, stored_enchants, unbreakable, additional_tooltip or all.</gray>"));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ This item cannot hold tooltip flags.</red>"));
            return true;
        }
        for (ItemFlag flag : flags) {
            meta.addItemFlags(flag);
        }
        item.setItemMeta(meta);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Hidden flag(s) applied:</white> <yellow>"
                + args[2] + "</yellow><white>.</white>"));
        return true;
    }

    private static List<ItemFlag> flagsFor(String input) {
        String flag = input.toLowerCase(Locale.ROOT);
        if (flag.equals("all")) return List.of(ItemFlag.values());
        return switch (flag) {
            case "armor_trim" -> List.of(ItemFlag.HIDE_ARMOR_TRIM);
            case "attributes" -> List.of(ItemFlag.HIDE_ATTRIBUTES);
            case "destroys" -> List.of(ItemFlag.HIDE_DESTROYS);
            case "dye" -> List.of(ItemFlag.HIDE_DYE);
            case "enchants" -> List.of(ItemFlag.HIDE_ENCHANTS);
            case "placed_on" -> List.of(ItemFlag.HIDE_PLACED_ON);
            case "stored_enchants" -> List.of(ItemFlag.HIDE_STORED_ENCHANTS);
            case "unbreakable" -> List.of(ItemFlag.HIDE_UNBREAKABLE);
            case "additional_tooltip" -> List.of(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            default -> List.of();
        };
    }

    /** /ui itemnbt unbreakable &lt;true|false&gt; */
    private static boolean unbreakable(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt unbreakable <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[2]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        if (value) {
            item.setData(DataComponentTypes.UNBREAKABLE);
        } else {
            item.resetData(DataComponentTypes.UNBREAKABLE);
        }
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Unbreakable set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt repaircost &lt;value&gt; */
    private static boolean repairCost(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt repaircost <value></white>"));
            return true;
        }
        Integer cost = parseInt(args[2]);
        if (cost == null || cost < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Repair cost must be a non-negative number: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.REPAIR_COST, cost);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Repair cost set to</white> <yellow>" + cost + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // EQUIPMENT (minecraft:equippable component)
    // ══════════════════════════════════════════════════════════════

    private static boolean equipment(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt equipment <entities|cameraoverlay|dispensable|equipinteract|equipsound|slot> ...</white>"));
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "entities" -> equipmentEntities(p, item, args);
            case "cameraoverlay" -> equipmentCameraOverlay(p, item, args);
            case "dispensable" -> equipmentDispensable(p, item, args);
            case "equipinteract" -> equipmentEquipInteract(p, item, args);
            case "equipsound" -> equipmentEquipSound(p, item, args);
            case "slot" -> equipmentSlot(p, item, args);
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown equipment action: </red><yellow>" + args[2]
                        + "</yellow><gray>. Use entities, cameraoverlay, dispensable, equipinteract, equipsound or slot.</gray>"));
                yield true;
            }
        };
    }

    /** /ui itemnbt equipment entities &lt;entity&gt; &lt;true|false&gt; */
    private static boolean equipmentEntities(Player p, ItemStack item, String[] args) {
        if (args.length < 5) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt equipment entities <entityType> <true|false></white>"));
            return true;
        }
        EntityType type = resolveRegistry(Registry.ENTITY_TYPE, args[3]);
        if (type == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown entity type: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        Boolean allow = parseBool(args[4]);
        if (allow == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[4] + "</yellow>"));
            return true;
        }

        Equippable old = item.getData(DataComponentTypes.EQUIPPABLE);
        EquipmentSlot slot = old == null ? EquipmentSlot.HAND : old.slot();

        Set<TypedKey<EntityType>> keys = new LinkedHashSet<>();
        if (old != null && old.allowedEntities() != null) {
            keys.addAll(old.allowedEntities().values());
        }
        TypedKey<EntityType> typed = TypedKey.create(RegistryKey.ENTITY_TYPE,
                Key.key(type.getKey().getNamespace(), type.getKey().getKey()));
        if (allow) {
            keys.add(typed);
        } else {
            keys.remove(typed);
        }
        RegistryKeySet<EntityType> set = RegistrySet.keySet(RegistryKey.ENTITY_TYPE, keys);
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).allowedEntities(set));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Entity</white> <yellow>" + type.getKey().getKey()
                + "</yellow> <white>is now</white> <yellow>" + (allow ? "allowed" : "blocked")
                + "</yellow> <white>from equipping this item.</white>"));
        return true;
    }

    /** /ui itemnbt equipment cameraoverlay &lt;key|clear&gt; */
    private static boolean equipmentCameraOverlay(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt equipment cameraoverlay <textureKey|clear></white>"));
            return true;
        }
        Equippable old = item.getData(DataComponentTypes.EQUIPPABLE);
        if (args[3].equalsIgnoreCase("clear")) {
            if (old == null) {
                p.sendMessage(MessageUtil.parse("<red>❌ This item has no equipment data.</red>"));
                return true;
            }
            item.setData(DataComponentTypes.EQUIPPABLE, withoutEquippableFields(old, true, false));
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Camera overlay cleared.</white>"));
            return true;
        }
        Key overlay = parseResourceKey(args[3]);
        if (overlay == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid texture key: </red><yellow>" + args[3]
                    + "</yellow><gray>. Use namespace:path (e.g. minecraft:textures/misc/pumpkinblur.png).</gray>"));
            return true;
        }
        EquipmentSlot slot = old == null ? EquipmentSlot.HAND : old.slot();
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).cameraOverlay(overlay));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Camera overlay set to</white> <yellow>" + overlay.asString() + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt equipment dispensable &lt;true|false&gt; */
    private static boolean equipmentDispensable(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt equipment dispensable <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[3]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        Equippable old = item.getData(DataComponentTypes.EQUIPPABLE);
        EquipmentSlot slot = old == null ? EquipmentSlot.HAND : old.slot();
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).dispensable(value));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Dispensable set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt equipment equipinteract &lt;true|false&gt; */
    private static boolean equipmentEquipInteract(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt equipment equipinteract <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[3]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        Equippable old = item.getData(DataComponentTypes.EQUIPPABLE);
        EquipmentSlot slot = old == null ? EquipmentSlot.HAND : old.slot();
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).equipOnInteract(value));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Equip-on-interact set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt equipment equipsound &lt;sound|clear&gt; */
    private static boolean equipmentEquipSound(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt equipment equipsound <sound|clear></white>"));
            return true;
        }
        Equippable old = item.getData(DataComponentTypes.EQUIPPABLE);
        if (args[3].equalsIgnoreCase("clear")) {
            if (old == null) {
                p.sendMessage(MessageUtil.parse("<red>❌ This item has no equipment data.</red>"));
                return true;
            }
            item.setData(DataComponentTypes.EQUIPPABLE, withoutEquippableFields(old, false, true));
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Equip sound cleared.</white>"));
            return true;
        }
        Key sound = resolveSoundKey(args[3]);
        if (sound == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown sound: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        EquipmentSlot slot = old == null ? EquipmentSlot.HAND : old.slot();
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).equipSound(sound));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Equip sound set to</white> <yellow>" + sound.asString() + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt equipment slot &lt;slot&gt; &lt;true|false&gt; */
    private static boolean equipmentSlot(Player p, ItemStack item, String[] args) {
        if (args.length < 5) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt equipment slot <body|chest|feet|hand|head|legs|off_hand|saddle> <true|false></white>"));
            return true;
        }
        EquipmentSlot slot = parseSlot(args[3]);
        if (slot == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown slot: </red><yellow>" + args[3]
                    + "</yellow><gray>. Use body, chest, feet, hand, head, legs, off_hand or saddle.</gray>"));
            return true;
        }
        Boolean allow = parseBool(args[4]);
        if (allow == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[4] + "</yellow>"));
            return true;
        }
        Equippable old = item.getData(DataComponentTypes.EQUIPPABLE);
        if (!allow) {
            item.resetData(DataComponentTypes.EQUIPPABLE);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item is no longer equippable (equipment component removed).</white>"));
            return true;
        }
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Equip slot set to</white> <yellow>" + slot.name().toLowerCase()
                + "</yellow><white>.</white>"));
        return true;
    }

    /**
     * Rebuilds an {@link Equippable} from {@code old}, optionally dropping the
     * camera overlay and/or the equip sound (used for the "clear" actions —
     * the update-builder cannot unset a field).
     */
    private static Equippable withoutEquippableFields(Equippable old, boolean dropCamera, boolean dropSound) {
        Equippable.Builder b = Equippable.equippable(old.slot());
        if (!dropSound && old.equipSound() != null) b.equipSound(old.equipSound());
        if (old.assetId() != null) b.assetId(old.assetId());
        if (!dropCamera && old.cameraOverlay() != null) b.cameraOverlay(old.cameraOverlay());
        if (old.allowedEntities() != null) b.allowedEntities(old.allowedEntities());
        b.dispensable(old.dispensable());
        b.swappable(old.swappable());
        b.damageOnHurt(old.damageOnHurt());
        b.equipOnInteract(old.equipOnInteract());
        b.canBeSheared(old.canBeSheared());
        if (old.shearSound() != null) b.shearSound(old.shearSound());
        return b.build();
    }

    // ══════════════════════════════════════════════════════════════
    // FOOD (minecraft:food + minecraft:consumable components)
    // ══════════════════════════════════════════════════════════════

    private static boolean food(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food <addeffect|animation|canalwayseat|clear|consumeparticles|eatticks|info|nutrition|removeeffect|saturation|sound> ...</white>"));
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "addeffect" -> foodAddEffect(p, item, args);
            case "removeeffect", "removeeefect" -> foodRemoveEffect(p, item, args);
            case "animation" -> foodAnimation(p, item, args);
            case "canalwayseat" -> foodCanAlwaysEat(p, item, args);
            case "clear" -> foodClear(p, item, args);
            case "consumeparticles" -> foodConsumeParticles(p, item, args);
            case "eatticks" -> foodEatTicks(p, item, args);
            case "info" -> foodInfo(p, item, args);
            case "nutrition" -> foodNutrition(p, item, args);
            case "saturation" -> foodSaturation(p, item, args);
            case "sound" -> foodSound(p, item, args);
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown food action: </red><yellow>" + args[2]
                        + "</yellow><gray>. Use addeffect, animation, canalwayseat, clear, consumeparticles, eatticks, info, nutrition, removeeffect, saturation or sound.</gray>"));
                yield true;
            }
        };
    }

    /** /ui itemnbt food addeffect &lt;effect&gt; &lt;ticks|-1&gt; &lt;level 1-255&gt; &lt;particles true|false&gt; */
    private static boolean foodAddEffect(Player p, ItemStack item, String[] args) {
        if (args.length < 7) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food addeffect <effect> <ticks|-1> <level 1-255> <true|false></white>"));
            return true;
        }
        PotionEffectType type = resolveRegistry(Registry.POTION_EFFECT_TYPE, args[3]);
        if (type == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown effect: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        int ticks;
        try {
            ticks = Integer.parseInt(args[4].trim());
        } catch (NumberFormatException e) {
            p.sendMessage(MessageUtil.parse("<red>❌ Duration must be a number of ticks (or -1 for infinity): </red><yellow>" + args[4] + "</yellow>"));
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(args[5].trim());
        } catch (NumberFormatException e) {
            p.sendMessage(MessageUtil.parse("<red>❌ Level must be a number 1-255: </red><yellow>" + args[5] + "</yellow>"));
            return true;
        }
        if (level < 1 || level > 255) {
            p.sendMessage(MessageUtil.parse("<red>❌ Level out of range (1-255): </red><yellow>" + level + "</yellow>"));
            return true;
        }
        Boolean particles = parseBool(args[6]);
        if (particles == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false for particles, got: </red><yellow>" + args[6] + "</yellow>"));
            return true;
        }

        int duration = ticks < 0 ? INFINITE_TICKS : ticks;
        PotionEffect effect = new PotionEffect(type, duration, level - 1, false, particles, true);
        ConsumeEffect apply = ConsumeEffect.applyStatusEffects(List.of(effect), 1.0f);

        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().addEffect(apply));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Effect</white> <yellow>" + type.getKey().getKey()
                + "</yellow> <white>added to the food:</white> <gray>"
                + (ticks < 0 ? "infinite" : ticks + " ticks") + ", level " + level
                + ", particles: " + particles + "</gray><white>.</white>"));
        return true;
    }

    /** /ui itemnbt food removeeffect &lt;effect&gt; */
    private static boolean foodRemoveEffect(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food removeeffect <effect></white>"));
            return true;
        }
        PotionEffectType type = resolveRegistry(Registry.POTION_EFFECT_TYPE, args[3]);
        if (type == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown effect: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        TypedKey<PotionEffectType> typed = TypedKey.create(RegistryKey.MOB_EFFECT,
                Key.key(type.getKey().getNamespace(), type.getKey().getKey()));
        RegistryKeySet<PotionEffectType> set = RegistrySet.keySet(RegistryKey.MOB_EFFECT, List.of(typed));
        ConsumeEffect remove = ConsumeEffect.removeEffects(set);
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().addEffect(remove));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Effect</white> <yellow>" + type.getKey().getKey()
                + "</yellow> <white>will now be removed when eaten.</white>"));
        return true;
    }

    /** /ui itemnbt food animation &lt;type&gt; */
    private static boolean foodAnimation(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food animation <block|bow|brush|crossbow|drink|eat|none|spear|spyglass|toot_horn></white>"));
            return true;
        }
        ItemUseAnimation animation = parseAnimation(args[3]);
        if (animation == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown animation: </red><yellow>" + args[3]
                    + "</yellow><gray>. Use block, bow, brush, crossbow, drink, eat, none, spear, spyglass or toot_horn.</gray>"));
            return true;
        }
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().animation(animation));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Eating animation set to</white> <yellow>" + animation.name().toLowerCase()
                + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt food canalwayseat &lt;true|false&gt; */
    private static boolean foodCanAlwaysEat(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food canalwayseat <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[3]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().canAlwaysEat(value));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Can-always-eat set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt food clear — removes all consume effects and consume settings. */
    private static boolean foodClear(Player p, ItemStack item, String[] args) {
        item.resetData(DataComponentTypes.CONSUMABLE);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>All food effects (and consume settings) cleared.</white>"));
        return true;
    }

    /** /ui itemnbt food consumeparticles &lt;particle|true|false|clear&gt; */
    private static boolean foodConsumeParticles(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food consumeparticles <true|false|clear></white>"));
            return true;
        }
        String raw = args[3].toLowerCase(Locale.ROOT);
        boolean show;
        if (raw.equals("false") || raw.equals("clear")) {
            show = false;
        } else if (raw.equals("true") || resolveRegistry(Registry.PARTICLE_TYPE, args[3]) != null) {
            show = true;
        } else {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown particle: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().hasConsumeParticles(show));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Consume particles set to</white> <yellow>" + show + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt food eatticks &lt;ticks&gt; */
    private static boolean foodEatTicks(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food eatticks <ticks></white>"));
            return true;
        }
        Integer ticks = parseInt(args[3]);
        if (ticks == null || ticks <= 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Eat time must be a positive number of ticks: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(ticks / 20.0f));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Eating time set to</white> <yellow>" + ticks
                + "</yellow> <white>ticks (</white><yellow>" + (ticks / 20.0) + "s</yellow><white>).</white>"));
        return true;
    }

    /** /ui itemnbt food info — prints the current food + consume data. */
    private static boolean foodInfo(Player p, ItemStack item, String[] args) {
        FoodProperties food = item.getData(DataComponentTypes.FOOD);
        Consumable consumable = item.getData(DataComponentTypes.CONSUMABLE);

        String itemName = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        p.sendMessage(MessageUtil.parse("<dark_gray>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓</dark_gray>"));
        p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <gold>✦</gold> <white>Food data of</white> <yellow>" + itemName));

        if (food == null && consumable == null) {
            p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <gray>This item has no food data.</gray>"));
        } else {
            if (food != null) {
                p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <aqua>Nutrition:</aqua> <white>" + food.nutrition()));
                p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <aqua>Saturation:</aqua> <white>" + food.saturation()));
                p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <aqua>Can always eat:</aqua> <white>" + food.canAlwaysEat()));
            }
            if (consumable != null) {
                p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <aqua>Eat time:</aqua> <white>"
                        + Math.round(consumable.consumeSeconds() * 20) + " ticks</white>"));
                p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <aqua>Animation:</aqua> <white>"
                        + (consumable.animation() == null ? "none" : consumable.animation().name().toLowerCase())));
                p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <aqua>Sound:</aqua> <white>"
                        + (consumable.sound() == null ? "default" : consumable.sound().asString())));
                p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <aqua>Particles:</aqua> <white>" + consumable.hasConsumeParticles()));
                List<ConsumeEffect> effects = consumable.consumeEffects();
                p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>  <aqua>Effects:</aqua> <white>" + effects.size()));
                for (ConsumeEffect effect : effects) {
                    if (effect instanceof ConsumeEffect.ApplyStatusEffects apply) {
                        for (PotionEffect pe : apply.effects()) {
                            p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>    <gray>-</gray> <yellow>"
                                    + pe.getType().getKey().getKey() + " " + (pe.getAmplifier() + 1)
                                    + "</yellow> <gray>"
                                    + (pe.getDuration() >= INFINITE_TICKS ? "infinite" : pe.getDuration() + " ticks")
                                    + " (particles: " + pe.hasParticles() + ")</gray>"));
                        }
                    } else if (effect instanceof ConsumeEffect.RemoveStatusEffects remove) {
                        p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>    <gray>-</gray> <red>remove:</red> <gray>"
                                + remove.removeEffects().size() + " effect type(s)</gray>"));
                    } else if (effect instanceof ConsumeEffect.ClearAllStatusEffects) {
                        p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>    <gray>-</gray> <red>clear all effects</red>"));
                    } else if (effect instanceof ConsumeEffect.PlaySound play) {
                        p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>    <gray>-</gray> <aqua>play sound:</aqua> <gray>"
                                + (play.sound() == null ? "?" : play.sound().asString()) + "</gray>"));
                    } else if (effect instanceof ConsumeEffect.TeleportRandomly) {
                        p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray>    <gray>-</gray> <aqua>teleport randomly</aqua>"));
                    }
                }
            }
        }
        p.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛</dark_gray>"));
        return true;
    }

    /** /ui itemnbt food nutrition &lt;int&gt; */
    private static boolean foodNutrition(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food nutrition <value></white>"));
            return true;
        }
        Integer value = parseInt(args[3]);
        if (value == null || value < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Nutrition must be a non-negative number: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(value));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Nutrition set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt food saturation &lt;int&gt; */
    private static boolean foodSaturation(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food saturation <value></white>"));
            return true;
        }
        Integer value = parseInt(args[3]);
        if (value == null || value < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Saturation must be a non-negative number: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().saturation(value));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Saturation set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt food sound &lt;sound|clear&gt; */
    private static boolean foodSound(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt food sound <sound|clear></white>"));
            return true;
        }
        Consumable old = item.getData(DataComponentTypes.CONSUMABLE);
        if (args[3].equalsIgnoreCase("clear")) {
            if (old == null) {
                p.sendMessage(MessageUtil.parse("<red>❌ This item has no consume data.</red>"));
                return true;
            }
            item.setData(DataComponentTypes.CONSUMABLE, withoutConsumableSound(old));
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Eating sound cleared (default sound restored).</white>"));
            return true;
        }
        Key sound = resolveSoundKey(args[3]);
        if (sound == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown sound: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().sound(sound));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Eating sound set to</white> <yellow>" + sound.asString() + "</yellow><white>.</white>"));
        return true;
    }

    /** Rebuilds a {@link Consumable} without its custom sound (the update-builder cannot unset it). */
    private static Consumable withoutConsumableSound(Consumable old) {
        Consumable.Builder b = Consumable.consumable();
        b.consumeSeconds(old.consumeSeconds());
        if (old.animation() != null) b.animation(old.animation());
        b.hasConsumeParticles(old.hasConsumeParticles());
        b.effects(old.consumeEffects());
        return b.build();
    }

    // ══════════════════════════════════════════════════════════════
    // MAX STACK SIZE / MAX DURABILITY
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt maxstacksize <value> */
    private static boolean maxStackSize(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt maxstacksize <1-99></white>"));
            return true;
        }
        Integer value = parseInt(args[2]);
        if (value == null || value < 1 || value > 99) {
            p.sendMessage(MessageUtil.parse("<red>❌ Stack size must be 1-99: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.MAX_STACK_SIZE, value);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Max stack size set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt maxdurability <value> */
    private static boolean maxDurability(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt maxdurability <value></white>"));
            return true;
        }
        Integer value = parseInt(args[2]);
        if (value == null || value < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Durability must be non-negative: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.MAX_DAMAGE, value);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Max durability set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // FIRE RESISTANT / GLOW / GLIDER / RARITY / AMOUNT / DURABILITY
    // ══════════════════════════════════════════════════════════════

    /** Fire damage types from the #minecraft:is_fire tag. */
    private static final List<String> FIRE_DAMAGE_TYPES = List.of(
            "in_fire", "campfire", "on_fire", "lava",
            "hot_floor", "sulfur_cube_hot", "unattributed_fireball", "fireball");

    /** /ui itemnbt fireresistent <true|false> — fire/lava immunity via damage_resistant. */
    private static boolean fireResistant(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt fireresistent <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[2]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        if (value) {
            Set<TypedKey<DamageType>> keys = new LinkedHashSet<>();
            for (String name : FIRE_DAMAGE_TYPES) {
                keys.add(TypedKey.create(RegistryKey.DAMAGE_TYPE, Key.key("minecraft", name)));
            }
            RegistryKeySet<DamageType> set = RegistrySet.keySet(RegistryKey.DAMAGE_TYPE, keys);
            item.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(set));
        } else {
            item.resetData(DataComponentTypes.DAMAGE_RESISTANT);
        }
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Fire resistant set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt glow <true|false> — enchant glint override. */
    private static boolean glow(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt glow <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[2]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, value);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Glow (glint override) set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt glider <true|false> — elytra-like gliding. */
    private static boolean glider(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt glider <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[2]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Expected true or false, got: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        if (value) {
            item.setData(DataComponentTypes.GLIDER);
        } else {
            item.resetData(DataComponentTypes.GLIDER);
        }
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Glider set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt rarity <common|uncommon|rare|epic> */
    private static boolean rarity(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt rarity <common|uncommon|rare|epic></white>"));
            return true;
        }
        ItemRarity rarity = parseRarity(args[2]);
        if (rarity == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown rarity: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use common, uncommon, rare or epic.</gray>"));
            return true;
        }
        item.setData(DataComponentTypes.RARITY, rarity);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Rarity set to</white> <yellow>" + rarity.name().toLowerCase() + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt amount <value> — set item stack amount. */
    private static boolean amount(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt amount <value></white>"));
            return true;
        }
        Integer value = parseInt(args[2]);
        if (value == null || value < 1) {
            p.sendMessage(MessageUtil.parse("<red>❌ Amount must be >= 1: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        int max = item.getMaxStackSize();
        int clamped = Math.min(value, max);
        item.setAmount(clamped);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Amount set to</white> <yellow>" + clamped + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt durability <value> — disabled while integrity mechanic is on. */
    private static boolean durability(Player p, ItemStack item, String[] args) {
        if (IntegrityManager.isEnabled()) {
            p.sendMessage(MessageUtil.parse("<red>❌ Durability editing is disabled while the Integrity mechanic is enabled.</red>"));
            return true;
        }
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt durability <value></white>"));
            return true;
        }
        Integer value = parseInt(args[2]);
        if (value == null || value < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Durability must be non-negative: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        int max = item.getDataOrDefault(DataComponentTypes.MAX_DAMAGE, Integer.valueOf(item.getType().getMaxDurability()));
        if (max <= 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ This item has no durability.</red>"));
            return true;
        }
        if (value > max) {
            p.sendMessage(MessageUtil.parse("<red>❌ Value exceeds max durability (</red><yellow>" + max + "</yellow><red>).</red>"));
            return true;
        }
        item.setData(DataComponentTypes.DAMAGE, max - value);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Current durability set to</white> <yellow>" + value + "</yellow><white> / </white><yellow>" + max + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // BANNER
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt banner <add|set|remove|info> ... */
    private static boolean banner(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt banner <add|set|remove|info> ...</white>"));
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> bannerAdd(p, item, args);
            case "set" -> bannerSet(p, item, args);
            case "remove" -> bannerRemove(p, item, args);
            case "info" -> bannerInfo(p, item, args);
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown banner action: </red><yellow>" + args[2]
                        + "</yellow><gray>. Use add, set, remove or info.</gray>"));
                yield true;
            }
        };
    }

    private static List<Pattern> bannerPatterns(ItemStack item) {
        BannerPatternLayers layers = item.getData(DataComponentTypes.BANNER_PATTERNS);
        return layers == null ? new ArrayList<>() : new ArrayList<>(layers.patterns());
    }

    /** /ui itemnbt banner add <index> <pattern> <color> */
    private static boolean bannerAdd(Player p, ItemStack item, String[] args) {
        if (args.length < 6) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt banner add <index> <pattern> <color></white>"));
            return true;
        }
        Integer idx = parseInt(args[3]);
        if (idx == null || idx < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid index: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        PatternType patternType = resolveRegistry(Registry.BANNER_PATTERN, args[4]);
        DyeColor dyeColor = parseDyeColor(args[5]);
        if (patternType == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown banner pattern: </red><yellow>" + args[4] + "</yellow>"));
            return true;
        }
        if (dyeColor == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown dye color: </red><yellow>" + args[5] + "</yellow>"));
            return true;
        }
        List<Pattern> patterns = bannerPatterns(item);
        int insertAt = Math.min(idx, patterns.size());
        patterns.add(insertAt, new Pattern(dyeColor, patternType));
        item.setData(DataComponentTypes.BANNER_PATTERNS, BannerPatternLayers.bannerPatternLayers(patterns));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Pattern added at index</white> <yellow>" + insertAt
                + "</yellow><white>: </white><yellow>" + patternType.getKey().getKey()
                + "</yellow><white> / </white><yellow>" + dyeColor.name().toLowerCase() + "</white>"));
        return true;
    }

    /** /ui itemnbt banner set <index> <pattern> <color> */
    private static boolean bannerSet(Player p, ItemStack item, String[] args) {
        if (args.length < 6) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt banner set <index> <pattern> <color></white>"));
            return true;
        }
        Integer idx = parseInt(args[3]);
        if (idx == null || idx < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid index: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        PatternType patternType = resolveRegistry(Registry.BANNER_PATTERN, args[4]);
        DyeColor dyeColor = parseDyeColor(args[5]);
        if (patternType == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown banner pattern: </red><yellow>" + args[4] + "</yellow>"));
            return true;
        }
        if (dyeColor == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown dye color: </red><yellow>" + args[5] + "</yellow>"));
            return true;
        }
        List<Pattern> patterns = bannerPatterns(item);
        if (idx >= patterns.size()) {
            p.sendMessage(MessageUtil.parse("<red>❌ Index out of range (0-</red><yellow>" + (patterns.size() - 1) + "</yellow><red>).</red>"));
            return true;
        }
        patterns.set(idx, new Pattern(dyeColor, patternType));
        item.setData(DataComponentTypes.BANNER_PATTERNS, BannerPatternLayers.bannerPatternLayers(patterns));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Pattern</white> <yellow>" + idx
                + "</yellow> <white>replaced with</white> <yellow>" + patternType.getKey().getKey()
                + "</yellow><white> / </white><yellow>" + dyeColor.name().toLowerCase() + "</white>"));
        return true;
    }

    /** /ui itemnbt banner remove <index> */
    private static boolean bannerRemove(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt banner remove <index></white>"));
            return true;
        }
        Integer idx = parseInt(args[3]);
        if (idx == null || idx < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid index: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        List<Pattern> patterns = bannerPatterns(item);
        if (idx >= patterns.size()) {
            p.sendMessage(MessageUtil.parse("<red>❌ Index out of range (0-</red><yellow>" + (patterns.size() - 1) + "</yellow><red>).</red>"));
            return true;
        }
        Pattern removed = patterns.remove((int) idx);
        if (patterns.isEmpty()) {
            item.resetData(DataComponentTypes.BANNER_PATTERNS);
        } else {
            item.setData(DataComponentTypes.BANNER_PATTERNS, BannerPatternLayers.bannerPatternLayers(patterns));
        }
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Pattern removed at index</white> <yellow>" + idx
                + "</yellow><white> (</white><yellow>" + removed.getPattern().getKey().getKey()
                + "</yellow><white> / </white><yellow>" + removed.getColor().name().toLowerCase() + "</white><white>)</white>"));
        return true;
    }

    /** /ui itemnbt banner info <all|index> */
    private static boolean bannerInfo(Player p, ItemStack item, String[] args) {
        List<Pattern> patterns = bannerPatterns(item);
        if (patterns.isEmpty()) {
            p.sendMessage(MessageUtil.parse("<gray>No banner patterns.</gray>"));
            return true;
        }
        // Show specific index
        if (args.length >= 4 && !args[3].equalsIgnoreCase("all")) {
            Integer idx = parseInt(args[3]);
            if (idx == null || idx < 0 || idx >= patterns.size()) {
                p.sendMessage(MessageUtil.parse("<red>❌ Index out of range (0-</red><yellow>" + (patterns.size() - 1) + "</yellow><red>).</red>"));
                return true;
            }
            Pattern pat = patterns.get(idx);
            p.sendMessage(MessageUtil.parse("<dark_gray>┌── Pattern #</dark_gray><yellow>" + idx + "</yellow><dark_gray> ──</dark_gray>"));
            p.sendMessage(MessageUtil.parse("<dark_gray>│</dark_gray> <aqua>Pattern:</aqua> <white>" + pat.getPattern().getKey().getKey() + "</white>"));
            p.sendMessage(MessageUtil.parse("<dark_gray>│</dark_gray> <aqua>Color:</aqua> <white>" + pat.getColor().name().toLowerCase() + "</white>"));
            p.sendMessage(MessageUtil.parse("<dark_gray>└──────────────────</dark_gray>"));
            return true;
        }
        // Show all
        p.sendMessage(MessageUtil.parse("<dark_gray>┏━━━ Banner patterns (</dark_gray><yellow>" + patterns.size() + "</yellow><dark_gray>) ━━━</dark_gray>"));
        for (int i = 0; i < patterns.size(); i++) {
            Pattern pat = patterns.get(i);
            p.sendMessage(MessageUtil.parse("<dark_gray>┃</dark_gray> <yellow>#" + i + "</yellow> <white>"
                    + pat.getPattern().getKey().getKey() + "</white> <gray>/</gray> <white>"
                    + pat.getColor().name().toLowerCase() + "</white>"));
        }
        p.sendMessage(MessageUtil.parse("<dark_gray>┗━━━━━━━━━━━━━━━━━━</dark_gray>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // COLOR / SKULL OWNER / FIREWORK POWER / POTION EFFECTS
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt color <R> <G> <B> — for leather, potions, tipped arrows, firework stars. */
    private static boolean color(Player p, ItemStack item, String[] args) {
        if (args.length < 5) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt color <R> <G> <B> (0-255)</white>"));
            return true;
        }
        Integer r = parseInt(args[2]);
        Integer g = parseInt(args[3]);
        Integer b = parseInt(args[4]);
        if (r == null || g == null || b == null || r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            p.sendMessage(MessageUtil.parse("<red>❌ R, G and B must be 0-255.</red>"));
            return true;
        }
        Color color = Color.fromRGB(r, g, b);
        Material type = item.getType();
        String name = type.name();
        if (name.startsWith("LEATHER_") || type == Material.WOLF_ARMOR) {
            item.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
        } else if (type == Material.POTION || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION || type == Material.TIPPED_ARROW) {
            PotionContents old = item.getData(DataComponentTypes.POTION_CONTENTS);
            PotionContents.Builder builder = PotionContents.potionContents();
            if (old != null) {
                if (old.potion() != null) builder.potion(old.potion());
                if (old.customName() != null) builder.customName(old.customName());
                builder.addCustomEffects(old.customEffects());
            }
            builder.customColor(color);
            item.setData(DataComponentTypes.POTION_CONTENTS, builder.build());
        } else if (type == Material.FIREWORK_STAR) {
            FireworkEffect old = item.getData(DataComponentTypes.FIREWORK_EXPLOSION);
            FireworkEffect.Builder fb = FireworkEffect.builder();
            if (old != null) {
                fb.with(old.getType());
                fb.flicker(old.hasFlicker());
                fb.trail(old.hasTrail());
                fb.withFade(old.getFadeColors());
            }
            fb.withColor(color);
            item.setData(DataComponentTypes.FIREWORK_EXPLOSION, fb.build());
        } else {
            p.sendMessage(MessageUtil.parse("<red>❌ This item does not support coloring.</red> <gray>(works on leather armor, potions, tipped arrows, firework stars)</gray>"));
            return true;
        }
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Color set to RGB(</white><yellow>" + r + "</yellow><white>, </white><yellow>" + g
                + "</yellow><white>, </white><yellow>" + b + "</yellow><white>)</white>"));
        return true;
    }

    /** /ui itemnbt skullowner <name> — set head owner profile. */
    private static boolean skullOwner(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt skullowner <playerName></white>"));
            return true;
        }
        String name = args[2];
        PlayerProfile profile = Bukkit.createProfile(name);
        item.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(profile));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Head owner set to</white> <yellow>" + name + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt fireworkpower <1-255> */
    private static boolean fireworkPower(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt fireworkpower <1-255></white>"));
            return true;
        }
        Integer power = parseInt(args[2]);
        if (power == null || power < 1 || power > 255) {
            p.sendMessage(MessageUtil.parse("<red>❌ Flight duration must be 1-255: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        Fireworks old = item.getData(DataComponentTypes.FIREWORKS);
        List<FireworkEffect> effects = old == null ? List.of() : old.effects();
        item.setData(DataComponentTypes.FIREWORKS, Fireworks.fireworks(effects, power));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Firework power set to</white> <yellow>" + power + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // POTION EFFECTS
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt potioneffect <add|remove|reset> ... */
    private static boolean potionEffect(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt potioneffect <add|remove|reset> ...</white>"));
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> potionEffectAdd(p, item, args);
            case "remove" -> potionEffectRemove(p, item, args);
            case "reset" -> potionEffectReset(p, item, args);
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown potioneffect action: </red><yellow>" + args[2]
                        + "</yellow><gray>. Use add, remove or reset.</gray>"));
                yield true;
            }
        };
    }

    /** /ui itemnbt potioneffect add <effect> <duration ticks|-1> <power 1-255> */
    private static boolean potionEffectAdd(Player p, ItemStack item, String[] args) {
        if (args.length < 6) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt potioneffect add <effect> <duration|-1> <power 1-255></white>"));
            return true;
        }
        PotionEffectType type = resolveRegistry(Registry.POTION_EFFECT_TYPE, args[3]);
        if (type == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown effect: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        int duration;
        try {
            duration = Integer.parseInt(args[4].trim());
        } catch (NumberFormatException e) {
            p.sendMessage(MessageUtil.parse("<red>❌ Duration must be ticks (or -1 for infinity): </red><yellow>" + args[4] + "</yellow>"));
            return true;
        }
        int power;
        try {
            power = Integer.parseInt(args[5].trim());
        } catch (NumberFormatException e) {
            p.sendMessage(MessageUtil.parse("<red>❌ Power must be 1-255: </red><yellow>" + args[5] + "</yellow>"));
            return true;
        }
        if (power < 1 || power > 255) {
            p.sendMessage(MessageUtil.parse("<red>❌ Power out of range (1-255): </red><yellow>" + power + "</yellow>"));
            return true;
        }
        int ticks = duration < 0 ? INFINITE_TICKS : duration;
        PotionContents old = item.getData(DataComponentTypes.POTION_CONTENTS);
        PotionContents.Builder builder = PotionContents.potionContents();
        if (old != null) {
            if (old.potion() != null) builder.potion(old.potion());
            if (old.customName() != null) builder.customName(old.customName());
            if (old.customColor() != null) builder.customColor(old.customColor());
            builder.addCustomEffects(old.customEffects());
        }
        builder.addCustomEffect(new PotionEffect(type, ticks, power - 1, false, true, true));
        item.setData(DataComponentTypes.POTION_CONTENTS, builder.build());
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Effect</white> <yellow>" + type.getKey().getKey()
                + "</yellow> <white>added:</white> <gray>"
                + (duration < 0 ? "infinite" : ticks + " ticks") + ", power " + power + "</gray><white>.</white>"));
        return true;
    }

    /** /ui itemnbt potioneffect remove <effect> */
    private static boolean potionEffectRemove(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt potioneffect remove <effect></white>"));
            return true;
        }
        PotionEffectType type = resolveRegistry(Registry.POTION_EFFECT_TYPE, args[3]);
        if (type == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown effect: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        PotionContents old = item.getData(DataComponentTypes.POTION_CONTENTS);
        if (old == null || old.customEffects().isEmpty()) {
            p.sendMessage(MessageUtil.parse("<red>❌ No custom effects on this item.</red>"));
            return true;
        }
        List<PotionEffect> kept = new ArrayList<>();
        boolean removed = false;
        for (PotionEffect e : old.customEffects()) {
            if (e.getType().getKey().getKey().equalsIgnoreCase(type.getKey().getKey())) {
                removed = true;
            } else {
                kept.add(e);
            }
        }
        if (!removed) {
            p.sendMessage(MessageUtil.parse("<red>❌ Effect</red> <yellow>" + type.getKey().getKey() + "</yellow> <red>not found on this item.</red>"));
            return true;
        }
        PotionContents.Builder builder = PotionContents.potionContents();
        if (old.potion() != null) builder.potion(old.potion());
        if (old.customName() != null) builder.customName(old.customName());
        if (old.customColor() != null) builder.customColor(old.customColor());
        builder.addCustomEffects(kept);
        item.setData(DataComponentTypes.POTION_CONTENTS, builder.build());
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Effect</white> <yellow>" + type.getKey().getKey() + "</yellow> <white>removed.</white>"));
        return true;
    }

    /** /ui itemnbt potioneffect reset — removes all custom effects, preserves potion type/color. */
    private static boolean potionEffectReset(Player p, ItemStack item, String[] args) {
        PotionContents old = item.getData(DataComponentTypes.POTION_CONTENTS);
        if (old == null || old.customEffects().isEmpty()) {
            p.sendMessage(MessageUtil.parse("<gray>No custom effects to remove.</gray>"));
            return true;
        }
        PotionContents.Builder builder = PotionContents.potionContents();
        if (old.potion() != null) builder.potion(old.potion());
        if (old.customName() != null) builder.customName(old.customName());
        if (old.customColor() != null) builder.customColor(old.customColor());
        item.setData(DataComponentTypes.POTION_CONTENTS, builder.build());
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>All custom potion effects cleared.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // BOOK AUTHOR / BOOK TYPE
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt bookauthor <name> — set the author of a written book. */
    private static boolean bookAuthor(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt bookauthor <author></white>"));
            return true;
        }
        WrittenBookContent old = item.getData(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (old == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ This item is not a written book.</red>"));
            return true;
        }
        String author = joinFrom(args, 2);
        WrittenBookContent.Builder builder = WrittenBookContent.writtenBookContent(old.title().raw(), author);
        for (var page : old.pages()) {
            builder.addFilteredPage(page);
        }
        builder.generation(old.generation());
        builder.resolved(old.resolved());
        item.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, builder.build());
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Book author set to</white> <yellow>" + author + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt booktype <original|copy_of_original|copy_of_copy|tattered> */
    private static boolean bookType(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt booktype <original|copy_of_original|copy_of_copy|tattered></white>"));
            return true;
        }
        WrittenBookContent old = item.getData(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (old == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ This item is not a written book.</red>"));
            return true;
        }
        Integer generation = parseBookGeneration(args[2]);
        if (generation == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown book type: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use original, copy_of_original, copy_of_copy or tattered.</gray>"));
            return true;
        }
        WrittenBookContent.Builder builder = WrittenBookContent.writtenBookContent(old.title().raw(), old.author());
        for (var page : old.pages()) {
            builder.addFilteredPage(page);
        }
        builder.generation(generation);
        builder.resolved(old.resolved());
        item.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, builder.build());
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Book type set to</white> <yellow>" + args[2] + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // ATTRIBUTES
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt attribute <add|remove|reset> ... */
    private static boolean attribute(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt attribute <add|remove|reset> ...</white>"));
            return true;
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> attributeAdd(p, item, args);
            case "remove" -> attributeRemove(p, item, args);
            case "reset" -> attributeReset(p, item, args);
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown attribute action: </red><yellow>" + args[2]
                        + "</yellow><gray>. Use add, remove or reset.</gray>"));
                yield true;
            }
        };
    }

    /** /ui itemnbt attribute add <attribute> <amount> <operation> <slotGroup> */
    private static boolean attributeAdd(Player p, ItemStack item, String[] args) {
        if (args.length < 7) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt attribute add <attribute> <amount> <operation> <slotGroup></white>"));
            return true;
        }
        Attribute attribute = resolveRegistry(Registry.ATTRIBUTE, args[3]);
        if (attribute == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown attribute: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[4].trim());
        } catch (NumberFormatException e) {
            p.sendMessage(MessageUtil.parse("<red>❌ Amount must be a number: </red><yellow>" + args[4] + "</yellow>"));
            return true;
        }
        AttributeModifier.Operation operation = parseOperation(args[5]);
        if (operation == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown operation: </red><yellow>" + args[5]
                    + "</yellow><gray>. Use add_number, add_scalar or multiply_scalar_1.</gray>"));
            return true;
        }
        EquipmentSlotGroup group = parseSlotGroup(args[6]);
        if (group == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown slot group: </red><yellow>" + args[6]
                    + "</yellow><gray>. Use any, mainhand, offhand, hand, feet, legs, chest, head, armor, body or saddle.</gray>"));
            return true;
        }
        AttributeModifier modifier = new AttributeModifier(UUID.randomUUID(),
                attribute.getKey().getKey() + "_" + System.nanoTime(), amount, operation, group);
        ItemAttributeModifiers old = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
        if (old != null) {
            for (ItemAttributeModifiers.Entry entry : old.modifiers()) {
                builder.addModifier(entry.attribute(), entry.modifier(), entry.getGroup());
            }
        }
        builder.addModifier(attribute, modifier, group);
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Attribute</white> <yellow>" + attribute.getKey().getKey()
                + "</yellow> <white>added:</white> <gray>" + amount + " " + operation.name().toLowerCase()
                + " on " + args[6].toLowerCase() + "</gray><white>.</white>"));
        return true;
    }

    /** /ui itemnbt attribute remove <ID> */
    private static boolean attributeRemove(Player p, ItemStack item, String[] args) {
        if (args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt attribute remove <ID></white>"));
            return true;
        }
        Integer idx = parseInt(args[3]);
        if (idx == null || idx < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid ID: </red><yellow>" + args[3] + "</yellow>"));
            return true;
        }
        ItemAttributeModifiers old = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (old == null || old.modifiers().isEmpty()) {
            p.sendMessage(MessageUtil.parse("<red>❌ No attributes on this item.</red>"));
            return true;
        }
        List<ItemAttributeModifiers.Entry> list = new ArrayList<>(old.modifiers());
        if (idx >= list.size()) {
            p.sendMessage(MessageUtil.parse("<red>❌ ID out of range (0-</red><yellow>" + (list.size() - 1) + "</yellow><red>).</red>"));
            return true;
        }
        ItemAttributeModifiers.Entry removed = list.remove((int) idx);
        if (list.isEmpty()) {
            item.resetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        } else {
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
            for (ItemAttributeModifiers.Entry entry : list) {
                builder.addModifier(entry.attribute(), entry.modifier(), entry.getGroup());
            }
            item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
        }
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Attribute #</white><yellow>" + idx
                + "</yellow> <white>removed (</white><yellow>" + removed.attribute().getKey().getKey()
                + "</yellow><white>).</white>"));
        return true;
    }

    /** /ui itemnbt attribute reset — removes all attributes. */
    private static boolean attributeReset(Player p, ItemStack item, String[] args) {
        ItemAttributeModifiers old = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (old == null || old.modifiers().isEmpty()) {
            p.sendMessage(MessageUtil.parse("<gray>No attributes to remove.</gray>"));
            return true;
        }
        item.resetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>All attributes cleared.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // CUSTOM MODEL DATA / ITEM MODEL / COMPASS
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt cmdata <value> — set CustomModelData (float). */
    private static boolean cmData(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt cmdata <value></white>"));
            return true;
        }
        Integer value = parseInt(args[2]);
        if (value == null || value < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ CustomModelData must be a non-negative number: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        CustomModelData cmd = CustomModelData.customModelData().addFloat(value).build();
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmd);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>CustomModelData set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt itemmodel <ns:key> — set the item model. */
    private static boolean itemModel(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt itemmodel <namespace:path></white>"));
            return true;
        }
        Key model = parseResourceKey(args[2]);
        if (model == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid model key: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use namespace:path (e.g. minecraft:item/diamond_sword).</gray>"));
            return true;
        }
        item.setData(DataComponentTypes.ITEM_MODEL, model);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item model set to</white> <yellow>" + model.asString() + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt compass <clear|set> <X> <Y> <Z> <world> — magnetizes a compass to a lodestone. */
    private static boolean compass(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt compass <clear|set> [X] [Y] [Z] [world]</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.LODESTONE_TRACKER);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Compass unmagnetized.</white>"));
            return true;
        }
        if (!args[2].equalsIgnoreCase("set")) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown compass action: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use clear or set.</gray>"));
            return true;
        }
        if (args.length < 7) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt compass set <X> <Y> <Z> <world></white>"));
            return true;
        }
        Integer x = parseInt(args[3]);
        Integer y = parseInt(args[4]);
        Integer z = parseInt(args[5]);
        if (x == null || y == null || z == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid coordinates.</red>"));
            return true;
        }
        World world = Bukkit.getWorld(args[6]);
        if (world == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown world: </red><yellow>" + args[6] + "</yellow>"));
            return true;
        }
        org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z);
        item.setData(DataComponentTypes.LODESTONE_TRACKER, LodestoneTracker.lodestoneTracker(loc, true));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Compass magnetized to</white> <yellow>" + x + " " + y + " " + z
                + "</yellow> <white>in</white> <yellow>" + world.getName() + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // AXOLOTL / GOAT HORN / ARMOR TRIM
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt axltype <blue|cyan|gold|lucy|wild> — axolotl variant in a bucket. */
    private static boolean axolotlType(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt axltype <blue|cyan|gold|lucy|wild></white>"));
            return true;
        }
        Axolotl.Variant variant = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "blue" -> Axolotl.Variant.BLUE;
            case "cyan" -> Axolotl.Variant.CYAN;
            case "gold" -> Axolotl.Variant.GOLD;
            case "lucy" -> Axolotl.Variant.LUCY;
            case "wild" -> Axolotl.Variant.WILD;
            default -> null;
        };
        if (variant == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown axolotl variant: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use blue, cyan, gold, lucy or wild.</gray>"));
            return true;
        }
        item.setData(DataComponentTypes.AXOLOTL_VARIANT, variant);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Axolotl variant set to</white> <yellow>" + args[2].toLowerCase() + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt ghsound <admire|call|dream|feel|ponder|seek|sing|yearn> — goat horn sound. */
    private static boolean goatHornSound(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt ghsound <admire|call|dream|feel|ponder|seek|sing|yearn></white>"));
            return true;
        }
        MusicInstrument instrument = resolveRegistry(Registry.INSTRUMENT, args[2] + "_goat_horn");
        if (instrument == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown goat horn sound: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use admire, call, dream, feel, ponder, seek, sing or yearn.</gray>"));
            return true;
        }
        item.setData(DataComponentTypes.INSTRUMENT, instrument);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Goat horn sound set to</white> <yellow>" + args[2].toLowerCase() + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt material <material> — set the item's material (e.g. iron_ingot). */
    private static boolean material(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt material <material></white>"));
            return true;
        }
        Material material = resolveRegistry(Registry.MATERIAL, args[2]);
        if (material == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown material: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setType(material);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Material set to</white> <yellow>" + material.getKey().getKey() + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt armortrim <clear|set> [material] [pattern] */
    private static boolean armorTrim(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt armortrim <clear|set> [material] [pattern]</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.TRIM);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Armor trim cleared.</white>"));
            return true;
        }
        if (!args[2].equalsIgnoreCase("set") || args.length < 5) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt armortrim set <material> <pattern></white>"));
            return true;
        }
        TrimMaterial material = resolveRegistry(Registry.TRIM_MATERIAL, args[3]);
        TrimPattern pattern = resolveRegistry(Registry.TRIM_PATTERN, args[4]);
        if (material == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown trim material: </red><yellow>" + args[3]
                    + "</yellow><gray>. Use amethyst, copper, diamond, emerald, gold, iron, lapis, netherite, quartz, redstone or resin.</gray>"));
            return true;
        }
        if (pattern == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown trim pattern: </red><yellow>" + args[4] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.TRIM, ItemArmorTrim.itemArmorTrim(new ArmorTrim(material, pattern)));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Armor trim set:</white> <yellow>" + args[3].toLowerCase()
                + "</yellow> <white>/</white> <yellow>" + args[4].toLowerCase() + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // TOOLTIP STYLE / ITEM NAME / HIDE TOOLTIP
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt tooltipstyle <ns:key|clear> — tooltip style (e.g. minecraft:missing, custom pack styles). */
    private static boolean tooltipStyle(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt tooltipstyle <namespace:path|clear></white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.TOOLTIP_STYLE);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Tooltip style cleared.</white>"));
            return true;
        }
        Key key = parseResourceKey(args[2]);
        if (key == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid tooltip style key: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use namespace:path.</gray>"));
            return true;
        }
        item.setData(DataComponentTypes.TOOLTIP_STYLE, key);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Tooltip style set to</white> <yellow>" + key.asString() + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt itemname "<miniMessage>"|clear — set the item name (grey, non-custom name). */
    private static boolean itemName(Player p, ItemStack item, String[] args) {
        String text = joinFrom(args, 2);
        if (text.isEmpty() || text.equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.ITEM_NAME);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item name removed.</white>"));
            return true;
        }
        Component name = MM.deserialize(text);
        item.setData(DataComponentTypes.ITEM_NAME, name);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item name set to</white> <yellow>" + text + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt hidetooltip <true|false> — hide the whole tooltip (including the item name). */
    private static boolean hideTooltip(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt hidetooltip <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[2]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Use true or false: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        if (value) {
            item.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                    io.papermc.paper.datacomponent.item.TooltipDisplay.tooltipDisplay().hideTooltip(true));
        } else {
            item.resetData(DataComponentTypes.TOOLTIP_DISPLAY);
        }
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Tooltip hiding set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // USE COOLDOWN / USE REMAINDER
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt usecooldown <seconds> [group]|clear — set the use cooldown. */
    private static boolean useCooldown(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt usecooldown <seconds> [cooldownGroup|clear]</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.USE_COOLDOWN);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Use cooldown removed.</white>"));
            return true;
        }
        float seconds;
        try {
            seconds = Float.parseFloat(args[2].trim());
        } catch (NumberFormatException e) {
            p.sendMessage(MessageUtil.parse("<red>❌ Cooldown must be a number (seconds): </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        if (seconds < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Cooldown cannot be negative.</red>"));
            return true;
        }
        UseCooldown.Builder builder = UseCooldown.useCooldown(seconds);
        if (args.length >= 4 && !args[3].equalsIgnoreCase("clear")) {
            Key group = parseResourceKey(args[3]);
            if (group == null) {
                p.sendMessage(MessageUtil.parse("<red>❌ Invalid cooldown group: </red><yellow>" + args[3]
                        + "</yellow><gray>. Use namespace:path.</gray>"));
                return true;
            }
            builder.cooldownGroup(group);
        }
        item.setData(DataComponentTypes.USE_COOLDOWN, builder);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Use cooldown set to</white> <yellow>" + seconds
                + "</yellow><white> seconds.</white>"));
        return true;
    }

    /** /ui itemnbt useremainder <material> [amount]|clear — the item left after use. */
    private static boolean useRemainder(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt useremainder <material> [amount]|clear</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.USE_REMAINDER);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Use remainder removed.</white>"));
            return true;
        }
        Material material = resolveRegistry(Registry.MATERIAL, args[2]);
        if (material == null || material.isAir()) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown material: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            Integer parsed = parseInt(args[3]);
            if (parsed == null || parsed < 1 || parsed > 99) {
                p.sendMessage(MessageUtil.parse("<red>❌ Amount must be 1-99: </red><yellow>" + args[3] + "</yellow>"));
                return true;
            }
            amount = parsed;
        }
        item.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(new ItemStack(material, amount)));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Use remainder set to</white> <yellow>" + material.getKey().getKey()
                + "</yellow> <white>×</white> <yellow>" + amount + "</yellow><white>.</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // CAN BREAK / CAN PLACE ON
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt canbreak|canplaceon <add <block...>|clear|info> — adventure-mode block rules. */
    private static boolean canBreakPlace(Player p, ItemStack item, String[] args, boolean isBreak) {
        String cmd = isBreak ? "canbreak" : "canplaceon";
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt " + cmd + " <add <block...>|clear|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("clear")) {
            item.resetData(isBreak ? DataComponentTypes.CAN_BREAK : DataComponentTypes.CAN_PLACE_ON);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>" + (isBreak ? "Can-break" : "Can-place-on")
                    + " rules cleared.</white>"));
            return true;
        }
        if (action.equals("info")) {
            ItemAdventurePredicate old = item.getData(isBreak ? DataComponentTypes.CAN_BREAK : DataComponentTypes.CAN_PLACE_ON);
            if (old == null || old.predicates().isEmpty()) {
                p.sendMessage(MessageUtil.parse("<gray>No " + (isBreak ? "can-break" : "can-place-on") + " rules.</gray>"));
                return true;
            }
            List<String> blocks = new ArrayList<>();
            for (BlockPredicate predicate : old.predicates()) {
                for (BlockType bt : predicate.blocks().resolve(Registry.BLOCK)) {
                    blocks.add(bt.getKey().getKey());
                }
            }
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>" + (isBreak ? "Can break" : "Can place on")
                    + "</white> <gray>(" + blocks.size() + " blocks):</gray> <white>" + String.join(", ", blocks) + "</white>"));
            return true;
        }
        if (!action.equals("add") || args.length < 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt " + cmd + " <add <block...>|clear|info></white>"));
            return true;
        }
        List<BlockType> blockTypes = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            BlockType bt = resolveRegistry(Registry.BLOCK, args[i]);
            if (bt == null) {
                unknown.add(args[i]);
            } else {
                blockTypes.add(bt);
            }
        }
        if (blockTypes.isEmpty()) {
            p.sendMessage(MessageUtil.parse("<red>❌ No valid blocks provided.</red>"
                    + (unknown.isEmpty() ? "" : " <gray>Unknown: " + String.join(", ", unknown) + "</gray>")));
            return true;
        }
        RegistryKeySet<BlockType> keySet = RegistrySet.keySetFromValues(RegistryKey.BLOCK, blockTypes);
        BlockPredicate predicate = BlockPredicate.predicate().blocks(keySet).build();
        ItemAdventurePredicate old = item.getData(isBreak ? DataComponentTypes.CAN_BREAK : DataComponentTypes.CAN_PLACE_ON);
        List<BlockPredicate> predicates = new ArrayList<>();
        if (old != null) predicates.addAll(old.predicates());
        predicates.add(predicate);
        item.setData(isBreak ? DataComponentTypes.CAN_BREAK : DataComponentTypes.CAN_PLACE_ON,
                ItemAdventurePredicate.itemAdventurePredicate(predicates));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>" + (isBreak ? "Can break" : "Can place on")
                + " updated (+" + blockTypes.size() + " blocks).</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // ENCHANTABLE / MAP
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt enchantable <value>|clear — whether the item can be enchanted. */
    private static boolean enchantable(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt enchantable <value>|clear</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.ENCHANTABLE);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Enchantable value cleared.</white>"));
            return true;
        }
        Integer value = parseInt(args[2]);
        if (value == null || value < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Enchantable value must be a non-negative number: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(value));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Enchantable set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt mapid <id>|clear — set the filled-map ID. */
    private static boolean mapId(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt mapid <id>|clear</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.MAP_ID);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map ID cleared.</white>"));
            return true;
        }
        Integer id = parseInt(args[2]);
        if (id == null || id < 0) {
            p.sendMessage(MessageUtil.parse("<red>❌ Map ID must be a non-negative number: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.MAP_ID, MapId.mapId(id));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map ID set to</white> <yellow>" + id + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt mapcolor <R> <G> <B>|clear — map tint color. */
    private static boolean mapColor(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt mapcolor <R> <G> <B>|clear</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.MAP_COLOR);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map color cleared.</white>"));
            return true;
        }
        if (args.length < 5) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt mapcolor <R> <G> <B></white>"));
            return true;
        }
        Integer r = parseInt(args[2]);
        Integer g = parseInt(args[3]);
        Integer b = parseInt(args[4]);
        if (r == null || g == null || b == null || r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            p.sendMessage(MessageUtil.parse("<red>❌ RGB values must be 0-255.</red>"));
            return true;
        }
        item.setData(DataComponentTypes.MAP_COLOR,
                MapItemColor.mapItemColor().color(Color.fromRGB(r, g, b)).build());
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map color set to RGB(</white><yellow>" + r
                + "</yellow><white>, </white><yellow>" + g + "</yellow><white>, </white><yellow>" + b + "</yellow><white>).</white>"));
        return true;
    }

    /** /ui itemnbt mappost <lock|scale|clear> — map post-processing. */
    private static boolean mapPost(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt mappost <lock|scale|clear></white>"));
            return true;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "clear" -> {
                item.resetData(DataComponentTypes.MAP_POST_PROCESSING);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map post-processing cleared.</white>"));
            }
            case "lock" -> {
                item.setData(DataComponentTypes.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map locked.</white>"));
            }
            case "scale" -> {
                item.setData(DataComponentTypes.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map scale applied.</white>"));
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown map post-processing: </red><yellow>" + args[2]
                        + "</yellow><gray>. Use lock, scale or clear.</gray>"));
            }
        }
        return true;
    }

    /** /ui itemnbt mapdeco <add <name> <type> <x> <z> <rotation>|remove <name>|clear|info> — map markers. */
    private static boolean mapDeco(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt mapdeco <add <name> <type> <x> <z> <rotation>|remove <name>|clear|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        MapDecorations old = item.getData(DataComponentTypes.MAP_DECORATIONS);
        Map<String, MapDecorations.DecorationEntry> map = old == null
                ? new java.util.LinkedHashMap<>()
                : new java.util.LinkedHashMap<>(old.decorations());
        switch (action) {
            case "clear" -> {
                item.resetData(DataComponentTypes.MAP_DECORATIONS);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map decorations cleared.</white>"));
                return true;
            }
            case "info" -> {
                if (map.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>No map decorations.</gray>"));
                } else {
                    List<String> lines = new ArrayList<>();
                    for (var entry : map.entrySet()) {
                        lines.add("<yellow>" + entry.getKey() + "</yellow> <gray>→</gray> <white>"
                                + entry.getValue().type().getKey().getKey() + "</white> <gray>("
                                + entry.getValue().x() + ", " + entry.getValue().z() + ", rot "
                                + entry.getValue().rotation() + ")</gray>");
                    }
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Map decorations</white> <gray>(" + lines.size()
                            + "):</gray> " + String.join("<gray>, </gray>", lines)));
                }
                return true;
            }
            case "remove" -> {
                if (args.length < 4 || !map.containsKey(args[3])) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Decoration </red><yellow>" + (args.length < 4 ? "?" : args[3])
                            + "</yellow><red> not found.</red>"));
                    return true;
                }
                map.remove(args[3]);
                if (map.isEmpty()) {
                    item.resetData(DataComponentTypes.MAP_DECORATIONS);
                } else {
                    item.setData(DataComponentTypes.MAP_DECORATIONS, MapDecorations.mapDecorations(map));
                }
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Decoration </white><yellow>" + args[3]
                        + "</yellow> <white>removed.</white>"));
                return true;
            }
            case "add" -> {
                if (args.length < 8) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt mapdeco add <name> <type> <x> <z> <rotation></white>"));
                    return true;
                }
                MapCursor.Type type = resolveRegistry(Registry.MAP_DECORATION_TYPE, args[4]);
                if (type == null) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Unknown decoration type: </red><yellow>" + args[4] + "</yellow>"));
                    return true;
                }
                Double x = parseDouble(args[5]);
                Double z = parseDouble(args[6]);
                Float rotation = parseFloat(args[7]);
                if (x == null || z == null || rotation == null) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Coordinates must be numbers.</red>"));
                    return true;
                }
                map.put(args[3], MapDecorations.decorationEntry(type, x, z, rotation));
                item.setData(DataComponentTypes.MAP_DECORATIONS, MapDecorations.mapDecorations(map));
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Decoration </white><yellow>" + args[3]
                        + "</yellow> <white>added.</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use add, remove, clear or info.</gray>"));
                return true;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // WRITABLE BOOK / SUSPICIOUS STEW / DEATH PROTECTION
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt writablebook <addpage <text>|clearpages|info> — book & quill pages. */
    private static boolean writableBook(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt writablebook <addpage <text>|clearpages|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "clearpages" -> {
                item.resetData(DataComponentTypes.WRITABLE_BOOK_CONTENT);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Book pages cleared.</white>"));
                return true;
            }
            case "info" -> {
                WritableBookContent old = item.getData(DataComponentTypes.WRITABLE_BOOK_CONTENT);
                if (old == null || old.pages().isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>This is not a book & quill with pages.</gray>"));
                } else {
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Book has</white> <yellow>"
                            + old.pages().size() + "</yellow> <white>page(s).</white>"));
                }
                return true;
            }
            case "addpage" -> {
                String text = joinFrom(args, 3);
                if (text.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Page text cannot be empty.</red>"));
                    return true;
                }
                WritableBookContent old = item.getData(DataComponentTypes.WRITABLE_BOOK_CONTENT);
                WritableBookContent.Builder builder = old == null
                        ? WritableBookContent.writeableBookContent()
                        : WritableBookContent.writeableBookContent();
                if (old != null) {
                    for (var page : old.pages()) {
                        builder.addFilteredPage(page);
                    }
                }
                builder.addPage(text);
                item.setData(DataComponentTypes.WRITABLE_BOOK_CONTENT, builder);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Page added.</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use addpage, clearpages or info.</gray>"));
                return true;
            }
        }
    }

    /** /ui itemnbt suspiciousstew <add <effect> <ticks>|remove <effect>|reset|info> — stew effects. */
    private static boolean suspiciousStew(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt suspiciousstew <add <effect> <ticks>|remove <effect>|reset|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        SuspiciousStewEffects old = item.getData(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS);
        List<SuspiciousEffectEntry> effects = old == null ? new ArrayList<>() : new ArrayList<>(old.effects());
        switch (action) {
            case "reset" -> {
                item.resetData(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Stew effects reset.</white>"));
                return true;
            }
            case "info" -> {
                if (effects.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>No stew effects.</gray>"));
                } else {
                    List<String> lines = new ArrayList<>();
                    for (SuspiciousEffectEntry e : effects) {
                        lines.add("<yellow>" + e.effect().getKey().getKey() + "</yellow> <gray>"
                                + e.duration() + " ticks</gray>");
                    }
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Stew effects:</white> "
                            + String.join("<gray>, </gray>", lines)));
                }
                return true;
            }
            case "add" -> {
                if (args.length < 5) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt suspiciousstew add <effect> <ticks></white>"));
                    return true;
                }
                PotionEffectType type = resolveRegistry(Registry.POTION_EFFECT_TYPE, args[3]);
                if (type == null) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Unknown effect: </red><yellow>" + args[3] + "</yellow>"));
                    return true;
                }
                Integer ticks = parseInt(args[4]);
                if (ticks == null || ticks < 0) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Duration must be ticks (non-negative): </red><yellow>" + args[4] + "</yellow>"));
                    return true;
                }
                effects.add(SuspiciousEffectEntry.create(type, ticks));
                item.setData(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.suspiciousStewEffects(effects));
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Stew effect</white> <yellow>" + type.getKey().getKey()
                        + "</yellow> <white>added.</white>"));
                return true;
            }
            case "remove" -> {
                if (args.length < 4) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt suspiciousstew remove <effect></white>"));
                    return true;
                }
                PotionEffectType type = resolveRegistry(Registry.POTION_EFFECT_TYPE, args[3]);
                if (type == null) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Unknown effect: </red><yellow>" + args[3] + "</yellow>"));
                    return true;
                }
                boolean removed = effects.removeIf(e -> e.effect().getKey().getKey().equalsIgnoreCase(type.getKey().getKey()));
                if (!removed) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Effect not found.</red>"));
                    return true;
                }
                if (effects.isEmpty()) {
                    item.resetData(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS);
                } else {
                    item.setData(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.suspiciousStewEffects(effects));
                }
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Effect removed.</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use add, remove, reset or info.</gray>"));
                return true;
            }
        }
    }

    /** /ui itemnbt deathprotection <true|false> — totem-of-undying-style protection. */
    private static boolean deathProtection(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt deathprotection <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[2]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Use true or false: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        if (value) {
            // The default totem effects: absorption 2 (45s), regeneration 2 (45s), fire resistance (40s)
            List<PotionEffect> totemEffects = List.of(
                    new PotionEffect(PotionEffectType.ABSORPTION, 900, 1, false, true, true),
                    new PotionEffect(PotionEffectType.REGENERATION, 900, 1, false, true, true),
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0, false, true, true));
            item.setData(DataComponentTypes.DEATH_PROTECTION,
                    DeathProtection.deathProtection(List.of(ConsumeEffect.applyStatusEffects(totemEffects, 1.0f))));
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Death protection enabled (totem effects).</white>"));
        } else {
            item.resetData(DataComponentTypes.DEATH_PROTECTION);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Death protection disabled.</white>"));
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // JUKEBOX / NOTE BLOCK / BUNDLE / POT / LOOT
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt jukeboxplayable <song|clear> — make the item playable on a jukebox. */
    private static boolean jukeboxPlayable(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt jukeboxplayable <song|clear></white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.JUKEBOX_PLAYABLE);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Jukebox song cleared.</white>"));
            return true;
        }
        JukeboxSong song = resolveRegistry(Registry.JUKEBOX_SONG, args[2]);
        if (song == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown song: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use a jukebox song key (e.g. thirteen, cat, pigstep).</gray>"));
            return true;
        }
        item.setData(DataComponentTypes.JUKEBOX_PLAYABLE, JukeboxPlayable.jukeboxPlayable(song));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Jukebox song set to</white> <yellow>" + song.getKey().getKey()
                + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt noteblocksound <sound|clear> — the sound a note block plays from this item. */
    private static boolean noteBlockSound(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt noteblocksound <sound|clear></white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.NOTE_BLOCK_SOUND);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Note block sound cleared.</white>"));
            return true;
        }
        Key key = resolveSoundKey(args[2]);
        if (key == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown sound: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.NOTE_BLOCK_SOUND, key);
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Note block sound set to</white> <yellow>" + key.asString()
                + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt bundle <add <material> [amount]|clear|info> — bundle contents. */
    private static boolean bundle(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt bundle <add <material> [amount]|clear|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        BundleContents old = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
        List<ItemStack> contents = old == null ? new ArrayList<>() : new ArrayList<>(old.contents());
        switch (action) {
            case "clear" -> {
                item.resetData(DataComponentTypes.BUNDLE_CONTENTS);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Bundle contents cleared.</white>"));
                return true;
            }
            case "info" -> {
                if (contents.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>This is not a bundle with contents.</gray>"));
                } else {
                    List<String> lines = new ArrayList<>();
                    for (ItemStack is : contents) {
                        lines.add("<yellow>" + is.getType().getKey().getKey() + "</yellow> <gray>×" + is.getAmount() + "</gray>");
                    }
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Bundle:</white> " + String.join("<gray>, </gray>", lines)));
                }
                return true;
            }
            case "add" -> {
                if (args.length < 4) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt bundle add <material> [amount]</white>"));
                    return true;
                }
                Material material = resolveRegistry(Registry.MATERIAL, args[3]);
                if (material == null || material.isAir()) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Unknown material: </red><yellow>" + args[3] + "</yellow>"));
                    return true;
                }
                int amount = 1;
                if (args.length >= 5) {
                    Integer parsed = parseInt(args[4]);
                    if (parsed == null || parsed < 1 || parsed > 99) {
                        p.sendMessage(MessageUtil.parse("<red>❌ Amount must be 1-99: </red><yellow>" + args[4] + "</yellow>"));
                        return true;
                    }
                    amount = parsed;
                }
                contents.add(new ItemStack(material, amount));
                item.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(contents));
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Added</white> <yellow>" + material.getKey().getKey()
                        + "</yellow> <white>×</white> <yellow>" + amount + "</yellow> <white>to the bundle.</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use add, clear or info.</gray>"));
                return true;
            }
        }
    }

    /** /ui itemnbt potdecorations <back> <left> <right> <front>|clear — flower pot decorations. */
    private static boolean potDecorations(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt potdecorations <back> <left> <right> <front>|clear</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.POT_DECORATIONS);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Pot decorations cleared.</white>"));
            return true;
        }
        if (args.length < 6) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt potdecorations <back> <left> <right> <front></white>"));
            return true;
        }
        ItemType back = resolveRegistry(Registry.ITEM, args[2]);
        ItemType left = resolveRegistry(Registry.ITEM, args[3]);
        ItemType right = resolveRegistry(Registry.ITEM, args[4]);
        ItemType front = resolveRegistry(Registry.ITEM, args[5]);
        if (back == null || left == null || right == null || front == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Unknown item in decorations.</red> <gray>Use item keys (e.g. minecraft:brick).</gray>"));
            return true;
        }
        item.setData(DataComponentTypes.POT_DECORATIONS, PotDecorations.potDecorations(back, left, right, front));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Pot decorations set.</white>"));
        return true;
    }

    /** /ui itemnbt containerloot <loottable> [seed]|clear — container loot table. */
    private static boolean containerLoot(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt containerloot <loottable> [seed]|clear</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.CONTAINER_LOOT);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Container loot cleared.</white>"));
            return true;
        }
        Key table = parseResourceKey(args[2]);
        if (table == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Invalid loot table key: </red><yellow>" + args[2]
                    + "</yellow><gray>. Use namespace:path (e.g. minecraft:chests/simple_dungeon).</gray>"));
            return true;
        }
        long seed = 0;
        if (args.length >= 4) {
            try {
                seed = Long.parseLong(args[3].trim());
            } catch (NumberFormatException e) {
                p.sendMessage(MessageUtil.parse("<red>❌ Seed must be a number: </red><yellow>" + args[3] + "</yellow>"));
                return true;
            }
        }
        item.setData(DataComponentTypes.CONTAINER_LOOT, SeededContainerLoot.seededContainerLoot(table, seed));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Container loot set to</white> <yellow>" + table.asString()
                + "</yellow> <white>(seed</white> <yellow>" + seed + "</yellow><white>).</white>"));
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // OMINOUS BOTTLE / INTANGIBLE / FIREWORK / CHARGED / CONTAINER / RECIPES / REPAIRABLE
    // ══════════════════════════════════════════════════════════════

    /** /ui itemnbt ominousbottle <amplifier>|clear — ominous bottle amplifier. */
    private static boolean ominousBottle(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt ominousbottle <amplifier>|clear</white>"));
            return true;
        }
        if (args[2].equalsIgnoreCase("clear")) {
            item.resetData(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER);
            p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Ominous bottle amplifier cleared.</white>"));
            return true;
        }
        Integer amplifier = parseInt(args[2]);
        if (amplifier == null || amplifier < 0 || amplifier > 4) {
            p.sendMessage(MessageUtil.parse("<red>❌ Amplifier must be 0-4: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        item.setData(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER, OminousBottleAmplifier.amplifier(amplifier));
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Ominous bottle amplifier set to</white> <yellow>" + amplifier
                + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt intangibleprojectile <true|false> — projectile passes through entities when fired. */
    private static boolean intangibleProjectile(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt intangibleprojectile <true|false></white>"));
            return true;
        }
        Boolean value = parseBool(args[2]);
        if (value == null) {
            p.sendMessage(MessageUtil.parse("<red>❌ Use true or false: </red><yellow>" + args[2] + "</yellow>"));
            return true;
        }
        if (value) {
            item.setData(DataComponentTypes.INTANGIBLE_PROJECTILE);
        } else {
            item.resetData(DataComponentTypes.INTANGIBLE_PROJECTILE);
        }
        p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Intangible projectile set to</white> <yellow>" + value + "</yellow><white>.</white>"));
        return true;
    }

    /** /ui itemnbt firework <add <type> <r> <g> <b> [flicker] [trail]|remove <index>|clear|info> — firework rocket. */
    private static boolean firework(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt firework <add <type> <R> <G> <B> [flicker] [trail]|remove <index>|clear|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        Fireworks old = item.getData(DataComponentTypes.FIREWORKS);
        List<FireworkEffect> effects = old == null ? new ArrayList<>() : new ArrayList<>(old.effects());
        int flight = old == null ? 1 : old.flightDuration();
        switch (action) {
            case "clear" -> {
                item.resetData(DataComponentTypes.FIREWORKS);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Firework effects cleared.</white>"));
                return true;
            }
            case "info" -> {
                if (effects.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>No firework effects. Use </gray><yellow>/ui itemnbt firework add ...</yellow>"));
                } else {
                    List<String> lines = new ArrayList<>();
                    for (int i = 0; i < effects.size(); i++) {
                        FireworkEffect e = effects.get(i);
                        lines.add("<yellow>#" + i + "</yellow> <white>" + e.getType().name().toLowerCase()
                                + "</white> <gray>" + (e.hasFlicker() ? "flicker " : "")
                                + (e.hasTrail() ? "trail" : "") + "</gray>");
                    }
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Firework</white> <gray>(" + effects.size()
                            + " effects, flight " + flight + "):</gray> " + String.join("<gray>, </gray>", lines)));
                }
                return true;
            }
            case "remove" -> {
                if (args.length < 4) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt firework remove <index></white>"));
                    return true;
                }
                Integer idx = parseInt(args[3]);
                if (idx == null || idx < 0 || idx >= effects.size()) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Index out of range (0-</red><yellow>" + (effects.size() - 1) + "</yellow><red>).</red>"));
                    return true;
                }
                effects.remove((int) idx);
                if (effects.isEmpty()) {
                    item.resetData(DataComponentTypes.FIREWORKS);
                } else {
                    item.setData(DataComponentTypes.FIREWORKS, Fireworks.fireworks(effects, flight));
                }
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Firework effect #</white><yellow>" + idx
                        + "</yellow> <white>removed.</white>"));
                return true;
            }
            case "add" -> {
                if (args.length < 6) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt firework add <type> <R> <G> <B> [flicker] [trail]</white>"));
                    return true;
                }
                FireworkEffect.Type type;
                try {
                    type = FireworkEffect.Type.valueOf(args[3].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Unknown firework type: </red><yellow>" + args[3]
                            + "</yellow><gray>. Use ball, ball_large, burst, creeper or star.</gray>"));
                    return true;
                }
                Integer r = parseInt(args[4]);
                Integer g = parseInt(args[5]);
                Integer b = parseInt(args[6]);
                if (r == null || g == null || b == null || r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
                    p.sendMessage(MessageUtil.parse("<red>❌ RGB values must be 0-255.</red>"));
                    return true;
                }
                boolean flicker = args.length >= 8 && parseBool(args[7]) == Boolean.TRUE;
                boolean trail = args.length >= 9 && parseBool(args[8]) == Boolean.TRUE;
                FireworkEffect.Builder fb = FireworkEffect.builder()
                        .with(type)
                        .withColor(Color.fromRGB(r, g, b));
                if (flicker) fb.flicker(true);
                if (trail) fb.trail(true);
                effects.add(fb.build());
                item.setData(DataComponentTypes.FIREWORKS, Fireworks.fireworks(effects, flight));
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Firework effect added (</white><yellow>" + effects.size()
                        + "</yellow> <white>total, flight</white> <yellow>" + flight + "</yellow><white>).</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use add, remove, clear or info.</gray>"));
                return true;
            }
        }
    }

    /** /ui itemnbt chargedprojectiles <add <material> [amount]|clear|info> — crossbow-loaded projectiles. */
    private static boolean chargedProjectiles(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt chargedprojectiles <add <material> [amount]|clear|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        ChargedProjectiles old = item.getData(DataComponentTypes.CHARGED_PROJECTILES);
        List<ItemStack> projectiles = old == null ? new ArrayList<>() : new ArrayList<>(old.projectiles());
        switch (action) {
            case "clear" -> {
                item.resetData(DataComponentTypes.CHARGED_PROJECTILES);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Charged projectiles cleared.</white>"));
                return true;
            }
            case "info" -> {
                if (projectiles.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>No charged projectiles.</gray>"));
                } else {
                    List<String> lines = new ArrayList<>();
                    for (ItemStack is : projectiles) {
                        lines.add("<yellow>" + is.getType().getKey().getKey() + "</yellow> <gray>×" + is.getAmount() + "</gray>");
                    }
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Charged:</white> " + String.join("<gray>, </gray>", lines)));
                }
                return true;
            }
            case "add" -> {
                if (args.length < 4) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt chargedprojectiles add <material> [amount]</white>"));
                    return true;
                }
                Material material = resolveRegistry(Registry.MATERIAL, args[3]);
                if (material == null || material.isAir()) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Unknown material: </red><yellow>" + args[3] + "</yellow>"));
                    return true;
                }
                int amount = 1;
                if (args.length >= 5) {
                    Integer parsed = parseInt(args[4]);
                    if (parsed == null || parsed < 1 || parsed > 99) {
                        p.sendMessage(MessageUtil.parse("<red>❌ Amount must be 1-99: </red><yellow>" + args[4] + "</yellow>"));
                        return true;
                    }
                    amount = parsed;
                }
                projectiles.add(new ItemStack(material, amount));
                item.setData(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectiles.chargedProjectiles(projectiles));
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Projectile added.</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use add, clear or info.</gray>"));
                return true;
            }
        }
    }

    /** /ui itemnbt container <add <material> [amount]|clear|info> — container (shulker) contents. */
    private static boolean containerContents(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt container <add <material> [amount]|clear|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        ItemContainerContents old = item.getData(DataComponentTypes.CONTAINER);
        List<ItemStack> contents = old == null ? new ArrayList<>() : new ArrayList<>(old.contents());
        switch (action) {
            case "clear" -> {
                item.resetData(DataComponentTypes.CONTAINER);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Container contents cleared.</white>"));
                return true;
            }
            case "info" -> {
                if (contents.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>No container contents.</gray>"));
                } else {
                    List<String> lines = new ArrayList<>();
                    for (ItemStack is : contents) {
                        if (is == null || is.getType().isAir()) continue;
                        lines.add("<yellow>" + is.getType().getKey().getKey() + "</yellow> <gray>×" + is.getAmount() + "</gray>");
                    }
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Container:</white> " + String.join("<gray>, </gray>", lines)));
                }
                return true;
            }
            case "add" -> {
                if (args.length < 4) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt container add <material> [amount]</white>"));
                    return true;
                }
                Material material = resolveRegistry(Registry.MATERIAL, args[3]);
                if (material == null || material.isAir()) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Unknown material: </red><yellow>" + args[3] + "</yellow>"));
                    return true;
                }
                int amount = 1;
                if (args.length >= 5) {
                    Integer parsed = parseInt(args[4]);
                    if (parsed == null || parsed < 1 || parsed > 99) {
                        p.sendMessage(MessageUtil.parse("<red>❌ Amount must be 1-99: </red><yellow>" + args[4] + "</yellow>"));
                        return true;
                    }
                    amount = parsed;
                }
                contents.add(new ItemStack(material, amount));
                item.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(contents));
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Item added to the container.</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use add, clear or info.</gray>"));
                return true;
            }
        }
    }

    /** /ui itemnbt recipes <add <key>|clear|info> — knowledge-book recipes. */
    private static boolean recipes(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt recipes <add <recipe-key>|clear|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        List<Key> keys = item.getData(DataComponentTypes.RECIPES);
        List<Key> list = keys == null ? new ArrayList<>() : new ArrayList<>(keys);
        switch (action) {
            case "clear" -> {
                item.resetData(DataComponentTypes.RECIPES);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Recipes cleared.</white>"));
                return true;
            }
            case "info" -> {
                if (list.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>No recipes on this item.</gray>"));
                } else {
                    List<String> lines = new ArrayList<>();
                    for (Key k : list) lines.add("<yellow>" + k.asString() + "</yellow>");
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Recipes:</white> " + String.join("<gray>, </gray>", lines)));
                }
                return true;
            }
            case "add" -> {
                if (args.length < 4) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt recipes add <recipe-key></white>"));
                    return true;
                }
                Key key = parseResourceKey(args[3]);
                if (key == null) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Invalid recipe key: </red><yellow>" + args[3]
                            + "</yellow><gray>. Use namespace:path.</gray>"));
                    return true;
                }
                if (!list.contains(key)) list.add(key);
                item.setData(DataComponentTypes.RECIPES, list);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Recipe </white><yellow>" + key.asString()
                        + "</yellow> <white>added.</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use add, clear or info.</gray>"));
                return true;
            }
        }
    }

    /** /ui itemnbt repairable <add <material...>|clear|info> — anvil repair materials. */
    private static boolean repairable(Player p, ItemStack item, String[] args) {
        if (args.length < 3) {
            p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt repairable <add <material...>|clear|info></white>"));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        Repairable old = item.getData(DataComponentTypes.REPAIRABLE);
        switch (action) {
            case "clear" -> {
                item.resetData(DataComponentTypes.REPAIRABLE);
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Repair materials cleared.</white>"));
                return true;
            }
            case "info" -> {
                if (old == null || old.types().isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<gray>No repair materials on this item.</gray>"));
                } else {
                    List<String> lines = new ArrayList<>();
                    for (ItemType t : old.types().resolve(Registry.ITEM)) {
                        lines.add("<yellow>" + t.getKey().getKey() + "</yellow>");
                    }
                    p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Repairable with:</white> " + String.join("<gray>, </gray>", lines)));
                }
                return true;
            }
            case "add" -> {
                if (args.length < 4) {
                    p.sendMessage(MessageUtil.parse("<red>❌ Usage: </red><white>/ui itemnbt repairable add <material...></white>"));
                    return true;
                }
                List<ItemType> items = new ArrayList<>();
                List<String> unknown = new ArrayList<>();
                for (int i = 3; i < args.length; i++) {
                    ItemType it = resolveRegistry(Registry.ITEM, args[i]);
                    if (it == null) unknown.add(args[i]);
                    else items.add(it);
                }
                if (items.isEmpty()) {
                    p.sendMessage(MessageUtil.parse("<red>❌ No valid items provided.</red>"
                            + (unknown.isEmpty() ? "" : " <gray>Unknown: " + String.join(", ", unknown) + "</gray>")));
                    return true;
                }
                List<ItemType> merged = new ArrayList<>();
                if (old != null) {
                    merged.addAll(old.types().resolve(Registry.ITEM));
                }
                for (ItemType t : items) {
                    if (!merged.contains(t)) merged.add(t);
                }
                item.setData(DataComponentTypes.REPAIRABLE,
                        Repairable.repairable(RegistrySet.keySetFromValues(RegistryKey.ITEM, merged)));
                p.sendMessage(MessageUtil.parse("<green>✔</green> <white>Repair materials updated (+</white><yellow>" + items.size()
                        + "</yellow><white>).</white>"));
                return true;
            }
            default -> {
                p.sendMessage(MessageUtil.parse("<red>❌ Unknown action: </red><yellow>" + action
                        + "</yellow><gray>. Use add, clear or info.</gray>"));
                return true;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    /** Joins args from {@code start} with spaces and strips one pair of surrounding quotes. */
    private static String joinFrom(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        String result = sb.toString().trim();
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    /** Parses an integer, returns null on failure. */
    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a double, returns null on failure. */
    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a float, returns null on failure. */
    private static Float parseFloat(String raw) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses true/false (case-insensitive), returns null otherwise. */
    private static Boolean parseBool(String raw) {
        if (raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("false")) return false;
        return null;
    }

    /**
     * Resolves a registry entry by its key name. Accepts {@code name},
     * {@code minecraft:name} and {@code namespace:name} (full key).
     */
    private static <T extends Keyed> T resolveRegistry(Registry<T> registry, String input) {
        String norm = input.trim().toLowerCase(Locale.ROOT);
        String key = norm.contains(":") ? norm.substring(norm.indexOf(':') + 1) : norm;

        try {
            T value = registry.get(NamespacedKey.minecraft(key));
            if (value != null) return value;
        } catch (IllegalArgumentException ignored) {
            // invalid key
        }
        try {
            NamespacedKey full = NamespacedKey.fromString(norm);
            if (full != null) {
                T value = registry.get(full);
                if (value != null) return value;
            }
        } catch (IllegalArgumentException ignored) {
            // invalid key
        }
        for (T value : registry) {
            if (value.getKey().getKey().equalsIgnoreCase(key)) return value;
        }
        return null;
    }

    /** Resolves a sound to an adventure Key (registry first, then raw ns:path). */
    private static Key resolveSoundKey(String input) {
        Sound sound = resolveRegistry(Registry.SOUNDS, input);
        if (sound != null) {
            NamespacedKey sk = sound.getKey();
            return Key.key(sk.getNamespace(), sk.getKey());
        }
        // Allow sounds from resource packs that are not in the registry
        return parseResourceKey(input);
    }

    /** Parses "namespace:path" (bare path → minecraft:) into an adventure Key. */
    private static Key parseResourceKey(String input) {
        String norm = input.trim().toLowerCase(Locale.ROOT);
        if (norm.isEmpty()) return null;
        try {
            if (norm.contains(":")) {
                String[] parts = norm.split(":", 2);
                return Key.key(parts[0], parts[1]);
            }
            return Key.key("minecraft", norm);
        } catch (Exception e) {
            return null;
        }
    }

    private static EquipmentSlot parseSlot(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "body" -> EquipmentSlot.BODY;
            case "chest" -> EquipmentSlot.CHEST;
            case "feet" -> EquipmentSlot.FEET;
            case "hand" -> EquipmentSlot.HAND;
            case "head" -> EquipmentSlot.HEAD;
            case "legs" -> EquipmentSlot.LEGS;
            case "off_hand" -> EquipmentSlot.OFF_HAND;
            case "saddle" -> EquipmentSlot.SADDLE;
            default -> null;
        };
    }

    private static ItemUseAnimation parseAnimation(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "block" -> ItemUseAnimation.BLOCK;
            case "bow" -> ItemUseAnimation.BOW;
            case "brush" -> ItemUseAnimation.BRUSH;
            case "crossbow" -> ItemUseAnimation.CROSSBOW;
            case "drink" -> ItemUseAnimation.DRINK;
            case "eat" -> ItemUseAnimation.EAT;
            case "none" -> ItemUseAnimation.NONE;
            case "spear" -> ItemUseAnimation.SPEAR;
            case "spyglass" -> ItemUseAnimation.SPYGLASS;
            case "toot_horn" -> ItemUseAnimation.TOOT_HORN;
            default -> null;
        };
    }

    private static ItemRarity parseRarity(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "common" -> ItemRarity.COMMON;
            case "uncommon" -> ItemRarity.UNCOMMON;
            case "rare" -> ItemRarity.RARE;
            case "epic" -> ItemRarity.EPIC;
            default -> null;
        };
    }

    private static DyeColor parseDyeColor(String input) {
        String norm = input.toLowerCase(Locale.ROOT).replace("-", "_");
        for (DyeColor c : DyeColor.values()) {
            if (c.name().toLowerCase(Locale.ROOT).equals(norm)) return c;
        }
        return null;
    }

    /** Maps book type names to generation ints (0=original, 1=copy_of_original, 2=copy_of_copy, 3=tattered). */
    private static Integer parseBookGeneration(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "original" -> 0;
            case "copy_of_original" -> 1;
            case "copy_of_copy" -> 2;
            case "tattered" -> 3;
            default -> null;
        };
    }

    private static AttributeModifier.Operation parseOperation(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "add_number" -> AttributeModifier.Operation.ADD_NUMBER;
            case "add_scalar" -> AttributeModifier.Operation.ADD_SCALAR;
            case "multiply_scalar_1" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> null;
        };
    }

    private static EquipmentSlotGroup parseSlotGroup(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "any" -> EquipmentSlotGroup.ANY;
            case "mainhand" -> EquipmentSlotGroup.MAINHAND;
            case "offhand" -> EquipmentSlotGroup.OFFHAND;
            case "hand" -> EquipmentSlotGroup.HAND;
            case "feet" -> EquipmentSlotGroup.FEET;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "head" -> EquipmentSlotGroup.HEAD;
            case "armor" -> EquipmentSlotGroup.ARMOR;
            case "body" -> EquipmentSlotGroup.BODY;
            case "saddle" -> EquipmentSlotGroup.SADDLE;
            default -> null;
        };
    }

    private static void sendUsage(Player p) {
        p.sendMessage(MessageUtil.parse(
                "<yellow>Usage:</yellow>\n"
                + "<gray>/ui itemnbt [player] <subcommand> ... — edits the main-hand item (default: your own)</gray>\n"
                + "<white>/ui itemnbt setname \"<miniMessage>\"</white>\n"
                + "<white>/ui itemnbt lore add [index] \"<line>\" | set <index> \"<line>\" | remove <index></white>\n"
                + "<white>/ui itemnbt hide <armor_trim|attributes|destroys|dye|enchants|placed_on|stored_enchants|unbreakable|additional_tooltip|all></white>\n"
                + "<white>/ui itemnbt unbreakable <true|false></white>\n"
                + "<white>/ui itemnbt repaircost <value></white>\n"
                + "<white>/ui itemnbt equipment <entities|cameraoverlay|dispensable|equipinteract|equipsound|slot> ...</white>\n"
                + "<white>/ui itemnbt food <addeffect|removeeffect|animation|canalwayseat|clear|consumeparticles|eatticks|info|nutrition|saturation|sound> ...</white>\n"
                + "<white>/ui itemnbt maxstacksize <1-99></white>\n"
                + "<white>/ui itemnbt maxdurability <value></white>\n"
                + "<white>/ui itemnbt fireresistent <true|false></white>\n"
                + "<white>/ui itemnbt glow <true|false></white>\n"
                + "<white>/ui itemnbt glider <true|false></white>\n"
                + "<white>/ui itemnbt rarity <common|uncommon|rare|epic></white>\n"
                + "<white>/ui itemnbt amount <value></white>\n"
                + "<white>/ui itemnbt durability <value></white>\n"
                + "<white>/ui itemnbt banner <add|set|remove|info> ...</white>\n"
                + "<white>/ui itemnbt color <R> <G> <B> (0-255)</white>\n"
                + "<white>/ui itemnbt skullowner <name></white>\n"
                + "<white>/ui itemnbt fireworkpower <1-255></white>\n"
                + "<white>/ui itemnbt potioneffect <add|remove|reset> ...</white>\n"
                + "<white>/ui itemnbt bookauthor <author></white>\n"
                + "<white>/ui itemnbt booktype <original|copy_of_original|copy_of_copy|tattered></white>\n"
                + "<white>/ui itemnbt attribute <add|remove|reset> ...</white>\n"
                + "<white>/ui itemnbt cmdata <value></white>\n"
                + "<white>/ui itemnbt itemmodel <namespace:path></white>\n"
                + "<white>/ui itemnbt compass <clear|set> [X] [Y] [Z] [world]</white>\n"
                + "<white>/ui itemnbt axltype <blue|cyan|gold|lucy|wild></white>\n"
                + "<white>/ui itemnbt ghsound <admire|call|dream|feel|ponder|seek|sing|yearn></white>\n"
                + "<white>/ui itemnbt armortrim <clear|set> [material] [pattern]</white>\n"
                + "<white>/ui itemnbt material <material></white>\n"
                + "<white>/ui itemnbt tooltipstyle <namespace:path|clear></white>\n"
                + "<white>/ui itemnbt itemname \"<miniMessage>\"|clear</white>\n"
                + "<white>/ui itemnbt hidetooltip <true|false></white>\n"
                + "<white>/ui itemnbt usecooldown <seconds> [group]|clear</white>\n"
                + "<white>/ui itemnbt useremainder <material> [amount]|clear</white>\n"
                + "<white>/ui itemnbt canbreak|canplaceon <add <block...>|clear|info></white>\n"
                + "<white>/ui itemnbt enchantable <value>|clear</white>\n"
                + "<white>/ui itemnbt mapid <id>|clear | mapcolor <R> <G> <B>|clear | mappost <lock|scale|clear></white>\n"
                + "<white>/ui itemnbt mapdeco <add <name> <type> <x> <z> <rot>|remove <name>|clear|info></white>\n"
                + "<white>/ui itemnbt writablebook <addpage <text>|clearpages|info></white>\n"
                + "<white>/ui itemnbt suspiciousstew <add <effect> <ticks>|remove <effect>|reset|info></white>\n"
                + "<white>/ui itemnbt deathprotection <true|false></white>\n"
                + "<white>/ui itemnbt jukeboxplayable <song|clear></white>\n"
                + "<white>/ui itemnbt noteblocksound <sound|clear></white>\n"
                + "<white>/ui itemnbt bundle <add <material> [amount]|clear|info></white>\n"
                + "<white>/ui itemnbt potdecorations <back> <left> <right> <front>|clear</white>\n"
                + "<white>/ui itemnbt containerloot <loottable> [seed]|clear</white>\n"
                + "<white>/ui itemnbt ominousbottle <amplifier>|clear</white>\n"
                + "<white>/ui itemnbt intangibleprojectile <true|false></white>\n"
                + "<white>/ui itemnbt firework <add <type> <R> <G> <B> [flicker] [trail]|remove <idx>|clear|info></white>\n"
                + "<white>/ui itemnbt chargedprojectiles <add <material> [amount]|clear|info></white>\n"
                + "<white>/ui itemnbt container <add <material> [amount]|clear|info></white>\n"
                + "<white>/ui itemnbt recipes <add <key>|clear|info></white>\n"
                + "<white>/ui itemnbt repairable <add <material...>|clear|info></white>\n"
                + "<gray>Edits the item in the target's main hand (online or offline .dat). Run /ui itemnbt food info for details.</gray>"));
    }

    // ══════════════════════════════════════════════════════════════
    // TAB-COMPLETE
    // ══════════════════════════════════════════════════════════════

    private static final List<String> SUBCOMMANDS = List.of(
            "setname", "lore", "hide", "unbreakable", "repaircost", "equipment", "food",
            "maxstacksize", "maxdurability", "fireresistent", "glow", "glider", "rarity",
            "amount", "durability", "banner", "color", "skullowner", "fireworkpower", "potioneffect",
            "bookauthor", "booktype", "attribute", "cmdata", "itemmodel", "compass",
            "axltype", "ghsound", "armortrim", "material",
            "tooltipstyle", "itemname", "hidetooltip", "usecooldown", "useremainder",
            "canbreak", "canplaceon", "enchantable", "mapid", "mapcolor", "mappost", "mapdeco",
            "writablebook", "suspiciousstew", "deathprotection", "jukeboxplayable", "noteblocksound",
            "bundle", "potdecorations", "containerloot", "ominousbottle", "intangibleprojectile",
            "firework", "chargedprojectiles", "container", "recipes", "repairable");

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length < 2) return result;

        // Optional target player: /ui itemnbt <ник> <subcommand> ...
        boolean hasNick = false;
        if (args.length >= 3) {
            @SuppressWarnings("deprecation")
            Player named = Bukkit.getPlayerExact(args[1]);
            if (named != null) {
                hasNick = true;
                String[] effArgs = new String[args.length - 1];
                effArgs[0] = args[0];
                System.arraycopy(args, 2, effArgs, 1, args.length - 2);
                args = effArgs;
            }
        }

        // First level: player names + subcommands
        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            if (!hasNick) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(partial)) result.add(online.getName());
                }
            }
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(partial)) result.add(s);
            }
            return result;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "lore" -> loreTab(result, sender, args);
            case "hide" -> {
                if (args.length == 3) {
                    String partial = args[2].toLowerCase(Locale.ROOT);
                    for (String s : List.of("armor_trim", "attributes", "destroys", "dye", "enchants",
                            "placed_on", "stored_enchants", "unbreakable", "additional_tooltip", "all")) {
                        if (s.startsWith(partial)) result.add(s);
                    }
                }
            }
            case "unbreakable", "equipment" -> {
                if (args.length == 3) {
                    String partial = args[2].toLowerCase(Locale.ROOT);
                    if (args[1].equalsIgnoreCase("unbreakable")) {
                        addStartsWith(result, partial, "true", "false");
                    } else {
                        for (String s : List.of("entities", "cameraoverlay", "dispensable", "equipinteract",
                                "equipsound", "slot")) {
                            if (s.startsWith(partial)) result.add(s);
                        }
                    }
                }
                if (args[1].equalsIgnoreCase("equipment")) {
                    equipmentTab(result, sender, args);
                }
            }
            case "repaircost" -> {
                if (args.length == 3) addStartsWith(result, args[2], "0", "1", "5", "10", "25", "100");
            }
            case "food" -> foodTab(result, sender, args);
            case "maxstacksize" -> {
                if (args.length == 3) addStartsWith(result, args[2], "1", "2", "4", "8", "16", "32", "64");
            }
            case "maxdurability", "fireworkpower" -> {
                if (args.length == 3) addStartsWith(result, args[2], "10", "20", "50", "100", "255", "512", "1024");
            }
            case "fireresistent", "glow", "glider" -> {
                if (args.length == 3) addStartsWith(result, args[2], "true", "false");
            }
            case "rarity" -> {
                if (args.length == 3) addStartsWith(result, args[2], "common", "uncommon", "rare", "epic");
            }
            case "amout", "amount" -> {
                if (args.length == 3) addStartsWith(result, args[2], "1", "2", "4", "8", "16", "32", "64");
            }
            case "durability" -> {
                if (args.length == 3) addStartsWith(result, args[2], "0", "10", "50", "100", "255", "512");
            }
            case "banner" -> bannerTab(result, sender, args);
            case "color" -> {
                if (args.length == 4) addStartsWith(result, args[3], "0", "32", "64", "128", "192", "255");
                if (args.length == 5) addStartsWith(result, args[4], "0", "32", "64", "128", "192", "255");
                if (args.length == 6) addStartsWith(result, args[5], "0", "32", "64", "128", "192", "255");
            }
            case "skullowner" -> {
                if (args.length == 3) {
                    String partial = args[2].toLowerCase(Locale.ROOT);
                    for (Player player : ((Player) sender).getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) result.add(player.getName());
                    }
                }
            }
            case "potioneffect" -> potionEffectTab(result, sender, args);
            case "bookauthor" -> {
                if (args.length == 3) {
                    String partial = args[2].toLowerCase(Locale.ROOT);
                    for (Player player : ((Player) sender).getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) result.add(player.getName());
                    }
                }
            }
            case "booktype" -> {
                if (args.length == 3) addStartsWith(result, args[2], "original", "copy_of_original", "copy_of_copy", "tattered");
            }
            case "attribute", "attrubite" -> attributeTab(result, sender, args);
            case "cmdata" -> {
                if (args.length == 3) addStartsWith(result, args[2], "0", "1", "5", "10", "100", "1000", "10000");
            }
            case "itemmodel" -> {
                if (args.length == 3) addStartsWith(result, args[2], "minecraft:item/", "minecraft:block/");
            }
            case "compass" -> compassTab(result, sender, args);
            case "axltype" -> {
                if (args.length == 3) addStartsWith(result, args[2], "blue", "cyan", "gold", "lucy", "wild");
            }
            case "ghsound" -> {
                if (args.length == 3) addStartsWith(result, args[2], "admire", "call", "dream", "feel", "ponder", "seek", "sing", "yearn");
            }
            case "armortrim" -> armorTrimTab(result, sender, args);
            case "material" -> {
                if (args.length == 3) addRegistryKeys(result, Registry.MATERIAL, args[2], 250);
            }
            case "tooltipstyle" -> {
                if (args.length == 3) {
                    String partial = args[2].toLowerCase(Locale.ROOT);
                    addStartsWith(result, partial, "minecraft:missing", "clear");
                }
            }
            case "itemname" -> {
                if (args.length == 3) addStartsWith(result, args[2].toLowerCase(Locale.ROOT), "clear");
            }
            case "hidetooltip", "intangibleprojectile", "deathprotection" -> {
                if (args.length == 3) addStartsWith(result, args[2], "true", "false");
            }
            case "usecooldown" -> {
                if (args.length == 3) addStartsWith(result, args[2], "1", "2", "5", "10", "30", "60", "clear");
                if (args.length == 4) {
                    String partial = args[3].toLowerCase(Locale.ROOT);
                    addStartsWith(result, partial, "minecraft:mainhand", "clear");
                }
            }
            case "useremainder" -> {
                if (args.length == 3) {
                    addRegistryKeys(result, Registry.MATERIAL, args[2], 150);
                    addStartsWith(result, args[2], "clear");
                } else if (args.length == 4) {
                    addStartsWith(result, args[3], "1", "2", "4", "8", "16", "64");
                }
            }
            case "canbreak", "canplaceon" -> {
                if (args.length == 3) {
                    addStartsWith(result, args[2], "add", "clear", "info");
                } else if (args[2].equalsIgnoreCase("add") && args.length >= 4) {
                    addRegistryKeys(result, Registry.BLOCK, args[args.length - 1], 100);
                }
            }
            case "enchantable" -> {
                if (args.length == 3) addStartsWith(result, args[2], "0", "1", "5", "10", "15", "255", "clear");
            }
            case "mapid" -> {
                if (args.length == 3) addStartsWith(result, args[2], "0", "1", "10", "100", "1000", "clear");
            }
            case "mapcolor" -> {
                if (args.length == 4) addStartsWith(result, args[3], "0", "32", "64", "128", "192", "255");
                if (args.length == 5) addStartsWith(result, args[4], "0", "32", "64", "128", "192", "255");
                if (args.length == 6) addStartsWith(result, args[5], "0", "32", "64", "128", "192", "255");
            }
            case "mappost" -> {
                if (args.length == 3) addStartsWith(result, args[2], "lock", "scale", "clear");
            }
            case "mapdeco" -> mapDecoTab(result, sender, args);
            case "writablebook" -> {
                if (args.length == 3) addStartsWith(result, args[2], "addpage", "clearpages", "info");
            }
            case "suspiciousstew" -> suspiciousStewTab(result, sender, args);
            case "jukeboxplayable" -> {
                if (args.length == 3) {
                    addRegistryKeys(result, Registry.JUKEBOX_SONG, args[2], 50);
                    addStartsWith(result, args[2], "clear");
                }
            }
            case "noteblocksound" -> {
                if (args.length == 3) {
                    addRegistryKeys(result, Registry.SOUNDS, args[2], 150);
                    addStartsWith(result, args[2], "clear");
                }
            }
            case "bundle", "container", "chargedprojectiles" -> {
                if (args.length == 3) addStartsWith(result, args[2], "add", "clear", "info");
                else if (args[2].equalsIgnoreCase("add") && args.length == 4) {
                    addRegistryKeys(result, Registry.MATERIAL, args[3], 150);
                } else if (args[2].equalsIgnoreCase("add") && args.length == 5) {
                    addStartsWith(result, args[4], "1", "2", "4", "8", "16", "64");
                }
            }
            case "potdecorations" -> {
                if (args.length == 3) addStartsWith(result, args[2], "clear");
                else if (args.length >= 4 && args.length <= 7) {
                    addRegistryKeys(result, Registry.ITEM, args[args.length - 1], 100);
                }
            }
            case "containerloot" -> {
                if (args.length == 3) {
                    addStartsWith(result, args[2], "minecraft:chests/", "clear");
                } else if (args.length == 4) {
                    addStartsWith(result, args[3], "0", "1", "2", "12345");
                }
            }
            case "ominousbottle" -> {
                if (args.length == 3) addStartsWith(result, args[2], "0", "1", "2", "3", "4", "clear");
            }
            case "firework" -> fireworkTab(result, sender, args);
            case "recipes" -> {
                if (args.length == 3) addStartsWith(result, args[2], "add", "clear", "info");
            }
            case "repairable" -> {
                if (args.length == 3) addStartsWith(result, args[2], "add", "clear", "info");
                else if (args[2].equalsIgnoreCase("add") && args.length >= 4) {
                    addRegistryKeys(result, Registry.ITEM, args[args.length - 1], 150);
                }
            }
            default -> { }
        }
        return result;
    }

    private static void mapDecoTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            addStartsWith(result, args[2], "add", "remove", "clear", "info");
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("add")) {
            if (args.length == 4) {
                // name — free text
            } else if (args.length == 5) {
                addRegistryKeys(result, Registry.MAP_DECORATION_TYPE, args[4], 50);
            } else if (args.length <= 8) {
                addStartsWith(result, args[args.length - 1], "0", "0.5", "1", "2", "4", "8");
            }
        } else if (action.equals("remove") && args.length == 4) {
            if (sender instanceof Player p) {
                MapDecorations md = p.getInventory().getItemInMainHand().getData(DataComponentTypes.MAP_DECORATIONS);
                if (md != null) {
                    String partial = args[3].toLowerCase(Locale.ROOT);
                    for (String name : md.decorations().keySet()) {
                        if (name.toLowerCase(Locale.ROOT).startsWith(partial)) result.add(name);
                    }
                }
            }
        }
    }

    private static void suspiciousStewTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            addStartsWith(result, args[2], "add", "remove", "reset", "info");
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("add") || action.equals("remove")) {
            if (args.length == 4) {
                addRegistryKeys(result, Registry.POTION_EFFECT_TYPE, args[3], 100);
            } else if (action.equals("add") && args.length == 5) {
                addStartsWith(result, args[4], "20", "100", "200", "600", "1200");
            }
        }
    }

    private static void fireworkTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            addStartsWith(result, args[2], "add", "remove", "clear", "info");
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("add")) {
            if (args.length == 4) {
                addStartsWith(result, args[3], "ball", "ball_large", "burst", "creeper", "star");
            } else if (args.length == 5 || args.length == 6 || args.length == 7) {
                addStartsWith(result, args[args.length - 1], "0", "32", "64", "128", "192", "255");
            } else if (args.length == 8) {
                addStartsWith(result, args[7], "true", "false");
            } else if (args.length == 9) {
                addStartsWith(result, args[8], "true", "false");
            }
        } else if (action.equals("remove") && args.length == 4) {
            if (sender instanceof Player p) {
                Fireworks fw = p.getInventory().getItemInMainHand().getData(DataComponentTypes.FIREWORKS);
                if (fw != null) {
                    for (int i = 0; i < fw.effects().size(); i++) addStartsWith(result, args[3], String.valueOf(i));
                }
            }
        }
    }

    private static void armorTrimTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            addStartsWith(result, args[2], "clear", "set");
            return;
        }
        if (!args[2].equalsIgnoreCase("set")) return;
        if (args.length == 4) {
            addStartsWith(result, args[3], "amethyst", "copper", "diamond", "emerald", "gold", "iron",
                    "lapis", "netherite", "quartz", "redstone", "resin");
        } else if (args.length == 5) {
            addStartsWith(result, args[4], "bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib",
                    "sentry", "shaper", "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");
        }
    }

    private static void loreTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            for (String s : List.of("add", "set", "remove")) {
                if (s.startsWith(partial)) result.add(s);
            }
            return;
        }
        // Suggest existing lore indexes for set/remove
        if ((args[2].equalsIgnoreCase("set") || args[2].equalsIgnoreCase("remove")) && args.length == 4) {
            int size = loreSize(sender);
            for (int i = 0; i < size; i++) {
                result.add(String.valueOf(i));
            }
        }
    }

    private static int loreSize(CommandSender sender) {
        if (!(sender instanceof Player p)) return 0;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return 0;
        List<Component> lore = item.lore();
        return lore == null ? 0 : lore.size();
    }

    private static void equipmentTab(List<String> result, CommandSender sender, String[] args) {
        String sub = args[2].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "entities" -> {
                if (args.length == 4) {
                    String partial = args[3].toLowerCase(Locale.ROOT);
                    for (EntityType type : Registry.ENTITY_TYPE) {
                        if (type.getKey().getKey().startsWith(partial)) result.add(type.getKey().getKey());
                    }
                } else if (args.length == 5) {
                    addStartsWith(result, args[4], "true", "false");
                }
            }
            case "cameraoverlay" -> {
                if (args.length == 4) {
                    String partial = args[3].toLowerCase(Locale.ROOT);
                    addStartsWith(result, partial, "minecraft:textures/misc/pumpkinblur.png", "clear");
                }
            }
            case "dispensable", "equipinteract" -> {
                if (args.length == 4) addStartsWith(result, args[3], "true", "false");
            }
            case "equipsound" -> {
                if (args.length == 4) addRegistryKeys(result, Registry.SOUNDS, args[3], 150);
            }
            case "slot" -> {
                if (args.length == 4) {
                    String partial = args[3].toLowerCase(Locale.ROOT);
                    for (String s : List.of("body", "chest", "feet", "hand", "head", "legs", "off_hand", "saddle")) {
                        if (s.startsWith(partial)) result.add(s);
                    }
                } else if (args.length == 5) {
                    addStartsWith(result, args[4], "true", "false");
                }
            }
            default -> { }
        }
    }

    private static void foodTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            for (String s : List.of("addeffect", "removeeffect", "animation", "canalwayseat", "clear",
                    "consumeparticles", "eatticks", "info", "nutrition", "saturation", "sound")) {
                if (s.startsWith(partial)) result.add(s);
            }
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "addeffect", "removeeffect", "removeeefect" -> {
                if (args.length == 4) {
                    addRegistryKeys(result, Registry.POTION_EFFECT_TYPE, args[3], 100);
                } else if (args.length == 5 && args[2].equalsIgnoreCase("addeffect")) {
                    addStartsWith(result, args[4], "-1", "20", "100", "600", "1200", "3600", "7200");
                } else if (args.length == 6 && args[2].equalsIgnoreCase("addeffect")) {
                    addStartsWith(result, args[5], "1", "2", "3", "5", "10", "255");
                } else if (args.length == 7 && args[2].equalsIgnoreCase("addeffect")) {
                    addStartsWith(result, args[6], "true", "false");
                }
            }
            case "animation" -> {
                if (args.length == 4) {
                    String partial = args[3].toLowerCase(Locale.ROOT);
                    for (String s : List.of("block", "bow", "brush", "crossbow", "drink", "eat",
                            "none", "spear", "spyglass", "toot_horn")) {
                        if (s.startsWith(partial)) result.add(s);
                    }
                }
            }
            case "canalwayseat", "consumeparticles" -> {
                if (args.length == 4) {
                    addStartsWith(result, args[3], "true", "false", "clear");
                    if (args[2].equalsIgnoreCase("consumeparticles")) {
                        addRegistryKeys(result, Registry.PARTICLE_TYPE, args[3], 50);
                    }
                }
            }
            case "eatticks", "nutrition", "saturation" -> {
                if (args.length == 4) addStartsWith(result, args[3], "1", "2", "4", "8", "16", "32");
            }
            case "sound" -> {
                if (args.length == 4) addRegistryKeys(result, Registry.SOUNDS, args[3], 150);
            }
            default -> { }
        }
    }

    private static void bannerTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            for (String s : List.of("add", "set", "remove", "info")) {
                if (s.startsWith(partial)) result.add(s);
            }
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if ((action.equals("add") || action.equals("set"))) {
            if (args.length == 4) {
                // index suggestions
                if (sender instanceof Player p) {
                    int size = bannerPatterns(p.getInventory().getItemInMainHand()).size();
                    for (int i = 0; i <= size; i++) addStartsWith(result, args[3], String.valueOf(i));
                }
            } else if (args.length == 5) {
                // PatternType registry
                addRegistryKeys(result, Registry.BANNER_PATTERN, args[4], 150);
            } else if (args.length == 6) {
                // DyeColor
                String partial = args[5].toLowerCase(Locale.ROOT);
                for (DyeColor c : DyeColor.values()) {
                    if (c.name().toLowerCase(Locale.ROOT).startsWith(partial)) result.add(c.name().toLowerCase());
                }
            }
        } else if (action.equals("remove") && args.length == 4) {
            if (sender instanceof Player p) {
                int size = bannerPatterns(p.getInventory().getItemInMainHand()).size();
                for (int i = 0; i < size; i++) addStartsWith(result, args[3], String.valueOf(i));
            }
        } else if (action.equals("info") && args.length == 4) {
            addStartsWith(result, args[3], "all");
        }
    }

    private static void potionEffectTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            for (String s : List.of("add", "remove", "reset")) {
                if (s.startsWith(partial)) result.add(s);
            }
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("add")) {
            if (args.length == 4) {
                addRegistryKeys(result, Registry.POTION_EFFECT_TYPE, args[3], 100);
            } else if (args.length == 5) {
                addStartsWith(result, args[4], "-1", "20", "100", "600", "1200", "3600", "7200");
            } else if (args.length == 6) {
                addStartsWith(result, args[5], "1", "2", "3", "5", "10", "255");
            }
        } else if (action.equals("remove")) {
            if (args.length == 4 && sender instanceof Player p) {
                PotionContents pc = p.getInventory().getItemInMainHand().getData(DataComponentTypes.POTION_CONTENTS);
                if (pc != null) {
                    String partial = args[3].toLowerCase(Locale.ROOT);
                    for (PotionEffect e : pc.customEffects()) {
                        String key = e.getType().getKey().getKey();
                        if (key.startsWith(partial)) result.add(key);
                    }
                }
            }
        }
    }

    private static void attributeTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            for (String s : List.of("add", "remove", "reset")) {
                if (s.startsWith(partial)) result.add(s);
            }
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("add")) {
            if (args.length == 4) {
                addRegistryKeys(result, Registry.ATTRIBUTE, args[3], 100);
            } else if (args.length == 5) {
                addStartsWith(result, args[4], "0", "1", "2", "5", "10", "0.5", "-1");
            } else if (args.length == 6) {
                addStartsWith(result, args[5], "add_number", "add_scalar", "multiply_scalar_1");
            } else if (args.length == 7) {
                String partial = args[6].toLowerCase(Locale.ROOT);
                for (String s : List.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body", "saddle")) {
                    if (s.startsWith(partial)) result.add(s);
                }
            }
        } else if (action.equals("remove") && args.length == 4) {
            if (sender instanceof Player p) {
                ItemAttributeModifiers am = p.getInventory().getItemInMainHand().getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
                if (am != null) {
                    for (int i = 0; i < am.modifiers().size(); i++) addStartsWith(result, args[3], String.valueOf(i));
                }
            }
        }
    }

    private static void compassTab(List<String> result, CommandSender sender, String[] args) {
        if (args.length == 3) {
            addStartsWith(result, args[2], "clear", "set");
            return;
        }
        if (args[2].equalsIgnoreCase("set")) {
            if (args.length == 7) {
                String partial = args[6].toLowerCase(Locale.ROOT);
                for (World world : ((Player) sender).getServer().getWorlds()) {
                    if (world.getName().toLowerCase(Locale.ROOT).startsWith(partial)) result.add(world.getName());
                }
            } else {
                addStartsWith(result, args[args.length - 1], "0", "64", "128", "-64");
            }
        }
    }

    private static void addStartsWith(List<String> result, String partial, String... values) {
        String p = partial.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(p)) result.add(value);
        }
    }

    /** Adds registry keys matching the partial, capped at {@code max} entries. */
    private static <T extends Keyed> void addRegistryKeys(List<String> result, Registry<T> registry,
                                                          String partial, int max) {
        String p = partial.toLowerCase(Locale.ROOT);
        int count = 0;
        for (T value : registry) {
            if (count >= max) break;
            String key = value.getKey().getKey();
            if (key.startsWith(p)) {
                result.add(key);
                count++;
            }
        }
    }
}
