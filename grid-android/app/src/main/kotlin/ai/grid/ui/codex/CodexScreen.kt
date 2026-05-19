package ai.grid.ui.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG   = Color(0xFF0A0A0A)
private val CYAN = Color(0xFF00E5FF)

@Composable
fun CodexScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding(),
    ) {
        Text(
            "BRAIN GRAPH",
            color = CYAN,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = CYAN.copy(alpha = 0.2f))

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("NODE GRAPH VISUALIZER", color = Color.Gray, fontFamily = FontFamily.Monospace)
                Text(
                    "File dependency graph + RAG memory contexts",
                    color = Color.DarkGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "[ SPRINT 2 ]",
                    color = CYAN.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
