#pragma once
#include <jni.h>

extern "C" {

JNIEXPORT jstring  JNICALL Java_ai_grid_bridge_GodotBridge_gridGetSceneTree(JNIEnv *env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_ai_grid_bridge_GodotBridge_gridSpawnNode(JNIEnv *env, jobject thiz, jstring class_name, jstring parent_path);
JNIEXPORT jboolean JNICALL Java_ai_grid_bridge_GodotBridge_gridSetPropertyVector3(JNIEnv *env, jobject thiz, jstring node_path, jstring property, jfloat x, jfloat y, jfloat z);
JNIEXPORT jboolean JNICALL Java_ai_grid_bridge_GodotBridge_gridLoadAsset(JNIEnv *env, jobject thiz, jstring file_path);
JNIEXPORT jint     JNICALL Java_ai_grid_bridge_GodotBridge_gridCreateSnapshot(JNIEnv *env, jobject thiz);
JNIEXPORT jboolean JNICALL Java_ai_grid_bridge_GodotBridge_gridRevertToSnapshot(JNIEnv *env, jobject thiz, jint snapshot_id);

} // extern "C"
