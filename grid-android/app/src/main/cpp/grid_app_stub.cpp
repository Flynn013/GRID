#include <jni.h>
#include <android/log.h>

#define TAG "GRIDAppGlue"

// Stub entry point. Real FFI lives in modules/grid_ffi compiled into libgodot.so.
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "GRID app glue loaded (stub)");
    return JNI_VERSION_1_6;
}
