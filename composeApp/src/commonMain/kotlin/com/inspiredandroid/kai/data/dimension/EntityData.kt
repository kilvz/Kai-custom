package com.inspiredandroid.kai.data.dimension

import kotlinx.serialization.Serializable

@Serializable
data class EntityData(
    val id: String,
    val realm: String,
    val domain: String,
    val content: String,
    val sourceFile: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class Realm(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
)

@Serializable
data class Domain(
    val id: String,
    val realm: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
)

@Serializable
data class KGFact(
    val id: String,
    val subject: String,
    val predicate: String,
    val `object`: String,
    val validFrom: Long? = null,
    val validTo: Long? = null,
    val sourceEntityId: String? = null,
    val createdAt: Long,
)

data class SearchResult(
    val entity: EntityData,
    val score: Double,
    val snippet: String = "",
)
