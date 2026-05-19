package ai.grid.ui.files

import ai.grid.bridge.GodotBridge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private val BG   = Color(0xFF0A0A0A)
private val CYAN = Color(0xFF00E5FF)
private val CARD = Color(0xFF141414)
private val TEXT = Color(0xFFE0E0E0)

@Composable
fun FilesScreen(vm: FilesViewModel = viewModel()) {
    val currentDir by vm.currentDir.collectAsState()
    val entries    by vm.entries.collectAsState()
    val scope      = rememberCoroutineScope()
    var showNewFolder by remember { mutableStateOf(false) }
    var folderName   by remember { mutableStateOf("") }

    if (showNewFolder) {
        NewFolderDialog(
            value   = folderName,
            onValue = { folderName = it },
            onDismiss = { showNewFolder = false; folderName = "" },
            onConfirm = {
                vm.createFolder(folderName)
                showNewFolder = false
                folderName = ""
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BG)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Header ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SUBSTRATE",
                    color = CYAN, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Default.Refresh, null, tint = CYAN)
                }
            }

            // ── Breadcrumb ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CARD)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (vm.canGoUp) {
                    IconButton(
                        onClick = { vm.navigateUp() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack, null,
                            tint = CYAN, modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    vm.relativePath(currentDir).ifBlank { "/" },
                    color = Color.Gray, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = CYAN.copy(alpha = 0.15f))

            // ── Entries ─────────────────────────────────────────────
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Empty directory",
                        color = Color.DarkGray, fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(entries, key = { it.absolutePath }) { file ->
                        FileRow(
                            file = file,
                            onOpen = {
                                if (file.isDirectory) vm.navigateTo(file)
                            },
                            onLoad = if (file.name.endsWith(".glb", ignoreCase = true)) {
                                {
                                    scope.launch(Dispatchers.IO) {
                                        GodotBridge.loadAsset(file.absolutePath)
                                    }
                                }
                            } else null
                        )
                    }
                }
            }
        }

        // ── FAB ─────────────────────────────────────────────────
        FloatingActionButton(
            onClick  = { showNewFolder = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding(),
            containerColor = CYAN,
            contentColor   = Color.Black
        ) {
            Icon(Icons.Default.CreateNewFolder, "New folder")
        }
    }
}

@Composable
private fun FileRow(
    file: File,
    onOpen: () -> Unit,
    onLoad: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = file.isDirectory, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (file.isDirectory) Icons.Default.FolderOpen else Icons.Default.InsertDriveFile,
            null,
            tint = if (file.isDirectory) Color(0xFF00E5FF) else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                color = if (file.isDirectory) Color(0xFFE0E0E0) else Color.Gray,
                fontSize = 13.sp, fontFamily = FontFamily.Monospace
            )
            if (!file.isDirectory) {
                Text(
                    formatSize(file.length()),
                    color = Color.DarkGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
        if (onLoad != null) {
            OutlinedButton(
                onClick = onLoad,
                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    "LOAD",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    HorizontalDivider(color = Color(0xFF1A1A1A))
}

@Composable
private fun NewFolderDialog(
    value: String,
    onValue: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF141414),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "NEW FOLDER",
                    color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValue,
                    singleLine = true,
                    placeholder = { Text("folder-name", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor     = Color(0xFFE0E0E0),
                        unfocusedTextColor   = Color(0xFFE0E0E0),
                        cursorColor          = Color(0xFF00E5FF),
                        containerColor       = Color(0xFF0A0A0A),
                    )
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = value.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor   = Color.Black
                        )
                    ) {
                        Text("CREATE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1_024L         -> "$bytes B"
    bytes < 1_048_576L     -> "${bytes / 1_024} KB"
    bytes < 1_073_741_824L -> "${bytes / 1_048_576} MB"
    else                   -> "${bytes / 1_073_741_824} GB"
}
