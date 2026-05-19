package ai.grid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LLMProvider { ANTHROPIC, GEMINI, LITERT }

data class SettingsData(
    val activeProvider: String = LLMProvider.ANTHROPIC.name,
    val anthropicKey:   String = "",
    val geminiKey:      String = "",
    val liteRtPath:     String = "",
)

private val Context.dataStore by preferencesDataStore(name = "grid_settings")

class SettingsRepository private constructor(private val ctx: Context) {

    private object Keys {
        val PROVIDER  = stringPreferencesKey("active_provider")
        val ANTHROPIC = stringPreferencesKey("anthropic_key")
        val GEMINI    = stringPreferencesKey("gemini_key")
        val LITERT    = stringPreferencesKey("litert_path")
    }

    val flow: Flow<SettingsData> = ctx.dataStore.data.map { p ->
        SettingsData(
            activeProvider = p[Keys.PROVIDER]  ?: LLMProvider.ANTHROPIC.name,
            anthropicKey   = p[Keys.ANTHROPIC] ?: "",
            geminiKey      = p[Keys.GEMINI]    ?: "",
            liteRtPath     = p[Keys.LITERT]    ?: "",
        )
    }

    suspend fun save(d: SettingsData) = ctx.dataStore.edit { p ->
        p[Keys.PROVIDER]  = d.activeProvider
        p[Keys.ANTHROPIC] = d.anthropicKey
        p[Keys.GEMINI]    = d.geminiKey
        p[Keys.LITERT]    = d.liteRtPath
    }

    companion object {
        @Volatile private var INSTANCE: SettingsRepository? = null
        fun get(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
