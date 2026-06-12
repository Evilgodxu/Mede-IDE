package com.medeide.jh.data.utils

import android.os.Build
import android.util.Log
import java.io.File

/**
 * CPU 特性检测工具，识别 ARM CPU 支持的指令集扩展。
 *
 * 读取 /proc/cpuinfo 的 Features 行，用于选择最优 native 库。
 */
object CpuFeatureDetector {

    data class CpuFeatures(
        val hasFp16: Boolean = false,
        val hasDotProd: Boolean = false,
        val hasSve: Boolean = false,
        val hasI8mm: Boolean = false,
        val isAtLeastArmV82: Boolean = false,
        val isAtLeastArmV84: Boolean = false,
        val isEmulated: Boolean = false,
        val isArm64V8a: Boolean = false,
        val isArmV7a: Boolean = false,
        val cpuCount: Int = 0,
        val cpuModel: String = "",
    ) {

        /** 推荐的最优特性等级名，用于日志/UI 显示 */
        val featureLevel: String get() = when {
            isAtLeastArmV84 && hasSve && hasI8mm && hasFp16 && hasDotProd -> "ARMv8.4+SVE+I8MM+FP16+DotProd"
            isAtLeastArmV84 && hasSve && hasFp16 && hasDotProd -> "ARMv8.4+SVE+FP16+DotProd"
            isAtLeastArmV84 && hasI8mm && hasFp16 && hasDotProd -> "ARMv8.4+I8MM+FP16+DotProd"
            isAtLeastArmV84 && hasFp16 && hasDotProd -> "ARMv8.4+FP16+DotProd"
            isAtLeastArmV82 && hasFp16 && hasDotProd -> "ARMv8.2+FP16+DotProd"
            isAtLeastArmV82 && hasFp16 -> "ARMv8.2+FP16"
            isArm64V8a -> "ARMv8.0 (arm64-v8a)"
            isArmV7a -> "ARMv7a (armeabi-v7a)"
            else -> "Unknown"
        }
    }

    private var cached: CpuFeatures? = null

    /** 检测 CPU 特性（带缓存） */
    fun detect(): CpuFeatures {
        cached?.let { return it }
        val features = detectInternal().also { cached = it }
        Log.d("CpuFeatureDetector", "CPU features: $features")
        FileLogger.d("CpuFeatureDetector", "CPU features: $features")
        return features
    }

    /** 清除缓存（设备热插拔场景） */
    fun clearCache() { cached = null }

    private fun detectInternal(): CpuFeatures {
        val cpuFeaturesStr = readCpuFeatures()
        val cpuModel = readCpuModel()
        val cpuCount = readCpuCount()
        val isEmulated = Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")

        val hasFp16 = cpuFeaturesStr.contains("fp16") || cpuFeaturesStr.contains("fphp")
        val hasDotProd = cpuFeaturesStr.contains("dotprod") || cpuFeaturesStr.contains("asimddp")
        val hasSve = cpuFeaturesStr.contains("sve")
        val hasI8mm = cpuFeaturesStr.contains("i8mm")
        val hasAsimd = cpuFeaturesStr.contains("asimd")
        val hasCrc32 = cpuFeaturesStr.contains("crc32")
        val hasAes = cpuFeaturesStr.contains("aes")
        val hasDcpop = cpuFeaturesStr.contains("dcpop")
        val hasUscat = cpuFeaturesStr.contains("uscat")

        val isAtLeastArmV82 = hasAsimd && hasCrc32 && hasAes
        val isAtLeastArmV84 = isAtLeastArmV82 && hasDcpop && hasUscat
        val isArm64V8a = Build.SUPPORTED_ABIS.isNotEmpty() && Build.SUPPORTED_ABIS[0] == "arm64-v8a"
        val isArmV7a = !isArm64V8a && Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() &&
            Build.SUPPORTED_32_BIT_ABIS[0]?.equals("armeabi-v7a") == true

        return CpuFeatures(
            hasFp16 = hasFp16,
            hasDotProd = hasDotProd,
            hasSve = hasSve,
            hasI8mm = hasI8mm,
            isAtLeastArmV82 = isAtLeastArmV82,
            isAtLeastArmV84 = isAtLeastArmV84,
            isEmulated = isEmulated,
            isArm64V8a = isArm64V8a,
            isArmV7a = isArmV7a,
            cpuCount = cpuCount,
            cpuModel = cpuModel,
        )
    }

    /** 读取 /proc/cpuinfo 中的 Features 行 */
    private fun readCpuFeatures(): String {
        return try {
            File("/proc/cpuinfo").readText()
                .substringAfter("Features")
                .substringAfter(":")
                .substringBefore("\n")
                .trim()
                .lowercase()
        } catch (_: Exception) { "" }
    }

    /** 读取 CPU 型号（如 Cortex-X4, Kryo 920） */
    private fun readCpuModel(): String {
        return try {
            File("/proc/cpuinfo").readText()
                .substringAfter("Processor")
                .substringAfter(":")
                .substringBefore("\n")
                .trim()
                .ifEmpty {
                    File("/proc/cpuinfo").readText()
                        .substringAfter("model name")
                        .substringAfter(":")
                        .substringBefore("\n")
                        .trim()
                }
                .ifEmpty {
                    Build.HARDWARE
                }
        } catch (_: Exception) { Build.HARDWARE }
    }

    /** 读取 CPU 核心数 */
    private fun readCpuCount(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (_: Exception) { 4 }
    }
}
