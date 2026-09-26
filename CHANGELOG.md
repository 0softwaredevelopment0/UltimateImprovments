# Changelog

All notable changes to the UltimateImprovments plugin family are documented
in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] — since 1.8.3 (2026-09-26)

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

## [1.8.3] — 2026-09-26

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
