#include "register_types.h"
#include "core/config/engine.h"
#include "grid_scene_bridge.h"

void initialize_grid_ffi_module(ModuleInitializationLevel p_level) {
	if (p_level != MODULE_INITIALIZATION_LEVEL_SCENE) {
		return;
	}
	GDREGISTER_CLASS(GridSceneBridge);
	Engine::get_singleton()->add_singleton(
			Engine::Singleton("GridSceneBridge", memnew(GridSceneBridge)));
}

void uninitialize_grid_ffi_module(ModuleInitializationLevel p_level) {
	if (p_level != MODULE_INITIALIZATION_LEVEL_SCENE) {
		return;
	}
}
