# Changelog

All notable changes to the UltimateImprovments plugin family are documented
in this file.

## [1.8.3-alpha.6] — since 1.8.3-alpha.5 (2026-09-30)

> **Alpha channel**: verified on the test server (Purpur 26.3); broader testing pending.

### Added
- **Crafter-only hint** — trying to assemble a custom item in a workbench or
  the 2×2 grid now shows a localized actionbar hint (throttled to one per 2 s
  per player; the throttle clears on quit). The hard block and the chat
  safety-net message remain. Configurable per language: new
  `[messages.crafting]` (RU) / `[messages_en.crafting]` (EN) sections in
  `UI-Items.toml`, key `crafting.crafter_only`, routed to UI-Items via
  `AddonCatalog`.
- **`/ui addon list` pagination arrows** — the same clickable `[<]` / `[>]`
  page navigation as `/ui help` (yellow when available, gray when not).
- **`Registries` helper (ui-core)** — modern registry access through
  `RegistryAccess.getRegistry(RegistryKey)` for enchantments, banner patterns,
  trim materials/patterns, jukebox songs, instruments and sounds (the legacy
  `Registry.*` statics are deprecated for removal).
- **Distribution archive** — `UltimateImprovments-1.8.3-alpha.6-jars.tar`.

### Changed
- **Distribution jars slimmed 16×: 298.5 MB → 18.6 MB.** The SQLite JDBC
  driver (24 MB of native libraries for 24 OS/arch combos) used to leak into
  every addon jar transitively via `project(':ui-core')`; it is now bundled
  in UI-Core alone (the only module that touches `org.sqlite` directly), with
  natives trimmed to real server platforms (Windows/Linux x86_64,
  Linux/macOS aarch64). Addon jars dropped from ~14 MB to 0.3–0.5 MB.
- **New root task `distributeJars`** — shadow-only assembly of all 21 jars
  into `build/libs` without tests; `package_jars.sh` builds through it and
  tars only the current version (no longer wipes `build/distribution`).
- **Migrated to Paper 26.3** (dev bundle `26.3.build.+`, `api-version: 26.3`
  in all 21 manifests, docs updated). Compile-level breakages fixed:
  `ClientboundExplodePacket` gained the `playSound` component, the
  `MAP_COLOR` data component was removed from the game (the dead
  `/ui itemnbt mapcolor` subcommand removed with it), NMS
  `Entity#setInvulnerable` removed (god off + JoinInvulnerableReset moved to
  the Bukkit API).
- **162 deprecated-API call sites modernized** (compile now runs with
  `-Xlint:deprecation` permanently): `Player.isOnGround()` → `Entity` cast
  (29 anticheat checks), `kickPlayer(String)` → `kick(Component)` (13),
  `disallow(Result, Component)` (7), Adventure `customName()/displayName()/
  lore()` accessors (16), `AnvilView` repair cost (4 listeners, reflection
  fallback removed), `InventoryView#setCursor` (4), typed `GameRule` API (9),
  `BookMeta.pages()`, `deathMessage(Component)`, `getInputChoice()`,
  `Criteria.DUMMY`, `URI.create().toURL()`, `ExactChoice` varargs,
  `AttributeModifier(NamespacedKey, ...)`, `Registry.getKeyOrThrow(...)`.
  Kept with documented `@SuppressWarnings` where no modern replacement
  exists: `EntityDamageEvent.DamageModifier` (blunting/vulnerability),
  `Material.isInteractable` (AoE filter), the deprecated `PlayerLoginEvent`
  (async pre-login migration is a separate task), `PlayerAnimationEvent`
  (NoSwingCheck), in-place `ItemStack#setType`, Vault `Economy` String
  overloads (mandatory interface implementations).
- **Datapack fixed for 26.3** (`pack.mcmeta` `min/max_format` 107 → 121):
  worldgen overrides replaced with the vanilla 26.3 trees (density functions,
  noise, noise settings, placed features; 11 files for entities that no
  longer exist in 26.3 removed), advancements fixed for the new schemas
  (`recipes` list conditions validated at world load → obtain-based
  criteria with `custom_data` PDC predicates matching the
  `PublicBukkitValues` storage layout; `location` trigger `player` condition
  is a single object now), loot condition discriminators `condition` →
  `type` (12 tables, 116 condition objects).
- **Armor / armor-trim effect defaults** — `duration_ticks` 61 vs
  `check_interval_ticks` 40: each refresh lands a full second before the
  previous application expires, so effects no longer blink between checks.
  Existing live configs keep their explicit values (auto-repair never
  overwrites) — bump manually or `/ui config regen UI-Player.toml`.
- **Turret beam aims at the center of the victim's bounding box**, shifted
  ~1 block down (clamped above the feet) — it used to target the eye level
  and visually fly over mobs.
- **Docs refreshed**: README repositioned as a big collection of gameplay
  features (21-plugin family, single-restart install, AddonCatalog addon
  system), GUIDE.md fully rewritten for the current architecture, all
  `rizer001-Development` links/repositories moved to the renamed
  `0softwaredevelopment0` organization (11 git remotes included).
- Requirement pinned to **Paper 26.2+** — clarified after the api-version
  investigation (plugins with `api-version: 26.2` cannot load on 26.1);
  later migrated to 26.3 (see above).

### Fixed
- **"Discharge!" granted for the Photon Cannon** — obtain-based advancement
  criteria matched the plain vanilla item (shocker and photon cannon are both
  warped fungus on a stick). Criteria for shoker, photon cannon, health meter,
  entity locator and antimatter now require the crafting result's PDC marker
  via the `custom_data` predicate (matched under Bukkit's `PublicBukkitValues`
  storage); the health meter advancement also targeted the wrong base item
  (recovery compass → name tag).
- **Turrets were completely silent** — the beam sweep task was lost during
  the UI-Other module split: `TurretManager.init()` only loaded persisted
  configs and nothing ever called `tick()`. UI-Combat now schedules the sweep
  every 10 ticks and cancels it (+ beam cleanup) on disable.
- **DamageSource builder crash** — Paper 26.3 requires `withDirectEntity`
  when `withCausingEntity` is set: every Blazing Sword hit, burn tick and
  turret beam threw `IllegalArgumentException`. All three builders set both.
- **`/ui addons` removed** — it was dead code (never registered); the
  universal `/ui addon` manager (`list|status|enable|disable|restart`) is the
  only addon command, the README/GUIDE point to `/ui addon list`.
- Standalone `CHANGELOG-1.8.3-beta.3.md` removed (superseded by this file).

[1.8.3-alpha.6]: https://github.com/0softwaredevelopment0/UltimateImprovments/compare/1.8.3-alpha.5...1.8.3-alpha.6
