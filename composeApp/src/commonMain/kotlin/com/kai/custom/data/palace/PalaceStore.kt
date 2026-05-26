package com.kai.custom.data.palace

interface PalaceStore {

    fun isReady(): Boolean

    fun initialize()

    // Wing operations
    fun ensureWing(wing: Wing): Wing
    fun getWing(wingId: String): Wing?
    fun getAllWings(): List<Wing>

    // Room operations
    fun ensureRoom(wingId: String, roomId: String, name: String, description: String = ""): Room
    fun getRooms(wingId: String): List<Room>

    // Drawer operations
    fun putDrawer(drawer: Drawer): Drawer
    fun getDrawer(id: String): Drawer?
    fun getDrawersByRoom(wingId: String, roomId: String): List<Drawer>
    fun getAllDrawers(): List<Drawer>
    fun deleteDrawer(id: String): Boolean
    fun countDrawers(): Long

    fun getDrawerByMetadataKey(key: String, value: String): Drawer?

    // Content search (FTS / keyword)
    fun searchDrawers(query: String, limit: Int = 10): List<SearchResult>

    // Knowledge graph
    fun putFact(fact: KGFact): KGFact
    fun getFactsBySubject(subject: String): List<KGFact>
    fun getFactsByObject(`object`: String): List<KGFact>
    fun queryKGE(relation: String? = null, limit: Int = 20): List<KGFact>
    fun deleteFact(id: String): Boolean

    // Backup
    fun getExportData(): ByteArray
    fun importFromData(data: ByteArray)
}
