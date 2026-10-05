# Changelog

All notable changes to the UltimateImprovments plugin family are documented
in this file.

## [1.8.3-beta.5] — since 1.8.3-beta.4 (2026-10-05)

### Changed
- **Unbreaking now applies to all plugin durability wear** — the Unbreaking
  roll used to be opt-in and off by default
  (`features.integrity.unbreaking.enabled = false`), so plugin wear (aoe,
  veinminer, treecapitator, degradation, levitation, flight, item stealing,
  Piercing, sunburn) ignored the enchantment entirely. The roll is now on by
  default and follows the vanilla chance tables, one roll per damage point:
  tools `1/(level+1)`, armor `0.6+0.4/(level+1)` (armor pieces detected via
  ArmorMeta). New public helper `ItemDurabilityUtil.applyUnbreaking`; the
  sunburn fallback path (integrity feature disabled) rolls as well. The
  config toggle is kept — vanilla wear is always rolled by vanilla itself.
- **INFO hint at startup** when the Unbreaking roll is disabled, so servers
  can immediately see why item durability ignores the enchantment.

### Notes
- Existing servers with a deployed config keep their explicit
  `enabled = false` — flip it to `true` (or delete the
  `[features.integrity.unbreaking]` section) to get the new default.

## [1.8.3-beta.4] — since 1.8.3-alpha.7 (2026-10-04)

> **Beta channel**: feature-complete; the enchantment rework was verified by
> build + unit tests, in-game verification on the test server is the next step.

### Added
- **Custom enchantments in random loot** — all 18 `ui:*` enchantments joined
  the `minecraft:enchantment/on_random_loot` tag (it previously contained only
  wind_burst/mending), so loot rolls (`enchant_randomly`, e.g. end village
  towers) can now produce them, matching the other four spawn tags.
- **`/ui enchant confirm` / `/ui enchant cancel`** — the 255 level ceiling is
  gone; a give above `enchant.max_level` (default 10) is parked per-sender and
  must be confirmed within 60 seconds (the warning message has clickable
  confirm/cancel hints). `take` is not gated.
- **Namespaced enchantment ids** — arguments, tab-complete, the success
  message and `/ui enchant check` use `namespace:name` ("minecraft:mending",
  "ui:aoe"); bare names still resolve.
- **Error 019 — ambiguous enchantment name** (`/ui enchant`): a bare name
  registered in several namespaces (e.g. `ui:aoe` + `test:aoe`) is rejected
  with the list of ids; specify one explicitly. The text is configurable in
  both languages: `[messages.enchant]` / `[messages_en.enchant]`, key
  `ambiguous_enchant` (%enchant% = typed name, %list% = registered ids).

### Changed
- **All custom enchantment levels capped at 10** — the datapack `max_level`
  and the Java `MAX_LEVEL` constants are in sync now (six charms were 255,
  magnet/lava_walker 16, AoE raised 8 → 10 with a 21×21×21 scan cube). This
  fixes mobs/villagers/loot spawning absurd levels; existing higher items keep
  working (mechanics clamp at read).
- **`enchant.max_level` is the confirm threshold**, not a cap (rule bound
  widened to 1..1_000_000 in ConfigRules; template updated).
- **Curse of Disappearance ×10 stronger** — 0.01% vanish chance per second
  per level (level 10 = 0.1%/s), so the curse is actually noticeable.
- **`/ui enchant check` reworked** — full enchantment ids, fixed a broken
  skip that duplicated custom rows, and five previously invisible charms
  (repairing, lava_walker, blunting, vulnerability, disappearance) now show.

## [1.8.3-alpha.7] — since 1.8.3-alpha.6 (2026-10-02)

> **Alpha channel**: manual testing pending; not verified on the test server yet.

### Added
- **`/ui opself` — console-confirmed self-OP** (ui-admin): a player may
  request operator status for themselves, but OP is only granted after an
  explicit console decision. Flow: `/ui opself` (player, sudo-gated) →
  console alert with hints → `/ui opself confirm` or `/ui opself cancel`
  (console-only subcommands) → the player is told the decision in their
  language. Requests expire after `op_self.ttl_seconds` (default 60 s) with
  a notice to the player; a new request is rejected while one is pending;
  the permission and OP status are re-checked at decision time. The grant
  deliberately does NOT touch the operator whitelist (it has its own
  commands). Sudo gate: `"ui opself"` added to `sudo.dangerous_commands`
  defaults (UI-Guard.toml) and to the interceptor's error-precedence map,
  so the sudo password dialog opens before the request is sent. New
  permission `ui.command.opself` (default TRUE — the console confirmation
  is the real gate) and config toggle `[op_self] enabled` (default true).
  All texts are configurable in both languages: `[messages.op_self]` (RU)
  / `[messages_en.op_self]` (EN) in UI-Admin.toml, routed via `AddonCatalog`.
