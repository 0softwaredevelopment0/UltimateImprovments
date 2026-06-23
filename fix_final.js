#!/usr/bin/env node
"use strict";
const fs = require('fs');
const path = require('path');
const BASE = "src/main/java/com/mcplugin";

// Fix sub/ → subcommands/ in all Java files
function* walkJava(dir) {
    if (!fs.existsSync(dir)) return;
    for (const e of fs.readdirSync(dir, {withFileTypes:true})) {
        const fp = path.join(dir, e.name);
        if (e.isDirectory()) yield* walkJava(fp);
        else if (e.name.endsWith(".java")) yield fp;
    }
}

// Fix ShokerProjectile package
const sp = path.join(BASE, "combat/weapons/core/ShokerProjectile.java");
if (fs.existsSync(sp)) {
    let c = fs.readFileSync(sp, 'utf-8');
    c = c.replace("package com.mcplugin.guns.projectile;", "package com.mcplugin.combat.weapons.core;");
    fs.writeFileSync(sp, c);
    console.log("Fixed ShokerProjectile package");
}

// Fix subcommands/ package declarations
const scDir = path.join(BASE, "infrastructure/commands/subcommands");
if (fs.existsSync(scDir)) {
    for (const f of fs.readdirSync(scDir)) {
        const fp = path.join(scDir, f);
        if (!f.endsWith(".java")) continue;
        let c = fs.readFileSync(fp, 'utf-8');
        const updated = c.replace(
            /^package\s+com\.mcplugin\.infrastructure\.commands\.sub;/m,
            "package com.mcplugin.infrastructure.commands.subcommands;"
        );
        if (updated !== c) {
            fs.writeFileSync(fp, updated);
            console.log(`Fixed package: ${f}`);
        }
    }
}

// Replace all references to .sub. → .subcommands. in all Java files
let count = 0;
for (const fp of walkJava(BASE)) {
    let c = fs.readFileSync(fp, 'utf-8');
    const orig = c;
    c = c.split("infrastructure.commands.sub.").join("infrastructure.commands.subcommands.");
    if (c !== orig) {
        fs.writeFileSync(fp, c);
        count++;
    }
}
console.log(`Fixed ${count} files with sub→subcommands references`);

// Cleanup empty dirs
function cleanDirs(dir) {
    if (!fs.existsSync(dir)) return;
    for (const e of fs.readdirSync(dir, {withFileTypes:true})) {
        if (e.isDirectory()) cleanDirs(path.join(dir, e.name));
    }
    const rem = fs.readdirSync(dir);
    if (rem.length === 0 && dir !== BASE) fs.rmdirSync(dir);
}
cleanDirs(BASE);
console.log("Done");
