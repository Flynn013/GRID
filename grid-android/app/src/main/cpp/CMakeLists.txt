cmake_minimum_required(VERSION 3.22.1)
project("grid_app_glue")

# This library is an app-level glue stub.
# The real FFI functions (gridGetSceneTree, gridSpawnNode, etc.) are compiled
# into libgodot.so via modules/grid_ffi/ in the Godot SCons build.
# This stub lets CMake succeed in UI-only dev builds before the Godot AAR is available.

add_library(grid_app_glue SHARED
        grid_app_stub.cpp)

target_link_libraries(grid_app_glue
        android
        log)
