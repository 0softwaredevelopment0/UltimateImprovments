# UI-Other Split Plan (9 addons)

> Internal working plan for decomposing `ui-other` into 9 separate addon JARs.
> Status: **Phases 0-2 + 3a done** (UI-Enchant, UI-Auth, UI-Protection extracted).
> One family version (see AGENTS.md). Update as phases complete; delete when `ui-other` is gone.
>
> **Progress**
> - 2026-09-28 — Phase 0: integrity → ui-shared; `CoreHooks`; enchant decoupled. Green.
> - 2026-09-28 — Phase 1: **UI-Enchant** (`428d95f5`). Phase 2: **UI-Auth** (`bc8f4d2c`).
> - 2026-09-28 — Phase 3a: **UI-Protection** (`063d8b03`) — `mechanics/protection/**`,
>   `ProtectionSubcommand`, `VoidProtectionListener` (package → `…mechanics.protection`),
>   `ProtectionModule`, `config/UI-Protection.toml`; routes `protection`/`void_protection`.
> - Next: **Phase 3b — UI-World + UI-Items** (classify `features/world/*` into world vs
>   custom-item managers; crafting listeners depend on those). Then UI-Player,
>   UI-Display, UI-Guard, UI-Admin. Notes:
>   - `RecipeRegistry` already lives in ui-shared (crafting is not a blocker).
>   - `VanishManager` is referenced by `TabManager` (display) and `MiscSubcommand`
>     (command) → hoist to ui-core or add a hook before extracting UI-Player.
>   - `StructureIntegrityManager` is referenced by `MultimeterListener` / `EnderChestManager`
>     (scanner/items) → keep with UI-Items (or hoist).

## 1. Current state (metrics, 2026-09-28)

- `ui-other`: **~289 Java files**, 1 giant `module/SimpleModules.java` (**1760 lines**),
  1 `ui-core/src/main/resources/config/UI-Other.toml` (**3709 lines, 32 sections**),
  **69 registered modules**.
- Biggest domains: `mechanics` 134, `enchantment` 56, `command` 51, `listener` 15,
  `server` 8, `op` 4, `display` 3, `economy` 5, `broadcast` 3,
  `mechanics/security` 30, `mechanics/features` 64, `mechanics/crafting` 24.
- `ui-other/build.gradle` `implementation`-depends on **all 11** other addons.

## 2. Infrastructure facts (how addons work today)

- **Addon host**: `ui-core` (`Main` / `PluginStartup`). Each addon is a Paper plugin with
  `plugin.yml` → `addon-for: UI-Core`, `depend: [UI-Core, ...]`.
- **Module system**: `ui-core` `com.ultimateimprovments.module.ModuleManager` +
  `PluginModule`/`SimpleModule`; an addon registers modules in its own `onEnable()`.
- **Config**: `CompositeConfig` routes every `get/set` by first path segment to the owning
  addon's `configs/UI-<Addon>.toml`; message groups routed by `messages[._en].<group>`.
  `AddonConfigManager.init()` iterates `AddonCatalog.catalog()`, generating missing files
  from bundled fragments at `ui-core/src/main/resources/config/UI-<Addon>.toml`.
  **Adding an addon requires** (all four):
  1. `AddonCatalog.catalog()` entry (load order);
  2. `AddonCatalog.route("UI-<Addon>", <rootKeys...>)`;
  3. `AddonCatalog.routeMsg("UI-<Addon>", <msgGroups...>)`;
  4. bundled fragment `ui-core/src/main/resources/config/UI-<Addon>.toml`.
- **Commands**: `CommandScanner.autoRegister(registry, plugin, "com.ultimateimprovments.command.subcommands")`
  scans **the calling plugin's own JAR**. So subcommands can live in any addon as long as
  that addon's bootstrap calls autoRegister with the same package prefix (it will only see
  its own classes). `CommandRegistrar`/`CommandScanner` base stays in core/shared.
- **Addon discovery/status**: `com.ultimateimprovments.addon.AddonRegistry`
  (`reportModules`, `reportError`) — keep the `reportModuleStats` call per addon bootstrap.

## 3. Target addons (9)

