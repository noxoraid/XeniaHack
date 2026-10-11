#include "xenia/core/State.hpp"
#include <algorithm>
#include <cmath>
#include "xenia/ui/Platform.hpp"
using namespace geode::prelude;

namespace xenia {
    State& State::get() {
        static State s;
        return s;
    }

    void State::load() {
        auto m = Mod::get();
        auto d = [](double v, double lo, double hi, double fallback) {
            return std::isfinite(v) ? std::clamp(v, lo, hi) : fallback;
        };

        noclip = m->getSavedValue<bool>("noclip", false);
        speedhack = m->getSavedValue<bool>("speedhack", false);
        speed = d(m->getSavedValue<double>("speed", 1.0), kMinSpeed, kMaxSpeed, 1.0);
        botSpeed = d(m->getSavedValue<double>("botSpeed", 1.0), kMinSpeed, kMaxSpeed, 1.0);
        theme = std::clamp(m->getSavedValue<int>("theme", xenia::ui::platform::kDefaultTheme), 0, 4);
        uiMode = std::clamp(m->getSavedValue<int>("uiMode", xenia::ui::platform::kDefaultUiMode), 0, 1);
        lang = std::clamp(m->getSavedValue<int>("lang", 0), 0, 1);
        tableOrder = m->getSavedValue<std::string>("tableOrder", "");
        tableCollapsed = std::clamp(m->getSavedValue<int>("tableCollapsed", 0), 0, 0xFFFF);
        overlayX = m->getSavedValue<double>("overlayX", -1.0);
        overlayY = m->getSavedValue<double>("overlayY", -1.0);

        uiScale = (float)d(m->getSavedValue<double>("uiScale", 1.0), 0.6, 1.4, 1.0);
        uiOpacity = (float)d(m->getSavedValue<double>("uiOpacity", 1.0), 0.3, 1.0, 1.0);
        textScale = (float)d(m->getSavedValue<double>("textScale", 1.0), 0.7, 1.4, 1.0);
        logoSize = (float)d(m->getSavedValue<double>("logoSize", 24.0), 14.0, 60.0, 24.0);
        clockSize = (float)d(m->getSavedValue<double>("clockSize", 30.0), kMinHud, kMaxHud, 30.0);
        stepSize = (float)d(m->getSavedValue<double>("stepSize", 30.0), kMinHud, kMaxHud, 30.0);
        fontIdx = std::clamp(m->getSavedValue<int>("fontIdx", 0), 0, 3);

        safeMode = m->getSavedValue<bool>("safeMode", true);
        tpsBypass = m->getSavedValue<bool>("tpsBypass", false);
        tps = d(m->getSavedValue<double>("tps", 240.0), kMinTps, kMaxTps, 240.0);
        cbfBypass = m->getSavedValue<bool>("cbfBypass", true);
        particleFixed = m->getSavedValue<bool>("particleFixed", true);
        speedAudio = m->getSavedValue<bool>("speedAudio", true);

        macroName = m->getSavedValue<std::string>("macroName", "");
        showStats = m->getSavedValue<bool>("showStats", true);

        straightShip = m->getSavedValue<bool>("straightShip", false);
        straightWave = m->getSavedValue<bool>("straightWave", false);
        shipClick = m->getSavedValue<bool>("shipClick", true);
        waveClick = m->getSavedValue<bool>("waveClick", true);
        shipCps = d(m->getSavedValue<double>("shipCps", 12.0), kMinCps, kMaxCps, 12.0);
        waveCps = d(m->getSavedValue<double>("waveCps", 12.0), kMinCps, kMaxCps, 12.0);

        autoClick = m->getSavedValue<bool>("autoClick", false);
        acMode = std::clamp(m->getSavedValue<int>("acMode", 0), 0, 1);
        acHold = std::clamp(m->getSavedValue<int>("acHold", 2), 0, kMaxTicks);
        acRelease = std::clamp(m->getSavedValue<int>("acRelease", 2), 0, kMaxTicks);
        acHoldCps = d(m->getSavedValue<double>("acHoldCps", 30.0), kMinRate, 1e12, 30.0);
        acReleaseCps = d(m->getSavedValue<double>("acReleaseCps", 30.0), kMinRate, 1e12, 30.0);

        waveSlide = m->getSavedValue<bool>("waveSlide", false);
        slideReal = m->getSavedValue<bool>("slideReal", true);
        slideCps = d(m->getSavedValue<double>("slideCps", 12.0), kMinCps, kMaxCps, 12.0);

        clickInd = m->getSavedValue<bool>("clickInd", false);
        ciMode = std::clamp(m->getSavedValue<int>("ciMode", 0), 0, 1);
        ciLadder = m->getSavedValue<bool>("ciLadder", true);
        ciPerfectOn = m->getSavedValue<bool>("ciPerfectOn", true);
        auto col3 = [&](char const* key, int out[3], int r, int g, int bl) {
            int def[3] = {r, g, bl};
            for (int i = 0; i < 3; i++) out[i] = std::clamp(m->getSavedValue<int>(fmt::format("{}{}", key, i), def[i]), 0, 255);
        };
        col3("ciRung", ciRung, 240, 240, 240);
        col3("ciBar", ciBar, 230, 50, 50);
        col3("ciArrow", ciArrow, 60, 230, 110);
        col3("ciPath", ciPath, 0, 255, 90);
        col3("ciZone", ciZone, 255, 255, 255);
        col3("ciPerfect", ciPerfect, 80, 255, 120);
        ciX = d(m->getSavedValue<double>("ciX2", 60.0), 0.0, 1000.0, 60.0);
        ciY = d(m->getSavedValue<double>("ciY2", 44.0), 0.0, 1000.0, 44.0);
        ciLen = d(m->getSavedValue<double>("ciLen2", 150.0), 40.0, 400.0, 150.0);
        ciW = d(m->getSavedValue<double>("ciW2", 40.0), 14.0, 120.0, 40.0);
        ciAhead = d(m->getSavedValue<double>("ciAhead", 0.8), 0.2, 5.0, 0.8);
        ciReach = d(m->getSavedValue<double>("ciReach", 1.2), 0.2, 5.0, 1.2);
        ciThick = d(m->getSavedValue<double>("ciThick", 3.0), 0.5, 12.0, 3.0);
        ciOpacity = d(m->getSavedValue<double>("ciOpacity", 0.9), 0.1, 1.0, 0.9);
        ciTextSize = d(m->getSavedValue<double>("ciTextSize", 0.45), 0.2, 1.2, 0.45);

        statsMask = std::clamp(m->getSavedValue<int>("statsMask", StatFps | StatTps | StatFrame | StatInput | StatBot), 0, (1 << kStatCount) - 1);
    }

