#!/usr/bin/env node
"use strict";

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const BASE = "src/main/java/com/mcplugin";

// ============================================================
// Additional file moves (missing from first run)
// ============================================================
const EXTRA_MOVES = {
    "features/itemskill/ItemKillManager.java": "mechanics/features/items/ItemKillManager.java",
};

console.log("--- Moving extra files ---");
for (const [oldRel, newRel] of Object.entries(EXTRA_MOVES)) {
    const oldFull = path.join(BASE, oldRel);
    const newFull = path.join(BASE, newRel);
    if (fs.existsSync(oldFull)) {
        fs.mkdirSync(path.dirname(newFull), { recursive: true });
        try {
            execSync(`git mv "${oldFull}" "${newFull}"`, { stdio: 'pipe' });
        } catch {
            fs.renameSync(oldFull, newFull);
        }
        console.log(`  MOVED: ${oldRel} → ${newRel}`);
    } else {
        console.log(`  NOT FOUND: ${oldRel}`);
    }
}

// ============================================================
// Complete replacement map: OLD string → NEW string
// Key rule: replace MOST SPECIFIC first (longest matches first)
// ============================================================
const REPLACEMENTS = [
    // Package path replacements (wildcard imports + FQN references)
    // ENERGY packages
    ["com.mcplugin.energy.transfer.cable", "com.mcplugin.energy.transfer.cable"],  // stays same but some refs wrong
    ["com.mcplugin.energy.generation.basic", "com.mcplugin.energy.generation.basic"],
    ["com.mcplugin.energy.generation.reactor", "com.mcplugin.energy.generation.reactor"],
    ["com.mcplugin.energy.machines.furnace", "com.mcplugin.energy.machines.furnace"],
    ["com.mcplugin.energy.machines.workbench", "com.mcplugin.energy.machines.workbench"],
    ["com.mcplugin.energy.storage.battery", "com.mcplugin.energy.storage.battery"],
    
    // COMBAT packages
    ["com.mcplugin.guns.plasmacannon.projectile", "com.mcplugin.combat.weapons.plasma.projectile"],
    ["com.mcplugin.guns.plasmacannon", "com.mcplugin.combat.weapons.plasma"],
    ["com.mcplugin.guns.shoker", "com.mcplugin.combat.weapons.shoker"],
    ["com.mcplugin.guns.projectile", "com.mcplugin.combat.weapons.core"],
    ["com.mcplugin.guns", "com.mcplugin.combat.weapons.core"],

    // MECHANICS: security
    ["com.mcplugin.auth", "com.mcplugin.mechanics.security.auth"],
    ["com.mcplugin.cp", "com.mcplugin.mechanics.security.codepanel"],

    // MECHANICS: environment
    ["com.mcplugin.radiation", "com.mcplugin.mechanics.environment.radiation"],
    ["com.mcplugin.features.lightning", "com.mcplugin.mechanics.environment.lightning"],
    ["com.mcplugin.features.magnet", "com.mcplugin.mechanics.environment.magnet"],

    // MECHANICS: crafting
    ["com.mcplugin.crafting", "com.mcplugin.mechanics.crafting"],

    // MECHANICS: features sub-packages (specific first)
    ["com.mcplugin.features.integrity", "com.mcplugin.mechanics.features.integrity"],
    ["com.mcplugin.features.updater", "com.mcplugin.mechanics.features.updater"],
    ["com.mcplugin.features.savedhotbar", "com.mcplugin.mechanics.features.savedhotbar"],
    ["com.mcplugin.features.itemkill", "com.mcplugin.mechanics.features.items"],
    ["com.mcplugin.features.itemskill", "com.mcplugin.mechanics.features.items"],
    ["com.mcplugin.features.unbreakablebreaker", "com.mcplugin.mechanics.features.items"],
    ["com.mcplugin.features.notes", "com.mcplugin.mechanics.features.items"],
    ["com.mcplugin.features.blockdmg", "com.mcplugin.mechanics.features.blocks"],
    ["com.mcplugin.features.enderchest", "com.mcplugin.mechanics.features.blocks"],
    ["com.mcplugin.features.glassbreak", "com.mcplugin.mechanics.features.blocks"],
    ["com.mcplugin.features.boostedcobweb", "com.mcplugin.mechanics.features.blocks"],
    ["com.mcplugin.features.terracotaspeed", "com.mcplugin.mechanics.features.blocks"],
    ["com.mcplugin.features.containertrigger", "com.mcplugin.mechanics.features.blocks"],
    ["com.mcplugin.features.healthmeter", "com.mcplugin.mechanics.features.player"],
    ["com.mcplugin.features.vanish", "com.mcplugin.mechanics.features.player"],
    ["com.mcplugin.features.elytraboost", "com.mcplugin.mechanics.features.player"],
    ["com.mcplugin.features.attributes", "com.mcplugin.mechanics.features.player"],
    ["com.mcplugin.features.shieldslowness", "com.mcplugin.mechanics.features.player"],
    ["com.mcplugin.features.leash", "com.mcplugin.mechanics.features.player"],
    ["com.mcplugin.features.modeprotect", "com.mcplugin.mechanics.features.player"],
    ["com.mcplugin.features.entitylocator", "com.mcplugin.mechanics.features.world"],
    ["com.mcplugin.features.deathbell", "com.mcplugin.mechanics.features.world"],
    ["com.mcplugin.features.dragonegg", "com.mcplugin.mechanics.features.world"],
    ["com.mcplugin.features.antimatter", "com.mcplugin.mechanics.features.world"],
    ["com.mcplugin.features.beacon", "com.mcplugin.mechanics.features.world"],
    ["com.mcplugin.features.waypoint", "com.mcplugin.mechanics.features.world"],
    ["com.mcplugin.features.minecartspeed", "com.mcplugin.mechanics.features.world"],

    // MECHANICS: features base
    ["com.mcplugin.features.FeaturesManager", "com.mcplugin.mechanics.features.FeaturesManager"],

    // INFRASTRUCTURE packages (specific sub-packages first)
    ["com.mcplugin.commands.subcommands", "com.mcplugin.infrastructure.commands.sub"],
    ["com.mcplugin.commands.home", "com.mcplugin.infrastructure.commands.home"],
    ["com.mcplugin.commands.vote", "com.mcplugin.infrastructure.commands.vote"],
    ["com.mcplugin.commands", "com.mcplugin.infrastructure.commands"],
    ["com.mcplugin.listeners", "com.mcplugin.infrastructure.listeners"],
    ["com.mcplugin.server", "com.mcplugin.infrastructure.server"],
    ["com.mcplugin.config", "com.mcplugin.infrastructure.config"],
    ["com.mcplugin.database", "com.mcplugin.infrastructure.database"],
    ["com.mcplugin.structure", "com.mcplugin.infrastructure.util"],
    ["com.mcplugin.util", "com.mcplugin.infrastructure.util"],
    ["com.mcplugin.module", "com.mcplugin.infrastructure.modules"],
    ["com.mcplugin.main", "com.mcplugin.infrastructure.core"],
    ["com.mcplugin.tasks", "com.mcplugin.energy.transfer.cable"],

    // Old top-level packages that don't exist anymore
    ["com.mcplugin.core1", "com.mcplugin.energy.generation.reactor"],
    ["com.mcplugin.cable", "com.mcplugin.energy.transfer.cable"],

    // Specific wrong imports from first migration (fix incorrectly generated paths)
    // EnergyModule has wrong imports for ElectricFurnaceManager and GeneratorManager
    // These would be in energy.transfer.cable.ElectricFurnaceManager → energy.machines.furnace
    // But the replacement above for energy.transfer.cable stays as energy.transfer.cable...
    // The specific wrong imports need to be fixed individually

    // Fix indirect references: "from" pattern (used in some comments)
    // Already covered by the global replacements above
];

