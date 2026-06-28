# MC-Plugin v1.8 — Full Changelog

> From v1.8.00 to v1.8.85

---

## 🔧 Core / Infrastructure

- **v1.8.10** Fix `/mp reload` crash
- **v1.8.04** Fix `/mp reload` double-registration bug
- **v1.8.05** Fix `/mp reload` to work from console
- **v1.8.06** Fix `/mp reload` crash (FishingListener scheduler)
- **v1.8.33** Fix stale `TabManager` references and migrate Maintenance to SQLite
- **v1.8.56** Fix chat formatting (empty recipients fallback and listener leak on reload)
- **v1.8.73** Add configurable periodic access list check (`access_control.check_interval_ticks`)
- **v1.8.73** Optimized `OpWhitelistManager` — removed dead code
- **v1.8.74** ConfigRepairManager with `ConfigRules` (auto-add missing keys instead of replace)
- **v1.8.75** Fix `config.yml` bugs: removed duplicate `home:` section, fixed `chat_ping` comments
- **v1.8.76** Fix `ConfigRepairManager` empty list bug, update stale comments
- **v1.8.77** `ConnectionWrapper` with no-op `close()` — fixes "stmt pointer is closed" spam
- **v1.8.83** Fix `boostedcobweb` — teleport back + full velocity zero (PlayerMoveEvent)
- **v1.8.84** Fix `boostedcobweb` — тройная блокировка: cancel + teleport + zero velocity
- **v1.8.85** Fix vanish tab re-hide — ванишнутые больше не видны как прозрачные иконки

## 💬 Custom Chat / Ping System

- **v1.8.25** Add custom chat system (MiniMessage support)
- **v1.8.26** Chat mode config consolidation
- **v1.8.32** Fix chat intercept priority and bypass permission
- **v1.8.37** Fix literal `</white>` in chat
- **v1.8.46** Fix chat Component-level append
- **v1.8.56** Fix chat formatting (empty recipients fallback and listener leak)
- **v1.8.67** Fix chat text color preservation with `playerMiniMessage`
- **v1.8.74** Add chat ping system (@everyone, @nick, @non-op, @is-admin, @is-non-admin)
- **v1.8.74** Chat ping sound + notification, configurable style in `chat_ping.ping_style`
- **v1.8.80** ChatManager: skip processing for players in mod session (moderation messages hidden)
- **v1.8.80** ReportManager priority changed to HIGHEST — гарантированная обработка модерации

## 📋 Tab / Scoreboard / BossBar / BelowName

- **v1.8.12** Add Tab + Scoreboard system (header/footer/placeholders)
- **v1.8.13** Fix TabManager
- **v1.8.14** Add `player_list.format` template
- **v1.8.15** Add `hide_spectators` option for tab list
- **v1.8.18** Add BossBar + Tab sorting system (A-Z, Z-A, LuckPerms, OP)
- **v1.8.17** Add BelowName scoreboard objective
- **v1.8.38** Rewrite BelowNameManager
- **v1.8.39** Add separate update interval for tab list player names
- **v1.8.45** Fix spectators visible in tab via per-tick re-hide
- **v1.8.66** Fix belowname — remove [LF] symbol, use bullet separator
- **v1.8.85** Fix vanish tab re-hide — скрытие ванишнутых после сортировки

## 📊 Placeholders

- **v1.8.27** Add internal LuckPerms placeholders (`{prefix}`, `{suffix}`, `{group}`)
- **v1.8.42** Add `{player_ping_color}` placeholder (HEX gradient)
- **v1.8.49** Add `{player_ping_gradient}` placeholder
- **v1.8.47** Fix `{player_ping_color}` MiniMessage tag
- **v1.8.60** New placeholders: TPS/MS/RT/RAM/Online with time windows (min/avg/max) and gradients
- **v1.8.63** TPS/MSPT format update (always 2 decimal places)
- **v1.8.64** Add ping placeholders with smooth gradients
- **v1.8.74** Full documentation of all placeholders in plugin-guide.txt

## 🔐 Punishment / Reports / Moderation

- **v1.8.57** Add report system (`/mp report`, `/mp reports`, `/mp modreport`, `/mp repstatus`)
- **v1.8.65** Fix `ReportManager.init()` never being called (player visits not saving, reports broken)
- **v1.8.69** Punishment system: ban/mute/kick/warn with flags: `-time`, `-permanent`, `-ip`, `-hw`
- **v1.8.70** Add `/mp punish unwarn <player> <reason> <warnId>`
- **v1.8.71** Fix 2 bugs: parsePunishArgs startIndex fix, unpunish offline by player_name
- **v1.8.72** Block vanilla `/whitelist` command (player + console) — use `/mp whitelist`
- **v1.8.78** Tab completion for `/mp reports add|remove <id>`
- **v1.8.79** Tab completion for `/mp modreport <name>` — shows names from moderation queue
- **v1.8.80** Fix mute cache: mute online players actually blocks chat now
- **v1.8.80** Punishment notifications: `mcplugin.punish.notify` permission
- **v1.8.81** Notify for unban/unmute/unwarn via `mcplugin.punish.notify`
- **v1.8.82** Notify player on warn removal if online

