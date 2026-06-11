package com.template.jh.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.template.jh.model.McpServer
import com.template.jh.model.Rule
import com.template.jh.model.SkillItem
import com.template.jh.model.chat.BackendType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class RecentEntry(
    val path: String,
    val name: String,
    val timestamp: Long,
)

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
        val SHOW_TOOL_CALLS = booleanPreferencesKey("show_tool_calls")
        val DEEP_THINK_ENABLED = booleanPreferencesKey("deep_think_enabled")
        val THINKING_ROUNDS = intPreferencesKey("thinking_rounds")
        val LAST_OPENED_FOLDER_URI = stringPreferencesKey("last_opened_folder_uri")
        val OPENED_FILE_TABS = stringPreferencesKey("opened_file_tabs")
        val RECENT_FILES = stringPreferencesKey("recent_files_json")
        val RECENT_FOLDERS = stringPreferencesKey("recent_folders_json")
        // 模型自动加载
        val LAST_MODEL_PATH = stringPreferencesKey("last_model_path")
        val AUTO_LOAD_LAST_MODEL = booleanPreferencesKey("auto_load_last_model")
        // 云端模型
        val CLOUD_MODEL_ENABLED = booleanPreferencesKey("cloud_model_enabled")
        val CLOUD_PROFILES_JSON = stringPreferencesKey("cloud_profiles_json")
        val ACTIVE_CLOUD_PROFILE_ID = stringPreferencesKey("active_cloud_profile_id")
        // 本地推理后端
        val BACKEND_TYPE = stringPreferencesKey("backend_type")
        val NPU_LIBRARY_DIR = stringPreferencesKey("npu_library_dir")
        // MTP (Multi-Turn Prediction / Speculative Decoding)
        val ENABLE_SPECULATIVE_DECODING = booleanPreferencesKey("enable_speculative_decoding")
        // 模型参数持久化（topK/topP/temperature/seed/contextWindow）
        val MODEL_PARAMS_JSON = stringPreferencesKey("model_params_json")
        val PERMISSION_GUIDE_SHOWN = booleanPreferencesKey("permission_guide_shown")
    }

    val themeMode: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.THEME_MODE] ?: "system" }

    val language: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.LANGUAGE] ?: "system" }

    val modelName: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.MODEL_NAME] ?: "" }

    // 云端模型配置（多 profile 支持）
    val cloudModelEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[PreferencesKeys.CLOUD_MODEL_ENABLED] ?: false }

    val cloudModelProfiles: Flow<List<com.template.jh.model.chat.CloudModelProfile>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PreferencesKeys.CLOUD_PROFILES_JSON] ?: return@map emptyList()
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    com.template.jh.model.chat.CloudModelProfile(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        apiEndpoint = obj.optString("apiEndpoint", "https://api.openai.com/v1"),
                        apiKey = obj.optString("apiKey", ""),
                        modelName = obj.optString("modelName", "gpt-4o"),
                        contextWindow = obj.optInt("contextWindow", 128000),
                        maxTokens = obj.optInt("maxTokens", 8192),
                    )
                }
            } catch (_: Exception) { emptyList() }
        }

    val activeCloudProfileId: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.ACTIVE_CLOUD_PROFILE_ID] ?: "" }

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

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = language }
    }

    suspend fun setModelName(name: String) {
        context.dataStore.edit { it[PreferencesKeys.MODEL_NAME] = name }
    }

    suspend fun setCloudModelEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.CLOUD_MODEL_ENABLED] = enabled }
    }

    suspend fun setCloudModelProfiles(profiles: List<com.template.jh.model.chat.CloudModelProfile>) {
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            profiles.forEach { p ->
                val obj = org.json.JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("apiEndpoint", p.apiEndpoint)
                obj.put("apiKey", p.apiKey)
                obj.put("modelName", p.modelName)
                obj.put("contextWindow", p.contextWindow)
                obj.put("maxTokens", p.maxTokens)
                arr.put(obj)
            }
            prefs[PreferencesKeys.CLOUD_PROFILES_JSON] = arr.toString()
        }
    }

    suspend fun setActiveCloudProfileId(id: String) {
        context.dataStore.edit { it[PreferencesKeys.ACTIVE_CLOUD_PROFILE_ID] = id }
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

    val showToolCalls: Flow<Boolean> = context.dataStore.data
        .map { it[PreferencesKeys.SHOW_TOOL_CALLS] ?: false }

    val deepThinkEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[PreferencesKeys.DEEP_THINK_ENABLED] ?: true }

    val thinkingRounds: Flow<Int> = context.dataStore.data
        .map { (it[PreferencesKeys.THINKING_ROUNDS] ?: 2).coerceIn(1, 10) }

    suspend fun setShowToolCalls(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_TOOL_CALLS] = enabled }
    }

    suspend fun setDeepThinkEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DEEP_THINK_ENABLED] = enabled }
    }

    suspend fun setThinkingRounds(rounds: Int) {
        context.dataStore.edit { it[PreferencesKeys.THINKING_ROUNDS] = rounds.coerceIn(1, 10) }
    }

    val lastOpenedFolderUri: Flow<String?> = context.dataStore.data
        .map { it[PreferencesKeys.LAST_OPENED_FOLDER_URI] }

    suspend fun setLastOpenedFolderUri(uri: String) {
        context.dataStore.edit { it[PreferencesKeys.LAST_OPENED_FOLDER_URI] = uri }
    }

    val openedFileTabs: Flow<List<String>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PreferencesKeys.OPENED_FILE_TABS] ?: return@map emptyList()
            try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { arr.optString(it) }
            } catch (_: Exception) { emptyList() }
        }

    suspend fun setOpenedFileTabs(paths: List<String>) {
        context.dataStore.edit {
            it[PreferencesKeys.OPENED_FILE_TABS] = org.json.JSONArray(paths).toString()
        }
    }

    // 最近打开文件（最多 10 条）
    val recentFiles: Flow<List<RecentEntry>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PreferencesKeys.RECENT_FILES] ?: return@map emptyList()
            parseRecentEntries(json)
        }

    // 最近打开目录（最多 10 条）
    val recentFolders: Flow<List<RecentEntry>> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PreferencesKeys.RECENT_FOLDERS] ?: return@map emptyList()
            parseRecentEntries(json)
        }

    suspend fun addRecentFile(path: String, name: String) {
        context.dataStore.edit { prefs ->
            val list = prefs[PreferencesKeys.RECENT_FILES]?.let { parseRecentEntries(it) } ?: emptyList()
            prefs[PreferencesKeys.RECENT_FILES] = recentEntriesToJson(insertRecent(list, path, name))
        }
    }

    suspend fun addRecentFolder(path: String, name: String) {
        context.dataStore.edit { prefs ->
            val list = prefs[PreferencesKeys.RECENT_FOLDERS]?.let { parseRecentEntries(it) } ?: emptyList()
            prefs[PreferencesKeys.RECENT_FOLDERS] = recentEntriesToJson(insertRecent(list, path, name))
        }
    }

    private fun insertRecent(list: List<RecentEntry>, path: String, name: String): List<RecentEntry> {
        val deduped = list.filter { it.path != path }
        val entry = RecentEntry(path, name, System.currentTimeMillis())
        return (listOf(entry) + deduped).take(10)
    }

    private fun parseRecentEntries(json: String): List<RecentEntry> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RecentEntry(
                    path = obj.optString("path", ""),
                    name = obj.optString("name", ""),
                    timestamp = obj.optLong("timestamp", 0L),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun recentEntriesToJson(entries: List<RecentEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject()
            obj.put("path", e.path)
            obj.put("name", e.name)
            obj.put("timestamp", e.timestamp)
            arr.put(obj)
        }
        return arr.toString()
    }

    // 上次加载的模型路径
    val lastModelPath: Flow<String?> = context.dataStore.data
        .map { it[PreferencesKeys.LAST_MODEL_PATH] }

    suspend fun setLastModelPath(path: String) {
        context.dataStore.edit { it[PreferencesKeys.LAST_MODEL_PATH] = path }
    }

    // 是否自动加载上次模型
    val autoLoadLastModel: Flow<Boolean> = context.dataStore.data
        .map { it[PreferencesKeys.AUTO_LOAD_LAST_MODEL] ?: true }

    suspend fun setAutoLoadLastModel(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_LOAD_LAST_MODEL] = enabled }
    }

    // 本地推理后端类型
    val backendType: Flow<BackendType> = context.dataStore.data
        .map { it[PreferencesKeys.BACKEND_TYPE]?.let { BackendType.fromName(it) } ?: BackendType.CPU }

    suspend fun setBackendType(type: BackendType) {
        context.dataStore.edit { it[PreferencesKeys.BACKEND_TYPE] = type.name }
    }

    // NPU 库目录路径
    val npuLibraryDir: Flow<String> = context.dataStore.data
        .map { it[PreferencesKeys.NPU_LIBRARY_DIR] ?: "" }

    suspend fun setNpuLibraryDir(dir: String) {
        context.dataStore.edit { it[PreferencesKeys.NPU_LIBRARY_DIR] = dir }
    }

    // MTP (Multi-Turn Prediction / Speculative Decoding)
    val enableSpeculativeDecoding: Flow<Boolean> = context.dataStore.data
        .map { it[PreferencesKeys.ENABLE_SPECULATIVE_DECODING] ?: false }

    suspend fun setEnableSpeculativeDecoding(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ENABLE_SPECULATIVE_DECODING] = enabled }
    }

    // 模型参数完整持久化（避免重启重置为默认值）
    val modelParams: Flow<com.template.jh.model.chat.ModelParams> = context.dataStore.data
        .map { prefs ->
            val json = prefs[PreferencesKeys.MODEL_PARAMS_JSON]
            if (json != null) {
                try {
                    val obj = JSONObject(json)
                    com.template.jh.model.chat.ModelParams(
                        topK = obj.optInt("topK", 10),
                        topP = obj.optDouble("topP", 0.95),
                        temperature = obj.optDouble("temperature", 0.8),
                        seed = obj.optInt("seed", 0),
                        contextWindowTokens = obj.optInt("contextWindowTokens", 32768),
                        enableSpeculativeDecoding = obj.optBoolean("enableSpeculativeDecoding", false),
                        backendType = BackendType.fromName(obj.optString("backendType", "CPU")),
                    )
                } catch (_: Exception) { com.template.jh.model.chat.ModelParams() }
            } else com.template.jh.model.chat.ModelParams()
        }

    suspend fun setModelParams(params: com.template.jh.model.chat.ModelParams) {
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

    private fun rulesToJson(rules: List<Rule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            val obj = org.json.JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("content", r.content)
            arr.put(obj)
        }
        return arr.toString()
    }

    // 权限引导是否已显示
    suspend fun isPermissionGuideShown(): Boolean {
        return context.dataStore.data.map { it[PreferencesKeys.PERMISSION_GUIDE_SHOWN] ?: false }.first()
    }

    suspend fun setPermissionGuideShown(shown: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PERMISSION_GUIDE_SHOWN] = shown }
    }
}
