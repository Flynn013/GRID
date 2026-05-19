package ai.grid.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val BG   = Color(0xFF0A0A0A)
private val CYAN = Color(0xFF00E5FF)
private val CARD = Color(0xFF141414)
private val TEXT = Color(0xFFE0E0E0)

@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    val nodes      by vm.nodes.collectAsState()
    val selected   by vm.selected.collectAsState()
    val isFetching by vm.isFetching.collectAsState()
    val activeProp by vm.activeProp.collectAsState()
    val propX      by vm.propX.collectAsState()
    val propY      by vm.propY.collectAsState()
    val propZ      by vm.propZ.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
    ) {
        // ── Header ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TWEAKER",
                color = CYAN, fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFetching) {
                    CircularProgressIndicator(
                        color = CYAN,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "${nodes.size} nodes",
                        color = Color.Gray, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                OutlinedButton(
                    onClick = { vm.refresh() },
                    border = BorderStroke(1.dp, CYAN),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("SYNC", color = CYAN, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        HorizontalDivider(color = CYAN.copy(alpha = 0.2f))

        // ── Scene tree ─────────────────────────────────────────────
        if (nodes.isEmpty() && !isFetching) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AccountTree, null,
                        tint = CYAN.copy(alpha = 0.25f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "NO SCENE LOADED",
                        color = Color.Gray, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Navigate to STAGE, then tap SYNC",
                        color = Color.DarkGray, fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(nodes, key = { it.path.ifBlank { it.name } }) { node ->
                    val isSelected = selected == node
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) CYAN.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .clickable { vm.selectNode(node) }
                            .padding(
                                start = (16 + node.depth * 14).dp,
                                end = 16.dp, top = 5.dp, bottom = 5.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (node.childCount > 0) Icons.Default.ChevronRight
                            else Icons.Default.FiberManualRecord,
                            null,
                            tint = if (isSelected) CYAN else Color.DarkGray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                node.name,
                                color = if (isSelected) CYAN else TEXT,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                            Text(
                                node.className,
                                color = Color.DarkGray, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // ── Property panel ────────────────────────────────────────
        if (selected != null) {
            HorizontalDivider(color = CYAN.copy(alpha = 0.3f))
            Column(
                modifier = Modifier
                    .background(CARD)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    selected!!.name,
                    color = CYAN, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
                Text(
                    selected!!.path.ifBlank { "(root)" },
                    color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(10.dp))

                // Property tabs
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("position", "rotation", "scale").forEach { prop ->
                        val sel = activeProp == prop
                        OutlinedButton(
                            onClick = { vm.selectProp(prop) },
                            border = BorderStroke(1.dp, if (sel) CYAN else Color.DarkGray),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                prop.uppercase(),
                                color = if (sel) CYAN else Color.Gray,
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // XYZ inputs
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("X", propX) { v: String -> vm.setX(v) },
                        Triple("Y", propY) { v: String -> vm.setY(v) },
                        Triple("Z", propZ) { v: String -> vm.setZ(v) },
                    ).forEach { (label, value, onEdit) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label, color = Color.Gray, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            OutlinedTextField(
                                value = value,
                                onValueChange = onEdit,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = CYAN,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedTextColor     = TEXT,
                                    unfocusedTextColor   = TEXT,
                                    cursorColor          = CYAN,
                                    containerColor       = BG,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 12.sp
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.applyProperty() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CYAN, contentColor = Color.Black
                    )
                ) {
                    Text(
                        "APPLY ${activeProp.uppercase()}",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
