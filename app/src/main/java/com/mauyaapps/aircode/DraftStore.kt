package com.mauyaapps.aircode

import com.mauyaapps.aircode.filesystem.DraftFile
import com.mauyaapps.aircode.filesystem.DraftFolder
import com.mauyaapps.aircode.filesystem.DraftNode
import com.mauyaapps.aircode.filesystem.DraftTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DraftStore {

    private val _nodes = MutableStateFlow<List<DraftNode>>(emptyList())
    val nodes: StateFlow<List<DraftNode>> = _nodes.asStateFlow()

    private var nextId = 1L

    fun find(id: Long): DraftNode? = DraftTree.find(_nodes.value, id)

    fun createFolder(parentId: Long?, name: String): Result<Long> =
        add(parentId, DraftFolder(id = nextId++, name = name))

    fun createFile(parentId: Long?, name: String): Result<Long> =
        add(parentId, DraftFile(id = nextId++, name = name))

    fun rename(id: Long, newName: String): Result<Unit> =
        DraftTree.rename(_nodes.value, id, newName).map { _nodes.value = it }

    fun delete(id: Long) {
        _nodes.value = DraftTree.remove(_nodes.value, id)
    }

    fun setText(id: Long, text: String) {
        _nodes.value = DraftTree.setText(_nodes.value, id, text)
    }

    private fun add(parentId: Long?, node: DraftNode): Result<Long> =
        DraftTree.add(_nodes.value, parentId, node).map { roots ->
            _nodes.value = roots
            node.id
        }
}
