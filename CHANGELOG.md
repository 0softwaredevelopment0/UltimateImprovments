# Changelog

All notable changes to the UltimateImprovments plugin family are documented
in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.8.3-beta.2] — since 1.8.3-alpha.4 (2026-09-27)

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
  can still ignite you. The melt registry persists across restarts
  (`lava_walker_melts.yml`: position + exact original lava data + wall-clock
  deadline; autosave every 5 min + save on shutdown), so a melting plate comes
  back to lava even after a restart — and no longer multiplies lava sources:
  the melt restores the captured state verbatim with a forced physics update,
  so the restored lava starts flowing immediately.
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
