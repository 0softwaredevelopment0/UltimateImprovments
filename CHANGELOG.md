# Changelog

All notable changes to the UltimateImprovments plugin family are documented
in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.8.3-beta.3] — since 1.8.3-beta.2 (2026-09-27)

### Added
- **`lava_walker` charm (Lava Walker)** — Frost Walker for LAVA, levels 1-255,
  boots only. Lava under the wearer's feet temporarily turns into obsidian:
  radius = level (1 → 1×1, 2 → 3×3, ... hard-capped at 16 → 31×31); created
  blocks melt back to lava after a random 20–45 s (frosted-ice style) and the
  block the player stands on never melts first. Both crust layers (below the
  feet and at the feet — for wading) are converted. Offered in the third
  enchanting-table slot (same min-cost gate as the other charms), available in
  villager trades and mob equipment, NOT in random loot. Sneaking disables the
  conversion; no fire protection is granted — the first step into fresh lava
  can still ignite you. The melt registry persists across restarts in the
  shared family SQLite database (`lava_walker_melts` table: position + exact
  original lava data + wall-clock deadline; autosave every 5 min + save on
  shutdown), so a melting plate comes back to lava even after a restart — and
  the melt restores the captured lava state verbatim (a source stays a source,
  a flow keeps its level) with a forced physics update, so the restored lava
  starts flowing immediately. Every created block costs the boots 1 integrity
  use (a max-radius sweep can cost 961 uses).
- **`armor_trim_effects` feature** — configurable potion effects based on the
  armor TRIM MATERIAL (the smithing-table ingot/crystal, i.e. the trim color),
  not the trim pattern. Units under `armor_trim_effects` name one or more trim
  materials (amethyst/copper/diamond/emerald/gold/iron/lapis/netherite/quartz/
  redstone/resin — registry-resolved, datapack materials work), a count rule
  (EXACT: effect level = number of matching pieces; MIN: activates at `count`
  pieces with a fixed level), the effect, amplifier base, duration/interval
  and particles/ambient/icon flags (all default off). The default set covers
  ALL 11 trim materials with themed effects (netherite → fire_resistance,
  iron → resistance, diamond → absorption, gold → haste, emerald →
  hero_of_the_village, amethyst → regeneration, copper → water_breathing,
  lapis → night_vision, quartz → speed, redstone → strength, resin →
  slow_falling) under the MIN rule with count 1: the level never grows with
  more pieces. Multiple units work in parallel; effects expire naturally when
  pieces are removed.
- **Armor charms on the elytra** — Flight, Levitation, Igniting and the Curse
  of Vulnerability accept the elytra in addition to their armor pieces: the
  enchanting table / anvil / `/ui enchant` take it (datapack item tags), the
  PDC failsafe syncs the elytra (chest) slot, and Igniting / Vulnerability
  effects read the elytra there. Flight / Levitation already read the chest
  slot, so they work on a worn elytra as-is.

### Changed
- **Flight is incompatible with Repairing on the chest slot** — if the worn
  chestplate (or chest-slot elytra) carries the Repairing charm, the flight is
  revoked and not granted while it stays on: that is the piece paying the
  flight engine's own drain. Repairing on other armor pieces is fine — the
  chestplate itself still visibly wears down.
- **Attack AoE has no damage falloff and caps at radius 10** — every cleaved
  target takes the same force as the original hit; the radius equals the
  charm level capped at 10 (the level where the old falloff formula would
  have reached zero anyway).
- **Container Stealing: levels 1-10 and a steal roll** — level N = N×10%
  chance the charm works (level 10 = always). On a failed roll the break is
  fully vanilla: the container drops empty and its contents spill out —
  nothing is ever destroyed. Shulker boxes are excluded (vanilla already
  keeps their contents; stealing them would double-preserve the loot).
- **Lava Walker costs boots durability** — every created obsidian block costs
  the boots 1 integrity use (a max-radius 31×31 sweep can cost 961 uses).
