package com.medeide.jh.screens.home.aitools.tools

import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger
import java.io.BufferedReader
import java.io.InputStreamReader

class TerminalTools(
    private val fileManager: FileManager?,
) {
    fun executeCommand(
        command: String,
        workingDir: String = "",
        timeoutSeconds: Int = 60,
    ): String {
        Log.d("TerminalTools", "executeCommand: command=$command workingDir=$workingDir")
        FileLogger.d("TerminalTools", "executeCommand: command=$command workingDir=$workingDir")

        val fm = fileManager ?: return err("No project folder is open.")
        val cwd = if (workingDir.isBlank()) {
            fm.projectDirPath.ifEmpty { fm.storageRootPath }
        } else {
            if (workingDir.startsWith("/")) workingDir
            else "${fm.projectDirPath}/$workingDir"
        }

        if (cwd.isBlank()) return err("Cannot determine working directory.")

        return try {
            val process = ProcessBuilder()
                .command("/system/bin/sh", "-c", command)
                .directory(java.io.File(cwd))
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            val completed = process.waitFor(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return err("Command timed out after $timeoutSeconds seconds")
            }

            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            val exitCode = process.exitValue()
            val result = output.toString().trimEnd()

            if (exitCode != 0) {
                FileLogger.w("TerminalTools", "Command failed with exit code $exitCode: $command")
                ok("Exit code: $exitCode\n$result")
            } else {
                ok(result)
            }
        } catch (e: Exception) {
            Log.e("TerminalTools", "executeCommand failed: ${e.message}", e)
            FileLogger.e("TerminalTools", "executeCommand failed: ${e.message}", e)
            err("Command execution failed: ${e.message}")
        }
    }

    fun compileProject(
        workingDir: String = "",
        buildCommand: String = "./gradlew assembleDebug",
    ): String {
        Log.d("TerminalTools", "compileProject: buildCommand=$buildCommand workingDir=$workingDir")
        FileLogger.d("TerminalTools", "compileProject: buildCommand=$buildCommand")
        return executeCommand(buildCommand, workingDir, 300)
    }

    fun runTests(
        workingDir: String = "",
        testCommand: String = "./gradlew test",
    ): String {
        Log.d("TerminalTools", "runTests: testCommand=$testCommand workingDir=$workingDir")
        FileLogger.d("TerminalTools", "runTests: testCommand=$testCommand")
        return executeCommand(testCommand, workingDir, 300)
    }

    fun gitCommand(
        command: String,
        workingDir: String = "",
    ): String {
        Log.d("TerminalTools", "gitCommand: command=$command workingDir=$workingDir")
        FileLogger.d("TerminalTools", "gitCommand: command=$command")
        return executeCommand("git $command", workingDir, 60)
    }

    fun listDirectoryTree(
        path: String = "",
        depth: Int = 2,
        ignorePatterns: String = "",
    ): String {
        Log.d("TerminalTools", "listDirectoryTree: path=$path depth=$depth")
        FileLogger.d("TerminalTools", "listDirectoryTree: path=$path depth=$depth")

        val fm = fileManager ?: return err("No project folder is open.")
        val relPath = if (path.isBlank()) "" else resolvePathOrAbsolute(path, fm)

        val ignoreList = if (ignorePatterns.isNotBlank()) ignorePatterns.split(",").map { it.trim() } else emptyList()

        val tree = buildString {
            appendLine(ok("Directory tree${if (relPath.isNotBlank()) " for $relPath" else ""} (depth: $depth):"))
            appendLine(generateTree(relPath, depth, 0, ignoreList, fm))
        }
        return tree.trimEnd()
    }

    private fun generateTree(
        path: String,
        maxDepth: Int,
        currentDepth: Int,
        ignorePatterns: List<String>,
        fm: FileManager,
    ): String {
        if (currentDepth > maxDepth) return ""

        val nodes = fm.listFilesAsNodes(path)
        if (nodes.isEmpty()) return ""

        val filtered = nodes.filter { node ->
            !ignorePatterns.any { node.name.matches(it.toRegex()) }
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        return buildString {
            for ((index, node) in filtered.withIndex()) {
                val isLast = index == filtered.size - 1
                val prefix = buildString {
                    for (i in 0 until currentDepth) {
                        append("│   ")
                    }
                    append(if (isLast) "└── " else "├── ")
                }

                append(prefix)
                append(if (node.isDirectory) "${node.name}/" else node.name)
                if (!node.isDirectory && node.size > 0) {
                    append(" (${formatSize(node.size)})")
                }
                appendLine()

                if (node.isDirectory && currentDepth < maxDepth) {
                    append(generateTree(node.path, maxDepth, currentDepth + 1, ignorePatterns, fm))
                }
            }
        }
    }

    fun checkFileExists(path: String): String {
        Log.d("TerminalTools", "checkFileExists: path=$path")
        FileLogger.d("TerminalTools", "checkFileExists: path=$path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(path, fm)

        return if (fm.exists(fullPath)) {
            ok("File exists: $fullPath")
        } else {
            err("File not found: $fullPath")
        }
    }

    fun getFileInfo(path: String): String {
        Log.d("TerminalTools", "getFileInfo: path=$path")
        FileLogger.d("TerminalTools", "getFileInfo: path=$path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(path, fm)

        if (!fm.exists(fullPath)) {
            return err("File not found: $fullPath")
        }

        val file = java.io.File(fullPath)

        return buildString {
            appendLine(ok("File info for $fullPath:"))
            appendLine("  Name: ${file.name}")
            appendLine("  Type: ${if (file.isDirectory) "Directory" else "File"}")
            appendLine("  Size: ${formatSize(file.length())}")
            appendLine("  Path: ${file.absolutePath}")
            appendLine("  Last Modified: ${java.util.Date(file.lastModified()).toString()}")
        }.trimEnd()
    }

    fun findFileByName(
        name: String,
        startPath: String = "",
        caseSensitive: Boolean = false,
    ): String {
        Log.d("TerminalTools", "findFileByName: name=$name startPath=$startPath")
        FileLogger.d("TerminalTools", "findFileByName: name=$name")

        val fm = fileManager ?: return err("No project folder is open.")
        val rootPath = if (startPath.isBlank()) "" else resolvePathOrAbsolute(startPath, fm)

        val matches = mutableListOf<String>()
        searchFile(name, rootPath, matches, caseSensitive, fm)

        return if (matches.isEmpty()) {
            ok("No files named '$name' found.")
        } else {
            ok("${matches.size} files found:\n${matches.joinToString("\n")}")
        }
    }

    fun getEnvironmentVariable(name: String): String {
        Log.d("TerminalTools", "getEnvironmentVariable: name=$name")
        FileLogger.d("TerminalTools", "getEnvironmentVariable: name=$name")

        val value = System.getenv(name)
        return if (value != null) {
            ok("${name}=${value}")
        } else {
            err("Environment variable '$name' not set.")
        }
    }

    fun listEnvironmentVariables(): String {
        Log.d("TerminalTools", "listEnvironmentVariables")
        FileLogger.d("TerminalTools", "listEnvironmentVariables")

        val env = System.getenv()
        val sorted = env.entries.sortedBy { it.key }

        return buildString {
            appendLine(ok("Environment variables:"))
            for ((key, value) in sorted) {
                appendLine("  ${key}=${value}")
            }
        }.trimEnd()
    }

    fun getCurrentWorkingDirectory(): String {
        Log.d("TerminalTools", "getCurrentWorkingDirectory")
        FileLogger.d("TerminalTools", "getCurrentWorkingDirectory")

        val fm = fileManager ?: return err("No project folder is open.")
        return ok("Current working directory: ${fm.projectDirPath.ifEmpty { fm.storageRootPath }}")
    }

    fun changeDirectory(path: String): String {
        Log.d("TerminalTools", "changeDirectory: path=$path")
        FileLogger.d("TerminalTools", "changeDirectory: path=$path")

        return ok("Directory change not supported in this context. Use executeCommand with 'cd $path && command'.")
    }

    fun executeCommandWithOutput(
        command: String,
        workingDir: String = "",
        timeoutSeconds: Int = 60,
        captureOutput: Boolean = true,
    ): String {
        Log.d("TerminalTools", "executeCommandWithOutput: command=$command workingDir=$workingDir")
        FileLogger.d("TerminalTools", "executeCommandWithOutput: command=$command")

        return executeCommand(command, workingDir, timeoutSeconds)
    }

    fun executeMultipleCommands(
        commands: List<String>,
        workingDir: String = "",
        timeoutSeconds: Int = 60,
    ): String {
        Log.d("TerminalTools", "executeMultipleCommands: ${commands.size} commands")
        FileLogger.d("TerminalTools", "executeMultipleCommands: ${commands.size} commands")

        val combined = commands.joinToString(" && ")
        return executeCommand(combined, workingDir, timeoutSeconds)
    }

    fun setFilePermissions(
        path: String,
        permissions: String,
    ): String {
        Log.d("TerminalTools", "setFilePermissions: path=$path permissions=$permissions")
        FileLogger.d("TerminalTools", "setFilePermissions: path=$path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(path, fm)

        return try {
            val file = java.io.File(fullPath)
            val mode = permissions.toIntOrNull(8)
            if (mode == null) {
                return err("Invalid permission format. Use octal format like '755'.")
            }
            file.setExecutable((mode and 64) != 0)
            file.setReadable((mode and 256) != 0)
            file.setWritable((mode and 128) != 0)
            ok("Permissions set to $permissions for $fullPath")
        } catch (e: Exception) {
            Log.e("TerminalTools", "setFilePermissions failed: ${e.message}", e)
            FileLogger.e("TerminalTools", "setFilePermissions failed: ${e.message}", e)
            err("Failed to set permissions: ${e.message}")
        }
    }

    fun getFilePermissions(path: String): String {
        Log.d("TerminalTools", "getFilePermissions: path=$path")
        FileLogger.d("TerminalTools", "getFilePermissions: path=$path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(path, fm)

        return try {
            val file = java.io.File(fullPath)
            val sb = StringBuilder()
            sb.append(if (file.isDirectory) 'd' else '-')
            sb.append(if (file.canRead()) 'r' else '-')
            sb.append(if (file.canWrite()) 'w' else '-')
            sb.append(if (file.canExecute()) 'x' else '-')
            ok("Permissions for $fullPath: $sb")
        } catch (e: Exception) {
            Log.e("TerminalTools", "getFilePermissions failed: ${e.message}", e)
            FileLogger.e("TerminalTools", "getFilePermissions failed: ${e.message}", e)
            err("Failed to get permissions: ${e.message}")
        }
    }

    fun countLinesInFile(path: String): String {
        Log.d("TerminalTools", "countLinesInFile: path=$path")
        FileLogger.d("TerminalTools", "countLinesInFile: path=$path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(path, fm)

        return try {
            val content = fm.readFileRaw(fullPath) ?: return err("File not found: $fullPath")
            val lines = content.lines()
            ok("File $path has ${lines.size} lines")
        } catch (e: Exception) {
            Log.e("TerminalTools", "countLinesInFile failed: ${e.message}", e)
            FileLogger.e("TerminalTools", "countLinesInFile failed: ${e.message}", e)
            err("Failed to count lines: ${e.message}")
        }
    }

    fun countFilesInDirectory(path: String = "", recursive: Boolean = false): String {
        Log.d("TerminalTools", "countFilesInDirectory: path=$path recursive=$recursive")
        FileLogger.d("TerminalTools", "countFilesInDirectory: path=$path")

        val fm = fileManager ?: return err("No project folder is open.")
        val relPath = if (path.isBlank()) "" else resolvePathOrAbsolute(path, fm)

        return try {
            val count = if (recursive) {
                countFilesRecursive(relPath, fm)
            } else {
                fm.listFilesAsNodes(relPath).filterNot { it.isDirectory }.size
            }
            ok("Directory $path contains $count files${if (recursive) " (recursive)" else ""}")
        } catch (e: Exception) {
            Log.e("TerminalTools", "countFilesInDirectory failed: ${e.message}", e)
            FileLogger.e("TerminalTools", "countFilesInDirectory failed: ${e.message}", e)
            err("Failed to count files: ${e.message}")
        }
    }

    private fun countFilesRecursive(path: String, fm: FileManager): Int {
        var count = 0
        val nodes = fm.listFilesAsNodes(path)
        for (node in nodes) {
            if (node.isDirectory) {
                count += countFilesRecursive(node.path, fm)
            } else {
                count++
            }
        }
        return count
    }

    fun sortFilesBySize(path: String = "", descending: Boolean = true): String {
        Log.d("TerminalTools", "sortFilesBySize: path=$path descending=$descending")
        FileLogger.d("TerminalTools", "sortFilesBySize: path=$path")

        val fm = fileManager ?: return err("No project folder is open.")
        val relPath = if (path.isBlank()) "" else resolvePathOrAbsolute(path, fm)

        val nodes = fm.listFilesAsNodes(relPath).filterNot { it.isDirectory }
        if (nodes.isEmpty()) return ok("No files in directory $path")

        val sorted = nodes.sortedBy { if (descending) -it.size else it.size }

        return buildString {
            appendLine(ok("Files sorted by size${if (descending) " (descending)" else ""}:"))
            for (node in sorted) {
                appendLine("  ${formatSize(node.size)} - ${node.name}")
            }
        }.trimEnd()
    }

    private fun searchFile(
        name: String,
        path: String,
        matches: MutableList<String>,
        caseSensitive: Boolean,
        fm: FileManager,
    ) {
        val nodes = fm.listFilesAsNodes(path)
        for (node in nodes) {
            val match = if (caseSensitive) {
                node.name == name
            } else {
                node.name.equals(name, ignoreCase = true)
            }

            if (match) {
                matches.add(node.path)
            }

            if (node.isDirectory) {
                searchFile(name, node.path, matches, caseSensitive, fm)
            }
        }
    }

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildExecuteCommandTool())
            arr.put(buildCompileProjectTool())
            arr.put(buildRunTestsTool())
            arr.put(buildGitCommandTool())
            arr.put(buildListDirectoryTreeTool())
            arr.put(buildCheckFileExistsTool())
            arr.put(buildGetFileInfoTool())
            arr.put(buildFindFileByNameTool())
            arr.put(buildGetEnvironmentVariableTool())
            arr.put(buildListEnvironmentVariablesTool())
            arr.put(buildGetCurrentWorkingDirectoryTool())
            arr.put(buildExecuteMultipleCommandsTool())
            arr.put(buildSetFilePermissionsTool())
            arr.put(buildGetFilePermissionsTool())
            arr.put(buildCountLinesInFileTool())
            arr.put(buildCountFilesInDirectoryTool())
            arr.put(buildSortFilesBySizeTool())
            return arr
        }

        fun toolNames(): List<String> = listOf(
            "executeCommand", "compileProject", "runTests", "gitCommand",
            "listDirectoryTree", "checkFileExists", "getFileInfo", "findFileByName",
            "getEnvironmentVariable", "listEnvironmentVariables", "getCurrentWorkingDirectory",
            "executeMultipleCommands", "setFilePermissions", "getFilePermissions",
            "countLinesInFile", "countFilesInDirectory", "sortFilesBySize",
        )

        private fun buildExecuteCommandTool() = toolDef("executeCommand",
            "执行任意 shell 命令。用于编译、运行、调试等操作。谨慎使用，避免执行危险命令。",
            listOf("command"),
            "command" to p("string", "要执行的 shell 命令，如'ls -la'或'./gradlew build'"),
            "workingDir" to p("string", "工作目录，相对项目根目录，留空使用项目根目录"),
            "timeoutSeconds" to p("integer", "超时时间（秒），默认60秒"),
        )

        private fun buildCompileProjectTool() = toolDef("compileProject",
            "编译项目。默认使用 Gradle 构建命令。适用于 Android 项目编译。",
            props = arrayOf(
                "workingDir" to p("string", "项目目录，相对项目根目录，留空使用项目根目录"),
                "buildCommand" to p("string", "构建命令，默认'./gradlew assembleDebug'"),
            ),
        )

        private fun buildRunTestsTool() = toolDef("runTests",
            "运行项目测试。默认使用 Gradle 测试命令。",
            props = arrayOf(
                "workingDir" to p("string", "项目目录，相对项目根目录，留空使用项目根目录"),
                "testCommand" to p("string", "测试命令，默认'./gradlew test'"),
            ),
        )

        private fun buildGitCommandTool() = toolDef("gitCommand",
            "执行 Git 命令。用于版本控制操作，如'commit'、'push'、'status'等。",
            listOf("command"),
            "command" to p("string", "Git 子命令，如'status'、'log --oneline'、'commit -m \"message\"'"),
            "workingDir" to p("string", "Git 仓库目录，相对项目根目录"),
        )

        private fun buildListDirectoryTreeTool() = toolDef("listDirectoryTree",
            "显示目录树结构。支持深度控制和忽略模式。",
            props = arrayOf(
                "path" to p("string", "起始目录，相对项目根目录，留空从根目录开始"),
                "depth" to p("integer", "递归深度，默认2"),
                "ignorePatterns" to p("string", "忽略模式，逗号分隔，如'*.class,*.jar,.git'"),
            ),
        )

        private fun buildCheckFileExistsTool() = toolDef("checkFileExists",
            "检查文件或目录是否存在。",
            listOf("path"),
            "path" to p("string", "文件或目录路径，相对项目根目录"),
        )

        private fun buildGetFileInfoTool() = toolDef("getFileInfo",
            "获取文件详细信息，包括大小、类型、修改时间等。",
            listOf("path"),
            "path" to p("string", "文件或目录路径，相对项目根目录"),
        )

        private fun buildFindFileByNameTool() = toolDef("findFileByName",
            "按文件名搜索文件。支持大小写不敏感匹配。",
            listOf("name"),
            "name" to p("string", "要查找的文件名，如'MainActivity.kt'"),
            "startPath" to p("string", "搜索起始目录，相对项目根目录，留空从根目录开始"),
            "caseSensitive" to p("boolean", "是否大小写敏感，默认false"),
        )

        private fun buildGetEnvironmentVariableTool() = toolDef("getEnvironmentVariable",
            "获取环境变量的值。",
            listOf("name"),
            "name" to p("string", "环境变量名称，如'PATH'、'HOME'"),
        )

        private fun buildListEnvironmentVariablesTool() = toolDef("listEnvironmentVariables",
            "列出所有环境变量及其值。",
            props = emptyArray(),
        )

        private fun buildGetCurrentWorkingDirectoryTool() = toolDef("getCurrentWorkingDirectory",
            "获取当前工作目录路径。",
            props = emptyArray(),
        )

        private fun buildExecuteMultipleCommandsTool() = toolDef("executeMultipleCommands",
            "执行多个命令，用 && 连接。所有命令成功才返回成功。",
            listOf("commands"),
            "commands" to p("string", "命令列表，JSON数组格式，如'[\"ls -la\",\"pwd\"]'"),
            "workingDir" to p("string", "工作目录，相对项目根目录"),
            "timeoutSeconds" to p("integer", "超时时间（秒），默认60秒"),
        )

        private fun buildSetFilePermissionsTool() = toolDef("setFilePermissions",
            "设置文件或目录的权限。使用八进制格式如'755'。",
            listOf("path", "permissions"),
            "path" to p("string", "文件或目录路径，相对项目根目录"),
            "permissions" to p("string", "权限设置，八进制格式如'755'"),
        )

        private fun buildGetFilePermissionsTool() = toolDef("getFilePermissions",
            "获取文件或目录的权限信息。",
            listOf("path"),
            "path" to p("string", "文件或目录路径，相对项目根目录"),
        )

        private fun buildCountLinesInFileTool() = toolDef("countLinesInFile",
            "统计文件的行数。",
            listOf("path"),
            "path" to p("string", "文件路径，相对项目根目录"),
        )

        private fun buildCountFilesInDirectoryTool() = toolDef("countFilesInDirectory",
            "统计目录中的文件数量，支持递归统计。",
            props = arrayOf(
                "path" to p("string", "目录路径，相对项目根目录，留空统计根目录"),
                "recursive" to p("boolean", "是否递归统计子目录，默认false"),
            ),
        )

        private fun buildSortFilesBySizeTool() = toolDef("sortFilesBySize",
            "按文件大小排序并显示，默认降序。",
            props = arrayOf(
                "path" to p("string", "目录路径，相对项目根目录，留空显示根目录"),
                "descending" to p("boolean", "是否降序排列，默认true"),
            ),
        )
    }
}
