#!/usr/bin/env python3
"""Migrate MC-Plugin to new architecture: category/subcategory/system/classes"""

import os
import re
import subprocess
import sys

BASE = "src/main/java/com/mcplugin"

# ============================================================
# COMPLETE FILE MAPPING (old_relative → new_relative)
# ============================================================
FILE_MAP = {
    # ── ENERGY ──
    "energy/EnergyManager.java": "energy/EnergyManager.java",
    "energy/EnergyBalancerTask.java": "energy/EnergyBalancerTask.java",
    "energy/GeneratorManager.java": "energy/generation/basic/GeneratorManager.java",
    "energy/GeneratorStructure.java": "energy/generation/basic/GeneratorStructure.java",
    "energy/GeneratorTask.java": "energy/generation/basic/GeneratorTask.java",
    "energy/ElectricFurnaceManager.java": "energy/machines/furnace/ElectricFurnaceManager.java",
    "energy/BatteryDrainTask.java": "energy/storage/battery/BatteryDrainTask.java",
    "energy/CableLossTask.java": "energy/transfer/cable/CableLossTask.java",
    "energy/crafting/EnergyCraftingListener.java": "energy/machines/workbench/EnergyCraftingListener.java",
    "energy/crafting/EnergyWorkbenchManager.java": "energy/machines/workbench/EnergyWorkbenchManager.java",
    "energy/visual/CableVisualTask.java": "energy/transfer/cable/CableVisualTask.java",
    "tasks/CableTickTask.java": "energy/transfer/cable/CableTickTask.java",

    # ── ENERGY: cable → energy/transfer/cable ──
    "cable/CableBlock.java": "energy/transfer/cable/CableBlock.java",
    "cable/CableNetwork.java": "energy/transfer/cable/CableNetwork.java",
    "cable/CableNode.java": "energy/transfer/cable/CableNode.java",
    "cable/NodeType.java": "energy/transfer/cable/NodeType.java",

    # ── ENERGY: reactor (core1 → energy/generation/reactor) ──
    "core1/ReactorCommand.java": "energy/generation/reactor/ReactorCommand.java",
    "core1/ReactorConfig.java": "energy/generation/reactor/ReactorConfig.java",
    "core1/ReactorDisplay.java": "energy/generation/reactor/ReactorDisplay.java",
    "core1/ReactorListener.java": "energy/generation/reactor/ReactorListener.java",
    "core1/ReactorManager.java": "energy/generation/reactor/ReactorManager.java",
    "core1/ReactorPersistence.java": "energy/generation/reactor/ReactorPersistence.java",
    "core1/ReactorState.java": "energy/generation/reactor/ReactorState.java",
    "core1/ReactorStructure.java": "energy/generation/reactor/ReactorStructure.java",
    "core1/ReactorTask.java": "energy/generation/reactor/ReactorTask.java",

    # ── COMBAT: guns → combat/weapons/... ──
    "guns/WeaponResolver.java": "combat/weapons/core/WeaponResolver.java",
    "guns/projectile/BaseProjectile.java": "combat/weapons/core/BaseProjectile.java",
    "guns/projectile/ProjectileManager.java": "combat/weapons/core/ProjectileManager.java",
    "guns/projectile/ProjectileType.java": "combat/weapons/core/ProjectileType.java",
    "guns/shoker/ShokerListener.java": "combat/weapons/shoker/ShokerListener.java",
    "guns/shoker/ShokerProjectile.java": "combat/weapons/shoker/ShokerProjectile.java",
    "guns/plasmacannon/GunListener.java": "combat/weapons/plasma/GunListener.java",
    "guns/plasmacannon/PlasmaProjectile.java": "combat/weapons/plasma/PlasmaProjectile.java",
    "guns/plasmacannon/PlasmaProjectileTask.java": "combat/weapons/plasma/PlasmaProjectileTask.java",
    "guns/plasmacannon/projectile/CollisionResult.java": "combat/weapons/plasma/projectile/CollisionResult.java",
    "guns/plasmacannon/projectile/CollisionType.java": "combat/weapons/plasma/projectile/CollisionType.java",
    "guns/plasmacannon/projectile/PlasmaBlockCollisions.java": "combat/weapons/plasma/projectile/PlasmaBlockCollisions.java",
    "guns/plasmacannon/projectile/PlasmaCollision.java": "combat/weapons/plasma/projectile/PlasmaCollision.java",
    "guns/plasmacannon/projectile/PlasmaConstants.java": "combat/weapons/plasma/projectile/PlasmaConstants.java",
    "guns/plasmacannon/projectile/PlasmaEffects.java": "combat/weapons/plasma/projectile/PlasmaEffects.java",
    "guns/plasmacannon/projectile/PlasmaEntityCollisions.java": "combat/weapons/plasma/projectile/PlasmaEntityCollisions.java",
    "guns/plasmacannon/projectile/PlasmaPhysics.java": "combat/weapons/plasma/projectile/PlasmaPhysics.java",
    "guns/plasmacannon/projectile/PlasmaRaycast.java": "combat/weapons/plasma/projectile/PlasmaRaycast.java",

    # ── MECHANICS: auth → mechanics/security/auth ──
    "auth/AuthAuthenticator.java": "mechanics/security/auth/AuthAuthenticator.java",
    "auth/AuthConfig.java": "mechanics/security/auth/AuthConfig.java",
    "auth/AuthDatabase.java": "mechanics/security/auth/AuthDatabase.java",
    "auth/AuthGUI.java": "mechanics/security/auth/AuthGUI.java",
    "auth/AuthGUIAnvilReader.java": "mechanics/security/auth/AuthGUIAnvilReader.java",
    "auth/AuthGUIItems.java": "mechanics/security/auth/AuthGUIItems.java",
    "auth/AuthGUITracker.java": "mechanics/security/auth/AuthGUITracker.java",
    "auth/AuthListener.java": "mechanics/security/auth/AuthListener.java",
    "auth/AuthManager.java": "mechanics/security/auth/AuthManager.java",
    "auth/AuthPlayerState.java": "mechanics/security/auth/AuthPlayerState.java",
    "auth/AuthRateLimiter.java": "mechanics/security/auth/AuthRateLimiter.java",
    "auth/AuthTimeoutManager.java": "mechanics/security/auth/AuthTimeoutManager.java",

    # ── MECHANICS: cp → mechanics/security/codepanel ──
    "cp/CodePanelCleanupTask.java": "mechanics/security/codepanel/CodePanelCleanupTask.java",
    "cp/CodePanelClick.java": "mechanics/security/codepanel/CodePanelClick.java",
    "cp/CodePanelCommand.java": "mechanics/security/codepanel/CodePanelCommand.java",
    "cp/CodePanelDatabase.java": "mechanics/security/codepanel/CodePanelDatabase.java",
    "cp/CodePanelGUI.java": "mechanics/security/codepanel/CodePanelGUI.java",
    "cp/CodePanelGUIListener.java": "mechanics/security/codepanel/CodePanelGUIListener.java",
    "cp/CodePanelSession.java": "mechanics/security/codepanel/CodePanelSession.java",

    # ── MECHANICS: radiation → mechanics/environment/radiation ──
    "radiation/RadiationManager.java": "mechanics/environment/radiation/RadiationManager.java",
    "radiation/RadiationTask.java": "mechanics/environment/radiation/RadiationTask.java",

    # ── MECHANICS: lightning → mechanics/environment/lightning ──
    "features/lightning/LightningManager.java": "mechanics/environment/lightning/LightningManager.java",
    "features/lightning/LightningStructure.java": "mechanics/environment/lightning/LightningStructure.java",

    # ── MECHANICS: magnet → mechanics/environment/magnet ──
    "features/magnet/MagnetConfig.java": "mechanics/environment/magnet/MagnetConfig.java",
    "features/magnet/MagnetDatabase.java": "mechanics/environment/magnet/MagnetDatabase.java",
    "features/magnet/MagnetEventListener.java": "mechanics/environment/magnet/MagnetEventListener.java",
    "features/magnet/MagnetManager.java": "mechanics/environment/magnet/MagnetManager.java",
    "features/magnet/MagnetPersistence.java": "mechanics/environment/magnet/MagnetPersistence.java",
    "features/magnet/MagnetStructure.java": "mechanics/environment/magnet/MagnetStructure.java",

    # ── MECHANICS: crafting → mechanics/crafting ──
    "crafting/AntimatterCraftListener.java": "mechanics/crafting/AntimatterCraftListener.java",
    "crafting/DosimeterCraftListener.java": "mechanics/crafting/DosimeterCraftListener.java",
    "crafting/EntityLocatorCraftListener.java": "mechanics/crafting/EntityLocatorCraftListener.java",
    "crafting/HealthMeterCraftListener.java": "mechanics/crafting/HealthMeterCraftListener.java",
    "crafting/LeadShieldCraftListener.java": "mechanics/crafting/LeadShieldCraftListener.java",
    "crafting/MultimeterCraftListener.java": "mechanics/crafting/MultimeterCraftListener.java",
    "crafting/PlasmaCannonCraftListener.java": "mechanics/crafting/PlasmaCannonCraftListener.java",
    "crafting/RecipeRegistry.java": "mechanics/crafting/RecipeRegistry.java",
    "crafting/ShokerCraftListener.java": "mechanics/crafting/ShokerCraftListener.java",

    # ── MECHANICS: features → mechanics/features/... ──
    "features/FeaturesManager.java": "mechanics/features/FeaturesManager.java",

    # items
    "features/itemkill/ItemKillManager.java": "mechanics/features/items/ItemKillManager.java",
    "features/unbreakablebreaker/UnbreakableBreakerManager.java": "mechanics/features/items/UnbreakableBreakerManager.java",
    "features/notes/NotesDatabase.java": "mechanics/features/items/NotesDatabase.java",
    "features/notes/NotesGUI.java": "mechanics/features/items/NotesGUI.java",
    "features/notes/NotesGUIListener.java": "mechanics/features/items/NotesGUIListener.java",
    "features/notes/NotesManager.java": "mechanics/features/items/NotesManager.java",

    # blocks
    "features/blockdmg/BlockDmgManager.java": "mechanics/features/blocks/BlockDmgManager.java",
    "features/enderchest/EnderChestManager.java": "mechanics/features/blocks/EnderChestManager.java",
    "features/glassbreak/GlassBreakManager.java": "mechanics/features/blocks/GlassBreakManager.java",
    "features/boostedcobweb/BoostedCobwebManager.java": "mechanics/features/blocks/BoostedCobwebManager.java",
    "features/terracotaspeed/TerracotaSpeedManager.java": "mechanics/features/blocks/TerracotaSpeedManager.java",
    "features/containertrigger/ContainerTriggerManager.java": "mechanics/features/blocks/ContainerTriggerManager.java",

    # player
    "features/healthmeter/HealthMeterManager.java": "mechanics/features/player/HealthMeterManager.java",
    "features/vanish/VanishManager.java": "mechanics/features/player/VanishManager.java",
    "features/elytraboost/ElytraBoostManager.java": "mechanics/features/player/ElytraBoostManager.java",
    "features/attributes/AttributesManager.java": "mechanics/features/player/AttributesManager.java",
    "features/shieldslowness/ShieldSlownessManager.java": "mechanics/features/player/ShieldSlownessManager.java",
    "features/leash/LeashManager.java": "mechanics/features/player/LeashManager.java",
    "features/modeprotect/ModeProtectManager.java": "mechanics/features/player/ModeProtectManager.java",

    # world
    "features/entitylocator/EntityLocatorManager.java": "mechanics/features/world/EntityLocatorManager.java",
    "features/deathbell/DeathBellManager.java": "mechanics/features/world/DeathBellManager.java",
    "features/dragonegg/DragonEggManager.java": "mechanics/features/world/DragonEggManager.java",
    "features/antimatter/AntimatterManager.java": "mechanics/features/world/AntimatterManager.java",
    "features/beacon/BeaconManager.java": "mechanics/features/world/BeaconManager.java",
    "features/waypoint/WaypointManager.java": "mechanics/features/world/WaypointManager.java",
    "features/minecartspeed/MinecartSpeedManager.java": "mechanics/features/world/MinecartSpeedManager.java",

    # integrity (sub-system)
    "features/integrity/IntegrityCombineListener.java": "mechanics/features/integrity/IntegrityCombineListener.java",
    "features/integrity/IntegrityConfig.java": "mechanics/features/integrity/IntegrityConfig.java",
    "features/integrity/IntegrityListener.java": "mechanics/features/integrity/IntegrityListener.java",
    "features/integrity/IntegrityManager.java": "mechanics/features/integrity/IntegrityManager.java",
    "features/integrity/PiercingListener.java": "mechanics/features/integrity/PiercingListener.java",

    # updater
    "features/updater/UpdateChecker.java": "mechanics/features/updater/UpdateChecker.java",

    # saved hotbar
    "features/savedhotbar/CreativeItemValidator.java": "mechanics/features/savedhotbar/CreativeItemValidator.java",

    # ── INFRASTRUCTURE ──
    "Main.java": "infrastructure/core/Main.java",
    "Keys.java": "infrastructure/core/Keys.java",
    "main/CommandRegistrar.java": "infrastructure/core/CommandRegistrar.java",
    "main/DatapackInstaller.java": "infrastructure/core/DatapackInstaller.java",
    "main/TaskManager.java": "infrastructure/core/TaskManager.java",

    # config
    "config/ConfigIntegrityValidator.java": "infrastructure/config/ConfigIntegrityValidator.java",
    "config/ConfigRules.java": "infrastructure/config/ConfigRules.java",
    "config/ConfigValueValidator.java": "infrastructure/config/ConfigValueValidator.java",
    "config/MessagesManager.java": "infrastructure/config/MessagesManager.java",

    # database
    "database/AsyncAutoSaveManager.java": "infrastructure/database/AsyncAutoSaveManager.java",
    "database/DatabaseInit.java": "infrastructure/database/DatabaseInit.java",
    "database/DatabaseManager.java": "infrastructure/database/DatabaseManager.java",
    "database/DBBootstrap.java": "infrastructure/database/DBBootstrap.java",

    # structure → infrastructure/util
    "structure/StructureTemplate.java": "infrastructure/util/StructureTemplate.java",

    # util
    "util/BlockUtil.java": "infrastructure/util/BlockUtil.java",
    "util/FileLogger.java": "infrastructure/util/FileLogger.java",
    "util/LocationUtil.java": "infrastructure/util/LocationUtil.java",
    "util/MessageUtil.java": "infrastructure/util/MessageUtil.java",
    "util/SoundUtil.java": "infrastructure/util/SoundUtil.java",

    # ── INFRASTRUCTURE: commands ──
    "commands/AskCordsManager.java": "infrastructure/commands/AskCordsManager.java",
    "commands/AuthCommand.java": "infrastructure/commands/AuthCommand.java",
    "commands/ChgDimCommand.java": "infrastructure/commands/ChgDimCommand.java",
    "commands/ChgDimGUI.java": "infrastructure/commands/ChgDimGUI.java",
    "commands/CodePaneKeyCommand.java": "infrastructure/commands/CodePaneKeyCommand.java",
    "commands/DimensionManager.java": "infrastructure/commands/DimensionManager.java",
    "commands/ItemCommand.java": "infrastructure/commands/ItemCommand.java",
    "commands/PluginReloadCommand.java": "infrastructure/commands/PluginReloadCommand.java",
    "commands/PowerCommand.java": "infrastructure/commands/PowerCommand.java",
    "commands/PowerManager.java": "infrastructure/commands/PowerManager.java",
    "commands/SuicideCommand.java": "infrastructure/commands/SuicideCommand.java",
    "commands/VanishListCommand.java": "infrastructure/commands/VanishListCommand.java",
    "commands/home/HomeCommand.java": "infrastructure/commands/home/HomeCommand.java",
    "commands/home/HomeDatabase.java": "infrastructure/commands/home/HomeDatabase.java",
    "commands/vote/VoteManager.java": "infrastructure/commands/vote/VoteManager.java",
    "commands/subcommands/AuthSubcommand.java": "infrastructure/commands/sub/AuthSubcommand.java",
    "commands/subcommands/BroadcastSubcommand.java": "infrastructure/commands/sub/BroadcastSubcommand.java",
    "commands/subcommands/ChgDimSubcommand.java": "infrastructure/commands/sub/ChgDimSubcommand.java",
    "commands/subcommands/CodePaneSubcommand.java": "infrastructure/commands/sub/CodePaneSubcommand.java",
    "commands/subcommands/HelpCommand.java": "infrastructure/commands/sub/HelpCommand.java",
    "commands/subcommands/ItemSubcommand.java": "infrastructure/commands/sub/ItemSubcommand.java",
    "commands/subcommands/MiscSubcommand.java": "infrastructure/commands/sub/MiscSubcommand.java",
    "commands/subcommands/ModulesSubcommand.java": "infrastructure/commands/sub/ModulesSubcommand.java",
    "commands/subcommands/PowerSubcommand.java": "infrastructure/commands/sub/PowerSubcommand.java",
    "commands/subcommands/RadiationSubcommand.java": "infrastructure/commands/sub/RadiationSubcommand.java",
    "commands/subcommands/ReloadSubcommand.java": "infrastructure/commands/sub/ReloadSubcommand.java",
    "commands/subcommands/StructureSubcommand.java": "infrastructure/commands/sub/StructureSubcommand.java",
    "commands/subcommands/UpdateSubcommand.java": "infrastructure/commands/sub/UpdateSubcommand.java",

    # ── INFRASTRUCTURE: listeners ──
    "listeners/BlockBreakListener.java": "infrastructure/listeners/BlockBreakListener.java",
    "listeners/BlockPlaceListener.java": "infrastructure/listeners/BlockPlaceListener.java",
    "listeners/ChatFilterManager.java": "infrastructure/listeners/ChatFilterManager.java",
    "listeners/FishingListener.java": "infrastructure/listeners/FishingListener.java",
    "listeners/MultimeterListener.java": "infrastructure/listeners/MultimeterListener.java",
    "listeners/PluginHideListener.java": "infrastructure/listeners/PluginHideListener.java",
    "listeners/PowerInterceptListener.java": "infrastructure/listeners/PowerInterceptListener.java",
    "listeners/ServerBrandListener.java": "infrastructure/listeners/ServerBrandListener.java",
    "listeners/ShulkerBulletListener.java": "infrastructure/listeners/ShulkerBulletListener.java",
    "listeners/VoidProtectionListener.java": "infrastructure/listeners/VoidProtectionListener.java",

    # ── INFRASTRUCTURE: server ──
    "server/EmergencyEntitiesKill.java": "infrastructure/server/EmergencyEntitiesKill.java",
    "server/PacketGuard.java": "infrastructure/server/PacketGuard.java",
    "server/RedstoneGuard.java": "infrastructure/server/RedstoneGuard.java",
    "server/RedstoneGuardListener.java": "infrastructure/server/RedstoneGuardListener.java",
    "server/RedstoneGuardTask.java": "infrastructure/server/RedstoneGuardTask.java",
    "server/ServerOverloadNotify.java": "infrastructure/server/ServerOverloadNotify.java",
    "server/ServerOverloadWarning.java": "infrastructure/server/ServerOverloadWarning.java",

    # ── INFRASTRUCTURE: modules ──
    "module/AntimatterModule.java": "infrastructure/modules/AntimatterModule.java",
    "module/AttributesModule.java": "infrastructure/modules/AttributesModule.java",
    "module/AuthModule.java": "infrastructure/modules/AuthModule.java",
    "module/AutoSaveModule.java": "infrastructure/modules/AutoSaveModule.java",
    "module/BeaconModule.java": "infrastructure/modules/BeaconModule.java",
    "module/BlockDmgModule.java": "infrastructure/modules/BlockDmgModule.java",
    "module/BoostedCobwebModule.java": "infrastructure/modules/BoostedCobwebModule.java",
    "module/CableModule.java": "infrastructure/modules/CableModule.java",
    "module/ChatFilterModule.java": "infrastructure/modules/ChatFilterModule.java",
    "module/ContainerTriggerModule.java": "infrastructure/modules/ContainerTriggerModule.java",
    "module/CoreModule.java": "infrastructure/modules/CoreModule.java",
    "module/CraftingModule.java": "infrastructure/modules/CraftingModule.java",
    "module/CreativeItemValidatorModule.java": "infrastructure/modules/CreativeItemValidatorModule.java",
    "module/DatabaseModule.java": "infrastructure/modules/DatabaseModule.java",
    "module/DatapackModule.java": "infrastructure/modules/DatapackModule.java",
    "module/DeathBellModule.java": "infrastructure/modules/DeathBellModule.java",
    "module/DragonEggModule.java": "infrastructure/modules/DragonEggModule.java",
    "module/ElytraBoostModule.java": "infrastructure/modules/ElytraBoostModule.java",
    "module/EnderChestModule.java": "infrastructure/modules/EnderChestModule.java",
    "module/EnergyModule.java": "infrastructure/modules/EnergyModule.java",
    "module/EntityLocatorModule.java": "infrastructure/modules/EntityLocatorModule.java",
    "module/GlassBreakModule.java": "infrastructure/modules/GlassBreakModule.java",
    "module/HealthMeterModule.java": "infrastructure/modules/HealthMeterModule.java",
    "module/IntegrityModule.java": "infrastructure/modules/IntegrityModule.java",
    "module/ItemKillModule.java": "infrastructure/modules/ItemKillModule.java",
    "module/LeashModule.java": "infrastructure/modules/LeashModule.java",
    "module/LightningModule.java": "infrastructure/modules/LightningModule.java",
    "module/MagnetModule.java": "infrastructure/modules/MagnetModule.java",
    "module/MinecartSpeedModule.java": "infrastructure/modules/MinecartSpeedModule.java",
    "module/ModeProtectModule.java": "infrastructure/modules/ModeProtectModule.java",
    "module/ModuleManager.java": "infrastructure/modules/ModuleManager.java",
    "module/NotesModule.java": "infrastructure/modules/NotesModule.java",
    "module/PacketGuardModule.java": "infrastructure/modules/PacketGuardModule.java",
    "module/PluginModule.java": "infrastructure/modules/PluginModule.java",
    "module/PowerModule.java": "infrastructure/modules/PowerModule.java",
    "module/RadiationModule.java": "infrastructure/modules/RadiationModule.java",
    "module/ReactorModule.java": "infrastructure/modules/ReactorModule.java",
    "module/RedstoneGuardModule.java": "infrastructure/modules/RedstoneGuardModule.java",
    "module/ShieldSlownessModule.java": "infrastructure/modules/ShieldSlownessModule.java",
    "module/TasksModule.java": "infrastructure/modules/TasksModule.java",
    "module/TerracotaSpeedModule.java": "infrastructure/modules/TerracotaSpeedModule.java",
    "module/UnbreakableBreakerModule.java": "infrastructure/modules/UnbreakableBreakerModule.java",
    "module/UpdateModule.java": "infrastructure/modules/UpdateModule.java",
    "module/VanishModule.java": "infrastructure/modules/VanishModule.java",
    "module/VersionCheckModule.java": "infrastructure/modules/VersionCheckModule.java",
    "module/VoidProtectionModule.java": "infrastructure/modules/VoidProtectionModule.java",
    "module/WaypointModule.java": "infrastructure/modules/WaypointModule.java",
}