// Sort by longest first so specific matches take priority
REPLACEMENTS.sort((a, b) => b[0].length - a[0].length);

// ============================================================
// Also fix specific wrong-import issues from first migration
// These are individual class placements that the generic package map got wrong
// ============================================================
const FIX_WRONG_IMPORTS = [
    // TaskManager.java - wrong paths for energy classes
    ["com.mcplugin.energy.transfer.cable.BatteryDrainTask", "com.mcplugin.energy.storage.battery.BatteryDrainTask"],
    ["com.mcplugin.energy.transfer.cable.EnergyBalancerTask", "com.mcplugin.energy.EnergyBalancerTask"],
    ["com.mcplugin.energy.transfer.cable.GeneratorTask", "com.mcplugin.energy.generation.basic.GeneratorTask"],

    // EnergyModule.java - wrong paths
    ["com.mcplugin.energy.transfer.cable.ElectricFurnaceManager", "com.mcplugin.energy.machines.furnace.ElectricFurnaceManager"],
    ["com.mcplugin.energy.transfer.cable.GeneratorManager", "com.mcplugin.energy.generation.basic.GeneratorManager"],
    
    // Also fix the MagnetModule FQN reference patterns
    // These are covered by the generic package replacement above
];

// ============================================================
// Process ALL Java files
// ============================================================
function* walkJavaFiles(dir) {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            yield* walkJavaFiles(full);
        } else if (entry.name.endsWith(".java")) {
            yield full;
        }
    }
}

console.log("\n--- Fixing remaining references ---");
let totalFixes = 0;

for (const jf of walkJavaFiles(BASE)) {
    let content = fs.readFileSync(jf, 'utf-8');
    const original = content;
    
    // Apply all replacements
    for (const [oldStr, newStr] of REPLACEMENTS) {
        if (content.includes(oldStr)) {
            content = content.split(oldStr).join(newStr);
        }
    }
    
    // Apply specific wrong-import fixes
    for (const [oldStr, newStr] of FIX_WRONG_IMPORTS) {
        if (content.includes(oldStr)) {
            content = content.split(oldStr).join(newStr);
        }
    }
    
    // Fix self-package declarations that might still be wrong
    // The package should match the directory structure
    const relPath = path.relative(BASE, jf).replace(/\\/g, "/");
    const dir = path.dirname(relPath);
    const correctPkg = dir === "." ? "com.mcplugin" : "com.mcplugin." + dir.replace(/\//g, ".");
    
    const pkgMatch = content.match(/^package\s+([\w.]+);/m);
    if (pkgMatch && pkgMatch[1] !== correctPkg) {
        content = content.replace(
            /^package\s+[\w.]+;/m,
            `package ${correctPkg};`
        );
    }
    
    if (content !== original) {
        fs.writeFileSync(jf, content, 'utf-8');
        totalFixes++;
    }
}

console.log(`  Fixed ${totalFixes} files`);

// ============================================================
// Cleanup empty dirs
// ============================================================
console.log("\n--- Cleanup ---");
function removeEmptyDirs(dir) {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        if (entry.isDirectory()) {
            removeEmptyDirs(path.join(dir, entry.name));
        }
    }
    if (fs.existsSync(dir)) {
        const remaining = fs.readdirSync(dir);
        if (remaining.length === 0 && dir !== BASE) {
            fs.rmdirSync(dir);
            console.log(`  Removed empty: ${path.relative(BASE, dir)}`);
        }
    }
}
removeEmptyDirs(BASE);

console.log("\n=== DONE ===");
