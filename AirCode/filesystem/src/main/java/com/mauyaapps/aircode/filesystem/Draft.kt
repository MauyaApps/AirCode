package com.mauyaapps.aircode.filesystem

import com.mauyaapps.aircode.filesystem.WorkspaceException.Reason

sealed interface DraftNode {
    val id: Long
    val name: String
}

data class DraftFolder(
    override val id: Long,
    override val name: String,
    val children: List<DraftNode> = emptyList(),
) : DraftNode

data class DraftFile(
    override val id: Long,
    override val name: String,
    val text: String = "",
) : DraftNode

data class DraftEntry(val node: DraftNode, val depth: Int, val isExpanded: Boolean)

object DraftTree {

    fun find(roots: List<DraftNode>, id: Long): DraftNode? {
        for (node in roots) {
            if (node.id == id) return node
            if (node is DraftFolder) find(node.children, id)?.let { return it }
        }
        return null
    }

    fun add(roots: List<DraftNode>, parentId: Long?, node: DraftNode): Result<List<DraftNode>> = runCatching {
        val name = NameRules.validate(node.name)
        val siblings = if (parentId == null) {
            roots
        } else {
            (find(roots, parentId) as? DraftFolder ?: throw WorkspaceException(Reason.NOT_FOUND)).children
        }
        if (siblings.any { it.name == name }) throw WorkspaceException(Reason.ALREADY_EXISTS)
        val added = when (node) {
            is DraftFolder -> node.copy(name = name)
            is DraftFile -> node.copy(name = name)
        }
        if (parentId == null) {
            roots + added
        } else {
            mapNode(roots, parentId) { (it as DraftFolder).copy(children = it.children + added) }
        }
    }

    fun remove(roots: List<DraftNode>, id: Long): List<DraftNode> =
        roots.filter { it.id != id }.map { if (it is DraftFolder) it.copy(children = remove(it.children, id)) else it }

    fun rename(roots: List<DraftNode>, id: Long, newName: String): Result<List<DraftNode>> = runCatching {
        val name = NameRules.validate(newName)
        val siblings = siblingsOf(roots, id) ?: throw WorkspaceException(Reason.NOT_FOUND)
        if (siblings.any { it.id != id && it.name == name }) throw WorkspaceException(Reason.ALREADY_EXISTS)
        mapNode(roots, id) {
            when (it) {
                is DraftFolder -> it.copy(name = name)
                is DraftFile -> it.copy(name = name)
            }
        }
    }

    fun setText(roots: List<DraftNode>, id: Long, text: String): List<DraftNode> =
        mapNode(roots, id) { if (it is DraftFile) it.copy(text = text) else it }

    fun flatten(roots: List<DraftNode>, expanded: Set<Long>): List<DraftEntry> {
        val result = mutableListOf<DraftEntry>()

        fun walk(nodes: List<DraftNode>, depth: Int) {
            val sorted = nodes.sortedWith(
                compareByDescending<DraftNode> { it is DraftFolder }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
            for (node in sorted) {
                val isExpanded = node is DraftFolder && node.id in expanded
                result += DraftEntry(node, depth, isExpanded)
                if (node is DraftFolder && isExpanded) walk(node.children, depth + 1)
            }
        }

        walk(roots, 0)
        return result
    }

    private fun siblingsOf(roots: List<DraftNode>, id: Long): List<DraftNode>? {
        if (roots.any { it.id == id }) return roots
        for (node in roots) {
            if (node is DraftFolder) siblingsOf(node.children, id)?.let { return it }
        }
        return null
    }

    private fun mapNode(nodes: List<DraftNode>, id: Long, transform: (DraftNode) -> DraftNode): List<DraftNode> =
        nodes.map { node ->
            when {
                node.id == id -> transform(node)
                node is DraftFolder -> node.copy(children = mapNode(node.children, id, transform))
                else -> node
            }
        }
}
