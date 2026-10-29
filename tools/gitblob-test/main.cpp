#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <string>
#include <vector>

#include "gitblob.h"


static int failures = 0;

static void expect(bool ok, const char* what) {
    if (!ok) {
        std::printf("FAIL %s\n", what);
        failures++;
    }
}

static std::string capture(const std::string& cmd) {
    std::string out;
    FILE* p = popen(cmd.c_str(), "r");
    if (!p) return out;
    char buf[128];
    while (std::fgets(buf, sizeof(buf), p)) out += buf;
    pclose(p);
    while (!out.empty() && (out.back() == '\n' || out.back() == ' ')) out.pop_back();
    return out;
}

static void make(const std::string& path, size_t size) {
    std::ofstream f(path, std::ios::binary);
    for (size_t i = 0; i < size; i++) f.put(static_cast<char>((i * 131 + 7) & 0xFF));
}

int main() {
    const size_t sizes[] = {0, 1, 3, 55, 56, 57, 63, 64, 65, 119, 120, 4096, 65535, 65536, 65537, 300001};
    for (size_t size : sizes) {
        std::string path = "/tmp/gitblob-case.bin";
        make(path, size);
        std::string mine;
        bool ok = nx::gitBlobSha(path.c_str(), mine);
        std::string want = capture("git hash-object " + path);
        expect(ok && mine == want, ("size " + std::to_string(size)).c_str());
    }
    std::string none;
    expect(!nx::gitBlobSha("/tmp/does-not-exist-gitblob", none), "missing file");
    expect(!nx::gitBlobSha("/tmp", none), "directory");
    std::printf(failures == 0 ? "OK\n" : "FAILED %d\n", failures);
    return failures == 0 ? 0 : 1;
}
