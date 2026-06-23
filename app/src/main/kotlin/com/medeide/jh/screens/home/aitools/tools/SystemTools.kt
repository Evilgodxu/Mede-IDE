package com.medeide.jh.screens.home.aitools.tools

import android.content.Context
import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger
import android.os.Build
import android.os.Environment

class SystemTools(
    private val fileManager: FileManager?,
    private val context: Context?,
) {
    fun getDeviceInfo(): String {
        Log.d("SystemTools", "getDeviceInfo")
        FileLogger.d("SystemTools", "getDeviceInfo")

        return buildString {
            appendLine(ok("Device Information:"))
            appendLine("  Manufacturer: ${Build.MANUFACTURER}")
            appendLine("  Model: ${Build.MODEL}")
            appendLine("  Device: ${Build.DEVICE}")
            appendLine("  Product: ${Build.PRODUCT}")
            appendLine("  Brand: ${Build.BRAND}")
            appendLine("  Hardware: ${Build.HARDWARE}")
            appendLine("  Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("  Build ID: ${Build.ID}")
            appendLine("  Build Type: ${Build.TYPE}")
            appendLine("  Display: ${Build.DISPLAY}")
        }.trimEnd()
    }

    fun getStorageInfo(): String {
        Log.d("SystemTools", "getStorageInfo")
        FileLogger.d("SystemTools", "getStorageInfo")

        return try {
            val externalStorage = Environment.getExternalStorageDirectory()
            val totalSpace = externalStorage.totalSpace
            val freeSpace = externalStorage.freeSpace
            val usableSpace = externalStorage.usableSpace

            return buildString {
                appendLine(ok("Storage Information:"))
                appendLine("  External Storage: ${externalStorage.absolutePath}")
                appendLine("  Total Space: ${formatSize(totalSpace)}")
                appendLine("  Free Space: ${formatSize(freeSpace)}")
                appendLine("  Usable Space: ${formatSize(usableSpace)}")
                appendLine("  Used Space: ${formatSize(totalSpace - freeSpace)}")
                appendLine("  Usage: ${String.format("%.1f%%", ((totalSpace - freeSpace).toDouble() / totalSpace.toDouble()) * 100)}")
            }.trimEnd()
        } catch (e: Exception) {
            Log.e("SystemTools", "getStorageInfo failed: ${e.message}", e)
            FileLogger.e("SystemTools", "getStorageInfo failed: ${e.message}", e)
            err("Failed to get storage info: ${e.message}")
        }
    }

    fun getMemoryInfo(): String {
        Log.d("SystemTools", "getMemoryInfo")
        FileLogger.d("SystemTools", "getMemoryInfo")

        return try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory

            return buildString {
                appendLine(ok("Memory Information:"))
                appendLine("  Maximum Memory: ${formatSize(maxMemory)}")
                appendLine("  Total Memory: ${formatSize(totalMemory)}")
                appendLine("  Free Memory: ${formatSize(freeMemory)}")
                appendLine("  Used Memory: ${formatSize(usedMemory)}")
                appendLine("  Usage: ${String.format("%.1f%%", (usedMemory.toDouble() / maxMemory.toDouble()) * 100)}")
            }.trimEnd()
        } catch (e: Exception) {
            Log.e("SystemTools", "getMemoryInfo failed: ${e.message}", e)
            FileLogger.e("SystemTools", "getMemoryInfo failed: ${e.message}", e)
            err("Failed to get memory info: ${e.message}")
        }
    }

    fun getCpuInfo(): String {
        Log.d("SystemTools", "getCpuInfo")
        FileLogger.d("SystemTools", "getCpuInfo")

        return try {
            val process = ProcessBuilder()
                .command("/system/bin/cat", "/proc/cpuinfo")
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 50) {
                output.appendLine(line)
                count++
            }

            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            ok(output.toString().trimEnd())
        } catch (e: Exception) {
            Log.e("SystemTools", "getCpuInfo failed: ${e.message}", e)
            FileLogger.e("SystemTools", "getCpuInfo failed: ${e.message}", e)
            err("Failed to get CPU info: ${e.message}")
        }
    }

    fun getNetworkInfo(): String {
        Log.d("SystemTools", "getNetworkInfo")
        FileLogger.d("SystemTools", "getNetworkInfo")

        return try {
            val process = ProcessBuilder()
                .command("/system/bin/getprop", "net.dns1")
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val dns1 = reader.readLine()?.trim() ?: "Unknown"

            val ipProcess = ProcessBuilder()
                .command("/system/bin/ip", "route", "get", "1")
                .redirectErrorStream(true)
                .start()

            val ipReader = java.io.BufferedReader(java.io.InputStreamReader(ipProcess.inputStream))
            var ipLine: String?
            var localIp = "Unknown"
            while (ipReader.readLine().also { ipLine = it } != null) {
                val match = Regex("src\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(ipLine ?: "")
                if (match != null) {
                    localIp = match.groupValues[1]
                    break
                }
            }

            ipProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)

            return buildString {
                appendLine(ok("Network Information:"))
                appendLine("  Local IP Address: $localIp")
                appendLine("  DNS Server: $dns1")
            }.trimEnd()
        } catch (e: Exception) {
            Log.e("SystemTools", "getNetworkInfo failed: ${e.message}", e)
            FileLogger.e("SystemTools", "getNetworkInfo failed: ${e.message}", e)
            err("Failed to get network info: ${e.message}")
        }
    }

    fun getSystemProperties(): String {
        Log.d("SystemTools", "getSystemProperties")
        FileLogger.d("SystemTools", "getSystemProperties")

        return try {
            val process = ProcessBuilder()
                .command("/system/bin/getprop")
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 100) {
                output.appendLine(line)
                count++
            }

            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            ok(output.toString().trimEnd())
        } catch (e: Exception) {
            Log.e("SystemTools", "getSystemProperties failed: ${e.message}", e)
            FileLogger.e("SystemTools", "getSystemProperties failed: ${e.message}", e)
            err("Failed to get system properties: ${e.message}")
        }
    }

    fun getCurrentTime(): String {
        Log.d("SystemTools", "getCurrentTime")
        FileLogger.d("SystemTools", "getCurrentTime")

        val now = java.util.Date()
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", java.util.Locale.getDefault())
        return ok("Current time: ${formatter.format(now)}")
    }

    fun getAppVersion(): String {
        Log.d("SystemTools", "getAppVersion")
        FileLogger.d("SystemTools", "getAppVersion")

        val ctx = context ?: return err("Context not available")

        return try {
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            return buildString {
                appendLine(ok("App Version Information:"))
                appendLine("  Package Name: ${packageInfo.packageName}")
                appendLine("  Version Name: ${packageInfo.versionName}")
                appendLine("  Version Code: ${packageInfo.versionCode}")
            }.trimEnd()
        } catch (e: Exception) {
            Log.e("SystemTools", "getAppVersion failed: ${e.message}", e)
            FileLogger.e("SystemTools", "getAppVersion failed: ${e.message}", e)
            err("Failed to get app version: ${e.message}")
        }
    }

    fun listInstalledPackages(): String {
        Log.d("SystemTools", "listInstalledPackages")
        FileLogger.d("SystemTools", "listInstalledPackages")

        val ctx = context ?: return err("Context not available")

        return try {
            val packages = ctx.packageManager.getInstalledPackages(0)
            val filtered = packages.filter { pkg ->
                pkg.applicationInfo?.enabled == true && !pkg.packageName.startsWith("com.android")
            }.sortedBy { it.packageName }

            return buildString {
                appendLine(ok("Installed Packages (${filtered.size}):"))
                filtered.take(50).forEach { pkg ->
                    appendLine("  ${pkg.packageName} (${pkg.versionName})")
                }
                if (filtered.size > 50) {
                    appendLine("  ... and ${filtered.size - 50} more packages")
                }
            }.trimEnd()
        } catch (e: Exception) {
            Log.e("SystemTools", "listInstalledPackages failed: ${e.message}", e)
            FileLogger.e("SystemTools", "listInstalledPackages failed: ${e.message}", e)
            err("Failed to list installed packages: ${e.message}")
        }
    }

    fun checkInternetConnection(): String {
        Log.d("SystemTools", "checkInternetConnection")
        FileLogger.d("SystemTools", "checkInternetConnection")

        return try {
            val process = ProcessBuilder()
                .command("/system/bin/ping", "-c", "1", "-W", "5", "google.com")
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            val exitCode = process.exitValue()

            if (exitCode == 0) {
                ok("Internet connection: [OK] Connected")
            } else {
                ok("Internet connection: [WARNING] Not connected or slow")
            }
        } catch (e: Exception) {
            Log.e("SystemTools", "checkInternetConnection failed: ${e.message}", e)
            FileLogger.e("SystemTools", "checkInternetConnection failed: ${e.message}", e)
            err("Failed to check internet: ${e.message}")
        }
    }

    fun executeSystemCommand(command: String): String {
        Log.d("SystemTools", "executeSystemCommand: $command")
        FileLogger.d("SystemTools", "executeSystemCommand: $command")

        return try {
            val process = ProcessBuilder()
                .command("/system/bin/sh", "-c", command)
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
                return err("Command timed out")
            }

            val exitCode = process.exitValue()
            val result = output.toString().trimEnd()

            if (exitCode != 0) {
                ok("Exit code: $exitCode\n$result")
            } else {
                ok(result)
            }
        } catch (e: Exception) {
            Log.e("SystemTools", "executeSystemCommand failed: ${e.message}", e)
            FileLogger.e("SystemTools", "executeSystemCommand failed: ${e.message}", e)
            err("Command execution failed: ${e.message}")
        }
    }

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildGetDeviceInfoTool())
            arr.put(buildGetStorageInfoTool())
            arr.put(buildGetMemoryInfoTool())
            arr.put(buildGetCpuInfoTool())
            arr.put(buildGetNetworkInfoTool())
            arr.put(buildGetCurrentTimeTool())
            arr.put(buildGetAppVersionTool())
            arr.put(buildCheckInternetConnectionTool())
            arr.put(buildExecuteSystemCommandTool())
            return arr
        }

        fun toolNames(): List<String> = listOf(
            "getDeviceInfo", "getStorageInfo", "getMemoryInfo",
            "getCpuInfo", "getNetworkInfo", "getCurrentTime",
            "getAppVersion", "checkInternetConnection", "executeSystemCommand",
        )

        private fun buildGetDeviceInfoTool() = toolDef("getDeviceInfo",
            "获取设备信息，包括制造商、型号、Android 版本等。",
            props = emptyArray(),
        )

        private fun buildGetStorageInfoTool() = toolDef("getStorageInfo",
            "获取存储信息，包括总空间、可用空间、已用空间等。",
            props = emptyArray(),
        )

        private fun buildGetMemoryInfoTool() = toolDef("getMemoryInfo",
            "获取内存信息，包括最大内存、已用内存等。",
            props = emptyArray(),
        )

        private fun buildGetCpuInfoTool() = toolDef("getCpuInfo",
            "获取 CPU 信息。读取 /proc/cpuinfo 文件内容。",
            props = emptyArray(),
        )

        private fun buildGetNetworkInfoTool() = toolDef("getNetworkInfo",
            "获取网络信息，包括本地 IP 地址和 DNS 服务器。",
            props = emptyArray(),
        )

        private fun buildGetCurrentTimeTool() = toolDef("getCurrentTime",
            "获取当前系统时间。",
            props = emptyArray(),
        )

        private fun buildGetAppVersionTool() = toolDef("getAppVersion",
            "获取应用版本信息。",
            props = emptyArray(),
        )

        private fun buildCheckInternetConnectionTool() = toolDef("checkInternetConnection",
            "检查网络连接状态。通过 ping google.com 判断。",
            props = emptyArray(),
        )

        private fun buildExecuteSystemCommandTool() = toolDef("executeSystemCommand",
            "执行系统级命令。谨慎使用，避免执行危险命令。",
            listOf("command"),
            "command" to p("string", "要执行的系统命令"),
        )
    }
}