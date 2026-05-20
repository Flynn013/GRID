#pragma once

#include "core/object/object.h"
#include "core/string/ustring.h"

/**
 * GridSceneBridge exposes live Godot SceneTree manipulation to the Kotlin agent
 * via a JNI layer (jni/grid_ffi_jni.cpp).
 *
 * Registered as a Godot singleton: Engine.get_singleton("GridSceneBridge")
 */
class GridSceneBridge : public Object {
	GDCLASS(GridSceneBridge, Object);

protected:
	static void _bind_methods();

public:
	// Returns the live SceneTree as a JSON string for LLM context injection.
	String get_scene_tree_json();

	// Instantiates p_class_name and adds it under p_parent_path.
	bool spawn_node(const String &p_class_name, const String &p_parent_path);

	// Sets a Vector3 property on the node at p_node_path.
	bool set_property_vector3(const String &p_node_path, const String &p_property,
			float p_x, float p_y, float p_z);

	// Reads a Vector3 property from the node at p_node_path.
	// Returns a comma-separated "x,y,z" string ("0,0,0" if not found).
	String get_property_vector3(const String &p_node_path, const String &p_property);

	// Loads a .glb file from the Android sandbox into the active scene.
	bool load_glb_asset(const String &p_file_path);

	// Creates an in-RAM snapshot of the current SceneTree. Returns snapshot ID.
	int create_snapshot();

	// Reverts the SceneTree to the snapshot with the given ID.
	bool revert_to_snapshot(int p_snapshot_id);

	// Compiles and runs arbitrary GDScript code in a RefCounted context.
	// Returns JSON: {"ok":true,"result":<variant>} or {"ok":false,"error":"..."}
	// "tree" is pre-injected as a class variable pointing to the live SceneTree.
	String execute_gdscript(const String &p_code);

	// Removes the node at p_node_path from the tree (queue_free). Returns false if not found.
	bool delete_node(const String &p_node_path);

	// Sets any property on a node using a JSON-encoded Variant value string.
	// value_json: a JSON literal — "3.14" (float), "\"hello\"" (String), "true" (bool), etc.
	bool set_property(const String &p_node_path, const String &p_property, const String &p_value_json);

private:
	static String _node_to_json(class Node *p_node, int p_depth = 0);
};
