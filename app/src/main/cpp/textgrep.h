#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace nx {

struct GrepHit {
    int64_t line;
    int64_t start;
    int64_t end;
};

size_t grepLiteral(const uint8_t* buf, size_t len, const uint8_t* needle, size_t nlen, bool ignoreCase,
                   size_t maxHits, std::vector<GrepHit>& out);

bool looksBinary(const uint8_t* buf, size_t len);

}
