package com.template.jh.screens.home.components.resourcepanel

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.template.jh.screens.home.FileItem

// 扁平目录树状态管理器 - 使用相对路径作为键
class FlatTreeStateHolder {
    var rootNodes: List<ResourceNode> by mutableStateOf(emptyList())
        private set

    var visibleNodes: List<ResourceNode> by mutableStateOf(emptyList())
        private set

    var loadingKeys: Set<String> by mutableStateOf(emptySet())
        private set

    private val expandedKeys = mutableSetOf<String>()
    private val childrenCache = mutableStateMapOf<String, List<FileItemNode>>()

    // 设置根节点
    fun setRoot(fileItems: List<FileItem>) {
        rootNodes = fileItems.map {
            ResourceNode(
                uri = it.uri,
                name = it.name,
                relativePath = it.relativePath,
                isDirectory = it.isDirectory,
                depth = 0,
                filePath = it.filePath,
            )
        }
        rebuildVisibleNodes()
    }

    // 加载子节点
    fun loadAndExpand(
        relativePath: String,
        onLoad: (String, (List<FileItem>) -> Unit) -> Unit,
    ) {
        if (relativePath in loadingKeys) return
        loadingKeys = loadingKeys + relativePath

        onLoad(relativePath) { children ->
            childrenCache[relativePath] = children.map {
                FileItemNode(it.name, it.uri, it.relativePath, it.isDirectory, it.filePath)
            }
            loadingKeys = loadingKeys - relativePath
            expandedKeys.add(relativePath)
            rebuildVisibleNodes()
        }
    }

    // 切换展开/折叠
    fun toggle(
        node: ResourceNode,
        onLoad: (String, (List<FileItem>) -> Unit) -> Unit,
    ) {
        if (!node.isDirectory) return
        if (isExpanded(node)) {
            collapse(node)
        } else {
            if (childrenCache.containsKey(node.relativePath)) {
                expandedKeys.add(node.relativePath)
                rebuildVisibleNodes()
            } else {
                loadAndExpand(node.relativePath, onLoad)
            }
        }
    }

    fun collapse(node: ResourceNode) {
        expandedKeys.remove(node.relativePath)
        val childKeys = expandedKeys.filter { it.startsWith("${node.relativePath}/") }
        expandedKeys.removeAll(childKeys.toSet())
        rebuildVisibleNodes()
    }

    fun isExpanded(node: ResourceNode): Boolean = node.relativePath in expandedKeys
    fun isLoading(key: String): Boolean = key in loadingKeys

    // 重建可见节点列表
    private fun rebuildVisibleNodes() {
        val result = mutableListOf<ResourceNode>()
        fun appendChildren(parentPath: String, depth: Int) {
            val cached = childrenCache[parentPath] ?: return
            for (child in cached) {
                val childNode = ResourceNode(
                    uri = child.uri,
                    name = child.name,
                    relativePath = child.relativePath,
                    isDirectory = child.isDirectory,
                    depth = depth,
                    filePath = child.filePath,
                )
                result.add(childNode)
                if (child.isDirectory && child.relativePath in expandedKeys) {
                    appendChildren(child.relativePath, depth + 1)
                }
            }
        }
        for (root in rootNodes) {
            result.add(root)
            if (root.isDirectory && root.relativePath in expandedKeys) {
                appendChildren(root.relativePath, 1)
            }
        }
        visibleNodes = result
    }

    fun refreshParent(
        parentPath: String,
        onLoad: (String, (List<FileItem>) -> Unit) -> Unit,
    ) {
        childrenCache.remove(parentPath)
        if (parentPath in expandedKeys) {
            loadAndExpand(parentPath, onLoad)
        }
    }
}

@Composable
fun rememberFlatTreeState(): FlatTreeStateHolder = remember { FlatTreeStateHolder() }
