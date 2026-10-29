#pragma once

#include <cstdint>
#include <cstring>
#include <vector>

#define JNIEXPORT
#define JNICALL
#define JNI_FALSE 0
#define JNI_TRUE 1

typedef int8_t jbyte;
typedef int32_t jint;
typedef int64_t jlong;
typedef int32_t jsize;
typedef uint8_t jboolean;

struct _jobject {};
typedef _jobject* jobject;

struct _jarray : _jobject {
    std::vector<jbyte> bytes;
    std::vector<jlong> longs;
};
typedef _jarray* jarray;
typedef _jarray* jbyteArray;
typedef _jarray* jlongArray;

struct JNIEnv {
    jsize GetArrayLength(jarray a) { return static_cast<jsize>(a->bytes.empty() ? a->longs.size() : a->bytes.size()); }

    void GetByteArrayRegion(jbyteArray a, jsize start, jsize len, jbyte* buf) {
        if (start < 0 || len < 0 || static_cast<size_t>(start + len) > a->bytes.size()) __builtin_trap();
        memcpy(buf, a->bytes.data() + start, static_cast<size_t>(len));
    }

    jlongArray NewLongArray(jsize n) {
        jlongArray a = new _jarray();
        a->longs.resize(static_cast<size_t>(n));
        return a;
    }

    void SetLongArrayRegion(jlongArray a, jsize start, jsize len, const jlong* buf) {
        if (start < 0 || len < 0 || static_cast<size_t>(start + len) > a->longs.size()) __builtin_trap();
        memcpy(a->longs.data() + start, buf, static_cast<size_t>(len) * sizeof(jlong));
    }
};
