package ai.grid.ui.projects

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

// ==============================================================================
// 1. DATA MODELS & ENUMS
// ==============================================================================

enum class ProjectType { APP, GAME }

data class FeatureNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val subFeatures: List<FeatureNode> = emptyList()
)

data class ProjectSummary(val id: String, val title: String, val type: ProjectType, val status: String)

// ==============================================================================
// 2. MAIN SCREEN ROUTER
// ==============================================================================

@Composable
fun ProjectsScreen(
    onNavigateToSession: (String) -> Unit
) {
    var isCreating by remember { mutableStateOf(false) }

    val bgColor    = Color(0xFF121212)
    val cyanAccent = Color(0xFF00E5FF)

    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
        if (isCreating) {
            ProjectCreationWizard(
                onCancel = { isCreating = false },
                onGenerate = {
                    isCreating = false
                    onNavigateToSession("new_session_id")
                }
            )
        } else {
            ProjectDashboard(
                onNewProjectClick = { isCreating = true },
                onProjectClick = { projectId -> onNavigateToSession(projectId) },
                cyanAccent = cyanAccent,
                bgColor = bgColor
            )
        }
    }
}

// ==============================================================================
// 3. DASHBOARD
// ==============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDashboard(
    onNewProjectClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    cyanAccent: Color,
    bgColor: Color
) {
    val mockProjects = listOf(
        ProjectSummary("1", "The Low Hiss",  ProjectType.GAME, "IN PROGRESS"),
        ProjectSummary("2", "CLU Core Shell", ProjectType.APP,  "DONE")
    )

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewProjectClick,
                containerColor = cyanAccent,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "THE FORGE",
                    color = cyanAccent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            items(mockProjects) { project ->
                Card(
                    onClick = { onProjectClick(project.id) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(project.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (project.type == ProjectType.GAME) Icons.Default.SportsEsports else Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(project.type.name, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Badge(
                            containerColor = if (project.status == "DONE") Color(0xFF00C853) else Color(0xFFFFAB00)
                        ) {
                            Text(
                                project.status,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 4. CREATION WIZARD
// ==============================================================================

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjectCreationWizard(
    onCancel: () -> Unit,
    onGenerate: (Any) -> Unit
) {
    var selectedType by remember { mutableStateOf(ProjectType.APP) }

    val bgColor   = Color(0xFF121212)
    val cardColor = Color(0xFF1E1E1E)
    val cyanAccent = Color(0xFF00E5FF)
    val textColor  = Color(0xFFE0E0E0)

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cardColor),
                    ) {
                        ProjectType.values().forEach { type ->
                            val isSelected = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedType = type }
                                    .background(if (isSelected) cyanAccent else Color.Transparent)
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.name,
                                    color = if (isSelected) Color.Black else textColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel", tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onGenerate(selectedType) },
                containerColor = cyanAccent,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Check, "Generate")
                Spacer(Modifier.width(8.dp))
                Text("Generate GDD Session", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedType,
            modifier = Modifier.padding(padding)
        ) { targetType ->
            when (targetType) {
                ProjectType.APP  -> AppConfigForm(cyanAccent, cardColor, textColor, bgColor)
                ProjectType.GAME -> GameConfigForm(cyanAccent, cardColor, textColor, bgColor)
            }
        }
    }
}

// ==============================================================================
// 5. GAME CONFIG FORM
// ==============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameConfigForm(cyanAccent: Color, cardColor: Color, textColor: Color, bgColor: Color) {
    var workingTitle by remember { mutableStateOf("") }
    var is3D by remember { mutableStateOf(true) }
    var perspective by remember { mutableStateOf("Third-Person") }
    var artStyle by remember { mutableStateOf("") }
    var vibeLore by remember { mutableStateOf("") }
    var features by remember { mutableStateOf(listOf<FeatureNode>()) }
    val perspectives = listOf("First-Person", "Third-Person", "Top-Down", "Isometric", "Side-Scroller")
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            SectionHeader("WORKING TITLE")
            OutlinedTextField(
                value = workingTitle, onValueChange = { workingTitle = it },
                placeholder = { Text("e.g. The Low Hiss") }, modifier = Modifier.fillMaxWidth(),
                colors = cluTextFieldColors(cyanAccent, cardColor, textColor), singleLine = true
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader("DIMENSION")
                    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cardColor).fillMaxWidth()) {
                        listOf(false to "2D", true to "3D").forEach { (val3D, label) ->
                            Box(
                                modifier = Modifier.weight(1f).clickable { is3D = val3D }
                                    .background(if (is3D == val3D) cyanAccent else Color.Transparent)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (is3D == val3D) Color.Black else textColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader("PERSPECTIVE")
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = perspective, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = cluTextFieldColors(cyanAccent, cardColor, textColor)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            perspectives.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = { perspective = option; expanded = false }
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
                value = artStyle, onValueChange = { artStyle = it },
                placeholder = { Text("e.g. Low-poly, gritty cyberpunk...") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = cluTextFieldColors(cyanAccent, cardColor, textColor), maxLines = 4
            )
        }
        item {
            SectionHeader("VIBE & LORE")
            OutlinedTextField(
                value = vibeLore, onValueChange = { vibeLore = it },
                placeholder = { Text("Narrative, atmosphere, lighting cues...") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = cluTextFieldColors(cyanAccent, cardColor, textColor), maxLines = 4
            )
        }
        item { SectionHeader("GAME SYSTEMS & MECHANICS") }
        items(features) { feature ->
            FeatureCard(feature, 0, cyanAccent, cardColor, textColor) { parentId, newSub ->
                features = updateFeatureTree(features, parentId, newSub)
            }
        }
        item {
            AddRootFeatureButton(cyanAccent) { name, desc ->
                features = features + FeatureNode(name = name, description = desc)
            }
        }
    }
}

// ==============================================================================
// 6. APP CONFIG FORM
// ==============================================================================

@Composable
fun AppConfigForm(cyanAccent: Color, cardColor: Color, textColor: Color, bgColor: Color) {
    var workingTitle by remember { mutableStateOf("") }
    var uiTheme by remember { mutableStateOf("") }
    var features by remember { mutableStateOf(listOf<FeatureNode>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            SectionHeader("WORKING TITLE")
            OutlinedTextField(
                value = workingTitle, onValueChange = { workingTitle = it },
                placeholder = { Text("e.g. CLU Pocket IDE") }, modifier = Modifier.fillMaxWidth(),
                colors = cluTextFieldColors(cyanAccent, cardColor, textColor), singleLine = true
            )
        }
        item {
            SectionHeader("UI THEME")
            OutlinedTextField(
                value = uiTheme, onValueChange = { uiTheme = it },
                placeholder = { Text("Describe the visual style...") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = cluTextFieldColors(cyanAccent, cardColor, textColor), maxLines = 4
            )
        }
        item { SectionHeader("APP FEATURES") }
        items(features) { feature ->
            FeatureCard(feature, 0, cyanAccent, cardColor, textColor) { parentId, newSub ->
                features = updateFeatureTree(features, parentId, newSub)
            }
        }
        item {
            AddRootFeatureButton(cyanAccent) { name, desc ->
                features = features + FeatureNode(name = name, description = desc)
            }
        }
    }
}

// ==============================================================================
// 7. SHARED UI HELPERS
// ==============================================================================

@Composable
fun SectionHeader(title: String) {
    Text(title, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun cluTextFieldColors(accent: Color, bg: Color, text: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = Color.DarkGray,
    containerColor = bg,
    focusedTextColor = text,
    unfocusedTextColor = text,
    cursorColor = accent
)

@Composable
fun FeatureCard(
    feature: FeatureNode,
    depth: Int,
    cyanAccent: Color,
    cardColor: Color,
    textColor: Color,
    onAddSubFeature: (String, FeatureNode) -> Unit
) {
    var showAddSub by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .padding(16.dp)
    ) {
        Text(feature.name, color = cyanAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(feature.description, color = textColor, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        feature.subFeatures.forEach { sub ->
            FeatureCard(sub, depth + 1, cyanAccent, Color(0xFF121212), textColor, onAddSubFeature)
        }
        Text(
            "+ Add Sub-Feature",
            color = cyanAccent.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { showAddSub = true }.padding(top = 8.dp)
        )
        if (showAddSub) {
            FeatureInputDialog(
                { showAddSub = false },
                { name, desc -> onAddSubFeature(feature.id, FeatureNode(name = name, description = desc)); showAddSub = false }
            )
        }
    }
}

@Composable
fun AddRootFeatureButton(cyanAccent: Color, onAdd: (String, String) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) FeatureInputDialog({ showAdd = false }, { n, d -> onAdd(n, d); showAdd = false })
    OutlinedButton(
        onClick = { showAdd = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cyanAccent)
    ) {
        Icon(Icons.Default.Add, "Add Feature")
        Spacer(Modifier.width(8.dp))
        Text("Add Root Feature / System")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureInputDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = { Text("Add Feature", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    colors = cluTextFieldColors(Color(0xFF00E5FF), Color(0xFF121212), Color.White)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Description") },
                    colors = cluTextFieldColors(Color(0xFF00E5FF), Color(0xFF121212), Color.White),
                    modifier = Modifier.height(100.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, desc) }) {
                Text("Add", color = Color(0xFF00E5FF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

fun updateFeatureTree(nodes: List<FeatureNode>, targetId: String, newChild: FeatureNode): List<FeatureNode> {
    return nodes.map { node ->
        if (node.id == targetId) node.copy(subFeatures = node.subFeatures + newChild)
        else node.copy(subFeatures = updateFeatureTree(node.subFeatures, targetId, newChild))
    }
}
