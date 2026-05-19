package ai.grid.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG   = Color(0xFF0A0A0A)
private val CYAN = Color(0xFF00E5FF)
private val CARD = Color(0xFF141414)
private val TEXT = Color(0xFFE0E0E0)

data class FileEntry(val name: String, val isDir: Boolean, val size: String = "")

@Composable
fun FilesScreen() {
    val currentPath = "/data/user/0/ai.grid/projects"
    val entries = remember {
        listOf(
            FileEntry("my_game", true),
            FileEntry("my_game/scene.tscn", false, "4.2 KB"),
            FileEntry("my_game/assets", true),
            FileEntry("my_game/assets/player.glb", false, "1.2 MB"),
            FileEntry("my_game/project.godot", false, "1.1 KB"),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("SUBSTRATE", color = CYAN, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(currentPath, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = { /* file picker */ }) {
                Icon(Icons.Default.FileUpload, "Import", tint = CYAN)
            }
        }
        HorizontalDivider(color = CYAN.copy(alpha = 0.2f))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(entries) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (entry.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        null,
                        tint = if (entry.isDir) CYAN else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.name.substringAfterLast("/"),
                            color = TEXT,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (entry.size.isNotEmpty()) {
                            Text(entry.size, color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    if (entry.name.endsWith(".glb")) {
                        OutlinedButton(
                            onClick = { /* ai.grid.bridge.GodotBridge.loadAsset(entry.name) */ },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CYAN),
                        ) { Text("LOAD", fontSize = 10.sp) }
                    }
                }
                HorizontalDivider(color = CARD)
            }
        }
    }
}
