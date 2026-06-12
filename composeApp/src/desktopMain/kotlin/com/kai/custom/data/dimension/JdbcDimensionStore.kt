package com.kai.custom.data.dimension

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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
        PRIMARY KEY (id, realm)
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
        embedding TEXT,
        protected INTEGER NOT NULL DEFAULT 0
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

class JdbcDimensionStore(private val dbPath: String) : DimensionStore {

    private var conn: Connection? = null
    private var ready = false
    private var schemaResetMessage: String? = null

    override fun isReady(): Boolean = ready

    override fun schemaResetMessage(): String? = schemaResetMessage

    override fun initialize() {
        try {
            Class.forName("org.sqlite.JDBC")
            conn = DriverManager.getConnection("jdbc:sqlite:$dbPath").also { c ->
                c.createStatement().apply {
                    executeUpdate("PRAGMA journal_mode=WAL")
                    executeUpdate("PRAGMA foreign_keys=ON")
                }
            }
            val db = conn!!
            db.createStatement().apply {
                executeUpdate(SQL_CREATE_REALMS)
                executeUpdate(SQL_CREATE_DOMAINS)
                executeUpdate(SQL_CREATE_ENTITIES)
                executeUpdate(SQL_CREATE_KG_FACTS)
            }

            val entitiesCols = db.metaData.getColumns(null, null, "entities", "protected")
            if (!entitiesCols.next()) {
                db.createStatement().executeUpdate("ALTER TABLE entities ADD COLUMN protected INTEGER NOT NULL DEFAULT 0")
            }
            entitiesCols.close()

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
            e.printStackTrace()
            ready = false
        }
    }

    private fun db(): Connection = conn ?: throw IllegalStateException("DimensionStore not initialized")

    override fun ensureRealm(realm: Realm): Realm {
        val existing = getRealm(realm.id)
        if (existing != null) return existing
        db().prepareStatement("INSERT INTO realms (id, name, description, created_at) VALUES (?, ?, ?, ?)").use { stmt ->
            stmt.setString(1, realm.id)
            stmt.setString(2, realm.name)
            stmt.setString(3, realm.description)
            stmt.setLong(4, realm.createdAt)
            stmt.executeUpdate()
        }
        return realm
    }

