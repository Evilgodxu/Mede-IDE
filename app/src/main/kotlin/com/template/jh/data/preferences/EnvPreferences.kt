package com.template.jh.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * 环境变量配置管理器
 *
 * 用于存储和管理应用的环境变量配置，支持键值对形式的自定义环境变量
 * 这些变量可在终端执行时使用
 */
class EnvPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取环境变量值
     *
     * 查找顺序:
     * 1. 应用偏好设置 (如果非空)
     * 2. 系统环境变量 System.getenv (可能返回 null)
     *
     * @param key 环境变量名
     * @return 环境变量值，找不到返回 null
     */
    fun getEnv(key: String): String? {
        val name = key.trim()
        if (name.isEmpty()) return null

        val fromPrefs = prefs.getString(name, null)
        if (!fromPrefs.isNullOrEmpty()) {
            return fromPrefs
        }

        return try {
            System.getenv(name)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 设置环境变量值
     *
     * @param key 环境变量名
     * @param value 环境变量值
     */
    fun setEnv(key: String, value: String) {
        val name = key.trim()
        if (name.isEmpty()) return
        prefs.edit().putString(name, value).apply()
    }

    /**
     * 删除环境变量
     *
     * @param key 环境变量名
     */
    fun removeEnv(key: String) {
        val name = key.trim()
        if (name.isEmpty()) return
        prefs.edit().remove(name).apply()
    }

    /**
     * 获取所有存储的环境变量
     *
     * @return 环境变量键值对映射
     */
    fun getAllEnv(): Map<String, String> {
        return prefs.all.mapNotNull { (k, v) ->
            val key = k.trim()
            val value = v as? String
            if (key.isNotEmpty() && !value.isNullOrEmpty()) key to value else null
        }.toMap()
    }

    /**
     * 批量设置环境变量
     *
     * @param variables 环境变量映射
     */
    fun setAllEnv(variables: Map<String, String>) {
        val editor = prefs.edit().clear()
        variables.forEach { (k, v) ->
            val key = k.trim()
            if (key.isNotEmpty()) {
                editor.putString(key, v)
            }
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "env_preferences"

        @Volatile
        private var INSTANCE: EnvPreferences? = null

        fun getInstance(context: Context): EnvPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EnvPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
