package ai.grid.bridge

import android.util.Log

/**
 * Kotlin-side wrapper for the GRID FFI bridge.
 *
 * The `external fun` declarations below map to C++ functions compiled into
 * libgodot.so via modules/grid_ffi/jni/grid_ffi_jni.cpp.
 *
 * JNI name convention:
 *   Java_ai_grid_bridge_GodotBridge_<methodName>
 *
 * In UI-stub mode (godot-lib.aar not compiled), all calls silently no-op.
 */
object GodotBridge {

    private const val TAG = "GodotBridge"
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        try {
            System.loadLibrary("godot")
            loaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libgodot.so not found — UI-stub mode active")
        }
    }

    fun getSceneTreeJson(): String =
        try { gridGetSceneTree() } catch (_: UnsatisfiedLinkError) { "{\"nodes\":[]}" }

    fun spawnNode(className: String, parentPath: String = "/root"): Boolean =
        try { gridSpawnNode(className, parentPath) } catch (_: UnsatisfiedLinkError) { false }

    fun setPropertyVector3(
        nodePath: String, property: String,
        x: Float, y: Float, z: Float
    ): Boolean =
        try { gridSetPropertyVector3(nodePath, property, x, y, z) }
        catch (_: UnsatisfiedLinkError) { false }

    /**
     * Reads a Vector3 property (position, rotation, scale) from a live Godot node.
     * Returns Triple(0f, 0f, 0f) in UI-stub mode or when the node is not found.
     */
    fun getPropertyVector3(nodePath: String, property: String): Triple<Float, Float, Float> =
        try {
            val csv = gridGetPropertyVector3(nodePath, property)
            val parts = csv.split(",")
            Triple(
                parts.getOrNull(0)?.toFloatOrNull() ?: 0f,
                parts.getOrNull(1)?.toFloatOrNull() ?: 0f,
                parts.getOrNull(2)?.toFloatOrNull() ?: 0f,
            )
        } catch (_: UnsatisfiedLinkError) {
            Triple(0f, 0f, 0f)
        } catch (_: Exception) {
            Triple(0f, 0f, 0f)
        }

    fun loadAsset(filePath: String): Boolean =
        try { gridLoadAsset(filePath) } catch (_: UnsatisfiedLinkError) { false }

    fun createSnapshot(): Int =
        try { gridCreateSnapshot() } catch (_: UnsatisfiedLinkError) { -1 }

    fun revertToSnapshot(snapshotId: Int): Boolean =
        try { gridRevertToSnapshot(snapshotId) } catch (_: UnsatisfiedLinkError) { false }

    /**
     * Compiles and runs arbitrary GDScript code.
     * Returns JSON: {"ok":true,"result":<variant>} or {"ok":false,"error":"..."}
     * Inside the script, access the scene tree via: var tree = Engine.get_main_loop()
     */
    fun executeGdscript(code: String): String =
        try { gridExecuteGdscript(code) }
        catch (_: UnsatisfiedLinkError) { "{\"ok\":false,\"error\":\"stub_mode\"}" }

    // ── JNI declarations ─ implemented in modules/grid_ffi/jni/grid_ffi_jni.cpp ──────────────────────
    private external fun gridGetSceneTree(): String
    private external fun gridSpawnNode(className: String, parentPath: String): Boolean
    private external fun gridSetPropertyVector3(nodePath: String, property: String, x: Float, y: Float, z: Float): Boolean
    private external fun gridGetPropertyVector3(nodePath: String, property: String): String
    private external fun gridLoadAsset(filePath: String): Boolean
    private external fun gridCreateSnapshot(): Int
    private external fun gridRevertToSnapshot(snapshotId: Int): Boolean
    private external fun gridExecuteGdscript(code: String): String
}
