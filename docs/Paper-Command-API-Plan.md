# Paper Command API Migration (commands)

> Move command registration off the legacy Bukkit `CommandMap`/`PluginCommand`
> onto Paper's command API (Brigadier + `LifecycleEvents.COMMANDS`).
> Status: **/ui done** (`Main`/`PaperCommands`); the rest is scoped below.

## Done

- `ui-core/.../command/PaperCommands.java`: registers the root `ui` (alias
  `ultimateimprovments`) through `getLifecycleManager().registerEventHandler(
  LifecycleEvents.COMMANDS, ...)`. The tree is now a **real Brigadier tree**:
  `literal("ui") [executes → help] .then(argument("sub", word())
  .suggests(<names/aliases>).then(argument("args", greedyString())
  .suggests(<registry tab-complete>)))`.
- The tree is **dynamic on purpose**: `sub` is a `word` (not a literal per name), so
  subcommands registered by addons *after* UI-Core's enable are still resolvable —
  nothing is enumerated at registration time. `sub` + the greedy tail are turned back
  into the legacy `String[]` and forwarded to `SubCommandRegistry.dispatch` /
  `tabComplete`, so **all subcommand handlers and permissions are unchanged**.
- `check`/`uncheck` are *not* special-cased with a typed `player` argument: the
  subcommand set is unknown at registration, so typing stays at the registry level.
- Registered from `Main.onEnable()` (UI-Core), guarded against re-registration across
  `/ui reload`.
- Removed the legacy `/ui` + `/ultimateimprovments` registration from
  `ui-other core/CommandRegistrar`.

## Still on the legacy CommandMap (deliberate)

`ui-other core/CommandRegistrar` still registers, via `getCommandMap()` reflection:

- troll fakes: `/forceop`, `/crash`;
- **vanilla overrides**: `/list`, `/stop`, `/restart`, `/msg`, `/tell`, `/w`,
  `/reply`, `/r`.

Reason: these **replace vanilla server commands**. Paper's Brigadier registration
adds nodes to the dispatcher and is not a drop-in replacement for replacing vanilla
commands; the legacy `CommandMap` override (unregister + re-register) is the
supported mechanism. Moving these is out of scope / not advisable without a
dedicated approach.

## Future (optional)

- Replace the greedy-string bridge with **one Brigadier node per subcommand** and
  typed arguments (`ArgumentTypes.integer/player/enchantment/...`) + auto
  suggestions. Bigger rewrite of the 51 handlers; not required for "use the Paper
  command API".
- Or adopt the third-party **CommandAPI** framework on top of Brigadier.

## Verification

Build green. Runtime must be checked in game: `/ui`, `/ui help`, subcommand
dispatch, tab-complete, and that no duplicate `/ui` command is registered.
