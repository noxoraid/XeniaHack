#include <jni.h>

#include <string>

#include "gitblob.h"

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_uplink_app_GitHash_nativeBlob(JNIEnv* env, jobject, jstring path) {
    if (!path) return nullptr;
    const char* raw = env->GetStringUTFChars(path, nullptr);
    if (!raw) return nullptr;
    std::string hex;
    bool ok = nx::gitBlobSha(raw, hex);
    env->ReleaseStringUTFChars(path, raw);
    if (!ok) return nullptr;
    return env->NewStringUTF(hex.c_str());
}

}
