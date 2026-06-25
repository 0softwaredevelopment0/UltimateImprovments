#!/usr/bin/env python3
"""
Build codebuff-memory.db — comprehensive MC-Plugin context database.
Saves all architecture, modules, items, commands, config, and git history.
"""

import sqlite3
import os
import subprocess
from pathlib import Path

PROJECT = Path(r"C:\MC-Plugin")
DB_PATH = PROJECT / ".codebuff-memory.db"

def get_git_log(count=50):
    try:
        result = subprocess.run(
            ["git", "log", f"--max-count={count}", "--oneline", "--no-decorate"],
            cwd=str(PROJECT), capture_output=True, text=True, timeout=10
        )
        return result.stdout.strip().split("\n") if result.stdout.strip() else []
    except Exception:
        return []

def get_git_log_full(count=10):
    try:
        result = subprocess.run(
            ["git", "log", f"--max-count={count}", "--format=%H|%an|%ad|%s", "--date=short"],
            cwd=str(PROJECT), capture_output=True, text=True, timeout=10
        )
        lines = result.stdout.strip().split("\n") if result.stdout.strip() else []
        return [dict(zip(["sha","author","date","message"], l.split("|", 3))) for l in lines if "|" in l]
    except Exception:
        return []

def main():
    # Remove existing DB if any
    if DB_PATH.exists():
        DB_PATH.unlink()

    conn = sqlite3.connect(str(DB_PATH))
    conn.execute("PRAGMA foreign_keys = ON;")
    c = conn.cursor()

    # ============================================================
    # 1. PROJECT INFO
    # ============================================================
    c.execute("""
        CREATE TABLE project_info (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    """)
    project_data = [
        ("name", "MC-Plugin"),
        ("version", "1.7.77"),
        ("main_class", "com.mcplugin.infrastructure.core.Main"),
        ("api_version", "26.2 (Paper 1.21.4)"),
        ("author", "rizer001"),
        ("language", "Java 25"),
        ("build_system", "Gradle + Paperweight Userdev 2.0.0-beta.21 + Shadow 9.4.2"),
        ("database", "SQLite (via xerial:sqlite-jdbc:3.46.1.3)"),
        ("auth_hashing", "Argon2 (via kammerer:argon2-jvm:2.11)"),
        ("description", "Multi-purpose Minecraft Paper plugin: energy systems, custom crafting, authentication, \
voting, homes, radiation, magnetic physics, integrity system, protection modules, and more."),
        ("base_command", "/mp (dispatched by PluginReloadCommand)"),
        ("load", "POSTWORLD"),
    ]
    c.executemany("INSERT INTO project_info (key, value) VALUES (?, ?)", project_data)

    # ============================================================
    # 2. PDC KEYS
    # ============================================================
    c.execute("""
        CREATE TABLE pdc_keys (
            key_name TEXT PRIMARY KEY,
            namespaced_key TEXT NOT NULL,
            purpose TEXT NOT NULL
        )
    """)
    pdc_keys = [
        ("PLASMA", "mcplugin:is_plasma_cannon", "Photon cannon (PlasmaCannon) item identification"),
        ("SHOCKER", "mcplugin:is_shocker", "Electro Shoker item identification"),
        ("ANTIMATTER", "mcplugin:isAntimatter", "Antimatter Flask item identification"),
        ("LOCATOR", "mcplugin:isLocator", "Entity Locator item identification"),
        ("HEALTH_METER", "mcplugin:isHealthMeter", "Health Meter item identification"),
        ("ORE_FINDER", "mcplugin:isOreFinder", "Ore Finder item identification"),
        ("MOB_FINDER", "mcplugin:isMobFinder", "Mob Finder item identification"),
        ("RADAR", "mcplugin:isRadar", "Portable Radar item identification"),
        ("LEAD_INGOT", "mcplugin:isLeadIngot", "Lead Ingot material — anti-uncraft protection"),
        ("LEAD_SHIELD", "mcplugin:isLeadShield", "Lead Shield item identification"),
        ("AUTH_GUI", "mcplugin:auth_gui", "Auth GUI item tagging"),
        ("CHGDIM_GUI", "mcplugin:chgdim_gui", "Change dimension GUI item tagging"),
        ("INTEGRITY_TAG", "mcplugin:integrity_tag", "Integrity system — marks items tracked"),
        ("INTEGRITY_MAX", "mcplugin:integrity_max", "Integrity system — max durability %"),
        ("INTEGRITY_CURRENT", "mcplugin:integrity_current", "Integrity system — current durability %"),
        ("INTEGRITY_LAST_SEEN", "mcplugin:integrity_last_seen", "Integrity system — last seen timestamp"),
        ("INTEGRITY_WARN_FLAGS", "mcplugin:integrity_warn_flags", "Integrity system — warning flags bitmask"),
        ("INTEGRITY_VERSION", "mcplugin:integrity_version", "Integrity system — data version"),
    ]
    c.executemany("INSERT INTO pdc_keys (key_name, namespaced_key, purpose) VALUES (?, ?, ?)", pdc_keys)

    # ============================================================
    # 3. MODULES (51 total)
    # ============================================================
    c.execute("""
        CREATE TABLE modules (
            name TEXT PRIMARY KEY,
            package TEXT NOT NULL,
            essential INTEGER DEFAULT 0,
            enabled_by_default INTEGER DEFAULT 1,
            category TEXT NOT NULL,
            purpose TEXT NOT NULL
        )
    """)
    modules = [
        # System
        ("VersionCheck", "infrastructure.modules", 0, 1, "System", "Checks plugin version on startup"),
        ("Database", "infrastructure.modules", 1, 1, "System", "Initializes SQLite database manager"),

        ("Datapack", "infrastructure.modules", 0, 1, "System", "Installs bundled datapack resources"),
        # Core
        ("Core", "infrastructure.modules", 1, 1, "Core", "Core listeners: block break/place, server brand, plugin hide, fishing, shulker bullets"),
        ("Power", "infrastructure.modules", 0, 1, "Core", "Server shutdown/restart management (/mp power)"),
        # Energy
        ("Cable", "energy", 0, 1, "Energy", "Cable network: energy transfer between nodes, visual blinking"),
        ("GeneratorBasic", "energy", 0, 1, "Energy", "Basic generators (BLAST_FURNACE + ItemFrame), burns fuel for energy"),
        ("Reactor", "energy", 0, 1, "Energy", "Dark Synthesis Reactor — multiblock nuclear reactor with temp/pressure/wear mechanics"),
        ("ElectricFurnace", "energy", 0, 1, "Energy", "Electric furnace (BLAST_FURNACE + cable) — smelts with lightning + energy"),
        ("Workbench", "energy", 0, 1, "Energy", "Energy workbench tracking — all CRAFTING_TABLE placements registered"),
        ("Battery", "energy", 0, 1, "Energy", "Battery storage block (single)"),
        ("BatteryMulti", "energy", 0, 1, "Energy", "Battery multiblock structure with smooth charge/discharge curves"),
        ("Light", "energy", 0, 1, "Energy", "Light multiblock — consumes energy to illuminate area"),
        # Mechanics
        ("Radiation", "mechanics", 0, 1, "Mechanics", "Radiation system: sources, effects, lead shield protection, decay"),
        ("Lightning", "mechanics", 0, 1, "Mechanics", "Lightning rod structure mechanics"),
        # Crafting + Auth
        ("Crafting", "mechanics", 1, 1, "Crafting", "All custom item recipes + AssemblerListener"),
        ("Auth", "mechanics", 0, 1, "Auth", "Player authentication: password login/register, GUI-based, IP checks, rate limiting"),
        # Features
        ("Attributes", "mechanics", 0, 1, "Feature", "Custom player attributes: attack damage, sneak speed, attack speed"),
        ("Beacon", "mechanics", 0, 1, "Feature", "Beacon trap — effects when standing on beacon block"),
        ("BlockDmg", "mechanics", 0, 1, "Feature", "Dripstone and end rod damage"),
        ("BoostedCobweb", "mechanics", 0, 1, "Feature", "Enhanced cobweb effects (fatigue, weakness, slowness)"),
        ("DragonEgg", "mechanics", 0, 1, "Feature", "Periodic dragon egg spawn"),
        ("EntityLocator", "mechanics", 0, 1, "Feature", "Entity locator item — shows nearest entity on action bar"),
        ("ItemKill", "mechanics", 0, 1, "Feature", "Anti-lag: removes >6400 items on ground"),
        ("Magnet", "mechanics", 0, 1, "Feature", "Magnetic physics — lodestone + item frames attract metal items/players/mobs"),
        ("ModeProtect", "mechanics", 0, 1, "Feature", "Forced survival gamemode in configured worlds"),
        ("TerracotaSpeed", "mechanics", 0, 1, "Feature", "Speed boost on terracotta blocks"),
        ("Waypoint", "mechanics", 0, 1, "Feature", "Client-side waypoints (dark_red color)"),
        ("Integrity", "mechanics", 0, 1, "Feature", "Item integrity system — replaces vanilla durability with %-based system"),
        ("Antimatter", "mechanics", 0, 1, "Feature", "Antimatter Flask explosion on throw"),
        ("UnbreakableBreaker", "mechanics", 0, 1, "Feature", "Progressive block breaking for unbreakable blocks (bedrock, barrier, etc.)"),
        ("DeathBell", "mechanics", 0, 1, "Feature", "Death bell — lightning on bell ring"),
        ("EnderChest", "mechanics", 0, 1, "Feature", "Ender chest explosion chance on open"),
        ("GlassBreak", "mechanics", 0, 1, "Feature", "Damage when breaking glass by hand"),
        ("ShieldSlowness", "mechanics", 0, 1, "Feature", "Slowness effect when blocking with shield"),
        ("CreativeItemValidator", "mechanics", 0, 1, "Feature", "Validates creative mode items for oversized NBT/data"),
        ("ContainerTrigger", "mechanics", 0, 1, "Feature", "Container-triggered block blinking (redstone lamps, etc.)"),
        ("Vanish", "mechanics", 0, 1, "Feature", "Player vanish /mp vanish"),
        ("Notes", "mechanics", 0, 1, "Feature", "Player notes GUI system"),
        ("MinecartSpeed", "mechanics", 0, 1, "Feature", "Exponential minecart acceleration on powered rails"),
        # Protection
        ("RedstoneGuard", "infrastructure", 0, 1, "Protection", "Redstone overload protection — blocks overactive chunks"),
        ("PacketGuard", "infrastructure", 0, 1, "Protection", "Packet size limit guard — kicks oversized packets"),
        # Utility
        ("ChatFilter", "mechanics", 0, 1, "Utility", "Chat profanity filter with word lists and regex patterns"),
        ("VoidProtection", "mechanics", 0, 1, "Utility", "Teleports player out of void in configured worlds"),
        # Background
        ("Tasks", "infrastructure", 0, 1, "Background", "Background tasks: battery drain, cable tick, generator, emergency entity kill, server overload"),
        ("AutoSave", "infrastructure", 0, 1, "Background", "Async auto-save manager for database"),
        ("Update", "mechanics", 0, 1, "Background", "GitHub update checker and auto-installer"),
        ("Leash", "mechanics", 0, 1, "Feature", "Leash any entity with lead — configurable range and pull behavior"),
        ("ElytraBoost", "mechanics", 0, 1, "Feature", "Elytra boost on jump (/mp togglefly)"),
    ]
    c.executemany("INSERT INTO modules (name, package, essential, enabled_by_default, category, purpose) VALUES (?, ?, ?, ?, ?, ?)", modules)

    # ============================================================
    # 4. CUSTOM ITEMS (with recipes)
    # ============================================================
    c.execute("""
        CREATE TABLE custom_items (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            display_name TEXT NOT NULL,
            material TEXT NOT NULL,
            recipe_shape TEXT NOT NULL,
            recipe_ingredients TEXT NOT NULL,
            lore TEXT NOT NULL,
            pdc_key TEXT NOT NULL,
            description TEXT NOT NULL,
            craft_only_in_assembler INTEGER DEFAULT 1
        )
    """)
    items = [
        (1, "Lead Ingot", "Lead Ingot", "NETHERITE_INGOT",
         "III / INI / III",
         "I=IRON_INGOT, N=NETHERITE_INGOT",
         "Used to craft a Lead Shield",
         "LEAD_INGOT", "Material component for crafting Lead Shield. 1 netherite + 8 iron.", 1),

        (2, "Lead Shield", "Lead Shield", "SHIELD",
         "LLL / LSL / LLL",
         "L=LeadIngot(NETHERITE_INGOT), S=SHIELD",
         "Protects from radiation when held",
         "LEAD_SHIELD", "Radiation protection item. Reduces radiation when held in hand.", 1),

        (3, "Entity Locator", "Entity Locator", "RECOVERY_COMPASS",
         "NRC / RRC / BBT",
         "N=NETHERITE_SCRAP, R=REDSTONE_TORCH, C=RECOVERY_COMPASS, BB=REDSTONE_BLOCK, T=TRIPWIRE_HOOK, Breeze Rod",
         "Shows distance to nearest entity",
         "LOCATOR", "Shows distance to nearest entity on action bar.", 1),

        (4, "Photon cannon", "Photon cannon", "WARPED_FUNGUS_ON_A_STICK",
         "PGN / EHR / BEP",
         "P=PURPUR_BLOCK, G=GLASS_PANE, N=NETHER_STAR, E=ECHO_SHARD, H=HEART_OF_THE_SEA, R=GLASS_PANE, B=BREEZE_ROD",
         "Shoots with echo_shard",
         "PLASMA", "Ranged weapon. Fires echo shards (ammo in offhand).", 1),

        (5, "Electro Shoker", "Electro Shoker", "WARPED_FUNGUS_ON_A_STICK",
         "BYB / YBB / SNN",
         "B=BLACK_CONCRETE, Y=YELLOW_CONCRETE, Bz=BLAZE_ROD, Br=BREEZE_ROD, S=STICK, N=NETHERITE_SCRAP",
         "Stuns enemies with electricity",
         "SHOCKER", "Shock weapon. Stuns enemies with electricity (ammo = breeze rod).", 1),

        (6, "Antimatter Flask", "Antimatter Flask", "SPLASH_POTION",
         "NNN / NGN / NNN",
         "N=NETHERITE_SCRAP, G=GLASS_BOTTLE",
         "Creates a powerful explosion when thrown",
         "ANTIMATTER", "Throwable explosive. Creates a powerful explosion with radiation.", 1),

        (7, "Multimeter", "Multimeter", "CLOCK",
         "IDI / DCD / IDI",
         "I=IRON_INGOT, D=DIAMOND, C=CLOCK",
         "RMB on cable/battery — show energy info",
         "(internal)", "Energy diagnostic tool. Shows energy info when right-clicking cables/batteries.", 1),

        (8, "Health Meter", "Health Meter", "NAME_TAG",
         "ILI / LHL / ILI",
         "I=IRON_INGOT, L=LAPIS_LAZULI, H=HEART_OF_THE_SEA",
         "RMB — check entity health",
         "HEALTH_METER", "RMB to check health of the entity you're looking at.", 1),

        (9, "Ore Finder", "Ore Finder", "COMPASS",
         "IRI / DGD / IRI",
         "I=IRON_INGOT, R=REDSTONE, D=DIAMOND, G=GOLD_INGOT",
         "RMB — scan chunk for ores",
         "ORE_FINDER", "RMB to scan current chunk and show ore counts.", 1),

        (10, "Mob Finder", "Mob Finder", "SPYGLASS",
         "BNB / NIN / BNB",
         "B=BREEZE_ROD, N=NETHERITE_SCRAP, I=IRON_INGOT",
         "RMB — scan chunk for mobs",
         "MOB_FINDER", "RMB to scan current chunk and show mob counts.", 1),

        (11, "Portable Radar", "Portable Radar", "ENDER_EYE",
         "NEN / EBE / NEN",
         "N=NETHERITE_SCRAP, E=ENDER_EYE, B=REDSTONE_BLOCK",
         "RMB — find nearest entity within 64 blocks",
         "RADAR", "Finds nearest entity within 64 blocks. Shows type, distance with color coding, direction, and distance bar.", 1),
    ]
    c.executemany("""
        INSERT INTO custom_items (id, name, display_name, material, recipe_shape, recipe_ingredients, lore, pdc_key, description, craft_only_in_assembler)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, items)

    # ============================================================
    # 5. ENERGY SYSTEM
    # ============================================================
    c.execute("""
        CREATE TABLE energy_system (
            component TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            description TEXT NOT NULL,
            config_path TEXT,
            default_value TEXT
        )
    """)
    energy = [
        ("Generator", "Source", "BLAST_FURNACE + ItemFrame on top. Burns fuel, adds energy each tick.",
         "energy.generator.energy_per_fuel", "100"),
        ("Electric Furnace", "Consumer", "BLAST_FURNACE with cable underneath. Consumes 100 energy + lightning to smelt items.",
         "energy.electric_furnace.energy_per_smelt", "100"),
        ("Cable Network", "Transmission", "COPPER_BLOCK or IRON_BLOCK cables. Do NOT store energy — only transmit. Blink 10off/10on when energy flows.",
         "energy.cable.max_energy", "0 (no storage)"),
        ("Battery (single)", "Storage", "Single block battery. Stores up to 100000 energy, discharges 10/tick with redstone signal.",
         "energy.battery.max_energy", "100000"),
        ("Battery Multiblock", "Storage", "Multiblock battery structure with smooth charge/discharge curves. Charge multiplier 2.0, discharge 0.5.",
         "energy.battery.smooth_charge.enabled", "true"),
        ("Battery Drain", "Distribution", "Distributes energy between batteries. Transfers 5/tick.",
         "energy.battery_drain.transfer_per_tick", "5"),
        ("Energy Balancer", "Balance", "Balances energy levels between connected batteries. Max transfer 25/tick. Does NOT include batteries by default.",
         "energy.balancer.max_transfer", "25"),
        ("Energy Workbench (Assembler)", "Consumer/UI", "Item Assembler GUI. Must right-click CRAFTING_TABLE (registered as workbench). Costs 100 energy per craft from network.",
         "energy_crafting.energy_per_craft", "100"),
        ("Dark Synthesis Reactor", "Source", "Complex nuclear reactor multiblock. Core temp/pressure, shell integrity, casing temp/pressure, wear system, meltdown explosion.",
         "reactor.*", "(many params)"),
        ("Light Multiblock", "Consumer", "Light-emitting multiblock structure. Consumes energy to stay lit.", None, None),
    ]
    c.executemany("INSERT INTO energy_system VALUES (?, ?, ?, ?, ?)", energy)

    # ============================================================
    # 6. DATABASE TABLES
    # ============================================================
    c.execute("""
        CREATE TABLE database_tables (
            table_name TEXT PRIMARY KEY,
            purpose TEXT NOT NULL,
            columns TEXT NOT NULL
        )
    """)
    db_tables = [
        ("cables", "Cable node positions with energy and type",
         "world, x, y, z, energy, type (PK: world,x,y,z)"),
        ("cable_connections", "Cable network connections between nodes",
         "world,x,y,z, to_world,to_x,to_y,to_z"),
        ("workbenches", "Registered energy workbench (CRAFTING_TABLE) locations",
         "world, x, y, z (PK)"),
        ("generators", "Generator block data with fuel and energy",
         "world, x, y, z, fuel, energy (PK)"),
        ("player_radiation", "Per-player radiation dose",
         "uuid (PK), radiation"),
        ("reactors", "Reactor state (temp, pressure, integrity, wear)",
         "reactor_id (PK), world, x, y, z, core_temp, core_press, core_sh_int, case_temp, case_press, case_int, recipe_time, self_destruct, reactor_wear, energy_generated"),
        ("magnets", "Magnet multiblock structures",
         "id (PK), world, center_x,y,z, block_count, active"),
        ("magnet_blocks", "Individual blocks of magnet structures",
         "magnet_id, x, y, z (FK→magnets)"),
        ("batteries", "Battery multiblock structures",
         "id (PK), world, center_x,y,z, block_count"),
        ("battery_blocks", "Individual blocks of battery structures",
         "battery_id, x, y, z (FK→batteries)"),
        ("lights", "Light multiblock structures",
         "id (PK), world, center_x,y,z, block_count, lit"),
        ("light_blocks", "Individual blocks of light structures",
         "light_id, x, y, z (FK→lights)"),
        ("elytra_boost_disabled", "Players with Elytra boost disabled",
         "uuid (PK)"),
        ("dimension_returns", "Dimension return teleport points",
         "uuid (PK), world, x, y, z, yaw, pitch, has_return"),
        ("auth", "Player authentication (password hashes)",
         "uuid (PK), password_hash, salt, ip_address"),
        ("code_panel_keys", "Code panel key storage",
         "key_name (PK), code, command, max_attempts, attempts_used, expires_at, whitelist, blacklist"),
        ("notes", "Player notes",
         "player_uuid, slot_number, content (PK: player_uuid+slot_number)"),
        ("player_homes", "Player home teleport points",
         "uuid, home_name, world, x, y, z, yaw, pitch (PK: uuid+home_name)"),
        ("vanished_players", "Players in vanish state",
         "uuid (PK)"),
        ("updater_state", "Plugin updater state (latest commit SHA, installed tag)",
         "key (PK), value"),
        ("votes", "Vote system — created votes",
         "name (PK), title, question, creator_uuid, created_at, expires_at, ended"),
        ("vote_answers", "Vote system — answer options",
         "vote_name, answer_index, title, description (FK→votes)"),
        ("vote_records", "Vote system — player votes cast",
         "vote_name, player_uuid, answer_index (FK→votes)"),
    ]
    c.executemany("INSERT INTO database_tables VALUES (?, ?, ?)", db_tables)

    # ============================================================
    # 7. COMMANDS
    # ============================================================
    c.execute("""
        CREATE TABLE commands (
            subcommand TEXT PRIMARY KEY,
            handler TEXT NOT NULL,
            permission TEXT,
            description TEXT NOT NULL
        )
    """)
    commands = [
        ("help", "HelpCommand.execute()", "mcplugin", "Show command list"),
        ("chgdim", "ChgDimSubcommand.execute()", "mcplugin.command.chgdim", "Teleport between dimensions"),
        ("codepane", "CodePaneSubcommand.execute()", "mcplugin.command.codepane", "Open code panel GUI"),
        ("pane_click", "CodePaneSubcommand.paneClick()", "mcplugin.command.codepane", "Code panel button click handler"),
        ("structures/str", "StructureSubcommand.execute()", "mcplugin.command.structures", "Manage multiblock structures (dfc, magnet, lightning)"),
        ("item", "ItemSubcommand.execute()", "mcplugin.command.item", "Item management (int list|set|add)"),
        ("auth", "AuthSubcommand.execute()", "mcplugin.command.auth.*", "Auth management (forcelogin, resetauth, chgpass, delsession, logout)"),
        ("power", "PowerSubcommand.execute()", "mcplugin.command.power.*", "Server power management (off, reboot, confirm, undo)"),
        ("suicide", "SuicideCommand.execute()", "mcplugin", "Start suicide countdown"),
        ("forcesuicide", "SuicideCommand.forceExecute()", "mcplugin.command.forcesuicide", "Force another player to suicide"),
        ("modules", "ModulesSubcommand.execute()", "mcplugin", "List/enable/disable modules"),
        ("sethome", "HomeCommand.dispatch()", "mcplugin.command.sethome", "Save home point"),
        ("home", "HomeCommand.dispatch()", "mcplugin.command.home", "Teleport to home"),
        ("delhome", "HomeCommand.dispatch()", "mcplugin.command.delhome", "Delete home point"),
        ("listhomes", "HomeCommand.dispatch()", "mcplugin.command.listhomes", "List homes"),
        ("ophomels", "HomeCommand.dispatch()", "mcplugin.command.ophomels", "List player's homes (admin)"),
        ("opdelhome", "HomeCommand.dispatch()", "mcplugin.command.opdelhome", "Delete player's home (admin)"),
        ("checkver", "UpdateSubcommand.checkOnly()", "mcplugin.command.checkver", "Check for plugin updates"),
        ("updatejar", "UpdateSubcommand.downloadAndReplace()", "mcplugin", "Install plugin update"),
        ("vanish", "MiscSubcommand.vanish()", "mcplugin.command.vanish", "Toggle player vanish"),
        ("notes", "MiscSubcommand.notes()", "mcplugin.command.notes", "Open notes GUI"),
        ("checkrad", "RadiationSubcommand.execute()", "mcplugin.command.checkrad", "Check player radiation level"),
        ("setrad", "RadiationSubcommand.execute()", "mcplugin.command.setrad", "Set player radiation level"),
        ("togglespeed", "MiscSubcommand.toggleSpeed()", "mcplugin", "Toggle speed display"),
        ("togglefly", "MiscSubcommand.toggleFly()", "mcplugin", "Toggle Elytra boost on jump"),
        ("toggleradview", "MiscSubcommand.toggleRadView()", "mcplugin.command.toggleradview", "Toggle radiation display on action bar"),
        ("reload", "ReloadSubcommand.execute()", "mcplugin.command.reload", "Reload plugin config"),
        ("vote", "VoteManager", "mcplugin.command.vote.*", "Voting system (create, delete, change, stats, vote)"),
        ("askcords", "AskCordsManager.execute()", "mcplugin.command.askcords", "Request player coordinates"),
        ("askcords_accept", "AskCordsManager.accept()", "mcplugin.command.askcords", "Accept coordinate request"),
        ("askcords_decline", "AskCordsManager.decline()", "mcplugin.command.askcords", "Decline coordinate request"),
        ("bc", "BroadcastSubcommand.execute()", "mcplugin.command.broadcast", "Broadcast message to all players"),
        ("cilist", "CilistCommand.execute()", "mcplugin", "List all custom items with recipes"),
        ("reactor", "(separate /reactor command)", "mcplugin.command.reactor", "Assemble reactor structures"),
    ]
    c.executemany("INSERT INTO commands VALUES (?, ?, ?, ?)", commands)

    # ============================================================
    # 8. ENERGY CRAFTING / ASSEMBLER SYSTEM
    # ============================================================
    c.execute("""
        CREATE TABLE assembler_system (
            aspect TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    """)
    assembler = [
        ("gui_title", "Item assembler"),
        ("trigger", "RIGHT_CLICK_BLOCK on CRAFTING_TABLE registered as EnergyWorkbench"),
        ("created_by", "AssemblerListener.java — opens Bukkit.createInventory(null, WORKBENCH, 'Item assembler')"),
        ("energy_cost", "100 energy per craft (from config: energy_crafting.energy_per_craft)"),
        ("energy_check", "EnergyCraftingListener checks title='Item assembler', searches connected cable network for 100 energy"),
        ("energy_behavior_no_energy", "Preview shows empty result slot; CraftItemEvent cancelled with error message"),
        ("items_check", "All 11 custom CraftListeners call AssemblerChecker.isAssemblerCraft(e) and reject if not in assembler"),
        ("vanilla_recipes", "Vanilla recipes work normally in assembler (energy still checked by EnergyCraftingListener)"),
        ("regular_tables", "Regular CRAFTING_TABLEs without energy connection work normally for vanilla recipes"),
        ("anti_uncraft", "LeadIngotCraftListener has EventPriority.HIGHEST handler that blocks any non-lead_ingot recipe containing lead ingot"),
    ]
    c.executemany("INSERT INTO assembler_system VALUES (?, ?)", assembler)

    # ============================================================
    # 9. RADIATION SYSTEM
    # ============================================================
    c.execute("""
        CREATE TABLE radiation_system (
            aspect TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    """)
    radiation = [
        ("natural_decay", "1 per tick"),
        ("ancient_debris_radiation", "+2 per tick when in inventory"),
        ("basalt_deltas_radiation", "+2 per tick when in biome"),
        ("end_radiation", "+2 per tick in End under open sky"),
        ("lead_shield_reduction", "-2 per tick when held"),
        ("antirad_reduction", "-100 when eating cake (radiation >= 200)"),
        ("kill_reduction", "-100 when killing player/mob (radiation >= 200)"),
        ("death_reset", "Yes — radiation resets on death"),
        ("mace_use_radiation", "+50 per use"),
        ("trident_use_radiation", "+50 per use"),
        ("elytra_use_radiation", "+50 per use"),
        ("reactor_core_radiation", "+10 per tick near reactor core (coreTemp >= 1000)"),
        ("reactor_pressure_radiation", "+600 every 5 seconds from reactor pressure"),
        ("reactor_meltdown_close", "+6400 in close proximity"),
        ("reactor_meltdown_far", "+3200 within 20 blocks"),
    ]
    c.executemany("INSERT INTO radiation_system VALUES (?, ?)", radiation)

    # ============================================================
    # 10. CONFIG (summary of key paths)
    # ============================================================
    c.execute("""
        CREATE TABLE config_summary (
            config_path TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            default_value TEXT NOT NULL,
            description TEXT NOT NULL
        )
    """)
    config = [
        ("plugin_version", "string", "1.7.75", "Universal version identifier (MAJOR.MINOR.COMMITS)"),
        ("energy_crafting.enabled", "boolean", "true", "Enable energy requirement for Item Assembler"),
        ("energy_crafting.energy_per_craft", "integer", "100", "Energy cost per craft in assembler"),
        ("energy_crafting.workbench_search_radius", "integer", "3", "Radius to search for workbench near player"),
        ("energy.generator.enabled", "boolean", "true", "Enable generator system"),
        ("energy.generator.energy_per_fuel", "integer", "100", "Energy generated per fuel tick"),
        ("energy.generator.fuel_burn_ticks", "integer", "1600", "Fuel burn duration in ticks"),
        ("energy.electric_furnace.enabled", "boolean", "true", "Enable electric furnace"),
        ("energy.electric_furnace.energy_per_smelt", "integer", "100", "Energy per lightning smelt"),
        ("energy.cable.enabled", "boolean", "true", "Enable cables"),
        ("energy.cable.max_energy", "integer", "0", "Cable max energy (0=no storage)"),
        ("energy.cable.blink.enabled", "boolean", "true", "Enable cable blinking on energy flow"),
        ("energy.battery.max_energy", "integer", "100000", "Battery max energy storage"),
        ("energy.battery.discharge_per_tick", "integer", "10", "Energy discharge per tick with redstone signal"),
        ("energy.battery.smooth_charge.enabled", "boolean", "true", "Enable smooth charge/discharge curves"),
        ("energy.balancer.enabled", "boolean", "true", "Enable energy balancer"),
        ("features.integrity.enabled", "boolean", "true", "Enable item integrity system"),
        ("features.integrity.cost_multiplier", "double", "1.0", "Multiplier for integrity consumption rate"),
        ("features.antimatter.enabled", "boolean", "true", "Enable antimatter explosion"),
        ("features.magnet.enabled", "boolean", "true", "Enable magnet system"),
        ("features.unbreakable_breaker.enabled", "boolean", "true", "Enable progressive unbreakable block breaking"),
        ("auth.enabled", "boolean", "true", "Enable authentication system"),
        ("auth.min_password_length", "integer", "8", "Minimum password length"),
        ("auth.session_duration_minutes", "integer", "60", "Auth session duration"),
        ("auth.max_accounts_per_ip", "integer", "3", "Max accounts per IP address"),
        ("radiation.enabled", "boolean", "true", "Enable radiation system"),
        ("suicide.countdown_duration", "integer", "10", "Suicide countdown in seconds"),
        ("home.max_homes", "integer", "10", "Maximum home points per player"),
        ("redstone_guard.enabled", "boolean", "true", "Enable redstone overload protection"),
        ("packet_guard.enabled", "boolean", "true", "Enable packet size guard"),
        ("chat_filter.enabled", "boolean", "false", "Enable chat profanity filter"),
    ]
    c.executemany("INSERT INTO config_summary VALUES (?, ?, ?, ?)", config)

    # ============================================================
    # 11. ARCHITECTURE NOTES
    # ============================================================
    c.execute("""
        CREATE TABLE architecture_notes (
            topic TEXT PRIMARY KEY,
            content TEXT NOT NULL
        )
    """)
    arch = [
        ("Plugin Structure",
         "MC-Plugin uses a modular architecture. Main.java registers all PluginModule subclasses.\n"
         "Each module handles its own init/disable lifecycle independently. Essential modules are\n"
         "marked with isEssential() and log a critical warning if they fail.\n"
         "Command dispatch is handled by PluginReloadCommand which delegates to subcommand classes."),
        ("Energy Flow",
         "Generator (burn fuel) → Cable Network (transmit only, no storage) → Battery (storage via multiblock)\n"
         "→ Consumers (Electric Furnace, Item Assembler, Light).\n"
         "Cables do NOT store energy. They BFS-search to batteries for delivery.\n"
         "BatteryDrain distributes energy between batteries. EnergyBalancer balances levels."),
        ("Custom Item System",
         "All 11 custom items use PDC (PersistentDataContainer) for identification.\n"
         "Keys.java defines all NamespacedKeys. Each item has its own CraftListener class\n"
         "that registers a ShapedRecipe and overrides the result with PDC.\n"
         "Items ONLY craft in the Item Assembler GUI (checked via AssemblerChecker.isAssemblerCraft).\n"
         "Lead Ingot has anti-uncraft protection via HIGHEST priority PrepareItemCraftEvent handler."),
        ("Item Assembler Energy",
         "All registered CRAFTING_TABLE blocks become energy workbenches.\n"
         "Right-click opens 'Item assembler' GUI. EnergyCraftingListener applies to this title only.\n"
         "If network has <100 energy, result is null. If player bypasses and clicks → cancelled + message.\n"
         "100 energy is deducted from network on successful craft."),
        ("Reactor Mechanics",
         "Dark Synthesis Reactor: multiblock structure with core and casing.\n"
         "Core: temperature (-272 to 6000), pressure, shell integrity (0-100), recipe timer.\n"
         "Casing: temperature (-271 to 8000), pressure (0-10000), integrity (0-100).\n"
         "Wear system: normal interval 1200 ticks, degradation interval 20 ticks.\n"
         "Critical failure → meltdown (countdown 30s, final 11s, explosion radius 10)."),
        ("Auth System",
         "GUI-based password authentication. Register → enter password via anvil rename.\n"
         "Login → enter password, click confirm. Passwords hashed with Argon2.\n"
         "Session persists for configurable duration. IP change resets session.\n"
         "Rate limited (1 request per 5 seconds). Max 5 wrong attempts before kick.\n"
         "Max 3 accounts per IP. Duplicate name check for cracked servers."),
    ]
    c.executemany("INSERT INTO architecture_notes VALUES (?, ?)", arch)

    # ============================================================
    # 12. GIT HISTORY
    # ============================================================
    c.execute("""
        CREATE TABLE git_history (
            sha TEXT PRIMARY KEY,
            message TEXT NOT NULL
        )
    """)
    commits = get_git_log(50)
    for commit in commits:
        if " " in commit:
            sha, message = commit.split(" ", 1)
            c.execute("INSERT INTO git_history (sha, message) VALUES (?, ?)", (sha, message))

    c.execute("""
        CREATE TABLE git_last_commits (
            sha TEXT PRIMARY KEY,
            author TEXT,
            date TEXT,
            message TEXT NOT NULL
        )
    """)
    for commit in get_git_log_full(10):
        c.execute("INSERT INTO git_last_commits VALUES (?, ?, ?, ?)",
                  (commit["sha"], commit["author"], commit["date"], commit["message"]))

    conn.commit()
    conn.close()

    # Show summary
    conn = sqlite3.connect(str(DB_PATH))
    c = conn.cursor()
    tables = c.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
    total_rows = 0
    print(f"\n{'='*60}")
    print(f"  .codebuff-memory.db created at: {DB_PATH}")
    print(f"{'='*60}")
    for (tname,) in tables:
        count = c.execute(f"SELECT COUNT(*) FROM [{tname}]").fetchone()[0]
        total_rows += count
        print(f"  📊 {tname}: {count} rows")
    print(f"{'='*60}")
    print(f"  Total: {len(tables)} tables, {total_rows} rows")
    print(f"{'='*60}\n")
    conn.close()

if __name__ == "__main__":
    main()