# Build reverse package mapping: old_package → new_package
def pkg_from_path(p):
    """Convert relative file path to Java package"""
    d = os.path.dirname(p)
    if not d:
        return "com.mcplugin"
    return "com.mcplugin." + d.replace("/", ".").replace("\\", ".")

pkg_map = {}
for old_rel, new_rel in FILE_MAP.items():
    old_pkg = pkg_from_path(old_rel)
    new_pkg = pkg_from_path(new_rel)
    if old_pkg != new_pkg:
        pkg_map[old_pkg] = new_pkg

# Sort by length (longest first) so we replace more specific packages first
pkg_pairs = sorted(pkg_map.items(), key=lambda x: -len(x[0]))

print(f"=== Will move {len(FILE_MAP)} files ===")
print(f"=== Will update {len(pkg_map)} package references ===")

# ============================================================
# STEP 1: Create directories and move files
# ============================================================
print("\n--- STEP 1: Moving files ---")
for old_rel, new_rel in FILE_MAP.items():
    old_full = os.path.join(BASE, old_rel)
    new_full = os.path.join(BASE, new_rel)
    
    if not os.path.exists(old_full):
        print(f"  SKIP (not found): {old_rel}")
        continue
    
    # Create target directory
    os.makedirs(os.path.dirname(new_full), exist_ok=True)
    
    # Use git mv if possible, else regular move
    result = subprocess.run(
        ["git", "mv", old_full, new_full],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        # Regular move
        os.rename(old_full, new_full)
        print(f"  MOVED: {old_rel} → {new_rel}")
    else:
        print(f"  GIT_MV: {old_rel} → {new_rel}")

# ============================================================
# STEP 2: Update package declarations and imports in ALL Java files
# ============================================================
print("\n--- STEP 2: Updating package declarations and imports ---")

def update_file_content(filepath):
    """Update package declaration and imports in a Java file"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Update self package declaration
    rel_path = os.path.relpath(filepath, BASE).replace("\\", "/")
    new_pkg = pkg_from_path(rel_path)
    content = re.sub(
        r'^package\s+com\.mcplugin[\w.]*;',
        f'package {new_pkg};',
        content,
        flags=re.MULTILINE
    )
    
    # Update import statements
    for old_pkg, new_pkg in pkg_pairs:
        # Replace "import com.mcplugin.old_pkg.ClassName;"
        pattern = re.escape(f"import {old_pkg}.") + r"(\w+;)"
        replacement = f"import {new_pkg}.\\1"
        content = re.sub(pattern, replacement, content)
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

# Find ALL Java files and update them
all_java_files = []
for root, dirs, files in os.walk(BASE):
    for f in files:
        if f.endswith(".java"):
            all_java_files.append(os.path.join(root, f))

updated_count = 0
for java_file in all_java_files:
    if update_file_content(java_file):
        updated_count += 1

print(f"  Updated {updated_count} Java files")

# ============================================================
# STEP 3: Update plugin.yml main class
# ============================================================
print("\n--- STEP 3: Updating plugin.yml ---")
plugin_yml = "src/main/resources/plugin.yml"
with open(plugin_yml, 'r', encoding='utf-8') as f:
    yml_content = f.read()

yml_content = yml_content.replace(
    "main: com.mcplugin.Main",
    "main: com.mcplugin.infrastructure.core.Main"
)

with open(plugin_yml, 'w', encoding='utf-8') as f:
    f.write(yml_content)

print("  Updated plugin.yml main class")

# ============================================================
# STEP 4: Clean up empty directories
# ============================================================
print("\n--- STEP 4: Cleanup empty directories ---")
# Remove empty directories in BASE
for root, dirs, files in os.walk(BASE, topdown=False):
    if root == BASE:
        continue
    if not files and not dirs:
        try:
            os.rmdir(root)
            print(f"  Removed empty: {os.path.relpath(root, BASE)}")
        except OSError:
            pass

print("\n=== DONE ===")
print(f"Moved {len(FILE_MAP)} files, updated {updated_count} Java files")
