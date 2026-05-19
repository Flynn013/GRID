package ai.grid.ui.settings

import ai.grid.data.SettingsData
import ai.grid.data.SettingsRepository
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)
    val settings = repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsData())
    fun save(data: SettingsData) = viewModelScope.launch { repo.save(data) }
}
