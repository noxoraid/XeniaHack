#include "gitblob.h"

#include <cstdio>
#include <cstring>
#include <sys/stat.h>

namespace nx {

static inline uint32_t rol(uint32_t v, int n) { return (v << n) | (v >> (32 - n)); }

static void sha1Block(Sha1& s, const uint8_t* p) {
    uint32_t w[80];
    for (int i = 0; i < 16; i++) {
        w[i] = (static_cast<uint32_t>(p[i * 4]) << 24) | (static_cast<uint32_t>(p[i * 4 + 1]) << 16) |
               (static_cast<uint32_t>(p[i * 4 + 2]) << 8) | static_cast<uint32_t>(p[i * 4 + 3]);
    }
    for (int i = 16; i < 80; i++) w[i] = rol(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
    uint32_t a = s.h[0], b = s.h[1], c = s.h[2], d = s.h[3], e = s.h[4];
    for (int i = 0; i < 80; i++) {
        uint32_t f, k;
        if (i < 20) { f = (b & c) | (~b & d); k = 0x5A827999; }
        else if (i < 40) { f = b ^ c ^ d; k = 0x6ED9EBA1; }
        else if (i < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDC; }
        else { f = b ^ c ^ d; k = 0xCA62C1D6; }
        uint32_t t = rol(a, 5) + f + e + k + w[i];
        e = d;
        d = c;
        c = rol(b, 30);
        b = a;
        a = t;
    }
    s.h[0] += a;
    s.h[1] += b;
    s.h[2] += c;
    s.h[3] += d;
    s.h[4] += e;
}

void sha1Start(Sha1& s) {
    s.h[0] = 0x67452301;
    s.h[1] = 0xEFCDAB89;
    s.h[2] = 0x98BADCFE;
    s.h[3] = 0x10325476;
    s.h[4] = 0xC3D2E1F0;
    s.total = 0;
    s.used = 0;
}

void sha1Feed(Sha1& s, const uint8_t* data, size_t len) {
    s.total += len;
    while (len > 0) {
        size_t take = 64 - s.used;
        if (take > len) take = len;
        std::memcpy(s.block + s.used, data, take);
        s.used += take;
        data += take;
        len -= take;
        if (s.used == 64) {
            sha1Block(s, s.block);
            s.used = 0;
        }
    }
}

void sha1Finish(Sha1& s, uint8_t out[20]) {
    uint64_t bits = s.total * 8;
    uint8_t pad = 0x80;
    sha1Feed(s, &pad, 1);
    uint8_t zero = 0;
    while (s.used != 56) sha1Feed(s, &zero, 1);
    uint8_t tail[8];
    for (int i = 0; i < 8; i++) tail[i] = static_cast<uint8_t>(bits >> (56 - i * 8));
    sha1Feed(s, tail, 8);
    for (int i = 0; i < 5; i++) {
        out[i * 4] = static_cast<uint8_t>(s.h[i] >> 24);
        out[i * 4 + 1] = static_cast<uint8_t>(s.h[i] >> 16);
        out[i * 4 + 2] = static_cast<uint8_t>(s.h[i] >> 8);
        out[i * 4 + 3] = static_cast<uint8_t>(s.h[i]);
    }
}

bool gitBlobSha(const char* path, std::string& hex) {
    struct stat st;
    if (stat(path, &st) != 0 || !S_ISREG(st.st_mode)) return false;
    FILE* f = std::fopen(path, "rb");
    if (!f) return false;
    char head[48];
    int n = std::snprintf(head, sizeof(head), "blob %lld", static_cast<long long>(st.st_size));
    Sha1 s;
    sha1Start(s);
    sha1Feed(s, reinterpret_cast<const uint8_t*>(head), static_cast<size_t>(n));
    uint8_t nul = 0;
    sha1Feed(s, &nul, 1);
    uint8_t buf[65536];
    int64_t left = static_cast<int64_t>(st.st_size);
    while (left > 0) {
        size_t want = left > static_cast<int64_t>(sizeof(buf)) ? sizeof(buf) : static_cast<size_t>(left);
        size_t got = std::fread(buf, 1, want, f);
        if (got == 0) {
            std::fclose(f);
            return false;
        }
        sha1Feed(s, buf, got);
        left -= static_cast<int64_t>(got);
    }
    std::fclose(f);
    uint8_t digest[20];
    sha1Finish(s, digest);
    static const char* digits = "0123456789abcdef";
    hex.assign(40, '0');
    for (int i = 0; i < 20; i++) {
        hex[i * 2] = digits[digest[i] >> 4];
        hex[i * 2 + 1] = digits[digest[i] & 15];
    }
    return true;
}

}
