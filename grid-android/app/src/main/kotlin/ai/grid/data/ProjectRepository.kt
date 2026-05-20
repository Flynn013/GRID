package ai.grid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ProjectType { GAME, APP }

data class ProjectConfig(
    val name: String,
    val type: ProjectType,
    val is3D: Boolean = true,
    val perspective: String = "",
    val artStyle: String = "",
    val vibeLore: String = "",
    val uiTheme: String = "",
    val featuresSummary: String = "",
)

@Serializable
data class Project(
    val id: String       = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val path: String,
    val gddPath: String,
    val createdAt: Long  = System.currentTimeMillis(),
    val status: String   = "ACTIVE",
)

private val Context.projectStore by preferencesDataStore(name = "grid_projects")

class ProjectRepository private constructor(private val ctx: Context) {

    private val ser   = Json { ignoreUnknownKeys = true }
    private val pKey  = stringPreferencesKey("projects_json")
    private val aKey  = stringPreferencesKey("active_id")

    val projectsFlow: Flow<List<Project>> = ctx.projectStore.data.map { prefs ->
        prefs[pKey]?.let { str ->
            runCatching { ser.decodeFromString<List<Project>>(str) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    val activeProjectFlow: Flow<Project?> = ctx.projectStore.data.map { prefs ->
        val activeId = prefs[aKey] ?: return@map null
        prefs[pKey]?.let { str ->
            runCatching { ser.decodeFromString<List<Project>>(str) }.getOrDefault(emptyList())
                .find { it.id == activeId }
        }
    }

    suspend fun createProject(config: ProjectConfig): Project = withContext(Dispatchers.IO) {
        val id   = UUID.randomUUID().toString()
        val slug = config.name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32)
        val dir  = File(ctx.filesDir, "projects/$slug-${id.take(8)}").also { it.mkdirs() }

        val gdd  = File(dir, "GDD_MASTER.md").also { it.writeText(buildGdd(config)) }

        try {
            val git = org.eclipse.jgit.api.Git.init().setDirectory(dir).call()
            git.add().addFilepattern(".").call()
            git.commit()
                .setAuthor("GRID", "grid@local")
                .setCommitter("GRID", "grid@local")
                .setMessage("init: scaffold ${config.name}")
                .call()
            git.close()
        } catch (_: Exception) {}

        val project = Project(
            id      = id,
            name    = config.name,
            type    = config.type.name,
            path    = dir.absolutePath,
            gddPath = gdd.absolutePath,
        )

        ctx.projectStore.edit { prefs ->
            val current = prefs[pKey]?.let {
                runCatching { ser.decodeFromString<List<Project>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[pKey] = ser.encodeToString(current + project)
            prefs[aKey] = id
        }
        project
    }

    suspend fun setActive(id: String) {
        ctx.projectStore.edit { prefs -> prefs[aKey] = id }
    }

    private fun buildGdd(c: ProjectConfig) = buildString {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        appendLine("# GDD_MASTER — ${c.name}")
        appendLine()
        appendLine("## Overview")
        appendLine("- **Type**: ${c.type.name}")
        appendLine("- **Created**: $date")
        appendLine("- **Engine**: Godot 4 / GRID")
        appendLine()
        if (c.type == ProjectType.GAME) {
            appendLine("## Dimension & Perspective")
            appendLine("- ${if (c.is3D) "3D" else "2D"}  |  ${c.perspective}")
            appendLine()
            if (c.artStyle.isNotBlank())  { appendLine("## Art Direction");  appendLine(c.artStyle);  appendLine() }
            if (c.vibeLore.isNotBlank())  { appendLine("## Vibe & Lore");    appendLine(c.vibeLore);  appendLine() }
        } else {
            if (c.uiTheme.isNotBlank())   { appendLine("## UI Theme");        appendLine(c.uiTheme);   appendLine() }
        }
        if (c.featuresSummary.isNotBlank()) {
            appendLine("## Systems & Features")
            appendLine(c.featuresSummary)
            appendLine()
        }
        if (c.type == ProjectType.GAME) {
            appendLine("## Godot Node Palette")
            if (c.is3D) {
                appendLine("_Key 3D nodes for this project:_")
                appendLine("- **Node3D** — base spatial; position/rotation/scale:Vector3")
                appendLine("- **MeshInstance3D** — mesh:Mesh, surface_override_material:Material")
                appendLine("- **CharacterBody3D** — velocity:Vector3; move_and_slide() per frame")
                appendLine("- **RigidBody3D** — mass, linear_velocity, gravity_scale, freeze")
                appendLine("- **Area3D** — body_entered/area_entered signals; trigger zones")
                appendLine("- **DirectionalLight3D** — light_energy, light_color, shadow_enabled")
                appendLine("- **Camera3D** — fov, near, far; current=true to activate")
                appendLine("- **CollisionShape3D** — shape: BoxShape3D|SphereShape3D|CapsuleShape3D")
                appendLine("- **AnimationPlayer** — play(name), seek(t), stop(); speed_scale")
                appendLine("- **NavigationAgent3D** — target_position, max_speed; velocity_computed signal")
            } else {
                appendLine("_Key 2D nodes for this project:_")
                appendLine("- **Node2D** — base 2D; position:Vector2, rotation:float, z_index:int")
                appendLine("- **Sprite2D** — texture:Texture2D, hframes, vframes, frame, centered")
                appendLine("- **AnimatedSprite2D** — sprite_frames:SpriteFrames; play(\"name\")")
                appendLine("- **CharacterBody2D** — velocity:Vector2; move_and_slide() per frame")
                appendLine("- **RigidBody2D** — mass, gravity_scale, linear_velocity, lock_rotation")
                appendLine("- **Area2D** — body_entered/area_entered signals; hitboxes, pickups")
                appendLine("- **Camera2D** — zoom:Vector2, limits, position_smoothing_enabled")
                appendLine("- **TileMapLayer** — tile_set:TileSet; set_cell(Vector2i, src_id, coords)")
                appendLine("- **AnimationPlayer** — play(name), seek(t), stop(); speed_scale")
                appendLine("- **CollisionShape2D** — shape: RectangleShape2D|CircleShape2D|CapsuleShape2D")
            }
            appendLine()
        }
        appendLine("## Sprint Log")
        appendLine("<!-- CLU will append sprint notes here -->")
    }

    companion object {
        @Volatile private var INSTANCE: ProjectRepository? = null
        fun get(ctx: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: ProjectRepository(ctx.applicationContext).also { INSTANCE = it }
        }
    }
}
