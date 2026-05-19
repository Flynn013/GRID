package ai.grid.agent

import ai.grid.bridge.GodotFFITools
import androidx.lifecycle.ViewModel
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

enum class Role { USER, ASSISTANT, TOOL }

data class Message(
    val role: Role,
    val content: String,
    val isCode: Boolean = false,
    val toolCallId: String? = null,
)

private const val SYSTEM_PROMPT = """
You are CLU, the embedded AI agent of GRID — a sovereign mobile game engine IDE.
You have direct FFI access to the live Godot 4 SceneTree via tool calls.
Always call godot_get_scene_tree before making structural changes.
Keep responses concise and technical. Prefer tool actions over explanations.
When asked to spawn objects, build scenes, or manipulate the 3D world, use the tools immediately.
""".trimIndent()

class CLUAgent : ViewModel() {

    private val _messages   = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _activeTask = MutableStateFlow<String?>(null)
    val activeTask: StateFlow<String?> = _activeTask.asStateFlow()

    private var anthropicKey: String = ""
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    fun setApiKey(key: String) { anthropicKey = key }

    suspend fun send(userText: String) {
        _messages.update { it + Message(Role.USER, userText) }
        _isThinking.value = true
        try {
            runAgentLoop()
        } finally {
            _isThinking.value = false
            _activeTask.value = null
        }
    }

    private suspend fun runAgentLoop(maxRounds: Int = 6) {
        repeat(maxRounds) {
            val response = callAnthropic() ?: return
            val stopReason = response["stop_reason"]?.jsonPrimitive?.content
            val contentBlocks = response["content"]?.jsonArray ?: return

            var hasToolUse = false
            for (block in contentBlocks) {
                when (block.jsonObject["type"]?.jsonPrimitive?.content) {
                    "text" -> {
                        val text = block.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
                        if (text.isNotBlank()) {
                            _messages.update { it + Message(Role.ASSISTANT, text) }
                        }
                    }
                    "tool_use" -> {
                        hasToolUse = true
                        val toolName = block.jsonObject["name"]?.jsonPrimitive?.content ?: continue
                        val toolId   = block.jsonObject["id"]?.jsonPrimitive?.content   ?: continue
                        val input    = block.jsonObject["input"]?.jsonObject ?: buildJsonObject {}

                        _activeTask.value = "→ $toolName"
                        val result = GodotFFITools.dispatch(toolName, input)
                        _activeTask.value = null

                        _messages.update { it + Message(Role.TOOL, "$toolName → $result", toolCallId = toolId) }
                    }
                }
            }

            if (!hasToolUse || stopReason == "end_turn") return
        }
    }

    private suspend fun callAnthropic(): JsonObject? {
        if (anthropicKey.isBlank()) {
            _messages.update {
                it + Message(Role.ASSISTANT, "No API key. Go to VENDOR → set Anthropic key.")
            }
            return null
        }

        // Build message history in Anthropic format (user/assistant alternation).
        // Tool results are collapsed into the preceding assistant turn.
        val history = buildJsonArray {
            for (msg in _messages.value) {
                when (msg.role) {
                    Role.USER -> add(buildJsonObject {
                        put("role", "user")
                        put("content", msg.content)
                    })
                    Role.ASSISTANT -> add(buildJsonObject {
                        put("role", "assistant")
                        put("content", msg.content)
                    })
                    Role.TOOL -> { /* embedded in assistant content blocks via Anthropic tool-result format */ }
                }
            }
        }

        val body = buildJsonObject {
            put("model",      "claude-opus-4-7")
            put("max_tokens", 4096)
            put("system",     SYSTEM_PROMPT)
            put("messages",   history)
            put("tools",      Json.parseToJsonElement(GodotFFITools.toolSchemas.toString()))
        }

        return try {
            val resp = http.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key",          anthropicKey)
                header("anthropic-version",  "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            json.parseToJsonElement(resp.bodyAsText()).jsonObject
        } catch (e: Exception) {
            _messages.update { it + Message(Role.ASSISTANT, "API error: ${e.message}") }
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        http.close()
    }
}
