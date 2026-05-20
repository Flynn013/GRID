package ai.grid.auth

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Handles Anthropic OAuth 2.0 + PKCE for GRID.
 *
 * Flow:
 *  1. startOAuthFlow(ctx)       — generates PKCE, opens claude.ai/oauth/authorize in Custom Tab
 *  2. User logs in, lands on console.anthropic.com/oauth/code/callback which shows a one-time code
 *  3. User copies code, pastes into VENDOR screen
 *  4. exchangeCode(ctx, code)   — POSTs to Anthropic token endpoint, stores tokens
 *  5. getValidToken(ctx)        — returns access token, auto-refreshes if within 5 min of expiry
 *
 * Uses the same public client ID as Claude CLI (installed-app flow; no client secret required).
 */
object AnthropicOAuthManager {

    private const val CLIENT_ID    = "9d1c250a-e61b-48f7-9f87-25f0b6b9f9ea"
    private const val AUTH_URL     = "https://claude.ai/oauth/authorize"
    private const val TOKEN_URL    = "https://console.anthropic.com/v1/oauth/token"
    private const val SCOPES       = "org:create_api_key user:profile user:inference"
    private const val REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback"

    private const val REFRESH_BUFFER_MS = 5L * 60 * 1000   // refresh 5 min before expiry
    private const val KEY_ACCESS  = "oauth_access_token"
    private const val KEY_REFRESH = "oauth_refresh_token"
    private const val KEY_EXPIRY  = "oauth_expires_at_ms"

    // Held in memory between startOAuthFlow() and exchangeCode()
    @Volatile private var pendingVerifier: String? = null

    private val http = OkHttpClient()

    // ── Prefs ──────────────────────────────────────────────────────────────────

    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            "grid_oauth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun storeTokens(ctx: Context, access: String, refresh: String, expiresIn: Long) {
        val expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L
        prefs(ctx).edit()
            .putString(KEY_ACCESS,  access)
            .putString(KEY_REFRESH, refresh)
            .putLong(KEY_EXPIRY,    expiresAtMs)
            .apply()
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun isSignedIn(ctx: Context) = prefs(ctx).getString(KEY_ACCESS, null) != null

    fun signOut(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRY).apply()
        pendingVerifier = null
    }

    /**
     * Builds the authorization URL, stores the PKCE verifier, and opens
     * the Anthropic login page in a Chrome Custom Tab.
     */
    fun startOAuthFlow(ctx: Context) {
        val verifier  = generateVerifier()
        val challenge = generateChallenge(verifier)
        pendingVerifier = verifier

        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("response_type",         "code")
            .appendQueryParameter("client_id",             CLIENT_ID)
            .appendQueryParameter("redirect_uri",          REDIRECT_URI)
            .appendQueryParameter("scope",                 SCOPES)
            .appendQueryParameter("code_challenge",        challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        CustomTabsIntent.Builder().build().launchUrl(ctx, authUri)
    }

    /**
     * Exchanges the one-time code (copied from the Anthropic callback page) for
     * access + refresh tokens. Stores them in EncryptedSharedPreferences.
     */
    suspend fun exchangeCode(ctx: Context, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val verifier = pendingVerifier
            ?: return@withContext Result.failure(Exception("No pending OAuth flow — tap Sign In first."))

        val body = JSONObject().apply {
            put("grant_type",    "authorization_code")
            put("client_id",     CLIENT_ID)
            put("code",          code.trim())
            put("redirect_uri",  REDIRECT_URI)
            put("code_verifier", verifier)
        }

        runCatching {
            val resp = http.newCall(
                Request.Builder()
                    .url(TOKEN_URL)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .header("Content-Type", "application/json")
                    .build()
            ).execute()

            val text = resp.body?.string() ?: throw Exception("Empty response from token endpoint")
            if (!resp.isSuccessful) throw Exception("Token exchange failed (${resp.code}): $text")

            val obj       = JSONObject(text)
            val access    = obj.getString("access_token")
            val refresh   = obj.optString("refresh_token", "")
            val expiresIn = obj.optLong("expires_in", 3600L)
            storeTokens(ctx, access, refresh, expiresIn)
            pendingVerifier = null
        }
    }

    /**
     * Returns a valid access token, automatically refreshing if within 5 min of expiry.
     * Returns null if not signed in or refresh fails (caller should prompt re-auth).
     */
    suspend fun getValidToken(ctx: Context): String? = withContext(Dispatchers.IO) {
        val p = prefs(ctx)
        val access    = p.getString(KEY_ACCESS,  null) ?: return@withContext null
        val expiresAt = p.getLong(KEY_EXPIRY, 0L)

        if (System.currentTimeMillis() < expiresAt - REFRESH_BUFFER_MS) {
            return@withContext access
        }

        val refresh = p.getString(KEY_REFRESH, null)
        if (refresh.isNullOrBlank()) { signOut(ctx); return@withContext null }

        val refreshed = doRefresh(ctx, refresh)
        if (!refreshed) { signOut(ctx); null } else prefs(ctx).getString(KEY_ACCESS, null)
    }

    // ── Private ────────────────────────────────────────────────────────────────

    private fun doRefresh(ctx: Context, refreshToken: String): Boolean {
        return runCatching {
            val body = JSONObject().apply {
                put("grant_type",    "refresh_token")
                put("client_id",     CLIENT_ID)
                put("refresh_token", refreshToken)
            }
            val resp = http.newCall(
                Request.Builder()
                    .url(TOKEN_URL)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .header("Content-Type", "application/json")
                    .build()
            ).execute()

            val text = resp.body?.string() ?: return false
            if (!resp.isSuccessful) return false

            val obj     = JSONObject(text)
            val access  = obj.getString("access_token")
            val refresh = obj.optString("refresh_token", refreshToken)
            val exp     = obj.optLong("expires_in", 3600L)
            storeTokens(ctx, access, refresh, exp)
            true
        }.getOrDefault(false)
    }

    private fun generateVerifier(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateChallenge(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
