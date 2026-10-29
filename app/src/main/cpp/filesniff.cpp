#include "filesniff.h"

#include <cstdio>
#include <cstring>

namespace nx {

namespace {

bool startsWith(const uint8_t* data, size_t len, const char* tag, size_t at = 0) {
    size_t n = std::strlen(tag);
    return len >= at + n && std::memcmp(data + at, tag, n) == 0;
}

int brandKind(const uint8_t* data, size_t len) {
    static const char* const stills[] = {"heic", "heix", "hevc", "mif1", "msf1", "avif"};
    static const char* const sounds[] = {"M4A ", "M4B ", "M4P "};
    for (const char* b : stills) if (startsWith(data, len, b, 8)) return SniffImage;
    for (const char* b : sounds) if (startsWith(data, len, b, 8)) return SniffAudio;
    return SniffVideo;
}

int containerKind(const uint8_t* data, size_t len) {
    if (startsWith(data, len, "RIFF")) {
        if (startsWith(data, len, "WEBP", 8)) return SniffImage;
        if (startsWith(data, len, "WAVE", 8)) return SniffAudio;
        if (startsWith(data, len, "AVI ", 8)) return SniffVideo;
        return -1;
    }
    if (len >= 12 && startsWith(data, len, "ftyp", 4)) return brandKind(data, len);
    return -1;
}

int magicKind(const uint8_t* d, size_t len) {
    if (len >= 4 && d[0] == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47) return SniffImage;
    if (len >= 3 && d[0] == 0xFF && d[1] == 0xD8 && d[2] == 0xFF) return SniffImage;
    if (startsWith(d, len, "GIF8")) return SniffImage;
    if (len >= 14 && d[0] == 'B' && d[1] == 'M' && d[6] == 0 && d[7] == 0 && d[8] == 0 && d[9] == 0) return SniffImage;
    int boxed = containerKind(d, len);
    if (boxed >= 0) return boxed;
    if (startsWith(d, len, "ID3")) return SniffAudio;
    if (len >= 3 && d[0] == 0xFF && (d[1] & 0xF6) == 0xF0) return SniffAudio;
    if (len >= 3 && d[0] == 0xFF && (d[1] & 0xE0) == 0xE0 && ((d[1] >> 3) & 3) != 1 && (((d[1] >> 1) & 3) == 1 || ((d[1] >> 1) & 3) == 2) && (d[2] >> 4) != 0xF) return SniffAudio;
    if (startsWith(d, len, "OggS") || startsWith(d, len, "fLaC") || startsWith(d, len, "#!AMR")) return SniffAudio;
    if (len >= 4 && d[0] == 0x1A && d[1] == 0x45 && d[2] == 0xDF && d[3] == 0xA3) return SniffVideo;
    if (startsWith(d, len, "FLV") && len >= 4 && d[3] == 1) return SniffVideo;
    if (len >= 4 && d[0] == 0 && d[1] == 0 && d[2] == 1 && d[3] == 0xBA) return SniffVideo;
    if (startsWith(d, len, "%PDF")) return SniffPdf;
    if (startsWith(d, len, "PK\x03\x04") || startsWith(d, len, "Rar!") || startsWith(d, len, "7z\xBC\xAF")) return SniffArchive;
    if (len >= 2 && d[0] == 0x1F && d[1] == 0x8B) return SniffArchive;
    return -1;
}

size_t sequenceLength(uint8_t lead) {
    if (lead < 0x80) return 1;
    if (lead >= 0xC2 && lead <= 0xDF) return 2;
    if (lead >= 0xE0 && lead <= 0xEF) return 3;
    if (lead >= 0xF0 && lead <= 0xF4) return 4;
    return 0;
}

bool looksLikeText(const uint8_t* d, size_t len) {
    size_t window = len < 8192 ? len : 8192;
    if (window >= 2 && ((d[0] == 0xFF && d[1] == 0xFE) || (d[0] == 0xFE && d[1] == 0xFF))) return false;
    size_t control = 0;
    size_t broken = 0;
    size_t i = 0;
    while (i < window) {
        uint8_t b = d[i];
        if (b == 0) return false;
        size_t n = sequenceLength(b);
        if (n == 0) {
            broken++;
            i++;
            continue;
        }
        if (i + n > window) break;
        bool ok = true;
        for (size_t k = 1; k < n; k++) if ((d[i + k] & 0xC0) != 0x80) ok = false;
        if (!ok) {
            broken++;
            i++;
            continue;
        }
        if (n == 1 && b < 0x20 && b != '\t' && b != '\n' && b != '\r' && b != '\f' && b != 0x1B && b != 0x08) control++;
        i += n;
    }
    return control * 50 <= window && broken * 100 <= window;
}

}

int sniffKind(const uint8_t* data, size_t len) {
    if (len == 0) return SniffText;
    int known = magicKind(data, len);
    if (known >= 0) return known;
    return looksLikeText(data, len) ? SniffText : SniffBinary;
}

bool buildLineIndex(const char* path, size_t maxLines, size_t maxChunk, int64_t maxBytes, LineIndex& out) {
    out.starts.clear();
    out.truncated = false;
    out.size = 0;
    if (maxLines == 0 || maxChunk < 16) return false;
    FILE* file = std::fopen(path, "rb");
    if (!file) return false;
    if (std::fseek(file, 0, SEEK_END) != 0) {
        std::fclose(file);
        return false;
    }
    int64_t size = static_cast<int64_t>(std::ftell(file));
    std::fseek(file, 0, SEEK_SET);
    if (size < 0) {
        std::fclose(file);
        return false;
    }
    out.size = size;
    int64_t limit = size < maxBytes ? size : maxBytes;
    if (limit < size) out.truncated = true;
    if (limit == 0) {
        std::fclose(file);
        return true;
    }

    std::vector<uint8_t> buffer(1 << 20);
    out.starts.push_back(0);
    int64_t lineStart = 0;
    int64_t pos = 0;
    bool pending = false;
    bool full = false;

    auto push = [&](int64_t at) {
        if (out.starts.size() >= maxLines) {
            out.truncated = true;
            full = true;
            return;
        }
        out.starts.push_back(at);
    };

    while (pos < limit && !full) {
        size_t want = buffer.size();
        if (static_cast<int64_t>(want) > limit - pos) want = static_cast<size_t>(limit - pos);
        size_t got = std::fread(buffer.data(), 1, want, file);
        if (got == 0) break;
        for (size_t i = 0; i < got && !full; i++, pos++) {
            uint8_t b = buffer[i];
            if (pending) {
                pending = false;
                if (b != '\n' && (b & 0xC0) != 0x80) {
                    push(pos);
                    lineStart = pos;
                    if (full) break;
                } else if (b != '\n') {
                    pending = true;
                }
            }
            if (b == '\n') {
                lineStart = pos + 1;
                if (lineStart < limit) push(lineStart);
            } else if (!pending && pos + 1 - lineStart >= static_cast<int64_t>(maxChunk)) {
                pending = true;
            }
        }
    }
    std::fclose(file);
    return true;
}

}
