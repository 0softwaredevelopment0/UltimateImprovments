# Changelog

All notable changes to the UltimateImprovments plugin family are documented
in this file.

## [1.8.5-alpha.12] - since 1.8.5-alpha.11 (2026-10-21)

> **Alpha channel** — permission fix for the sculk contact hazards on top of `1.8.5-alpha.11`.

### Fixed
- **The sculk hazard bypass was effectively on for every admin.**
  `ui.sculk.hazards.bypass` was checked with `Player#hasPermission`, which also
  answers `true` for a wildcard grant — `*` or `ui.*`, the normal LuckPerms
  setup for operators and staff — so the player testing the mechanic was
  immune and the hazards looked broken. Immunity now requires an **explicit**
  grant of that exact node: the new `Permissions.isExplicitlyGranted` combines
  `Permissible#isPermissionSet` (exact node only, wildcards excluded) with
  `hasPermission`. OP alone and wildcards no longer count, and the immunity log
  line plus the `At you:` line of `/ui sculk status` now say "granted
  explicitly".

## [1.8.5-alpha.11] - since 1.8.5-alpha.10 (2026-10-21)

> **Alpha channel** — bugfix for the sculk contact hazards on top of `1.8.5-alpha.10`.

### Fixed
- **Sculk hazards could silently do nothing.** Three failure modes of the
  `SculkHazards` module are now covered:
  - a live `configs/UI-World.toml` without `[sculk.hazards]` used to switch the
    whole module off with a single console warning (the in-memory repair only
    rewrites the file, so any hand-edited or partially written copy could hit
    this). The module now falls back to the bundled `UI-World.toml` defaults,
    keeps running and logs one actionable warning instead of going dark;
  - a player holding the bypass node — including through a wildcard grant such
    as `ui.*` or `*`, which matches `ui.sculk.hazards.bypass` too — was skipped
    without any trace, which looks exactly like "the effects are broken". The
    skip is now logged once per player (`[SCULK] Hazards: <player> is immune
    (<node>)`), and `/ui sculk status` reports the flag live;
  - `/ui sculk reload`-style reloads could leave the timer stopped if the
    previous task had been cancelled; the timer is now (re)started whenever it
    is missing.
- **`/ui sculk status` now shows the player's own evaluation** — the block at
  the feet, the block below, the gamemode, the bypass flag and the rules that
  match right now (`matched: vein, block` / `(none)`), so a "nothing happens"
  report can be told apart from a config, permission or geometry problem in one
  look.

### Added
- `radius` (per hazard rule, `[sculk.hazards.<rule>]`, default `0`, clamped to
  0..4) — proximity instead of strict contact: with `radius = 1..4` a rule also
  fires when a matching block is inside a cube of that size around the player
  (nearest block wins, `offset` is ignored). At `0` the behaviour is unchanged:
  only the block selected by `offset` counts. Every pulse scans
  `(2*radius+1)^3` blocks per player per rule, so 1-2 is the sensible range.

## [1.8.5-alpha.10] - since 1.8.5-alpha.9 (2026-10-21)

> **Alpha channel** — performance fix on top of `1.8.5-alpha.9`.

### Changed
- **DFC fuel checks no longer take a block-entity snapshot.** `ReactorFuel`
  read both fuel barrels through `Block#getState()`, which serializes and
  re-deserializes the barrel NBT on every call (`saveWithFullMetadata` +
  `loadStatic`). The heat check, `isHeating()` and the Fuel sign asked for it
  several times per tick, which made `hasBarrelFuel` the single largest
  consumer of the server tick in the profiler (≈4.6 ms + ≈1.7 ms of
  `isHeating`). Barrels are now read through `Block#getState(false)` (live
  state, no snapshot — the returned inventory is still the real one), only
  for `Material.BARREL` blocks, and the answer is cached for 10 ticks
  (dropped immediately after a fuel unit is consumed). `ensureNamed` reads
  the live custom name and only builds a snapshot when the name actually has
  to be written; the fusion floor barrel uses the live state too.
- **The rotating structure audit no longer parses strings per cell.** The
  tracker mirrors its template index into flat int/category/material arrays,
  so a cell check is three int reads plus one block lookup instead of a
  `String#split`, three `Integer#parseInt` and a `"dx,dy,dz"` concat +
  HashMap lookup for the expected material. The ignored-cell set is fetched
  once per pass instead of once per cell.
- **Reactor visuals are gated by nearby players.** `ReactorShield.tickParticles`
  and `ReactorDisplay.tickVisual` return immediately when no player is within
  `reactor.particle_view_distance` — vanilla drops those particle packets for
  everyone else anyway, so an unvisited reactor no longer spends ~1 ms/tick
  spawning particles and ambient sounds.
- **Smaller hot-path cleanups:** bulb power checks use `World#getBlockAt` with
  integer offsets instead of a `Location` clone per lamp, and the core
  radiation scan iterates the online players directly instead of copying them
  into an array every tick.

### Added
- `reactor.particle_view_distance` (UI-MBS.toml, default 32.0) — radius of the
  reactor visual gate (0 = always spawn).

## [1.8.5-alpha.9] - since 1.8.5-alpha.8 (2026-10-21)

> **Alpha channel** — new feature on top of `1.8.5-alpha.8`.

### Added
- **Sculk contact hazards** — the `SculkHazards` module (UI-World) makes the
  grown sculk dangerous to walk on. Every second (`[sculk.hazards]
  interval_ticks`, 20 ticks) the pulse inspects the block under the player and
  applies the matching debuff, refreshing it for as long as the player stays
  there:
  - **sculk vein** → wither I for 1 s (`offset = 0`: veins have no collision,
    the player stands inside their block);
  - **sculk block** → wither I for 1 s (`offset = -1`: the player stands on top
    of it, i.e. occupies the block above);
  - **sculk shrieker** → evoker fangs summoned at the centre of the shrieker's
    block — the fangs have no owner, so they damage the player standing on the
    half-block (and every mob in the hitbox);
  - **sculk sensor / calibrated sculk sensor** → slowness 255 for 1 s
    (`offset = 0`: the sensor is half a block tall);
  - **sculk catalyst** → mining fatigue 255 for 1 s (`offset = -1`).
  Every rule has its own table (`[sculk.hazards.vein|block|shrieker|sensor|
  catalyst]`) with `enabled`, `blocks`, `offset`, `effect`, `duration_ticks`,
  `amplifier`, `particles`/`ambient`/`icon` — or `fangs`, `fangs_count`,
  `fangs_attack_delay_ticks` instead of an effect. Creative and spectator
  players are skipped, `ui.sculk.hazards.bypass` grants immunity, and
  `use_growth_world_filter = true` makes the hazards follow the
  `[sculk.worlds]` filter. The module is independent of
  `/ui sculk start|stop`; `/ui sculk status` now lists the active rules.

## [1.8.5-alpha.8] - since 1.8.5-alpha.7 (2026-10-21)

> **Alpha channel** — behavior change on top of `1.8.5-alpha.7`.

