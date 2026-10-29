#include "textgrep.h"

#include <cstring>

namespace nx {

static inline uint8_t fold(uint8_t c) {
    return (c >= 'A' && c <= 'Z') ? static_cast<uint8_t>(c + 32) : c;
}

static bool lineHas(const uint8_t* p, size_t n, const uint8_t* key, size_t klen, bool ignoreCase) {
    if (n < klen) return false;
    size_t last = n - klen;
    if (!ignoreCase) {
        const uint8_t* cur = p;
        const uint8_t* end = p + last + 1;
        while (cur < end) {
            const void* found = memchr(cur, key[0], static_cast<size_t>(end - cur));
            if (!found) return false;
            const uint8_t* at = static_cast<const uint8_t*>(found);
            if (memcmp(at, key, klen) == 0) return true;
            cur = at + 1;
        }
        return false;
    }
    for (size_t i = 0; i <= last; i++) {
        size_t k = 0;
        while (k < klen && fold(p[i + k]) == key[k]) k++;
        if (k == klen) return true;
    }
    return false;
}

size_t grepLiteral(const uint8_t* buf, size_t len, const uint8_t* needle, size_t nlen, bool ignoreCase,
                   size_t maxHits, std::vector<GrepHit>& out) {
    out.clear();
    if (!buf || !needle || len == 0 || nlen == 0 || maxHits == 0) return 0;
    std::vector<uint8_t> key(needle, needle + nlen);
    if (ignoreCase) {
        for (size_t i = 0; i < key.size(); i++) key[i] = fold(key[i]);
    }
    size_t pos = 0;
    int64_t line = 1;
    while (pos < len && out.size() < maxHits) {
        const void* nl = memchr(buf + pos, '\n', len - pos);
        size_t end = nl ? static_cast<size_t>(static_cast<const uint8_t*>(nl) - buf) : len;
        size_t stop = end;
        if (stop > pos && buf[stop - 1] == '\r') stop--;
        if (lineHas(buf + pos, stop - pos, key.data(), key.size(), ignoreCase)) {
            out.push_back(GrepHit{line, static_cast<int64_t>(pos), static_cast<int64_t>(stop)});
        }
        pos = end + 1;
        line++;
    }
    return out.size();
}

bool looksBinary(const uint8_t* buf, size_t len) {
    if (!buf || len == 0) return false;
    size_t n = len < 4096 ? len : 4096;
    return memchr(buf, 0, n) != nullptr;
}

}
