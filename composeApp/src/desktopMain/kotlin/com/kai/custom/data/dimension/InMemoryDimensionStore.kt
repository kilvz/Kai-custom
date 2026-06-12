package com.kai.custom.data.dimension

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

class InMemoryDimensionStore : DimensionStore {

    private val realms = ConcurrentHashMap<String, Realm>()
    private val domains = ConcurrentHashMap<String, Domain>()
    private val entities = ConcurrentHashMap<String, EntityData>()
    private val facts = ConcurrentHashMap<String, KGFact>()
    private var ready = false

    override fun isReady(): Boolean = ready

    override fun initialize() {
        val now = System.currentTimeMillis()
        realms.putIfAbsent("default", Realm("default", "Default", "Default realm", now))
        ready = true
    }

    override fun ensureRealm(realm: Realm): Realm {
        val existing = realms[realm.id]
        if (existing != null) return existing
        realms[realm.id] = realm
        return realm
    }

    override fun getRealm(realmId: String): Realm? = realms[realmId]

    override fun getAllRealms(): List<Realm> = realms.values.toList()

    override fun ensureDomain(realm: String, domainId: String, name: String, description: String): Domain {
        val key = "$realm:$domainId"
        val existing = domains[key]
        if (existing != null) return existing
        val domain = Domain(domainId, realm, name, description, System.currentTimeMillis())
        domains[key] = domain
        return domain
    }

    override fun getDomains(realm: String): List<Domain> = domains.values.filter { it.realm == realm }

    override fun putEntity(entity: EntityData): EntityData {
        entities[entity.id] = entity
        return entity
    }

    override fun getEntity(id: String): EntityData? = entities[id]

    override fun getEntitiesByDomain(realm: String, domain: String): List<EntityData> = entities.values.filter { it.realm == realm && it.domain == domain }

    override fun getAllEntities(): List<EntityData> = entities.values.toList()

    override fun deleteEntity(id: String): Boolean = entities.remove(id) != null

    override fun countEntities(): Long = entities.size.toLong()

    override fun getEntityByMetadataKey(key: String, value: String): EntityData? = entities.values.find { it.metadata[key] == value }

    override fun searchEntities(query: String, limit: Int): List<SearchResult> {
        val lower = query.lowercase()
        return entities.values
            .filter { it.content.lowercase().contains(lower) || it.realm.lowercase().contains(lower) || it.domain.lowercase().contains(lower) }
            .sortedByDescending { it.updatedAt }
            .take(limit)
            .map { SearchResult(it, 0.5) }
    }

    override fun searchSimilar(embedding: List<Float>, limit: Int, minScore: Double): List<SearchResult> = entities.values.take(limit).map { SearchResult(it, 0.0) }

    override fun putFact(fact: KGFact): KGFact {
        facts[fact.id] = fact
        return fact
    }

    override fun getFactsBySubject(subject: String): List<KGFact> = facts.values.filter { it.subject == subject }

    override fun getFactsByObject(`object`: String): List<KGFact> = facts.values.filter { it.`object` == `object` }

    override fun queryKGE(relation: String?, limit: Int): List<KGFact> {
        var result = facts.values.toList()
        if (relation != null) result = result.filter { it.predicate == relation }
        return result.take(limit)
    }

    override fun searchFacts(query: String, limit: Int): List<KGFact> {
        val lower = query.lowercase()
        return facts.values
            .filter { it.subject.lowercase().contains(lower) || it.predicate.lowercase().contains(lower) || it.`object`.lowercase().contains(lower) }
            .take(limit)
    }

    override fun deleteFact(id: String): Boolean = facts.remove(id) != null

    override fun getExportData(): ByteArray {
        val data = buildMap {
            put("realms", realms.values.toList())
            put("domains", domains.values.toList())
            put("entities", entities.values.toList())
            put("facts", facts.values.toList())
        }
        val jsonStr = json.encodeToString(data)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(jsonStr.toByteArray()) }
        return baos.toByteArray()
    }

    override fun importFromData(data: ByteArray) {
        try {
            val decompressed = GZIPInputStream(ByteArrayInputStream(data)).readAllBytes().toString(Charsets.UTF_8)

            @Suppress("UNCHECKED_CAST")
            val map = json.decodeFromString<Map<String, List<Map<String, Any?>>>>(decompressed)
            realms.clear()
            domains.clear()
            entities.clear()
            facts.clear()
            map["realms"]?.forEach { r ->
                val realm = Realm(r["id"] as String, r["name"] as String, (r["description"] ?: "") as String, (r["createdAt"] as Number).toLong())
                realms[realm.id] = realm
            }
            map["entities"]?.forEach { e ->
                val entity = EntityData(
                    id = e["id"] as String,
                    realm = e["realm"] as String,
                    domain = e["domain"] as String,
                    content = (e["content"] ?: "") as String,
                    sourceFile = e["sourceFile"] as? String,
                    createdAt = (e["createdAt"] as Number).toLong(),
                    updatedAt = (e["updatedAt"] as? Number)?.toLong() ?: (e["createdAt"] as Number).toLong(),
                )
                entities[entity.id] = entity
            }
            map["facts"]?.forEach { f ->
                val fact = KGFact(
                    id = f["id"] as String,
                    subject = f["subject"] as String,
                    predicate = f["predicate"] as String,
                    `object` = f["object"] as String,
                    validFrom = (f["validFrom"] as? Number)?.toLong(),
                    validTo = (f["validTo"] as? Number)?.toLong(),
                    sourceEntityId = f["sourceEntityId"] as? String,
                    createdAt = (f["createdAt"] as Number).toLong(),
                )
                facts[fact.id] = fact
            }
        } catch (_: Exception) {
            initialize()
        }
    }
}
