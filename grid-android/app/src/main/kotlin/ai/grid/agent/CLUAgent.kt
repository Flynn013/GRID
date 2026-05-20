package ai.grid.agent

import ai.grid.auth.AnthropicOAuthManager
import ai.grid.bridge.GodotFFITools
import ai.grid.data.LLMProvider
import ai.grid.data.Project
import ai.grid.data.ProjectRepository
import ai.grid.data.SettingsData
import ai.grid.data.SettingsRepository
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

// ── History persistence types ─────────────────────────────────────────────

@Serializable
private data class SavedMessage(
    val role: String,
    val content: String,
    val isCode: Boolean = false,
    val toolCallId: String? = null,
)

@Serializable
private data class SavedBlock(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val inputJson: String? = null,
)

@Serializable
private data class SavedEntry(
    val type: String,
    val text: String? = null,
    val blocks: List<SavedBlock>? = null,
    val id: String? = null,
    val name: String? = null,
    val result: String? = null,
)

@Serializable
private data class HistoryFile(
    val messages: List<SavedMessage>,
    val entries: List<SavedEntry>,
)

private fun Message.toSaved() = SavedMessage(role.name, content, isCode, toolCallId)
private fun SavedMessage.toMessage() = Message(Role.valueOf(role), content, isCode, toolCallId)

private fun Block.toSaved(): SavedBlock = when (this) {
    is Block.Text    -> SavedBlock("text", text = text)
    is Block.ToolUse -> SavedBlock("tool_use", id = id, name = name, inputJson = input.toString())
}

private fun SavedBlock.toBlock(json: Json): Block? = when (type) {
    "text"     -> text?.let { Block.Text(it) }
    "tool_use" -> if (id != null && name != null && inputJson != null)
        runCatching { Block.ToolUse(id, name, json.parseToJsonElement(inputJson).jsonObject) }.getOrNull()
    else null
    else       -> null
}

private fun ApiEntry.toSaved(): SavedEntry = when (this) {
    is ApiEntry.UserText        -> SavedEntry("user",        text   = text)
    is ApiEntry.AssistantBlocks -> SavedEntry("assistant",   blocks = blocks.map { it.toSaved() })
    is ApiEntry.ToolResult      -> SavedEntry("tool_result", id     = id, name = name, result = result)
}

private fun SavedEntry.toApiEntry(json: Json): ApiEntry? = when (type) {
    "user"        -> text?.let { ApiEntry.UserText(it) }
    "assistant"   -> blocks?.mapNotNull { it.toBlock(json) }?.let { ApiEntry.AssistantBlocks(it) }
    "tool_result" -> if (id != null && name != null && result != null) ApiEntry.ToolResult(id, name, result) else null
    else          -> null
}

