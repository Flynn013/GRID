package ai.grid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LLMProvider { ANTHROPIC, GEMINI, LITERT }

data class SettingsData(
    val activeProvider: String = "ANTHROPIC",
    val anthropicKey:   String = "",
    val geminiKey:      String = "",
    val liteRtPath:     String = "",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "grid_settings")

private val PROVIDER_KEY     = stringPreferencesKey("active_provider")
private val LITERT_PATH_KEY  = stringPreferencesKey("litert_path")
private val KEYS_VERSION_KEY = intPreferencesKey("keys_version")

class SettingsRepository private constructor(private val ctx: Context) {

    // Android Keystore-backed encrypted storage for sensitive API keys.
    // Lazy so the Keystore round-trip (key generation on first use) doesn't
    // block the singleton constructor.
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            "grid_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Combined settings flow.
     * Non-sensitive fields come from DataStore; API keys are read synchronously
     * from EncryptedSharedPreferences inside the map operator (fast in-memory
     * decryption after first Keystore unlock). The keys_version counter ensures
     * the flow re-emits after every save(), even when only keys changed.
     */
    val flow: Flow<SettingsData> = ctx.dataStore.data.map { prefs ->
        SettingsData(
            activeProvider = prefs[PROVIDER_KEY]    ?: "ANTHROPIC",
            anthropicKey   = securePrefs.getString("anthropic_key", "") ?: "",
            geminiKey      = securePrefs.getString("gemini_key",    "") ?: "",
            liteRtPath     = prefs[LITERT_PATH_KEY] ?: "",
        )
    }

    suspend fun save(d: SettingsData) {
        // Keys first — encrypted at rest in Keystore-backed EncryptedSharedPreferences.
        securePrefs.edit()
            .putString("anthropic_key", d.anthropicKey)
            .putString("gemini_key",    d.geminiKey)
            .apply()
        // Non-sensitive config + version bump to trigger flow re-emission.
        ctx.dataStore.edit { prefs ->
            prefs[PROVIDER_KEY]     = d.activeProvider
            prefs[LITERT_PATH_KEY]  = d.liteRtPath
            prefs[KEYS_VERSION_KEY] = (prefs[KEYS_VERSION_KEY] ?: 0) + 1
        }
    }

    companion object {
        @Volatile private var INSTANCE: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
