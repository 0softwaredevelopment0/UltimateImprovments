# Changelog

All notable changes to the UltimateImprovments plugin family are documented
in this file.

## [1.8.3-release.1] — since 1.8.3-alpha.8 (2026-10-07)

> **Release channel** — the first full (non-pre-release) family release.
> The whole 1.8.3 content (DFC cinematic startup/shutdowns/self-destruct,
> Unbreaking on plugin wear, multi-reactor) plus the fixes below were
> verified in-game on the test server against the full checklist.

### Added
- **`/ui update [tag]` — download a release into the update folder** — fetches
  a GitHub release (latest, or an explicit tag "1.8.4"/"v1.8.4"), downloads
  its `*-jars.tar` asset and extracts the family JARs into
  `plugins/UltimateImprovments/update/`. Nothing is applied automatically;
  permission `ui.command.swapjar` (one workflow with `/ui swapjar`).
- **Multi-reactor support completed (D.F.C)** — assembling a second reactor at
  a different location no longer fails with "The reactor is already active at
  this place!". The assembly used to go through the legacy single-reactor
  accessor and rejected whenever ANY reactor existed on the server; it now
  rejects only a reactor at the SAME anchor and otherwise creates a new
  reactor instance with its own ID (`REACTOR-<world>-<x>-<y>-<z>`) that is
  ticked, persisted, audited and reported independently. Supporting changes:
  the tick task drives every assembled reactor, the rotating structure audit
  keeps one cell-state cache per anchor (the audit used to be bound to a
  single base and would thrash between two reactors), sign click stats and
  glass auto-repair resolve their reactor by block position, and component
  broadcasts (shield/lasers/fusion/case) go through their owning reactor.
  The chat-command stats box shows the nearest reactor.

### Changed
- **`/ui swapjar` is now the apply step of the update flow** — instead of the
  dead runtime hot-swap (Paper 26.3+ blocks runtime JAR registration) it
  moves the downloaded `UI-*.jar` files from
  `plugins/UltimateImprovments/update/` into `plugins/` (deleting the old
  JARs of the same artifacts first — a move, not a copy) and asks for a
  server restart. Artifacts whose old JARs are locked (Windows holds the
  open JAR handles of the running JVM) are reported and left in `update/`
  with manual replacement instructions; on Linux the move succeeds while the
  server runs.

### Removed
- **The legacy updater (`/ui checkver`, `/ui updatejar`, the startup update
  check)** — it queried the old repository layout (`rizer001` owner,
  `build/libs/` Contents API, monolithic `UltimateImprovments-<ver>.jar`
  names) and matched nothing since the multi-module release era. Replaced by
  `/ui update` + `/ui swapjar`. The module count drops 98 → 97
  (UpdateChecker module removed).

### Fixed
- **The startup sequence could run alongside the self-destruct protocol** —
  the startup lamp pulse rolled the 1% self-destruct chance and then started
  the cinematic startup anyway, so both sequences broadcast at once; pulsing
  the lamp while the protocol was already active started a fresh startup
  during the countdown. The protocol now owns the reactor: a pulse while it
  is active is ignored, a roll that hits aborts the startup before it begins,
  an in-flight sequence (restored from the database) is cancelled on load and
  on the next tick (a still-forming CREATING shield resets to OFFLINE). The
  FINALE no longer hangs when the protocol runs on a core that never formed a
  shield (the startup was blocked — nothing to burn): it completes and shuts
  the systems down instead of waiting for a shield failure forever.
- **`/ui reload` reloaded 0 of 20 addons** — the hot-reload engine unloaded
  every addon from the PluginManager and tried to load it back from its JAR
  file, which Paper/Purpur 26.3 hard-blocks for paper-plugins
  (`IllegalStateException: Cannot register paper plugins during runtime!` —
  every UI JAR ships a `paper-plugin.yml`), leaving the whole family
  disabled and unloaded. Verified against the server bytecode: this version
  also CLOSES the plugin classloader inside `disablePlugin`
  (`ConfiguredPluginClassLoader.close()`), so plain disable→enable would hit
  a "zip file closed" zombie. The engine now performs a SOFT lifecycle cycle
  on the same instance — `PluginDisableEvent` + `setEnabled(false)` (a real
  `onDisable`, classloader kept open) → config re-read →
  `enablePlugin` (real `onEnable`). The same soft path serves
  `/ui reload <addon>`, `/ui addon enable|disable|restart` (disable no longer
  closes the JAR, so a later enable works) and
  `/ui plugin enable|restart`. Note: code updates still require a server
  restart — already-loaded classes stay cached and a closed classloader
  cannot be reopened.