- **Flight now strains the whole armor set** — while actively flying, every
  worn armor piece loses 1 use of integrity per second (previously only the
  charming chestplate paid, the rest of the set flew for free).
- **Flight now costs integrity while gliding too** — planning with a
  charmed elytra drains 1 chestplate use per second, same rate as flying:
  the glide is the same engine and no longer travels for free.
- **Levitation now has an integrity cost** — actively boosting (jump key
  held) drains 1 use per second from the charming chestplate; releasing the
  key is free. Cheaper than Flight, fitting its lower value. Previously
  the jetpack was entirely free.
- **`/ui enchant` gains `lava_walker`** in the custom-enchantments config list.

### Fixed
- **Lava Walker could suffocate the player it helped** — the conversion swept
  the player's own feet and head layers, placing solid obsidian INSIDE the
  player (vanilla suffocation damage + trapped until the melt). The feet and
  head blocks of the converting player are now skipped; only the block they
  stand ON is converted.
- **Lava Walker melt could trap or lag behind the player** — the "don't melt
  under a player" check used a stale cache of the last conversion center: a
  player could walk off and the block stayed un-melted forever, or a plate
  melted under a player who walked onto it from the side. The sweep now does
  a live bounding-box occupancy check of all players in the world.
- **Lava Walker doubled its own conversion** — the move listener was
  registered twice in the module init (manually and inside register()),
  running the conversion pass twice per event. Removed the manual one.
- **Container Stealing bypassed protection plugins** — the listener ran at
  NORMAL priority and wiped the container contents immediately: a protection
  plugin (WorldGuard, etc.) running later could cancel the break, leaving a
  LOOTED container in place. The listener now runs at MONITOR (after all
  protection checks) and performs the snapshot/wipe/drop on the next tick,
  when the break decision is final — a cancelled break keeps the container
  fully intact.
- **`ConcurrentModificationException` in the unbreakable breaker sweep** —
  the per-tick maintenance task called `cleanup()` (which mutates the map)
  while iterating `activeBreaks` with its iterator open; the first session
  reset inside the sweep (player looked away / died / went offline) threw a
  CME and spammed the console. The sweep now removes entries via
  `iterator.remove()` and cleans the reverse map directly.
- **Container Stealing emptied the container it stole** — breaking a container
  with the charm dropped a plain empty container item while the contents were
  stored in a plugin-only PDC blob that was never read back (breaking it again
  spilled nothing, placing the stolen container gave an empty chest: the items
  vanished). The dropped container now carries its contents in vanilla
  block-state NBT (`BlockStateMeta` snapshot of the broken block — same format
  as ctrl+pick-block), so the item tooltip preview shows the stored items and
  placing the container restores them natively, surviving restarts and
  datapack outages. Containers stolen before the fix still restore their
  contents from the legacy PDC blob when placed.

