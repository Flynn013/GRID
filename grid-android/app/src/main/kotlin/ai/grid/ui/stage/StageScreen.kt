package ai.grid.ui.stage

import ai.grid.bridge.GodotBridge
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val CYAN = Color(0xFF00E5FF)
private val BG   = Color(0xFF000000)

@Composable
fun StageScreen(projectPath: String? = null) {
    val context              = LocalContext.current
    val scope                = rememberCoroutineScope()
    var isCLUOverlayVisible  by remember { mutableStateOf(false) }
    var lastSnapshotId       by remember { mutableStateOf(-1) }
    var showRevertConfirm    by remember { mutableStateOf(false) }

    // Revert confirmation dialog
    if (showRevertConfirm) {
        AlertDialog(
            onDismissRequest = { showRevertConfirm = false },
            containerColor   = Color(0xFF141414),
            title = {
                Text("REVERT SCENE?", color = CYAN,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            },
            text  = {
                Text(
                    "Restores your last snapshot.\nUnsaved changes will be lost.",
                    color = Color.Gray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRevertConfirm = false
                    scope.launch(Dispatchers.IO) { GodotBridge.revertToSnapshot(lastSnapshotId) }
                }) {
                    Text("REVERT", color = Color(0xFFFF5252),
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevertConfirm = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BG)) {

        if (projectPath == null) {
            // ── No project placeholder ─────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "NO PROJECT LOADED",
                        color = CYAN, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Go to FORGE → create or open a project",
                        color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                    )
                }
            }
        } else {
            // ── Godot render surface ──────────────────────────────────
            // GodotFragment is guarded with ClassNotFoundException for UI-stub dev mode.
            AndroidView(
                factory = { ctx -> FrameLayout(ctx).apply { id = View.generateViewId() } },
                modifier = Modifier.fillMaxSize(),
            ) { frameLayout ->
                val activity = context as? FragmentActivity ?: return@AndroidView
                val fm = activity.supportFragmentManager
                if (fm.findFragmentByTag("godot") != null) return@AndroidView
                try {
                    val cls = Class.forName("org.godotengine.godot.GodotFragment")
                    val fragment = cls.getDeclaredConstructor().newInstance()
                            as androidx.fragment.app.Fragment
                    fragment.arguments = Bundle().apply {
                        putStringArray("args", arrayOf("--path", projectPath))
                    }
                    fm.commit { add(frameLayout.id, fragment, "godot") }
                } catch (_: ClassNotFoundException) {}
            }
        }

        // ── Revert FAB (only when snapshot exists) ────────────────────────
        if (lastSnapshotId >= 0) {
            FloatingActionButton(
                onClick = { showRevertConfirm = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .navigationBarsPadding(),
                containerColor = Color(0xFFFF5252),
                contentColor   = Color.White,
            ) {
                Icon(Icons.Default.Undo, "Revert")
            }
        }

        // ── CLU toggle FAB ────────────────────────────────────────────
        FloatingActionButton(
            onClick = { isCLUOverlayVisible = !isCLUOverlayVisible },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .navigationBarsPadding(),
            containerColor = CYAN,
            contentColor   = Color.Black,
            shape          = CircleShape,
        ) {
            Icon(
                if (isCLUOverlayVisible) Icons.Default.Close else Icons.Default.Psychology,
                "CLU"
            )
        }

        // ── Quick CLU panel ─────────────────────────────────────────
        AnimatedVisibility(
            visible  = isCLUOverlayVisible,
            enter    = slideInVertically(initialOffsetY = { it }),
            exit     = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            StageQuickCLUPanel(
                onDismiss    = { isCLUOverlayVisible = false },
                onSpawnLight = {
                    scope.launch(Dispatchers.IO) {
                        GodotBridge.spawnNode("DirectionalLight3D", "/root")
                    }
                },
                onSpawnCube  = {
                    scope.launch(Dispatchers.IO) {
                        GodotBridge.spawnNode("MeshInstance3D", "/root")
                    }
                },
                onSnapshot   = {
                    scope.launch(Dispatchers.IO) {
                        val id = GodotBridge.createSnapshot()
                        if (id >= 0) lastSnapshotId = id
                    }
                },
            )
        }
    }

    BackHandler(enabled = isCLUOverlayVisible) { isCLUOverlayVisible = false }
}

@Composable
private fun StageQuickCLUPanel(
    onDismiss: () -> Unit,
    onSpawnLight: () -> Unit,
    onSpawnCube: () -> Unit,
    onSnapshot: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        color         = Color(0xE0141414),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "STAGE INJECT",
                    color = CYAN, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, fontSize = 14.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.Gray)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "Spawn Light" to onSpawnLight,
                    "Spawn Cube"  to onSpawnCube,
                    "Snapshot"    to onSnapshot,
                ).forEach { (label, action) ->
                    OutlinedButton(
                        onClick  = action,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = CYAN),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Text(
                "Scene live — CLU sees the active SceneTree via FFI.",
                color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}