    void State::save() {
        auto m = Mod::get();
        m->setSavedValue<bool>("noclip", noclip);
        m->setSavedValue<bool>("speedhack", speedhack);
        m->setSavedValue<double>("speed", speed);
        m->setSavedValue<double>("botSpeed", botSpeed);
        m->setSavedValue<int>("theme", theme);
        m->setSavedValue<int>("uiMode", uiMode);
        m->setSavedValue<int>("lang", lang);
        m->setSavedValue<std::string>("tableOrder", tableOrder);
        m->setSavedValue<int>("tableCollapsed", tableCollapsed);
        m->setSavedValue<double>("overlayX", overlayX);
        m->setSavedValue<double>("overlayY", overlayY);

        m->setSavedValue<double>("uiScale", (double)uiScale);
        m->setSavedValue<double>("uiOpacity", (double)uiOpacity);
        m->setSavedValue<double>("textScale", (double)textScale);
        m->setSavedValue<double>("logoSize", (double)logoSize);
        m->setSavedValue<double>("clockSize", (double)clockSize);
        m->setSavedValue<double>("stepSize", (double)stepSize);
        m->setSavedValue<int>("fontIdx", fontIdx);

        m->setSavedValue<bool>("safeMode", safeMode);
        m->setSavedValue<bool>("tpsBypass", tpsBypass);
        m->setSavedValue<double>("tps", tps);
        m->setSavedValue<bool>("cbfBypass", cbfBypass);
        m->setSavedValue<bool>("particleFixed", particleFixed);
        m->setSavedValue<bool>("speedAudio", speedAudio);

        m->setSavedValue<std::string>("macroName", macroName);
        m->setSavedValue<bool>("showStats", showStats);

        m->setSavedValue<bool>("straightShip", straightShip);
        m->setSavedValue<bool>("straightWave", straightWave);
        m->setSavedValue<bool>("shipClick", shipClick);
        m->setSavedValue<bool>("waveClick", waveClick);
        m->setSavedValue<double>("shipCps", shipCps);
        m->setSavedValue<double>("waveCps", waveCps);

        m->setSavedValue<bool>("autoClick", autoClick);
        m->setSavedValue<int>("acMode", acMode);
        m->setSavedValue<int>("acHold", acHold);
        m->setSavedValue<int>("acRelease", acRelease);
        m->setSavedValue<double>("acHoldCps", acHoldCps);
        m->setSavedValue<double>("acReleaseCps", acReleaseCps);

        m->setSavedValue<bool>("waveSlide", waveSlide);
        m->setSavedValue<bool>("slideReal", slideReal);
        m->setSavedValue<double>("slideCps", slideCps);

        m->setSavedValue<bool>("clickInd", clickInd);
        m->setSavedValue<int>("ciMode", ciMode);
        m->setSavedValue<bool>("ciLadder", ciLadder);
        m->setSavedValue<bool>("ciPerfectOn", ciPerfectOn);
        auto put3 = [&](char const* key, int const c[3]) {
            for (int i = 0; i < 3; i++) m->setSavedValue<int>(fmt::format("{}{}", key, i), c[i]);
        };
        put3("ciRung", ciRung); put3("ciBar", ciBar); put3("ciArrow", ciArrow);
        put3("ciPath", ciPath); put3("ciZone", ciZone); put3("ciPerfect", ciPerfect);
        m->setSavedValue<double>("ciX2", ciX);
        m->setSavedValue<double>("ciY2", ciY);
        m->setSavedValue<double>("ciLen2", ciLen);
        m->setSavedValue<double>("ciW2", ciW);
        m->setSavedValue<double>("ciAhead", ciAhead);
        m->setSavedValue<double>("ciReach", ciReach);
        m->setSavedValue<double>("ciThick", ciThick);
        m->setSavedValue<double>("ciOpacity", ciOpacity);
        m->setSavedValue<double>("ciTextSize", ciTextSize);

        m->setSavedValue<int>("statsMask", statsMask);
    }
}
