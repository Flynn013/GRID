package ai.grid.ui.projects

import ai.grid.data.Project
import ai.grid.data.ProjectConfig
import ai.grid.data.ProjectRepository
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProjectRepository.get(app)

    val projects: StateFlow<List<Project>> = repo.projectsFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val activeProject: StateFlow<Project?> = repo.activeProjectFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    private val _lastCreated = MutableStateFlow<Project?>(null)
    val lastCreated: StateFlow<Project?> = _lastCreated.asStateFlow()

    fun createProject(config: ProjectConfig) {
        viewModelScope.launch {
            val project = repo.createProject(config)
            _lastCreated.value = project
        }
    }

    fun setActive(id: String) {
        viewModelScope.launch { repo.setActive(id) }
    }

    fun clearLastCreated() { _lastCreated.value = null }
}
