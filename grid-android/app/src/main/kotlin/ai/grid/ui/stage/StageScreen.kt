package ai.grid.ui.stage

import android.widget.FrameLayout
import android.view.View
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

private val CYAN = Color(0xFF00E5FF)
private val BG   = Color(0xFF000000)

@Composable
fun StageScreen() {
    val context = LocalContext.current
    var isCLUOverlayVisible by remember { mutableStateOf(false) }
    var snapshotExists by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
    ) {
        // Godot render surface
        // GodotFragment lives inside godot-lib.aar; guarded with try/catch for UI-stub dev mode.
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply { id = View.generateViewId() }
            },
            modifier = Modifier.fillMaxSize(),
        ) { frameLayout ->
            val activity = context as? FragmentActivity ?: return@AndroidView
            val fm = activity.supportFragmentManager
            if (fm.findFragmentByTag("godot") == null) {
                try {
                    val cls = Class.forName("org.godotengine.godot.GodotFragment")
                    val fragment = cls.getDeclaredConstructor().newInstance()
                            as androidx.fragment.app.Fragment
                    fm.commit { add(frameLayout.id, fragment, "godot") }
                } catch (_: ClassNotFoundException) {
                    // godot-lib.aar not compiled yet — UI-stub mode
                }
            }
        }

        // Revert button — appears once a snapshot exists
        if (snapshotExists) {
            FloatingActionButton(
                onClick = { /* ai.grid.bridge.GodotBridge.revertToLastSnapshot() */ },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .navigationBarsPadding(),
                containerColor = Color(0xFFFF5252),
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Undo, "Revert")
            }
        }

        // Floating CLU button
        FloatingActionButton(
            onClick = { isCLUOverlayVisible = !isCLUOverlayVisible },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .navigationBarsPadding(),
            containerColor = CYAN,
            contentColor = Color.Black,
            shape = CircleShape,
        ) {
            Icon(
                if (isCLUOverlayVisible) Icons.Default.Close else Icons.Default.Psychology,
                "CLU"
            )
        }

        AnimatedVisibility(
            visible = isCLUOverlayVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            StageQuickCLUPanel(
                onDismiss = { isCLUOverlayVisible = false },
                onSnapshot = { snapshotExists = true },
            )
        }
    }

    BackHandler(enabled = isCLUOverlayVisible) { isCLUOverlayVisible = false }
}

@Composable
private fun StageQuickCLUPanel(onDismiss: () -> Unit, onSnapshot: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        color = Color(0xE0141414),
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
                    color = CYAN,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.Gray)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Spawn Light", "Spawn Cube", "Save Snapshot").forEach { label ->
                    OutlinedButton(
                        onClick = {
                            // ai.grid.bridge.GodotBridge.<action>()
                            if (label == "Save Snapshot") onSnapshot()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CYAN),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, fontSize = 10.sp, maxLines = 2)
                    }
                }
            }
            Text(
                "Scene live — CLU sees the active SceneTree via FFI.",
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
