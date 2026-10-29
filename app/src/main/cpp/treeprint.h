#pragma once

#include <cstdint>
#include <string>
#include <vector>

uint64_t tree_fingerprint(const std::string& root, const std::vector<std::string>& skip_names, const std::string& skip_prefix);
