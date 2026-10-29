#include <jni.h>

#include <vector>

#include "vtscreen.h"

namespace {

VtScreen* screenOf(jlong handle) {
    return reinterpret_cast<VtScreen*>(handle);
}

void pushInts(JNIEnv* env, jintArray target, const std::vector<int32_t>& source) {
    env->SetIntArrayRegion(target, 0, static_cast<jsize>(source.size()), reinterpret_cast<const jint*>(source.data()));
}

}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_uplink_app_TermCore_create(JNIEnv*, jobject, jint cols, jint rows) {
    return reinterpret_cast<jlong>(new VtScreen(cols, rows));
}

JNIEXPORT void JNICALL
Java_com_uplink_app_TermCore_destroy(JNIEnv*, jobject, jlong handle) {
    delete screenOf(handle);
}

JNIEXPORT jstring JNICALL
Java_com_uplink_app_TermCore_feed(JNIEnv* env, jobject, jlong handle, jcharArray data, jint count) {
    std::vector<jchar> buffer(static_cast<size_t>(count));
    env->GetCharArrayRegion(data, 0, count, buffer.data());
    std::u16string replies = screenOf(handle)->feed(reinterpret_cast<const char16_t*>(buffer.data()), count);
    if (replies.empty()) return nullptr;
    return env->NewString(reinterpret_cast<const jchar*>(replies.data()), static_cast<jsize>(replies.size()));
}

JNIEXPORT void JNICALL
Java_com_uplink_app_TermCore_resize(JNIEnv*, jobject, jlong handle, jint cols, jint rows) {
    screenOf(handle)->resize(cols, rows);
}

JNIEXPORT void JNICALL
Java_com_uplink_app_TermCore_state(JNIEnv* env, jobject, jlong handle, jintArray out) {
    const VtScreen* s = screenOf(handle);
    jint values[6] = {
        s->cx(), s->cy(), s->appCursor() ? 1 : 0, s->bracketed() ? 1 : 0,
        s->cursorVisible() ? 1 : 0, s->historySize(),
    };
    env->SetIntArrayRegion(out, 0, 6, values);
}

JNIEXPORT void JNICALL
Java_com_uplink_app_TermCore_row(JNIEnv* env, jobject, jlong handle, jint index,
                                     jintArray ch, jintArray fg, jintArray bg, jintArray fl) {
    jsize capacity = env->GetArrayLength(ch);
    std::vector<int32_t> a, b, c, d;
    screenOf(handle)->copyRow(index, capacity, a, b, c, d);
    pushInts(env, ch, a);
    pushInts(env, fg, b);
    pushInts(env, bg, c);
    pushInts(env, fl, d);
}

}
