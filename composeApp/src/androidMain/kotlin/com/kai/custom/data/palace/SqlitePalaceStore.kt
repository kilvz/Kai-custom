package com.kai.custom.data.palace

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val DB_NAME = "kai_palace.db"
private const val DB_VERSION = 3

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

private const val SQL_CREATE_WINGS = """
    CREATE TABLE IF NOT EXISTS wings (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL UNIQUE,
        description TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL
    )
"""

private const val SQL_CREATE_ROOMS = """
    CREATE TABLE IF NOT EXISTS rooms (
        id TEXT PRIMARY KEY,
        wing_id TEXT NOT NULL REFERENCES wings(id) ON DELETE CASCADE,
        name TEXT NOT NULL,
        description TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL,
        UNIQUE(name, wing_id)
    )
"""

private const val SQL_CREATE_DRAWERS = """
    CREATE TABLE IF NOT EXISTS drawers (
        id TEXT PRIMARY KEY,
        room_id TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
        content TEXT NOT NULL,
        content_hash TEXT NOT NULL DEFAULT '',
        source_file TEXT,
        metadata TEXT NOT NULL DEFAULT '{}',
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
    )
"""

private const val SQL_CREATE_DRAWERS_FTS = """
    CREATE VIRTUAL TABLE IF NOT EXISTS drawers_fts USING fts5(
        content,
        room_id UNINDEXED,
        content='drawers',
        content_rowid='rowid',
        tokenize='porter unicode61'
    )
"""

private const val SQL_CREATE_KG = """
    CREATE TABLE IF NOT EXISTS kg_facts (
        id TEXT PRIMARY KEY,
        subject TEXT NOT NULL,
        predicate TEXT NOT NULL,
        object TEXT NOT NULL,
        valid_from INTEGER,
        valid_to INTEGER,
        source_drawer_id TEXT REFERENCES drawers(id) ON DELETE SET NULL,
        created_at INTEGER NOT NULL
    )
"""

private const val SQL_CREATE_KG_INDEX = """
    CREATE INDEX IF NOT EXISTS idx_kg_subject ON kg_facts(subject)
"""
private const val SQL_CREATE_KG_OBJECT_INDEX = """
    CREATE INDEX IF NOT EXISTS idx_kg_object ON kg_facts(object)
"""

private const val SQL_CREATE_DRAWERS_ROOM_INDEX = """
    CREATE INDEX IF NOT EXISTS idx_drawers_room ON drawers(room_id)
"""

private const val SQL_CREATE_TRIGGER_DRAWER_INSERT = """
    CREATE TRIGGER IF NOT EXISTS drawers_ai AFTER INSERT ON drawers BEGIN
        INSERT INTO drawers_fts(rowid, content, room_id) VALUES (new.rowid, new.content, new.room_id);
    END
"""

private const val SQL_CREATE_TRIGGER_DRAWER_DELETE = """
    CREATE TRIGGER IF NOT EXISTS drawers_ad AFTER DELETE ON drawers BEGIN
        INSERT INTO drawers_fts(drawers_fts, rowid, content, room_id) VALUES('delete', old.rowid, old.content, old.room_id);
    END
"""

private const val SQL_CREATE_TRIGGER_DRAWER_UPDATE = """
    CREATE TRIGGER IF NOT EXISTS drawers_au AFTER UPDATE ON drawers BEGIN
        INSERT INTO drawers_fts(drawers_fts, rowid, content, room_id) VALUES('delete', old.rowid, old.content, old.room_id);
        INSERT INTO drawers_fts(rowid, content, room_id) VALUES (new.rowid, new.content, new.room_id);
    END
"""

private const val SQL_BACKUP_TRACKING = """
    CREATE TABLE IF NOT EXISTS backup_tracking (
        id TEXT PRIMARY KEY,
        drive_file_id TEXT,
        timestamp INTEGER NOT NULL,
        size_bytes INTEGER,
        status TEXT NOT NULL DEFAULT 'pending'
    )
"""

