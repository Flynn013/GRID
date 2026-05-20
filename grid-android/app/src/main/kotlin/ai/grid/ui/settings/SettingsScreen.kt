package ai.grid.ui.settings

import ai.grid.data.LLMProvider
import ai.grid.data.SettingsData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BG    = Color(0xFF0A0A0A)
private val CYAN  = Color(0xFF00E5FF)
private val CARD  = Color(0xFF141414)
private val TEXT  = Color(0xFFE0E0E0)
private val GREEN = Color(0xFF00C853)

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val ctx     = LocalContext.current
    val current by vm.settings.collectAsState()
    val oauthState by vm.oauthState.collectAsState()
    val oauthError by vm.oauthError.collectAsState()

    var activeProvider  by remember(current.activeProvider) {
        mutableStateOf(
            runCatching { LLMProvider.valueOf(current.activeProvider) }
                .getOrDefault(LLMProvider.ANTHROPIC)
        )
    }
    var anthropicKey    by remember(current.anthropicKey) { mutableStateOf(current.anthropicKey) }
    var geminiKey       by remember(current.geminiKey)    { mutableStateOf(current.geminiKey) }
    var liteRtPath      by remember(current.liteRtPath)   { mutableStateOf(current.liteRtPath) }
    var anthropicVisible by remember { mutableStateOf(false) }
    var geminiVisible    by remember { mutableStateOf(false) }
    var showSaved        by remember { mutableStateOf(false) }
    var oauthCode        by remember { mutableStateOf("") }

    LaunchedEffect(showSaved) {
        if (showSaved) { delay(2000); showSaved = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .statusBarsPadding()
    ) {
        Text(
            "VENDING MACHINE",
            color = CYAN, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = CYAN.copy(alpha = 0.2f))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Provider selector ────────────────────────────────────────────
            item {
                SectionLabel("LLM PROVIDER")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CARD, RoundedCornerShape(8.dp))
                ) {
                    LLMProvider.entries.forEach { provider ->
                        val selected = activeProvider == provider
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) CYAN else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = { activeProvider = provider }) {
                                Text(
                                    provider.displayLabel,
                                    color = if (selected) Color.Black else TEXT,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }

            // ── Anthropic API key (shown only for ANTHROPIC provider) ────────
            if (activeProvider == LLMProvider.ANTHROPIC) {
                item {
                    SectionLabel("ANTHROPIC API KEY")
                    OutlinedTextField(
                        value = anthropicKey,
                        onValueChange = { anthropicKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("sk-ant-...", color = Color.Gray) },
                        trailingIcon = {
                            IconButton(onClick = { anthropicVisible = !anthropicVisible }) {
                                Icon(
                                    if (anthropicVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    null, tint = Color.Gray
                                )
                            }
                        },
                        visualTransformation = if (anthropicVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        colors = fieldColors(),
                    )
                }
            }

            // ── Anthropic OAuth (CLAUDE provider) ────────────────────────────
            if (activeProvider == LLMProvider.ANTHROPIC_OAUTH) {
                item {
                    SectionLabel("ANTHROPIC ACCOUNT")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CARD, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (oauthState) {
                            OAuthUiState.SIGNED_IN -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("✓  Signed in", color = GREEN, fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { vm.oauthSignOut(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("SIGN OUT", color = Color.Gray, fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace)
                                }
                            }

                            OAuthUiState.IDLE -> {
                                Text(
                                    "Sign in with your Anthropic account (Claude Pro / Max).\n" +
                                    "You will be redirected to claude.ai to log in, then shown a one-time code to paste back here.",
                                    color = TEXT.copy(alpha = 0.7f), fontSize = 12.sp
                                )
                                Button(
                                    onClick = { vm.startOAuthFlow(ctx) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CYAN, contentColor = Color.Black
                                    )
                                ) {
                                    Text("SIGN IN WITH ANTHROPIC",
                                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }

                            OAuthUiState.AWAITING_CODE, OAuthUiState.ERROR -> {
                                Text(
                                    "After signing in, copy the one-time code from the Anthropic page and paste it below.",
                                    color = TEXT.copy(alpha = 0.7f), fontSize = 12.sp
                                )
                                OutlinedTextField(
                                    value = oauthCode,
                                    onValueChange = { oauthCode = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Paste code here…", color = Color.Gray) },
                                    colors = fieldColors(),
                                )
                                if (oauthError != null) {
                                    Text(oauthError!!, color = Color.Red, fontSize = 11.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { vm.startOAuthFlow(ctx); oauthCode = "" },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CYAN)
                                    ) {
                                        Text("REOPEN BROWSER", fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace)
                                    }
                                    Button(
                                        onClick = { vm.submitAuthCode(ctx, oauthCode) },
                                        enabled = oauthCode.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CYAN, contentColor = Color.Black
                                        )
                                    ) {
                                        Text("CONFIRM", fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            OAuthUiState.EXCHANGING -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp, color = CYAN
                                    )
                                    Text("Exchanging code…", color = TEXT, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Gemini API key ───────────────────────────────────────────────
            if (activeProvider == LLMProvider.GEMINI) {
                item {
                    SectionLabel("GEMINI API KEY")
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("AIza...", color = Color.Gray) },
                        trailingIcon = {
                            IconButton(onClick = { geminiVisible = !geminiVisible }) {
                                Icon(
                                    if (geminiVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    null, tint = Color.Gray
                                )
                            }
                        },
                        visualTransformation = if (geminiVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        colors = fieldColors(),
                    )
                }
            }

            // ── LiteRT model path ────────────────────────────────────────────
            if (activeProvider == LLMProvider.LITERT) {
                item {
                    SectionLabel("LOCAL LITERT MODEL PATH")
                    OutlinedTextField(
                        value = liteRtPath,
                        onValueChange = { liteRtPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("/data/local/tmp/model.litertlm", color = Color.Gray) },
                        colors = fieldColors(),
                    )
                }
            }

            // ── Save ─────────────────────────────────────────────────────────
            item {
                Button(
                    onClick = {
                        vm.save(
                            SettingsData(
                                activeProvider = activeProvider.name,
                                anthropicKey   = anthropicKey,
                                geminiKey      = geminiKey,
                                liteRtPath     = liteRtPath,
                            )
                        )
                        showSaved = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CYAN, contentColor = Color.Black
                    )
                ) {
                    Text("SAVE CONFIG", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                if (showSaved) {
                    Text(
                        "✓ CONFIG SAVED",
                        color = CYAN, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        color = Color.Gray, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Color(0xFF00E5FF),
    unfocusedBorderColor = Color.DarkGray,
    focusedTextColor     = Color(0xFFE0E0E0),
    unfocusedTextColor   = Color(0xFFE0E0E0),
    cursorColor          = Color(0xFF00E5FF),
    containerColor       = Color(0xFF0A0A0A),
)
