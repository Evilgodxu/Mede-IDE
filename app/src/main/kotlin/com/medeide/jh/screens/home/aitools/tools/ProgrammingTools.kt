package com.medeide.jh.screens.home.aitools.tools

import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger

class ProgrammingTools(
    private val fileManager: FileManager?,
) {
    fun runPythonScript(
        scriptContent: String,
        workingDir: String = "",
    ): String {
        Log.d("ProgrammingTools", "runPythonScript: ${scriptContent.length} chars")
        FileLogger.d("ProgrammingTools", "runPythonScript")

        val fm = fileManager ?: return err("No project folder is open.")
        val cwd = if (workingDir.isBlank()) {
            fm.projectDirPath.ifEmpty { fm.storageRootPath }
        } else {
            if (workingDir.startsWith("/")) workingDir else "${fm.projectDirPath}/$workingDir"
        }

        return try {
            val tempFile = java.io.File.createTempFile("script", ".py", java.io.File(cwd))
            tempFile.writeText(scriptContent)

            val process = ProcessBuilder()
                .command("python3", tempFile.name)
                .directory(java.io.File(cwd))
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            val completed = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                tempFile.delete()
                return err("Python script timed out")
            }

            val exitCode = process.exitValue()
            tempFile.delete()

            if (exitCode != 0) {
                ok("Exit code: $exitCode\n${output.toString().trimEnd()}")
            } else {
                ok(output.toString().trimEnd())
            }
        } catch (e: Exception) {
            Log.e("ProgrammingTools", "runPythonScript failed: ${e.message}", e)
            FileLogger.e("ProgrammingTools", "runPythonScript failed: ${e.message}", e)
            err("Failed to run Python script: ${e.message}")
        }
    }

    fun runJavaScript(
        code: String,
        workingDir: String = "",
    ): String {
        Log.d("ProgrammingTools", "runJavaScript: ${code.length} chars")
        FileLogger.d("ProgrammingTools", "runJavaScript")

        val fm = fileManager ?: return err("No project folder is open.")
        val cwd = if (workingDir.isBlank()) {
            fm.projectDirPath.ifEmpty { fm.storageRootPath }
        } else {
            if (workingDir.startsWith("/")) workingDir else "${fm.projectDirPath}/$workingDir"
        }

        return try {
            val tempFile = java.io.File.createTempFile("script", ".js", java.io.File(cwd))
            tempFile.writeText(code)

            val process = ProcessBuilder()
                .command("node", tempFile.name)
                .directory(java.io.File(cwd))
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            val completed = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                tempFile.delete()
                return err("JavaScript timed out")
            }

            val exitCode = process.exitValue()
            tempFile.delete()

            if (exitCode != 0) {
                ok("Exit code: $exitCode\n${output.toString().trimEnd()}")
            } else {
                ok(output.toString().trimEnd())
            }
        } catch (e: Exception) {
            Log.e("ProgrammingTools", "runJavaScript failed: ${e.message}", e)
            FileLogger.e("ProgrammingTools", "runJavaScript failed: ${e.message}", e)
            err("Failed to run JavaScript: ${e.message}")
        }
    }

    fun runShellScript(
        scriptContent: String,
        workingDir: String = "",
    ): String {
        Log.d("ProgrammingTools", "runShellScript: ${scriptContent.length} chars")
        FileLogger.d("ProgrammingTools", "runShellScript")

        val fm = fileManager ?: return err("No project folder is open.")
        val cwd = if (workingDir.isBlank()) {
            fm.projectDirPath.ifEmpty { fm.storageRootPath }
        } else {
            if (workingDir.startsWith("/")) workingDir else "${fm.projectDirPath}/$workingDir"
        }

        return try {
            val tempFile = java.io.File.createTempFile("script", ".sh", java.io.File(cwd))
            tempFile.writeText("#!/bin/bash\n$scriptContent")
            tempFile.setExecutable(true)

            val process = ProcessBuilder()
                .command("/system/bin/sh", tempFile.name)
                .directory(java.io.File(cwd))
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            val completed = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                tempFile.delete()
                return err("Shell script timed out")
            }

            val exitCode = process.exitValue()
            tempFile.delete()

            if (exitCode != 0) {
                ok("Exit code: $exitCode\n${output.toString().trimEnd()}")
            } else {
                ok(output.toString().trimEnd())
            }
        } catch (e: Exception) {
            Log.e("ProgrammingTools", "runShellScript failed: ${e.message}", e)
            FileLogger.e("ProgrammingTools", "runShellScript failed: ${e.message}", e)
            err("Failed to run shell script: ${e.message}")
        }
    }

    fun formatCode(
        code: String,
        language: String = "kotlin",
    ): String {
        Log.d("ProgrammingTools", "formatCode: language=$language")
        FileLogger.d("ProgrammingTools", "formatCode: language=$language")

        return when (language.lowercase()) {
            "kotlin" -> formatKotlin(code)
            "java" -> formatJava(code)
            "python" -> formatPython(code)
            "javascript", "js" -> formatJavaScript(code)
            "json" -> formatJson(code)
            else -> err("Unsupported language for formatting: $language")
        }
    }

    private fun formatKotlin(code: String): String {
        return try {
            val lines = code.lines()
            val formatted = mutableListOf<String>()
            var indent = 0

            for (line in lines) {
                val trimmed = line.trim()

                if (trimmed.endsWith("{") && !trimmed.startsWith("}")) {
                    formatted.add("    ".repeat(indent) + trimmed)
                    indent++
                } else if (trimmed.startsWith("}")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                } else if (trimmed.startsWith("else") || trimmed.startsWith("catch") || trimmed.startsWith("finally")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                    if (trimmed.contains("{")) {
                        indent++
                    }
                } else {
                    formatted.add("    ".repeat(indent) + trimmed)
                }
            }

            ok(formatted.joinToString("\n"))
        } catch (e: Exception) {
            err("Failed to format Kotlin code: ${e.message}")
        }
    }

    private fun formatJava(code: String): String {
        return try {
            val lines = code.lines()
            val formatted = mutableListOf<String>()
            var indent = 0

            for (line in lines) {
                val trimmed = line.trim()

                if (trimmed.endsWith("{") && !trimmed.startsWith("}")) {
                    formatted.add("    ".repeat(indent) + trimmed)
                    indent++
                } else if (trimmed.startsWith("}")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                } else if (trimmed.startsWith("else") || trimmed.startsWith("catch") || trimmed.startsWith("finally")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                    if (trimmed.contains("{")) {
                        indent++
                    }
                } else {
                    formatted.add("    ".repeat(indent) + trimmed)
                }
            }

            ok(formatted.joinToString("\n"))
        } catch (e: Exception) {
            err("Failed to format Java code: ${e.message}")
        }
    }

    private fun formatPython(code: String): String {
        return try {
            val lines = code.lines()
            val formatted = mutableListOf<String>()
            var indent = 0

            for (line in lines) {
                val trimmed = line.trim()

                if (trimmed.isEmpty()) {
                    formatted.add("")
                    continue
                }

                if (trimmed.endsWith(":")) {
                    formatted.add("    ".repeat(indent) + trimmed)
                    indent++
                } else if (trimmed.startsWith("return") || trimmed.startsWith("break") || trimmed.startsWith("continue") || trimmed.startsWith("pass") || trimmed.startsWith("raise")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                } else if (trimmed.startsWith("elif") || trimmed.startsWith("else")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                    if (trimmed.contains(":")) {
                        indent++
                    }
                } else if (trimmed.startsWith("except") || trimmed.startsWith("finally")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                    if (trimmed.contains(":")) {
                        indent++
                    }
                } else {
                    formatted.add("    ".repeat(indent) + trimmed)
                }
            }

            ok(formatted.joinToString("\n"))
        } catch (e: Exception) {
            err("Failed to format Python code: ${e.message}")
        }
    }

    private fun formatJavaScript(code: String): String {
        return try {
            val lines = code.lines()
            val formatted = mutableListOf<String>()
            var indent = 0

            for (line in lines) {
                val trimmed = line.trim()

                if (trimmed.endsWith("{") && !trimmed.startsWith("}")) {
                    formatted.add("    ".repeat(indent) + trimmed)
                    indent++
                } else if (trimmed.startsWith("}")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                } else if (trimmed.startsWith("else") || trimmed.startsWith("catch") || trimmed.startsWith("finally")) {
                    indent = (indent - 1).coerceAtLeast(0)
                    formatted.add("    ".repeat(indent) + trimmed)
                    if (trimmed.contains("{")) {
                        indent++
                    }
                } else {
                    formatted.add("    ".repeat(indent) + trimmed)
                }
            }

            ok(formatted.joinToString("\n"))
        } catch (e: Exception) {
            err("Failed to format JavaScript code: ${e.message}")
        }
    }

    private fun formatJson(code: String): String {
        return try {
            val json = org.json.JSONObject(code)
            ok(json.toString(4))
        } catch (e: Exception) {
            err("Invalid JSON: ${e.message}")
        }
    }

    fun validateJson(jsonString: String): String {
        Log.d("ProgrammingTools", "validateJson: ${jsonString.length} chars")
        FileLogger.d("ProgrammingTools", "validateJson")

        return try {
            org.json.JSONObject(jsonString)
            ok("JSON is valid")
        } catch (e: Exception) {
            err("Invalid JSON: ${e.message}")
        }
    }

    fun validateXml(xmlString: String): String {
        Log.d("ProgrammingTools", "validateXml: ${xmlString.length} chars")
        FileLogger.d("ProgrammingTools", "validateXml")

        return try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            builder.parse(org.xml.sax.InputSource(java.io.StringReader(xmlString)))
            ok("XML is valid")
        } catch (e: Exception) {
            err("Invalid XML: ${e.message}")
        }
    }

    fun generateUUID(): String {
        Log.d("ProgrammingTools", "generateUUID")
        FileLogger.d("ProgrammingTools", "generateUUID")

        return ok(java.util.UUID.randomUUID().toString())
    }

    fun calculateChecksum(
        path: String,
        algorithm: String = "MD5",
    ): String {
        Log.d("ProgrammingTools", "calculateChecksum: path=$path algorithm=$algorithm")
        FileLogger.d("ProgrammingTools", "calculateChecksum: path=$path")

        val fm = fileManager ?: return err("No project folder is open.")
        val fullPath = resolvePathOrAbsolute(path, fm)

        return try {
            val file = java.io.File(fullPath)
            if (!file.exists()) return err("File not found: $fullPath")

            val digest = java.security.MessageDigest.getInstance(algorithm.uppercase())
            val inputStream = java.io.FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()

            val hash = digest.digest()
            val hexString = StringBuilder()
            for (byte in hash) {
                val hex = Integer.toHexString(0xff and byte.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }

            ok("$algorithm checksum for $path: ${hexString.toString().uppercase()}")
        } catch (e: Exception) {
            Log.e("ProgrammingTools", "calculateChecksum failed: ${e.message}", e)
            FileLogger.e("ProgrammingTools", "calculateChecksum failed: ${e.message}", e)
            err("Failed to calculate checksum: ${e.message}")
        }
    }

    fun encodeBase64(input: String): String {
        Log.d("ProgrammingTools", "encodeBase64: ${input.length} chars")
        FileLogger.d("ProgrammingTools", "encodeBase64")

        return try {
            val encoded = android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
            ok(encoded)
        } catch (e: Exception) {
            err("Failed to encode: ${e.message}")
        }
    }

    fun decodeBase64(encoded: String): String {
        Log.d("ProgrammingTools", "decodeBase64: ${encoded.length} chars")
        FileLogger.d("ProgrammingTools", "decodeBase64")

        return try {
            val decoded = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            ok(String(decoded))
        } catch (e: Exception) {
            err("Failed to decode: ${e.message}")
        }
    }

    fun escapeString(input: String): String {
        Log.d("ProgrammingTools", "escapeString: ${input.length} chars")
        FileLogger.d("ProgrammingTools", "escapeString")

        val escaped = input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\'", "\\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return ok(escaped)
    }

    fun unescapeString(input: String): String {
        Log.d("ProgrammingTools", "unescapeString: ${input.length} chars")
        FileLogger.d("ProgrammingTools", "unescapeString")

        val unescaped = input
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\'", "\'")
            .replace("\\\\", "\\")
        return ok(unescaped)
    }

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildRunPythonScriptTool())
            arr.put(buildRunJavaScriptTool())
            arr.put(buildRunShellScriptTool())
            arr.put(buildFormatCodeTool())
            arr.put(buildValidateJsonTool())
            arr.put(buildValidateXmlTool())
            arr.put(buildGenerateUUIDTool())
            arr.put(buildCalculateChecksumTool())
            arr.put(buildEncodeBase64Tool())
            arr.put(buildDecodeBase64Tool())
            arr.put(buildEscapeStringTool())
            arr.put(buildUnescapeStringTool())
            return arr
        }

        fun toolNames(): List<String> = listOf(
            "runPythonScript", "runJavaScript", "runShellScript",
            "formatCode", "validateJson", "validateXml",
            "generateUUID", "calculateChecksum",
            "encodeBase64", "decodeBase64",
            "escapeString", "unescapeString",
        )

        private fun buildRunPythonScriptTool() = toolDef("runPythonScript",
            "执行 Python 脚本代码。将代码写入临时文件并执行，返回输出结果。",
            listOf("scriptContent"),
            "scriptContent" to p("string", "Python 脚本代码内容"),
            "workingDir" to p("string", "工作目录，相对项目根目录"),
        )

        private fun buildRunJavaScriptTool() = toolDef("runJavaScript",
            "执行 JavaScript 代码。需要系统安装 Node.js。",
            listOf("code"),
            "code" to p("string", "JavaScript 代码内容"),
            "workingDir" to p("string", "工作目录，相对项目根目录"),
        )

        private fun buildRunShellScriptTool() = toolDef("runShellScript",
            "执行 Shell 脚本代码。写入临时文件并执行。",
            listOf("scriptContent"),
            "scriptContent" to p("string", "Shell 脚本代码内容"),
            "workingDir" to p("string", "工作目录，相对项目根目录"),
        )

        private fun buildFormatCodeTool() = toolDef("formatCode",
            "格式化代码。支持 Kotlin、Java、Python、JavaScript、JSON。",
            listOf("code"),
            "code" to p("string", "要格式化的代码"),
            "language" to p("string", "语言：kotlin/java/python/javascript/json，默认kotlin"),
        )

        private fun buildValidateJsonTool() = toolDef("validateJson",
            "验证 JSON 字符串是否有效。",
            listOf("jsonString"),
            "jsonString" to p("string", "JSON 字符串"),
        )

        private fun buildValidateXmlTool() = toolDef("validateXml",
            "验证 XML 字符串是否有效。",
            listOf("xmlString"),
            "xmlString" to p("string", "XML 字符串"),
        )

        private fun buildGenerateUUIDTool() = toolDef("generateUUID",
            "生成一个新的 UUID。",
            props = emptyArray(),
        )

        private fun buildCalculateChecksumTool() = toolDef("calculateChecksum",
            "计算文件的校验和。支持 MD5、SHA-1、SHA-256 等算法。",
            listOf("path"),
            "path" to p("string", "文件路径，相对项目根目录"),
            "algorithm" to p("string", "算法名称，如MD5、SHA-1、SHA-256，默认MD5"),
        )

        private fun buildEncodeBase64Tool() = toolDef("encodeBase64",
            "将字符串编码为 Base64。",
            listOf("input"),
            "input" to p("string", "要编码的字符串"),
        )

        private fun buildDecodeBase64Tool() = toolDef("decodeBase64",
            "将 Base64 字符串解码为原始字符串。",
            listOf("encoded"),
            "encoded" to p("string", "Base64 编码的字符串"),
        )

        private fun buildEscapeStringTool() = toolDef("escapeString",
            "转义字符串中的特殊字符，用于插入代码或 JSON。",
            listOf("input"),
            "input" to p("string", "要转义的字符串"),
        )

        private fun buildUnescapeStringTool() = toolDef("unescapeString",
            "反转义字符串中的特殊字符。",
            listOf("input"),
            "input" to p("string", "要反转义的字符串"),
        )
    }
}