package com.medeide.jh.screens.home.landscape.editor.snippets

/**
 * 代码片段模板
 */
data class CodeSnippet(
    val id: String,
    val name: String,
    val description: String,
    val language: String,
    val prefix: String,
    val body: String,
    val category: SnippetCategory = SnippetCategory.General,
)

enum class SnippetCategory {
    General,
    Kotlin,
    Java,
    Python,
    JavaScript,
    Web,
    Android,
    DataStructures,
}

/**
 * 预定义的代码片段模板
 */
object CodeSnippets {
    val all = listOf(
        // Kotlin
        CodeSnippet(
            id = "kotlin_main",
            name = "main 函数",
            description = "Kotlin 程序入口点",
            language = "kotlin",
            prefix = "main",
            body = "fun main(args: Array<String>) {\n    ${'$'}0\n}",
            category = SnippetCategory.Kotlin,
        ),
        CodeSnippet(
            id = "kotlin_data_class",
            name = "data class",
            description = "数据类定义",
            language = "kotlin",
            prefix = "data",
            body = "data class ${'$'}{NAME:ClassName}(\n    val id: String,\n    val name: String,\n)",
            category = SnippetCategory.Kotlin,
        ),
        CodeSnippet(
            id = "kotlin_composable",
            name = "Composable 函数",
            description = "Jetpack Compose UI 组件",
            language = "kotlin",
            prefix = "comp",
            body = "@Composable\nfun ${'$'}{NAME:ComponentName}(\n    modifier: Modifier = Modifier,\n) {\n    ${'$'}0\n}",
            category = SnippetCategory.Android,
        ),
        CodeSnippet(
            id = "kotlin_suspend",
            name = "suspend 函数",
            description = "协程挂起函数",
            language = "kotlin",
            prefix = "suspend",
            body = "suspend fun ${'$'}{NAME:functionName}(): Result<${'$'}{TYPE:DataType}> {\n    return try {\n        // TODO: 实现逻辑\n        Result.success(${'$'}0)\n    } catch (e: Exception) {\n        Result.failure(e)\n    }\n}",
            category = SnippetCategory.Kotlin,
        ),
        CodeSnippet(
            id = "kotlin_when",
            name = "when 表达式",
            description = "when 分支表达式",
            language = "kotlin",
            prefix = "when",
            body = "when (val result = ${'$'}{EXPRESSION:condition}) {\n    is Success -> {\n        ${'$'}0\n    }\n    is Failure -> {\n        // 错误处理\n    }\n    else -> {\n        // 默认情况\n    }\n}",
            category = SnippetCategory.Kotlin,
        ),

        // Java
        CodeSnippet(
            id = "java_main",
            name = "main 方法",
            description = "Java 程序入口点",
            language = "java",
            prefix = "main",
            body = "public static void main(String[] args) {\n    ${'$'}0\n}",
            category = SnippetCategory.Java,
        ),
        CodeSnippet(
            id = "java_class",
            name = "类定义",
            description = "Java 类模板",
            language = "java",
            prefix = "class",
            body = "public class ${'$'}{NAME:ClassName} {\n    private ${'$'}{TYPE:type} ${'$'}{FIELD:field};\n\n    public ${'$'}{NAME:ClassName}(${'$'}{TYPE:type} ${'$'}{FIELD:field}) {\n        this.${'$'}{FIELD:field} = ${'$'}{FIELD:field};\n    }\n\n    ${'$'}0\n}",
            category = SnippetCategory.Java,
        ),

        // Python
        CodeSnippet(
            id = "python_main",
            name = "main 函数",
            description = "Python 程序入口",
            language = "python",
            prefix = "main",
            body = "def main():\n    ${'$'}0\n\nif __name__ == \"__main__\":\n    main()",
            category = SnippetCategory.Python,
        ),
        CodeSnippet(
            id = "python_class",
            name = "类定义",
            description = "Python 类模板",
            language = "python",
            prefix = "class",
            body = "class ${'$'}{NAME:ClassName}:\n    def __init__(self, ${'$'}{PARAMS:params}):\n        ${'$'}0\n\n    def __str__(self):\n        return f\"${'$'}{NAME:ClassName}(...)",
            category = SnippetCategory.Python,
        ),

        // JavaScript
        CodeSnippet(
            id = "js_function",
            name = "函数",
            description = "JavaScript 函数",
            language = "javascript",
            prefix = "func",
            body = "function ${'$'}{NAME:functionName}(${'$'}{PARAMS:params}) {\n    ${'$'}0\n}",
            category = SnippetCategory.JavaScript,
        ),
        CodeSnippet(
            id = "js_arrow",
            name = "箭头函数",
            description = "ES6 箭头函数",
            language = "javascript",
            prefix = "arrow",
            body = "const ${'$'}{NAME:functionName} = (${'$'}{PARAMS:params}) => {\n    ${'$'}0\n}",
            category = SnippetCategory.JavaScript,
        ),
        CodeSnippet(
            id = "js_async",
            name = "异步函数",
            description = "async/await 异步函数",
            language = "javascript",
            prefix = "async",
            body = "async function ${'$'}{NAME:functionName}(${'$'}{PARAMS:params}) {\n    try {\n        const result = await ${'$'}{PROMISE:promise};\n        ${'$'}0\n        return result;\n    } catch (error) {\n        console.error(error);\n        throw error;\n    }\n}",
            category = SnippetCategory.JavaScript,
        ),

        // Web
        CodeSnippet(
            id = "html5",
            name = "HTML5 模板",
            description = "HTML5 文档模板",
            language = "html",
            prefix = "html5",
            body = "<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>${'$'}{TITLE:Page Title}</title>\n    <link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n    ${'$'}0\n    <script src=\"script.js\"></script>\n</body>\n</html>",
            category = SnippetCategory.Web,
        ),
        CodeSnippet(
            id = "css_flex",
            name = "Flexbox 布局",
            description = "Flexbox 布局模板",
            language = "css",
            prefix = "flex",
            body = ".${'$'}{CLASS:container} {\n    display: flex;\n    justify-content: ${'$'}{JUSTIFY:center};\n    align-items: ${'$'}{ALIGN:center};\n    gap: ${'$'}{GAP:16px};\n}",
            category = SnippetCategory.Web,
        ),

        // 数据结构
        CodeSnippet(
            id = "ds_linkedlist",
            name = "链表节点",
            description = "单链表节点定义",
            language = "kotlin",
            prefix = "linkedlist",
            body = "class ListNode(var value: Int) {\n    var next: ListNode? = null\n}",
            category = SnippetCategory.DataStructures,
        ),
        CodeSnippet(
            id = "ds_tree_node",
            name = "树节点",
            description = "二叉树节点定义",
            language = "kotlin",
            prefix = "treenode",
            body = "class TreeNode(var value: Int) {\n    var left: TreeNode? = null\n    var right: TreeNode? = null\n}",
            category = SnippetCategory.DataStructures,
        ),
    )

    fun getByLanguage(language: String): List<CodeSnippet> =
        all.filter { it.language.equals(language, ignoreCase = true) }

    fun getByCategory(category: SnippetCategory): List<CodeSnippet> =
        all.filter { it.category == category }

    fun search(query: String): List<CodeSnippet> =
        all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.prefix.contains(query, ignoreCase = true)
        }
}
