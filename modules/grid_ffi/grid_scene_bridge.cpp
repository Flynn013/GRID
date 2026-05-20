#include "grid_scene_bridge.h"

#include "core/config/engine.h"
#include "core/io/json.h"
#include "core/object/ref_counted.h"
#include "core/string/string_name.h"
#include "modules/gdscript/gdscript.h"
#include "scene/main/node.h"
#include "scene/main/scene_tree.h"
#include "scene/main/window.h"
#include "scene/resources/packed_scene.h"

// GLB import support
#ifdef MODULE_GLTF_ENABLED
#include "modules/gltf/gltf_document.h"
#include "modules/gltf/gltf_state.h"
#endif

#include <map>

void GridSceneBridge::_bind_methods() {
	ClassDB::bind_method(D_METHOD("get_scene_tree_json"),
			&GridSceneBridge::get_scene_tree_json);
	ClassDB::bind_method(D_METHOD("spawn_node", "class_name", "parent_path"),
			&GridSceneBridge::spawn_node);
	ClassDB::bind_method(D_METHOD("set_property_vector3", "node_path", "property", "x", "y", "z"),
			&GridSceneBridge::set_property_vector3);
	ClassDB::bind_method(D_METHOD("get_property_vector3", "node_path", "property"),
			&GridSceneBridge::get_property_vector3);
	ClassDB::bind_method(D_METHOD("load_glb_asset", "file_path"),
			&GridSceneBridge::load_glb_asset);
	ClassDB::bind_method(D_METHOD("create_snapshot"),
			&GridSceneBridge::create_snapshot);
	ClassDB::bind_method(D_METHOD("revert_to_snapshot", "snapshot_id"),
			&GridSceneBridge::revert_to_snapshot);
	ClassDB::bind_method(D_METHOD("execute_gdscript", "code"),
			&GridSceneBridge::execute_gdscript);
}

String GridSceneBridge::get_scene_tree_json() {
	SceneTree *tree = SceneTree::get_singleton();
	if (!tree) {
		return "{\"error\":\"no_scene_tree\"}";
	}
	Node *root = tree->get_root();
	if (!root) {
		return "{\"error\":\"no_root\"}";
	}
	return "{\"root\":" + _node_to_json(root) + "}";
}

String GridSceneBridge::_node_to_json(Node *p_node, int p_depth) {
	if (!p_node || p_depth > 16) {
		return "null";
	}
	String result = "{";
	result += "\"name\":\"" + String(p_node->get_name()) + "\",";
	result += "\"class\":\"" + p_node->get_class() + "\",";
	result += "\"path\":\"" + String(p_node->get_path()) + "\",";
	result += "\"children\":[";
	for (int i = 0; i < p_node->get_child_count(); i++) {
		if (i > 0) {
			result += ",";
		}
		result += _node_to_json(p_node->get_child(i), p_depth + 1);
	}
	result += "]}";
	return result;
}

bool GridSceneBridge::spawn_node(const String &p_class_name, const String &p_parent_path) {
	SceneTree *tree = SceneTree::get_singleton();
	if (!tree) {
		return false;
	}
	Node *parent = tree->get_root()->get_node_or_null(NodePath(p_parent_path));
	if (!parent) {
		parent = tree->get_root();
	}
	Object *obj = ClassDB::instantiate(p_class_name);
	Node *node = Object::cast_to<Node>(obj);
	if (!node) {
		if (obj) {
			memdelete(obj);
		}
		return false;
	}
	node->set_name(p_class_name);
	parent->add_child(node);
	node->set_owner(tree->get_root());
	return true;
}

bool GridSceneBridge::set_property_vector3(const String &p_node_path, const String &p_property,
		float p_x, float p_y, float p_z) {
	SceneTree *tree = SceneTree::get_singleton();
	if (!tree) {
		return false;
	}
	Node *node = tree->get_root()->get_node_or_null(NodePath(p_node_path));
	if (!node) {
		return false;
	}
	node->set(p_property, Vector3(p_x, p_y, p_z));
	return true;
}

