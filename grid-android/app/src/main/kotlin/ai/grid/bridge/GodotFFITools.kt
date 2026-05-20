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
        add(toolDef(
            name = "godot_create_snapshot",
            description = "Creates an in-RAM checkpoint of the current SceneTree. Call before making destructive structural changes so the scene can be reverted if needed. Returns the snapshot ID.",
            params = buildJsonObject {},
            required = emptyList()
        ))
        add(toolDef(
            name = "godot_execute_gdscript",
            description = "Compiles and runs arbitrary GDScript code with full Godot engine access. Use Engine.get_main_loop() inside the script to access the SceneTree. Can spawn any node type, set any property, connect signals, play animations, create resources. Returns JSON {ok, result}.",
            params = buildJsonObject {
                put("code", prop("string", "GDScript code to execute. Do NOT include 'extends' or 'func _run():' — just write the body lines directly. Use Engine.get_main_loop() for tree access."))
            },
            required = listOf("code")
        ))
        add(toolDef(
            name = "godot_write_script",
            description = "Writes a GDScript (.gd) file to the active project directory. Use to persist AI-generated scripts that nodes can then reference.",
            params = buildJsonObject {
                put("rel_path",  prop("string", "Path relative to project root e.g. scripts/player.gd"))
                put("content",   prop("string", "Full GDScript source code"))
            },
            required = listOf("rel_path", "content")
        ))
        add(toolDef(
            name = "godot_read_file",
            description = "Reads a file from the active project directory. Use to inspect existing scripts, scenes (.tscn), or config files before editing.",
            params = buildJsonObject {
                put("rel_path", prop("string", "Path relative to project root e.g. project.godot"))
            },
            required = listOf("rel_path")
        ))
        add(toolDef(
            name = "godot_list_dir",
            description = "Lists files and directories in the active project directory (or a subdirectory). Use to explore project structure before reading or writing files.",
            params = buildJsonObject {
                put("rel_path", prop("string", "Path relative to project root, or empty string for root"))
            },
            required = emptyList()
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
            "godot_create_snapshot" -> {
                GodotBridge.ensureLoaded()
                val id = GodotBridge.createSnapshot()
                if (id >= 0) "Snapshot created: id=$id" else "error: snapshot failed"
            }
            "godot_execute_gdscript" -> {
                GodotBridge.ensureLoaded()
                val code = args["code"]?.jsonPrimitive?.content ?: return "error: missing code"
                GodotBridge.executeGdscript(code)
            }
            "godot_write_script" -> withContext(Dispatchers.IO) {
                val relPath = args["rel_path"]?.jsonPrimitive?.content ?: return@withContext "error: missing rel_path"
                val content = args["content"]?.jsonPrimitive?.content ?: return@withContext "error: missing content"
                val projectDir = activeGddPath?.let { File(it).parentFile }
                    ?: return@withContext "error: no active project"
                runCatching {
                    val target = File(projectDir, relPath)
                    target.parentFile?.mkdirs()
                    target.writeText(content)
                    "Written: $relPath (${content.length} chars)"
                }.getOrElse { "error: ${it.message}" }
            }
            "godot_read_file" -> withContext(Dispatchers.IO) {
                val relPath = args["rel_path"]?.jsonPrimitive?.content ?: return@withContext "error: missing rel_path"
                val projectDir = activeGddPath?.let { File(it).parentFile }
                    ?: return@withContext "error: no active project"
                runCatching {
                    val target = File(projectDir, relPath)
                    if (!target.exists()) return@runCatching "error: file not found"
                    target.readText().take(8000)
                }.getOrElse { "error: ${it.message}" }
            }
            "godot_list_dir" -> withContext(Dispatchers.IO) {
                val relPath = args["rel_path"]?.jsonPrimitive?.content ?: ""
                val projectDir = activeGddPath?.let { File(it).parentFile }
                    ?: return@withContext "error: no active project"
                runCatching {
                    val target = if (relPath.isBlank()) projectDir else File(projectDir, relPath)
                    if (!target.exists() || !target.isDirectory) return@runCatching "error: directory not found"
                    target.listFiles()
                        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                        ?.joinToString("\n") { f -> if (f.isDirectory) "[dir]  ${f.name}" else "[file] ${f.name}  (${f.length()} B)" }
                        ?: "empty"
                }.getOrElse { "error: ${it.message}" }
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