| # | Addon | Contents (packages) | ~files | TOML roots / msg groups |
|---|---|---|---|---|
| 1 | **UI-Enchant** | `enchantment/**` (18 charms), `command/subcommands/EnchantSubcommand` | ~57 | `enchant` (settings + msg group) |
| 2 | **UI-Auth** | `mechanics/security/auth/**` | ~17 | `auth` |
| 3 | **UI-Guard** | `mechanics/security/{sudo,codepanel,check,botprotect}`, `server/{PacketGuard,ProxyServerListener,RedstoneGuard*,ServerOverload*,EmergencyEntitiesKill}`, `maintenance` | ~28 | `sudo`,`codepanel`,`packet_guard`,`proxy_server`,`redstone_guard`,`bot_protection`,`server_overload_warning`,`emergency_entity_kill`,`maintenance` |
| 4 | **UI-World** | `mechanics/features/{world,blocks,collapse,movement}` (non-item managers), `mechanics/particle/**` + `particle_accelerator`, `block_friction`, `wireless_redstone`, `meteor`, `features/omniscanner` | ~45 | `features`,`meteor`,`wireless_redstone`,`particle_accelerator`,`block_friction` |
| 5 | **UI-Items** | `mechanics/crafting/**`, `mechanics/features/items/**`, `netherite_upgrade`, custom-item managers split out of `features/world` (see §5) | ~45 | item/craft flags, `netherite_upgrade` |
| 6 | **UI-Player** | `mechanics/features/player/**` (armor/trim effects, attributes, elytra boost, leash, mode-protect, shield slowness, vanish, join-invuln) | ~15 | `armor_effects`,`armor_trim_effects`,`vanish` |
| 7 | **UI-Protection** | `mechanics/protection/**`, `features/structure` + `structure_integrity`, `void_protection`, `command/subcommands/ProtectionSubcommand` | ~14 | `protection`,`structure_integrity`,`void_protection` |
| 8 | **UI-Display** | `display/**` (tab/scoreboard/bossbar), `features/updater`, `motd`,`changedimmension`,`brand_spoof`,`death_logger`, `command/subcommands/{ChgDim,Broadcast,ExecChat}` | ~16 | `tab`,`scoreboard`,`bossbar`,`motd`,`changedimmension`,`brand_spoof`,`death_logger` |
| 9 | **UI-Admin** | `op/**`, `server/{AccessListCheckTask,...}` not in Guard, `economy/**`, `features/scanner`, `swapjar`, `command/subcommands/{Op*,Deop,...}` | ~20 | `economy`, `[op]`-related |

Everything else not listed stays temporarily in `ui-other` until it is empty, then
`ui-other` is deleted. (Optional later: promote `UI-Economy` to its own 10th addon.)

## 4. Shared foundation to hoist BEFORE splitting

These are used across the target boundaries. Hoist to `ui-core`/`ui-shared` first:

1. **Integrity** — `mechanics/features/integrity/**` (`ItemDurabilityUtil`,
   `PiercingListener`, warn/cleanup listeners). Used by UI-Enchant, UI-World (Sunburn),
   UI-Player. → **ui-shared** (runtime utility), config `features.integrity.*` stays with
   whichever addon owns `features` (move config later or keep in core).
2. **`database/**`** (shared SQLite connector), **`util/**`**, `Keys`, `MessageUtil`,
   `ConsoleLogger`, `AddonRegistry`, `CommandRegistrar`/`CommandScanner` base
   → **ui-core** (already partly there).
3. **Cross-addon item registry**: if custom items are needed by several addons
   (crafting → items), keep a **public item/service facade** in ui-core or ui-shared and
   make addons depend on the interface, not on each other's classes.
4. **Dialog handlers / per-player cleanup** (`PlayerQuitCleanupListener`) — currently one
   central listener in UI-Other that touches auth/codepanel/sudo. Either keep a shared
   "state cleanup" registry in core, or give each addon its own quit cleanup.

## 5. Dependency hotspots (must be resolved before the cut)

- **crafting ↔ custom items**: `mechanics/crafting/*CraftListener` reference item managers
  that live in `features/world` (ConcreteBucket, EntityLocator, Waypoint, Antimatter,
  HeavyCore, Dosimeter, MetalDetector, Particle* , PlasmaCannon, …). **UI-Items must own
  both** the craft listeners and those managers; classify each `features/world` file as
  "world mechanic" vs "custom item" during Phase 3.
- **enchant ↔ datapack**: enchant JSON lives in `ui-datapack`; UI-Enchant must soft-depend
  on UI-Datapack (as UI-Other does today) and keep the `DatapackGate` skip logic.
