#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace nx {

enum SniffKind : int {
    SniffBinary = 0,
    SniffText = 1,
    SniffImage = 2,
    SniffAudio = 3,
    SniffVideo = 4,
    SniffArchive = 5,
    SniffPdf = 6,
};

struct LineIndex {
    std::vector<int64_t> starts;
    int64_t size = 0;
    bool truncated = false;
};

int sniffKind(const uint8_t* data, size_t len);

bool buildLineIndex(const char* path, size_t maxLines, size_t maxChunk, int64_t maxBytes, LineIndex& out);

}
