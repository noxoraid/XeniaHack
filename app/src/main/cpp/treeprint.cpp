#include "treeprint.h"

#include <filesystem>
#include <system_error>

namespace fs = std::filesystem;

namespace {

constexpr uint64_t kOffset = 1469598103934665603ULL;
constexpr uint64_t kPrime = 1099511628211ULL;

void mix_bytes(uint64_t& h, const void* data, size_t n) {
    const unsigned char* p = static_cast<const unsigned char*>(data);
    for (size_t i = 0; i < n; ++i) {
        h ^= p[i];
        h *= kPrime;
    }
}

void mix_u64(uint64_t& h, uint64_t v) {
    mix_bytes(h, &v, sizeof v);
}

bool skipped(const std::string& name, const std::vector<std::string>& names, const std::string& prefix) {
    for (const auto& n : names) {
        if (name == n) return true;
    }
    return !prefix.empty() && name.compare(0, prefix.size(), prefix) == 0;
}

}

uint64_t tree_fingerprint(const std::string& root, const std::vector<std::string>& skip_names, const std::string& skip_prefix) {
    uint64_t h = kOffset;
    std::error_code ec;
    const fs::path base(root);
    fs::recursive_directory_iterator it(base, fs::directory_options::skip_permission_denied, ec);
    if (ec) return h;
    const fs::recursive_directory_iterator end;
    while (it != end) {
        const fs::directory_entry entry = *it;
        const std::string name = entry.path().filename().string();
        if (skipped(name, skip_names, skip_prefix)) {
            it.disable_recursion_pending();
        } else {
            std::error_code rel_ec;
            const std::string rel = fs::relative(entry.path(), base, rel_ec).generic_string();
            mix_bytes(h, rel.data(), rel.size());
            std::error_code t_ec;
            const auto mtime = fs::last_write_time(entry.path(), t_ec);
            mix_u64(h, t_ec ? 0 : static_cast<uint64_t>(mtime.time_since_epoch().count()));
            std::error_code s_ec;
            const bool regular = entry.is_regular_file(s_ec);
            const uintmax_t size = regular ? fs::file_size(entry.path(), s_ec) : 0;
            mix_u64(h, s_ec ? 0 : static_cast<uint64_t>(size));
        }
        it.increment(ec);
        if (ec) break;
    }
    return h;
}
