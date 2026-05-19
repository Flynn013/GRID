package ai.grid.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

data class NodeProperty(val name: String, val type: String, val value: String)

@Composable
fun EditorScreen() {
    val selectedNode = "CharacterBody3D"
    var properties by remember {
        mutableStateOf(
            listOf(
                NodeProperty("position", "Vector3", "(0.0, 0.0, 0.0)"),
                NodeProperty("rotation", "Vector3", "(0.0, 0.0, 0.0)"),
                NodeProperty("scale",    "Vector3", "(1.0, 1.0, 1.0)"),
                NodeProperty("visible",  "bool",    "true"),
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("TWEAKER", color = CYAN, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("//", color = Color.Gray, fontSize = 14.sp)
            Text(selectedNode, color = TEXT, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider(color = CYAN.copy(alpha = 0.2f))
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(properties) { prop ->
                PropertyRow(prop) { newVal ->
                    properties = properties.map { if (it.name == prop.name) it.copy(value = newVal) else it }
                    // ai.grid.bridge.GodotBridge.setPropertyVector3(selectedNode, prop.name, ...)
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(prop: NodeProperty, onValueChange: (String) -> Unit) {
    var editValue by remember(prop.value) { mutableStateOf(prop.value) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(prop.name, color = CYAN, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(prop.type, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        OutlinedTextField(
            value = editValue,
            onValueChange = {
                editValue = it
                onValueChange(it)
            },
            modifier = Modifier.width(180.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CYAN,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = TEXT,
                unfocusedTextColor = TEXT,
                cursorColor = CYAN,
                containerColor = BG,
            ),
        )
    }
}
