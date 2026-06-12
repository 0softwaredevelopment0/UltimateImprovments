package com.mcplugin.database;

import java.sql.Connection;
import java.sql.Statement;

public class DatabaseInit {

    public static void init() {

        try (Connection con = DatabaseManager.getConnection();
             Statement st = con.createStatement()) {

            // =========================
            // ⚡ CABLES
            // =========================
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}