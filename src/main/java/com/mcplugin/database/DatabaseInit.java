package com.mcplugin.database;

import com.mcplugin.core.Main;
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
            // ⚛ REACTOR WEAR COLUMN MIGRATION (для старых БД)
            // =========================
            try {
                st.execute("ALTER TABLE reactors ADD COLUMN reactor_wear INTEGER DEFAULT 0");
            } catch (Exception ignored) {
                // Column already exists — это нормально
            }

            // =========================
            // ⚛ ENERGY GENERATED COLUMN MIGRATION (для старых БД)
            // =========================
            try {
                st.execute("ALTER TABLE reactors ADD COLUMN energy_generated INTEGER DEFAULT 0");
            } catch (Exception ignored) {
                // Column already exists — это нормально
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
        // 🦅 ELYTRA BOOST DISABLED (persist /mp togglefly state)
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
            // 🔐 AUTH (регистрация/логин)
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
        // 👻 VANISHED PLAYERS (таблица, а не config.yml)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS vanished_players (
                uuid TEXT PRIMARY KEY
            );
        """);

        // =========================
        // 🛡 OP WHITELIST — белый список операторов
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS op_whitelist (
                player_name TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            );
        """);

        // Служебная таблица для хранения enabled-флага
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
        // 🗺 STRUCTURE CHUNKS — чанки, в которых есть Marker'ы структур
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
        // 🔄 UPDATER STATE (последний SHA коммита / тег релиза)
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS updater_state (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );
        """);

        // =========================
        // 🛠 MAINTENANCE WHITELIST — белый список для режима техработ
        // =========================
        st.execute("""
            CREATE TABLE IF NOT EXISTS maintenance_whitelist (
                player_name TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            );
        """);

        // Служебная таблица для хранения enabled/maintenance_meta
        // БЕЗ INSERT OR IGNORE — миграция из config.yml происходит в
        // MaintenanceManager.loadFromDb() при первом запуске.
        st.execute("""
            CREATE TABLE IF NOT EXISTS maintenance_meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );
        """);

        // =========================
        // =========================
        // 📋 PLAYER VISITS — отслеживание первого входа
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
        // 📋 REPORTS — жалобы на игроков
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
        // 📋 MOD REPORTS — список репортов для модерации
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
        // 🛡 PUNISHMENTS — баны, муты, кики
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
        // ⚠ WARNS — предупреждения
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
        // 📋 CUSTOM WHITELIST (MC-Plugin, не ванильный)
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
        // 🏗 STRUCTURE INTEGRITY — stress/integrity данных эндер-сундуков
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
        // 📋 BLACKLIST — чёрный список
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
        // 📡 WIRELESS REDSTONE — связанные лампы
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

        // Инициализация строк latest_commit_sha и installed_tag, если их нет
        st.execute("""
            INSERT OR IGNORE INTO updater_state (key, value)
            VALUES ('latest_commit_sha', '');
        """);
        st.execute("""
            INSERT OR IGNORE INTO updater_state (key, value)
            VALUES ('installed_tag', '');
        """);

        // Миграция: если был старый latest_tag (c предыдущих версий) — переносим в installed_tag
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