- **`/ui opself` anti-spam cooldown + admin alerts** (same release):
  requests are rate-limited by `op_self.request_cooldown_seconds` (default
  60 s, 0 = off, counts from the moment a request is sent); players on
  cooldown get a message with the remaining seconds. Bypass permission
  `ui.command.opself.bypasscooldown` (default FALSE). Admins with
  `ui.alerts` / OP now receive an AlertBroadcast when a request is sent,
  confirmed or denied (texts: `admin_request` / `admin_confirmed` /
  `admin_denied` in both language sections).
- **Meteor scheduler log toggle** — the periodic console lines
  "Next meteor in N minute(s) (N ticks)" and "No players online in
  '<world>', skipping." are now silent by default; set
  `[meteor] log_scheduler = true` (UI-World.toml) to bring them back.
  One-off/action warnings (world not found, active limit, force spawn)
  are unaffected.
- **"Loaded successfully" alert per session** (ui-core): after a server
  start, every alerts holder (`ui.alerts` / OP) receives
  `prefix + "UltimateImprovments loaded successfully!"` exactly once —
  the first time they are present after startup (join listener +
  sweep of already-online players at enable). Later joins in the same
  session stay silent; the in-memory flag resets on server restart.
  Feature toggle `[loaded_alert] enabled` (default true, UI-Core.toml,
  read live). New `LoadedAlertListener`, registered in `PluginStartup`.
