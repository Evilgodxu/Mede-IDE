package com.medeide.jh.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medeide.jh.core.data.logging.FileLogger
import com.medeide.jh.model.DEFAULT_ROLE_ID
import com.medeide.jh.model.Rule
import com.medeide.jh.model.chat.AiBehaviorSettings
import com.medeide.jh.model.chat.BackendType
import com.medeide.jh.model.chat.ChatMode
import com.medeide.jh.model.chat.CloudModelProfile
import com.medeide.jh.model.chat.ModelParams
import com.medeide.jh.model.chat.UserProfile
import com.medeide.jh.screens.home.recent.RecentFileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val IDE_MODE = booleanPreferencesKey("ide_mode")
        val CLOUD_MODEL_ENABLED = booleanPreferencesKey("cloud_model_enabled")
        val CLOUD_PROFILES_JSON = stringPreferencesKey("cloud_profiles_json")
        val ACTIVE_CLOUD_PROFILE_ID = stringPreferencesKey("active_cloud_profile_id")
        val CHAT_MODE = stringPreferencesKey("chat_mode")
        val TONE_MODE = stringPreferencesKey("tone_mode")
        val LEARNING_MODE = booleanPreferencesKey("learning_mode")
        val RULES_JSON = stringPreferencesKey("rules_json")
        val ACTIVE_ROLE_ID = stringPreferencesKey("active_role_id")
        val MODEL_PARAMS_JSON = stringPreferencesKey("model_params_json")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_AVATAR_URI = stringPreferencesKey("user_avatar_uri")
        val AGENT_NAME = stringPreferencesKey("agent_name")
        val AGENT_AVATAR_URI = stringPreferencesKey("agent_avatar_uri")
        val RECENT_FILES_JSON = stringPreferencesKey("recent_files_json")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.THEME_MODE] ?: "system" }
    val language: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.LANGUAGE] ?: "system" }
    val ideMode: Flow<Boolean?> = context.dataStore.data.map { it[PreferencesKeys.IDE_MODE] }

    // ── 云端模型配置 ──

    val cloudModelEnabled: Flow<Boolean> = context.dataStore.data.map { it[PreferencesKeys.CLOUD_MODEL_ENABLED] ?: false }

    val cloudModelProfiles: Flow<List<CloudModelProfile>> = context.dataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.CLOUD_PROFILES_JSON] ?: return@map emptyList()
        try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CloudModelProfile(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    apiEndpoint = obj.optString("apiEndpoint", "https://api.openai.com/v1"),
                    apiKey = obj.optString("apiKey", ""),
                    modelName = obj.optString("modelName", "gpt-4o"),
                    contextWindow = obj.optInt("contextWindow", 128000),
                    maxTokens = obj.optInt("maxTokens", 16000),
                    maxToolRounds = obj.optInt("maxToolRounds", 200),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    val activeCloudProfileId: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.ACTIVE_CLOUD_PROFILE_ID] ?: "" }

    // ── AI 行为设置 ──

    val chatMode: Flow<ChatMode> = context.dataStore.data.map { prefs ->
        ChatMode.fromKey(prefs[PreferencesKeys.CHAT_MODE] ?: "agent")
    }

    val aiBehavior: Flow<AiBehaviorSettings> = context.dataStore.data.map { prefs ->
        AiBehaviorSettings(
            learningModeEnabled = prefs[PreferencesKeys.LEARNING_MODE] ?: false,
        )
    }

    // ── 角色定义 ──

    val rules: Flow<List<Rule>> = context.dataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.RULES_JSON] ?: return@map listOf(Rule.defaultRole())
        try {
            val arr = JSONArray(json)
            val custom = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Rule(id = obj.optString("id"), name = obj.optString("name"),
                    content = obj.optString("content"), isDefault = obj.optBoolean("isDefault", false))
            }
            if (custom.any { it.isDefault }) custom else listOf(Rule.defaultRole()) + custom
        } catch (_: Exception) { listOf(Rule.defaultRole()) }
    }

    val activeRoleId: Flow<String> = context.dataStore.data.map { it[PreferencesKeys.ACTIVE_ROLE_ID] ?: DEFAULT_ROLE_ID }

    // ── 本地模型参数 ──

    val modelParams: Flow<ModelParams> = context.dataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.MODEL_PARAMS_JSON] ?: return@map ModelParams()
        try {
            val obj = JSONObject(json)
            ModelParams(
                topK = obj.optInt("topK", 10),
                topP = obj.optDouble("topP", 0.7),
                temperature = obj.optDouble("temperature", 0.1),
                seed = obj.optInt("seed", 0),
                contextWindowTokens = obj.optInt("contextWindowTokens", 4096),
                enableSpeculativeDecoding = obj.optBoolean("enableSpeculativeDecoding", false),
                backendType = BackendType.fromName(obj.optString("backendType", "GPU")),
            )
        } catch (_: Exception) { ModelParams() }
    }

    suspend fun setModelParams(params: ModelParams) {
        FileLogger.d("Prefs", "setModelParams ctx=${params.contextWindowTokens} backend=${params.backendType}")
        context.dataStore.edit { prefs ->
            val obj = JSONObject()
            obj.put("topK", params.topK)
            obj.put("topP", params.topP)
            obj.put("temperature", params.temperature)
            obj.put("seed", params.seed)
            obj.put("contextWindowTokens", params.contextWindowTokens)
            obj.put("enableSpeculativeDecoding", params.enableSpeculativeDecoding)
            obj.put("backendType", params.backendType.name)
            prefs[PreferencesKeys.MODEL_PARAMS_JSON] = obj.toString()
        }
    }

    // ── 用户/Agent 资料 ──

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            userName = prefs[PreferencesKeys.USER_NAME] ?: "",
            userAvatarUri = prefs[PreferencesKeys.USER_AVATAR_URI] ?: "",
            agentName = prefs[PreferencesKeys.AGENT_NAME] ?: "AI",
            agentAvatarUri = prefs[PreferencesKeys.AGENT_AVATAR_URI] ?: "",
        )
    }

    suspend fun setUserProfile(profile: UserProfile) {
        FileLogger.d("Prefs", "setUserProfile user=${profile.userName} agent=${profile.agentName}")
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.USER_NAME] = profile.userName
            prefs[PreferencesKeys.USER_AVATAR_URI] = profile.userAvatarUri
            prefs[PreferencesKeys.AGENT_NAME] = profile.agentName
            prefs[PreferencesKeys.AGENT_AVATAR_URI] = profile.agentAvatarUri
        }
    }

    // ── 写入方法 ──

    suspend fun setCloudModelEnabled(enabled: Boolean) {
        FileLogger.d("Prefs", "setCloudModelEnabled=$enabled")
        context.dataStore.edit { it[PreferencesKeys.CLOUD_MODEL_ENABLED] = enabled }
    }

    suspend fun setCloudModelProfiles(profiles: List<CloudModelProfile>) {
        FileLogger.i("Prefs", "setCloudModelProfiles count=${profiles.size}")
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            profiles.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("apiEndpoint", p.apiEndpoint)
                    put("apiKey", p.apiKey); put("modelName", p.modelName)
                    put("contextWindow", p.contextWindow); put("maxTokens", p.maxTokens)
                    put("maxToolRounds", p.maxToolRounds)
                })
            }
            prefs[PreferencesKeys.CLOUD_PROFILES_JSON] = arr.toString()
        }
    }

    suspend fun setActiveCloudProfileId(id: String) {
        FileLogger.d("Prefs", "setActiveCloudProfileId=$id")
        context.dataStore.edit { it[PreferencesKeys.ACTIVE_CLOUD_PROFILE_ID] = id }
    }

    suspend fun setChatMode(mode: ChatMode) {
        FileLogger.d("Prefs", "setChatMode=${mode.key}")
        context.dataStore.edit { it[PreferencesKeys.CHAT_MODE] = mode.key }
    }

    suspend fun setAiBehavior(settings: AiBehaviorSettings) {
        FileLogger.d("Prefs", "setAiBehavior learningMode=${settings.learningModeEnabled}")
        context.dataStore.edit {
            it[PreferencesKeys.LEARNING_MODE] = settings.learningModeEnabled
        }
    }

    suspend fun setRules(rules: List<Rule>) {
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            rules.filter { !it.isDefault }.forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id); put("name", r.name); put("content", r.content); put("isDefault", r.isDefault)
                })
            }
            prefs[PreferencesKeys.RULES_JSON] = arr.toString()
        }
    }

    suspend fun setActiveRoleId(id: String) {
        context.dataStore.edit { it[PreferencesKeys.ACTIVE_ROLE_ID] = id }
    }

    suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode } }
    suspend fun setLanguage(language: String) { context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = language } }
    suspend fun setIdeMode(landscape: Boolean) { context.dataStore.edit { it[PreferencesKeys.IDE_MODE] = landscape } }

    // ── 最近文件 ──

    private companion object {
        const val MAX_RECENT_FILES = 20
    }

    val recentFiles: Flow<List<RecentFileEntry>> = context.dataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.RECENT_FILES_JSON] ?: return@map emptyList()
        try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RecentFileEntry(
                    path = obj.getString("path"),
                    name = obj.getString("name"),
                    isDirectory = obj.optBoolean("isDirectory", true),
                    lastOpenedTime = obj.optLong("lastOpenedTime", System.currentTimeMillis()),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addRecentFile(entry: RecentFileEntry) {
        FileLogger.d("Prefs", "addRecentFile path=${entry.path}")
        context.dataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.RECENT_FILES_JSON] ?: "[]"
            val arr = JSONArray(json)
            // 移除已存在的相同路径
            val filtered = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("path") != entry.path) {
                    filtered.put(obj)
                }
            }
            // 添加到最前面
            filtered.put(JSONObject().apply {
                put("path", entry.path)
                put("name", entry.name)
                put("isDirectory", entry.isDirectory)
                put("lastOpenedTime", entry.lastOpenedTime)
            })
            // 限制数量
            val limited = JSONArray()
            val start = maxOf(0, filtered.length() - MAX_RECENT_FILES)
            for (i in start until filtered.length()) {
                limited.put(filtered.getJSONObject(i))
            }
            prefs[PreferencesKeys.RECENT_FILES_JSON] = limited.toString()
        }
    }
}
