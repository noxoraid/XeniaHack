#pragma once

#include <cstdint>
#include <string>

namespace nx {

struct Sha1 {
    uint32_t h[5];
    uint64_t total;
    uint8_t block[64];
    size_t used;
};

void sha1Start(Sha1& s);
void sha1Feed(Sha1& s, const uint8_t* data, size_t len);
void sha1Finish(Sha1& s, uint8_t out[20]);
bool gitBlobSha(const char* path, std::string& hex);

}
