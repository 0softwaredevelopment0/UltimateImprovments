# Paper Command API Migration (commands)

> Move command registration off the legacy Bukkit `CommandMap`/`PluginCommand`
> onto Paper's command API (Brigadier + `LifecycleEvents.COMMANDS`).
> Status: **/ui done** (`Main`/`PaperCommands`); the rest is scoped below.

## Done

- `ui-core/.../command/PaperCommands.java`: registers the root `ui` (alias
  `ultimateimprovments`) through `getLifecycleManager().registerEventHandler(
  LifecycleEvents.COMMANDS, ...)`. The tree is
  `literal("ui") [executes → help] .then(argument("args", greedyString()).suggests(...).executes(...))`.
- The Brigadier node forwards the raw tail to `SubCommandRegistry.dispatch` /
  `SubCommandRegistry.tabComplete`, so **all 51 existing subcommand handlers are
  unchanged** — only the registration moved to Paper's API.
- Suggestion provider accounts for the greedy-string replacement rule (suggestions
  are prefixed with the already-typed text up to the last space).
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
