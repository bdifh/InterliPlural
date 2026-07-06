package com.interli.plural

import java.util.UUID

data class RelationNode(
    val id: String = UUID.randomUUID().toString(),
    val type: NodeType,
    var name: String = "",
    var color: Int? = null,
    var imageUri: String? = null,
    var x: Float = 500f,
    var y: Float = 500f,
    val memberId: String? = null
)

enum class NodeType {
    MEMBER, LOCATION, RELATIONSHIP_ORB
}

data class RelationEdge(
    val id: String = UUID.randomUUID().toString(),
    var nodeIds: MutableList<String> = mutableListOf(),
    var groupIds: MutableList<String> = mutableListOf(),
    var tag: String? = null,
    var note: String? = null,
    val fromNodeId: String? = null,
    val toNodeId: String? = null
) {
    fun getSafeNodeIds(): List<String> {
        if (nodeIds.isNotEmpty()) return nodeIds
        val legacy = mutableListOf<String>()
        fromNodeId?.let { legacy.add(it) }
        toNodeId?.let { legacy.add(it) }
        return legacy
    }
}

data class RelationGroup(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var color: Int = -6934396,
    var nodeIds: MutableList<String> = mutableListOf(),
    var snapEnabled: Boolean = true
)

data class RelationsData(
    val nodes: MutableList<RelationNode> = mutableListOf(),
    val edges: MutableList<RelationEdge> = mutableListOf(),
    val groups: MutableList<RelationGroup> = mutableListOf(),
    var smartLayoutEnabled: Boolean = false
)

data class RelationEnvironment(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "Main",
    var data: RelationsData = RelationsData()
)
