package com.kai.custom.data.palace

import kotlinx.serialization.Serializable

@Serializable
data class PalaceExport(
    val version: Int = 1,
    val exportedAt: Long,
    val drawers: List<DrawerExport>,
    val kgFacts: List<FactExport>,
)

@Serializable
data class DrawerExport(
    val id: String,
    val wingId: String,
    val roomId: String,
    val content: String,
    val sourceFile: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class FactExport(
    val id: String,
    val subject: String,
    val predicate: String,
    val `object`: String,
    val validFrom: Long? = null,
    val validTo: Long? = null,
    val sourceDrawerId: String? = null,
    val createdAt: Long,
)
