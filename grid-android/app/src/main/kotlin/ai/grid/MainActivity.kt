package ai.grid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ai.grid.nav.GRIDNavGraph
import ai.grid.ui.theme.GRIDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GRIDTheme {
                GRIDNavGraph(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