- **`/ui stresstest` — server benchmark / stress test** (ui-guard): runs a
  controlled load generator and reports how the server held up.
  `/ui stresstest start <type> <power>` with types `entity` (spawns around
  the anchor, every spawn PDC-marked and removed on stop), `block` (chain
  redstone updates on a snapshotted platform, chunks pinned with plugin
  chunk tickets), `chunk` (load/unload of already-generated chunks outside
  the view distance — no new terrain is ever created) and `selector`
  (repeated global entity-list walks plus `getNearbyEntities` queries);
  powers `minimal|low|moderate|high|max` scale the work per cycle. A
  warmup (`stresstest.warmup_seconds`, default 3 s) samples the baseline
  before any load, then MSPT/TPS/RAM/entity count are sampled every second
  and the report (duration, reason, MSPT avg/max, TPS avg/min, RAM %, work
  units) is printed to the initiator and the console
  (`stresstest.log_report`). Safety: `stresstest.max_duration_seconds`
  (default 300, 0 = unlimited) auto-stops a forgotten run,
  `stresstest.max_entities` caps the entity generator, disabling the module
  (reload/shutdown) stops the run and restores every world change, and a
  2 s startup sweep clears PDC-marked leftovers after a crash. New
  permission `ui.command.stresstest` (default FALSE — the command creates
  real load), config `[stresstest]` and message sections
  `[messages.stresstest]` / `[messages_en.stresstest]` in UI-Guard.toml
  (routed via `AddonCatalog`), a `/ui help` entry, and `"ui stresstest"`
  added to `sudo.dangerous_commands` (with `ui.command.stresstest` in the
  interceptor's 002-over-003 precedence map).

### Fixed
- **`/ui stresstest` run bugs** (ui-guard, same release):
  - **entity**: pruned references by `isValid()`, which is also false for
    entities in unloaded chunks — they leaked on stop (nothing removed them
    until the next startup sweep) and silently freed `max_entities` cap
    space, letting the real entity count exceed the cap. Now only
    `isDead()` entities are dropped; chunk-unloaded ones stay tracked,
    counted against the cap and removed by `stop()`.
  - **chunk**: the initial loaded/unloaded state is now snapshotted at
    start and restored on stop — previously chunks loaded by the run could
    stay loaded forever (unload requests are best-effort) and chunks the
    run unloaded were never re-loaded, so the world state was not
    restored. `work` now counts actual state changes (loads verified via
    `isChunkLoaded`, unload requests credited next cycle only when the
    server really unloaded the chunk) instead of raw request counts, and
    the first cycle loads (matching the documented behaviour) instead of
    starting with unload requests at the anchor.
  - **block**: the snapshot is restored with `applyPhysics = false` — the
    restore no longer fires a neighbor-update storm across the region
    right after the load stopped.
  - **manager**: a run that died with a generator error now sends the
    report to the initiator (previously console-only, the player never
    learned the run had stopped); `/ui stresstest stop` issued by a second
    admin reports to both the original initiator and the stopper;
    the     report duration no longer shows `0.0s` when a run is stopped
    during the warmup; the report's `Entities` column is captured before
    the generator cleans up (previously always showed the post-cleanup
    count); and a scheduler rejection of the run task now rolls the whole
    start back instead of leaking the prepared generator.
  - **entity: drop-abuse protection** — test entities now spawn
    invulnerable (no death, no loot for players hitting them mid-run)
    and never despawn on their own (`setRemoveWhenFarAway(false)` +
    `setPersistent(true)`); the final cleanup removes them via
    `remove()`, which produces no drops and no death animation.
  - **block: crash-recovery record in the DB** — the platform snapshot is
    persisted to `ui_state` (namespace `stresstest`, key `block_region`)
    BEFORE the first block is modified; if a run is killed hard (crash,
    kill -9) and the modified region ends up in the world save, the next
    startup reads the record and restores the region (mirroring the
    entity PDC sweep). The record is deleted after a successful stop;
    restore failures keep it for a retry at the next startup.
- **Wireless redstone: bind default is now OFF** (ui-world + ui-core):
  `wireless_bind_enabled` defaulted to ON in the DDL although the
  documented default was OFF ("/ui wirelessbind", "/ui help") — rows
  auto-created with the old default armed shift+RMB binding for players
  who never used the feature. New default is 0 (DDL, migration column,
  in-code fallback) and a one-time guarded migration flips every stored
  value back to OFF; players who want the bind re-enable it with
  `/ui wirelessbind on`.
- **`/ui unlock` tab-complete + sign/book data loss** (ui-other):
  - tab-completion was never registered — `/ui ` no longer falls back to
    suggesting online player names; `book`/`sign` are suggested, and a
    wrong or missing argument now prints the usage line (previously the
    command failed silently);
  - **sign**: `/ui unlock sign` replaced the held sign with a freshly
    created item, copying back only name/lore/PDC — every other component
    (the sign text above all) was destroyed. Modern vanilla keeps the
    sign text AND the waxed flag in the item's block-state data, so the
    item is now edited in place via `BlockStateMeta` →
    `Sign#setWaxed(false)`: only the waxed flag flips, everything else is
    preserved. A sign without block data or already unwaxed reports that
    there is nothing to unlock;
  - **book**: `/ui unlock book` (written → writable) preserved only the
    pages — anvil display name, lore, plugin PDC, enchantments and custom
    model data are now carried over as well.

### Changed
- **Wireless redstone: every linked device now performs its real vanilla
  action** (ui-world):
  - **dispenser/dropper** — performs a REAL dispense via the block-state
    API (`Dispenser#dispense()`); previously only the `triggered`
    animation property was toggled, so a wirelessly triggered device
    "clicked" without ever shooting. The dropper (which has no
    `dispense()` in the API) is simulated faithfully: first available
    stack ejected toward the facing, `BlockDispenseEvent` fired first so
    protection plugins can cancel it, then the item consumed and dropped
    with velocity.
  - **piston/sticky piston** — extension powers the piston from an
    adjacent AIR block (never from the pushing face; falls back to
    overwriting the block behind it like before, restored on retract);
    retraction just removes the power block and lets vanilla retract —
    a sticky piston now pulls its block back for real. Previously the
    `extended` property was set manually on retract, leaving a ghost
    piston head in the world and skipping the vanilla retraction.
  - **observer** — a wirelessly activated observer pulses its output for
    2 ticks by driving the `powered` property directly (physics on, so
    the output side really powers and third observers detect the state
    change). Replaces the old stone-flicker trick that mutated the world
    in front of the observer and produced a double pulse (place + remove
    of the probe block).
  - **redstone lamp** — unchanged from the previous change: emits signal
    to adjacent dust and pulses observers watching it.
  - **redstone wire** — unchanged: driven to 15/0 directly.
- **Wireless redstone: a wirelessly activated lamp is now a real signal
  source** (ui-world): the `lit` property is applied without physics (the
  server would immediately undo it otherwise) and vanilla lamps never emit
  power, so a wirelessly lit lamp only glowed — no current, no observer
  reaction, useless in circuits. Now activating a lamp wirelessly also
  drives adjacent redstone dust to 15/0 (a real current the rest of the
  vanilla redstone can read and propagate) and pulses adjacent observers
  that watch the lamp (their powered property is driven directly for the
  vanilla 2-tick pulse length, which powers their output side too). The
  manager's watcher task propagates the observer pulse to its wireless
  partners as usual; deactivating restores the dust (a foreign signal on
  the same wire is only interrupted for one update).

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
[1.8.3-alpha.7]: https://github.com/0softwaredevelopment0/UltimateImprovments/compare/1.8.3-alpha.6...1.8.3-alpha.7
[1.8.3-beta.4]: https://github.com/0softwaredevelopment0/UltimateImprovments/compare/1.8.3-alpha.7...1.8.3-beta.4
[1.8.3-beta.5]: https://github.com/0softwaredevelopment0/UltimateImprovments/compare/1.8.3-beta.4...1.8.3-beta.5
