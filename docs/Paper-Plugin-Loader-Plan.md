# Paper Plugin Loader Migration Plan

> Migrate the whole UltimateImprovments family from the legacy Bukkit/Spigot
> `plugin.yml` to Paper's plugin loader (`paper-plugin.yml`). One family version.
> Status: **IMPLEMENTED** (`a63dd9d3`); runtime needs in-game verification.
> Delete when confirmed.

## Why

- **Classloading isolation**: each plugin gets its own classloader. This fixes the
  classpath pollution that caused the historical "duplicate `Main` class / `Main.getInstance()`
  null" bugs (the reason `shadowJar` already excludes sibling JARs).
- **Explicit dependency graph**: `dependencies.server` with `load`/`required`/
  `join-classpath` replaces the ad-hoc `depend`/`softdepend` + the custom
  `addon-for: UI-Core` marker (which required reading `plugin.yml` from the JAR).
- **Per-plugin libraries / loaders** become the supported way to ship deps.

## Key facts (Paper docs)

- `paper-plugin.yml` is required in the JAR (`plugin.yml` may coexist; Paper uses the
  paper manifest when present).
- A dependency entry: `{ load: BEFORE|AFTER|OMIT (default OMIT), required: bool (true),
  join-classpath: bool (true) }`. `join-classpath: true` = this plugin sees the
  dependency's classes; **the reverse is never granted**.
- Paper plugins cannot resolve **cyclic** loading (server refuses to start).
- `commands:` is not used by Paper plugins — commands must be registered from code
  (this project already registers everything in code, so no change).
- `PluginMeta` (API) exposes `getPermissions()`, so `permissions:` in
  `paper-plugin.yml` is supported.
- There is **no `libraries:` in `paper-plugin.yml`** (that is a legacy `plugin.yml`
  extension). Libraries are supplied via a `loader:` (`PluginLoader` +
  `MavenLibraryResolver`) — we avoid that by bundling argon2 (see below).

## What changes

1. Replace every module's `plugin.yml` → `paper-plugin.yml`.
   - Root `build.gradle` `processResources` must expand BOTH file names for
     `${project.version}`.
2. Dependency graph per plugin (see below) with `join-classpath: true` for every
   sibling whose classes the plugin references (the family shares core/shared classes).
3. **Argon2**: currently provided via `ui-core` `plugin.yml:` `libraries:` (legacy
   shared classloader). Under isolation it must travel with its consumers
   (`AuthDatabase` in UI-Auth, `SudoDatabase` in UI-Other) → make it an
   `implementation` dep in `ui-auth` and `ui-other` (bundled by shadow). Remove it
   from `ui-core`.
4. **Addon discovery**: `AddonRegistry` no longer reads the `addon-for` marker from
   `plugin.yml`. Identify an addon by membership in `AddonCatalog.catalog()`
   (case-insensitive, Core excluded). `registerExternal` still works.
5. Drop `addon-for`, `load`, `libraries` from manifests.

## Dependency graph (server section)

| Plugin | required (load BEFORE, join) | optional |
|---|---|---|
| UI-Core | — | PlaceholderAPI, LuckPerms, Vault |
| UI-Shared | UI-Core | — |
| UI-MBS | UI-Core | — |
| UI-Datapack | UI-Core | — |
| UI-Chat | UI-Core, UI-Punish | PlaceholderAPI, LuckPerms, Vault |
| UI-Clans | UI-Core | — |
| UI-Combat | UI-Core | — |
| UI-Punish | UI-Core | — |
| UI-Essentials | UI-Core | — |
| UI-Energy | UI-Core, UI-Shared, UI-MBS | — |
| UI-Anticheat | UI-Core | — |
| UI-Enchant | UI-Core, UI-Shared | UI-Datapack (load BEFORE) |
| UI-Auth | UI-Core | LuckPerms |
| UI-Protection | UI-Core, UI-Shared | — |
| UI-Display | UI-Core | — |
| UI-Admin | UI-Core | Vault, PlaceholderAPI, LuckPerms |
| UI-Player | UI-Core | — |
| UI-Guard | UI-Core | — |
| UI-Other | UI-Core, UI-Shared, UI-Energy, UI-Chat, UI-MBS, UI-Punish, UI-Clans, UI-Combat, UI-Anticheat, UI-Essentials, UI-Datapack | PlaceholderAPI, LuckPerms, Vault |

Optional entries: `required: false, join-classpath: true`.

## Order (no cycles)

`UI-Core` → `UI-Shared`/`UI-MBS`/`UI-Datapack`/leaves → `UI-Energy` (→ MBS/Shared) →
`UI-Other`. UI-Chat → UI-Punish. No plugin lists a dependent as a dependency, so the
graph is acyclic.

## Risks / open items (cannot be validated without a test server)

- Command registration: this project registers `/ui` via the **legacy** `CommandMap`
  from code (`SubCommandRegistry`/`CommandRegistrar`) — Paper plugins still expose the
  legacy CommandMap, but this is the highest-risk area; verify `/ui ...` in game.
- `join-classpath` correctness: a missing join silently yields `NoClassDefFoundError`
  at runtime; the graph above was derived from compiled import scans.
- `PlaceholderAPI`/`Vault`/`LuckPerms` are legacy plugins — Paper allows Paper↔Bukkit
  dependencies, so load order + join should work; verify PAPI expansion + Vault economy.
- The root `src/main/resources/plugin.yml` (old monolithic "UltimateImprovments") is
  not built by `settings.gradle`; leave it untouched.
- `load: STARTUP` (UI-Core/UI-Datapack/UI-MBS) was dropped — `paper-plugin.yml` has no
  such key; load order is expressed via dependencies. If UI-Datapack must install the
  datapack BEFORE worlds load, add a `bootstrapper:` later.

## Rollback

Each conversion is its own commit; reverting restores `plugin.yml`.