internal class PalaceDatabase(context: Context) : SQLiteOpenHelper(
    context, DB_NAME, null, DB_VERSION
) {
    private var _fts5Available = true

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_WINGS)
        db.execSQL(SQL_CREATE_ROOMS)
        db.execSQL(SQL_CREATE_DRAWERS)
        try {
            db.execSQL(SQL_CREATE_DRAWERS_FTS)
        } catch (_: Exception) {
            _fts5Available = false
        }
        db.execSQL(SQL_CREATE_KG)
        db.execSQL(SQL_CREATE_KG_INDEX)
        db.execSQL(SQL_CREATE_KG_OBJECT_INDEX)
        db.execSQL(SQL_CREATE_DRAWERS_ROOM_INDEX)
        if (_fts5Available) {
            db.execSQL(SQL_CREATE_TRIGGER_DRAWER_INSERT)
            db.execSQL(SQL_CREATE_TRIGGER_DRAWER_DELETE)
            db.execSQL(SQL_CREATE_TRIGGER_DRAWER_UPDATE)
        }
        db.execSQL(SQL_BACKUP_TRACKING)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS backup_tracking")
            db.execSQL("DROP TRIGGER IF EXISTS drawers_au")
            db.execSQL("DROP TRIGGER IF EXISTS drawers_ad")
            db.execSQL("DROP TRIGGER IF EXISTS drawers_ai")
            db.execSQL("DROP TABLE IF EXISTS drawers_fts")
            db.execSQL("DROP INDEX IF EXISTS idx_drawers_room")
            db.execSQL("DROP INDEX IF EXISTS idx_kg_object")
            db.execSQL("DROP INDEX IF EXISTS idx_kg_subject")
            db.execSQL("DROP TABLE IF EXISTS kg_facts")
            db.execSQL("DROP TABLE IF EXISTS drawers")
            db.execSQL("DROP TABLE IF EXISTS rooms")
            db.execSQL("DROP TABLE IF EXISTS wings")
            onCreate(db)
        }
    }

    fun isFts5Available(): Boolean = _fts5Available
}

private fun contentHash(content: String): String {
    val digest = MessageDigest.getInstance("MD5")
    return digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
}

class SqlitePalaceStore(context: Context) : PalaceStore {

    private val dbHelper = PalaceDatabase(context)
    private var initialized = false
    private var fts5Available = true

    override fun isReady(): Boolean = initialized

