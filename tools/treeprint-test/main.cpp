#include "treeprint.h"

#include <cassert>
#include <cstdio>
#include <filesystem>
#include <fstream>

namespace fs = std::filesystem;

namespace {

void write_file(const fs::path& p, const std::string& body) {
    fs::create_directories(p.parent_path());
    std::ofstream out(p, std::ios::binary | std::ios::trunc);
    out << body;
}

}

int main() {
    const fs::path root = fs::temp_directory_path() / "nx_treeprint_test";
    fs::remove_all(root);
    write_file(root / "a.txt", "one");
    write_file(root / "sub" / "b.txt", "two");

    const std::vector<std::string> none;
    const uint64_t first = tree_fingerprint(root.string(), none, "");
    assert(first == tree_fingerprint(root.string(), none, ""));

    write_file(root / "sub" / "b.txt", "two-changed-and-longer");
    assert(tree_fingerprint(root.string(), none, "") != first);

    const uint64_t before_marker = tree_fingerprint(root.string(), none, ".localhost_task_");
    write_file(root / ".localhost_task_7", "http://localhost:8080");
    assert(tree_fingerprint(root.string(), none, ".localhost_task_") == before_marker);
    assert(tree_fingerprint(root.string(), none, "") != before_marker);

    write_file(root / "node_modules" / "x.js", "1");
    const uint64_t with_modules = tree_fingerprint(root.string(), {"node_modules"}, "");
    write_file(root / "node_modules" / "x.js", "2222222");
    assert(tree_fingerprint(root.string(), {"node_modules"}, "") == with_modules);

    write_file(root / "a.txt", "one-more-byte");
    assert(tree_fingerprint(root.string(), {"node_modules"}, "") != with_modules);

    const uint64_t missing_a = tree_fingerprint("/nonexistent/nx_treeprint_root", none, "");
    assert(missing_a == tree_fingerprint("/nonexistent/nx_treeprint_root", none, ""));

    fs::remove_all(root);
    std::puts("treeprint ok");
    return 0;
}
