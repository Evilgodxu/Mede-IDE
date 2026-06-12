package com.medeide.jh.core.utils

/**
 * Termux 环境 shell 执行器
 * 通过 ProcessBuilder 调用 Termux 的 bash，设置正确的 PATH 和 HOME 环境变量，
 * 使系统可以检测并执行 Termux 中安装的开发工具（node, python, java, gradle 等）。
 */
object TermuxShell {

    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private const val BASH = "$TERMUX_PREFIX/bin/bash"

    /** 是否安装了 Termux（通过尝试执行 bash 检测） */
    val isAvailable: Boolean by lazy {
        try {
            val p = ProcessBuilder(BASH, "-c", "echo ok")
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor() == 0 && out.contains("ok")
        } catch (_: Exception) { false }
    }

    private val env
        get() = mapOf(
            "HOME" to TERMUX_HOME,
            "PREFIX" to TERMUX_PREFIX,
            "PATH" to "$TERMUX_PREFIX/bin:$TERMUX_PREFIX/bin/applets:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to "$TERMUX_PREFIX/lib",
            "TMPDIR" to "$TERMUX_PREFIX/tmp",
        )

    /**
     * 执行命令并返回 stdout
     * @return Pair<exitCode, output>
     */
    fun exec(command: String): Pair<Int, String> {
        return try {
            val process = ProcessBuilder(BASH, "-lc", command)
                .apply { environment().putAll(env) }
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            exitCode to output
        } catch (e: Exception) {
            -1 to (e.message ?: "unknown error")
        }
    }

    /** 执行命令并返回 stdout（仅成功时） */
    fun execOrNull(command: String): String? {
        val (exitCode, output) = exec(command)
        return output.takeIf { exitCode == 0 && output.isNotBlank() }
    }

    /** 检测命令是否可用 */
    fun hasCommand(cmd: String): Boolean {
        if (!isAvailable) return false
        val (exitCode, output) = exec("command -v $cmd")
        return exitCode == 0 && output.trim().isNotEmpty()
    }

    /** 检测 Java 版本 >= 指定版本 */
    fun hasJavaVersion(version: Int): Boolean {
        if (!isAvailable) return false
        val (_, output) = exec("java -version 2>&1 | head -1")
        return output.contains("\"$version.") || output.contains("openjdk $version")
    }
}
