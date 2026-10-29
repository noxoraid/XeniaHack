#include <jni.h>

#include <vector>

#include "textgrep.h"

extern "C" {

JNIEXPORT jlongArray JNICALL
Java_com_uplink_app_TextGrep_nativeGrep(JNIEnv* env, jobject, jbyteArray data, jint len, jbyteArray needle,
                                            jboolean ignoreCase, jint maxHits) {
    if (!data || !needle || len <= 0 || maxHits <= 0) return env->NewLongArray(0);
    jsize have = env->GetArrayLength(data);
    jsize size = len < have ? len : have;
    jsize keyLen = env->GetArrayLength(needle);
    if (size <= 0 || keyLen <= 0) return env->NewLongArray(0);
    std::vector<jbyte> buf(static_cast<size_t>(size));
    std::vector<jbyte> key(static_cast<size_t>(keyLen));
    env->GetByteArrayRegion(data, 0, size, buf.data());
    env->GetByteArrayRegion(needle, 0, keyLen, key.data());
    std::vector<nx::GrepHit> hits;
    nx::grepLiteral(reinterpret_cast<const uint8_t*>(buf.data()), buf.size(),
                    reinterpret_cast<const uint8_t*>(key.data()), key.size(), ignoreCase != JNI_FALSE,
                    static_cast<size_t>(maxHits), hits);
    std::vector<jlong> packed;
    packed.reserve(hits.size() * 3);
    for (const nx::GrepHit& h : hits) {
        packed.push_back(h.line);
        packed.push_back(h.start);
        packed.push_back(h.end);
    }
    jlongArray out = env->NewLongArray(static_cast<jsize>(packed.size()));
    if (!packed.empty()) env->SetLongArrayRegion(out, 0, static_cast<jsize>(packed.size()), packed.data());
    return out;
}

}
