#include "grid_ffi_jni.h"
#include "../grid_scene_bridge.h"
#include "core/config/engine.h"
#include "platform/android/jni_utils.h"

static GridSceneBridge *get_bridge() {
	Object *obj = Engine::get_singleton()->get_singleton_object(StringName("GridSceneBridge"));
	return Object::cast_to<GridSceneBridge>(obj);
}

extern "C" {

JNIEXPORT jstring JNICALL Java_ai_grid_bridge_GodotBridge_gridGetSceneTree(
		JNIEnv *env, jobject thiz) {
	GridSceneBridge *bridge = get_bridge();
	if (!bridge) {
		return env->NewStringUTF("{\"error\":\"bridge_not_ready\"}");
	}
	String result = bridge->get_scene_tree_json();
	return env->NewStringUTF(result.utf8().get_data());
}

JNIEXPORT jboolean JNICALL Java_ai_grid_bridge_GodotBridge_gridSpawnNode(
		JNIEnv *env, jobject thiz, jstring class_name, jstring parent_path) {
	GridSceneBridge *bridge = get_bridge();
	if (!bridge) {
		return JNI_FALSE;
	}
	return bridge->spawn_node(
			jstring_to_string(class_name, env),
			jstring_to_string(parent_path, env)
	) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_ai_grid_bridge_GodotBridge_gridSetPropertyVector3(
		JNIEnv *env, jobject thiz,
		jstring node_path, jstring property,
		jfloat x, jfloat y, jfloat z) {
	GridSceneBridge *bridge = get_bridge();
	if (!bridge) {
		return JNI_FALSE;
	}
	return bridge->set_property_vector3(
			jstring_to_string(node_path, env),
			jstring_to_string(property, env),
			x, y, z
	) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_ai_grid_bridge_GodotBridge_gridGetPropertyVector3(
		JNIEnv *env, jobject thiz, jstring node_path, jstring property) {
	GridSceneBridge *bridge = get_bridge();
	if (!bridge) {
		return env->NewStringUTF("0,0,0");
	}
	String result = bridge->get_property_vector3(
			jstring_to_string(node_path, env),
			jstring_to_string(property, env)
	);
	return env->NewStringUTF(result.utf8().get_data());
}

JNIEXPORT jboolean JNICALL Java_ai_grid_bridge_GodotBridge_gridLoadAsset(
		JNIEnv *env, jobject thiz, jstring file_path) {
	GridSceneBridge *bridge = get_bridge();
	if (!bridge) {
		return JNI_FALSE;
	}
	return bridge->load_glb_asset(jstring_to_string(file_path, env))
			? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_ai_grid_bridge_GodotBridge_gridCreateSnapshot(
		JNIEnv *env, jobject thiz) {
	GridSceneBridge *bridge = get_bridge();
	if (!bridge) {
		return -1;
	}
	return (jint)bridge->create_snapshot();
}

JNIEXPORT jboolean JNICALL Java_ai_grid_bridge_GodotBridge_gridRevertToSnapshot(
		JNIEnv *env, jobject thiz, jint snapshot_id) {
	GridSceneBridge *bridge = get_bridge();
	if (!bridge) {
		return JNI_FALSE;
	}
	return bridge->revert_to_snapshot((int)snapshot_id) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_ai_grid_bridge_GodotBridge_gridExecuteGdscript(
		JNIEnv *env, jobject thiz, jstring code) {
	GridSceneBridge *bridge = get_bridge();
	if (!bridge) {
		return env->NewStringUTF("{\"ok\":false,\"error\":\"bridge_not_ready\"}");
	}
	String result = bridge->execute_gdscript(jstring_to_string(code, env));
	return env->NewStringUTF(result.utf8().get_data());
}

} // extern "C"
