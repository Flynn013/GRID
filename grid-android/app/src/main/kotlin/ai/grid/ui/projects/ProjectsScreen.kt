package ai.grid.ui.projects

import ai.grid.data.Project
import ai.grid.data.ProjectConfig
import ai.grid.data.ProjectType
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// FeatureNode stays in the UI layer — not persisted, only used for GDD rendering
data class FeatureNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val subFeatures: List<FeatureNode> = emptyList()
)

private val BG      = Color(0xFF121212)
private val CARD    = Color(0xFF1E1E1E)
private val CYAN    = Color(0xFF00E5FF)
private val TEXT    = Color(0xFFE0E0E0)

// ==============================================================================
// SCREEN ROUTER
// ==============================================================================

@Composable
fun ProjectsScreen(
    onNavigateToSession: (String) -> Unit,
    vm: ProjectsViewModel = viewModel(),
) {
    var isCreating by remember { mutableStateOf(false) }
    val lastCreated by vm.lastCreated.collectAsState()

    // Navigate once a project finishes creating
    LaunchedEffect(lastCreated) {
        lastCreated?.let { project ->
            vm.clearLastCreated()
            isCreating = false
            onNavigateToSession(project.id)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BG) {
        if (isCreating) {
            ProjectCreationWizard(
                onCancel   = { isCreating = false },
                onGenerate = { config -> vm.createProject(config) },
            )
        } else {
            ProjectDashboard(
                vm              = vm,
                onNewProject    = { isCreating = true },
                onOpenProject   = { project ->
                    vm.setActive(project.id)
                    onNavigateToSession(project.id)
                },
            )
        }
    }
}

// ==============================================================================
// DASHBOARD
// ==============================================================================

@Composable
private fun ProjectDashboard(
    vm: ProjectsViewModel,
    onNewProject: () -> Unit,
    onOpenProject: (Project) -> Unit,
) {
    val projects      by vm.projects.collectAsState()
    val activeProject by vm.activeProject.collectAsState()

    Scaffold(
        containerColor = BG,
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onNewProject,
                containerColor = CYAN,
                contentColor   = Color.Black
            ) {
                Icon(Icons.Default.Add, "New Project")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    "THE FORGE",
                    color      = CYAN, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "${projects.size} project${if (projects.size != 1) "s" else ""}",
                    color = Color.Gray, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (projects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen, null,
                                tint = CYAN.copy(alpha = 0.25f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "NO PROJECTS YET",
                                color = Color.Gray, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Tap + to forge your first project",
                                color = Color.DarkGray, fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    val isActive = activeProject?.id == project.id
                    ProjectCard(
                        project  = project,
                        isActive = isActive,
                        onClick  = { onOpenProject(project) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: Project, isActive: Boolean, onClick: () -> Unit) {
    val date = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(project.createdAt))
    Card(
        onClick = onClick,
        colors  = CardDefaults.cardColors(containerColor = CARD),
        modifier = Modifier.fillMaxWidth(),
        border  = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, CYAN) else null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(project.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (isActive) {
                        Badge(containerColor = CYAN) {
                            Text("ACTIVE", color = Color.Black, fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (project.type == "GAME") Icons.Default.SportsEsports
                        else Icons.Default.PhoneAndroid,
                        null, tint = Color.Gray, modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(project.type, color = Color.Gray, fontSize = 11.sp)
                    Text("  ·  $date", color = Color.DarkGray, fontSize = 11.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.DarkGray)
        }
    }
}

// ==============================================================================
// CREATION WIZARD  (state lifted here so Generate FAB can collect all fields)
// ==============================================================================

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjectCreationWizard(
    onCancel: () -> Unit,
    onGenerate: (ProjectConfig) -> Unit,
) {
    var selectedType by remember { mutableStateOf(ProjectType.APP) }

    // ── Lifted game form state ───────────────────────────────────────
    var gameTitle     by remember { mutableStateOf("") }
    var is3D          by remember { mutableStateOf(true) }
    var perspective   by remember { mutableStateOf("Third-Person") }
    var artStyle      by remember { mutableStateOf("") }
    var vibeLore      by remember { mutableStateOf("") }
    var gameFeatures  by remember { mutableStateOf(listOf<FeatureNode>()) }

    // ── Lifted app form state ───────────────────────────────────────
    var appTitle      by remember { mutableStateOf("") }
    var uiTheme       by remember { mutableStateOf("") }
    var appFeatures   by remember { mutableStateOf(listOf<FeatureNode>()) }

    val nameValid = (if (selectedType == ProjectType.GAME) gameTitle else appTitle).isNotBlank()

    Scaffold(
        containerColor = BG,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CARD)
                            .fillMaxWidth(0.6f)
                    ) {
                        ProjectType.entries.forEach { type ->
                            val selected = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedType = type }
                                    .background(if (selected) CYAN else Color.Transparent)
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    type.name,
                                    color = if (selected) Color.Black else TEXT,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel", tint = TEXT)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BG)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!nameValid) return@ExtendedFloatingActionButton
                    val config = when (selectedType) {
                        ProjectType.GAME -> ProjectConfig(
                            name             = gameTitle,
                            type             = ProjectType.GAME,
                            is3D             = is3D,
                            perspective      = perspective,
                            artStyle         = artStyle,
                            vibeLore         = vibeLore,
                            featuresSummary  = renderFeatureTree(gameFeatures),
                        )
                        ProjectType.APP  -> ProjectConfig(
                            name             = appTitle,
                            type             = ProjectType.APP,
                            uiTheme          = uiTheme,
                            featuresSummary  = renderFeatureTree(appFeatures),
                        )
                    }
                    onGenerate(config)
                },
                containerColor = if (nameValid) CYAN else Color.DarkGray,
                contentColor   = Color.Black,
            ) {
                Icon(Icons.Default.Check, "Generate")
                Spacer(Modifier.width(8.dp))
                Text("GENERATE GDD", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedType,
            modifier    = Modifier.padding(padding)
        ) { targetType ->
            when (targetType) {
                ProjectType.GAME -> GameConfigForm(
                    workingTitle     = gameTitle,    onTitleChange   = { gameTitle = it },
                    is3D             = is3D,         onIs3DChange    = { is3D = it },
                    perspective      = perspective,  onPerspChange   = { perspective = it },
                    artStyle         = artStyle,     onArtChange     = { artStyle = it },
                    vibeLore         = vibeLore,     onVibeChange    = { vibeLore = it },
                    features         = gameFeatures, onFeaturesChange = { gameFeatures = it },
                )
                ProjectType.APP  -> AppConfigForm(
                    workingTitle     = appTitle,     onTitleChange    = { appTitle = it },
                    uiTheme          = uiTheme,      onUiThemeChange  = { uiTheme = it },
                    features         = appFeatures,  onFeaturesChange = { appFeatures = it },
                )
            }
        }
    }
}

// ==============================================================================
// GAME CONFIG FORM (stateless — all state owned by wizard)
// ==============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameConfigForm(
    workingTitle: String,      onTitleChange: (String) -> Unit,
    is3D: Boolean,             onIs3DChange: (Boolean) -> Unit,
    perspective: String,       onPerspChange: (String) -> Unit,
    artStyle: String,          onArtChange: (String) -> Unit,
    vibeLore: String,          onVibeChange: (String) -> Unit,
    features: List<FeatureNode>, onFeaturesChange: (List<FeatureNode>) -> Unit,
) {
    val perspectives = listOf("First-Person", "Third-Person", "Top-Down", "Isometric", "Side-Scroller")
    var perspExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding      = PaddingValues(bottom = 100.dp, top = 16.dp)
    ) {
        item {
            SectionHeader("WORKING TITLE")
            OutlinedTextField(
                value = workingTitle, onValueChange = onTitleChange,
                placeholder = { Text("e.g. The Low Hiss") },
                modifier    = Modifier.fillMaxWidth(),
                colors      = fieldColors(), singleLine = true
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader("DIMENSION")
                    Row(modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CARD)
                        .fillMaxWidth()
                    ) {
                        listOf(false to "2D", true to "3D").forEach { (val3D, label) ->
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clickable { onIs3DChange(val3D) }
                                    .background(if (is3D == val3D) CYAN else Color.Transparent)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label,
                                    color = if (is3D == val3D) Color.Black else TEXT,
                                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader("PERSPECTIVE")
                    ExposedDropdownMenuBox(
                        expanded = perspExpanded,
                        onExpandedChange = { perspExpanded = !perspExpanded }
                    ) {
                        OutlinedTextField(
                            value = perspective, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(perspExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors   = fieldColors()
                        )
                        ExposedDropdownMenu(perspExpanded, { perspExpanded = false }) {
                            perspectives.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { onPerspChange(opt); perspExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("ART STYLE")
            OutlinedTextField(
                value = artStyle, onValueChange = onArtChange,
                placeholder = { Text("Low-poly, gritty cyberpunk...") },
                modifier    = Modifier.fillMaxWidth().height(90.dp),
                colors      = fieldColors(), maxLines = 4
            )
        }
        item {
            SectionHeader("VIBE & LORE")
            OutlinedTextField(
                value = vibeLore, onValueChange = onVibeChange,
                placeholder = { Text("Narrative, atmosphere, lighting cues...") },
                modifier    = Modifier.fillMaxWidth().height(90.dp),
                colors      = fieldColors(), maxLines = 4
            )
        }
        item { SectionHeader("GAME SYSTEMS & MECHANICS") }
        items(features) { feature ->
            FeatureCard(feature, 0) { parentId, newSub ->
                onFeaturesChange(updateFeatureTree(features, parentId, newSub))
            }
        }
        item {
            AddRootFeatureButton { name, desc ->
                onFeaturesChange(features + FeatureNode(name = name, description = desc))
            }
        }
    }
}

// ==============================================================================
// APP CONFIG FORM (stateless)
// ==============================================================================

@Composable
fun AppConfigForm(
    workingTitle: String,        onTitleChange: (String) -> Unit,
    uiTheme: String,             onUiThemeChange: (String) -> Unit,
    features: List<FeatureNode>, onFeaturesChange: (List<FeatureNode>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding      = PaddingValues(bottom = 100.dp, top = 16.dp)
    ) {
        item {
            SectionHeader("WORKING TITLE")
            OutlinedTextField(
                value = workingTitle, onValueChange = onTitleChange,
                placeholder = { Text("e.g. CLU Pocket IDE") },
                modifier    = Modifier.fillMaxWidth(),
                colors      = fieldColors(), singleLine = true
            )
        }
        item {
            SectionHeader("UI THEME")
            OutlinedTextField(
                value = uiTheme, onValueChange = onUiThemeChange,
                placeholder = { Text("Describe the visual style...") },
                modifier    = Modifier.fillMaxWidth().height(90.dp),
                colors      = fieldColors(), maxLines = 4
            )
        }
        item { SectionHeader("APP FEATURES") }
        items(features) { feature ->
            FeatureCard(feature, 0) { parentId, newSub ->
                onFeaturesChange(updateFeatureTree(features, parentId, newSub))
            }
        }
        item {
            AddRootFeatureButton { name, desc ->
                onFeaturesChange(features + FeatureNode(name = name, description = desc))
            }
        }
    }
}

// ==============================================================================
// SHARED UI HELPERS
// ==============================================================================

@Composable
fun SectionHeader(title: String) {
    Text(
        title, color = Color.Gray, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        modifier   = Modifier.padding(bottom = 4.dp, top = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = CYAN,
    unfocusedBorderColor = Color.DarkGray,
    containerColor       = CARD,
    focusedTextColor     = TEXT,
    unfocusedTextColor   = TEXT,
    cursorColor          = CYAN,
)

@Composable
fun FeatureCard(
    feature: FeatureNode,
    depth: Int,
    onAddSubFeature: (String, FeatureNode) -> Unit,
) {
    var showAddSub by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp, bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (depth == 0) CARD else Color(0xFF111111))
            .padding(14.dp)
    ) {
        Text(feature.name, color = CYAN, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(feature.description, color = TEXT, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        feature.subFeatures.forEach { sub ->
            FeatureCard(sub, depth + 1, onAddSubFeature)
        }
        Text(
            "+ Add Sub-Feature",
            color    = CYAN.copy(alpha = 0.6f), fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { showAddSub = true }.padding(top = 4.dp)
        )
        if (showAddSub) {
            FeatureInputDialog(
                onDismiss = { showAddSub = false },
                onConfirm = { name, desc ->
                    onAddSubFeature(feature.id, FeatureNode(name = name, description = desc))
                    showAddSub = false
                }
            )
        }
    }
}

@Composable
fun AddRootFeatureButton(onAdd: (String, String) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) FeatureInputDialog({ showAdd = false }, { n, d -> onAdd(n, d); showAdd = false })
    OutlinedButton(
        onClick = { showAdd = true },
        modifier = Modifier.fillMaxWidth(),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = CYAN)
    ) {
        Icon(Icons.Default.Add, "Add Feature")
        Spacer(Modifier.width(8.dp))
        Text("Add Root Feature / System", fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun FeatureInputDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CARD,
        title = { Text("ADD FEATURE", color = CYAN, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CYAN, unfocusedBorderColor = Color.DarkGray,
                        containerColor = BG, focusedTextColor = TEXT, unfocusedTextColor = TEXT, cursorColor = CYAN
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.height(90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CYAN, unfocusedBorderColor = Color.DarkGray,
                        containerColor = BG, focusedTextColor = TEXT, unfocusedTextColor = TEXT, cursorColor = CYAN
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, desc) }) {
                Text("ADD", color = CYAN, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.Gray) }
        }
    )
}

fun updateFeatureTree(
    nodes: List<FeatureNode>,
    targetId: String,
    newChild: FeatureNode,
): List<FeatureNode> = nodes.map { node ->
    if (node.id == targetId) node.copy(subFeatures = node.subFeatures + newChild)
    else node.copy(subFeatures = updateFeatureTree(node.subFeatures, targetId, newChild))
}

private fun renderFeatureTree(nodes: List<FeatureNode>, depth: Int = 0): String = buildString {
    val indent = "  ".repeat(depth)
    nodes.forEach { n ->
        appendLine("$indent- **${n.name}**: ${n.description}")
        if (n.subFeatures.isNotEmpty()) append(renderFeatureTree(n.subFeatures, depth + 1))
    }
}
