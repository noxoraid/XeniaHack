#include <jni.h>
#include <dirent.h>
#include <sys/stat.h>
#include <string>
#include <vector>

namespace {

struct Tally {
    jlong files = 0;
    jlong bytes = 0;
    jlong skipped = 0;
};

bool globMatch(const char* pattern, const char* text) {
    const char* star = nullptr;
    const char* mark = nullptr;
    while (*text) {
        if (*pattern == '*') {
            star = pattern++;
            mark = text;
        } else if (*pattern == '?' || *pattern == *text) {
            pattern++;
            text++;
        } else if (star) {
            pattern = star + 1;
            text = ++mark;
        } else {
            return false;
        }
    }
    while (*pattern == '*') pattern++;
    return *pattern == '\0';
}

bool isExcluded(const std::vector<std::string>& patterns, const char* name) {
    for (const std::string& p : patterns) {
        if (globMatch(p.c_str(), name)) return true;
    }
    return false;
}

bool isDots(const char* name) {
    return name[0] == '.' && (name[1] == '\0' || (name[1] == '.' && name[2] == '\0'));
}

void walk(const std::string& dir, const std::vector<std::string>& patterns, Tally& tally, int depth) {
    if (depth > 64) return;
    DIR* handle = opendir(dir.c_str());
    if (!handle) return;
    while (dirent* entry = readdir(handle)) {
        const char* name = entry->d_name;
        if (isDots(name)) continue;
        if (isExcluded(patterns, name)) {
            tally.skipped++;
            continue;
        }
        std::string full = dir;
        if (full.back() != '/') full += '/';
        full += name;
        struct stat info;
        if (stat(full.c_str(), &info) != 0) continue;
        if (S_ISDIR(info.st_mode)) {
            walk(full, patterns, tally, depth + 1);
        } else if (S_ISREG(info.st_mode)) {
            tally.files++;
            tally.bytes += static_cast<jlong>(info.st_size);
        }
    }
    closedir(handle);
}

std::string baseName(const std::string& path) {
    size_t end = path.size();
    while (end > 1 && path[end - 1] == '/') end--;
    size_t cut = path.rfind('/', end - 1);
    if (cut == std::string::npos) return path.substr(0, end);
    return path.substr(cut + 1, end - cut - 1);
}

std::string readUtf(JNIEnv* env, jstring text) {
    const char* raw = env->GetStringUTFChars(text, nullptr);
    std::string out(raw ? raw : "");
    if (raw) env->ReleaseStringUTFChars(text, raw);
    return out;
}

}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_uplink_app_TreeScan_nativeScan(JNIEnv* env, jobject, jstring root, jobjectArray patterns) {
    std::vector<std::string> list;
    jsize count = env->GetArrayLength(patterns);
    for (jsize i = 0; i < count; i++) {
        jstring item = static_cast<jstring>(env->GetObjectArrayElement(patterns, i));
        list.push_back(readUtf(env, item));
        env->DeleteLocalRef(item);
    }

    std::string path = readUtf(env, root);
    Tally tally;
    struct stat info;
    if (!path.empty() && stat(path.c_str(), &info) == 0) {
        if (isExcluded(list, baseName(path).c_str())) {
            tally.skipped = 1;
        } else if (S_ISDIR(info.st_mode)) {
            walk(path, list, tally, 0);
        } else if (S_ISREG(info.st_mode)) {
            tally.files = 1;
            tally.bytes = static_cast<jlong>(info.st_size);
        }
    }

    jlongArray out = env->NewLongArray(3);
    jlong values[3] = {tally.files, tally.bytes, tally.skipped};
    env->SetLongArrayRegion(out, 0, 3, values);
    return out;
}
