package com.kai.custom.data.dimension

import kotlinx.serialization.Serializable

@Serializable
data class DimensionExport(
    val version: Int = 1,
    val exportedAt: Long,
    val entities: List<EntityExport>,
    val kgFacts: List<FactExport>,
)

@Serializable
data class EntityExport(
    val id: String,
    val realm: String,
    val domain: String,
    val content: String,
    val sourceFile: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val updatedAt: Long,
    val embedding: List<Float>? = null,
    val protected: Boolean = false,
)

@Serializable
data class FactExport(
    val id: String,
    val subject: String,
    val predicate: String,
    val `object`: String,
    val validFrom: Long? = null,
    val validTo: Long? = null,
    val sourceEntityId: String? = null,
    val createdAt: Long,
)
