package ai.grid.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GodotFFITools {

    // Set by GRIDNavGraph whenever the active project changes.
    @Volatile var activeGddPath:     String? = null
    @Volatile var activeProjectName: String? = null

    private val toolDefs: List<JsonObject> = buildList {
        // ── Godot scene tools ─────────────────────────────────────────────
        add(toolDef(
            name = "godot_get_scene_tree",
            description = "Scrapes the live Godot SceneTree into JSON. Call before spawning or modifying nodes.",
            params = buildJsonObject {},
            required = emptyList()
        ))
        add(toolDef(
            name = "godot_spawn_node",
            description = "Instantiates a Godot node class into the active scene (CharacterBody3D, MeshInstance3D, DirectionalLight3D, etc.).",
            params = buildJsonObject {
                put("class_name", prop("string", "Godot class name e.g. CharacterBody3D"))
                put("parent_path", prop("string", "Scene path to parent node e.g. /root/World"))
            },
            required = listOf("class_name")
        ))
        add(toolDef(
            name = "godot_set_property_vector3",
            description = "Sets a Vector3 property (position, rotation, scale) on a node, instantly in RAM.",
            params = buildJsonObject {
                put("node_path", prop("string", "Full scene path e.g. /root/World/Player"))
                put("property",  prop("string", "position | rotation | scale"))
                put("x",         prop("number", "X component"))
                put("y",         prop("number", "Y component"))
                put("z",         prop("number", "Z component"))
            },
            required = listOf("node_path", "property", "x", "y", "z")
        ))
        add(toolDef(
            name = "godot_load_asset",
            description = "Loads a .glb file from the Android filesystem into the active scene.",
            params = buildJsonObject {
                put("file_path", prop("string", "Absolute path to .glb e.g. /data/user/0/ai.grid/projects/player.glb"))
            },
            required = listOf("file_path")
        ))

        // ── Project / GDD tools ────────────────────────────────────────────
        add(toolDef(
            name = "project_get_gdd",
            description = "Reads GDD_MASTER.md of the active project. Call at session start or when you need to understand project scope before structural decisions.",
            params = buildJsonObject {},
            required = emptyList()
        ))
        add(toolDef(
            name = "project_append_sprint",
            description = "Appends a sprint-summary note to GDD_MASTER.md and commits it with JGit. Call at the end of a productive session to record what was built and what's next.",
            params = buildJsonObject {
                put("sprint_note", prop("string", "Markdown-formatted sprint summary: what was accomplished, what's next, open issues."))
            },
            required = listOf("sprint_note")
        ))
    }

    // Anthropic format: {name, description, input_schema: {type, properties, required}}
    val anthropicToolSchemas: JsonArray = buildJsonArray {
        toolDefs.forEach { t ->
            add(buildJsonObject {
                put("name",         t["name"]!!)
                put("description",  t["description"]!!)
                put("input_schema", buildJsonObject {
                    put("type",       "object")
                    put("properties", t["properties"] ?: buildJsonObject {})
                    t["required"]?.let { put("required", it) }
                })
            })
        }
    }

    // Gemini format: {name, description, parameters: {type, properties, required}}
    val geminiToolSchemas: JsonArray = buildJsonArray {
        toolDefs.forEach { t ->
            add(buildJsonObject {
                put("name",        t["name"]!!)
                put("description", t["description"]!!)
                put("parameters", buildJsonObject {
                    put("type",       "object")
                    put("properties", t["properties"] ?: buildJsonObject {})
                    t["required"]?.let { put("required", it) }
                })
            })
        }
    }

    // Plain (name, description) pairs for local-model prompt injection.
    val toolDescriptions: List<Pair<String, String>> = toolDefs.map {
        it["name"]!!.jsonPrimitive.content to it["description"]!!.jsonPrimitive.content
    }

    suspend fun dispatch(toolName: String, args: JsonObject): String {
        return when (toolName) {

            // ── Godot FFI ────────────────────────────────────────────────
            "godot_get_scene_tree" -> {
                GodotBridge.ensureLoaded()
                GodotBridge.getSceneTreeJson()
            }
            "godot_spawn_node" -> {
                GodotBridge.ensureLoaded()
                val cls    = args["class_name"]?.jsonPrimitive?.content ?: return "error: missing class_name"
                val parent = args["parent_path"]?.jsonPrimitive?.content ?: "/root"
                GodotBridge.spawnNode(cls, parent).toString()
            }
            "godot_set_property_vector3" -> {
                GodotBridge.ensureLoaded()
                val path = args["node_path"]?.jsonPrimitive?.content ?: return "error: missing node_path"
                val prop = args["property"]?.jsonPrimitive?.content  ?: return "error: missing property"
                val x    = args["x"]?.jsonPrimitive?.float ?: 0f
                val y    = args["y"]?.jsonPrimitive?.float ?: 0f
                val z    = args["z"]?.jsonPrimitive?.float ?: 0f
                GodotBridge.setPropertyVector3(path, prop, x, y, z).toString()
            }
            "godot_load_asset" -> {
                GodotBridge.ensureLoaded()
                val path = args["file_path"]?.jsonPrimitive?.content ?: return "error: missing file_path"
                GodotBridge.loadAsset(path).toString()
            }

            // ── Project / GDD ─────────────────────────────────────────────
            "project_get_gdd" -> withContext(Dispatchers.IO) {
                val path = activeGddPath ?: return@withContext "error: no active project"
                runCatching { File(path).readText() }
                    .getOrElse { "error: ${it.message}" }
            }
            "project_append_sprint" -> withContext(Dispatchers.IO) {
                val note    = args["sprint_note"]?.jsonPrimitive?.content
                    ?: return@withContext "error: missing sprint_note"
                val gddPath = activeGddPath
                    ?: return@withContext "error: no active project"
                val gddFile = File(gddPath)
                if (!gddFile.exists()) return@withContext "error: GDD_MASTER.md not found"

                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                gddFile.appendText("\n\n### Sprint — $ts\n$note\n")

                try {
                    val projectDir = gddFile.parentFile!!
                    val git = org.eclipse.jgit.api.Git.open(projectDir)
                    git.add().addFilepattern("GDD_MASTER.md").call()
                    git.commit()
                        .setAuthor("CLU", "clu@grid.ai")
                        .setCommitter("CLU", "clu@grid.ai")
                        .setMessage("sprint: $ts")
                        .call()
                    git.close()
                    "Sprint committed to GDD_MASTER.md"
                } catch (e: Exception) {
                    "Sprint written; git commit failed: ${e.message}"
                }
            }

            else -> "error: unknown tool $toolName"
        }
    }

    private fun toolDef(
        name: String,
        description: String,
        params: JsonObject,
        required: List<String>,
    ) = buildJsonObject {
        put("name",        name)
        put("description", description)
        put("properties",  params)
        if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(it) } })
    }

    private fun prop(type: String, description: String) = buildJsonObject {
        put("type",        type)
        put("description", description)
    }
}