String GridSceneBridge::get_property_vector3(const String &p_node_path, const String &p_property) {
	SceneTree *tree = SceneTree::get_singleton();
	if (!tree) {
		return "0,0,0";
	}
	Node *node = tree->get_root()->get_node_or_null(NodePath(p_node_path));
	if (!node) {
		return "0,0,0";
	}
	Variant value = node->get(p_property);
	if (value.get_type() != Variant::VECTOR3) {
		return "0,0,0";
	}
	Vector3 v = value;
	return String::num(v.x) + "," + String::num(v.y) + "," + String::num(v.z);
}

bool GridSceneBridge::load_glb_asset(const String &p_file_path) {
#ifdef MODULE_GLTF_ENABLED
	Ref<GLTFDocument> doc;
	doc.instantiate();
	Ref<GLTFState> state;
	state.instantiate();
	Error err = doc->append_from_file(p_file_path, state);
	if (err != OK) {
		return false;
	}
	SceneTree *tree = SceneTree::get_singleton();
	if (!tree) {
		return false;
	}
	Node *scene = doc->generate_scene(state);
	if (!scene) {
		return false;
	}
	tree->get_root()->add_child(scene);
	scene->set_owner(tree->get_root());
	return true;
#else
	return false;
#endif
}

static int g_next_snapshot_id = 1;
static std::map<int, Ref<PackedScene>> g_snapshots;

int GridSceneBridge::create_snapshot() {
	SceneTree *tree = SceneTree::get_singleton();
	if (!tree) {
		return -1;
	}
	Ref<PackedScene> packed;
	packed.instantiate();
	if (packed->pack(tree->get_root()) != OK) {
		return -1;
	}
	int id = g_next_snapshot_id++;
	g_snapshots[id] = packed;
	return id;
}

bool GridSceneBridge::revert_to_snapshot(int p_snapshot_id) {
	auto it = g_snapshots.find(p_snapshot_id);
	if (it == g_snapshots.end()) {
		return false;
	}
	SceneTree *tree = SceneTree::get_singleton();
	if (!tree) {
		return false;
	}
	return tree->change_scene_to_packed(it->second) == OK;
}

String GridSceneBridge::execute_gdscript(const String &p_code) {
	// Wrap user code in a RefCounted subclass with a _run() entry point.
	// Inside _run(), Engine.get_main_loop() gives access to the SceneTree.
	String src = "extends RefCounted\n\nfunc _run():\n";
	PackedStringArray lines = p_code.split("\n");
	for (int i = 0; i < lines.size(); i++) {
		src += "\t" + lines[i] + "\n";
	}
	src += "\treturn null\n";

	Ref<GDScript> scr;
	scr.instantiate();
	scr->set_source_code(src);

	Error compile_err = scr->reload(false);
	if (compile_err != OK) {
		return "{\"ok\":false,\"error\":\"compile_failed\",\"code\":" + itos((int)compile_err) + "}";
	}

	StringName base_type = scr->get_native()->get_name();
	Object *raw = ClassDB::instantiate(base_type);
	if (!raw) {
		return "{\"ok\":false,\"error\":\"instantiate_failed\"}";
	}

	Ref<RefCounted> guard;
	if (raw->is_ref_counted()) {
		guard = Ref<RefCounted>(Object::cast_to<RefCounted>(raw));
	}
	raw->set_script(scr);

	Callable::CallError ce;
	Variant ret;
	raw->callp(StringName("_run"), nullptr, 0, ret, ce);

	if (!raw->is_ref_counted()) {
		memdelete(raw);
	}

	if (ce.error != Callable::CallError::CALL_OK) {
		return "{\"ok\":false,\"error\":\"runtime_error\",\"code\":" + itos((int)ce.error) + "}";
	}

	return "{\"ok\":true,\"result\":" + JSON::stringify(ret) + "}";
}