### Changed
- **Config system: the monolithic `config.yml` resource is gone** — every
  addon and the core now ship their own default TOML template
  (`config/UI-<Addon>.toml`, 12 files bundled in UI-Core's JAR), which are the
  single source of defaults: they bootstrap `configs/UI-<Addon>.toml` on first
  run and auto-repair missing keys on upgrade. No runtime config format or
  server-side file changes — this is an internal cleanup of the defaults
  pipeline. Dead monolith-era classes were removed
  (`ConfigRepairManager`, `ConfigCrashSalvage`, `YamlDuplicateCleaner`
  callers) and `TomlConfigManager` is now a pure serialization utility.
- **Turret settings are configurable** — new `turret` section (UI-Combat):
  `range` (1–64, default 16) and `damage_per_tick` (0–100, default 1.0);
  defaults match the previous hard-coded constants.
- **`maintenance` keys routed to UI-Punish** — the `maintenance.enabled` and
  `messages.maintenance.*` defaults now live in `UI-Punish.toml` where the
  generator already placed them (previously the runtime routing table sent
  them to UI-Core, so fresh installs missed the keys).

### Fixed
- **Armor/trim effects were granted once and then disappeared** — the
  per-unit refresh counter counted 1-second heartbeat calls as ticks, so
  `interval_ticks: 100` meant a re-apply every 100 SECONDS instead of every
  5: an effect lasted its 120-tick duration and was then missing for ~94
  seconds ("one-shot" feel). Each unit now has TWO independent knobs, both
  defaulting to 40 ticks: `duration_ticks` (how long one application lasts)
  and `interval_ticks` (how often the check re-applies, min 20; the
  heartbeat converts it to whole seconds so the effect is never checked
  less often than configured). With `duration_ticks >= interval_ticks` the
  refresh lands before the previous application expires and the effect
  stays up continuously; a shorter duration deliberately turns the effect
  off between checks (warned in the log). The check-period key is named
  `check_interval_ticks` (the old `interval_ticks` still works as a legacy
  fallback when the new key is absent) and has no hard minimum — values
  below 20 ticks are allowed but pointless (the check heartbeat runs once
  per second), the config comments warn against them.
- **`auth.2fa.github.*` keys never worked** — the `2fa` TOML key segment is
  not a bare key, and toml4j keeps quoted header segments with their quotes,
  so the `[auth."2fa".github]` template section never matched the runtime
  path `auth.2fa.*` (the feature silently stayed disabled with empty
  defaults). The key is renamed to `auth.twofa.github.*` (templates,
  validation rules, code reads); the feature itself was off by default, so
  no behavior changes for existing setups.

## [1.8.3-beta.2] — since 1.8.3-alpha.4 (2026-09-27)

### Added
- **`armor_effects` feature** — configurable potion effects for wearing armor
  sets (leather/copper/chainmail/iron/golden/diamond/netherite families):
  rule FULL or COUNT (1–3 pieces), per-rule effect/amplifier/duration/interval,
  particles and ambient/icon toggles. Configured under the `armor_effects`
  section (UI-Other).

### Fixed
- **Offline invsee/endersee failed with "No data file found"** — resolving an
  offline player's UUID now falls back to `usercache.json` (both key orders,
  server root and world container) and a `.dat` scan by `bukkit.lastKnownName`,
  with a diagnostic warning listing what was searched when the player still
  cannot be resolved.
- **Container Stealing emptied the container it stole** — breaking a container
  with the charm dropped a plain empty container item while the contents were
  stored in a plugin-only PDC blob that was never read back (breaking it again
  spilled nothing, placing the stolen container gave an empty chest: the items
  vanished). The dropped container now carries its contents in vanilla
  block-state NBT (`BlockStateMeta` snapshot of the broken block — same format
  as ctrl+pick-block), so the item tooltip preview shows the stored items and
  placing the container restores them natively, surviving restarts and
  datapack outages. Containers stolen before the fix still restore their
  contents from the legacy PDC blob when placed.
- **`ConcurrentModificationException` in the unbreakable breaker sweep** —
  the per-tick maintenance task called `cleanup()` (which mutates the map)
  while iterating `activeBreaks` with its iterator open; the first session
  reset inside the sweep (player looked away / died / went offline) threw a
  CME and spammed the console. The sweep now removes entries via
  `iterator.remove()` and cleans the reverse map directly.

### Changed
- **Armor charms can now be applied to the elytra** — Flight, Levitation,
  Igniting and the Curse of Vulnerability accept the elytra in addition to
  their armor pieces: the enchanting table / anvil / `/ui enchant` take it
  (datapack item tags), the PDC failsafe syncs the elytra (chest) slot, and
  Igniting / Vulnerability effects now read the elytra there — it is not part
  of `getArmorContents()`. Flight / Levitation already read the chest slot, so
  they work on a worn elytra as-is.
