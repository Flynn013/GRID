package ai.grid.ui.editor

import ai.grid.bridge.GodotBridge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class SceneNode(
    val name: String,
    val className: String,
    val path: String,
    val depth: Int,
    val childCount: Int = 0,
)

class EditorViewModel : ViewModel() {

    private val _nodes      = MutableStateFlow<List<SceneNode>>(emptyList())
    val nodes: StateFlow<List<SceneNode>> = _nodes.asStateFlow()

    private val _selected   = MutableStateFlow<SceneNode?>(null)
    val selected: StateFlow<SceneNode?> = _selected.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _activeProp = MutableStateFlow("position")
    val activeProp: StateFlow<String> = _activeProp.asStateFlow()

    private val _propX = MutableStateFlow("0.0000")
    val propX: StateFlow<String> = _propX.asStateFlow()
    private val _propY = MutableStateFlow("0.0000")
    val propY: StateFlow<String> = _propY.asStateFlow()
    private val _propZ = MutableStateFlow("0.0000")
    val propZ: StateFlow<String> = _propZ.asStateFlow()

    fun setX(v: String) { _propX.value = v }
    fun setY(v: String) { _propY.value = v }
    fun setZ(v: String) { _propZ.value = v }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isFetching.value = true
            try {
                val raw = GodotBridge.getSceneTreeJson()
                _nodes.value = flattenTree(raw)
            } finally {
                _isFetching.value = false
            }
        }
    }

    fun selectNode(node: SceneNode) {
        _selected.value = node
        loadProperty(node.path, _activeProp.value)
    }

    fun selectProp(p: String) {
        _activeProp.value = p
        val path = _selected.value?.path ?: return
        loadProperty(path, p)
    }

    fun applyProperty() {
        val path = _selected.value?.path ?: return
        val x = _propX.value.toFloatOrNull() ?: return
        val y = _propY.value.toFloatOrNull() ?: return
        val z = _propZ.value.toFloatOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            GodotBridge.setPropertyVector3(path, _activeProp.value, x, y, z)
        }
    }

    private fun loadProperty(nodePath: String, property: String) {
        if (nodePath.isBlank()) {
            _propX.value = "0.0000"; _propY.value = "0.0000"; _propZ.value = "0.0000"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val (x, y, z) = GodotBridge.getPropertyVector3(nodePath, property)
            _propX.value = "%.4f".format(x)
            _propY.value = "%.4f".format(y)
            _propZ.value = "%.4f".format(z)
        }
    }

    private val parser = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun flattenTree(raw: String): List<SceneNode> {
        val out = mutableListOf<SceneNode>()
        try {
            val root = parser.parseToJsonElement(raw).jsonObject
            if (root.containsKey("nodes")) {
                root["nodes"]?.jsonArray?.forEach { collect(it.jsonObject, out, 0) }
            } else {
                collect(root, out, 0)
            }
        } catch (_: Exception) {}
        return out
    }

    private fun collect(obj: JsonObject, out: MutableList<SceneNode>, depth: Int) {
        val name      = obj["name"]?.jsonPrimitive?.content ?: return
        val className = obj["class"]?.jsonPrimitive?.content  ?: "Node"
        val path      = obj["path"]?.jsonPrimitive?.content   ?: ""
        val children  = obj["children"]?.jsonArray            ?: JsonArray(emptyList())
        out.add(SceneNode(name, className, path, depth, children.size))
        children.forEach { collect(it.jsonObject, out, depth + 1) }
    }
}
