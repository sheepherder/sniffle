#include <jni.h>
#include <string>
#include <android/log.h>

#define ARDUINOJSON_USE_LONG_LONG 1
#include "ArduinoJson.h"
#include "decoder.h"

#define TAG "TheengsJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static thread_local TheengsDecoder decoder;

static std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return "";
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r(c);
    env->ReleaseStringUTFChars(s, c);
    return r;
}

extern "C" JNIEXPORT jstring JNICALL
Java_de_schaefer_sniffle_ble_TheengsDecoder_nativeDecodeBLE(
        JNIEnv *env, jobject, jstring json_input) {
    if (!json_input) return nullptr;

    std::string input = jstr(env, json_input);
    StaticJsonDocument<4096> doc;
    if (deserializeJson(doc, input)) {
        LOGE("deserializeJson failed");
        return nullptr;
    }

    JsonObject obj = doc.as<JsonObject>();
    int result = decoder.decodeBLEJson(obj);
    if (result < 0) return nullptr;

    std::string output;
    serializeJson(obj, output);
    return env->NewStringUTF(output.c_str());
}

