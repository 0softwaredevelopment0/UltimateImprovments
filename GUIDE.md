# ✦ Ultimate Improvments — Full Guide

**Version:** 1.8.3-alpha.5
**Core:** Paper 26.3+ (or Leaf fork)
**Database:** SQLite (one shared `database.db` for the whole family)
**Author:** [rizer001](https://github.com/rizer001)

This is the complete guide: installation, every command, custom items, enchantments, achievements, modules, configuration and more.

---

## 📚 Table of Contents

1. [Installation](#-installation)
2. [Architecture & Modules](#-architecture--modules)
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

1. Download the distribution archive `UltimateImprovments-<version>-jars.tar` from
   [Releases](https://github.com/0softwaredevelopment0/UltimateImprovments/releases)
   (or grab the individual `UI-*-all.jar` files).
2. Drop **all** the jars into `plugins/` — the family ships as **21 plugins**
   (UI-Core + 20 addons). They form one system; a set of just a few of them is
   not supported.
3. Restart the server **once**. The bundled datapack is extracted from the
   UI-Datapack jar and enabled automatically **before worlds load**
   (Paper bootstrapper) — no `/datapack enable`, no double restart.

> The datapack lives in its own plugin part — **UI-Datapack**. Its settings
> (`datapack.enabled`, `datapack.modules.*`) are configured in
> `plugins/UltimateImprovments/configs/UI-Datapack.toml`; changing them
> requires a restart (pack discovery happens at server startup).

> Multi-block structures (NBT templates, markers, lightning/magnet mechanics,
> reactor/generator validation) live in **UI-MBS**. Energy-dependent structure
> mechanics talk to UI-Energy through the `MbsEnergy` API bridge, so UI-MBS
> never depends on UI-Energy.

> ⚠ Requires **Paper 26.3+** or a compatible fork (Leaf). Not compatible with Spigot/Bukkit.
> Java **26+** is required.

---

## 🧩 Architecture & Modules

Ultimate Improvments is a **large collection of gameplay features** shipped as
one family: **UI-Core** (the mandatory heart: database, config routing, command
tree, shared systems) plus **20 addons**, each owning its feature domain:

> UI-Shared, UI-Datapack, UI-MBS, UI-Energy, UI-Anticheat, UI-Essentials,
> UI-Combat, UI-Chat, UI-Clans, UI-Punish, UI-Other, UI-Enchant, UI-Auth,
> UI-Protection, UI-Display, UI-Admin, UI-Player, UI-Guard, UI-World, UI-Items

Everything runs on the modern **Paper plugin loader** (`paper-plugin.yml`,
isolated classloaders, explicit dependency graph with `join-classpath`), and
the `/ui` command tree is registered via Brigadier
(`LifecycleEvents.COMMANDS`).

On top of that, every feature is a **module** that can be toggled on/off at
runtime:

```
/ui modules                 — list all modules
/ui modules enable <name>   — enable a module
/ui modules disable <name>  — disable a module
```

If a module fails to load, the rest keep running. Essential modules (Core,
Database, Auth, Crafting, Energy, Reactor, Radiation, Power, Tasks) are always
on; everything else is optional.

**Optional modules include:** Datapack, RedstoneGuard, PacketGuard,
VoidProtection, ChatFilter, UpdateChecker, Vanish, Notes, Magnet, MinecartSpeed,
Lightning, Integrity, Antimatter, Attributes, Beacon, BlockDmg, BoostedCobweb,
ContainerTrigger, DeathBell, DragonEgg, EnderChest, EntityLocator, GlassBreak,
HealthMeter, Leash, ModeProtect, ShieldSlowness, TerracotaSpeed,
UnbreakableBreaker, Waypoint, CreativeItemValidator, WirelessRedstone,
ElytraBoost, Electric Furnace, Battery Drain, Battery Multi, Light Multi, Chat,
Tab, Scoreboard, BossBar, MOTD, Economy, Punish, BotProtection, Omniscanner,
AntiCheat, StructureIntegrity, ParticleAccelerator, Meteor, AutoBroadcast,
**all custom enchantments** (as separate modules), **Turret**, **BeyondSpace/
BedrockBreak/Kaboom/EarthCore/ServerOverload/...** (achievement modules),
DeathLogger, CmdBlockTracker.

The bundled addon set is registered in the **AddonCatalog**; the universal
manager `/ui addon` shows every addon with its status (`/ui addon list`,
permission `ui.command.addons`, included in `ui.*`).

---

## ⌨️ Commands

All commands start with `/ui`. Use `/ui help` (paginated, clickable pages) to
see them in-game. The root `/ui` is a real Brigadier tree with dynamic
tab-completion for all subcommands.

### General
```
/ui help                — paginated command list
/ui reload              — reload the plugin family
/ui checkver            — check for updates
/ui updatejar           — download & install update (with backup)
/ui modules ...         — module management
/ui addon               — addon status
/ui config regen <file> — regenerate an addon config from the template
/ui config reset <addon|all> — reset addon config(s) to defaults
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
/ui codepane key add|remove|list  — code panel keys
/ui maint               — maintenance mode
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
/ui togglespeed / togglefly / togglesb / togglebb / toggleping
/ui radview <on|off>    — admin radiation overlay (dosimeter format in the actionbar)
```

### Technology & Structures
```
/ui str dfc assemble|stats        — Dark Fusion Reactor
/ui str magnet assemble|stats     — Magnet structure
/ui str lightning enable|disable|stats — Lightning structure
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

Custom items are crafted in the **Crafter** block (recipe preview is visible in
the vanilla workbench and recipe book, but the actual craft only works in the
Crafter). Recipes come from the bundled datapack.

| Item | What it does |
|------|--------------|
| **Blazing Sword** | Golden sword, 1024 durability. On hit: sets the target on fire (7s) and deals burn damage over time — armor reduces it. |
| **Glass Sword** | 1 durability, deals **19 damage** on hit, then shatters. Breaks when used to break a block too. |
| **Electric Trident** | Trident, 512 durability. Strikes a single lightning bolt at whatever it hits, plus its normal damage. |
| **Photon Cannon** | Long-range projectile weapon. |
| **Electro Shoker** | Close-combat projectile weapon. |
| **Antimatter Flask** | Explosive item — devastating blast. |
| **Multimeter** | Inspect block/energy information. |
| **Metal Detector / Ore Finder / Mob Finder / Entity Locator** | Scanning tools. |
| **Health Meter** | Shows mob health. |
| **Portable Radar** | Nearby entity radar. |
| **Lead Ingot** | Radiation-crafting material (cannot be un-crafted back); used for the Hazmat Suit and the Dosimeter. |
| **Hazmat Suit** (4 leather pieces) | Radiation protection: −20% per piece, −80% for the full set. |
| **Dosimeter** | Shows the radiation level in the actionbar (emerald is the crafting sensor). |
| **Concrete Bucket** | Place concrete instantly. |
| **Structure Integrity Indicator** | Shows structure integrity. |
| **Particle Engine / Injector / Ring / Speed Sensor** | Particle accelerator components. |
| **Chunk Loader** | Keep chunks loaded (consumes an XP bottle). |
| **Ender Chest (Портативное хранилище)** | Portable storage. |

The bundled **datapack** also modifies/overrides vanilla recipes (netherite,
bookshelves, chainmail, heavy core, etc.) and adds all recipes under the `ui:`
namespace. Custom **enchantments in the enchanting table come exclusively from
the datapack** (no listeners involved).

---

## ✨ Custom Enchantments

Custom enchantments are data-driven (datapack-registered under `ui:`), applied
in the enchanting table (third slot) or via anvil / `/ui enchant give`. Most
support levels **1–255**; a few have gameplay caps:

| Enchantment | Levels | Effect |
|-------------|--------|--------|
| **AoE** (`ui:aoe`) | 1–8 | Area damage around the hit target, no falloff; blocked entities behind walls aren't hit. |
| **Attack AoE** (`ui:attack_aoe`) | 1–10 | Hits entities of the same type as the attacked one; full damage, no falloff, radius = level. |
| **Auto Smelt** (`ui:autosmelt`) | 1–10 | Level N = N×10% smelt chance rolled per dropped stack (works with AoE/VeinMiner — partial smelting). |
| **Vein Miner** (`ui:veinminer`) | 1–255 | Mine an entire ore vein at once. |
| **TreeCapitator** (`ui:treecapitator`) | 1–255 | Fells the whole tree. |
| **Flight** (`ui:flight`) | 1–255 | Jetpack-style flight; drains 1 integrity use per second from every worn piece (chest pays while gliding too). Incompatible with Repairing on the chest slot. |
| **Levitation** (`ui:levitation`) | 1–255 | Launches hit targets into the air; boosting drains 1 use/s from the chestplate. |
| **Magnet** (`ui:magnet`) | 1–16 | Attracts nearby items; radius = 2 blocks × level (cap 32). |
| **Igniting** (`ui:igniting`) | 1–255 | Sets hit targets on fire (water extinguishes fairly). |
| **Repairing** (`ui:repairing`) | 1–255 | Restores `level` durability points once per second (higher level = strictly better). |
| **Lava Walker** (`ui:lava_walker`) | 1–16 | Frost Walker for lava: obsidian crust under the feet, melts back after 20–45 s; 1 integrity per sweep. |
| **Container Stealing** (`ui:container_stealing`) | 1–10 | Level N = N×10% chance to steal a whole container with its contents on break; failed roll = fully vanilla break. |
| **Item Stealing** (`ui:item_stealing`) | 1–10 | Level N = N×10% chance to yank the held item from the hit target — the item physically flies to you. Respects the `ui.enchant.itemstealing.steal` permission. |
| **Self-Destruct** (`ui:self_destruct`) | 1–255 | 30s countdown (shown in the lore); the item locks in the owner's inventory only; then 19 damage and the item is destroyed. |
| **Degradation** (`ui:degradation`) | 1–255 | Cursed: worn armor degrades over time. |
| **Blunting** (`ui:blunting`) | 1–255 | Cursed "reverse sharpness": your own melee hits deal 0.5 less damage per level while holding the weapon. |
| **Vulnerability** (`ui:vulnerability`) | 1–255 | Cursed: wearer takes extra damage. |
| **Disappearance** (`ui:disappearance`) | 1–255 | Cursed: on a timed sweep the held item vanishes (pop sound + actionbar with the item name). |

Curses deliberately are **not** in the vanilla `#minecraft:curse` tag, so the
enchanting table can offer them. `/ui enchant` gives/takes/checks any of them.

---

## 🏆 Achievements

The plugin ships a **custom achievement tree** in the `ui:` namespace
(installed via the datapack), with 5 branches all growing from one root
(`ui:datapack/start` — "UltimateImprovments"). All custom achievements are
granted through plugin code (progress is stored in vanilla advancement data).

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

**Timed challenges** run with `/ui advancement start woodcutter|teleport`
(only one active challenge per player at a time; progress shown in the
actionbar; stop with `/ui advancement stop`).

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
- Kick screens and chat notifications are **fully configurable** in
  `configs/UI-Punish.toml` under `messages.punishment` (and
  `messages_en.punishment`) — MiniMessage format, placeholders `%player%`,
  `%punisher%`, `%reason%`, `%duration%`, `%discord_url%` (the Discord link is
  clickable in chat).
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
- Protection: **Hazmat Suit** (−20% per piece, −80% full set); eating lowers radiation (−10 rad at ≥ 200, actionbar feedback).
- **Dosimeter** shows levels in the actionbar; admins get a permanent overlay
  with `/ui radview <on|off>` (permission `ui.command.radview`).

### Structures
- **Magnet** — attracts metallic items (radius scales with structure).
- **Lightning** — controlled lightning strikes.
- **Particle Accelerator** — configurable particle acceleration.

---

## 🌍 World & Player Features

- **Custom chat** — per-group/per-world formats, player MiniMessage, pings (`@everyone`, `@nick`, ...), chat filter with wildcard+regex (Cyrillic-aware).
- **Tab / Scoreboard / BossBar / MOTD** — custom display systems; scoreboard supports gradients (`<gradient>`, `<rainbow>`) and placeholders.
- **Armor effects & trim effects** — configurable potion effects from worn armor materials and smithing-table trim materials (fully configurable units: effect, level, duration, check interval).
- **Item integrity** — every item has 0–100% integrity; anvil repair, combining, XP mending; color gradient in lore.
- **Totem charge** — charged totems (charge via anvil with netherite scrap) save your life once per charge.
- **Homes, spawn, RTP, dimension teleportation, notes, vanish** (persists across restarts).
- **Minecart speed** — acceleration on powered rails, collision damage = speed × 20, particles.
- **Omniscanner** — admin scanner with strict whitelists (blocks/items/entities); also finds items inside entity inventories (minecarts with chests, chested horses, villagers, ...).
- **Meteor showers**, **auto-broadcast** (conditions: is-op, is-gamemode, height, health, hunger, is-group, online-*, xp-lvl-*), **death bell**, **glass breaking**, **shield slowness**, **terracotta speed**, **boosted cobweb**, **entity locator**, **exp bottle upgrade**, **netherite upgrade**.
- **World clock / timelines** — `/time` works correctly in all dimensions.

---

## 🔑 Permissions

| Permission | Description |
|------------|-------------|
| `ui.*` | Everything |
| `ui.command.<name>` | Access to `/ui <name>` |
| `ui.command.*` | All commands |
| `ui.command.radview` | Admin radiation overlay (default FALSE) |
| `ui.command.configregen` / `ui.command.configreset` | Config regen/reset commands (default FALSE, additionally gated by `config.commands_enabled`) |
| `ui.enchant.itemstealing.steal` | Allows Item Stealing to trigger (default FALSE) |
| `ui.chat.filter.bypass` | Bypass the chat filter |
| `ui.packetguard.bypass` | Bypass packet size limits |
| `ui.gmprotect.bypass` | Bypass game-mode protection |
| `ui.creative.bypass` | Bypass creative item validation |
| `ui.show.brand` | Show the server brand in F3 |
| `ui.alerts` | Receive server alerts (used by auto-broadcast conditions) |

Permissions are registered **in code** and default to OP-only where it matters.

---

## ⚙️ Configuration

- **Per-addon TOML** — each addon owns
  `plugins/UltimateImprovments/configs/UI-<Addon>.toml` (UI-Core.toml,
  UI-Enchant.toml, UI-Guard.toml, ...): the single source of defaults, no
  monolithic config. Missing keys are auto-repaired from the bundled templates
  on startup; on a parse failure the broken file is backed up and restored.
- **Language** — `messages.lang` (`en`/`ru`) in UI-Core.toml; message sections
  exist in both languages (`messages.*` RU, `messages_en.*` EN).
- **Punishment messages** — `messages.punishment` / `messages_en.punishment`
  in UI-Punish.toml, MiniMessage lists with `%player%`, `%punisher%`,
  `%reason%`, `%duration%`, `%discord_url%` placeholders.
- **Auto Broadcast** — `[auto_broadcast]` section in its addon's TOML with
  sections, cooldowns and condition strings.
- **Armor/trim effects** — units with `duration_ticks` + `check_interval_ticks`
  (defaults 40/40).
- **Config recovery** — `/ui config regen <UI-<Addon>.toml>` and
  `/ui config reset <addon|all>`: numbered backups
  (`configs/UI-<Addon>-broken-<N>.toml`), triple-gated (permission +
  `config.commands_enabled` flag + clickable confirmation).
- Reload with `/ui reload` — module toggles and most config changes apply
  without a restart (the datapack itself is re-discovered only at startup).

---

## 🗄 Database (SQLite)

One shared database for the whole family: `plugins/UltimateImprovments/database.db`
(WAL mode). Main tables: `auth_users`, `auth_sessions`, `cables`,
`cable_connections`, `code_panel_keys`, `player_homes`, `player_notes`,
`player_radiation`, `updater_state`, `vanished_players`, `magnet_state`,
`reactor_state`, `player_settings`, `punishments`, `warns`, `whitelist`,
`blacklist`, `reports`, `lava_walker_melts` and more.

---

## 🏗️ Building & Updating

```bash
git clone https://github.com/0softwaredevelopment0/UltimateImprovments.git
cd UltimateImprovments
./gradlew distributeJars   # fast: shadow-only, all 21 jars into build/libs/
./gradlew build            # full build with tests
```

The jars land in `build/libs/UI-<Addon>-<version>-all.jar`. Requirements: JDK 26+, Git.

**Updating:**
1. Replace all `UI-*-all.jar` files in `plugins/` with the new version.
2. Run `/ui reload` (or restart).
3. The datapack re-extracts itself on server start; config auto-repair adds new sections.

---

## 🧪 For Developers

- **PlaceholderAPI** — all plugin placeholders register through PAPI if installed (`%ui_player_name%`, `%ui_player_world%`, `%ui_server_time%`, `%ui_online%`, ...), with an internal fallback resolver.
- **Soft-dependencies:** PlaceholderAPI, LuckPerms (wildcard blocking), Vault (economy).
- **Architecture:** every addon is a Gradle module with `paper-plugin.yml` and
  an explicit dependency graph (`join-classpath`); cross-addon calls are
  decoupled through the `CoreHooks` indirection layer; each addon owns its
  `config/UI-<Addon>.toml` bundle and routes config keys via `AddonCatalog`.

---

*Build date: 2026-09-29 | Latest version: 1.8.3-alpha.5*
