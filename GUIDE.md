# ✦ Ultimate Improvments — Full Guide

**Version:** 1.9
**Core:** Paper 26.2+ (or Leaf fork)
**Database:** SQLite
**Author:** [rizer001](https://github.com/rizer001)

This is the complete guide: installation, every command, custom items, enchantments, achievements, modules, configuration and more.

---

## 📚 Table of Contents

1. [Installation](#-installation)
2. [Modules](#-modules)
3. [Commands](#-commands)
4. [Custom Items & Crafting](#-custom-items--crafting)
5. [Custom Enchantments](#-custom-enchantments)
6. [Achievements](#-achievements)
7. [Turrets](#-turrets)
8. [Security & Administration](#-security--administration)
9. [Technology & Energy](#-technology--energy)
10. [World & Player Features](#-world--player-features)
11. [Permissions](#-permissions)
12. [Configuration](#-configuration)
13. [Database](#-database)
14. [Building & Updating](#-building--updating)

---

## 🚀 Installation

1. Download the `.jar` from [Releases](https://github.com/rizer001/UltimateImprovments/releases).
2. Drop it into `plugins/`.
3. Restart the server **twice** — the first run installs the bundled datapack, the second activates it.

> ⚠ Requires **Paper 26.2+** or a compatible fork (Leaf). Not compatible with Spigot/Bukkit.
> Java **26+** is required.

---

## 🔌 Modules

The plugin is built on a modular architecture. Each feature is a module that can be toggled on/off at runtime:

```
/ui modules                 — list all modules
/ui modules enable <name>   — enable a module
/ui modules disable <name>  — disable a module
```

If a module fails to load, the rest keep running. Essential modules (Core, Database, Auth, Crafting, Energy, Reactor, Radiation, Power, Tasks) are always on; everything else is optional.

**Optional modules include:** Datapack, RedstoneGuard, PacketGuard, VoidProtection, ChatFilter, UpdateChecker, Vanish, Notes, Magnet, MinecartSpeed, Lightning, Integrity, Antimatter, Attributes, Beacon, BlockDmg, BoostedCobweb, ContainerTrigger, DeathBell, DragonEgg, EnderChest, EntityLocator, GlassBreak, HealthMeter, Leash, ModeProtect, ShieldSlowness, TerracotaSpeed, UnbreakableBreaker, Waypoint, CreativeItemValidator, WirelessRedstone, ElytraBoost, Electric Furnace, Battery Drain, Battery Multi, Light Multi, Chat, Tab, Scoreboard, BossBar, MOTD, Economy, Punish, BotProtection, ProxyServer, Assembler, Omniscanner, AntiCheat, StructureIntegrity, ParticleAccelerator, Meteor, AutoBroadcast, **all custom enchantments** (as separate modules), **Turret**, **BeyondSpace/BedrockBreak/Kaboom/EarthCore/ServerOverload/...** (achievement modules), DeathLogger, CmdBlockTracker.

---

## ⌨️ Commands

All commands start with `/ui`. Use `/ui help` (paginated, 5 per page, clickable pages) to see them in-game.

### General
```
/ui help                — paginated command list
/ui reload              — reload the plugin
/ui checkver            — check for updates
/ui updatejar           — download & install update (with backup)
/ui modules ...         — module management
```

### Security & Punishments
```
/ui punish <nick> ban|mute|kick|warn <reason> [-time:30s|5m|2h|7d] [-permanent] [-ip] [-hw]
/ui punish actionlist   — list all active punishments
/ui punish unban|unmute|unwarn <nick>
/ui whitelist on|off|add|remove|list <nick>
/ui opwhitelist ...     — OP whitelist
/ui blacklist on|off|add|remove|list <nick>
/ui check <player>      — freeze player (anti-cheat)
/ui uncheck <player>    — unfreeze
/ui ac                  — anti-cheat stats
/ui sudo                — sudo mode (dangerous actions)
/ui cmdblocklist        — list active command blocks (#, world, coordinates)
/ui report <nick> <reason> — report a player
/ui reports ...         — report management
```

### Player Inventory
```
/ui invsee <nick>       — view/edit inventory (online AND offline, edits .dat with backup)
/ui endersee <nick>     — view/edit ender chest (online AND offline)
```

### Worlds & Teleportation
```
/ui chgdim              — dimension/world menu
/ui chgdim_teleport <world> — teleport to a world
/ui sethome <name>      — save a home
/ui home <name>         — teleport to a home
/ui listhomes           — list your homes
/ui delhome <name>      — delete a home
/ui ophomels / opdelhome — OP home management
/ui spawn / ui setspawn — spawn point
/ui near                — find nearby players
/ui rtp                 — random teleport
/ui getpos <nick>       — get player coordinates
/ui askpos <nick>       — request coordinates via dialog
/ui uuid <nick>         — get player UUID
```

### Player Actions
```
/ui suicide             — commit suicide (two-step confirm + countdown)
/ui forcesuicide <nick> — force-suicide a player
/ui fly <nick> / ui flyspeed <n>
/ui god <nick>          — god mode
/ui heal <nick> / ui feed <nick>
/ui vanish <nick>       — vanish a player
/ui notes               — open personal notes
/ui vote                — voting system
/ui expsplit            — split experience
/ui enchant ...         — enchantment manager
/ui pdc ...             — PDC (persistent data) manager
/ui item int list|set|add — item integrity
/ui unlock              — unlock book or sign
/ui togglespeed / togglefly / togglesb / togglebb / toggleping / toggleradview
```

### Technology & Structures
```
/ui str dfc assemble|stats        — Dark Fusion Reactor
/ui str magnet assemble|stats     — Magnet structure
/ui str lightning enable|disable|stats — Lightning structure
/ui codepane key add|remove|list  — code panel keys
/ui turret                        — turret configuration (see Turrets section)
/ui redstone                      — blocked redstone chunks
/ui protection                    — protection block admin
/ui menu                          — admin GUI
/ui toggleautocraft               — autocraft toggle
/ui togglebind                    — wireless redstone toggle
```

### Economy & Server
```
/ui money ...           — economy management
/ui broadcast <msg>     — broadcast (-clean = no prefix)
/ui clearchat <nick|all> — clear chat
/ui setrad <nick> <n>   — set radiation
/ui checkrad [nick]     — check radiation
/ui power off|reboot|confirm|undo — server power
/ui maint               — maintenance mode
/ui plugin              — plugin management
/ui swapjar             — swap plugin jar
/ui op <nick> / deop <nick> / chgop <nick>
/ui meteor              — meteor module
/ui cilist              — custom item list
/ui dont_run_this_command — grants the "impossible" achievement (don't run it!)
```

### Advancement Challenges
```
/ui advancement start woodcutter|teleport|let_me_teleport — start a timed challenge
/ui advancement stop    — stop the active challenge
```

> `/stop` and `/restart` are intercepted and route through `/ui power off|reboot`.

---

## 🗡 Custom Items & Crafting

Custom items are crafted in the **Crafter** block (recipe preview is visible in the vanilla workbench and recipe book, but the actual craft only works in the Crafter). All items are given to players via a datapack recipe book.

| Item | What it does |
|------|--------------|
| **Blazing Sword** (`<white>Blazing sword`) | Golden sword, 1024 durability. On hit: sets the target on fire (7s) and deals burn damage over time — armor reduces it. |
| **Glass Sword** (`<white>Glass sword`) | 1 durability, deals **19 damage** on hit, then shatters. Breaks when used to break a block too. |
| **Electric Trident** (`<white>Eletric trident`) | Trident, 512 durability. Strikes a single lightning bolt at whatever it hits, plus its normal damage. |
| **Photon Cannon** (`<white>Photon cannon`) | Long-range projectile weapon. |
| **Electro Shoker** (`<aqua>Electro Shoker`) | Close-combat projectile weapon. |
| **Antimatter Flask** | Explosive item — devastating blast. |
| **Multimeter** | Inspect block/energy information. |
| **Metal Detector / Ore Finder / Mob Finder / Entity Locator** | Scanning tools. |
| **Health Meter** | Shows mob health. |
| **Portable Radar** | Nearby entity radar. |
| **Lead Ingot / Lead Shield / Dosimeter** | Radiation protection and measurement. |
| **Concrete Bucket** | Place concrete instantly. |
| **Structure Integrity Indicator** | Shows structure integrity. |
| **Particle Engine / Injector / Ring / Speed Sensor** | Particle accelerator components. |
| **Chunk Loader** | Keep chunks loaded. |
| **Ender Chest (Портативное хранилище)** | Portable storage. |

The bundled **datapack** also modifies/overrides vanilla recipes (netherite, bookshelves, chainmail, heavy core, etc.) and adds all recipes under the `ui:` namespace.

---

## ✨ Custom Enchantments

All custom enchantments work like vanilla ones (applied via anvil/enchant command), support levels **1–255** and are registered as separate modules. Use `/ui enchant` to give/take/check them.

| Enchantment | Effect |
|-------------|--------|
| **AoE** (`ui:aoe`) | Area damage around the hit target; damage falls off with distance (per-block falloff %); blocked entities behind walls aren't hit. |
| **Attack AoE** (`ui:attack_aoe`) | Hits only entities of the same type as the one you attacked. |
| **Auto Smelt** (`ui:autosmelt`) | Ores smelt automatically when mined. |
| **Vein Miner** (`ui:veinminer`) | Mine an entire ore vein at once. |
| **TreeCapitator** (`ui:treecapitator`) | Fells the whole tree. |
| **Flight** (`ui:flight`) | Allows flying (jetpack-style); consumes item durability/integrity per second while flying. |
| **Magnet** (`ui:magnet`) | Attracts nearby items to the player. |
| **Igniting** (`ui:igniting`) | Sets hit targets on fire. |
| **Levitation** (`ui:levitation`) | Launches hit targets into the air. |
| **Repairing** (`ui:repairing`) | Restores item integrity every 1 sec; cooldown and amount scale with level (lvl 1 = 0.1%, lvl 255 = 25.5%). |
| **Self-Destruct** (`ui:self_destruct`) | 30s timer (shown in the lore as `<red>Self-destruct: <white><sec><gray>s`); the item can't be moved/dropped; after the timer — 19 damage and the item is destroyed. No sounds/particles for the victim. |
| **Degradation** (`ui:degradation`) | Item degrades over time. |
| **Item Stealing** (`ui:item_stealing`) | Steals items from hit targets. |

---

## 🏆 Achievements

The plugin ships a **custom achievement tree** in the `ui:` namespace (installed via the datapack), with 5 branches all growing from one root (`ui:datapack/start` — "UltimateImprovments"). All custom achievements are granted through plugin code (progress is stored in vanilla advancement data).

### Branch: Server
```
Something's not right here... (stay online 5s while MSPT > 50)
→ We're shutting down! (be online when the server shuts down via /ui power)
→ java.lang.OutOfMemoryError (be online when JVM heap ≥ 95%)
→ The server has not responding! (be online when the main thread freezes ≥ 10s)
```

### Branch: Challenges
```
Kaboom! (kill a mob with 1000+ damage from ONE mace hit)
→ The Woodcutter at Full Throttle (challenge: mine 7200 wood in 1 hour)
→ Unachievable Achievement (run /ui dont_run_this_command)
→ Suicide (commit suicide)
→ Let me teleport! (challenge: 60 ender-pearl teleports in 1 minute)
→ A netherite king (hold a netherite block in your inventory)
```

### Branch: Technology
```
Large capacities (craft ender chest) → ... → Discharge! (craft shoker)
→ Beyond Space (reach the world height limit; checked every 1s)
→ Where is the Earth's core here? (reach the world build limit at the bottom)
→ Hit, hit, to pieces! (break a bedrock block — possible via UnbreakableBreaker)
```

### Branch: Research
```
People of the Past (find an End village) → High Temperatures → Light Attack
```

### Branch: Reactor
```
Advanced Science (start the DFC) → We did it! (complete a reactor recipe)
→ Fading signals (DFC self-destruct) / Destructive Consequences (explode DFC)
→ In the Depths of Hell → Large Microwave (burn inside DFC) → One-time heating
```

**Timed challenges** run with `/ui advancement start woodcutter|teleport` (only one active challenge per player at a time; progress shown in the actionbar; stop with `/ui advancement stop`).

---

## 🔫 Turrets

End-crystal turrets are a ranged defense system:

- **Place** an end crystal anywhere.
- **Shift + RMB** on the crystal opens a chat GUI:
  - toggle the turret **on/off** (off by default),
  - switch **whitelist/blacklist** mode,
  - **add/remove/list/clear** targets (player names or entity types),
  - `/ui turret toggle|mode|add <target>|remove <target>|list|clear` (same actions via command).
- Turrets automatically fire beams at targets within **16×16×16** blocks, dealing **1 damage per tick** (mitigated by armor — no bypass).
- The beam **cannot pass through blocks** — line of sight is required.
- The turret works even when its owner is offline.

---

## 🛡 Security & Administration

### Auth
- Registration/login via a custom Anvil GUI.
- **Argon2id** password hashing.
- **GitHub 2FA** (OAuth) — clickable link in chat.
- IP check, account limit per IP, sessions, login timeout, max attempts, password change, force login, registration reset.

### Punishments
`/ui punish <nick> <ban|mute|kick|warn> <reason> [-time:30s|5m|2h|7d] [-permanent] [-ip] [-hw]`

- Temporary and permanent punishments, IP and hardware-ID scopes.
- Ban/mute/warn expiration with countdown.
- Kick screens and chat notifications are **fully configurable** in `config.yml` under `messages.punishment` (and `messages_en.punishment`) — MiniMessage format, placeholders `%player%`, `%punisher%`, `%reason%`, `%duration%`, `%discord_url%` (the Discord link is clickable in chat).
- Whitelist/blacklist are custom database systems independent of the vanilla whitelist.

### Anti-cheat & Protection
- Freeze/check players (`/ui check`), anti-cheat stats.
- **PacketGuard** — crash packet protection.
- **RedstoneGuard** — redstone update rate limiter.
- **BotProtection** — join rate limiting.
- **CreativeItemValidator** — validates creative items (size, PDC, lore).
- **CmdBlockTracker** — lists active command blocks.
- OP command / whitelist command blockers.

### Offline inventory editing
`/ui invsee <nick>` and `/ui endersee <nick>`:
- Online players are edited via the API;
- **Offline players are edited by reading/writing their `.dat` file** — a backup (`<uuid>-backup.dat`) is created next to it before saving;
- Multiverse-compatible (uses the player file from the correct world folder).

---

## ⚡ Technology & Energy

### Dark Fusion Reactor (DFC)
Multi-block structure (iron/copper/redstone blocks, lightning rods, item frame). Features:
- Core/case temperature, pressure, shell/case integrity simulation.
- Heating/cooling modes via redstone, fuel (diamond/gold blocks).
- Recipe progress — crafts ancient debris.
- Wear system — the reactor degrades over time.
- States: normal → degradation → self-destruct → meltdown.
- Emits radiation at high temperatures.

### Energy Network
- **Cables** — waxed lightning rod (straight) / waxed chiseled copper (corner).
- **Batteries** — waxed copper grate (storage).
- **Generators**, **electric furnace**, **energy workbench** (custom crafting with energy cost).
- Background tasks: energy loss, cable tick, battery drain, balancing; SQLite persistence; connection visualization.

### Radiation
- Levels: Safe → Mild → Moderate → High → Critical → Deadly → Lethal.
- Sources: ancient debris in inventory, basalt deltas, the End, weapons (mace/trident/elytra), the reactor.
- Protection: lead shield, antirad; dosimeter shows levels in the actionbar.

### Structures
- **Magnet** — attracts metallic items (radius scales with structure).
- **Lightning** — controlled lightning strikes.
- **Particle Accelerator** — configurable particle acceleration.

---

## 🌍 World & Player Features

- **Custom chat** — per-group/per-world formats, player MiniMessage, pings (`@everyone`, `@nick`, ...), chat filter with wildcard+regex (Cyrillic-aware).
- **Tab / Scoreboard / BossBar / MOTD** — custom display systems; scoreboard supports gradients (`<gradient>`, `<rainbow>`) and placeholders.
- **Item integrity** — every item has 0–100% integrity; anvil repair, combining, XP mending; color gradient in lore.
- **Totem charge** — charged totems (charge via anvil with netherite scrap) save your life once per charge.
- **Homes, spawn, RTP, dimension teleportation, notes, vanish** (persists across restarts).
- **Minecart speed** — acceleration on powered rails, collision damage = speed × 20, particles.
- **Meteor showers**, **auto-broadcast** (conditions: is-op, is-gamemode, height, health, hunger, is-group, online-*, xp-lvl-*), **death bell**, **glass breaking**, **shield slowness**, **terracotta speed**, **boosted cobweb**, **entity locator**, **exp bottle upgrade**, **netherite upgrade**.
- **World clock / timelines** — `/time` works correctly in all dimensions.

---

## 🔑 Permissions

| Permission | Description |
|------------|-------------|
| `ui.admin`, `ui.*` | Everything |
| `ui.command.<name>` | Access to `/ui <name>` |
| `ui.command.*` | All commands |
| `ui.chat.filter.bypass` | Bypass the chat filter |
| `ui.packetguard.bypass` | Bypass packet size limits |
| `ui.gmprotect.bypass` | Bypass game-mode protection |
| `ui.creative.bypass` | Bypass creative item validation |
| `ui.show.brand` | Show the server brand in F3 |
| `ui.alerts` | Receive server alerts (used by auto-broadcast conditions) |

Permissions are registered **in code** (not in `plugin.yml`) and default to OP-only where it matters.

---

## ⚙️ Configuration

- **`config.yml`** — all settings: auth, reactor, energy, radiation, features, chat, protection, modules, and more. The plugin auto-repairs it: missing keys are appended on startup, and on `InvalidConfigurationException` the file is restored.
- **Punishment messages** — `messages.punishment` (RU) / `messages_en.punishment` (EN), each message is a YAML list of MiniMessage lines; placeholders `%player%`, `%punisher%`, `%reason%`, `%duration%`, `%discord_url%`.
- **Auto Broadcast** — `auto_broadcast` section with sections, cooldowns and condition strings.
- Reload with `/ui reload` — module toggles and most config changes apply without a restart.

---

## 🗄 Database (SQLite)

Stored in the plugin data folder. Main tables: `auth_users`, `auth_sessions`, `cables`, `cable_connections`, `code_panel_keys`, `player_homes`, `player_notes`, `player_radiation`, `updater_state`, `vanished_players`, `magnet_state`, `reactor_state`, `player_settings`, `punishments`, `warns`, `whitelist`, `blacklist`, `reports`.

---

## 🏗️ Building & Updating

```bash
git clone https://github.com/rizer001/UltimateImprovments.git
cd UltimateImprovments
./gradlew build
```

The JAR lands in `Jar/UltimateImprovments-<version>.jar`. Requirements: JDK 26+, Git.

**Updating:**
1. Delete the old datapack in `world/datapacks/`.
2. Replace the `.jar`.
3. Run `/ui reload` (or restart).
4. The config auto-repair adds new sections.

---

## 🧪 For Developers

- **PlaceholderAPI** — all plugin placeholders register through PAPI if installed (`%ui_player_name%`, `%ui_player_world%`, `%ui_server_time%`, `%ui_online%`, ...), with an internal fallback resolver.
- **Soft-dependencies:** PlaceholderAPI, LuckPerms (wildcard blocking), Vault (economy).
- **Project layout:** `chat/`, `combat/`, `command/`, `config/`, `core/`, `database/`, `display/`, `economy/`, `enchantment/`, `energy/`, `hook/`, `mechanics/`, `module/`, `punish/`, `report/`, `whitelist/`, and more.

---

*Build date: 2026-08-18 | Latest version: 1.9*