## ⚡ Energy / Crafting / Reactor

- **v1.8.00** Add AutoCraft mode command
- **v1.8.18** Add AutoCraftManager
- **v1.8.44** Add Assembler system (auto-crafter)
- **v1.8.43** Remove redstone requirement from generator
- **v1.8.55** Light multi updates (WAXED_COPPER_BULB + energy buffer)
- **v1.8.62** Fix AssemblerModule (use runTaskTimer on BukkitRunnable)

## 🏗 Structures (Marker-based multiblocks)

- Full migration from SQLite to Marker entity-based system
- CRAFTER, Generator, Battery, Lightning, Magnet, Light structures migrated
- **v1.8.11** Fix structures lost after restart (delayed rebuild)
- **v1.8.19** Fix structures disappearing after restart (delayed rebuilds)
- **v1.8.20** Fix MetalDetector crash and StructureChunkTracker persistence
- **v1.8.21** Fix ChgDimGUI inventory revert bug

## 🛡 Netherite / Integrity

- **v1.8.28** Fix flight lore italics
- **v1.8.29** Fix Netherite upgrade (flat +0.1 per scrap)
- **v1.8.34** Fix Netherite scrap upgrade (preserve weapon damage)
- **v1.8.35** Remove extra lore line from Netherite upgrade
- **v1.8.36** Remove armor from Netherite scrap upgrade
- **v1.8.40** Use `BLOCK_BREAK_SPEED` for tools
- **v1.8.41** 1 Netherite ingot = 9 scrap upgrades
- **v1.8.52** Simplify netherite upgrade lore
- **v1.8.53** Remove unused code from NetheriteUpgradeListener
- **v1.8.54** Fix missing textures (removed `setItemModel()` calls)
- **v1.8.58** Armor integrity scales with damage formula
- **v1.8.59** Integrity cost multiplier scales with damage
- **v1.8.68** Netherite upgrade rework: scrap only, armor support, total attribute display

## 🛡 Security / Anti-Grief

- **v1.8.22** Add dynamic flight gradient + fix NetheriteUpgradeListener crashes
- **v1.8.23** Fix EnderChest explosion protection
- **v1.8.24** Add OP whitelist system
- **v1.8.30** Migrate OpWhitelist to SQLite
- **v1.8.31** Migrate structure chunk tracker to SQLite
- **v1.8.48** Fix bot protection rejoin cooldown persistence
- **v1.8.50** Fix brand spoof (added delay and handler)
- **v1.8.73** AccessListCheckTask — периодическая проверка whitelist/blacklist/opwhitelist

## 🛠 Maintenance / Misc

- **v1.8.16** Add maintenance mode
- **v1.8.51** Maintenance now controlled by `/mp maint on|off`
- **v1.8.18** Add `brand_spoof` config
- **v1.8.01-03** MOTD feature with custom player list and online counter modes
- **v1.8.07-08** Refactor online counter config, add scale mode
- **v1.8.09** Fix Concrete Bucket mechanics
- **v1.8.61** Fix plugin.yml YAML syntax
- **v1.8.74** ChatPingManager — новая система пингов в чате
- **v1.8.74** plugin-guide.txt — полная документация всех плейсхолдеров

---

## 🔑 New Permissions

| Permission | Description | Added in |
|---|---|---|
| `mcplugin.command.reports` | Управление репортами и модерацией | v1.8.57 |
| `mcplugin.punish.notify` | Получать уведомления о наказаниях | v1.8.80 |
| `mcplugin.chat.filter.bypass` | Байпас фильтра чата | v1.8.25 |
| `mcplugin.chat.custom.bypass` | Байпас кастомного чата | v1.8.32 |
| `mcplugin.gmprotect.bypass` | Байпас защиты режима | — |
| `mcplugin.show.brand` | Видеть реальный brand | v1.8.18 |

---

## 📝 plugin-guide.txt Updates

- Полная документация всех секций config.yml
- Все команды `/mp` с описанием
- Все права доступа (permissions)
- Все плейсхолдеры (встроенные, динамические, PAPI)
- Chat ping (@mentions)
- Репорты и модерация (`/mp reports`, `/mp modreport`)
- Обновлено: punishment notify permissions
