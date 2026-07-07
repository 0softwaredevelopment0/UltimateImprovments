package com.mcplugin.command.subcommands;

import com.mcplugin.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public final class CilistCommand {

    private CilistCommand() {}

    public static void execute(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════════════════════</gold>"));
        sender.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Custom Items</white> <gray>(craft only in Item Assembler)</gray>"));
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════════════════════</gold>"));
        sender.sendMessage(Component.empty());

        // 1. Lead Ingot
        sender.sendMessage(MessageUtil.parse("<yellow>1. Lead Ingot</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Netherite Ingot</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Iron Iron</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Netherite Iron</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Iron Iron</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>Used to craft a Lead Shield</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 2. Lead Shield
        sender.sendMessage(MessageUtil.parse("<yellow>2. Lead Shield</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Shield</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Lead Lead Lead</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Lead Shield Lead</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Lead Lead Lead</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>Protects from radiation when held</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 3. Entity Locator
        sender.sendMessage(MessageUtil.parse("<yellow>3. Entity Locator</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Recovery Compass</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Netherite Scrap Redstone Torch Compass</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Redstone Redstone Block Comparator</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Breeze Rod Breeze Rod Tripwire Hook</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>Shows distance to nearest entity (action bar)</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 4. Photon cannon
        sender.sendMessage(MessageUtil.parse("<yellow>4. Photon cannon</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Warped fungus on a stick</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Purpur Glass Pane Nether Star</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Echo Shard Heart of the Sea Glass Pane</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Breeze Rod Echo Shard Purpur</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>Shoots echo shards (ammo in offhand)</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 5. Electro Shoker
        sender.sendMessage(MessageUtil.parse("<yellow>5. Electro Shoker</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Warped fungus on a stick</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Black Concrete Yellow Concrete Blaze Rod</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Yellow Concrete Black Concrete Breeze Rod</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Stick Netherite Scrap Netherite Scrap</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>Stuns enemies with electricity (ammo — breeze rod)</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 6. Antimatter Flask
        sender.sendMessage(MessageUtil.parse("<yellow>6. Antimatter Flask</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Splash Potion</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Netherite Scrap Netherite Scrap Netherite Scrap</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Netherite Scrap Glass Bottle Netherite Scrap</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Netherite Scrap Netherite Scrap Netherite Scrap</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>Creates a powerful explosion with radiation when thrown</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 7. Multimeter
        sender.sendMessage(MessageUtil.parse("<yellow>7. Multimeter</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Clock</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Diamond Iron</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Diamond Clock Diamond</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Diamond Iron</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>RMB on cable/battery — show energy info</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 8. Health Meter
        sender.sendMessage(MessageUtil.parse("<yellow>8. Health Meter</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Name Tag</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Lapis Lazuli Iron</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Lapis Lazuli Heart of the Sea Lapis Lazuli</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Lapis Lazuli Iron</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>RMB — check health of the entity you're looking at</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 9. Ore Finder
        sender.sendMessage(MessageUtil.parse("<yellow>9. Ore Finder</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Compass</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Redstone Iron</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Diamond Gold Diamond</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Iron Redstone Iron</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>RMB — scan chunk and show ore counts</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 10. Mob Finder
        sender.sendMessage(MessageUtil.parse("<yellow>10. Mob Finder</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Spyglass</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Breeze Rod Netherite Scrap Breeze Rod</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Netherite Scrap Iron Breeze Rod</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Breeze Rod Netherite Scrap Breeze Rod</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>RMB — scan chunk and show mob counts</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        // 11. Portable Radar
        sender.sendMessage(MessageUtil.parse("<yellow>11. Portable Radar</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Material: </white><gray>Eye of Ender</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Recipe:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Netherite Scrap Eye of Ender Netherite Scrap</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Eye of Ender Redstone Block Eye of Ender</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Netherite Scrap Eye of Ender Netherite Scrap</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Description: </white><gray>RMB — find nearest entity within 64 blocks (type, coords, distance)</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Assembler only</red>"));
        sender.sendMessage(Component.empty());

        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════════════════════</gold>"));
        sender.sendMessage(MessageUtil.parse("<gray>💡 Place a crafting table and use it as an Item Assembler</gray>"));
        sender.sendMessage(MessageUtil.parse("<gray>   to craft all custom items!</gray>"));
        sender.sendMessage(Component.empty());
    }
}
