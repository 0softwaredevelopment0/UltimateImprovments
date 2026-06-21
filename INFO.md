# ✦ MC-Plugin — Full Feature Overview

**Version:** 26.2  
**Recommended Core:** Leaf (Paper-compatible)  
**Database:** SQLite  
**Author:** rizer001  

---

## 📋 Table of Contents

1. [Module System](#-module-system)
2. [Authentication (Auth)](#-authentication-auth)
3. [Dark Fusion Reactor (R.T.S)](#-dark-fusion-reactor-rts)
4. [Energy System (Cable Network)](#-energy-system-cable-network)
5. [Radiation](#-radiation)
6. [Magnet](#-magnet)
7. [Lightning Structure](#-lightning-structure)
8. [Code Panel](#-code-panel)
9. [Custom Crafting](#-custom-crafting)
10. [Integrity System](#-integrity-system)
11. [Minecart Speed](#-minecart-speed)
12. [Home Points (Homes)](#-home-points-homes)
13. [Dimension Change](#-dimension-change)
14. [Power Management](#-power-management)
15. [Suicide](#-suicide)
16. [Vanish](#-vanish)
17. [Notes](#-notes)
18. [Updater](#-updater)
19. [Chat Filter](#-chat-filter)
20. [Server Protection](#-server-protection)
21. [Other Mechanics](#-other-mechanics)
22. [Commands (/mp)](#-commands-mp)

---

## 🔌 Module System

The plugin is built on a modular architecture. **Each module = one feature**. If one module fails, the rest continue working. Modules can be toggled on/off at runtime via `/mp modules`.

**Essential modules (always on):**
| Module | Function |
|--------|----------|
| `Core` | Plugin core (commands, tasks, general listeners) |
| `Database` | SQLite database management |
| `Auth` | Authentication system |
| `Crafting` | Custom item recipes |
| `Cable` | Cable network (energy transfer) |
| `Energy` | Energy management (balancer, generator, battery drain) |
| `Reactor` | Dark Fusion Reactor |
| `Power` | Server shutdown/restart system |
| `Radiation` | Player radiation system |
| `Tasks` | Background tasks |

**Optional modules (can be toggled):**
| Module | Function |
|--------|----------|
| `Datapack` | Datapack installation |
| `RedstoneGuard` | Redstone anti-lag |
| `PacketGuard` | Packet size protection |
| `VoidProtection` | Void fall prevention |
| `ChatFilter` | Profanity filter |
| `UpdateChecker` | Automatic update checking |
| `VersionCheck` | Version compatibility check |
| `AutoSave` | Auto-save to DB (every 5 min) |
| `Vanish` | Player hiding system |
| `Notes` | Player notes system |
| `Magnet` | Magnet structure |
| `MinecartSpeed` | Minecart acceleration |
| `Lightning` | Lightning structure |
| `Integrity` | Item integrity (durability) system |
| `Antimatter` | Antimatter item |
| `Attributes` | Custom item attributes |
| `Beacon` | Enhanced beacon effects |
| `BlockDmg` | Custom block hardness |
| `BoostedCobweb` | Enhanced cobweb |
| `ContainerTrigger` | Container open triggers |
| `DeathBell` | Custom death bell |
| `DragonEgg` | Custom dragon egg behavior |
| `EnderChest` | Extended ender chest |
| `EntityLocator` | Entity finding item |
| `GlassBreak` | Realistic glass breaking |
| `HealthMeter` | Mob health display |
| `ItemKill` | Conditional item removal |
| `Leash` | Enhanced entity leashing |
| `ModeProtect` | GameMode protection per world |
| `ShieldSlowness` | Shield movement penalty |
| `TerracotaSpeed` | Speed boost on terracotta |
| `UnbreakableBreaker` | Break unbreakable blocks |
| `Waypoint` | Teleport point system |
| `CreativeItemValidator` | Creative item validation |

**Module management commands:**
```
/mp modules list              — list all modules and their status
/mp modules enable <name>     — enable a module
/mp modules disable <name>    — disable a module
```

**Note:** The old monolithic modules (`Mechanics`, `Protection`, `Listeners`) have been split into individual feature modules for better debugging and flexibility.

---

## 🔐 Authentication (Auth)

Mandatory player authentication system for server entry.

**Features:**
- Registration with password (Anvil GUI)
- Password login
- **Argon2id** password hashing (32MB memory, 2 iterations)
- IP address check on login (optional)
- Duplicate name check (optional)
- Account limit per IP (default: 3)
- Session duration (default: 60 min)
- Login timeout (default: 60 sec)
- Max wrong attempts (default: 5)
- Request cooldown (default: 5 sec)
- Password change via GUI
- Account logout
- Force login by admin
- Session reset / registration deletion

**Admin commands:**
```
/mp auth forcelogin <nick>   — force authorize a player
/mp auth resetauth <nick>    — delete registration (kicks player)
/mp auth chgpass <nick> <pass> — change player's password
/mp auth delsession <nick>   — reset session (kicks player)
/mp auth logout              — log out of your account
```

**Configuration (config.yml → auth.*):**
- `auth.enabled` — enable/disable the system
- `auth.min_password_length` / `auth.max_password_length`
- `auth.session_duration_minutes`
- `auth.login_timeout_seconds`
- `auth.max_wrong_attempts`
- `auth.max_accounts_per_ip`
- `auth.check_ip.enabled`
- `auth.check_duplicate_name.enabled`

---

## ⚛ Dark Fusion Reactor (R.T.S)

A multi-block structure for energy generation and Ancient Debris synthesis.

**Assembly:** `/mp str dfc assemble` — multi-block using Iron Blocks, Lightning Rods, Copper Blocks, Redstone Blocks and an Item Frame.

**Simulation parameters (configurable in config.yml → reactor.*):**
- Core and Case temperature
- Core and Case pressure
- Shell and Case integrity (0-100%)
- Wear system — reactor degrades over time
- Recipe progress (0-100) — Ancient Debris crafting

**Operation modes:**
- **Heating** — Redstone signal to lamp (-1, 0, -2) → increases temperature
- **Cooling** — Redstone signal to lamp (1, 0, -2) → decreases temperature
- **Fuel:** left barrel — diamond blocks, right barrel — gold blocks

**Results:**
- At 100% recipe progress → drops Ancient Debris in reactor center
- Energy generation into cable network (core temp × 0.9 E/tick)

**Reactor states:**
- ✅ Normal
- ⚠ Degradation (integrity < 100%)
- 💀 Self-destruct (wear = 100%)
- 💥 Meltdown — when integrity fails or self-destruct ends

**Radiation from reactor:**
- Inside the reactor chamber (when core temp ≥ 1000)
- High pressure releases radiation into surroundings (CAMPFIRE_SIGNAL_SMOKE particles)

**Statistics:** `/mp str dfc stats` — temperature, pressure, integrity, progress, wear, energy output

---

## 🔋 Energy System (Cable Network)

Cable network for transferring energy between blocks.

**Block types (Waxed Copper):**
- `WAXED_LIGHTNING_ROD` — straight cable (connects along axis)
- `WAXED_CHISELED_COPPER` — corner cable (turn)
- `WAXED_COPPER_GRATE` — battery (stores energy, does not connect to other batteries)

**Operations:**
- Add/remove energy from network node
- Transfer between connected nodes (with axis validation)
- Persistence to SQLite
- Visual rendering of connections (CableVisualTask)

**Node types:**
- `CABLE` — regular cable
- `BATTERY` — energy storage (battery→battery transfer blocked)
- `GENERATOR` — generator (created by reactor)

**Background tasks:**
- `CableLossTask` — energy loss in the network (every 20 sec)
- `CableTickTask` — cable tick (every 5 sec)
- `BatteryDrainTask` — battery discharge
- `GeneratorTask` — generator operation
- `EnergyBalancerTask` — energy balancing between nodes

---

## ☢ Radiation

Player radiation accumulation system with effects and dosimeter.

**Radiation sources:**
- **Ancient Debris** in inventory
- **Basalt Deltas** biome
- **The End** under open sky
- **Weapon use:** Mace, Trident, Elytra
- **Reactor:** inside chamber, during pressure release, at meltdown

**Radiation levels (R/h):**
| Level | Value | Effects |
|-------|-------|---------|
| Safe | 0-199 | None |
| Mild | 200-399 | Hunger I, Slow I |
| Moderate | 400-799 | Hunger II, Slow II, Nausea, Weakness |
| High | 800-1599 | Hunger III, Slow III, Nausea, Weakness II |
| Critical | 1600-3199 | Hunger V, Slow V, Nausea II, Weakness III, Blindness |
| Deadly | 3200-6399 | Hunger VII, Slow VII, Nausea II, Weakness IV, Blindness, Fatigue II |
| ☠ Lethal | 6400+ | All effects max level |

**Protection items:**
- **Lead Shield** — reduces radiation when held in hand
- **Antirad** — removes 100 radiation units
- **Dosimeter** — shows current radiation level in ActionBar (R/h)

**Radiation decay:** natural decrease over time (configurable).

**Commands:**
```
/mp checkrad [nick]  — check radiation level
/mp setrad <nick> <value> — set player radiation
```

---

## 🧲 Magnet

A multi-block structure that attracts metallic items.

**Assembly:** `/mp str magnet assemble` — multi-block using Iron Blocks, Lightning Rods, Iron Bars and an Item Frame.

**Features:**
- Attracts: metallic items (weapons, armor, tools), mobs in metallic armor, minecarts
- Radius scales with power (number of blocks in structure)
- Auto-collects items to structure center
- Cluster-based attraction system
- Attraction force depends on magnet power

**Stat check:** `/mp str magnet stats` — blocks, strength, radius, center, distance

---

## ⚡ Lightning Structure

A multi-block structure that generates controlled lightning strikes.

**Commands:**
```
/mp str lightning enable   — enable the lightning structure
/mp str lightning disable  — disable it
/mp str lightning stats    — view statistics
```

**Features:**
- Lightning strikes the center of the structure
- Can be toggled on/off
- Configurable via config.yml

---

## 🔢 Code Panel

Interactive chat-based code entry panel with clickable buttons.

**Commands:**
```
/mp codepane — open the code entry panel
```

**Key management:**
```
/mp codepane key add <name> <code> [flags]
/mp codepane key list
/mp codepane key remove <name>
/mp codepane key modify <name> <new_code> [flags]
```

**Key flags:**
- `attempts:<N>` — delete key after N successful uses
- `time:<N>s|m|h|d` — delete key after N seconds/minutes/hours/days
- `whitelist:<name1,name2...>` — only allow these players
- `blacklist:<name1,name2...>` — block these players
- `command:(<cmd>),(<cmd2>)` — execute commands on successful entry (%entity% = player nick)

**Storage:** SQLite (`code_panel_keys` table)

**Cleanup:** Expired keys and keys with exceeded attempt limits are auto-removed

---

## 🔨 Custom Crafting

Custom recipes for special items.

| Item | Description |
|------|-------------|
| **Multimeter** | Inspect block/energy information |
| **Plasma Cannon** | Long-range weapon |
| **Shoker** | Close-combat weapon |
| **Antimatter** | Special item |
| **Health Meter** | Mob health display |
| **Entity Locator** | Find nearby entities |
| **Dosimeter** | Shows radiation in ActionBar |
| **Lead Shield** | Radiation protection |

**Extra:** The datapack adds custom vanilla recipes (books, chains, echo shards, totems, spawners, netherite, etc.).

---

## 🔧 Integrity System

Every item has integrity (0-100%) that decreases with use.

**Mechanics:**
- Items are created with 100% integrity
- At 0% the item is destroyed
- **Anvil repair** — restores integrity
- **Item combining** — sums remaining integrity
- **Mending (XP repair)** — restores integrity
- **Silk Touch** — integrity restoration on harvest
- **XP collection** — restores integrity to all items

**Commands:**
```
/mp item int list               — check item integrity info
/mp item int set <value>        — set item integrity
/mp item int add <value>        — add to item integrity
```

---

## 🚂 Minecart Speed

Minecart acceleration system with exponential speed-up.

**Features:**
- Exponential acceleration on active POWERED_RAIL (×N per tick)
- Exponential deceleration off-rails
- Collision damage = speed × 20 (blocks/sec ↔ damage)
- Speed display in actionbar via `/mp togglespeed`
- Particles during movement

**Commands:**
```
/mp togglespeed — toggle speed display in ActionBar
```

---

## 🏠 Home Points (Homes)

Save and teleport to home points.

**Commands:**
```
/mp sethome <name>             — save a home point
/mp home <name>                — view home coordinates
/mp delhome <name>             — delete a home
/mp listhomes                  — list all homes
/mp ophomels <player>          — list a player's homes (operator)
/mp opdelhome <player> <name>  — delete a player's home (operator)
```

**Storage:** SQLite (`player_homes` table)
**Limit:** configurable in config.yml (`home.max_homes`, default: 10)
**Name length:** 1-16 characters (configurable)

---

## 🌍 Dimension Change

Teleportation between configured worlds via GUI.

**Commands:**
```
/mp chgdim — open world selection menu
```

**GUI interface:**
- Anvil GUI for entering world name
- Return teleports to saved location
- Cooldown between teleports (configurable)

**Subcommands:**
```
/mp chgdim_teleport <world> — teleport to a world
/mp chgdim_return           — return to original location
```

---

## 🔌 Power Management

Server shutdown/restart system with countdown.

**Commands:**
```
/mp power off           — request server shutdown
/mp power reboot        — request server restart
/mp power confirm       — confirm the request
/mp power undo          — cancel the request
```

**Features:**
- Request confirmation via `/mp power confirm`
- **BossBar** — depleting bar with remaining time
- **ActionBar** — seconds display
- **Sound** — beeping that accelerates towards the end
- Request timeout (default: 30 sec)
- Countdown duration (default: 10 sec)
- Intercepts `/stop` and `/restart` — redirects to `/mp power`

---

## ☠ Suicide

Self-kill mechanic with confirmation and countdown.

**Command:** `/mp suicide`

**Mechanics:**
1. First input — warning message
2. Second input (confirmation) — starts countdown
3. Cancellation impossible after confirmation
4. BossBar + ActionBar + sound effects
5. After countdown — player dies

---

## 👻 Vanish

Full player hiding system.

**Command:** `/mp vanish <nick>`

**Features:**
- Player is completely invisible to others
- Hidden from `/list` (custom `/list` command)
- Applies to offline players (activates on next login)
- Status stored in database

---

## 📝 Notes

Personal note system per player via GUI.

**Command:** `/mp notes`

**Interface:**
- Anvil GUI for creating/editing notes
- Each player can only see their own notes

**Storage:** SQLite (`player_notes` table)

---

## 🔄 Updater

Automatic update checking and installation from GitHub.

**Commands:**
```
/mp checkver (or /mp checkupdates) — check for updates
/mp updatejar                       — download and install
```

**Mechanics:**
- SHA commit comparison (GitHub API `/commits`)
- Release gate: new commits without new release → no update shown
- JAR download from GitHub Releases
- JAR replacement with backup
- Fallback: if replacement fails — saves `.jar.update` in plugins folder
- Status stored in SQLite (`updater_state` table)

---

## 🗣 Chat Filter

Profanity filter with wildcard and regex support.

**Features:**
- Wildcard patterns: `word`, `word*`, `*word`, `*word*`
- Full Java regex support
- Unicode-aware (correct Cyrillic handling via `\p{L}`)
- Highlighted bad words in logs (red color)
- Bypass permission: `mcplugin.chat.filter.bypass`

**Pattern sources (config.yml):**
- `chat_filter.words` — simple words with wildcard
- `chat_filter.regex_patterns` — complex Java regex

---

## 🛡 Server Protection

### RedstoneGuard
- Protection against laggy redstone contraptions
- Limits redstone updates per second
- Automatically disables problematic mechanisms
- Console logging

### PacketGuard
- Protection against crash packets (oversized packets)
- Kicks player when packet size limit is exceeded
- Bypass permission: `mcplugin.packetguard.bypass`

### Void Protection
- Saves players from falling into the void
- Teleports to safe position
- Message on save

### Emergency Entities Kill
- Emergency removal of excessive entities
- Triggers when entity limit is exceeded
- Configurable in config.yml

### Server Overload Warning
- Chat warning when server is under high load
- Configurable thresholds
- Console logging

---

## 🎯 Other Mechanics

### Attributes Manager
- Custom item attribute management
- Auto-applies attributes to custom items

### Beacon Manager
- Enhanced beacon effects
- Extended range

### Block Damage Manager
- Configurable block hardness
- Modified breaking time

### Boosted Cobweb
- Enhanced cobweb
- Increased slowdown

### Container Trigger
- Triggers on container opening (chests, shulkers, etc.)
- Command execution on open

### Dragon Egg
- Custom dragon egg behavior
- Resets on dragon respawn

### Ender Chest
- Extended ender chest interaction
- Additional slots

### Entity Locator
- Find nearest entities
- Display in chat

### Glass Break
- Realistic glass breaking
- Sound/visual effects

### Health Meter
- Mob health display
- Percentage/health bar

### Item Kill Manager
- Item removal under certain conditions
- Configurable filters

### Leash
- Leash any entity (mob→mob, mob→fence)
- Disable leash break
- **Usage:** interact with mob while holding leash

### Minecart Speed
- Minecart acceleration
- Collision damage
- Speed display

### Mode Protect
- Prevents game mode changes in specific worlds
- Bypass permission: `mcplugin.gmprotect.bypass`

### Saved Hotbar (Creative Item Validator)
- Validates items from saved hotbars
- Checks for oversized items (NBT)
- Bypass permission: `mcplugin.creative.bypass`

### Shield Slowness
- Slowdown when using shields
- Configurable multiplier

### Terracota Speed
- Speed boost on terracotta
- Configurable speed

### Unbreakable Breaker
- Break "unbreakable" blocks (bedrock, barrier)
- Special breaking conditions

### Waypoint Manager
- Waypoint/teleport point system
- Position saving

---

## ⌨ Commands (/mp)

Full command list:

### General
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp help` | Command list | mcplugin |
| `/mp reload` | Reload plugin | mcplugin.command.reload |

### Update Check
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp checkver` / `/mp checkupdates` | Check for updates | mcplugin.command.checkver |
| `/mp updatejar` | Install update | mcplugin.command.checkver |

### Authentication (admin)
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp auth forcelogin <nick>` | Force authorize | mcplugin.command.auth.forcelogin |
| `/mp auth resetauth <nick>` | Delete registration | mcplugin.command.auth.resetauth |
| `/mp auth chgpass <nick> <pass>` | Change password | mcplugin.command.auth.chgpass |
| `/mp auth delsession <nick>` | Reset session | mcplugin.command.auth.delsession |
| `/mp auth logout` | Log out | — |

### Dimensions
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp chgdim` | Open world menu | mcplugin.command.chgdim |
| `/mp chgdim_teleport <world>` | Teleport to world | mcplugin.command.chgdim |
| `/mp chgdim_return` | Return back | mcplugin.command.chgdim |

### Structures
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp structures dfc stats` | Reactor stats | mcplugin.command.structures |
| `/mp structures dfc assemble` | Assemble reactor | mcplugin.command.structures.dfc |
| `/mp structures magnet assemble` | Assemble magnet | mcplugin.command.structures.magnet |
| `/mp structures magnet stats` | Magnet stats | mcplugin.command.structures.magnet |
| `/mp structures lightning enable` | Enable lightning | mcplugin.command.structures |
| `/mp structures lightning disable` | Disable lightning | mcplugin.command.structures |
| `/mp structures lightning stats` | Lightning stats | mcplugin.command.structures |

### Code Panel
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp codepane` | Open panel | mcplugin.command.codepane |
| `/mp codepane key add <name> <code>` | Add key | mcplugin.command.codepane.key.add |
| `/mp codepane key list` | List keys | mcplugin.command.codepane.key.list |
| `/mp codepane key remove <name>` | Remove key | mcplugin.command.codepane.key.remove |
| `/mp codepane key modify <name> <new_code>` | Modify key | mcplugin.command.codepane.key.modify |

### Items
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp item int list` | Integrity info | mcplugin.command.item |
| `/mp item int set <value>` | Set integrity | mcplugin.command.item |
| `/mp item int add <value>` | Add integrity | mcplugin.command.item |

### Homes
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp sethome <name>` | Save home | mcplugin.command.sethome |
| `/mp home <name>` | View home | mcplugin.command.home |
| `/mp delhome <name>` | Delete home | mcplugin.command.delhome |
| `/mp listhomes` | List homes | mcplugin.command.listhomes |
| `/mp ophomels <player>` | Player's homes | mcplugin.command.ophomels |
| `/mp opdelhome <player> <name>` | Delete player's home | mcplugin.command.opdelhome |

### Power
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp power off` | Shutdown server | mcplugin.command.power.off |
| `/mp power reboot` | Restart server | mcplugin.command.power.reboot |
| `/mp power confirm` | Confirm request | mcplugin.command.power |
| `/mp power undo` | Cancel request | mcplugin.command.power.undo |

### Other
| Command | Description | Permission |
|---------|-------------|------------|
| `/mp suicide` | Suicide | mcplugin |
| `/mp vanish <nick>` | Vanish player | mcplugin.command.vanish |
| `/mp notes` | Notes GUI | mcplugin.command.notes |
| `/mp checkrad [nick]` | Check radiation | mcplugin.command.checkrad |
| `/mp setrad <nick> <value>` | Set radiation | mcplugin.command.setrad |
| `/mp togglespeed` | Speed display toggle | mcplugin |
| `/mp modules list` | List modules | * (op) |
| `/mp modules enable <module>` | Enable module | * (op) |
| `/mp modules disable <module>` | Disable module | * (op) |

### System (built-in command replacements)
| Command | Description |
|---------|-------------|
| `/stop` | Redirects to `/mp power off` |
| `/restart` | Redirects to `/mp power reboot` |
| `/list` | Custom player list (vanish-aware) |
| `/reactor` | Reactor structure assembly |

---

## 📄 Configuration Files

- `config.yml` — main plugin configuration
- `messages.yml` — all player-facing messages (customizable, MiniMessage format)
- `plugin.yml` — plugin metadata, commands, permissions

## 🗄 Database (SQLite)

Tables:
- `auth_users` — authentication users
- `auth_sessions` — authentication sessions
- `cables` — cable network nodes
- `cable_connections` — cable connections
- `code_panel_keys` — code panel keys
- `player_homes` — home points
- `player_notes` — player notes
- `player_radiation` — player radiation
- `updater_state` — update state
- `vanished_players` — vanish status
- `magnet_state` — magnet state
- `reactor_state` — reactor state
