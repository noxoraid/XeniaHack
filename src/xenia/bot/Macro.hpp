#pragma once

#include <Geode/Geode.hpp>
#include <filesystem>
#include <string>
#include <string_view>
#include <vector>

namespace xenia::bot {
    struct Frame {
        int frame = 0;
        bool down = false;
        bool player2 = false;
        int button = 1;
        bool half = false;
    };

    struct Sample {
        int frame = 0;
        bool hasP2 = false;
        float x1 = 0.f;
        float y1 = 0.f;
        double v1 = 0.0;
        float x2 = 0.f;
        float y2 = 0.f;
        double v2 = 0.0;
    };

    struct Macro {
        std::string name;
        std::vector<Frame> frames;
        std::vector<Sample> samples;
        double tps = 240.0;
        int levelId = 0;
        std::string levelName;
        long long created = 0;
        long long modified = 0;
    };

    class MacroManager {
    public:
        static constexpr size_t kMaxFrames = 20'000'000;
        static constexpr size_t kMaxSamples = 2'000'000;

        static std::filesystem::path dir();
        static bool validName(std::string_view name);
        static std::vector<std::string> list();
        static bool exists(std::string_view name);

        static geode::Result<void> create(std::string_view name);
        static geode::Result<void> remove(std::string_view name);
        static geode::Result<void> rename(std::string_view from, std::string_view to);
        static geode::Result<void> load(std::string_view name);
        static geode::Result<void> save();

        static geode::async::Task<geode::Result<void>> loadAsync(std::string name);
        static geode::async::Task<geode::Result<void>> saveAsync();

        static bool hasCurrent();
        static Macro& current();
        static std::string info();
    };
}
