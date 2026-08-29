package com.ultimateimprovments.database;

import com.ultimateimprovments.core.Main;
import java.sql.Connection;
import java.sql.Statement;

public class DatabaseInit {

    public static void init() {

        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {

            // Execute each CREATE TABLE/INDEX individually for consistency
            st.execute("""
                CREATE TABLE IF NOT EXISTS cables (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    energy INTEGER DEFAULT 0,
                    type TEXT DEFAULT 'CABLE',
                    PRIMARY KEY(world, x, y, z)
                );
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_cables_world
                ON cables(world);
            """);

            // =========================
            // 🔌 CABLE CONNECTIONS
            // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS cable_connections (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,

                    to_world TEXT NOT NULL,
                    to_x INTEGER NOT NULL,
                    to_y INTEGER NOT NULL,
                    to_z INTEGER NOT NULL
                );
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_connections_from
                ON cable_connections(world, x, y, z);
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_connections_to
                ON cable_connections(to_world, to_x, to_y, to_z);
            """);

            // =========================
            // 🛠 WORKBENCHES
            // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS workbenches (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY(world, x, y, z)
                );
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_workbenches_world
                ON workbenches(world);
            """);

            // =========================
            // ⚡ GENERATORS
            // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS generators (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    fuel INTEGER DEFAULT 0,
                    energy INTEGER DEFAULT 0,
                    PRIMARY KEY(world, x, y, z)
                );
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_generators_world
                ON generators(world);
            """);

            // =========================
            // ☢ PLAYER RADIATION
            // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS player_radiation (
                    uuid TEXT PRIMARY KEY,
                    radiation INTEGER DEFAULT 0
                );
            """);

            // =========================
            // ⚛ REACTORS
            // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS reactors (
                    reactor_id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    core_temp INTEGER DEFAULT 0,
                    core_press INTEGER DEFAULT 0,
                    core_sh_int INTEGER DEFAULT 100,
                    core_case_temp INTEGER DEFAULT 0,
                    core_case_press INTEGER DEFAULT 0,
                    core_case_int INTEGER DEFAULT 100,
                    recipe_time INTEGER DEFAULT 0,
                    self_destruct INTEGER DEFAULT 0,
                    reactor_wear INTEGER DEFAULT 0
                );
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_reactors_world
                ON reactors(world);
            """);

            // =========================
            // 🧲 MAGNETS
            // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS magnets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    world TEXT NOT NULL,
                    center_x INTEGER NOT NULL,
                    center_y INTEGER NOT NULL,
                    center_z INTEGER NOT NULL,
                    block_count INTEGER DEFAULT 1,
                    active INTEGER DEFAULT 1
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS magnet_blocks (
                    magnet_id INTEGER NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY(magnet_id, x, y, z),
                    FOREIGN KEY(magnet_id) REFERENCES magnets(id) ON DELETE CASCADE
                );
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_magnet_blocks_id
                ON magnet_blocks(magnet_id);
            """);

            // =========================
            // ⚛ REACTOR WEAR COLUMN MIGRATION (for old DBs)
            // =========================
            try {
                st.execute("ALTER TABLE reactors ADD COLUMN reactor_wear INTEGER DEFAULT 0");
            } catch (Exception ignored) {
                // Column already exists — this is fine
            }

            // =========================
            // ⚛ ENERGY GENERATED COLUMN MIGRATION (for old DBs)
            // =========================
            try {
                st.execute("ALTER TABLE reactors ADD COLUMN energy_generated INTEGER DEFAULT 0");
            } catch (Exception ignored) {
                // Column already exists — this is fine
            }

        // =========================
        // 🔋 BATTERY MULTIBLOCK
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS batteries (
                id INTEGER PRIMARY KEY,
                world TEXT NOT NULL,
                center_x INTEGER NOT NULL,
                center_y INTEGER NOT NULL,
                center_z INTEGER NOT NULL,
                block_count INTEGER DEFAULT 1
            );
        """);

        st.execute("""
            CREATE TABLE IF NOT EXISTS battery_blocks (
                battery_id INTEGER NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                PRIMARY KEY(battery_id, x, y, z),
                FOREIGN KEY(battery_id) REFERENCES batteries(id) ON DELETE CASCADE
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_battery_blocks_id
            ON battery_blocks(battery_id);
        """);

        // =========================
        // 💡 LIGHT MULTIBLOCK
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS lights (
                id INTEGER PRIMARY KEY,
                world TEXT NOT NULL,
                center_x INTEGER NOT NULL,
                center_y INTEGER NOT NULL,
                center_z INTEGER NOT NULL,
                block_count INTEGER DEFAULT 1,
                lit INTEGER DEFAULT 0
            );
        """);

        st.execute("""
            CREATE TABLE IF NOT EXISTS light_blocks (
                light_id INTEGER NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                PRIMARY KEY(light_id, x, y, z),
                FOREIGN KEY(light_id) REFERENCES lights(id) ON DELETE CASCADE
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_light_blocks_id
            ON light_blocks(light_id);
        """);

        // =========================
        // 🦅 ELYTRA BOOST DISABLED (persist /ui togglefly state)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS elytra_boost_disabled (
                uuid TEXT PRIMARY KEY
            );
        """);

        // =========================
        // 🌍 DIMENSION RETURNS
            // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS dimension_returns (
                    uuid TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT DEFAULT 0,
                    pitch FLOAT DEFAULT 0,
                    has_return INTEGER DEFAULT 0
                );
            """);

            // =========================
            // 🔐 AUTH (register/login)
            // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS auth (
                    uuid TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    salt TEXT NOT NULL,
                    ip_address TEXT DEFAULT ''
                );
            """);        // =========================
        // ⚙ PLAYER SETTINGS (bossbar/scoreboard toggles)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS player_settings (
                uuid TEXT PRIMARY KEY,
                bossbar_enabled INTEGER DEFAULT 1,
                scoreboard_enabled INTEGER DEFAULT 1
            );
        """);

        // =========================
        // 📦 UI STATE — generic key/value store for in-memory user data
        // that must survive a server restart (chat prefs, check state, etc.)
        // Namespaced so multiple systems can share one table without collisions.
        // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS ui_state (
                    namespace TEXT NOT NULL,
                    state_key TEXT NOT NULL,
                    state_value TEXT NOT NULL,
                    updated_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(namespace, state_key)
                );
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_ui_state_namespace
                ON ui_state(namespace);
            """);

        // =========================
        // 🔎 ACTIVE CHECKS — anti-cheat checks that survive a server restart
        // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS active_checks (
                    inspector_uuid TEXT PRIMARY KEY,
                    inspector_name TEXT NOT NULL DEFAULT '',
                    suspect_uuid TEXT NOT NULL,
                    suspect_name TEXT NOT NULL DEFAULT '',
                    inspector_world TEXT NOT NULL DEFAULT '',
                    inspector_x INTEGER NOT NULL DEFAULT 0,
                    inspector_y INTEGER NOT NULL DEFAULT 0,
                    inspector_z INTEGER NOT NULL DEFAULT 0,
                    inspector_yaw FLOAT NOT NULL DEFAULT 0,
                    inspector_pitch FLOAT NOT NULL DEFAULT 0,
                    started_at INTEGER NOT NULL DEFAULT 0
                );
            """);

        // =========================
        // 🔑 CODE PANEL KEYS
        // =========================
            st.execute("""
                CREATE TABLE IF NOT EXISTS code_panel_keys (
                    key_name TEXT PRIMARY KEY,
                    code TEXT NOT NULL,
                    command TEXT NOT NULL DEFAULT '',
                    max_attempts INTEGER DEFAULT -1,
                    attempts_used INTEGER DEFAULT 0,
                    expires_at INTEGER DEFAULT 0,
                    whitelist TEXT DEFAULT '',
                    blacklist TEXT DEFAULT ''
                );
            """);

        // =========================
        // 📝 NOTES
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS notes (
                player_uuid TEXT NOT NULL,
                slot_number INTEGER NOT NULL,
                content TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (player_uuid, slot_number)
            );
        """);

        // =========================
        // 🏠 PLAYER HOMES
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS player_homes (
                    uuid TEXT NOT NULL,
                    home_name TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT DEFAULT 0,
                    pitch FLOAT DEFAULT 0,
                    PRIMARY KEY(uuid, home_name)
                );
            """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_player_homes_uuid
                ON player_homes(uuid);
            """);

        // =========================
        // 👻 VANISHED PLAYERS (table, not config.yml)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS vanished_players (
                uuid TEXT PRIMARY KEY
            );
        """);

        // =========================
        // 🛡 OP WHITELIST — operator whitelist
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS op_whitelist (
                player_name TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            );
        """);

        // Helper table for storing the enabled flag
        st.execute("""
            CREATE TABLE IF NOT EXISTS op_whitelist_meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );
        """);
        st.execute("""
            INSERT OR IGNORE INTO op_whitelist_meta (key, value)
            VALUES ('enabled', 'false');
        """);

        // =========================
        // 🗺 STRUCTURE CHUNKS — chunks containing structure Markers
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS structure_chunks (
                world TEXT NOT NULL,
                cx INTEGER NOT NULL,
                cz INTEGER NOT NULL,
                PRIMARY KEY(world, cx, cz)
            );
        """);

        // =========================
        // 🏷 STRUCTURE MARKERS — FULL structure data (replaced Marker entities)
        // Source of truth: world + exact block coordinates → type + UUID of the structure.
        // All work happens from the RAM cache, but every entry is mirrored in the DB:
        // place/remove write immediately, plus a full re-save every 10 minutes
        // and on server shutdown.
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS structure_markers (
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                type TEXT NOT NULL,
                structure_uuid TEXT NOT NULL,
                PRIMARY KEY(world, x, y, z)
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_structure_markers_uuid
            ON structure_markers(structure_uuid);
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_structure_markers_world
            ON structure_markers(world);
        """);

        // =========================
        // 🔴 REDSTONE BLOCKS — permanently blocked redstone chunks (persist)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS redstone_blocks (
                block_number INTEGER PRIMARY KEY,
                world TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                blocked_at INTEGER NOT NULL,
                iterations INTEGER NOT NULL DEFAULT 0
            );
        """);

        // =========================
        // 🏗 BLOCK COLLAPSE — stickiness/weight of placed blocks (persist)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS block_collapse (
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                stickiness REAL NOT NULL DEFAULT 100,
                PRIMARY KEY(world, x, y, z)
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_block_collapse_world
            ON block_collapse(world);
        """);

        // =========================
        // 🔄 UPDATER STATE (last commit SHA / release tag)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS updater_state (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );
        """);

        // =========================
        // 🛠 MAINTENANCE WHITELIST — whitelist for the maintenance mode
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS maintenance_whitelist (
                player_name TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            );
        """);

        // Helper table for storing enabled/maintenance_meta
        // WITHOUT INSERT OR IGNORE — the migration from config.yml happens in
        // MaintenanceManager.loadFromDb() on the first run.
        st.execute("""
            CREATE TABLE IF NOT EXISTS maintenance_meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );
        """);

        // =========================
        // =========================
        // 📋 PLAYER VISITS — first join tracking
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS player_visits (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                first_join INTEGER NOT NULL DEFAULT (strftime('%s','now')),
                last_join INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            );
        """);

        // =========================
        // 📋 REPORTS — player reports
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reporter_uuid TEXT NOT NULL,
                reported_uuid TEXT NOT NULL,
                reason TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')),
                expires_at INTEGER NOT NULL,
                moderator_uuid TEXT DEFAULT '',
                verdict TEXT DEFAULT '',
                verdict_option TEXT DEFAULT '',
                moderated_at INTEGER DEFAULT 0
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_reports_status
            ON reports(status);
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_reports_reporter
            ON reports(reporter_uuid);
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_reports_reported
            ON reports(reported_uuid);
        """);

        // =========================
        // 📋 MOD REPORTS — report list for moderation
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS mod_reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                report_id INTEGER NOT NULL,
                name TEXT NOT NULL UNIQUE,
                FOREIGN KEY(report_id) REFERENCES reports(id) ON DELETE CASCADE
            );
        """);

        // =========================
        // 🛡 PUNISHMENTS — bans, mutes, kicks
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS punishments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                reason TEXT NOT NULL,
                ip_address TEXT DEFAULT '',
                hw_id TEXT DEFAULT '',
                punished_by TEXT NOT NULL,
                punished_at INTEGER NOT NULL,
                expires_at INTEGER DEFAULT 0,
                active INTEGER DEFAULT 1
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_punishments_uuid
            ON punishments(player_uuid);
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_punishments_type_active
            ON punishments(type, active);
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_punishments_ip
            ON punishments(ip_address);
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_punishments_hw
            ON punishments(hw_id);
        """);

        // =========================
        // ⚠ WARNS — warnings
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS warns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                reason TEXT NOT NULL,
                warned_by TEXT NOT NULL,
                warned_at INTEGER NOT NULL,
                expires_at INTEGER DEFAULT 0,
                ip_address TEXT DEFAULT '',
                hw_id TEXT DEFAULT ''
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_warns_uuid
            ON warns(player_uuid);
        """);

        // =========================
        // 📋 CUSTOM WHITELIST (UltimateImprovments, not vanilla)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS whitelist (
                player_name TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            );
        """);

        st.execute("""
            CREATE TABLE IF NOT EXISTS whitelist_meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );
        """);

        st.execute("""
            INSERT OR IGNORE INTO whitelist_meta (key, value)
            VALUES ('enabled', 'false');
        """);

        // =========================
        // 🏗 STRUCTURE INTEGRITY — stress/integrity of ender-chest data
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS structure_integrity (
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                stress REAL DEFAULT 0,
                integrity REAL DEFAULT 100,
                degradation_ticks INTEGER DEFAULT 0,
                PRIMARY KEY(world, x, y, z)
            );
        """);

        // =========================
        // 📋 BLACKLIST — blacklist
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS blacklist (
                player_name TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            );
        """);

        st.execute("""
            CREATE TABLE IF NOT EXISTS blacklist_meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );
        """);

        st.execute("""
            INSERT OR IGNORE INTO blacklist_meta (key, value)
            VALUES ('enabled', 'false');
        """);

        // =========================
        // 🤖 BOT PROTECTION COOLDOWNS (persist across restarts)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS bot_protection_cooldowns (
                uuid TEXT PRIMARY KEY,
                quit_time INTEGER NOT NULL
            );
        """);

        // =========================
        // 🏰 CLANS
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS clans (
                name TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                owner_uuid TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                has_home INTEGER DEFAULT 0,
                home_world TEXT DEFAULT '',
                home_x DOUBLE DEFAULT 0,
                home_y DOUBLE DEFAULT 0,
                home_z DOUBLE DEFAULT 0,
                home_yaw FLOAT DEFAULT 0,
                home_pitch FLOAT DEFAULT 0,
                description TEXT DEFAULT '',
                settings TEXT DEFAULT ''
            );
        """);

        // Migration for existing databases: CREATE TABLE IF NOT EXISTS won't add columns.
        try { st.execute("ALTER TABLE clans ADD COLUMN description TEXT DEFAULT ''"); } catch (Exception ignored) {}
        try { st.execute("ALTER TABLE clans ADD COLUMN settings TEXT DEFAULT ''"); } catch (Exception ignored) {}

        st.execute("""
            CREATE TABLE IF NOT EXISTS clan_members (
                clan_name TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                role TEXT NOT NULL,
                joined_at INTEGER NOT NULL,
                PRIMARY KEY (clan_name, player_uuid)
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_clan_members_uuid
            ON clan_members(player_uuid);
        """);

        // Migration: existing clan creators (owner_uuid) become the leader role.
        // Must run after clan_members exists — safe on fresh DBs (no rows to update).
        st.execute("""
            UPDATE clan_members SET role = 'leader'
            WHERE role != 'leader'
              AND player_uuid IN (SELECT owner_uuid FROM clans WHERE clans.name = clan_members.clan_name)
        """);

        st.execute("""
            CREATE TABLE IF NOT EXISTS clan_requests (
                clan_name TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                requested_at INTEGER NOT NULL,
                PRIMARY KEY (clan_name, player_uuid)
            );
            """);

        // Dependencies between clans
        try { st.execute("""
            CREATE TABLE IF NOT EXISTS clan_dependencies (
                main_clan TEXT NOT NULL PRIMARY KEY,
                dep_clan TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            );"""); } catch (Exception ignored) {}
        try { st.execute("""
            CREATE INDEX IF NOT EXISTS idx_dep_clan
            ON clan_dependencies(dep_clan);"""); } catch (Exception ignored) {}
        try { st.execute("""
            CREATE TABLE IF NOT EXISTS clan_dep_requests (
                from_clan TEXT NOT NULL,
                to_clan TEXT NOT NULL,
                requested_at INTEGER NOT NULL,
                PRIMARY KEY (from_clan, to_clan)
            );"""); } catch (Exception ignored) {}

            // Clan invites (add player via request system)
            try { st.execute("""
                CREATE TABLE IF NOT EXISTS clan_invites (
                    clan_name TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'member',
                    invited_by TEXT NOT NULL,
                    requested_at INTEGER NOT NULL,
                    PRIMARY KEY (clan_name, player_uuid)
                );"""); } catch (Exception ignored) {}

            // Dependency confirmation requests (depremove / depdisband)
            try { st.execute("""
                CREATE TABLE IF NOT EXISTS clan_dep_confirms (
                    action TEXT NOT NULL,
                    from_clan TEXT NOT NULL,
                    to_clan TEXT NOT NULL,
                    requested_at INTEGER NOT NULL,
                    PRIMARY KEY (action, from_clan, to_clan)
                );"""); } catch (Exception ignored) {}

        // =========================
        // 🗳 VOTES
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS votes (
                name TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                question TEXT NOT NULL,
                creator_uuid TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                ended INTEGER DEFAULT 0
            );
        """);

        st.execute("""
            CREATE TABLE IF NOT EXISTS vote_answers (
                vote_name TEXT NOT NULL,
                answer_index INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT DEFAULT '',
                PRIMARY KEY(vote_name, answer_index),
                FOREIGN KEY(vote_name) REFERENCES votes(name) ON DELETE CASCADE
            );
        """);

        st.execute("""
            CREATE TABLE IF NOT EXISTS vote_records (
                vote_name TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                answer_index INTEGER NOT NULL,
                PRIMARY KEY(vote_name, player_uuid),
                FOREIGN KEY(vote_name) REFERENCES votes(name) ON DELETE CASCADE
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_vote_records_name
            ON vote_records(vote_name);
        """);

        // =========================
        // 📡 WIRELESS REDSTONE — linked lamps
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS wireless_links (
                world TEXT NOT NULL,
                x1 INTEGER NOT NULL,
                y1 INTEGER NOT NULL,
                z1 INTEGER NOT NULL,
                x2 INTEGER NOT NULL,
                y2 INTEGER NOT NULL,
                z2 INTEGER NOT NULL,
                PRIMARY KEY(world, x1, y1, z1, x2, y2, z2)
            );
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_wireless_links_world
            ON wireless_links(world);
        """);

        // =========================
        // 🛡 TURRETS — end crystal turrets
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS turrets (
                world TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                enabled INTEGER DEFAULT 0,
                mode TEXT DEFAULT 'blacklist',
                entries TEXT DEFAULT '',
                owner TEXT DEFAULT '',
                PRIMARY KEY(world, x, y, z)
            );
        """);

        // 🛡 TURRETS — owner column migration (for old DBs)
        try {
            st.execute("ALTER TABLE turrets ADD COLUMN owner TEXT DEFAULT ''");
        } catch (Exception ignored) {
            // Column already exists — this is fine
        }

        // =========================
        // 📋 CMD LOG — /ui cmdlog <on/off> toggle (persist across restarts)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS cmdlog_meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );
        """);

        st.execute("""
            INSERT OR IGNORE INTO cmdlog_meta (key, value)
            VALUES ('enabled', 'false');
        """);

        // Initialize the latest_commit_sha and installed_tag rows if missing
        st.execute("""
            INSERT OR IGNORE INTO updater_state (key, value)
            VALUES ('latest_commit_sha', '');
        """);
        st.execute("""
            INSERT OR IGNORE INTO updater_state (key, value)
            VALUES ('installed_tag', '');
        """);

        // Migration: if there was an old latest_tag (from previous versions) — move it to installed_tag
        st.execute("""
            UPDATE updater_state SET value = (
                SELECT value FROM updater_state WHERE key = 'latest_tag' AND value != ''
            ) WHERE key = 'installed_tag' AND value = ''
            AND EXISTS (SELECT 1 FROM updater_state WHERE key = 'latest_tag' AND value != '');
        """);

        } catch (Exception e) {
            Main.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "[DB] Table initialization failed", e);
        }
    }
}