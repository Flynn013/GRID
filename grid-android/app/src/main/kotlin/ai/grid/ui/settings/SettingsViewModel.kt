package ai.grid.ui.settings

import ai.grid.auth.AnthropicOAuthManager
import ai.grid.data.SettingsData
import ai.grid.data.SettingsRepository
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OAuthUiState { IDLE, AWAITING_CODE, EXCHANGING, SIGNED_IN, ERROR }

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)

    val settings: StateFlow<SettingsData> =
        repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsData())

    private val _oauthState = MutableStateFlow(
        if (AnthropicOAuthManager.isSignedIn(app)) OAuthUiState.SIGNED_IN else OAuthUiState.IDLE
    )
    val oauthState: StateFlow<OAuthUiState> = _oauthState.asStateFlow()

    private val _oauthError = MutableStateFlow<String?>(null)
    val oauthError: StateFlow<String?> = _oauthError.asStateFlow()

    fun save(data: SettingsData) = viewModelScope.launch { repo.save(data) }

    fun startOAuthFlow(ctx: Context) {
        _oauthError.value = null
        AnthropicOAuthManager.startOAuthFlow(ctx)
        _oauthState.value = OAuthUiState.AWAITING_CODE
    }

    fun submitAuthCode(ctx: Context, code: String) {
        _oauthState.value = OAuthUiState.EXCHANGING
        _oauthError.value = null
        viewModelScope.launch {
            val result = AnthropicOAuthManager.exchangeCode(ctx, code)
            if (result.isSuccess) {
                _oauthState.value = OAuthUiState.SIGNED_IN
            } else {
                _oauthState.value = OAuthUiState.AWAITING_CODE
                _oauthError.value = result.exceptionOrNull()?.message ?: "Code exchange failed"
            }
        }
    }

    fun oauthSignOut(ctx: Context) {
        AnthropicOAuthManager.signOut(ctx)
        _oauthState.value = OAuthUiState.IDLE
        _oauthError.value = null
    }
}
