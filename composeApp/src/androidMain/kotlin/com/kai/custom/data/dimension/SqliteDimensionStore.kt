package com.kai.custom.data.dimension

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

private const val DB_NAME = "kai_dimension.db"
private const val DB_VERSION = 4

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

private const val SQL_CREATE_REALMS = """
    CREATE TABLE IF NOT EXISTS realms (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL UNIQUE,
        description TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL
    )
"""

private const val SQL_CREATE_DOMAINS = """
    CREATE TABLE IF NOT EXISTS domains (
        id TEXT NOT NULL,
        realm TEXT NOT NULL,
        name TEXT NOT NULL,
        description TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL,
        PRIMARY KEY (id, realm),
        FOREIGN KEY (realm) REFERENCES realms(id)
    )
"""

private const val SQL_CREATE_ENTITIES = """
    CREATE TABLE IF NOT EXISTS entities (
        id TEXT PRIMARY KEY,
        realm TEXT NOT NULL,
        domain TEXT NOT NULL,
        content TEXT NOT NULL DEFAULT '',
        source_file TEXT,
        metadata TEXT NOT NULL DEFAULT '{}',
        content_hash TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        FOREIGN KEY (realm) REFERENCES realms(id)
    )
"""

private const val SQL_CREATE_KG_FACTS = """
    CREATE TABLE IF NOT EXISTS kg_facts (
        id TEXT PRIMARY KEY,
        subject TEXT NOT NULL,
        predicate TEXT NOT NULL,
        object TEXT NOT NULL,
        valid_from INTEGER,
        valid_to INTEGER,
        source_entity_id TEXT,
        created_at INTEGER NOT NULL
    )
"""

class SqliteDimensionStore(context: Context) : DimensionStore {

    private var schemaResetMessage: String? = null

