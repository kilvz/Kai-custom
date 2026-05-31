package com.kai.custom.tools

import com.kai.custom.data.MemoryStore
import com.kai.custom.network.tools.Tool

/**
 * Curated tool registry that always registers the unified memory tool set
 * (search/store/forget/learn/reinforce + KG + diary) regardless of backend.
 * The MemoryStoreProvider handles local vs alt-memory delegation transparently.
 */
class CuratedToolRegistry(private val memoryStore: MemoryStore) {

    fun getCoreMemoryTools(): List<Tool> = buildList {
        addAll(CommonTools.getMemoryTools(memoryStore))
        addAll(CommonTools.getKgTools(memoryStore))
        addAll(CommonTools.getDiaryTools(memoryStore))
    }
}
