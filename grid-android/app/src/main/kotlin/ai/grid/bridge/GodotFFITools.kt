package ai.grid.bridge

import kotlinx.serialization.json.*

object GodotFFITools {

    private val toolDefs: List<JsonObject> = buildList {
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

    suspend fun dispatch(toolName: String, args: JsonObject): String {
        GodotBridge.ensureLoaded()
        return when (toolName) {
            "godot_get_scene_tree" ->
                GodotBridge.getSceneTreeJson()

            "godot_spawn_node" -> {
                val cls    = args["class_name"]?.jsonPrimitive?.content ?: return "error: missing class_name"
                val parent = args["parent_path"]?.jsonPrimitive?.content ?: "/root"
                GodotBridge.spawnNode(cls, parent).toString()
            }

            "godot_set_property_vector3" -> {
                val path = args["node_path"]?.jsonPrimitive?.content ?: return "error: missing node_path"
                val prop = args["property"]?.jsonPrimitive?.content  ?: return "error: missing property"
                val x    = args["x"]?.jsonPrimitive?.float ?: 0f
                val y    = args["y"]?.jsonPrimitive?.float ?: 0f
                val z    = args["z"]?.jsonPrimitive?.float ?: 0f
                GodotBridge.setPropertyVector3(path, prop, x, y, z).toString()
            }

            "godot_load_asset" -> {
                val path = args["file_path"]?.jsonPrimitive?.content ?: return "error: missing file_path"
                GodotBridge.loadAsset(path).toString()
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
