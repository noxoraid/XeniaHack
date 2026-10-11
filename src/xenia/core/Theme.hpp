#pragma once
#include <Geode/Geode.hpp>

namespace xenia {
    struct Theme {
        const char* name;
        cocos2d::ccColor3B bg;
        cocos2d::ccColor3B active;
    };

    inline const std::array<Theme, 4>& themes() {
        static const std::array<Theme, 4> t = {{
            {"Aurora",   {255,255,255}, {92,226,214}},
            {"Midnight", {205,222,255}, {110,170,255}},
            {"Purple",   {215,185,255}, {255,120,200}},
            {"Cyan",     {175,232,255}, {255,210,90}},
        }};
        return t;
    }
    inline const Theme& currentTheme(int i) {
        return themes()[std::clamp(i, 0, (int)themes().size() - 1)];
    }
}