- **Custom-enchant wear ignored the Unbreaking enchantment** — the Unbreaking
  roll in `ItemDurabilityUtil` read the enchantment level through the legacy
  `Enchantment.UNBREAKING` constant, which on Paper 26.3 is a stale API-view
  wrapper whose holder never matches the holders stored on items (the same
  registry mismatch the custom enchantments hit earlier). `getEnchantmentLevel`
  therefore always returned 0 and AoE, veinminer, treecapitator, degradation,
  levitation, flight, item stealing, Piercing and the sunburn wear consumed
  full durability regardless of the Unbreaking level. All Unbreaking readouts
  now resolve `minecraft:unbreaking` through the server registry
  (`Registries.unbreaking()`); the `/ui dura` info readout had the same
  defect and is fixed too.
- **Chat filter word patterns failed to compile** — the wildcard-to-regex
  converter built an invalid Unicode property escape (`\p%L%` instead of
  `\p{L}`) for word boundaries, so every word pattern with a single `*`
  (e.g. the default `*нах`) was rejected at startup with
  "Unknown character property name {%}" and silently dropped from the
  filter. Word boundaries now use the correct `\p{L}` letter property;
  fully-wildcarded words (`*word*`) were unaffected.

## [1.8.3-alpha.8] — since 1.8.3-beta.4 (2026-10-06)

> **Alpha channel**: a large consolidated release (the interim beta.5–beta.11
> builds were never released). The DFC sequences are verified by build + unit
> tests; in-game verification on the test server is pending.

### Added
- **Reactor stall shutdown (D.F.C)** — when the reaction loses its heat the
  core warns and shuts itself down automatically. One warning per downward
  threshold crossing while the reaction is running: below 1M C* (fusion
  stops), below 10k C* (critical), below 0 C* (reaction failure). Reaching
  absolute zero (−273 C*) starts the full shutdown procedure: announcement
  → 5s → "Shutting down power lasers..." (Power Laser #1 off, 3s, Power
  Laser #2 off) → 2s → "Shutting down stabilization lasers..." (2s, stab
  off) → 2s → "Closing content absorber valve..." (3s, valve closed) → 3s
  → "Shutting down reactor shield..." (SHUTDOWN state, integrity −10%/sec,
  sign status "Shutting down") → "Success." → 3s → "Core marked as offline,
  awaiting for startup." A laser switched off by the procedure ignores its
  ±5% control lamps until the next startup pulse; the startup sign shows
  "Offline" until a new pulse. The stall phase persists
  (`stall_phase`/`stall_ticks`/`core_offline` columns) and resumes after a
  restart.
- **Manual reactor shutdown (D.F.C)** — pulsing the startup lamp while the
  core is already running starts the shutdown with "Core shutdown initiated
  due to manual trigger, please wait." followed by the normal stall
  procedure. Two differences from the automatic stall: the trigger is
  silently ignored while the shield stress is above 10% (nothing happens),
  and after the "Shutting down power lasers..." step the core dumps all its
  heat to −273 C* at 10%/sec of the temperature it had at the shutdown
  start — the next shutdown step does not proceed until −273 is reached.
- **Cinematic reactor startup (D.F.C)** — the startup lamp pulse runs a full
  sequence instead of instantly forming the shield: "Core startup initiated
  due to a manual trigger, please wait." → 5s → "Starting up stabilization
  lasers..." → 3s → "Success." → 3s → "Starting up power lasers..." → 3s →
  "Success." → 3s → "Opening content absorber valve..." → 3s → "Success."
  → 3s → "Forming reactor shield..." (the shield builds at a fixed
  10%/sec, ~10s) → "Success." at 100% → 3s → "Igniting reactor core..."
  (the core becomes operational, central particles appear) → 3s → "Reactor
  startup complete, resume normal operations." The laser/absorber ±5%
  control is inert during the whole sequence and takes effect at the
  ignition step. The startup phase persists
  (`startup_phase`/`startup_ticks` columns) and resumes after a restart.
