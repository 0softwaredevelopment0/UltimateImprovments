#!/usr/bin/env node
/**
 * Codebuff Session Memory — сохраняет контекст сессий Codebuff в SQLite.
 * 
 * Использование:
 *   node codebuff-memory.js init                    — создать/обновить структуру БД
 *   node codebuff-memory.js save <type> [key] <val> — сохранить запись
 *       type: preference | decision | task | note | summary
 *   node codebuff-memory.js load                    — загрузить весь контекст (JSON)
 *   node codebuff-memory.js load-prefs              — только preferences (JSON)
 *   node codebuff-memory.js save-msg <role> <text>  — сохранить сообщение
 *   node codebuff-memory.js export                  — экспорт всего в JSON (stdout)
 *   node codebuff-memory.js end-session [summary]   — завершить сессию
 */

const Database = require('better-sqlite3');
const path = require('path');

const DB_PATH = path.join(__dirname, '.codebuff-memory.db');

// ── DB init ──────────────────────────────────────────────────────────

function openDb() {
    const db = new Database(DB_PATH);
    db.pragma('journal_mode = WAL');   // WAL — быстрая запись, конкурентное чтение
    db.pragma('foreign_keys = ON');
    return db;
}

function createTables(db) {
    db.exec(`
        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            started_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            ended_at TEXT,
            summary TEXT
        );
        CREATE TABLE IF NOT EXISTS context_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id INTEGER NOT NULL,
            entry_type TEXT NOT NULL CHECK(entry_type IN ('preference','decision','task','note','summary')),
            key TEXT,
            value TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY (session_id) REFERENCES sessions(id)
        );
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id INTEGER NOT NULL,
            role TEXT NOT NULL CHECK(role IN ('user','assistant')),
            content TEXT NOT NULL,
            timestamp TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY (session_id) REFERENCES sessions(id)
        );
    `);
}

function getOrCreateSession(db) {
    // Ищем незавершённую сессию
    const row = db.prepare(
        "SELECT id FROM sessions WHERE ended_at IS NULL ORDER BY id DESC LIMIT 1"
    ).get();
    if (row) return row.id;
    // Создаём новую
    const info = db.prepare("INSERT INTO sessions DEFAULT VALUES").run();
    return info.lastInsertRowid;
}

// ── helpers ──────────────────────────────────────────────────────────

const VALID_TYPES = ['preference', 'decision', 'task', 'note', 'summary'];
const VALID_ROLES = ['user', 'assistant'];

function usage(msg) {
    console.error(`✗ ${msg}`);
    console.error('Доступные команды: init, save, save-msg, load, load-prefs, export, end-session');
    process.exit(1);
}

// ── main ─────────────────────────────────────────────────────────────

function main() {
    const cmd = process.argv[2];

    // ── init ───────────────────────────────────────────────────────
    if (!cmd || cmd === 'init') {
        const db = openDb();
        createTables(db);
        db.close();
        console.log(`✓ База данных инициализирована: ${DB_PATH}`);
        return;
    }

    // ── остальные команды требуют существующей БД ──────────────────
    const { existsSync } = require('fs');
    if (!existsSync(DB_PATH)) {
        usage('БД не найдена. Сначала выполни: node codebuff-memory.js init');
    }

    const db = openDb();
    createTables(db);
    const sessionId = getOrCreateSession(db);

    switch (cmd) {

        // ── save ───────────────────────────────────────────────────
        case 'save': {
            const entryType = process.argv[3];
            if (!entryType || !VALID_TYPES.includes(entryType)) {
                usage(`Укажи тип: ${VALID_TYPES.join(' | ')}`);
            }

            let key = null;
            let value;
            if (process.argv[5]) {
                key = process.argv[4];
                value = process.argv.slice(5).join(' ');
            } else {
                value = process.argv.slice(4).join(' ');
            }

            const stmt = db.prepare(
                `INSERT INTO context_entries (session_id, entry_type, key, value)
                 VALUES (?, ?, ?, ?)`
            );
            stmt.run(sessionId, entryType, key, value);

            const preview = value.length > 80 ? value.slice(0, 80) + '…' : value;
            console.log(`✓ Сохранено [${entryType}]${key ? ` "${key}"` : ''}: ${preview}`);
            break;
        }

        // ── save-msg ───────────────────────────────────────────────
        case 'save-msg': {
            const role = process.argv[3];
            const text = process.argv.slice(4).join(' ');
            if (!role || !VALID_ROLES.includes(role)) {
                usage('Укажи role: user или assistant');
            }
            if (!text) {
                usage('Укажи текст сообщения');
            }

            const stmt = db.prepare(
                `INSERT INTO messages (session_id, role, content) VALUES (?, ?, ?)`
            );
            stmt.run(sessionId, role, text);

            console.log(`✓ Сообщение [${role}] сохранено (${text.length} симв.)`);
            break;
        }

        // ── load ───────────────────────────────────────────────────
        case 'load': {
            const sessions = db.prepare(`
                SELECT id, started_at, ended_at, summary
                FROM sessions ORDER BY id DESC LIMIT 5
            `).all();

            const entries = db.prepare(`
                SELECT ce.id, s.id AS session_id, s.started_at AS session_started,
                       ce.entry_type AS type, ce.key, ce.value, ce.created_at
                FROM context_entries ce
                JOIN sessions s ON s.id = ce.session_id
                ORDER BY ce.id DESC LIMIT 100
            `).all();

            const msgCount = db.prepare(
                "SELECT COUNT(*) AS cnt FROM messages"
            ).get().cnt;

            console.log(JSON.stringify({ sessions, entries, messages_count: msgCount }, null, 2));
            break;
        }

        // ── load-prefs ─────────────────────────────────────────────
        case 'load-prefs': {
            const prefs = db.prepare(`
                SELECT key, value, created_at
                FROM context_entries
                WHERE entry_type = 'preference'
                ORDER BY id DESC
            `).all();
            console.log(JSON.stringify(prefs, null, 2));
            break;
        }

        // ── export ─────────────────────────────────────────────────
        case 'export': {
            const sessions = db.prepare(
                "SELECT id, started_at, ended_at, summary FROM sessions ORDER BY id"
            ).all();
            const entries = db.prepare(
                "SELECT ce.id, ce.session_id, ce.entry_type AS type, ce.key, ce.value, ce.created_at FROM context_entries ce ORDER BY ce.id"
            ).all();
            const messages = db.prepare(
                "SELECT m.id, m.session_id, m.role, m.content, m.timestamp FROM messages m ORDER BY m.id"
            ).all();

            console.log(JSON.stringify({
                exported_at: new Date().toISOString(),
                db_path: DB_PATH,
                sessions,
                context_entries: entries,
                messages
            }, null, 2));
            break;
        }

        // ── end-session ────────────────────────────────────────────
        case 'end-session': {
            const summary = process.argv.slice(3).join(' ') || null;
            db.prepare(`
                UPDATE sessions
                SET ended_at = datetime('now', 'localtime'),
                    summary = COALESCE(? , summary)
                WHERE id = ?
            `).run(summary, sessionId);

            console.log(`✓ Сессия #${sessionId} завершена`);
            break;
        }

        default:
            usage(`Неизвестная команда: ${cmd}`);
    }

    db.close();
}

main();
