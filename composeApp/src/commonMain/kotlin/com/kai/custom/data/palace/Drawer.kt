package com.kai.custom.data.palace

import kotlinx.serialization.Serializable

@Serializable
data class Drawer(
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
data class Wing(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
)

@Serializable
data class Room(
    val id: String,
    val wingId: String,
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
    val sourceDrawerId: String? = null,
    val createdAt: Long,
)

data class SearchResult(
    val drawer: Drawer,
    val score: Double,
    val snippet: String = "",
)
