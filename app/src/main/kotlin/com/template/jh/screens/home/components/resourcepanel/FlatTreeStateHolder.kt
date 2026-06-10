package com.template.jh.screens.home.components.resourcepanel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.template.jh.model.FileItem

/**
 * 简单目录树状态管理 — 扁平列表 + 异步子节点加载
 * 去除了复杂的自动扁平化逻辑，改用直观的递归展开
 */
class FlatTreeStateHolder {
    var visibleNodes: List<ResourceNode> by mutableStateOf(emptyList())
        private set

    var loadingKeys: Set<String> by mutableStateOf(emptySet())
        private set

    private val expandedKeys = mutableSetOf<String>()
    private val childrenCache = mutableMapOf<String, List<ResourceNode>>()
    private var rootItems = listOf<FileItem>()

    /** 设置根文件列表，清除所有展开/缓存状态 */
    fun setRoot(files: List<FileItem>) {
        val same = files.size == rootItems.size &&
                files.zip(rootItems).all { (a, b) -> a.relativePath == b.relativePath }
        if (same) return
        rootItems = files
        expandedKeys.clear()
        childrenCache.clear()
        loadingKeys = emptySet()
        rebuild()
    }

    /** 展开/折叠目录 */
    fun toggle(
        node: ResourceNode,
        onLoad: (String, (List<FileItem>) -> Unit) -> Unit,
    ) {
        if (!node.isDirectory) return
        if (isExpanded(node)) {
            expandedKeys.remove(node.relativePath)
            rebuild()
        } else {
            if (childrenCache.containsKey(node.relativePath)) {
                expandedKeys.add(node.relativePath)
                rebuild()
            } else {
                loadAndExpand(node, onLoad)
            }
        }
    }

    fun isExpanded(node: ResourceNode): Boolean = node.relativePath in expandedKeys
    fun isLoading(key: String): Boolean = key in loadingKeys

    /** 刷新指定目录的子节点（重命名/删除/创建后调用） */
    fun refreshParent(
        parentPath: String,
        onLoad: (String, (List<FileItem>) -> Unit) -> Unit,
    ) {
        childrenCache.remove(parentPath)
        if (parentPath in expandedKeys) {
            val parentDepth = findNodeDepth(parentPath)
            loadChildren(parentPath, parentDepth, onLoad)
        }
    }

    // === 内部方法 ===

    private fun loadAndExpand(
        node: ResourceNode,
        onLoad: (String, (List<FileItem>) -> Unit) -> Unit,
    ) {
        val key = node.relativePath
        if (key in loadingKeys) return
        loadingKeys = loadingKeys + key
        onLoad(key) { children ->
            childrenCache[key] = children.map {
                ResourceNode(it.uri, it.name, it.relativePath, it.isDirectory, node.depth + 1, it.filePath)
            }
            loadingKeys = loadingKeys - key
            expandedKeys.add(key)
            rebuild()
        }
    }

    private fun loadChildren(
        parentPath: String,
        parentDepth: Int,
        onLoad: (String, (List<FileItem>) -> Unit) -> Unit,
    ) {
        if (parentPath in loadingKeys) return
        loadingKeys = loadingKeys + parentPath
        onLoad(parentPath) { children ->
            childrenCache[parentPath] = children.map {
                ResourceNode(it.uri, it.name, it.relativePath, it.isDirectory, parentDepth + 1, it.filePath)
            }
            loadingKeys = loadingKeys - parentPath
            if (parentPath !in expandedKeys) expandedKeys.add(parentPath)
            rebuild()
        }
    }

    private fun findNodeDepth(path: String): Int {
        var depth = 0
        var current = path
        while (current.contains('/')) {
            current = current.substringBeforeLast('/')
            depth++
        }
        return depth
    }

    private fun rebuild() {
        val result = mutableListOf<ResourceNode>()
        for (item in rootItems) {
            val node = ResourceNode(item.uri, item.name, item.relativePath, item.isDirectory, 0, item.filePath)
            result.add(node)
            if (item.isDirectory && item.relativePath in expandedKeys) {
                appendChildrenRecursive(result, item.relativePath, 1)
            }
        }
        visibleNodes = result
    }

    private fun appendChildrenRecursive(
        result: MutableList<ResourceNode>,
        parentPath: String,
        depth: Int,
    ) {
        val children = childrenCache[parentPath] ?: return
        for (child in children) {
            val node = child.copy(depth = depth)
            result.add(node)
            if (child.isDirectory && child.relativePath in expandedKeys) {
                appendChildrenRecursive(result, child.relativePath, depth + 1)
            }
        }
    }
}

@Composable
fun rememberFlatTreeState(): FlatTreeStateHolder = remember { FlatTreeStateHolder() }
