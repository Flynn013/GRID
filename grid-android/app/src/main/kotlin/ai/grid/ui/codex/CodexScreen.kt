package ai.grid.ui.codex

import ai.grid.data.Project
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BG   = Color(0xFF0A0A0A)
private val CYAN = Color(0xFF00E5FF)
private val CARD = Color(0xFF141414)
private val TEXT = Color(0xFFE0E0E0)

// ── Godot 4 reference data ─────────────────────────────────────────────────

private data class NodeRef(
    val name: String,
    val category: String,   // "2D", "3D", "GENERAL", "GDSCRIPT"
    val summary: String,
    val props: List<String>,
    val usage: String = "",
)

private val GODOT_REFS: List<NodeRef> = listOf(
    // GENERAL
    NodeRef("Node", "GENERAL", "Base class for all scene objects.",
        listOf("name:String", "process_mode:int", "owner:Node"),
        "add_child(n), remove_child(n), get_node(path), queue_free()"),
    NodeRef("RefCounted", "GENERAL", "Reference-counted base; auto-freed when no references remain.",
        listOf("(no spatial props)"), "Use for data objects, resources, helpers."),
    NodeRef("AnimationPlayer", "GENERAL", "Keyframe animation for any node property.",
        listOf("current_animation:String", "speed_scale:float", "autoplay:String"),
        "play(name), stop(), seek(t), get_animation(name)"),
    NodeRef("AudioStreamPlayer", "GENERAL", "Non-positional audio playback.",
        listOf("stream:AudioStream", "volume_db:float", "autoplay:bool"),
        "play(), stop(), playing:bool"),
    NodeRef("Timer", "GENERAL", "Fires timeout signal after wait_time seconds.",
        listOf("wait_time:float", "one_shot:bool", "autostart:bool"),
        "start(), stop(); signal timeout"),

    // 2D
    NodeRef("Node2D", "2D", "Base for all 2D objects.",
        listOf("position:Vector2", "rotation:float", "scale:Vector2", "z_index:int", "visible:bool"),
        "to_local(v), to_global(v), look_at(pos)"),
    NodeRef("Sprite2D", "2D", "Displays a Texture2D in 2D space.",
        listOf("texture:Texture2D", "hframes:int", "vframes:int", "frame:int", "centered:bool", "offset:Vector2"),
        "Use hframes/vframes for sprite sheets; frame to select the cell."),
    NodeRef("AnimatedSprite2D", "2D", "Frame-based 2D animation using SpriteFrames resource.",
        listOf("sprite_frames:SpriteFrames", "animation:StringName", "frame:int", "speed_scale:float"),
        "play(\"name\"), stop(), is_playing()"),
    NodeRef("CharacterBody2D", "2D", "Kinematic body for player/NPC movement.",
        listOf("velocity:Vector2", "up_direction:Vector2", "floor_snap_length:float"),
        "move_and_slide() — call in _physics_process(); is_on_floor()"),
    NodeRef("RigidBody2D", "2D", "Physics-simulated body.",
        listOf("mass:float", "gravity_scale:float", "linear_velocity:Vector2", "lock_rotation:bool"),
        "apply_impulse(v), apply_force(v); signal body_entered"),
    NodeRef("StaticBody2D", "2D", "Immovable collision body (terrain, walls).",
        listOf("physics_material_override:PhysicsMaterial"),
        "Needs CollisionShape2D child."),
    NodeRef("Area2D", "2D", "Detection zone — no physics response.",
        listOf("monitoring:bool", "monitorable:bool", "collision_layer:int", "collision_mask:int"),
        "signals: body_entered, body_exited, area_entered, area_exited"),
    NodeRef("Camera2D", "2D", "2D viewport camera.",
        listOf("zoom:Vector2", "limit_left:int", "limit_right:int", "limit_top:int", "limit_bottom:int", "position_smoothing_enabled:bool"),
        "make_current(); offset:Vector2"),
    NodeRef("TileMapLayer", "2D", "Single layer of grid-based tiles (replaces TileMap in 4.3+).",
        listOf("tile_set:TileSet", "enabled:bool"),
        "set_cell(coords:Vector2i, src_id, atlas_coords, alt); erase_cell(coords)"),
    NodeRef("CollisionShape2D", "2D", "Attaches a collision shape to a physics body.",
        listOf("shape:Shape2D", "disabled:bool"),
        "Shapes: RectangleShape2D, CircleShape2D, CapsuleShape2D"),

    // 3D
    NodeRef("Node3D", "3D", "Base for all 3D objects.",
        listOf("position:Vector3", "rotation:Vector3", "scale:Vector3", "basis:Basis", "visible:bool"),
        "look_at(target, up), to_local(v), to_global(v), rotate(axis, angle)"),
    NodeRef("MeshInstance3D", "3D", "Renders a 3D mesh.",
        listOf("mesh:Mesh", "surface_override_material:Material", "cast_shadow:int"),
        "Mesh types: BoxMesh, SphereMesh, CylinderMesh, PlaneMesh, ArrayMesh"),
    NodeRef("CharacterBody3D", "3D", "Kinematic body for 3D player/NPC movement.",
        listOf("velocity:Vector3", "floor_snap_length:float", "up_direction:Vector3"),
        "move_and_slide() in _physics_process(); is_on_floor(), is_on_wall()"),
    NodeRef("RigidBody3D", "3D", "Physics-simulated 3D body.",
        listOf("mass:float", "linear_velocity:Vector3", "angular_velocity:Vector3", "gravity_scale:float", "freeze:bool"),
        "apply_impulse(v), apply_force(v)"),
    NodeRef("StaticBody3D", "3D", "Immovable 3D physics body.",
        listOf("physics_material_override:PhysicsMaterial"),
        "Needs CollisionShape3D child. Use for terrain and walls."),
    NodeRef("Area3D", "3D", "3D detection zone.",
        listOf("monitoring:bool", "gravity:float", "gravity_point:bool"),
        "signals: body_entered, body_exited, area_entered, area_exited"),
    NodeRef("DirectionalLight3D", "3D", "Global directional light (sun/moon).",
        listOf("light_energy:float", "light_color:Color", "shadow_enabled:bool"),
        "Affects entire scene; set rotation for sun angle."),
    NodeRef("OmniLight3D", "3D", "Point light radiating in all directions.",
        listOf("light_energy:float", "light_color:Color", "omni_range:float", "shadow_enabled:bool"),
        "Use for lamps, fire, pickups."),
    NodeRef("SpotLight3D", "3D", "Cone-shaped spotlight.",
        listOf("light_energy:float", "spot_range:float", "spot_angle:float"),
        "Use for torches, flashlights, headlights."),
    NodeRef("Camera3D", "3D", "3D viewport camera.",
        listOf("fov:float", "near:float", "far:float", "current:bool"),
        "Set current=true to activate. project_position(), unproject_position()"),
    NodeRef("CollisionShape3D", "3D", "Attaches a 3D collision shape.",
        listOf("shape:Shape3D", "disabled:bool"),
        "Shapes: BoxShape3D, SphereShape3D, CapsuleShape3D, ConcavePolygonShape3D"),
    NodeRef("NavigationAgent3D", "3D", "NPC pathfinding agent.",
        listOf("target_position:Vector3", "max_speed:float", "path_desired_distance:float"),
        "signal velocity_computed(v) — apply velocity in _physics_process()"),
    NodeRef("WorldEnvironment", "3D", "Global sky, fog, tone mapping, SSAO.",
        listOf("environment:Environment"),
        "One per scene; controls ambient_light, fog, glow, ssao, ssil"),
    NodeRef("AudioStreamPlayer3D", "3D", "Positional 3D audio.",
        listOf("stream:AudioStream", "max_distance:float", "unit_size:float", "volume_db:float"),
        "play(), stop(); attenuation_model:int"),

    // GDSCRIPT
    NodeRef("@GDScript", "GDSCRIPT", "Built-in GDScript global functions.",
        listOf("print(…)", "printerr(…)", "range(n)", "randi()", "randf()", "lerp(a,b,t)", "clamp(v,lo,hi)"),
        "floor(), ceil(), abs(), sign(), sin(), cos(), sqrt(), pow()"),
    NodeRef("Vector2", "GDSCRIPT", "2D vector.",
        listOf("x:float", "y:float", "length():float", "normalized():Vector2"),
        "ZERO, ONE, UP, DOWN, LEFT, RIGHT; dot(v), cross(v), angle_to(v), rotated(angle)"),
    NodeRef("Vector3", "GDSCRIPT", "3D vector.",
        listOf("x:float", "y:float", "z:float", "length():float", "normalized():Vector3"),
        "ZERO, ONE, UP, DOWN; dot(v), cross(v), angle_to(v), rotated(axis, angle)"),
    NodeRef("Color", "GDSCRIPT", "RGBA color.",
        listOf("r:float", "g:float", "b:float", "a:float"),
        "Color(r,g,b,a); Color.RED, WHITE, BLACK, TRANSPARENT; blend(over), lerp(to,t)"),
    NodeRef("Tween", "GDSCRIPT", "Interpolation helper created by get_tree().create_tween().",
        listOf("(returned by create_tween())"),
        "tween_property(obj, prop, final, duration).set_trans(SINE).set_ease(IN_OUT)"),
)

// ── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun CodexScreen(activeProject: Project? = null) {
    var selectedTab  by remember { mutableStateOf(0) }
    var refreshKey   by remember { mutableStateOf(0) }
    var sprintNote   by remember { mutableStateOf("") }
    var isCommitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val gdd by produceState<String?>(null, activeProject?.path, refreshKey) {
        value = activeProject?.gddPath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching { File(path).readText() }.getOrNull()
            }
        }
    }

    val sprints: List<Pair<String, String>> = remember(gdd) {
        gdd?.substringAfter("## Sprint Log", "")?.trim()
            ?.split("### Sprint — ")?.drop(1)
            ?.map { chunk ->
                val lines = chunk.lines()
                chunk.lines().firstOrNull()?.trim().orEmpty() to
                    lines.drop(1).joinToString("\n").trim()
            } ?: emptyList()
    }

    val gdScripts by produceState<List<File>>(emptyList(), activeProject?.path, refreshKey) {
        value = withContext(Dispatchers.IO) {
            activeProject?.path?.let { dir ->
                File(dir).walkTopDown().filter { it.extension == "gd" }.sortedBy { it.name }.toList()
            } ?: emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("BRAIN GRAPH", color = CYAN, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                if (activeProject != null) {
                    Text(activeProject.name, color = Color.Gray, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
            if (activeProject == null) {
                Text("OPEN A PROJECT IN FORGE", color = Color.DarkGray,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        HorizontalDivider(color = CYAN.copy(alpha = 0.2f))

        if (activeProject == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Description, null,
                        tint = CYAN.copy(alpha = 0.25f), modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("GDD / REFERENCE", color = Color.Gray, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold)
                    Text("No project open. Go to FORGE → open a project.",
                        color = Color.DarkGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = CARD,
                contentColor     = CYAN,
                indicator = { tabPos ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPos[selectedTab]),
                        color    = CYAN
                    )
                }
            ) {
                listOf(
                    Icons.Default.Description to "GDD",
                    Icons.Default.History to "SPRINTS",
                    Icons.Default.Code to "SCRIPTS",
                    Icons.Default.LibraryBooks to "REFERENCE",
                ).forEachIndexed { idx, (icon, label) ->
                    Tab(
                        selected = selectedTab == idx, onClick = { selectedTab = idx },
                        icon = { Icon(icon, null) },
                        text = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    )
                }
            }

            when (selectedTab) {
                0 -> GddTab(gdd)
                1 -> SprintsTab(
                    sprints, sprintNote, { sprintNote = it }, isCommitting,
                    onCommit = {
                        val note = sprintNote.trim()
                        if (note.isBlank()) return@SprintsTab
                        scope.launch {
                            isCommitting = true
                            withContext(Dispatchers.IO) {
                                val gddFile = File(activeProject.gddPath)
                                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                                gddFile.appendText("\n\n### Sprint — $ts\n$note\n")
                                runCatching {
                                    val git = Git.open(File(activeProject.path))
                                    git.add().addFilepattern("GDD_MASTER.md").call()
                                    git.commit().setAuthor("GRID", "grid@local")
                                        .setCommitter("GRID", "grid@local")
                                        .setMessage("sprint: $ts").call()
                                    git.close()
                                }
                            }
                            sprintNote = ""; refreshKey++; isCommitting = false
                        }
                    }
                )
                2 -> ScriptsTab(gdScripts, onRefresh = { refreshKey++ })
                3 -> ReferenceTab()
            }
        }
    }
}

// ── GDD tab ──────────────────────────────────────────────────────────────────

@Composable
private fun GddTab(gdd: String?) {
    if (gdd == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CYAN, modifier = Modifier.size(32.dp))
        }
        return
    }
    SelectionContainer {
        Text(
            gdd, color = TEXT, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

// ── Sprints tab ──────────────────────────────────────────────────────────────

@Composable
private fun SprintsTab(
    sprints: List<Pair<String, String>>,
    sprintNote: String,
    onNoteChange: (String) -> Unit,
    isCommitting: Boolean,
    onCommit: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(color = CARD, shape = RoundedCornerShape(10.dp), tonalElevation = 2.dp) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("NEW SPRINT NOTE", color = CYAN, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = sprintNote, onValueChange = onNoteChange,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("What was built? What's next? Open issues?", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CYAN, unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = TEXT, unfocusedTextColor = TEXT,
                            cursorColor = CYAN, containerColor = BG,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onCommit,
                        enabled = sprintNote.isNotBlank() && !isCommitting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CYAN, contentColor = Color.Black)
                    ) {
                        if (isCommitting) CircularProgressIndicator(
                            color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Commit, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("COMMIT SPRINT", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        if (sprints.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                    Text("No sprint notes yet.", color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            item {
                Text("SPRINT HISTORY (${sprints.size})", color = Color.Gray, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            items(sprints.reversed()) { (ts, body) ->
                Surface(color = CARD, shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(ts, color = CYAN, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        if (body.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(body, color = TEXT, fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Scripts tab ──────────────────────────────────────────────────────────────

@Composable
private fun ScriptsTab(scripts: List<File>, onRefresh: () -> Unit) {
    var selected by remember { mutableStateOf<File?>(null) }
    var content  by remember(selected) { mutableStateOf<String?>(null) }

    LaunchedEffect(selected) {
        content = selected?.let { withContext(Dispatchers.IO) { it.readText() } }
    }

    if (selected != null && content != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CARD)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected!!.name, color = CYAN, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold)
                IconButton(onClick = { selected = null }) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }
            SelectionContainer {
                Text(
                    content!!, color = TEXT, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${scripts.size} script${if (scripts.size != 1) "s" else ""}",
                    color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (scripts.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                    Text("No .gd scripts yet. CLU writes them via godot_write_script.",
                        color = Color.DarkGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            items(scripts) { file ->
                Surface(
                    color = CARD, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { selected = file }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Code, null, tint = CYAN, modifier = Modifier.size(18.dp))
                        Column {
                            Text(file.name, color = TEXT, fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(
                                file.parentFile?.name ?: "", color = Color.Gray,
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Reference tab ────────────────────────────────────────────────────────────

@Composable
private fun ReferenceTab() {
    var query    by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("ALL") }
    var expanded by remember { mutableStateOf<String?>(null) }

    val categories = listOf("ALL", "2D", "3D", "GENERAL", "GDSCRIPT")

    val filtered = remember(query, category) {
        GODOT_REFS.filter { ref ->
            (category == "ALL" || ref.category == category) &&
            (query.isBlank() || ref.name.contains(query, ignoreCase = true) ||
                ref.summary.contains(query, ignoreCase = true) ||
                ref.props.any { it.contains(query, ignoreCase = true) })
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search nodes…", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CYAN, unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = TEXT, unfocusedTextColor = TEXT,
                cursorColor = CYAN, containerColor = BG
            )
        )
        // Category chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val sel = category == cat
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (sel) CYAN else CARD,
                    modifier = Modifier.clickable { category = cat }
                ) {
                    Text(
                        cat, color = if (sel) Color.Black else Color.Gray,
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.name }) { ref ->
                val isExpanded = expanded == ref.name
                Surface(
                    color = CARD, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        expanded = if (isExpanded) null else ref.name
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = when (ref.category) {
                                        "2D"       -> Color(0xFF1A3A1A)
                                        "3D"       -> Color(0xFF1A2A3A)
                                        "GDSCRIPT" -> Color(0xFF3A2A1A)
                                        else       -> Color(0xFF2A2A2A)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        ref.category, fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = when (ref.category) {
                                            "2D"       -> Color(0xFF4CAF50)
                                            "3D"       -> Color(0xFF2196F3)
                                            "GDSCRIPT" -> Color(0xFFFF9800)
                                            else       -> Color.Gray
                                        },
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(ref.name, color = CYAN, fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = Color.Gray, modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(ref.summary, color = TEXT.copy(alpha = 0.7f), fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp))
                        if (isExpanded) {
                            Spacer(Modifier.height(8.dp))
                            Text("PROPERTIES", color = Color.Gray, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            ref.props.forEach { prop ->
                                Text("  • $prop", color = TEXT, fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace)
                            }
                            if (ref.usage.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("USAGE", color = Color.Gray, fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                SelectionContainer {
                                    Text(ref.usage, color = Color(0xFF80CBC4), fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
