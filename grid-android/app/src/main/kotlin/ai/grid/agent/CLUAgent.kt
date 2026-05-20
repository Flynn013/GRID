package ai.grid.agent

import ai.grid.bridge.GodotFFITools
import ai.grid.data.Project
import ai.grid.data.ProjectRepository
import ai.grid.data.SettingsData
import ai.grid.data.SettingsRepository
import ai.grid.data.LLMProvider
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

enum class Role { USER, ASSISTANT, TOOL }

data class Message(
    val role: Role,
    val content: String,
    val isCode: Boolean = false,
    val toolCallId: String? = null,
)

private sealed class ApiEntry {
    data class UserText(val text: String) : ApiEntry()
    class  AssistantBlocks(val blocks: List<Block>) : ApiEntry()
    data class ToolResult(val id: String, val name: String, val result: String) : ApiEntry()
}

private sealed class Block {
    data class Text(val text: String) : Block()
    class  ToolUse(val id: String, val name: String, val input: JsonObject) : Block()
}

private const val BASE_SYSTEM = """
You are CLU, the embedded AI agent of GRID — a sovereign mobile game engine IDE.
You have direct FFI access to the live Godot 4 SceneTree via tool calls.
Always call godot_get_scene_tree before making structural changes.
Call project_get_gdd at the start of each session to understand project scope.
Keep responses concise and technical. Prefer tool actions over explanations.
At the end of a productive session, call project_append_sprint to record what was built.
""".trimIndent()

