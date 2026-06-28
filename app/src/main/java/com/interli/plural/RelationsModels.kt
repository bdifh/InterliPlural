package com.interli.plural

import java.util.UUID

data class RelationNode(
    val id: String = UUID.randomUUID().toString(),
    val type: NodeType, // MEMBER or LOCATION
    var name: String = "",
    var color: Int? = null,
    var imageUri: String? = null,
    var x: Float = 500f,
    var y: Float = 500f,
    val memberId: String? = null // Reference to Person.id if type is MEMBER
)

enum class NodeType {
    MEMBER, LOCATION
}

data class RelationEdge(
    val id: String = UUID.randomUUID().toString(),
    val fromNodeId: String,
    val toNodeId: String,
    var tag: String? = null
)

data class RelationGroup(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var color: Int = -6934396,
    var nodeIds: MutableList<String> = mutableListOf()
)

data class RelationsData(
    val nodes: MutableList<RelationNode> = mutableListOf(),
    val edges: MutableList<RelationEdge> = mutableListOf(),
    val groups: MutableList<RelationGroup> = mutableListOf()
)
