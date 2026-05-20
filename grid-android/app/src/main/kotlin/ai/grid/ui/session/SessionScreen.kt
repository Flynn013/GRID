package ai.grid.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.grid.agent.CLUAgent
import ai.grid.agent.Message
import ai.grid.agent.Role
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.Material3RichText
import kotlinx.coroutines.launch

private val BG      = Color(0xFF0A0A0A)
private val CYAN    = Color(0xFF00E5FF)
private val CARD    = Color(0xFF141414)
private val TEXT    = Color(0xFFE0E0E0)
private val TOOL_BG = Color(0xFF0D1F1F)

@Composable
fun SessionScreen(
    onNavigateToStage: () -> Unit,
    agent: CLUAgent = viewModel(),
) {
    val messages   by agent.messages.collectAsState()
    val isThinking by agent.isThinking.collectAsState()
    val activeTask by agent.activeTask.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "GRID // SESSION",
                    color = CYAN, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (activeTask != null) {
                    Text(
                        activeTask!!, color = Color.Gray,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onNavigateToStage) {
                    Icon(Icons.Default.SportsEsports, "Open Stage", tint = CYAN)
                }
                if (isThinking) {
                    CircularProgressIndicator(
                        color = CYAN,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        HorizontalDivider(color = CYAN.copy(alpha = 0.2f))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CARD)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Command CLU...",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = CYAN,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor     = TEXT,
                    unfocusedTextColor   = TEXT,
                    cursorColor          = CYAN,
                    containerColor       = BG,
                ),
                shape = RoundedCornerShape(8.dp),
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank() && !isThinking) {
                        val text = input.trim()
                        input = ""
                        scope.launch { agent.send(text) }
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = CYAN),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Send, "Send", tint = Color.Black)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val isUser = msg.role == Role.USER
    val isTool = msg.role == Role.TOOL

    when {
        isTool -> ToolBubble(msg.content)
        isUser -> UserBubble(msg.content)
        else   -> AssistantBubble(msg.content)
    }
}

@Composable
private fun UserBubble(content: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        SelectionContainer {
            Text(
                content,
                color = Color(0xFF80FFFF),
                fontSize = 14.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0xFF1A2A2A))
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun AssistantBubble(content: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(Color(0xFF141414))
                .padding(12.dp)
        ) {
            Text(
                "CLU",
                color = CYAN, fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            SelectionContainer {
                Material3RichText {
                    Markdown(content)
                }
            }
        }
    }
}

@Composable
private fun ToolBubble(content: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        SelectionContainer {
            Text(
                content,
                color = CYAN.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TOOL_BG)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
