#include <jni.h>

#include <vector>

#include "filesniff.h"

extern "C" {

JNIEXPORT jint JNICALL
Java_com_uplink_app_FileSniff_nativeKind(JNIEnv* env, jobject, jbyteArray data, jint count) {
    std::vector<jbyte> head(static_cast<size_t>(count));
    env->GetByteArrayRegion(data, 0, count, head.data());
    return nx::sniffKind(reinterpret_cast<const uint8_t*>(head.data()), head.size());
}

JNIEXPORT jlongArray JNICALL
Java_com_uplink_app_FileSniff_nativeLines(JNIEnv* env, jobject, jstring path, jint maxLines, jint maxChunk, jlong maxBytes) {
    const char* raw = env->GetStringUTFChars(path, nullptr);
    if (!raw) return nullptr;
    nx::LineIndex index;
    bool ok = nx::buildLineIndex(raw, static_cast<size_t>(maxLines), static_cast<size_t>(maxChunk), maxBytes, index);
    env->ReleaseStringUTFChars(path, raw);
    if (!ok) return nullptr;
    std::vector<jlong> packed;
    packed.reserve(index.starts.size() + 2);
    packed.push_back(index.truncated ? 1 : 0);
    packed.push_back(index.size);
    for (int64_t s : index.starts) packed.push_back(s);
    jlongArray out = env->NewLongArray(static_cast<jsize>(packed.size()));
    env->SetLongArrayRegion(out, 0, static_cast<jsize>(packed.size()), packed.data());
    return out;
}

}
