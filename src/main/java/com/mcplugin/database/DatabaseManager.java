package com.mcplugin.database;

import com.mcplugin.Main;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static Connection connection;
    private static File dbFile;

    // =========================
    // CONNECT
    // =========================
    public static synchronized void connect() {

        try {

            if (connection != null && !connection.isClosed()) {
                return;
            }

            File dataFolder = Main.getInstance().getDataFolder();
            dbFile = new File(dataFolder, "database.db");

            Class.forName("org.sqlite.JDBC");

            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + dbFile.getAbsolutePath()
            );

            applyPragmas();

            Main.getInstance().getLogger().fine("[Database] SQLite connected.");

        } catch (Exception e) {

            Main.getInstance().getLogger().severe(
                    "[Database] Connection failed: " + e.getMessage()
            );

            e.printStackTrace();
        }
    }

    // =========================
    // PRAGMAS (separate clean method)
    // =========================
    private static void applyPragmas() {

        try (Statement st = connection.createStatement()) {

            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA temp_store=MEMORY;");
            st.execute("PRAGMA foreign_keys=ON;");
            st.execute("PRAGMA busy_timeout=5000;");
            st.execute("PRAGMA cache_size=-10000;");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // =========================
    // GET CONNECTION
    // =========================
    public static synchronized Connection getConnection() {

        try {

            if (connection == null || connection.isClosed()) {
                connect();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return connection;
    }

    // =========================
    // IS CONNECTED
    // =========================
    public static boolean isConnected() {

        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // =========================
    // CLOSE
    // =========================
    public static synchronized void close() {

        try {

            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            connection = null;

            Main.getInstance().getLogger().fine("[Database] SQLite closed.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // =========================
    // DB FILE
    // =========================
    public static File getDbFile() {
        return dbFile;
    }
}