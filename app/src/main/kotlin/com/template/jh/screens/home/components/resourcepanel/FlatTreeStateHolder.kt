package com.template.jh.screens.home.components.resourcepanel

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.template.jh.model.FileItem

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

    // 设置根节点（根变更时清理已不可用的展开/缓存状态）
    fun setRoot(fileItems: List<FileItem>) {
        val newPaths = fileItems.map { it.relativePath }.toSet()
        expandedKeys.removeAll { key ->
            val topLevel = key.substringBefore('/')
            topLevel !in newPaths
        }
        childrenCache.keys.removeAll { key ->
            val topLevel = key.substringBefore('/')
            topLevel !in newPaths
        }
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

    // 清除状态（根变更时调用）
    fun clear() {
        expandedKeys.clear()
        childrenCache.clear()
        loadingKeys = emptySet()
        rootNodes = emptyList()
        visibleNodes = emptyList()
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

    // 判断目录是否应自动扁平化（已展开且只有一个子目录）
    private fun shouldFlatten(path: String): Boolean {
        if (path !in expandedKeys) return false
        val cached = childrenCache[path] ?: return false
        return cached.size == 1 && cached[0].isDirectory
    }

    // 递归扁平化：合并路径名称，直到遇到非单子目录节点
    private fun flattenChain(
        result: MutableList<ResourceNode>,
        prefixName: String,
        path: String,
        uri: Uri,
        filePath: String,
        depth: Int,
    ) {
        val child = childrenCache[path]!![0]
        val mergedName = "$prefixName/${child.name}"
        if (shouldFlatten(child.relativePath)) {
            flattenChain(result, mergedName, child.relativePath, child.uri, child.filePath, depth)
        } else {
            result.add(ResourceNode(child.uri, mergedName, child.relativePath, true, depth, child.filePath))
            if (child.relativePath in expandedKeys) {
                appendChildren(result, child.relativePath, depth + 1)
            }
        }
    }

    // 重建可见节点列表
    private fun rebuildVisibleNodes() {
        val result = mutableListOf<ResourceNode>()
        for (root in rootNodes) {
            if (shouldFlatten(root.relativePath)) {
                flattenChain(result, root.name, root.relativePath, root.uri, root.filePath, 0)
            } else {
                result.add(root)
                if (root.isDirectory && root.relativePath in expandedKeys) {
                    appendChildren(result, root.relativePath, 1)
                }
            }
        }
        visibleNodes = result
    }

    private fun appendChildren(result: MutableList<ResourceNode>, parentPath: String, depth: Int) {
        val cached = childrenCache[parentPath] ?: return
        for (child in cached) {
            if (shouldFlatten(child.relativePath)) {
                flattenChain(result, child.name, child.relativePath, child.uri, child.filePath, depth)
            } else {
                val node = ResourceNode(child.uri, child.name, child.relativePath, child.isDirectory, depth, child.filePath)
                result.add(node)
                if (child.isDirectory && child.relativePath in expandedKeys) {
                    appendChildren(result, child.relativePath, depth + 1)
                }
            }
        }
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