- **Unbreakable breaker sessions reset on death, gamemode change, quit and
  shutdown** — accumulated damage no longer survives dying, switching
  gamemode (any source: client command, `/gamemode`, plugin API) or a
  plugin disable / `/ui reload` / server stop. The new
  `UnbreakableBreakerManager.shutdown()` cancels the tick task,
  unregisters listeners and clears all in-memory sessions (sessions were
  never persisted — this just makes it explicit).

## [1.8.3-alpha.4] — since 1.8.3-alpha.3 (2026-09-27)

### Fixed
- **Repairing never repaired armor** (including the elytra) — the sweep and the
  PDC-failsafe sync read armor via `getArmorContents()`, which returns detached
  copies, so repairs and PDC writes were silently lost. Armor is now read by
  absolute slot index 36–39 through `inv.getItem()` (live mirror).
- **AutoSmelt smelted only one block of an area** — combined with AoE /
  VeinMiner / TreeCapitator, extra blocks are broken via `breakNaturally()`
  (which fires no BlockBreakEvent), so the origin-only listener never saw their
  drops. A new MONITOR `BlockDropItemEvent` handler re-smelts the freshly
  spawned item entities from area breaks.
- **Account Standing showed a doubled icon** (e.g. "✔ ✔ All good") — the status
  message already contained the icon and `fullMini()` prepended it again. The
  message schema is now `<key>.icon` (single icon) + `<key>.name` (text only).
- **`/ui god` was never registered** — the registration was lost in the
  UI-Essentials module split, so the command failed with "Unknown command".
  It is re-registered, and `god off` now also persists the cleared
  `Invulnerable` flag to player.dat (NMS write + `saveData()`), so a stale
  god flag can no longer leave a permanently immortal player.

### Changed
- **Repairing now repairs REAL durability points** — the repair restores exactly
  `level` vanilla durability points every `level` seconds (lvl 1 → 1 point/s).
  Previously it repaired `level × 0.1%` of the item's max durability, which was
  truncated to 0 points on items with a large max (elytra: `floor(432 × 0.001) = 0`)
  — the meta was rewritten every second, but no damage was ever removed.
- **`/ui reload` accepts a target**: `/ui reload` / `/ui reload all` keep the
  proven full-family cycle; `/ui reload <addon>` disables and re-enables a
  single UI-* plugin (e.g. `/ui reload other`); `/ui reload core` restarts
  only the core subsystems without touching addons. Tab-complete and a shared
  "reload in progress" guard included.
- **Unbreakable breaker hand breaking** — with `min_tool_tier: "-"` the bare
  hand now works too (at the lowest `damage.default` rate); a concrete tier
  stays a strict minimum for pickaxes (weaker tiers and the hand are rejected).
- **Removed the `ItemIntegrityAPI` compatibility facade** — all remaining
  callers (enchants + sunburn) use `ItemDurabilityUtil` directly; the old
  custom integrity system is now fully gone at the code level.

### Added
- **Unbreakable breaker permissions**:
  - `ui.breaker.use` — damage/break ANY configured unbreakable block;
  - `ui.breaker.use.<material>` (e.g. `ui.breaker.use.bedrock`) — damage/break
    ONLY that block (per-block whitelist for groups);
  - `ui.breaker.bypasstier` — ignore the `min_tool_tier` gate (any tool
    including the hand deals full configured damage).
- **`/ui clear <chat|attributes>`** command:
  - `/ui clear chat <player|all>` — port of the removed `/ui clearchat`
    (permissions `ui.command.clear.chat`);
  - `/ui clear attributes <player|UUID|all>` — resets every attribute of a
    player or any living entity (by UUID) to its default base value and strips
    runtime modifiers (permission `ui.command.clear.attributes`).

## [1.8.3-alpha.3] — since 1.8.3 (2026-09-26)

> Re-release of the alpha line with the reload/anticheat stability fixes below.

