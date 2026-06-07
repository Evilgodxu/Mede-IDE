package com.template.jh.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.template.jh.data.model.McpServer
import com.template.jh.data.model.NotificationSettings
import com.template.jh.data.model.Rule
import com.template.jh.data.model.SkillItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

// 用户偏好设置仓库
class UserPreferencesRepository(private val context: Context) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val USER_NAME = stringPreferencesKey("user_name")
        val RULES_JSON = stringPreferencesKey("rules_json")
        val SKILLS_JSON = stringPreferencesKey("skills_json")
        val MCP_SERVERS_JSON = stringPreferencesKey("mcp_servers_json")
        // 通知设置
        val NOTIFY_TASK_COMPLETED_SOUND = booleanPreferencesKey("notify_task_completed_sound")
        val NOTIFY_TASK_COMPLETED_POPUP = booleanPreferencesKey("notify_task_completed_popup")
        val NOTIFY_TASK_FAILED_SOUND = booleanPreferencesKey("notify_task_failed_sound")
        val NOTIFY_TASK_FAILED_POPUP = booleanPreferencesKey("notify_task_failed_popup")
        val NOTIFY_WAITING_AUTH_SOUND = booleanPreferencesKey("notify_waiting_auth_sound")
        val NOTIFY_WAITING_AUTH_POPUP = booleanPreferencesKey("notify_waiting_auth_popup")
        val DELETE_CARD_ENABLED = booleanPreferencesKey("delete_card_enabled")
    }

    val themeMode: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.THEME_MODE] ?: "system" }

    val language: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.LANGUAGE] ?: "system" }

    val modelName: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.MODEL_NAME] ?: "" }

    val userName: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.USER_NAME] ?: "" }

    val rules: Flow<List<Rule>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PreferencesKeys.RULES_JSON] ?: return@map emptyList<Rule>()
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    Rule(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        content = obj.optString("content"),
                        type = com.template.jh.data.model.RuleType.entries
                            .find { it.name == obj.optString("type") }
                            ?: com.template.jh.data.model.RuleType.Global,
                    )
                }
            } catch (_: Exception) { emptyList() }
        }

    val skills: Flow<List<SkillItem>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PreferencesKeys.SKILLS_JSON]
            if (json != null) {
                try {
                    val arr = JSONArray(json)
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        SkillItem(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            description = obj.optString("description"),
                            prompt = obj.optString("prompt"),
                            enabled = obj.optBoolean("enabled", true),
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList() // 无预置技能，由用户自行导入
            }
        }

    val mcpServers: Flow<List<McpServer>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PreferencesKeys.MCP_SERVERS_JSON] ?: return@map emptyList<McpServer>()
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    McpServer(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        command = obj.optString("command"),
                        args = obj.optString("args"),
                        enabled = obj.optBoolean("enabled", false),
                    )
                }
            } catch (_: Exception) { emptyList() }
        }

    val notificationSettings: Flow<NotificationSettings> = context.dataStore.data
        .map { prefs ->
            NotificationSettings(
                taskCompletedSound = prefs[PreferencesKeys.NOTIFY_TASK_COMPLETED_SOUND] ?: true,
                taskCompletedPopup = prefs[PreferencesKeys.NOTIFY_TASK_COMPLETED_POPUP] ?: true,
                taskFailedSound = prefs[PreferencesKeys.NOTIFY_TASK_FAILED_SOUND] ?: true,
                taskFailedPopup = prefs[PreferencesKeys.NOTIFY_TASK_FAILED_POPUP] ?: true,
                waitingUserActionSound = prefs[PreferencesKeys.NOTIFY_WAITING_AUTH_SOUND] ?: true,
                waitingUserActionPopup = prefs[PreferencesKeys.NOTIFY_WAITING_AUTH_POPUP] ?: true,
                deleteCardEnabled = prefs[PreferencesKeys.DELETE_CARD_ENABLED] ?: false,
            )
        }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = language }
    }

    suspend fun setModelName(name: String) {
        context.dataStore.edit { it[PreferencesKeys.MODEL_NAME] = name }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[PreferencesKeys.USER_NAME] = name }
    }

    suspend fun setRules(rules: List<Rule>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.RULES_JSON] = rulesToJson(rules)
        }
    }

    suspend fun setSkills(skills: List<SkillItem>) {
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            skills.forEach { s ->
                val obj = org.json.JSONObject()
                obj.put("id", s.id)
                obj.put("name", s.name)
                obj.put("description", s.description)
                obj.put("prompt", s.prompt)
                obj.put("enabled", s.enabled)
                arr.put(obj)
            }
            prefs[PreferencesKeys.SKILLS_JSON] = arr.toString()
        }
    }

    suspend fun setMcpServers(servers: List<McpServer>) {
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            servers.forEach { s ->
                val obj = org.json.JSONObject()
                obj.put("id", s.id)
                obj.put("name", s.name)
                obj.put("command", s.command)
                obj.put("args", s.args)
                obj.put("enabled", s.enabled)
                arr.put(obj)
            }
            prefs[PreferencesKeys.MCP_SERVERS_JSON] = arr.toString()
        }
    }

    suspend fun setNotificationSettings(settings: NotificationSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.NOTIFY_TASK_COMPLETED_SOUND] = settings.taskCompletedSound
            prefs[PreferencesKeys.NOTIFY_TASK_COMPLETED_POPUP] = settings.taskCompletedPopup
            prefs[PreferencesKeys.NOTIFY_TASK_FAILED_SOUND] = settings.taskFailedSound
            prefs[PreferencesKeys.NOTIFY_TASK_FAILED_POPUP] = settings.taskFailedPopup
            prefs[PreferencesKeys.NOTIFY_WAITING_AUTH_SOUND] = settings.waitingUserActionSound
            prefs[PreferencesKeys.NOTIFY_WAITING_AUTH_POPUP] = settings.waitingUserActionPopup
            prefs[PreferencesKeys.DELETE_CARD_ENABLED] = settings.deleteCardEnabled
        }
    }

    private fun rulesToJson(rules: List<Rule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            val obj = org.json.JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("content", r.content)
            obj.put("type", r.type.name)
            arr.put(obj)
        }
        return arr.toString()
    }
}
