package ai.grid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CYAN    = Color(0xFF00E5FF)
private val SURFACE = Color(0xFF141414)
private val BG      = Color(0xFF0A0A0A)

private val GRIDColors = darkColorScheme(
    primary          = CYAN,
    onPrimary        = Color.Black,
    primaryContainer = CYAN.copy(alpha = 0.15f),
    secondary        = Color(0xFF00BCD4),
    background       = BG,
    surface          = SURFACE,
    onBackground     = Color(0xFFE0E0E0),
    onSurface        = Color(0xFFE0E0E0),
    error            = Color(0xFFFF5252),
)

@Composable
fun GRIDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GRIDColors,
        content = content,
    )
}