## [1.8.3-alpha.2] — since 1.8.3 (2026-09-26)

> New version format: `version.subversion.patch-<channel>.<number>` with channels
> Nightly → Snapshot → Alpha → Beta → Release (from bleeding edge to stable).
> Numbering continues the pre-release line: an earlier build was published as
> `1.8.3-beta.1` before the channel format was introduced; this is the second
> pre-release on the new format.

### Added
- **Hazmat suit** — new radiation protection armor, replacement for the removed Lead Shield:
  - 4 leather-based pieces (helmet, chestplate, leggings, boots) with a
    yellow-green protective color, tagged with per-piece PDC keys
    (`ui:isHazmatHelmet`, `ui:isHazmatChestplate`, `ui:isHazmatLeggings`, `ui:isHazmatBoots`).
  - Each worn piece reduces **incoming** radiation by −20% (radiation is
    weakened, never removed); the full set gives −80%.
  - Armor is re-scanned every 2 seconds (`hazmat.scan_interval_ticks = 40`),
    so equipping/unequipping picks up without delay.
  - Recipes use the exact vanilla gold-armor shapes, with ONE gold slot
    replaced by the custom Lead Ingot (helmet `GPG / G G`, chestplate
    `G G / GPG / GGG`, leggings `GPG / G G / G G`, boots `G G / P G`).
  - Craftable **only in the vanilla Crafter** — in a regular workbench the
    result slot is empty; the recipes still show in the recipe book.
  - The anti-uncraft protection of the Lead Ingot whitelists all four hazmat
    recipes (and the Crafter auto-craft path), so lead ingots can be legally
    consumed but still cannot be melted back.
- New config section `hazmat` (bundled in UI-Shared):
  `enabled` (default `true`), `scan_interval_ticks` (default `40`),
  `protection_per_piece` (default `0.2`), `full_suit_bonus` (default `0.0`).
  Reloadable via `/ui reload`.
- Hazmat pieces are available in the omniscanner admin menu (items tab).

### Changed
- Lead Ingot lore updated: it is now used for the Dosimeter **and** the
  Hazmat suit (previously advertised the Lead Shield).
- Lead Ingot factory (`createLeadIngotStack`) moved from the deleted shield
  listener into `LeadIngotCraftListener` (shared by the dosimeter and hazmat
  recipes).

### Removed
- **Lead Shield** (item, recipe, `ui:isLeadShield` PDC key, admin-menu entry).
  Existing shield items in players' inventories remain but lose their
  radiation protection — replace them with the hazmat suit.
- Config keys `radiation.lead_shield_reduction` and the never-applied
  `radiation.antirad_reduction` (removed from `config.yml`, validation rules
  and the bundled UI-Shared.toml template). Leftover keys in existing on-disk
  `configs/UI-Shared.toml` are harmless.

### Internal / tooling
- `Scripts/build/package_jars.sh` — builds all addon JARs, gzips them and
  packs a distribution tar (added right before 1.8.3 was tagged).

## [1.8.3] (Release) — 2026-09-26

### Changed
- Family version unified to **1.8.3** across all 12 addons (Gradle
  `version`, `plugin.yml` via `${project.version}`, `plugin_version` in the
  config); old servers keep their on-disk `plugin_version` values.

### Fixed
- Reputation dialog keys and the fully commented English TOML config
  templates shipped together with the version bump (see 1.8.2 → 1.8.3 work:
  template-copy config generation, template-based repair, comment-preserving
  saves, YAML fragments replaced by 11 TOML templates).

## [1.8.2] — earlier

- Reputation view dialog (`/ui rep [player]`): native Paper dialog with the
  rep value, Account Standing status and a Close button.
- Config system overhaul: canonical `config.yml` + generated fully commented
  English TOML templates (`Scripts/other/generate_toml_templates.py`),
  template-copy generation on first run, template-based repair of missing
  keys, comment-preserving saves.