### Changed
- **Every pulse starts the bloom from a random one of the six face-adjacent
  blocks** — no pre-checks at all. A solid neighbour simply wastes that pulse
  (vanilla cannot place a sculk vein inside a solid block), so a walled-in
  catalyst never spreads, by design. A catalyst with an air/water face above a
  replaceable surface starts growing within a few pulses, because the random
  pick eventually lands on a valid face every second.

### Removed
- `warn_no_origin` and the no-origin console warning — with the random pick
  there is no "no origin" state any more; every catalyst always has six
  candidates.

## [1.8.5-alpha.7] - since 1.8.5-alpha.6 (2026-10-21)

> **Alpha channel** — behavior change on top of `1.8.5-alpha.6`.

### Changed
- **The bloom origin is now looked up only in the six face-adjacent blocks of
  the catalyst** — the previous build searched a radius around it. A neighbour
  qualifies only when it is air/water (a space where the vanilla charge can
  place sculk veins) and has a valid substrate on one of its own six faces: a
  `#minecraft:sculk_replaceable` block (fresh sculk seed) or an existing sculk
  block. The first qualifying neighbour is used, preferring a replaceable
  substrate over existing sculk and a substrate directly below. If none of the
  six neighbours qualifies, the catalyst is ignored — there is nowhere for the
  infection to start from.

### Added
- `warn_no_origin` (default `false`) — when enabled, a catalyst without a
  valid origin logs one actionable warning (once per catalyst) instead of
  being silently ignored. The warning is off by default to avoid console spam.

### Removed
- `bloom_origin_radius` — superseded by the fixed six-neighbour lookup. The key
  left in an existing config file is harmless and ignored.

## [1.8.5-alpha.6] - since 1.8.5-alpha.5 (2026-10-21)

> **Alpha channel** — bug-fix build on top of `1.8.5-alpha.5`.

### Fixed
- **A freshly placed sculk catalyst never spread** — the bloom was started at
  the catalyst block itself. A vanilla sculk charge can only grow from an
  air/water block space where a sculk vein can attach to a
  `#minecraft:sculk_replaceable` surface: the solid catalyst block is not such
  a space, and the default sculk behaviour on a non-sculk block neither grows
  veins nor converts anything, so every pulse was wasted (this also matches the
  vanilla rule that a lone catalyst in plain terrain does not spread on its
  own). The manager now searches for an air/water cell directly above a
  replaceable surface (or an existing sculk block) within
  `bloom_origin_radius` — new key, default 2, clamped to 1..8 — and starts the
  bloom from that cell, exactly like a mob death at that spot. A catalyst with
  no such surface within range now logs one actionable warning (once per
  catalyst) instead of silently doing nothing.
- `/ui sculk status` now also shows the origin search radius in the pulse line.

## [1.8.5-alpha.5] - since 1.8.5-alpha.4 (2026-10-21)

> **Alpha channel** — control surface for the sculk growth on top of `1.8.5-alpha.4`.

### Added
- **World filter for the sculk growth** — the new `[sculk.worlds]` section has
  an independently switchable whitelist and blacklist that may be enabled at
  the same time: the whitelist (when on) restricts growth to its worlds, then
  the blacklist (when on) removes worlds from the remaining set, so the
  blacklist always wins. Names are case-insensitive server world names
  (`world`, `world_nether`, ...). Catalysts in filtered-out worlds are not even
  registered, so they cost nothing; changing the filter and running `/ui
  reload` rebuilds the registry from the loaded chunks. An enabled-but-empty
  whitelist means "no growth anywhere", which is the safe way to scope the
  mechanic to one world before enabling it.
- **`/ui sculk status`** — prints the effective state (config master switch AND
  the runtime switch), how many catalysts are active right now (loaded chunk,
  valid block, allowed world) versus how many are tracked in total, the world
  filter in effect, and the pulse settings (interval, charge, per-pulse cap,
  MSPT gate).
- **`/ui sculk start|stop`** — turns the growth on/off at runtime and writes the
  switch to the `ui_state` table (`namespace = sculk`, `key = growth_enabled`),
  so it survives restarts. The config `[sculk] enabled` key remains the master
  switch and cannot be overridden by the command; `start` refuses to run while
  the master switch is off. If the database is unavailable the command still
  changes the in-memory state and says so instead of pretending it was saved.
- **Separate permissions** — `ui.command.sculk.status` (read-only),
  `ui.command.sculk.start` and `ui.command.sculk.stop`, so staff can watch the
  infection without being allowed to pause it (or the reverse). All three are
  registered in `Permissions` with default FALSE; tab-completion only offers
  the actions the sender may run.

## [1.8.5-alpha.4] - since 1.8.5-alpha.3 (2026-10-21)

> **Alpha channel** — new mechanic on top of `1.8.5-alpha.3`.

### Added
- **Infinitely growing sculk** — a sculk catalyst in vanilla only blooms when
  a mob dies nearby, because the death's XP becomes the bloom "charge" of its
  `SculkSpreader`; without deaths the patch stops growing. The new
  **SculkGrowth** module (UI-World) pulses every loaded catalyst on its own
  cooldown (`interval_ticks`, 20 = 1 s) through Paper's
  `SculkCatalyst#bloom(Block, int)` API, adding `charge_per_pulse` (default 10)
  each time. The spread itself is the real vanilla mechanic, not an emulation:
  only blocks from `#minecraft:sculk_replaceable` (stone, deepslate, dirt,
  gravel, sand, ...) are converted, sculk veins grow on surfaces, shriekers
  appear occasionally, and the vanilla sound/particles play. Ores, containers,
  tile entities and builds of other materials stay untouched. Because charge
  is never allowed to run out, the patch expands without a limit — it spreads
  like a virus.
- Catalysts are discovered automatically: loaded chunks are scanned for
  catalyst tile entities on chunk load and once at startup, player-placed
  catalysts are tracked from `BlockPlaceEvent`, broken ones are pruned. Only
  loaded chunks are processed (no force-loading), at most
  `max_catalysts_per_pulse` (default 32) catalysts bloom per pulse in a
  round-robin, and a pulse is skipped while the average tick time exceeds
  `max_mspt` (default 50 ms) — a TPS guard for worlds with hundreds of
  catalysts. Settings live in the new `[sculk]` section of
  `configs/UI-World.toml`.

### Fixed
- **Built JARs advertised a stale plugin version** — `processResources` expands
  `${project.version}` into `paper-plugin.yml`, but Gradle did not treat the
  version as an input of that task, so after a version-only bump the task was
  considered up-to-date and every jar kept the version of the last full
  rebuild (`1.8.4-alpha.2` through the whole `1.8.5-alpha.x` line). The version
  is now an explicit `processResources` input, so each release embeds the
  version it is built as.

## [1.8.5-alpha.3] - since 1.8.5-alpha.2 (2026-10-21)

> **Alpha channel** — bug-fix build on top of `1.8.5-alpha.2`.

