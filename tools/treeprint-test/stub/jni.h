#pragma once

#include <cstdint>

typedef int32_t jint;
typedef int64_t jlong;
typedef int32_t jsize;
typedef uint8_t jboolean;
typedef void* jobject;
typedef jobject jclass;
typedef jobject jstring;
typedef jobject jarray;
typedef jobject jobjectArray;
typedef char* jchar_ptr;

#define JNIEXPORT
#define JNICALL

struct JNIEnv {
    const char* GetStringUTFChars(jstring, jboolean*);
    void ReleaseStringUTFChars(jstring, const char*);
    jsize GetArrayLength(jarray);
    jobject GetObjectArrayElement(jobjectArray, jsize);
    void DeleteLocalRef(jobject);
};