- **Self-destruct protocol reworked (D.F.C)** — the 1% startup roll now runs
  a longer cinematic: 5s sensor blackout (No signal) → 1s → "All controls
  are non-functional, restarting systems..." (every control lamp locked) →
  5s → the T-60s announcement → 60s countdown with the protocol screen on
  the signs and a warning ping every second → "Beginning detonation
  procedure..." → 5s → "Bypassing internal PL power limits, new limit is
  2000%." → 5s → "Overdriving power lasers for 2000%, waiting for a
  meltdown." The overdrive ramps each Power Laser to 2000% and burns the
  shield at double the configured rate. When the shield reaches 0% (burn,
  stress — whatever kills it first) the protocol reports "Self-destruct
  protocol complete, detecting core shield failure, shutting down
  systems..." and the existing T-10s detonation countdown proceeds.

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
- **Inert control while offline/starting/stopping** — the control lamps are
  never locked, but while the reactor is offline, forming or shutting down
  the lasers do not heat/cool and no core particles/ambient hum are emitted.
- **Shield ramp-down is detonation-proof** — the SHUTDOWN shield state
  cannot fail, detonate or trigger the emergency stop; integrity simply
  ramps down and the shield ends offline.
- **The shield no longer transitions to WORKING automatically at 100%** —
  the ignition is an explicit step of the startup sequence (new `ignite()`).
  The old instant messages ("Forming the shield...", "Shield formed!...")
  are replaced by the sequence; the `shield_build_rate` config key no
  longer affects the forming speed (fixed 10%/sec).

### Fixed
- **Plugin wear ignored the `minecraft:unbreakable` tag** — items with the
  vanilla unbreakable component still lost durability from plugin wear (aoe,
  veinminer, treecapitator, degradation, levitation, flight, item stealing,
  Piercing, sunburn). All plugin wear paths now respect the tag, including
  the sunburn fallback path.
- **False alarms during shield forming** — the integrity warning ping
  (every 0.5s), the "Shield integrity compromised!" broadcast (every 10s),
  the red-white sign flashing and the side-barrel indicator bulbs treated
  the CREATING state (integrity below 100%) as a problem; they now only
  fire while the shield is actually WORKING. The same applies to the new
  SHUTDOWN state.
- **Typos in the stall messages** — "initained" → "initiated",
  "failue" → "failure", "awating" → "awaiting", "please active" →
  "please activate" (code fallbacks + EN config section).
- **3s pause between the core ignition and the startup completion message**
  — "Igniting reactor core..." is followed by a 3s wait before "Reactor
  startup complete, resume normal operations."; the laser/absorber control
  still takes effect at the ignition step, not after the pause.

### Removed
- **Legacy integrity-system migration code** — the one-time migration of old
  PDC integrity counters, the `INTEGRITY_*` PDC keys and the
  "Integrity: N%" lore sweep (including the item-event cleanup listener) are
  gone; every deployed item has long since been converted to the vanilla
  `damage` component.

### Notes
- Existing servers with a deployed config keep their explicit
  `enabled = false` — flip it to `true` (or delete the
  `[features.integrity.unbreaking]` section) to get the new default.

## [1.8.3-beta.4] — since 1.8.3-alpha.7 (2026-10-04)

> **Beta channel**: feature-complete; the enchantment rework was verified by
> build + unit tests, in-game verification on the test server is the next step.

### Added
- **`/ui dura` — held-item durability scaling** with two variants per
  operation: the default `multiply|divide` runs as a mechanics event (an
  unbreakable item loses nothing and the lost points are rolled through the
  vanilla Unbreaking chance tables), while `raw multiply|divide` ignores the
  gates and applies the numbers exactly as typed. `/ui dura` (bare) shows the
  held item's durability info (remaining, %, unbreakable, Unbreaking level).
  Fractional values are accepted (dot or comma). Permission:
  `ui.command.dura` (default FALSE).
- **Durability scaling API** — `ItemDurabilityUtil.multiplyItemIntegrity(item, factor)`
  and `divideItemIntegrity(item, divisor)` scale the item's remaining
  durability by any fractional number (rounded to whole vanilla points,
  clamped; editor ops — bypass the unbreakable tag and the Unbreaking roll
  on purpose). `increaseItemIntegrityPercent` now accepts fractional percents
  properly (point amount rounded instead of floored, so e.g. +0.4% on a
  100-max item no longer silently repairs nothing).
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
