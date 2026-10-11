#pragma once
#include <Geode/Geode.hpp>
#include <filesystem>

namespace xenia::bot {
    struct Frame {
        int frame;
        bool down;
        bool player2;
        int button;
        bool half = false;
    };

    struct Sample {
        int frame = 0;
        bool hasP2 = false;
        float x1 = 0.f, y1 = 0.f;
        double v1 = 0.0;
        float x2 = 0.f, y2 = 0.f;
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
        static bool validName(std::string const& name);
        static std::vector<std::string> list();
        static bool exists(std::string const& name);

        static std::string create(std::string const& name);
        static std::string remove(std::string const& name);
        static std::string rename(std::string const& from, std::string const& to);
        static std::string load(std::string const& name);
        static std::string save();

        static bool hasCurrent();
        static Macro& current();
        static std::string info();
    };
}