private const val BASE_SYSTEM = """
You are CLU, the embedded AI agent of GRID — a sovereign mobile game engine IDE for Android.
You have FULL Godot 4 engine access via tool calls. You can build complete 2D and 3D games.

## Workflow
1. Call project_get_gdd at session start to understand project scope.
2. Call godot_get_scene_tree before structural changes.
3. Call godot_create_snapshot before destructive edits (undo anchor).
4. Use godot_execute_gdscript to build, edit, and animate scenes with any GDScript.
5. Use godot_write_script to persist .gd scripts; godot_read_file / godot_list_dir to inspect project.
6. Call project_append_sprint at session end to commit progress.

## GDScript Execution Pattern
godot_execute_gdscript wraps your code in func _run(). "tree" (SceneTree) is pre-injected.
Example — spawn a CharacterBody3D and position it:
  var player = CharacterBody3D.new()
  player.name = "Player"
  tree.root.add_child(player)
  player.owner = tree.root
  player.position = Vector3(0, 1, 0)

## Godot 4 — Key 2D Nodes
- Node2D       | position:Vector2, rotation:float, scale:Vector2, z_index:int
- Sprite2D     | texture:Texture2D, hframes:int, vframes:int, frame:int, centered:bool
- AnimatedSprite2D | sprite_frames:SpriteFrames, animation:StringName; call .play("name")
- CharacterBody2D | velocity:Vector2, up_direction:Vector2; call move_and_slide() each frame
- RigidBody2D  | mass:float, gravity_scale:float, linear_velocity:Vector2, lock_rotation:bool
- Area2D       | monitoring:bool, monitorable:bool; signals: body_entered, area_entered
- Camera2D     | zoom:Vector2, limit_left/right/top/bottom:int, position_smoothing_enabled:bool
- TileMapLayer | tile_set:TileSet; set_cell(Vector2i, src_id, atlas_coords, alt_tile)
- AnimationPlayer | current_animation:String, speed_scale:float; play(name), stop(), seek(t)
- CollisionShape2D | shape:Shape2D(RectangleShape2D|CircleShape2D|CapsuleShape2D), disabled:bool

## Godot 4 — Key 3D Nodes
- Node3D       | position:Vector3, rotation:Vector3, scale:Vector3, basis:Basis
- MeshInstance3D | mesh:Mesh(BoxMesh|SphereMesh|CylinderMesh), surface_override_material:Material, cast_shadow:int
- CharacterBody3D | velocity:Vector3, floor_snap_length:float; call move_and_slide() each frame
- RigidBody3D  | mass:float, linear_velocity:Vector3, gravity_scale:float, freeze:bool
- Area3D       | monitoring:bool; signals: body_entered, area_entered
- DirectionalLight3D | light_energy:float, light_color:Color, shadow_enabled:bool
- Camera3D     | fov:float, near:float, far:float; set current=true to activate
- CollisionShape3D | shape:Shape3D(BoxShape3D|SphereShape3D|CapsuleShape3D), disabled:bool
- AnimationPlayer | play(name), seek(t), stop(), speed_scale:float, current_animation:String
- NavigationAgent3D | target_position:Vector3, max_speed:float; signal velocity_computed

Keep responses concise. Prefer tool actions over explanations.
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

    private val apiHistory   = mutableListOf<ApiEntry>()
    private var systemPrompt = BASE_SYSTEM
    private var liteRtEngine: LiteRTEngine? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    init {
        viewModelScope.launch { settingsRepo.flow.collect { config = it } }
        viewModelScope.launch {
            projectRepo.activeProjectFlow.collect { project ->
                project?.gddPath?.let { loadHistory(it) } ?: run {
                    apiHistory.clear()
                    _messages.value = emptyList()
                }
            }
        }
    }

    fun clearHistory() {
        apiHistory.clear()
        _messages.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { historyFilePath()?.delete() }
    }

    suspend fun send(userText: String) {
        // Rebuild system prompt with live project + GDD context.
        systemPrompt = buildSystemPrompt()

        apiHistory.add(ApiEntry.UserText(userText))
        _messages.update { it + Message(Role.USER, userText) }
        _isThinking.value = true
        try {
            when (runCatching { LLMProvider.valueOf(config.activeProvider) }.getOrDefault(LLMProvider.ANTHROPIC)) {
                LLMProvider.ANTHROPIC,
                LLMProvider.ANTHROPIC_OAUTH -> runAnthropicLoop()
                LLMProvider.GEMINI          -> runGeminiLoop()
                LLMProvider.LITERT          -> runLocalLoop()
            }
        } finally {
            _isThinking.value = false
            _activeTask.value = null
            saveHistory()
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
        val isOAuth = config.activeProvider == LLMProvider.ANTHROPIC_OAUTH.name
        if (!isOAuth && config.anthropicKey.isBlank()) {
            emit("No Anthropic key. Go to VENDOR → set Anthropic key.")
            return
        }
        if (isOAuth && !AnthropicOAuthManager.isSignedIn(getApplication())) {
            emit("Not signed in to Anthropic. Go to VENDOR → CLAUDE → Sign in.")
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

        val isOAuth = config.activeProvider == LLMProvider.ANTHROPIC_OAUTH.name
        return try {
            val resp = http.post("https://api.anthropic.com/v1/messages") {
                if (isOAuth) {
                    val token = AnthropicOAuthManager.getValidToken(getApplication())
                        ?: run { emit("Anthropic OAuth: session expired — go to VENDOR to sign in again."); return null }
                    header("Authorization", "Bearer $token")
                } else {
                    header("x-api-key", config.anthropicKey)
                }
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

    // ── LiteRT local inference ─────────────────────────────────────────────

    private suspend fun runLocalLoop(maxRounds: Int = 6) {
        if (config.liteRtPath.isBlank()) {
            emit("No local model path configured. Go to VENDOR → set LiteRT model path (e.g. /data/local/tmp/gemma-2b.litertlm).")
            return
        }

        val engine = liteRtEngine ?: LiteRTEngine().also { liteRtEngine = it }

        val loadResult = withContext(Dispatchers.IO) {
            engine.loadModelBlocking(config.liteRtPath)
        }
        if (loadResult.isFailure) {
            emit("Failed to load local model: ${loadResult.exceptionOrNull()?.message}")
            return
        }

        repeat(maxRounds) {
            val prompt = buildLocalPrompt()
            _activeTask.value = "local model..."

            val genResult = withContext(Dispatchers.IO) {
                engine.generateOnce(prompt)
            }
            _activeTask.value = null

            if (genResult.isFailure) {
                emit("Local inference error: ${genResult.exceptionOrNull()?.message}")
                return
            }

            val rawResponse = genResult.getOrThrow().trim()
            if (rawResponse.isBlank()) return

            val toolCallRegex   = Regex("""<tool_call>(.*?)</tool_call>""", RegexOption.DOT_MATCHES_ALL)
            val toolMatches     = toolCallRegex.findAll(rawResponse).toList()
            val textContent     = rawResponse.replace(toolCallRegex, "").trim()
            val assistantBlocks = mutableListOf<Block>()
            val pendingTools    = mutableListOf<Triple<String, String, JsonObject>>()

            if (textContent.isNotBlank()) {
                assistantBlocks.add(Block.Text(textContent))
                _messages.update { it + Message(Role.ASSISTANT, textContent) }
            }

            for (match in toolMatches) {
                try {
                    val callObj = json.parseToJsonElement(match.groupValues[1].trim()).jsonObject
                    val name  = callObj["name"]?.jsonPrimitive?.content  ?: continue
                    val input = callObj["input"]?.jsonObject ?: buildJsonObject {}
                    val id    = "l-${name}-${System.currentTimeMillis()}"
                    assistantBlocks.add(Block.ToolUse(id, name, input))
                    pendingTools.add(Triple(id, name, input))
                } catch (_: Exception) {}
            }

            if (assistantBlocks.isNotEmpty()) apiHistory.add(ApiEntry.AssistantBlocks(assistantBlocks))

            for ((id, name, input) in pendingTools) {
                _activeTask.value = "→ $name"
                val result = GodotFFITools.dispatch(name, input)
                _activeTask.value = null
                apiHistory.add(ApiEntry.ToolResult(id, name, result))
                _messages.update { it + Message(Role.TOOL, "$name → $result", toolCallId = id) }
            }

            if (pendingTools.isEmpty()) return
        }
    }

    /**
     * Formats the full apiHistory + system prompt + tool descriptions into a
     * single plain-text prompt for stateless local-model completion.
     * Models that follow the <tool_call> convention will produce parseable output;
     * those that don't will still produce readable text responses.
     */
    private fun buildLocalPrompt(): String = buildString {
        appendLine(systemPrompt)
        appendLine()
        appendLine("## Tool-Calling Instructions")
        appendLine("To call a tool, output a JSON object wrapped in <tool_call> tags on its own line:")
        appendLine("""<tool_call>{"name":"tool_name","input":{"param":"value"}}</tool_call>""")
        appendLine("Only use valid tool names listed below. Free text before or after the tag is shown to the user.")
        appendLine()
        appendLine("### Available Tools")
        GodotFFITools.toolDescriptions.forEach { (name, desc) ->
            appendLine("- $name: $desc")
        }
        appendLine()
        appendLine("---")
        appendLine()

        for (entry in apiHistory) {
            when (entry) {
                is ApiEntry.UserText -> appendLine("User: ${entry.text}")
                is ApiEntry.AssistantBlocks -> {
                    val rendered = buildString {
                        entry.blocks.forEach { b ->
                            when (b) {
                                is Block.Text    -> appendLine(b.text)
                                is Block.ToolUse -> appendLine(
                                    """<tool_call>{"name":"${b.name}","input":${b.input}}</tool_call>"""
                                )
                            }
                        }
                    }.trim()
                    if (rendered.isNotBlank()) appendLine("Assistant: $rendered")
                }
                is ApiEntry.ToolResult ->
                    appendLine("Tool [${entry.name}]: ${entry.result.take(800)}")
            }
        }
        append("Assistant:")
    }

    // ── History persistence ───────────────────────────────────────────────

    private fun historyFilePath(): File? {
        val gddPath = GodotFFITools.activeGddPath ?: return null
        return File(File(gddPath).parent, "clu_history.json")
    }

    private suspend fun saveHistory() = withContext(Dispatchers.IO) {
        val file = historyFilePath() ?: return@withContext
        runCatching {
            val data = HistoryFile(
                messages = _messages.value.map { it.toSaved() },
                entries  = apiHistory.map { it.toSaved() },
            )
            file.writeText(json.encodeToString(data))
        }
    }

    private suspend fun loadHistory(gddPath: String) = withContext(Dispatchers.IO) {
        val file = File(File(gddPath).parent, "clu_history.json")
        if (!file.exists()) {
            apiHistory.clear()
            _messages.value = emptyList()
            return@withContext
        }
        runCatching {
            val data = json.decodeFromString<HistoryFile>(file.readText())
            apiHistory.clear()
            apiHistory.addAll(data.entries.mapNotNull { it.toApiEntry(json) })
            _messages.value = data.messages.map { it.toMessage() }
        }.onFailure {
            apiHistory.clear()
            _messages.value = emptyList()
            file.delete()
        }
    }

    private fun emit(text: String) {
        _messages.update { it + Message(Role.ASSISTANT, text) }
    }

    override fun onCleared() {
        super.onCleared()
        http.close()
        liteRtEngine?.shutdown()
    }
}
