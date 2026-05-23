# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MC-Plugin is a Minecraft Paper plugin that adds advanced features including an energy system, custom weapons, code panels, and enhanced loot tables. The plugin uses Java 21 and targets Paper/Purpur 1.21.11.

## Build and Development Commands

```bash
# Build the plugin
./gradlew build

# Build and copy to server (reobfuscated jar)
./gradlew reobfJar

# Clean build
./gradlew clean build

# Run with specific Java version
./gradlew build -Dorg.gradle.java.home=/path/to/java21
```

The main artifact is `build/libs/MC-Plugin-1.0.jar` after running `reobfJar`.

## Architecture Overview

### Core Systems

**Energy System**: The plugin implements a complex energy network with cables, generators, batteries, and energy transfer. Key components:
- `CableNetwork` - Manages the global cable network with SQLite persistence
- `EnergyManager` - Handles energy transfers between nodes
- `CableNode` - Represents individual network nodes (generators, batteries, cables)
- Energy tasks run on tickers: `GeneratorTask`, `CableLossTask`, `BatteryDrainTask`, `EnergyBalancerTask`

**Custom Weapons**: Modular weapon system with projectile physics:
- `WeaponResolver` - Determines weapon types from items
- Plasma Cannon: Complex projectile system in `guns/plasmacannon/` with physics, effects, and collisions
- Shoker: Simpler weapon implementation in `guns/shoker/`
- Base projectile system in `guns/projectile/`

**Code Panel System**: Interactive GUI system for access control:
- `CodePanelCommand` and `CodePanelClick` - Handle GUI interactions
- `CodePanelSession` - Manages individual panel sessions
- Configurable codes in `config.yml` under `validcodes`

**Database Layer**: SQLite integration for persistence:
- `DatabaseManager` - Connection management
- `DatabaseInit` - Schema initialization
- Used primarily for cable network persistence

### Plugin Lifecycle

The plugin follows this initialization sequence in `Main.java`:
1. PDC Keys initialization (`Keys.init()`)
2. SQLite database setup
3. Datapack installation to world folder
4. Core systems init (CableNetwork, EnergyWorkbenchManager, craft listeners)
5. Event listener registration
6. Background task startup
7. Command registration

### Configuration Structure

The `config.yml` is extensively structured with sections for:
- Energy system parameters (generation rates, loss, transfer costs)
- Cable network settings
- Code panel configuration (UI, sounds, messages, valid codes)
- Energy crafting system
- Custom craft recipes (multimeter, weapons)

### Datapack Integration

The plugin automatically installs a datapack (`MC-Datapack`) containing:
- Custom loot tables for enhanced drops
- Advancements system
- Custom worldgen features and structures
- Recipe definitions

## Development Guidelines

**Energy System**: When modifying energy components, ensure thread safety as multiple tasks access the cable network concurrently. Always use `LocationUtil.normalize()` for location keys.

**Weapon System**: New weapons should extend the base projectile system. Physics calculations are separated into dedicated classes (e.g., `PlasmaPhysics`, `PlasmaMath`).

**Database Operations**: Use `DatabaseManager` for all SQLite operations. The connection is managed globally and closed on plugin disable.

**Configuration**: All configurable values should be in `config.yml`. Use the existing structure and naming conventions.

**Task Management**: Background tasks are managed in `Main.java`. New recurring tasks should follow the existing pattern with proper cleanup in `stopTasks()`.

## Testing and Deployment

**Local Testing**: 
- Build with `./gradlew reobfJar`
- Copy jar to Paper server plugins folder
- Restart server twice (first load creates datapack, second load applies it)
- Use `/mcplugin reload` for config changes (may cause bugs, restart preferred)

**Requirements**:
- Java 21+
- Paper/Purpur core (not Spigot/Bukkit)
- World with datapack support

**Permissions**: Only `mcplugin.reload` permission exists, defaulting to op level.