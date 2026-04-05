#include <jni.h>
#include <string>

// Stub — TheengsDecoder integration kommt in Task #2.
// Für jetzt: gibt null zurück damit der Build durchgeht.

extern "C" JNIEXPORT jstring JNICALL
Java_de_schaefer_sniffle_ble_TheengsDecoder_nativeDecodeBLE(
    JNIEnv *env, jobject /* this */, jstring json_input) {
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_de_schaefer_sniffle_ble_TheengsDecoder_nativeGetProperties(
    JNIEnv *env, jobject /* this */, jstring model_id) {
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_de_schaefer_sniffle_ble_TheengsDecoder_nativeGetAttribute(
    JNIEnv *env, jobject /* this */, jstring model_id, jstring attribute) {
    return nullptr;
}