    override fun getRealm(realmId: String): Realm? {
        db().prepareStatement("SELECT id, name, description, created_at FROM realms WHERE id = ?").use { stmt ->
            stmt.setString(1, realmId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return Realm(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        description = rs.getString("description") ?: "",
                        createdAt = rs.getLong("created_at"),
                    )
                }
            }
        }
        return null
    }

    override fun getAllRealms(): List<Realm> {
        val result = mutableListOf<Realm>()
        db().createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, name, description, created_at FROM realms").use { rs ->
                while (rs.next()) {
                    result.add(
                        Realm(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            description = rs.getString("description") ?: "",
                            createdAt = rs.getLong("created_at"),
                        ),
                    )
                }
            }
        }
        return result
    }

    override fun ensureDomain(realm: String, domainId: String, name: String, description: String): Domain {
        db().prepareStatement("SELECT id, realm, name, description, created_at FROM domains WHERE id = ? AND realm = ?").use { stmt ->
            stmt.setString(1, domainId)
            stmt.setString(2, realm)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return Domain(
                        id = rs.getString("id"),
                        realm = rs.getString("realm"),
                        name = rs.getString("name"),
                        description = rs.getString("description") ?: "",
                        createdAt = rs.getLong("created_at"),
                    )
                }
            }
        }
        val domain = Domain(domainId, realm, name, description, System.currentTimeMillis())
        db().prepareStatement("INSERT INTO domains (id, realm, name, description, created_at) VALUES (?, ?, ?, ?, ?)").use { stmt ->
            stmt.setString(1, domain.id)
            stmt.setString(2, domain.realm)
            stmt.setString(3, domain.name)
            stmt.setString(4, domain.description)
            stmt.setLong(5, domain.createdAt)
            stmt.executeUpdate()
        }
        return domain
    }

    override fun getDomains(realm: String): List<Domain> {
        val result = mutableListOf<Domain>()
        db().prepareStatement("SELECT id, realm, name, description, created_at FROM domains WHERE realm = ?").use { stmt ->
            stmt.setString(1, realm)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result.add(
                        Domain(
                            id = rs.getString("id"),
                            realm = rs.getString("realm"),
                            name = rs.getString("name"),
                            description = rs.getString("description") ?: "",
                            createdAt = rs.getLong("created_at"),
                        ),
                    )
                }
            }
        }
        return result
    }

    override fun putEntity(entity: EntityData): EntityData {
        val contentHash = sha256(entity.content)
        val existing = getEntity(entity.id)
        val embeddingJson = entity.embedding?.let { json.encodeToString(it) }
        if (existing != null) {
            db().prepareStatement(
                """UPDATE entities SET realm=?, domain=?, content=?, source_file=?, metadata=?, 
                   content_hash=?, updated_at=?, embedding=?, protected=? WHERE id=?""",
            ).use { stmt ->
                stmt.setString(1, entity.realm)
                stmt.setString(2, entity.domain)
                stmt.setString(3, entity.content)
                stmt.setString(4, entity.sourceFile)
                stmt.setString(5, json.encodeToString(entity.metadata))
                stmt.setString(6, contentHash)
                stmt.setLong(7, entity.updatedAt)
                stmt.setString(8, embeddingJson)
                stmt.setInt(9, if (entity.protected) 1 else 0)
                stmt.setString(10, entity.id)
                stmt.executeUpdate()
            }
        } else {
            db().prepareStatement(
                """INSERT INTO entities (id, realm, domain, content, source_file, metadata, content_hash, 
                   created_at, updated_at, embedding, protected) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            ).use { stmt ->
                stmt.setString(1, entity.id)
                stmt.setString(2, entity.realm)
                stmt.setString(3, entity.domain)
                stmt.setString(4, entity.content)
                stmt.setString(5, entity.sourceFile)
                stmt.setString(6, json.encodeToString(entity.metadata))
                stmt.setString(7, contentHash)
                stmt.setLong(8, entity.createdAt)
                stmt.setLong(9, entity.updatedAt)
                stmt.setString(10, embeddingJson)
                stmt.setInt(11, if (entity.protected) 1 else 0)
                stmt.executeUpdate()
            }
        }
        return entity
    }

    override fun getEntity(id: String): EntityData? {
        db().prepareStatement("SELECT * FROM entities WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (rs.next()) return rsToEntity(rs)
            }
        }
        return null
    }

    override fun getEntitiesByDomain(realm: String, domain: String): List<EntityData> {
        val result = mutableListOf<EntityData>()
        db().prepareStatement("SELECT * FROM entities WHERE realm = ? AND domain = ?").use { stmt ->
            stmt.setString(1, realm)
            stmt.setString(2, domain)
            stmt.executeQuery().use { rs ->
                while (rs.next()) result.add(rsToEntity(rs))
            }
        }
        return result
    }

    override fun getAllEntities(): List<EntityData> {
        val result = mutableListOf<EntityData>()
        db().createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM entities").use { rs ->
                while (rs.next()) result.add(rsToEntity(rs))
            }
        }
        return result
    }

    override fun deleteEntity(id: String): Boolean {
        db().prepareStatement("DELETE FROM entities WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() > 0
        }
    }

    override fun countEntities(): Long {
        db().createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) AS cnt FROM entities").use { rs ->
                if (rs.next()) return rs.getLong("cnt")
            }
        }
        return 0
    }

    override fun getEntityByMetadataKey(key: String, value: String): EntityData? {
        val likePattern = "%\"$key\":\"$value\"%"
        db().prepareStatement("SELECT * FROM entities WHERE metadata LIKE ?").use { stmt ->
            stmt.setString(1, likePattern)
            stmt.executeQuery().use { rs ->
                if (rs.next()) return rsToEntity(rs)
            }
        }
        return null
    }

    override fun searchEntities(query: String, limit: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val likeQuery = "%${query.replace("'", "''")}%"
        val result = mutableListOf<SearchResult>()
        db().prepareStatement("SELECT * FROM entities WHERE content LIKE ? OR metadata LIKE ? LIMIT ?").use { stmt ->
            stmt.setString(1, likeQuery)
            stmt.setString(2, likeQuery)
            stmt.setInt(3, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val entity = rsToEntity(rs)
                    result.add(SearchResult(entity, 0.5, entity.content.take(200)))
                }
            }
        }
        return result
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

    override fun putFact(fact: KGFact): KGFact {
        val existing = getFactById(fact.id)
        if (existing != null) {
            db().prepareStatement("UPDATE kg_facts SET subject=?, predicate=?, object=?, valid_from=?, valid_to=?, source_entity_id=? WHERE id=?").use { stmt ->
                stmt.setString(1, fact.subject)
                stmt.setString(2, fact.predicate)
                stmt.setString(3, fact.`object`)
                if (fact.validFrom != null) stmt.setLong(4, fact.validFrom) else stmt.setNull(4, java.sql.Types.INTEGER)
                if (fact.validTo != null) stmt.setLong(5, fact.validTo) else stmt.setNull(5, java.sql.Types.INTEGER)
                stmt.setString(6, fact.sourceEntityId)
                stmt.setString(7, fact.id)
                stmt.executeUpdate()
            }
        } else {
            db().prepareStatement("INSERT INTO kg_facts (id, subject, predicate, object, valid_from, valid_to, source_entity_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { stmt ->
                stmt.setString(1, fact.id)
                stmt.setString(2, fact.subject)
                stmt.setString(3, fact.predicate)
                stmt.setString(4, fact.`object`)
                if (fact.validFrom != null) stmt.setLong(5, fact.validFrom) else stmt.setNull(5, java.sql.Types.INTEGER)
                if (fact.validTo != null) stmt.setLong(6, fact.validTo) else stmt.setNull(6, java.sql.Types.INTEGER)
                stmt.setString(7, fact.sourceEntityId)
                stmt.setLong(8, fact.createdAt)
                stmt.executeUpdate()
            }
        }
        return fact
    }

    private fun getFactById(id: String): KGFact? {
        db().prepareStatement("SELECT * FROM kg_facts WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (rs.next()) return rsToFact(rs)
            }
        }
        return null
    }

    override fun getFactsBySubject(subject: String): List<KGFact> {
        val result = mutableListOf<KGFact>()
        db().prepareStatement("SELECT * FROM kg_facts WHERE subject = ?").use { stmt ->
            stmt.setString(1, subject)
            stmt.executeQuery().use { rs ->
                while (rs.next()) result.add(rsToFact(rs))
            }
        }
        return result
    }

    override fun getFactsByObject(`object`: String): List<KGFact> {
        val result = mutableListOf<KGFact>()
        db().prepareStatement("SELECT * FROM kg_facts WHERE object = ?").use { stmt ->
            stmt.setString(1, `object`)
            stmt.executeQuery().use { rs ->
                while (rs.next()) result.add(rsToFact(rs))
            }
        }
        return result
    }

    override fun queryKGE(relation: String?, limit: Int): List<KGFact> {
        val result = mutableListOf<KGFact>()
        if (relation != null) {
            db().prepareStatement("SELECT * FROM kg_facts WHERE predicate = ? ORDER BY created_at DESC LIMIT ?").use { stmt ->
                stmt.setString(1, relation)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) result.add(rsToFact(rs))
                }
            }
        } else {
            db().prepareStatement("SELECT * FROM kg_facts ORDER BY created_at DESC LIMIT ?").use { stmt ->
                stmt.setInt(1, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) result.add(rsToFact(rs))
                }
            }
        }
        return result
    }

    override fun searchFacts(query: String, limit: Int): List<KGFact> {
        if (query.isBlank()) return emptyList()
        val likeQuery = "%${query.replace("'", "''")}%"
        val result = mutableListOf<KGFact>()
        db().prepareStatement("SELECT * FROM kg_facts WHERE subject LIKE ? OR predicate LIKE ? OR object LIKE ? ORDER BY created_at DESC LIMIT ?").use { stmt ->
            stmt.setString(1, likeQuery)
            stmt.setString(2, likeQuery)
            stmt.setString(3, likeQuery)
            stmt.setInt(4, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) result.add(rsToFact(rs))
            }
        }
        return result
    }

    override fun deleteFact(id: String): Boolean {
        db().prepareStatement("DELETE FROM kg_facts WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() > 0
        }
    }

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
            val c = db()
            c.autoCommit = false
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
                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun rsToEntity(rs: java.sql.ResultSet): EntityData {
        val metadataStr = rs.getString("metadata") ?: "{}"
        val metadata = try {
            json.decodeFromString<Map<String, String>>(metadataStr)
        } catch (_: Exception) {
            emptyMap()
        }
        val embedding = try {
            val colIdx = rs.findColumn("embedding")
            val str = rs.getString(colIdx)
            if (str != null) json.decodeFromString<List<Float>>(str) else null
        } catch (_: Exception) {
            null
        }
        val protected = try {
            rs.getInt("protected") == 1
        } catch (_: Exception) {
            false
        }
        return EntityData(
            id = rs.getString("id"),
            realm = rs.getString("realm"),
            domain = rs.getString("domain"),
            content = rs.getString("content") ?: "",
            sourceFile = rs.getString("source_file"),
            metadata = metadata,
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at"),
            embedding = embedding,
            protected = protected,
        )
    }

    private fun rsToFact(rs: java.sql.ResultSet): KGFact {
        val vf = rs.getLong("valid_from")
        val validFrom = if (rs.wasNull()) null else vf
        val vt = rs.getLong("valid_to")
        val validTo = if (rs.wasNull()) null else vt

        return KGFact(
            id = rs.getString("id"),
            subject = rs.getString("subject"),
            predicate = rs.getString("predicate"),
            `object` = rs.getString("object"),
            validFrom = validFrom,
            validTo = validTo,
            sourceEntityId = rs.getString("source_entity_id"),
            createdAt = rs.getLong("created_at"),
        )
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

    override fun toString(): String = "JdbcDimensionStore(db=$dbPath, ready=$ready)"
}
