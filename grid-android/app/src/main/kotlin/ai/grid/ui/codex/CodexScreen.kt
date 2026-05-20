package ai.grid.ui.codex

import ai.grid.data.Project
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
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

    // Parse sprint entries from GDD (everything after "## Sprint Log")
    val sprints: List<Pair<String, String>> = remember(gdd) {
        gdd?.substringAfter("## Sprint Log", "")?.trim()
            ?.split("### Sprint — ")?.drop(1)
            ?.map { chunk ->
                val lines = chunk.lines()
                val header = lines.firstOrNull()?.trim() ?: ""
                val body   = lines.drop(1).joinToString("\n").trim()
                header to body
            } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
    ) {
        // ── Header ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "BRAIN GRAPH",
                    color = CYAN, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
                if (activeProject != null) {
                    Text(
                        activeProject.name,
                        color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (activeProject == null) {
                Text(
                    "OPEN A PROJECT IN FORGE",
                    color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
        HorizontalDivider(color = CYAN.copy(alpha = 0.2f))

        if (activeProject == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description, null,
                        tint = CYAN.copy(alpha = 0.25f), modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("GDD / SPRINT LOG", color = Color.Gray, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "No project open. Go to FORGE → open a project.",
                        color = Color.DarkGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            // ── Tabs ───────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = CARD,
                contentColor     = CYAN,
                indicator        = { tabPos ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPos[selectedTab]),
                        color    = CYAN
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    icon     = { Icon(Icons.Default.Description, null) },
                    text     = { Text("GDD",    fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    icon     = { Icon(Icons.Default.History, null) },
                    text     = { Text("SPRINTS", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                )
            }

            when (selectedTab) {
                0 -> GddTab(gdd)
                1 -> SprintsTab(
                    sprints      = sprints,
                    sprintNote   = sprintNote,
                    onNoteChange = { sprintNote = it },
                    isCommitting = isCommitting,
                    onCommit     = {
                        val note = sprintNote.trim()
                        if (note.isBlank()) return@SprintsTab
                        scope.launch {
                            isCommitting = true
                            withContext(Dispatchers.IO) {
                                val gddFile = File(activeProject.gddPath)
                                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                                gddFile.appendText("\n\n### Sprint — $ts\n$note\n")
                                try {
                                    val git = Git.open(File(activeProject.path))
                                    git.add().addFilepattern("GDD_MASTER.md").call()
                                    git.commit()
                                        .setAuthor("GRID", "grid@local")
                                        .setCommitter("GRID", "grid@local")
                                        .setMessage("sprint: $ts")
                                        .call()
                                    git.close()
                                } catch (_: Exception) {}
                            }
                            sprintNote = ""
                            refreshKey++
                            isCommitting = false
                        }
                    }
                )
            }
        }
    }
}

// ── GDD tab ──────────────────────────────────────────────────────────

@Composable
private fun GddTab(gdd: String?) {
    if (gdd == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(32.dp))
        }
        return
    }
    SelectionContainer {
        Text(
            text       = gdd,
            color      = TEXT,
            fontSize   = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
            modifier   = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

// ── Sprints tab ─────────────────────────────────────────────────────

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
        // Input card at top
        item {
            Surface(
                color  = CARD,
                shape  = RoundedCornerShape(10.dp),
                tonalElevation = 2.dp,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "NEW SPRINT NOTE",
                        color = CYAN, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value            = sprintNote,
                        onValueChange    = onNoteChange,
                        modifier         = Modifier.fillMaxWidth().height(120.dp),
                        placeholder      = { Text("What was built? What’s next? Open issues?", color = Color.Gray) },
                        colors           = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = CYAN,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor     = TEXT,
                            unfocusedTextColor   = TEXT,
                            cursorColor          = CYAN,
                            containerColor       = BG,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick  = onCommit,
                        enabled  = sprintNote.isNotBlank() && !isCommitting,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = CYAN, contentColor = Color.Black
                        )
                    ) {
                        if (isCommitting) {
                            CircularProgressIndicator(
                                color    = Color.Black,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Commit, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "COMMIT SPRINT",
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        if (sprints.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No sprint notes yet.",
                        color = Color.DarkGray, fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            item {
                Text(
                    "SPRINT HISTORY (${sprints.size})",
                    color = Color.Gray, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                )
            }
            items(sprints.reversed()) { (timestamp, body) ->
                Surface(
                    color  = CARD,
                    shape  = RoundedCornerShape(8.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            timestamp,
                            color = CYAN, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                        )
                        if (body.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    body, color = TEXT, fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace, lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