    override fun initialize() {
        val db = dbHelper.writableDatabase
        db.rawQuery("PRAGMA journal_mode=WAL", null).use { it.moveToFirst() }
        db.execSQL("PRAGMA foreign_keys=ON")
        fts5Available = dbHelper.isFts5Available()
        if (fts5Available) {
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='drawers_fts'", null).use { c ->
                fts5Available = c.moveToFirst()
            }
        }
        if (fts5Available) {
            try {
                db.rawQuery("SELECT count(*) FROM drawers_fts", null).use { it.moveToFirst() }
            } catch (_: Exception) {
                fts5Available = false
            }
        }
        seedDefaults(db)
        initialized = true
    }

    private fun seedDefaults(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        for (wing in PalaceConfig.defaultWings) {
            db.execSQL(
                "INSERT OR IGNORE INTO wings (id, name, description, created_at) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(wing.id, wing.name, wing.description, now),
            )
        }
        for ((wingId, rooms) in PalaceConfig.defaultRooms) {
            for ((roomId, desc) in rooms) {
                db.execSQL(
                    "INSERT OR IGNORE INTO rooms (id, wing_id, name, description, created_at) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any>(roomId, wingId, roomId, desc, now),
                )
            }
        }
    }

    private fun db(): SQLiteDatabase = dbHelper.writableDatabase

    override fun ensureWing(wing: Wing): Wing {
        val existing = getWing(wing.id)
        if (existing != null) return existing
        db().execSQL(
            "INSERT OR IGNORE INTO wings (id, name, description, created_at) VALUES (?, ?, ?, ?)",
            arrayOf<Any>(wing.id, wing.name, wing.description, wing.createdAt),
        )
        return wing
    }

    override fun getWing(wingId: String): Wing? {
        db().rawQuery("SELECT id, name, description, created_at FROM wings WHERE id = ?", arrayOf(wingId)).use { c ->
            if (c.moveToFirst()) {
                return Wing(c.getString(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return null
    }

    override fun getAllWings(): List<Wing> {
        val result = mutableListOf<Wing>()
        db().rawQuery("SELECT id, name, description, created_at FROM wings ORDER BY created_at", null).use { c ->
            while (c.moveToNext()) {
                result.add(Wing(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)))
            }
        }
        return result
    }

    override fun ensureRoom(wingId: String, roomId: String, name: String, description: String): Room {
        val existing = run {
            db().rawQuery(
                "SELECT id, wing_id, name, description, created_at FROM rooms WHERE id = ? AND wing_id = ?",
                arrayOf(roomId, wingId),
            ).use { c ->
                if (c.moveToFirst()) {
                    Room(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))
                } else null
            }
        }
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        db().execSQL(
            "INSERT OR IGNORE INTO rooms (id, wing_id, name, description, created_at) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any>(roomId, wingId, name, description, now),
        )
        return Room(roomId, wingId, name, description, now)
    }

    override fun getRooms(wingId: String): List<Room> {
        val result = mutableListOf<Room>()
        db().rawQuery(
            "SELECT id, wing_id, name, description, created_at FROM rooms WHERE wing_id = ? ORDER BY created_at",
            arrayOf(wingId),
        ).use { c ->
            while (c.moveToNext()) {
                result.add(Room(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
            }
        }
        return result
    }

    override fun putDrawer(drawer: Drawer): Drawer {
        val hash = contentHash(drawer.content)
        val metaJson = json.encodeToString(drawer.metadata)
        val now = System.currentTimeMillis()
        val existing = getDrawer(drawer.id)
        if (existing != null) {
            val cv = ContentValues().apply {
                put("content", drawer.content)
                put("content_hash", hash)
                put("source_file", drawer.sourceFile)
                put("metadata", metaJson)
                put("updated_at", now)
            }
            db().update("drawers", cv, "id = ?", arrayOf(drawer.id))
            return drawer.copy(updatedAt = now)
        }
        db().execSQL(
            "INSERT INTO drawers (id, room_id, content, content_hash, source_file, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(drawer.id, drawer.roomId, drawer.content, hash, drawer.sourceFile, metaJson, drawer.createdAt, drawer.updatedAt),
        )
        return drawer
    }

    override fun getDrawer(id: String): Drawer? {
        db().rawQuery(
            "SELECT d.id, r.wing_id, d.room_id, d.content, d.source_file, d.metadata, d.created_at, d.updated_at FROM drawers d JOIN rooms r ON r.id = d.room_id WHERE d.id = ?",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) {
                return cursorToDrawer(c)
            }
        }
        return null
    }

    override fun getDrawersByRoom(wingId: String, roomId: String): List<Drawer> {
        val result = mutableListOf<Drawer>()
        db().rawQuery(
            "SELECT d.id, r.wing_id, d.room_id, d.content, d.source_file, d.metadata, d.created_at, d.updated_at FROM drawers d JOIN rooms r ON r.id = d.room_id WHERE r.wing_id = ? AND d.room_id = ? ORDER BY d.created_at DESC",
            arrayOf(wingId, roomId),
        ).use { c ->
            while (c.moveToNext()) {
                result.add(cursorToDrawer(c))
            }
        }
        return result
    }

    override fun getDrawerByMetadataKey(key: String, value: String): Drawer? {
        try {
            val path = "$.\"${key.replace("\"", "\"\"")}\""
            db().rawQuery(
                "SELECT d.id, r.wing_id, d.room_id, d.content, d.source_file, d.metadata, d.created_at, d.updated_at FROM drawers d JOIN rooms r ON r.id = d.room_id WHERE json_extract(d.metadata, ?) = ? LIMIT 1",
                arrayOf(path, value),
            ).use { c ->
                if (c.moveToFirst()) return cursorToDrawer(c)
            }
        } catch (_: Exception) {
            // json_extract not available (rare), fall back to in-memory filter
            return getAllDrawers().firstOrNull { drawer ->
                drawer.metadata[key] == value
            }
        }
        return null
    }

    override fun getAllDrawers(): List<Drawer> {
        val result = mutableListOf<Drawer>()
        db().rawQuery(
            "SELECT d.id, r.wing_id, d.room_id, d.content, d.source_file, d.metadata, d.created_at, d.updated_at FROM drawers d JOIN rooms r ON r.id = d.room_id ORDER BY d.updated_at DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result.add(cursorToDrawer(c))
            }
        }
        return result
    }

    override fun deleteDrawer(id: String): Boolean {
        return db().delete("drawers", "id = ?", arrayOf(id)) > 0
    }

    override fun countDrawers(): Long {
        db().rawQuery("SELECT COUNT(*) FROM drawers", null).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return 0
    }

    override fun searchDrawers(query: String, limit: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        if (!fts5Available) {
            val pattern = "%${query.replace("'", "''")}%"
            val sql = """
                SELECT d.id, r.wing_id, d.room_id, d.content, d.source_file, d.metadata, d.created_at, d.updated_at
                FROM drawers d
                JOIN rooms r ON r.id = d.room_id
                WHERE d.content LIKE ?
                LIMIT ?
            """
            val result = mutableListOf<SearchResult>()
            db().rawQuery(sql, arrayOf(pattern, limit.toString())).use { c ->
                while (c.moveToNext()) {
                    val drawer = cursorToDrawer(c)
                    result.add(SearchResult(drawer, 0.5, ""))
                }
            }
            return result
        }
        val safeQuery = query.replace("'", "''")
        val sql = """
            SELECT d.id, r.wing_id, d.room_id, d.content, d.source_file, d.metadata, d.created_at, d.updated_at,
                   rank, snippet(drawers_fts, 0, '<b>', '</b>', '...', 40) AS snippet
            FROM drawers_fts
            JOIN drawers d ON d.rowid = drawers_fts.rowid
            JOIN rooms r ON r.id = d.room_id
            WHERE drawers_fts MATCH ?
            ORDER BY rank
            LIMIT ?
        """
        val result = mutableListOf<SearchResult>()
        db().rawQuery(sql, arrayOf(safeQuery, limit.toString())).use { c ->
            while (c.moveToNext()) {
                val drawer = cursorToDrawer(c)
                val score = 1.0 - c.getDouble(8).coerceIn(0.0, 1.0)
                val snippet = c.getString(9) ?: ""
                result.add(SearchResult(drawer, score, snippet))
            }
        }
        return result
    }

    override fun putFact(fact: KGFact): KGFact {
        val existing = run {
            db().rawQuery("SELECT id FROM kg_facts WHERE id = ?", arrayOf(fact.id)).use { c -> c.moveToFirst() }
        }
        if (existing) {
            val cv = ContentValues().apply {
                put("subject", fact.subject)
                put("predicate", fact.predicate)
                put("object", fact.`object`)
                put("valid_from", fact.validFrom)
                put("valid_to", fact.validTo)
                put("source_drawer_id", fact.sourceDrawerId)
            }
            db().update("kg_facts", cv, "id = ?", arrayOf(fact.id))
        } else {
            db().execSQL(
                "INSERT INTO kg_facts (id, subject, predicate, object, valid_from, valid_to, source_drawer_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(fact.id, fact.subject, fact.predicate, fact.`object`, fact.validFrom, fact.validTo, fact.sourceDrawerId, fact.createdAt),
            )
        }
        return fact
    }

    override fun getFactsBySubject(subject: String): List<KGFact> {
        val result = mutableListOf<KGFact>()
        db().rawQuery(
            "SELECT id, subject, predicate, object, valid_from, valid_to, source_drawer_id, created_at FROM kg_facts WHERE subject = ? ORDER BY created_at DESC",
            arrayOf(subject),
        ).use { c ->
            while (c.moveToNext()) result.add(cursorToKGFact(c))
        }
        return result
    }

    override fun getFactsByObject(`object`: String): List<KGFact> {
        val result = mutableListOf<KGFact>()
        db().rawQuery(
            "SELECT id, subject, predicate, object, valid_from, valid_to, source_drawer_id, created_at FROM kg_facts WHERE object = ? ORDER BY created_at DESC",
            arrayOf(`object`),
        ).use { c ->
            while (c.moveToNext()) result.add(cursorToKGFact(c))
        }
        return result
    }

    override fun queryKGE(relation: String?, limit: Int): List<KGFact> {
        val result = mutableListOf<KGFact>()
        val sql = if (relation != null) {
            "SELECT id, subject, predicate, object, valid_from, valid_to, source_drawer_id, created_at FROM kg_facts WHERE predicate = ? ORDER BY created_at DESC LIMIT ?"
        } else {
            "SELECT id, subject, predicate, object, valid_from, valid_to, source_drawer_id, created_at FROM kg_facts ORDER BY created_at DESC LIMIT ?"
        }
        val args = if (relation != null) arrayOf(relation, limit.toString()) else arrayOf(limit.toString())
        db().rawQuery(sql, args).use { c ->
            while (c.moveToNext()) result.add(cursorToKGFact(c))
        }
        return result
    }

    override fun deleteFact(id: String): Boolean {
        return db().delete("kg_facts", "id = ?", arrayOf(id)) > 0
    }

    override fun getExportData(): ByteArray {
        val allDrawers = getAllDrawers()
        val allFacts = queryKGE(null, Int.MAX_VALUE)
        val export = PalaceExport(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            drawers = allDrawers.map { d ->
                DrawerExport(
                    id = d.id, wingId = d.wingId, roomId = d.roomId,
                    content = d.content, sourceFile = d.sourceFile,
                    metadata = d.metadata, createdAt = d.createdAt, updatedAt = d.updatedAt,
                )
            },
            kgFacts = allFacts.map { f ->
                FactExport(
                    id = f.id, subject = f.subject, predicate = f.predicate,
                    `object` = f.`object`, validFrom = f.validFrom,
                    validTo = f.validTo, sourceDrawerId = f.sourceDrawerId,
                    createdAt = f.createdAt,
                )
            },
        )
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(json.encodeToString(export).toByteArray())
        }
        return output.toByteArray()
    }

    override fun importFromData(data: ByteArray) {
        val db = db()
        db.beginTransaction()
        try {
            val text = GZIPInputStream(ByteArrayInputStream(data)).readBytes().decodeToString()
            val export = json.decodeFromString<PalaceExport>(text)
            if (export.version < 1) throw IllegalArgumentException("Unsupported export version: ${export.version}")

            for (d in export.drawers) {
                ensureRoom(d.wingId, d.roomId, d.roomId)
                putDrawer(Drawer(d.id, d.wingId, d.roomId, d.content, d.sourceFile, d.metadata, d.createdAt, d.updatedAt))
            }
            for (f in export.kgFacts) {
                putFact(KGFact(f.id, f.subject, f.predicate, f.`object`, f.validFrom, f.validTo, f.sourceDrawerId, f.createdAt))
            }

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            android.util.Log.e("PalaceStore", "Import failed", e)
            throw e
        } finally {
            db.endTransaction()
        }
    }

    private fun cursorToDrawer(c: android.database.Cursor): Drawer {
        val id = c.getString(0)
        val wingId = c.getString(1)
        val roomId = c.getString(2)
        val content = c.getString(3)
        val sourceFile = c.getString(4)
        val metaJson = c.getString(5) ?: "{}"
        val metadata = try {
            json.decodeFromString<Map<String, String>>(metaJson)
        } catch (_: Exception) {
            emptyMap()
        }
        val createdAt = c.getLong(6)
        val updatedAt = c.getLong(7)
        return Drawer(id, wingId, roomId, content, sourceFile, metadata, createdAt, updatedAt)
    }

    private fun cursorToKGFact(c: android.database.Cursor): KGFact {
        val id = c.getString(0)
        val subject = c.getString(1)
        val predicate = c.getString(2)
        val obj = c.getString(3)
        val validFrom = if (c.isNull(4)) null else c.getLong(4)
        val validTo = if (c.isNull(5)) null else c.getLong(5)
        val sourceDrawerId = c.getString(6)
        val createdAt = c.getLong(7)
        return KGFact(id, subject, predicate, obj, validFrom, validTo, sourceDrawerId, createdAt)
    }

    fun close() {
        dbHelper.close()
    }
}