### Fixed
- **The generator left its trigger frame behind** — assembling the blast
  furnace generator registered the structure but never touched the item
  frame, so the frame (and any item inside it) stayed on top of the furnace
  instead of breaking and dropping. Every other structure already consumes
  its one-shot trigger frame (battery, lamp, lightning, DFC, CER). The frame
  is now removed after a successful assembly and drops as an item frame —
  `GLOW_ITEM_FRAME` for glow frames — together with the displayed item.
  `GeneratorManager.assembleFromFrame` takes the frame reference from
  `ReactorListener`, so no coordinate guessing is involved.
- **Battery charge of a late-loaded world was never restored (and its rows
  were wiped)** — the energy rewrite deleted the WHOLE `energy_storage`
  table and re-inserted only what the currently loaded worlds reported, so a
  battery in a world loaded later (Multiverse, delayed world load) lost its
  saved charge within one save interval (10 s). The rewrite is now scoped to
  the UIDs of the worlds loaded at that moment; rows of other worlds survive.
  In addition, stored energy is restored for the pools that a rebuild
  actually created (`BatteryManager.rebuildFromMarkers` →
  `EnergyPersistence.load(Collection<BatteryPool>)`), so a world that loads
  after startup gets its batteries back instead of starting empty, while
  already running batteries are never rolled back to an older DB snapshot by
  the 5/30/120 s rebuild passes.

## [1.8.5-alpha.2] - since 1.8.5-alpha.1 (2026-10-21)

> **Alpha channel** — bug-fix build on top of `1.8.5-alpha.1`.

### Fixed
- **A battery multiblock now really is ONE battery** — every copper-grate
  block kept its own private charge (`CableNode.energy`), so charging one
  face filled only the blocks the traversal happened to reach and the
  multimeter showed a different value on every block; the far side of the
  same structure looked empty and refused to give energy. The cluster now
  owns a single `BatteryPool` (new class in UI-Energy) and every block's
  network node delegates its get/add/remove/set calls to that one instance:
  charge through any face = the whole structure charges, discharge through
  any face = the same reserve is drained, and the tooltip is identical on
  every block. Capacity is still `energy.battery.max_energy × blocks`, but
  as one shared cap instead of `blocks` independent caps.
- **Energy reached through a battery counted once per block** — the
  availability/space checks summed the same reserve for every block of the
  cluster (a 20-block battery with 100 000 ⚡ could "pay" 2 000 000 ⚡), and
  the balancer treated the blocks of one structure as separate batteries.
  Traversals now deduplicate by pool (identity), the pool is settled once,
  and a battery reached from the far side of the cluster is still walked
  through so cables behind it stay reachable.
- **Balancing mixed-size batteries destroyed charge** — the pass equalized
  absolute values, so a 100-block battery and a single-block one "leveled"
  to the same number of joules and the large one was emptied almost to
  zero. Balancing now targets the same CHARGE PERCENTAGE for every storage
  unit, capped per unit by `energy.balancer.max_transfer`.
- **Stored energy could be lost on a delayed rebuild** — `BatteryManager`
  recreated clusters (and now their pools) on every world-load/delayed
  rebuild pass without carrying the charge over. The pool object is reused
  for a surviving cluster UUID, so the scheduled rebuilds at 5/30/120 s
  after startup no longer wipe a charged battery.
- **Merging two clusters threw one charge away** — placing a block that
  bridges two assembled batteries now adds both pools and clamps to the new
  capacity, so no energy disappears in the merge.
