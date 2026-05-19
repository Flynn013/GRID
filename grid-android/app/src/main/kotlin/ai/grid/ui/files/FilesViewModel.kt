package ai.grid.ui.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    // Root is app-private projects directory; created on first access.
    val root: File = File(app.filesDir, "projects").also { it.mkdirs() }

    private val _currentDir = MutableStateFlow(root)
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _entries = MutableStateFlow<List<File>>(emptyList())
    val entries: StateFlow<List<File>> = _entries.asStateFlow()

    val canGoUp: Boolean get() = _currentDir.value.canonicalPath != root.canonicalPath

    init { refresh() }

    fun navigateTo(dir: File) {
        _currentDir.value = dir
        refresh()
    }

    fun navigateUp() {
        val parent = _currentDir.value.parentFile ?: return
        if (!parent.canonicalPath.startsWith(root.canonicalPath)) return
        navigateTo(parent)
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _entries.value = _currentDir.value
                .listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            File(_currentDir.value, name.trim()).mkdirs()
            refresh()
        }
    }

    fun relativePath(file: File): String =
        file.canonicalPath.removePrefix(root.canonicalPath).ifBlank { "/" }
}