class CLUAgent(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository.get(app)
    private val projectRepo  = ProjectRepository.get(app)
    private var config: SettingsData = SettingsData()

    private val _messages   = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _activeTask = MutableStateFlow<String?>(null)
    val activeTask: StateFlow<String?> = _activeTask.asStateFlow()

    private val apiHistory = mutableListOf<ApiEntry>()
    private var systemPrompt = BASE_SYSTEM

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    init {
        viewModelScope.launch { settingsRepo.flow.collect { config = it } }
    }

    fun clearHistory() {
        apiHistory.clear()
        _messages.value = emptyList()
    }

    suspend fun send(userText: String) {
        // Rebuild system prompt with live project + GDD context
        systemPrompt = buildSystemPrompt()

        apiHistory.add(ApiEntry.UserText(userText))
        _messages.update { it + Message(Role.USER, userText) }
        _isThinking.value = true
        try {
            when (runCatching { LLMProvider.valueOf(config.activeProvider) }.getOrDefault(LLMProvider.ANTHROPIC)) {
                LLMProvider.ANTHROPIC -> runAnthropicLoop()
                LLMProvider.GEMINI    -> runGeminiLoop()
                LLMProvider.LITERT    -> emit("LiteRT local inference — coming in Sprint 2.")
            }
        } finally {
            _isThinking.value = false
            _activeTask.value = null
        }
    }

    private suspend fun buildSystemPrompt(): String {
        val project: Project? = try {
            projectRepo.activeProjectFlow.first()
        } catch (_: Exception) { null }

        val gdd: String? = project?.gddPath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching { File(path).readText().take(3500) }.getOrNull()
            }
        }

        return buildString {
            append(BASE_SYSTEM)
            if (project != null) {
                appendLine("\n\n## Active Project: ${project.name} (${project.type})")
                if (gdd != null) {
                    appendLine("\n## GDD_MASTER.md (first 3500 chars):")
                    appendLine("```")
                    append(gdd)
                    appendLine("\n```")
                }
            }
        }
    }

    // ── Anthropic ────────────────────────────────────────────────────

    private suspend fun runAnthropicLoop(maxRounds: Int = 8) {
        if (config.anthropicKey.isBlank()) {
            emit("No Anthropic key. Go to VENDOR → set Anthropic key.")
            return
        }
        repeat(maxRounds) {
            val resp = callAnthropic() ?: return
            val stopReason      = resp["stop_reason"]?.jsonPrimitive?.content
            val rawBlocks       = resp["content"]?.jsonArray ?: return
            val assistantBlocks = mutableListOf<Block>()
            val pendingTools    = mutableListOf<Triple<String, String, JsonObject>>()

            for (raw in rawBlocks) {
                when (raw.jsonObject["type"]?.jsonPrimitive?.content) {
                    "text" -> {
                        val text = raw.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
                        if (text.isNotBlank()) {
                            assistantBlocks.add(Block.Text(text))
                            _messages.update { it + Message(Role.ASSISTANT, text) }
                        }
                    }
                    "tool_use" -> {
                        val id    = raw.jsonObject["id"]?.jsonPrimitive?.content   ?: continue
                        val name  = raw.jsonObject["name"]?.jsonPrimitive?.content  ?: continue
                        val input = raw.jsonObject["input"]?.jsonObject ?: buildJsonObject {}
                        assistantBlocks.add(Block.ToolUse(id, name, input))
                        pendingTools.add(Triple(id, name, input))
                    }
                }
            }

            if (assistantBlocks.isNotEmpty()) apiHistory.add(ApiEntry.AssistantBlocks(assistantBlocks))

            for ((id, name, input) in pendingTools) {
                _activeTask.value = "→ $name"
                val result = GodotFFITools.dispatch(name, input)
                _activeTask.value = null
                apiHistory.add(ApiEntry.ToolResult(id, name, result))
                _messages.update { it + Message(Role.TOOL, "$name → $result", toolCallId = id) }
            }

            if (pendingTools.isEmpty() || stopReason == "end_turn") return
        }
    }

    private suspend fun callAnthropic(): JsonObject? {
        val messages = buildJsonArray {
            val pending = mutableListOf<ApiEntry.ToolResult>()

            fun flush() {
                if (pending.isEmpty()) return
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        pending.forEach { tr ->
                            add(buildJsonObject {
                                put("type",        "tool_result")
                                put("tool_use_id", tr.id)
                                put("content",     tr.result)
                            })
                        }
                    })
                })
                pending.clear()
            }

            for (entry in apiHistory) {
                when (entry) {
                    is ApiEntry.UserText -> {
                        flush()
                        add(buildJsonObject { put("role", "user"); put("content", entry.text) })
                    }
                    is ApiEntry.AssistantBlocks -> {
                        flush()
                        add(buildJsonObject {
                            put("role", "assistant")
                            put("content", buildJsonArray {
                                entry.blocks.forEach { b ->
                                    add(when (b) {
                                        is Block.Text    -> buildJsonObject { put("type", "text"); put("text", b.text) }
                                        is Block.ToolUse -> buildJsonObject {
                                            put("type",  "tool_use")
                                            put("id",    b.id)
                                            put("name",  b.name)
                                            put("input", b.input)
                                        }
                                    })
                                }
                            })
                        })
                    }
                    is ApiEntry.ToolResult -> pending.add(entry)
                }
            }
            flush()
        }

        val body = buildJsonObject {
            put("model",      "claude-opus-4-7")
            put("max_tokens", 4096)
            put("system",     systemPrompt)
            put("messages",   messages)
            put("tools",      GodotFFITools.anthropicToolSchemas)
        }

        return try {
            val resp = http.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key",         config.anthropicKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            json.parseToJsonElement(resp.bodyAsText()).jsonObject
        } catch (e: Exception) {
            emit("Anthropic error: ${e.message}")
            null
        }
    }

    // ── Gemini ─────────────────────────────────────────────────────────────

    private suspend fun runGeminiLoop(maxRounds: Int = 8) {
        if (config.geminiKey.isBlank()) {
            emit("No Gemini key. Go to VENDOR → set Gemini key.")
            return
        }
        repeat(maxRounds) {
            val resp = callGemini() ?: return
            val candidate    = resp["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return
            val finishReason = candidate["finishReason"]?.jsonPrimitive?.content
            val parts        = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: return

            val assistantBlocks = mutableListOf<Block>()
            val pendingTools    = mutableListOf<Triple<String, String, JsonObject>>()

            for (part in parts) {
                val text = part.jsonObject["text"]?.jsonPrimitive?.content
                val fc   = part.jsonObject["functionCall"]?.jsonObject
                when {
                    text != null && text.isNotBlank() -> {
                        assistantBlocks.add(Block.Text(text))
                        _messages.update { it + Message(Role.ASSISTANT, text) }
                    }
                    fc != null -> {
                        val name = fc["name"]?.jsonPrimitive?.content ?: continue
                        val args = fc["args"]?.jsonObject ?: buildJsonObject {}
                        val id   = "g-${name}-${System.currentTimeMillis()}"
                        assistantBlocks.add(Block.ToolUse(id, name, args))
                        pendingTools.add(Triple(id, name, args))
                    }
                }
            }

            if (assistantBlocks.isNotEmpty()) apiHistory.add(ApiEntry.AssistantBlocks(assistantBlocks))

            for ((id, name, input) in pendingTools) {
                _activeTask.value = "→ $name"
                val result = GodotFFITools.dispatch(name, input)
                _activeTask.value = null
                apiHistory.add(ApiEntry.ToolResult(id, name, result))
                _messages.update { it + Message(Role.TOOL, "$name → $result", toolCallId = id) }
            }

            if (pendingTools.isEmpty() || finishReason == "STOP") return
        }
    }

    private suspend fun callGemini(): JsonObject? {
        val contents = buildJsonArray {
            for (entry in apiHistory) {
                when (entry) {
                    is ApiEntry.UserText -> add(buildJsonObject {
                        put("role",  "user")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", entry.text) }) })
                    })
                    is ApiEntry.AssistantBlocks -> add(buildJsonObject {
                        put("role", "model")
                        put("parts", buildJsonArray {
                            entry.blocks.forEach { b ->
                                add(when (b) {
                                    is Block.Text    -> buildJsonObject { put("text", b.text) }
                                    is Block.ToolUse -> buildJsonObject {
                                        put("functionCall", buildJsonObject {
                                            put("name", b.name); put("args", b.input)
                                        })
                                    }
                                })
                            }
                        })
                    })
                    is ApiEntry.ToolResult -> add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("functionResponse", buildJsonObject {
                                    put("name", entry.name)
                                    put("response", buildJsonObject { put("result", entry.result) })
                                })
                            })
                        })
                    })
                }
            }
        }

        val body = buildJsonObject {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", systemPrompt) }) })
            })
            put("tools", buildJsonArray {
                add(buildJsonObject { put("function_declarations", GodotFFITools.geminiToolSchemas) })
            })
            put("contents", contents)
        }

        return try {
            val resp = http.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent") {
                parameter("key", config.geminiKey)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            json.parseToJsonElement(resp.bodyAsText()).jsonObject
        } catch (e: Exception) {
            emit("Gemini error: ${e.message}")
            null
        }
    }

    private fun emit(text: String) {
        _messages.update { it + Message(Role.ASSISTANT, text) }
    }

    override fun onCleared() {
        super.onCleared()
        http.close()
    }
}
