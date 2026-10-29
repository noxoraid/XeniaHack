#include <jni.h>

#include <string>
#include <vector>

#include "treeprint.h"

namespace {

std::string text(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

}

extern "C" JNIEXPORT jlong JNICALL
Java_com_uplink_app_TreePrint_nativeFingerprint(JNIEnv* env, jobject, jstring root, jobjectArray names, jstring prefix) {
    std::vector<std::string> skip;
    const jsize count = names ? env->GetArrayLength(names) : 0;
    for (jsize i = 0; i < count; ++i) {
        jstring item = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        skip.push_back(text(env, item));
        env->DeleteLocalRef(item);
    }
    return static_cast<jlong>(tree_fingerprint(text(env, root), skip, text(env, prefix)));
}