    private val dbHelper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(SQL_CREATE_REALMS)
            db.execSQL(SQL_CREATE_DOMAINS)
            db.execSQL(SQL_CREATE_ENTITIES)
            db.execSQL(SQL_CREATE_KG_FACTS)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE entities ADD COLUMN embedding TEXT")
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE entities ADD COLUMN protected INTEGER NOT NULL DEFAULT 0")
            }
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS kg_facts")
            db.execSQL("DROP TABLE IF EXISTS entities")
            db.execSQL("DROP TABLE IF EXISTS domains")
            db.execSQL("DROP TABLE IF EXISTS realms")
            onCreate(db)
            schemaResetMessage = "Database schema reset (v$oldVersion → v$newVersion)"
        }
    }

    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    private var ready = false

    override fun isReady(): Boolean = ready

    override fun schemaResetMessage(): String? = schemaResetMessage

    override fun initialize() {
        try {
            db.execSQL(SQL_CREATE_REALMS)
            db.execSQL(SQL_CREATE_DOMAINS)
            db.execSQL(SQL_CREATE_ENTITIES)
            db.execSQL(SQL_CREATE_KG_FACTS)

            for (realm in DimensionConfig.defaultRealms) {
                ensureRealm(Realm(realm.id, realm.name, realm.description, System.currentTimeMillis()))
            }
            for ((realmId, domains) in DimensionConfig.defaultDomains) {
                for ((domainId, desc) in domains) {
                    ensureDomain(realmId, domainId, domainId, desc)
                }
            }

            ready = true
        } catch (e: Exception) {
            android.util.Log.e("DimensionStore", "Init failed", e)
            ready = false
        }
    }

    // Realm operations

    override fun ensureRealm(realm: Realm): Realm {
        val existing = getRealm(realm.id)
        if (existing != null) return existing
        db.insert(
            "realms",
            null,
            ContentValues().apply {
                put("id", realm.id)
                put("name", realm.name)
                put("description", realm.description)
                put("created_at", realm.createdAt)
            },
        )
        return realm
    }

    override fun getRealm(realmId: String): Realm? {
        val cursor = db.query("realms", null, "id = ?", arrayOf(realmId), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) {
                Realm(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")),
                    description = it.getString(it.getColumnIndexOrThrow("description")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                )
            } else {
                null
            }
        }
    }

    override fun getAllRealms(): List<Realm> {
        val cursor = db.query("realms", null, null, null, null, null, null)
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        Realm(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            name = it.getString(it.getColumnIndexOrThrow("name")),
                            description = it.getString(it.getColumnIndexOrThrow("description")),
                            createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                        ),
                    )
                }
            }
        }
    }

    // Domain operations

    override fun ensureDomain(realm: String, domainId: String, name: String, description: String): Domain {
        val existing = db.query("domains", null, "id = ? AND realm = ?", arrayOf(domainId, realm), null, null, null)
        existing.use {
            if (it.moveToFirst()) {
                return Domain(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    realm = it.getString(it.getColumnIndexOrThrow("realm")),
                    name = it.getString(it.getColumnIndexOrThrow("name")),
                    description = it.getString(it.getColumnIndexOrThrow("description")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                )
            }
        }
        val domain = Domain(domainId, realm, name, description, System.currentTimeMillis())
        db.insert(
            "domains",
            null,
            ContentValues().apply {
                put("id", domain.id)
                put("realm", domain.realm)
                put("name", domain.name)
                put("description", domain.description)
                put("created_at", domain.createdAt)
            },
        )
        return domain
    }

    override fun getDomains(realm: String): List<Domain> {
        val cursor = db.query("domains", null, "realm = ?", arrayOf(realm), null, null, null)
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        Domain(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            realm = it.getString(it.getColumnIndexOrThrow("realm")),
                            name = it.getString(it.getColumnIndexOrThrow("name")),
                            description = it.getString(it.getColumnIndexOrThrow("description")),
                            createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                        ),
                    )
                }
            }
        }
    }

    // Entity operations

    override fun putEntity(entity: EntityData): EntityData {
        val contentHash = sha256(entity.content)
        val existing = getEntity(entity.id)
        val embeddingJson = entity.embedding?.let { json.encodeToString(it) }
        if (existing != null) {
            db.update(
                "entities",
                ContentValues().apply {
                    put("realm", entity.realm)
                    put("domain", entity.domain)
                    put("content", entity.content)
                    put("source_file", entity.sourceFile)
                    put("metadata", json.encodeToString(entity.metadata))
                    put("content_hash", contentHash)
                    put("updated_at", entity.updatedAt)
                    put("protected", if (entity.protected) 1 else 0)
                    if (embeddingJson != null) put("embedding", embeddingJson)
                },
                "id = ?",
                arrayOf(entity.id),
            )
        } else {
            db.insert(
                "entities",
                null,
                ContentValues().apply {
                    put("id", entity.id)
                    put("realm", entity.realm)
                    put("domain", entity.domain)
                    put("content", entity.content)
                    put("source_file", entity.sourceFile)
                    put("metadata", json.encodeToString(entity.metadata))
                    put("content_hash", contentHash)
                    put("created_at", entity.createdAt)
                    put("updated_at", entity.updatedAt)
                    put("protected", if (entity.protected) 1 else 0)
                    if (embeddingJson != null) put("embedding", embeddingJson)
                },
            )
        }
        return entity
    }

    override fun getEntity(id: String): EntityData? {
        val cursor = db.query("entities", null, "id = ?", arrayOf(id), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) cursorToEntity(it) else null
        }
    }

    override fun getEntitiesByDomain(realm: String, domain: String): List<EntityData> {
        val cursor = db.query("entities", null, "realm = ? AND domain = ?", arrayOf(realm, domain), null, null, null)
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(cursorToEntity(it))
                }
            }
        }
    }

    override fun getAllEntities(): List<EntityData> {
        val cursor = db.query("entities", null, null, null, null, null, null)
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(cursorToEntity(it))
                }
            }
        }
    }

    override fun deleteEntity(id: String): Boolean = db.delete("entities", "id = ?", arrayOf(id)) > 0

    override fun countEntities(): Long = android.database.DatabaseUtils.queryNumEntries(db, "entities")

    override fun getEntityByMetadataKey(key: String, value: String): EntityData? {
        val cursor = db.query("entities", null, "metadata LIKE ?", arrayOf("%\"$key\":\"$value\"%"), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) cursorToEntity(it) else null
        }
    }

    // Content search

    override fun searchEntities(query: String, limit: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val likeQuery = "%${query.replace("'", "''")}%"
        val cursor = db.query("entities", null, "content LIKE ?", arrayOf(likeQuery), null, null, null, limit.toString())
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val entity = cursorToEntity(it)
                    add(SearchResult(entity, 0.5, entity.content.take(200)))
                }
            }
        }
    }

    override fun searchSimilar(embedding: List<Float>, limit: Int, minScore: Double): List<SearchResult> {
        val all = getAllEntities().filter { it.embedding != null }
        val scored = all.mapNotNull { entity ->
            val e = entity.embedding ?: return@mapNotNull null
            val sim = cosineSimilarity(embedding, e)
            if (sim < minScore) return@mapNotNull null
            SearchResult(entity, sim, entity.content.take(200))
        }
        return scored.sortedByDescending { it.score }.take(limit)
    }

    // Knowledge graph operations

    override fun putFact(fact: KGFact): KGFact {
        val existing = db.query("kg_facts", null, "id = ?", arrayOf(fact.id), null, null, null)
        existing.use {
            if (it.moveToFirst()) {
                db.update(
                    "kg_facts",
                    ContentValues().apply {
                        put("subject", fact.subject)
                        put("predicate", fact.predicate)
                        put("object", fact.`object`)
                        put("valid_from", fact.validFrom)
                        put("valid_to", fact.validTo)
                        put("source_entity_id", fact.sourceEntityId)
                    },
                    "id = ?",
                    arrayOf(fact.id),
                )
            } else {
                db.insert(
                    "kg_facts",
                    null,
                    ContentValues().apply {
                        put("id", fact.id)
                        put("subject", fact.subject)
                        put("predicate", fact.predicate)
                        put("object", fact.`object`)
                        put("valid_from", fact.validFrom)
                        put("valid_to", fact.validTo)
                        put("source_entity_id", fact.sourceEntityId)
                        put("created_at", fact.createdAt)
                    },
                )
            }
        }
        return fact
    }

    override fun getFactsBySubject(subject: String): List<KGFact> {
        val cursor = db.query("kg_facts", null, "subject = ?", arrayOf(subject), null, null, null)
        return cursorToFacts(cursor)
    }

    override fun getFactsByObject(`object`: String): List<KGFact> {
        val cursor = db.query("kg_facts", null, "object = ?", arrayOf(`object`), null, null, null)
        return cursorToFacts(cursor)
    }

    override fun queryKGE(relation: String?, limit: Int): List<KGFact> {
        val selection = if (relation != null) "predicate = ?" else null
        val args = if (relation != null) arrayOf(relation) else null
        val cursor = db.query("kg_facts", null, selection, args, null, null, "created_at DESC", limit.toString())
        return cursorToFacts(cursor)
    }

    override fun searchFacts(query: String, limit: Int): List<KGFact> {
        if (query.isBlank()) return emptyList()
        val likeQuery = "%${query.replace("'", "''")}%"
        val cursor = db.query(
            "kg_facts",
            null,
            "subject LIKE ? OR predicate LIKE ? OR object LIKE ?",
            arrayOf(likeQuery, likeQuery, likeQuery),
            null,
            null,
            "created_at DESC",
            limit.toString(),
        )
        return cursorToFacts(cursor)
    }

    override fun deleteFact(id: String): Boolean = db.delete("kg_facts", "id = ?", arrayOf(id)) > 0

    // Backup

    override fun getExportData(): ByteArray {
        val entities = getAllEntities()
        val facts = queryKGE(limit = Int.MAX_VALUE)
        val export = DimensionExport(
            exportedAt = System.currentTimeMillis(),
            entities = entities.map { e ->
                EntityExport(e.id, e.realm, e.domain, e.content, e.sourceFile, e.metadata, e.createdAt, e.updatedAt, e.embedding, e.protected)
            },
            kgFacts = facts.map { f ->
                FactExport(f.id, f.subject, f.predicate, f.`object`, f.validFrom, f.validTo, f.sourceEntityId, f.createdAt)
            },
        )
        val jsonStr = json.encodeToString(export)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).bufferedWriter().use { it.write(jsonStr) }
        return bos.toByteArray()
    }

    override fun importFromData(data: ByteArray) {
        try {
            val jsonStr = GZIPInputStream(ByteArrayInputStream(data)).bufferedReader().readText()
            val export = json.decodeFromString<DimensionExport>(jsonStr)
            val db = this.db
            db.beginTransaction()
            try {
                for (entityExport in export.entities) {
                    val entity = EntityData(
                        id = entityExport.id,
                        realm = entityExport.realm,
                        domain = entityExport.domain,
                        content = entityExport.content,
                        sourceFile = entityExport.sourceFile,
                        metadata = entityExport.metadata,
                        createdAt = entityExport.createdAt,
                        updatedAt = entityExport.updatedAt,
                        embedding = entityExport.embedding,
                        protected = entityExport.protected,
                    )
                    putEntity(entity)
                }
                for (factExport in export.kgFacts) {
                    val fact = KGFact(
                        id = factExport.id,
                        subject = factExport.subject,
                        predicate = factExport.predicate,
                        `object` = factExport.`object`,
                        validFrom = factExport.validFrom,
                        validTo = factExport.validTo,
                        sourceEntityId = factExport.sourceEntityId,
                        createdAt = factExport.createdAt,
                    )
                    putFact(fact)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            android.util.Log.e("DimensionStore", "Import failed", e)
            throw e
        }
    }

    // Helpers

    private fun cursorToEntity(cursor: android.database.Cursor): EntityData {
        val metadataStr = cursor.getString(cursor.getColumnIndexOrThrow("metadata"))
        val metadata = try {
            json.decodeFromString<Map<String, String>>(metadataStr)
        } catch (_: Exception) {
            emptyMap()
        }
        val embedding = try {
            val colIdx = cursor.getColumnIndex("embedding")
            if (colIdx >= 0 && !cursor.isNull(colIdx)) {
                json.decodeFromString<List<Float>>(cursor.getString(colIdx))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
        val protected = try {
            val colIdx = cursor.getColumnIndex("protected")
            colIdx >= 0 && cursor.getInt(colIdx) == 1
        } catch (_: Exception) {
            false
        }
        return EntityData(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            realm = cursor.getString(cursor.getColumnIndexOrThrow("realm")),
            domain = cursor.getString(cursor.getColumnIndexOrThrow("domain")),
            content = cursor.getString(cursor.getColumnIndexOrThrow("content")) ?: "",
            sourceFile = cursor.getString(cursor.getColumnIndexOrThrow("source_file")),
            metadata = metadata,
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            embedding = embedding,
            protected = protected,
        )
    }

    private fun cursorToFacts(cursor: android.database.Cursor): List<KGFact> = cursor.use {
        buildList {
            while (it.moveToNext()) {
                add(
                    KGFact(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        subject = it.getString(it.getColumnIndexOrThrow("subject")),
                        predicate = it.getString(it.getColumnIndexOrThrow("predicate")),
                        `object` = it.getString(it.getColumnIndexOrThrow("object")),
                        validFrom = it.getLongOrNull(it.getColumnIndexOrThrow("valid_from")),
                        validTo = it.getLongOrNull(it.getColumnIndexOrThrow("valid_to")),
                        sourceEntityId = it.getString(it.getColumnIndexOrThrow("source_entity_id")),
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                    ),
                )
            }
        }
    }

    private fun android.database.Cursor.getLongOrNull(colIndex: Int): Long? {
        if (isNull(colIndex)) return null
        return getLong(colIndex)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom == 0.0) 0.0 else dot / denom
    }

    override fun toString(): String = "SqliteDimensionStore(db=$DB_NAME, ready=$ready)"
}
