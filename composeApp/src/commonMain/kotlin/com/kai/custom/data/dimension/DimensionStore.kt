package com.kai.custom.data.dimension

interface DimensionStore {

    fun isReady(): Boolean

    fun initialize()

    fun ensureRealm(realm: Realm): Realm
    fun getRealm(realmId: String): Realm?
    fun getAllRealms(): List<Realm>

    fun ensureDomain(realm: String, domainId: String, name: String, description: String = ""): Domain
    fun getDomains(realm: String): List<Domain>

    fun putEntity(entity: EntityData): EntityData
    fun getEntity(id: String): EntityData?
    fun getEntitiesByDomain(realm: String, domain: String): List<EntityData>
    fun getAllEntities(): List<EntityData>
    fun deleteEntity(id: String): Boolean
    fun countEntities(): Long

    fun getEntityByMetadataKey(key: String, value: String): EntityData?

    fun searchEntities(query: String, limit: Int = 10): List<SearchResult>

    fun searchSimilar(embedding: List<Float>, limit: Int = 10, minScore: Double = 0.5): List<SearchResult>

    fun putFact(fact: KGFact): KGFact
    fun getFactsBySubject(subject: String): List<KGFact>
    fun getFactsByObject(`object`: String): List<KGFact>
    fun queryKGE(relation: String? = null, limit: Int = 20): List<KGFact>
    fun searchFacts(query: String, limit: Int = 10): List<KGFact>
    fun deleteFact(id: String): Boolean

    fun getExportData(): ByteArray
    fun importFromData(data: ByteArray)
}