- **UIOther.onEnable/initPostModuleSystems**: references `space.*` (UI-Shared), `op`,
  `maintenance`, `structure`, dialog handlers, check/auth/codepanel/sudo. Split this
  bootstrap method per addon; keep shared wiring in core.
- **Command registration (found during Phase 1 prep)**: `EnchantSubcommand` is NOT a
  `SubCommand` — it is a static utility registered manually in
  `ui-other/.../command/PluginReloadCommand.java` (line ~286) via
  `LegacySubCommandAdapter.of("enchant", EnchantSubcommand::execute, …)`. Phase 1 must
  move that registration into the UI-Enchant bootstrap (its own adapter) and drop it
  from `PluginReloadCommand`.
  `CommandErrors` (`com.ultimateimprovments.command.CommandErrors`) already lives in
  **ui-core** — fine. `CommandScanner`/`CommandRegistrar`/`ClassScanner` still live in
  `ui-other/core`; hoist them to ui-core if addons should self-scan, otherwise register
  each addon's commands manually in its bootstrap (simpler, chosen for Phase 1).
- **SimpleModules**: split into per-addon registrars (`EnchantModules`, `WorldModules`, …).
  Keep only truly cross-cutting registration in `SimpleModules` (or delete it).

## 6. Phases (each ends with build → stage(JAR) → commit → push)

- **Phase 0 — foundation.** Hoist integrity/database/util to shared; generalize
  `AddonCatalog` (add empty slots for the 9 new addons? no — add only when used); split
  `SimpleModules` into per-domain registrar classes *inside ui-other* first (no new modules
  yet); move shared bootstrap wiring out of `UIOther` into core helpers. Build green,
  behavior identical. **Commit.**
- **Phase 1 — UI-Enchant (prove the pattern).**
  1. New Gradle module `ui-enchant` (settings.gradle, build.gradle modeled on `ui-other`
     minus sibling deps, `plugin.yml` `addon-for: UI-Core`, softdepend UI-Datapack).
  2. `git mv` `ui-other/.../enchantment` → `ui-enchant/.../enchantment`;
     `EnchantSubcommand` → `ui-enchant/.../command/subcommands/`.
  3. New `UIEnchant extends JavaPlugin` bootstrap: register the enchant modules
     (moved out of `SimpleModules`), call `CommandScanner.autoRegister(..., "…command.subcommands")`,
     `reportModuleStats`.
  4. Remove the enchant `registerXxx` calls/methods from `UIOther`/`SimpleModules`.
  5. Config: create `ui-core/.../config/UI-Enchant.toml` with `[enchant]` + `[messages.enchant]`
     + `[messages_en.enchant]`; delete those from `UI-Other.toml`; update `AddonCatalog`
     (`catalog()`, `route`, `routeMsg`).
  6. Build all; fix imports (`EnchantSubcommand`, `SimpleModules`, any cross refs).
  **Commit.**
- **Phase 2 — UI-Auth** (+ its dialog handler + DB + quit cleanup).
- **Phase 3 — UI-World + UI-Items** (resolve §5 hotspot; biggest careful cut).
- **Phase 4 — UI-Player, UI-Protection, UI-Display, UI-Guard, UI-Admin.**
- **Phase 5 — delete `ui-other`** when empty; update README/GUIDE/CHANGELOG; Vault.

## 7. Verification per phase

- `./gradlew build` green for all modules; `shadowJar` produced for the new addon.
- New addon's JAR present in `build/libs/` and `build/distribution` on release.
- `AddonCatalog` routing: `/ui addon status` lists the new addon; `/ui reload <Addon>` works.
- Config: fresh run generates `configs/UI-<Addon>.toml`; keys resolve via `getConfig()`.
- Commands of the addon appear in `/ui help` / tab-complete.

## 8. Risks / invariants

- **One family version** for every addon (AGENTS.md).
- **No hard cross-addon deps** except on `UI-Core` (+ `UI-Shared`, `UI-Datapack` where
  needed). Shared types live in core/shared.
- **Load order** in `AddonCatalog.catalog()` matters (core first; datapack before enchant).
- **Data folder / DB**: shared `plugins/UltimateImprovments`, one DB — do not duplicate.
- **`getArmorContents()` copies** and similar pitfalls documented in the Vault still apply.
- Rollback: each phase is its own commit; the pre-split state is tagged by git history.