- **Legacy saved energy is migrated, not dropped** — the per-block
  `energy_storage` rows written by previous builds are summed per cluster on
  load (the old per-block values were the cluster's split charge), and new
  saves write one row per storage unit instead of duplicating the total on
  every block.

## [1.8.5-alpha.1] - since 1.8.3-release.1 (2026-10-21)

> **Alpha channel** — first 1.8.5 build. Everything below landed after
> `1.8.3-release.1` (the whole 1.8.4 dev line): batteries as multi-block
> structures, the compact nuclear reactor with its thermal model, the
> datapack language variants, and the fixes on top. In-game verification of
> the newest features is still pending.

### Added
- **Batteries are real multi-block structures (UI-Energy + UI-Other)** — a
  waxed copper grate is now inert on its own: placing one creates no network
  node, no storage and no marker. A battery is assembled like the magnet — put
  a one-shot item frame on any grate and every copper grate connected to it
  becomes **one adaptive battery**: capacity `energy.battery.max_energy ×
  blocks` (100 000 ⚡ per block), the blocks share one charge pool and one
  operating mode. A redstone pulse on **any** block of the cluster cycles
  Charge → Charge & Discharge → Discharge (rising edge, so a lever held on does
  not keep flipping); the mode is persisted in the new `battery_modes` table.
  The multimeter reads the cluster's energy/capacity, block count and mode.
- **Compact nuclear reactor (UI-MBS + UI-Energy)** — a 3×3×3 multi-block
  energy generator (`nuclearreactor.nbt`). Assemble it with an item frame on a
  top corner (so the top-center barrel stays reachable), put emeralds and ice
  into that barrel, power the reinforced deepslate center with redstone (a
  lever on the barrel top powers the adjacent center — glass cannot hold a
  lever), and connect a cable under the bottom waxed chiseled copper port. It
  burns 1 emerald per 10 seconds and pushes 1000 ⚡/s into the network; fuel is
  spent only while the structure is intact, the center is powered, a cable is
  connected and the network actually accepts the energy (partial acceptance
  burns proportionally less).
- **CER thermal model, coolant and meltdown (UI-Energy + UI-MBS)** — the
  reactor now has a temperature in `[biome floor .. 1024]`. The floor is rolled
  at assembly from the build-site biome temperature (same formula as the DFC)
  and the passive cooling is scaled by the biome humidity. A running reactor
  heats to 512° at 20°/s with passive cooling disabled, then holds the point
  and drains the cooling buffer (1% per 8s); without coolant it keeps heating
  to 1024°, the center block turns into lava and a 10-second detonation
  countdown starts. Putting reinforced deepslate back into the center in time
  cancels the blast and resets the reactor to 512°; otherwise a charged
  creeper with explosion radius 5 detonates instantly. The barrel now also
  accepts ice — consumed automatically 1 block/s while the buffer is below
  100%, plain ice 1%, packed ice 2%, blue ice 3%. A stopped reactor passively
  cools at 1°/s × humidity down to its biome floor, and a restored coolant
  buffer pulls a running reactor back to 512°. Threshold particles (signal
  smoke above 640°, large smoke above 860°, lava sparks above 960°) and
  structure alerts (damage / restored / failure / meltdown / detonation
  prevented) are broadcast within 30 blocks. Breaking the redstone center
  detonates the reactor; breaking every template block dismantles it; any
  other break flips the structure into the damaged state until it is fully
  repaired. All state (temperature, limits, cooling, drain progress, meltdown
  timer, fuel, damage flag) persists per anchor in the new
  `nuclear_reactors` table, so a world can host any number of reactors.
  Configured in UI-MBS.toml `[nuclear_reactor]` (`heat_rate`, `work_temp`,
  `max_temp`, `passive_cool_*`, `temp_limit_*`, `cooling_*`, `meltdown_seconds`,
  `explosion_power`, `alert_radius` on top of the previous keys). The trigger
  item frame is consumed on assembly and dropped back (glow frames keep their
  type); SHIFT+RMB with the multimeter on any reactor block shows the `[CER]`
  data readout (temperature, cooling, meltdown countdown), and without a
  multimeter the player is asked to get one.
- **CER structure audit and meltdown escape hatch (UI-Energy)** — the reactor
  is now watched by a DFC-style rotating audit: 9 of the 27 template cells are
  re-read per second (`nuclear_reactor.audit_cells_per_second`, 27 = full check
  each pass), so bypasses that never fire `BlockBreakEvent` — explosions,
  pistons, plugin `setType` — flip the reactor into the damaged state, restore
  it, or dismantle it with the failure alert once every block is gone; player
  breaks/places still update the cache instantly. Sealing the meltdown lava
  with **any block other than reinforced deepslate** cancels the blast (the
  same "detonation prevented" alert) and dismantles the reactor, which can be
  rebuilt afterwards once the reinforced deepslate is back in the center.
- **NBT templates in the newer palette format** — `StructureTemplate` now
  accepts a palette `id` key as a fallback for the vanilla `Name`, so
  structure files exported by recent game versions load without a manual
  conversion step.

### Changed
- **Datapack ships in two languages (UI-Datapack)** — the bundled pack is now
  built twice: `UI-Datapack-en` and `UI-Datapack-ru`. The content is identical
  (enchantments, advancements, recipes, structures, worldgen); only the display
  names and descriptions are translated. The new `datapack.lang` key in
  `UI-Datapack.toml` (`"en"`, `"ru"` or `"auto"` — follows `messages.lang`)
  selects which variant the bootstrapper extracts into
  `plugins/UltimateImprovments/datapack/` and registers before the worlds load;
  stale variants are deleted, so the pack is never registered twice. Switching
  the language requires a restart (pack discovery happens at startup), and the
  active variant is visible in `/datapack list` and the startup log.
- **CER balance and heat visuals (UI-Energy + UI-MBS)** — the compact reactor
  now pushes **100 ⚡/s** instead of 1000 (UI-MBS.toml
  `nuclear_reactor.energy_per_second`, fallback updated) and the coal
  generator runs at **10 ⚡/s** (`energy.generator.fuel_burn_ticks` 100 → 200:
  one fuel item still yields 100 ⚡ but burns for 10 s). Overheat particles
  emit **8** particles per stage instead of 4/4/2 and use an explicit
  **0.1** speed, so the plume no longer looks like a fast jet.
- **CER network gate moved before the redstone gate (UI-Energy)** — the
  reactor checks the network for free storage **first**
  (`EnergyFlow.hasSpace`) and reports `OUTPUT_BLOCKED` ("network is full —
  the reactor is switched off") without touching fuel or the redstone state;
  the redstone gate on the center block is now the second check. A saturated
  network therefore stops the reactor (no fuel burn, passive cooldown) and it
  restarts automatically once a battery has room again.

### Fixed
- **Battery modes no longer flip on their own while charging** — the mode was
  polled from `Block#isBlockPowered()` / `isBlockIndirectlyPowered()` with a
  rotating per-tick scan, so indirect/weak power from neighbouring circuits
  looked like a new rising edge and the cluster cycled Charge → Both →
  Discharge on every charge cycle (with a chat message each time). Mode
  cycling is now driven by the real `BlockRedstoneEvent` of the redstone
  component next to the battery (lever, button, dust, torch, plate): only a
  genuine 0 → &gt;0 edge counts, and a 10-tick per-cluster debounce collapses
  the many events of one dust line into a single cycle. Nothing else can
  change the mode.
- **Structures vanished from the DB after every restart** — `purgeOrphaned`
  was called by the battery, lamp and magnet managers with only *their own*
  UUID set, so each startup deleted the markers of every other structure type
  (cables, generators, reactors, magnets, lamps) from `structure_markers` and,
  through the chunk-tracker rewrite, dropped their force-loaded chunks. The
  purge is now scoped to one marker type (`purgeOrphaned(type, uuids)`), so a
  battery rebuild no longer wipes the cables or the reactor.
- **A bloated config file stalled the whole server startup** — a runaway
  writer could grow a TOML (observed: 9 MB) and toml4j ground through it on
  the main thread for minutes. Every config read (salvage health check +
  per-addon load) now runs under `config.read_timeout_sec` (new key in
  UI-Core.toml `[config]`, default 5s, clamp 1..300): a read that exceeds
  the budget is abandoned on a daemon worker, the file is treated as
  broken — backed up as `UI-<Addon>-broken-<N>.toml` and regenerated from
  the bundled template — and startup proceeds. The first read of
  UI-Core.toml itself always uses the built-in 5s default (the configured
  value applies from that file onwards, including `/ui reload`).
- **Energy never flowed in a generator → wires → consumer network without
  a battery** — cables are pure routers, the generator pushed into
  batteries only and consumers (the reactor's case junctions included)
  pulled from batteries only: with no battery in the network nothing
  worked end to end (the generator "produced nothing", the reactor
  "did not accept energy"). The generator's furnace is now a
  GENERATOR-typed network node with a small leftover buffer
  (`energy.generator.buffer_capacity`, default 2000): `push` charges
  batteries first, the rest lands in the buffer, and any consumer pulling
  through the network drains it — a direct generator → wires → reactor
  setup works without a battery. The node is RAM-only and rebuilt lazily;
  the multimeter/blink show the transfer even when energy lands in the
  buffer.
- **Generator placement required a cable in place** — a generator placed
  before its cable stayed inactive forever (until a restart re-scan).
  Registration no longer requires a nearby cable: generation itself skips
  generators without a connected node, and a cable placed later connects
  the node automatically.
- **The generator dismantled message was hardcoded Russian** — routed
  through `StructuresMessages` with an English default
  (`generator.dismantled`, configurable in both language tabs).
- **The generator ignored redstone** — it burned fuel and produced energy
  around the clock. Now the generator works ONLY while its blast furnace
  block is powered by redstone: unpowered means no fuel consumption and no
  energy output, the burn timer freezes (the burning fuel resumes where it
  stopped when power returns) and the furnace shows the state visually
  (lit/unlit). No on/off chat messages, by design. The SHIFT+RMB info now
  shows the power state as a separate line.
- **Generator messages were hardcoded Russian** — every player-facing
  string (item name/lore, SHIFT+RMB info, assembly) now goes through
  `StructuresMessages` with hardcoded ENGLISH defaults and is configurable
  in the composite config under
  `messages.structures.generator.*` / `messages_en.structures.generator.*`
  (UI-Other.toml: RU and EN tabs).
- **Every reactor sign demanded the multimeter** — `onSignClick` intercepted
  ANY wall sign inside the reactor footprint (including the roof lamp
  labels like "Trigger Action") and demanded a multimeter. Per the design:
  at assembly the reactor now registers exactly the 8 control-panel DATA
  signs (ReactorDisplay offsets, DB table `reactor_panel_signs`, RAM cache)
  and clears the registration at disassembly (`setReactorLocation(null)`);
  the registry is rebuilt from the live reactors at startup (stale rows of
  reactors removed while the server was down are wiped, reactors built
  before the registry self-heal on load). Only registered signs cancel the
  click and demand the multimeter now; all other signs keep their normal
  vanilla behavior.
- **DFC went mute after one rejected startup pulse** — the laser latch
  (`started`) was set in `ReactorLasers.tick()` BEFORE calling
  `onTriggerPulse()`, unconditionally. With the startup energy gate, a
  rejected pulse (empty buffer) left `started = true` with no startup and
  no shutdown running, so every following Trigger Action pulse was routed
  to `tryManualShutdownTrigger()` — which silently ignores a non-WORKING
  shield. The reactor stopped reacting entirely: no gate message, no
  startup. The latch now moves into `onTriggerPulse()` and is set only
  when the startup is actually accepted; a rejected pulse keeps
  `started = false`, so every retry reaches the gate and its message.
- **DFC broadcasts had no anti-spam** — identical messages are now
  throttled per reactor with a 3-second cooldown (keyed by the final
  message text, so a changing value like the buffer charge readout still
  updates). The cooldown map is cleared on reset.
- **Multimeter was silent on every energy block** — `MultimeterListener`
  (reading cables, junctions, batteries, generators and the accelerator
  engine by right-clicking them with the multimeter) existed but was never
  registered by any plugin: dead code, the item did nothing outside the
  reactor sign check (which has its own hand check and worked). Registered
  in SimpleModules next to the other general listeners, and the item check
  is now hand-aware (`e.getItem()` — the multimeter works from the off
  hand too; `PlayerInteractEvent` fires per hand).

### Added
- **DFC case passive cooling** — the case no longer traps heat forever: it
  sheds `case_cool_rate` C*/sec (new key in UI-MBS.toml `[reactor]`, default
  3.0) scaled by the build-site humidity multiplier — the same roll as the
  core's natural cooling (legacy reactors multiply by 1.0; plains 0.5 →
  1.5 C*/sec, jungle 0.9 → 2.7). The cooling runs even with the reactor
  off: a hot case now coasts down to the lower temperature limit instead of
  freezing above the decay threshold until the next fusion run. The base
  rate is auto-clamped in the config loader so that even at the max
  humidity multiplier the cooling stays below the working-point (100%)
  fusion heat (`case_heat_rate` × ⅔): a running reactor at ≥100% speed
  always nets heating and the decay/maintenance loop stays reachable
  (breakeven with the defaults: fusion below ~30% speed loses to max-
  humidity cooling). Breach venting is unchanged — broken panes still vent
  far faster than passive cooling.
- **DFC post-maintenance cooldown** — saving a critical pane now starts a
  cooldown before a new pane can be marked critical:
  `case_maintain_cooldown_sec` (new key in UI-MBS.toml `[reactor]`, default
  10, 0 = off). Decay waves arriving while the cooldown runs wait it out —
  held exactly like the one-window-at-a-time rule: no new marks and no
  breaks until it expires, then the next wave starts a fresh window. The
  cooldown restarts on every successful save and is transient (not
  persisted, cleared on reset).
- **DFC case maintenance windows** — integrity decay no longer snaps panes
  blindly: a decay wave marks ONE random intact pane critical for
  `case_maintain_window_sec` (new key, default 10) — CRIT particles ramp
  at the block (radius 0.25, the count grows 1..10 with each elapsed
  second) and the reactor broadcasts "DFC Case requires maintenance."
  (`case_maintenance_required`, both language tabs) within the normal
  30-block audience. Exactly one window runs at a time — while a pane is
  pending its verdict, further decay is held (no new marks, no breaks).
  Saving the pane: right-click it with an iron ingot (main hand) — 1
  ingot is consumed, the pane stays, no integrity loss. A pane whose
  window expires snaps (degradation-style, no drop) and the case
  ESCALATES: while it is not fully repaired again, further decay points
  snap random panes instantly, without maintenance windows. Full repair
  resets the escalation and any pending crits.
- **DFC case breach bleeds fusion particles** — a breached case lets the
  accumulated fusion particles (P) escape through the broken glass:
  `case_breach_leak_per_pct` particles/sec per 1% of broken case glass
  (new key, default 10 — a 1% breach leaks 10 P/sec, a fully broken case
  drains the core in moments), with a small END_ROD burst streaming out
  of a random breach cell. A core drained dry gets its fusion throttled
  instead: fusion speed loses `case_breach_speed_penalty` points per 1%
  broken glass (new key, default 1 — e.g. a 1% breach runs fusion at
  −1%), floor 0; a fully broken case grinds fusion to a stop. The
  effective speed replaces the raw one everywhere — case heating, spawn
  rate and the signs/stats simply show the reduced values.
- **DFC case repair costs an iron ingot** — a pane placed into a cold case
  is only counted when the player holds an iron ingot in the OFF hand
  (the main hand holds the glass): one ingot per pane is consumed on
  placement (`reactor.case_repair_iron_cost`, new key, default 1; 0 =
  free repairs). Without the ingot the pane lands but does NOT count —
  after 1 second it pops off naturally (datapack loot table drops the
  glass back), unless the ingot appears in the off hand within the grace
  window, then it is consumed and the repair counts. The heat gate keeps
  priority: a hot case reports the heat first and never consumes ingots.
  The former hot-repair gate is generalized into the repair-failure gate
  (`ReactorCase.onPaneRepairFailed`, verdict re-checks heat first, then
  the ingot); only player placements are gated — event-blind placements
  (plugins/commands) remain plain audit-tracked repairs.
- **DFC case hot-repair gate** — a glass pane placed into the case while it
  is hotter than `reactor.case_repair_max_temp` (new key, default 1000 C*)
  no longer counts as a repair: the cell is ignored by the damage tracker
  (no integrity gain, no breach-message flicker) and its verdict runs after
  1 second — still hot → the pane pops off naturally
  (`breakNaturally`, datapack loot table → the glass item drops back out);
  cooled below the threshold in time → the pane holds and is counted as a
  real repair. The player is told on placement
  (`case_repair_too_hot`, both language tabs). Repairs into a cold case are
  unaffected; the admin/DB `repair()` path bypasses the gate as before.
- **DFC startup energy buffer** — the reactor now has its own internal
  energy buffer (`reactor.startup_energy`, default 5000 ⚡) charged through
  the case junctions: every `WAXED_CHISELED_COPPER` case cell connected to
  the cable network (the block itself registers as a network junction when
  placed; verified against `darkfusionreactor.nbt` — the whole case wall is
  waxed chiseled copper, the copper bars are not) pulls energy into the
  buffer at 250 ⚡/s via the unified `EnergyFlow`. A startup (Trigger Action
  lamp pulse on a fresh core) requires a FULL buffer: otherwise the startup
  is blocked with a "Not enough energy to start the reactor! Buffer:
  %have%/%need% ⚡" broadcast (new `startup_no_energy` message key). A
  successful startup consumes the whole buffer. The buffer persists in the
  new `energy_buffer` `reactors` DB column (auto-migration; flushed every
  5s while charging, immediately on startup/disassemble, restored on load).
   One pulse per second drives the charging (ReactorTask `ENERGY_BUFFER_INTERVAL`).
- **DFC humidity-scaled natural cooling** — the passive core decay (previously
  a hardcoded 1 C*/tick = 20 C*/sec) is now configurable and scales with the
  build-site biome humidity: at assembly the reactor rolls a cooling
  multiplier = biome humidity (`Block#getHumidity`, live from the climate —
  datapack/custom biomes work; a biome without a humidity value falls back
  to the configured default) + offset, clamped to [min .. max]. The
  multiplier is frozen at build time (a disassemble + rebuild rolls a fresh
  one) and scales the live-tunable base rate: plains (humidity 0.5) →
  20 × 0.5 = 10 C*/sec, jungle (0.9) → 18, desert (0.0) → 0. Legacy reactors
  (built before the mechanic) keep the plain rate until rebuilt. The rolled
  multiplier persists in the new `natural_cool_mult` `reactors` DB column
  (auto-migration; −1 = legacy). New config keys (UI-MBS.toml `[reactor]`):
  `natural_cool_rate` (20.0 C*/sec), `natural_cool_multiplier_offset` (0.0),
  `natural_cool_biome_fallback` (0.5), `natural_cool_multiplier_min` (0.0),
  `natural_cool_multiplier_max` (1.0).

### Changed
- **DFC case vent: temperature drains 10× slower on a partial breach** —
  only the pressure should drop hard when a pane pops: while ANY pane
  remains, the case temperature vents at a tenth of the former rate
  (0.05 × case_temp_max per second at a 100% breach fraction, down from
  0.5), so the fusion heat rise now outpaces a small breach's drain — a
  lightly holed case keeps heating instead of free-cooling to ambient
  (that free cooling was the exploit). A COMPLETELY broken case has no
  containment at all: its temperature drains at the former full rate and
  trends back to the lower limit within a second or two.
- **Energy network rework (ui-energy)** — the wires/electricity system is
  normalized around a single traversal service:
  - **`EnergyFlow`** (`transfer/cable`) is now the one and only BFS of the
    cable network. Producers (generator, reactor), consumers (lamp, electric
    furnace, MBS bridge, particle accelerator) and the balancer all go
    through its `push`/`pull`/`hasEnergy` — instead of five copy-pasted
    traversals with conflicting rules. Unified rules: cables are pure
    routers, the BFS always traverses THROUGH batteries (a full or
    mode-blocked battery no longer dead-ends the path), battery modes are
    respected on both ends, traversals are node-capped, and moved energy
    marks the cables (blink + multimeter readings).
  - **Battery energy now persists** — new `energy_storage` SQLite table,
    flushed every 10s and on shutdown/soft-reload, restored at startup.
    Previously every restart or `/ui reload` silently emptied all batteries.
  - **The reactor can actually output energy again** — its generation block
    pushed energy into CABLE nodes, where `CableNode.addEnergy` is a no-op
    (cables don't store), and the leftover went into a GENERATOR buffer
    nothing consumed. Output now goes through `EnergyFlow.push` into the
    network; unaccepted energy is lost visibly instead of vanishing into a
    dead-end buffer.
  - **One balancer instead of two** — `BatteryDrainTask` (a second balancer
    that pushed energy into the first battery with free space, ignoring
    charge modes, and fought the real balancer) is removed; its job is
    merged into `EnergyBalancerTask`, which now walks connected components
    via `EnergyFlow.collectBalanceGroup`.
- **Battery capacity is consistent** — cluster capacity is
  `blocks × energy.battery.max_energy` (the same per-block limit the nodes
  enforce) instead of the contradictory `blocks × 1000` hardcode, so the
  charge percentage display is correct. New battery nodes get their
  capacity at creation (previously up to 2s of silently swallowed energy
  after placement).
- **Cable blink uses real ticks** — `energy.cable.blink.off_ticks`/
  `on_ticks` are actual ticks now (the visual pass runs every 5 ticks;
  before they counted 40-tick task runs). Flow marks auto-expire (50t
  hold) instead of being force-cleared each visual pass.

### Added
- **Structure forbidden blocks** — a general mechanic for ALL NBT structures:
  a material from the forbidden list fails the check no matter where in the
  structure's full footprint it stands (a solid or an air cell, inside or
  outside the air-enforcement zones — a chest parked far from the core
  chamber is still rejected). Reported as a distinct "forbidden <block> at
  [coords]" fix (new `fix_forbidden` message key) in a separate
  `MatchResult.forbidden()` list; the match percentage counts them as
  mismatches. Global list: water, lava, TNT, pistons (they destroy any
  multi-block). Per-structure developer overrides
  (`StructureTemplate.FORBIDDEN_OVERRIDES`, not a user config):
  darkfusionreactor additionally forbids containers (chest/trapped/ender,
  hopper, dropper, dispenser, furnace family, brewing stand, all shulker
  boxes — they clash with the fuel/debris barrel systems and hide items
  inside the case) and the observer (wired against the roof bulbs it could
  auto-pulse the Trigger Action lamp — the reactor is player-operated).
- **DFC dynamic lower temperature limits** — every reactor rolls its own
  lower temperature limit at build time from the build-site biome:
  `limit = biome temperature × reactor.temp_limit_biome_multiplier +
  reactor.temp_limit_offset`, rounded to whole C* (desert 2.0 × 10 = 20).
  The value is read live from the biome holder (`Block#getTemperature`),
  so datapack/custom biomes work; a biome without a temperature value
  falls back to `reactor.temp_limit_biome_fallback` (default 1.0). The
  limit is per-reactor and persists in the new `temp_min` DB column;
  a disassemble + rebuild rolls a fresh one (legacy reactors keep −273
  until rebuilt). Every lower-limit check uses it: the core cooling
  floor, the stall point (the automatic shutdown now starts at the limit
  instead of absolute zero), the shutdown heat dump target and the case
  venting ambient floor. New config keys (UI-MBS.toml `[reactor]`):
  `temp_limit_offset` (0.0), `temp_limit_biome_multiplier` (10.0),
  `temp_limit_biome_fallback` (1.0).

### Removed
- **Dead energy config keys** — `energy.cable.enabled`, `energy.cable.max_energy`,
  `energy.cable.log` (cables no longer store energy; `CableLossTask` deleted),
  `energy.battery.discharge_per_tick`, `energy.battery.smooth_charge.*`,
  `energy.battery.visual.particles_enabled`, `energy.battery_drain.*` (the
  drain task is gone) and `energy.balancer.include_batteries` (never read).
  Removed from the code, ConfigRules and the UI-Energy.toml template; keys
  left in live server configs are simply ignored.
- **Dead config keys `reactor.core_temp_min` and `reactor.case_temp_min`**
  (default −273) — they have never affected anything: the floors were
  hardcoded and are now the dynamic per-reactor lower temperature limit.
  Removed from the code, ConfigRules and the UI-MBS.toml template; keys
  left in live server configs are simply ignored.
- **DFC meltdown restabilization** — a depleted shield no longer detonates
  instantly. The emergency systems deploy an auxiliary shield (100%
  integrity draining linearly to 0% over the T-3m window — the drain is
  visible on the Shield Stats sign; at 0% the detonation becomes
  unavoidable): explosion and fire in the core, the systems
  analyze, smoke vents and the multi-line WARNING block announces the
  "Emergency Termination Sequence". The stabilizers crank to 200% and the
  control locks (the lamps still click, nothing responds). The operators'
  second chance: netherite items in the content barrel (the floor barrel
  the fusion debris lands in) — the success chance is summed per item
  (ancient debris / netherite scrap +1%, netherite ingot +2%, netherite
  block +3%; other items are ignored, stacks count fully) and capped by
  the new `reactor.restab_chance_max` config key (default 99, range 0–100)
  — the best possible attempt is never certain. A long reactor run
  naturally accumulates debris in the barrel, so a no-cargo attempt can
  work. The attempt is the Trigger Action lamp pulse: the netherite items
  are consumed, the systems run their calibration sequence and roll the
  captured chance. Success →
  the automatic shutdown procedure runs and the reactor powers
  down. Failure (its own three-report sequence) or an expired aux shield →
  the detonation becomes unavoidable (the shield FAILED T-10s countdown).
  The whole phase machine persists across restarts.
- **DFC restabilization sequence extension** — two more reports between the
  shield re-creation attempt and the roll: "Estimating chance of successful
  shutdown..." (5s after the re-creation attempt) and "Awaiting reactor
  systems to respond..." (5s after the estimated chance) — the roll now
  happens 30 seconds after the attempt starts instead of 20.
- **DFC explicit manual/automatic final shutdown procedures** — the final
  shutdown phase (power lasers → heat dump → stabilization lasers →
  absorber valve → shield ramp → offline) is one shared chain entered
  through two explicit trigger kinds. Only the announcement differs:
  "due to manual trigger" (the operator lamp pulse on a running reactor)
  vs "due to automatic trigger" (the −273 C* reaction-failure stall and
  the restabilization success — previously announced as "due to reaction
  failure"). The restabilization success shutdown switched from the manual
  to the automatic procedure. The heat dump now runs for every trigger
  (behaviorally identical: the −273 C* stall passes through instantly).
  The trigger kind persists in the new `stall_trigger` column (legacy rows
  fall back to the old `stall_manual` flag), so the "Saved from extinction"
  advancement still counts only the genuine −273 C* emergency stall.

### Changed
- **DFC stall: the reaction-failure warning precedes the shutdown** — the
  "Core reaction failure due to critically low temperatures, reaction stall
  imminent!" warning moved from the 0 C* crossing to the lower-limit
  crossing (so it fires on every reactor, whatever its dynamic limit is),
  and the automatic shutdown now starts 5 seconds after it, together with
  the "Operator Incompetence" advancement (previously both fired instantly
  at the 0 C* crossing, one shutdown-length before the stall itself).
- **DFC assembly ignores foreign blocks outside the core chamber** — the
  NBT template check no longer fails on blocks standing in template air
  cells (interior decor space, the space above the roof): outside the
  functional core they cannot interfere. The core chamber is still
  enforced — a sphere of 3 blocks around the core center (the same
  (0.5, −4.5, 0.5) point the particle systems use): a foreign block
  inside it fails the assembly with the usual break instruction.
  Implemented as per-template air-enforcement zones in
  `StructureTemplate.checkAt` (templates without zones keep the full
  air matching); the zones also apply to the SHIFT+RMB structure scan
  and the closest-match hints.
- **DFC aux shield integrity warnings** — the draining auxiliary shield
  broadcasts once per downward threshold crossing at 75 / 50 / 25 / 10%:
  "Warning! Auxiliary shield integrity at N%!".
- **DFC advancement "Critical overload"** — granted when the meltdown
  restabilization phase starts (the shield just died, the T-3m window is
  open). Goal frame, heart of the sea icon, 75 XP, parent
  `ui:datapack/dfc_unstable`.
- **DFC advancement "Operator Incompetence"** — granted when the stall
  shutdown starts: the core reached the reactor's dynamic lower temperature
  limit, the "reaction failure, stall imminent" warning fired first and the
  automatic cooling shutdown follows 5 seconds later (together with the
  advancement). Goal frame, ice icon, 50 XP, parent
  `ui:datapack/start_dfc`.
- **DFC advancement "Self-destruct protocol"** (rework of "Fading signals",
  same `dfc_self_destruct` file) — granted at the T-60s announcement, after
  the reactor systems finish rebooting (sensors down → controls down →
  "Internal Dark Fusion Reactor Systems initiated a self-destruct protocol").
  Challenge frame, red terracotta icon, 150 XP.
- **DFC advancement "Senior ReactorOps"** — keep the shield integrity
  within 0–10% for one minute straight. Entry requires deliberately
  overdriving the core past ~22M C* (shield stress above 100%); hold the
  band by balancing stress around the limit — a step to 0% is the
  detonation, to 11% resets the progress. Challenge frame, spectral arrow
  icon, 300 XP, parent `ui:datapack/dfc_unstable`.
- **DFC advancement "Is it really that simple?"** (hidden) — pulsing the
  startup lamp during the self-destruct timed countdown gives a 0.5% chance
  per rising edge that the systems malfunction the protocol away: it
  disarms, the reactor powers down (controlled shield ramp, lasers off)
  and can be started again later. The protocol is not gone for good: it
  can roll again on subsequent startups at a halved chance (0.5% instead
  of 1% on the default config) until the reactor is disassembled. Too late
   once the detonation procedure is announced. Challenge frame, redstone
   torch icon, 200 XP, parent `ui:datapack/dfc_self_destruct`.
- **DFC advancement "Saved from extinction"** — granted when the emergency
  (stall) shutdown procedure completes successfully: the core coasts down,
  the systems step off one by one and the reactor is marked offline,
  awaiting a new startup. Covers both the automatic shutdown (reaction
  failure at −273 C*) and the manual trigger shutdown. Challenge frame,
  totem of undying icon, 250 XP, parent `ui:datapack/operator_incompetence`.

### Changed
- **The reactor lamp is officially the "Trigger Action" lamp** — one lamp,
  context actions: startup, manual shutdown, meltdown restabilization
  attempt, self-destruct cancel. Renamed across the code (`LAMP_STARTUP` →
  `LAMP_TRIGGER`, `onStartupPulse` → `onTriggerPulse`, comments and the
  WARNING hint line).
- **Roof lamp signs are now static white labels** — the aqua live-power
  readouts on the nine lamp signs are gone (the values live on the Power
  Stats / Shield Stats panels). Every 10 seconds the plugin rewrites the
  full label text taken from the NBT template (titles + "+5% Power" etc.),
  with the "Core Startup" label renamed to "Trigger Action / Startup /
  Shutdown / Restab. / Cancel" — a broken, replaced or wiped sign heals
  itself on the next pass.
- **DFC case messaging consolidated to exactly three events** — "DFC case
  breach detected!" (the first pane breaks), "DFC case failure detected!"
  (the case breaks completely — the integrity shatter or every glass cell
  destroyed by any cause; a full physical destruction now actually breaks
  the case instead of leaving it officially intact with zero glass) and
  "DFC case breach restored." (fully repaired). The 30-second broken-case
  reminder and the 10-second "Case glass is broken!" warning are gone, as
  are the per-pane "(left/total)" glass damage/repair reports.
- **A breached DFC case now vents** — every missing glass cell drains the
  case pressure at (broken/total) × 100% of the max pressure per second and
  the case temperature at half that rate (floor −273 C*): a holed case can
  no longer hold its pressure and temperature indefinitely while the glass
  is partially broken. Any breach outruns the fusion heat rise, so a vented
  case trends toward ambient until repaired.
- **Advancement tree regrouped into clear lines** — the "Research" branch
  (People of the Past → High Temperatures) no longer trails into tech
  crafts: the Photon Cannon ("Light Attack") and reinforced deepslate moved
  to the "Technology" branch, which is now one continuous craft progression
  ending in the two warp-fungus weapons (Photon Cannon → shoker).
- **Advancement icons reworked to match their content** — Critical overload
  (heart of the sea → netherite ingot, the restabilization currency), The
  power of fusion (emerald → ancient debris), Senior ReactorOps (spectral
  arrow → shield), Destructive Consequences (redstone → creeper head, the
  meltdown literally detonates a creeper), Vaporised in the plasma
  (redstone block → blaze powder), In the Depths of Hell (nether wart →
  lava bucket), Ah, where the oxyden? (redstone → bubble coral), The space
  (ender pearl → firework star, the rocket theme), Where is the Earth's
  core here? (firework star → magma block), OutOfMemoryError (structure
  void → shulker box, "memory full"). This also removes the stray
  redstone/redstone_block duplicates in the tree.
- **Advancement XP rebalance (fairness pass)** — rewards now match the
  actual effort: "Kaboom!" 100 → 200 (a 1000+ damage single mace hit is a
  real challenge), "Suicide" 100 → 25 (one command), "java.lang.
  OutOfMemoryError" 100 → 150 and "The server has not responding!" 100 →
  150 (the rarer server incidents outscale the common ones), "Saved from
  extinction" 200 → 250 (a full emergency shutdown procedure, level with
  "Vaporised in the plasma").
- **DFC advancement "One-time heating" renamed to "Vaporised in the plasma"**
  (`one_time_heater` → `vaporised_in_plasma`, datapack file and code) — the
  trigger is unchanged (be inside the DFC core when it explodes); new title
  and description.
- **Emergency core shutdown no longer rescues a deliberately overloaded
  shield** — the integrity-below-threshold safety only fires while the
  shield stress is within limits (≤100%): an operator-driven overheat is
  the operators' responsibility. This opens the 0–10% integrity window the
  "Senior ReactorOps" advancement requires; cooling down below the
  threshold integrity brings the safety back.
- **All DFC advancements now require proximity** — grants go only to players
  within 30 blocks of the DFC structure center (previously a mixed bag of
  everyone-online and a 20-block radius). The now-unused
  `reactor.selfdestruct_adv_radius` config key was removed.
- **DFC advancement XP rebalance** — every advancement got its own reward
  instead of flat 1 XP: dfc_unstable 10, inside_dfc 20, start_dfc 25,
  burn_inside_dfc 40, explode_dfc 75, detonation_irreversible 60,
  power_of_fusion 100, dfc_self_destruct 150, one_time_heater 250.

### Changed
- **The "linux" chat channel is renamed to "terminal"** — `/ui chatchnl terminal`,
  enum `ChatChannel.TERMINAL`, permission `ui.chat.channel.terminal`, config
  section `chat.channels.terminal.*` and log prefix `[TerminalChat]`. The name
  never matched reality (the channel runs PowerShell on Windows), so it now
  says what it does. The config loader falls back to the legacy
  `chat.channels.linux` section when the new one is absent, so existing
  configs keep working until renamed manually.

### Removed
- **The "Detonation irreversible" advancement** — granted at the start of the
  T-10s detonation countdown, which duplicated "Destructive Consequences"
  (the same meltdown, just 10 seconds apart). Removed along with its grant
  hook (`onShieldFailureCountdown`); the blast itself is still the moment
  that counts. Its TNT icon moved to "Destructive Consequences".
- **The dead "High-Tech Alloys" advancement (`craft_nethered_block`)** — its
  criterion was `minecraft:impossible` and nothing in the code ever granted
  it, which also made the whole subtree under it unobtainable (children are
  not tracked until the parent completes). "Discharge!" (`craft_shoker`) now
  hangs directly under "The force of decay" (`react_with_antimatter`).

### Fixed
- **The reactor warning ping no longer loops on a breached idle case** — the
  0.5s integrity ping in `ReactorDisplay.tickSound` fired for a case
  integrity below 100% regardless of the shield state, so any breach pinged
  forever even on an offline reactor. The case-integrity condition is gated
  on a WORKING shield, same as the shield-integrity one.
- **"Saved from extinction" is no longer granted for every shutdown** — the
  grant in `finishStallShutdown()` fired for the manual trigger shutdown
  and the restabilization success shutdown too. Only the EMERGENCY shutdown
  counts now: the automatic stall procedure started by the reaction failure
  (core coasting down to −273 C*). The manual/restab paths leave the
  advancement untouched. The manual-vs-emergency flag is now persisted
  (new `stall_manual` reactors column): previously a restart during a
  manual shutdown that had already passed its heat-dump phase lost the
  flag and the shutdown completed as a phantom "emergency" one.
- **The `dfc_unstable` advancement is now obtainable** — its grant check
  (`ReactorManager.checkDfcUnstable`) was defined but never called, so the
  parent of the whole DFC advancement branch could never fire. It is now
  checked every tick with the running core: the first shield or case
  integrity drop below 100% while the core is active grants it (once per
  reactor).
- **Reactor broadcasts reach the advancement grant radius** — event
  messages were sent only within 15 blocks of the anchor while all DFC
  advancements grant within 30, so a player could earn an advancement
  without ever seeing the warning that caused it (e.g. the T-10s
  detonation "run" message). Broadcasts now use the same 30-block radius
  (new `BROADCAST_RADIUS` constant).
- **The legacy `core_case_temp/press/int` DB columns are no longer
  written** — the persistence DTO carried a dead inner-case stat trio that
  was always saved as 0/0/100 and ignored on load (the live case lives in
  `case_broken/temp/press/int`). The columns remain in existing databases,
  just unused.
- **Dead code cleanup in the reactor package** — `ReactorState` trimmed to
  the fields persistence actually uses (the unused smoothed-display
  values, tick counters and the inner-case trio are gone), a stray `;`
  after a method in `ReactorCase`, stale spin comments fixed (the core
  spin target is 95 000 RPS at the working point, not "0.95 RPS").

- **Host terminal channel on Windows** — commands ran through `cmd.exe /c`
  (no `ls` and no PowerShell aliases → exit 1) and the output was read as
  UTF-8 while cmd writes the OEM codepage (CP866 on a Russian locale),
  garbling non-ASCII output. Commands now run through
  `powershell.exe -NoProfile -NonInteractive -Command` with the output
  encoding forced to UTF-8.

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
