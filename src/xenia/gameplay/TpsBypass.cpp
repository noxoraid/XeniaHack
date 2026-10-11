#include <Geode/Geode.hpp>
#include <algorithm>
#include <cmath>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"
using namespace geode::prelude;

using XeniaDeltaT = decltype(std::declval<GJBaseGameLayer&>().getModifiedDelta(0.f));

class $modify(XeniaTps, GJBaseGameLayer) {
    XeniaDeltaT getModifiedDelta(float dt) {
        double tps = xenia::bot::effectiveTps();
        if (!typeinfo_cast<PlayLayer*>(this) || std::fabs(tps - 240.0) < 1e-9) {
            return GJBaseGameLayer::getModifiedDelta(dt);
        }
        float modifier = std::min(1.f, m_gameState.m_timeWarp);
        if (modifier <= 0.f) modifier = 1.f;
        double spt = 1.0 / tps / modifier;
        double total = (double)dt + (double)m_extraDelta;
        double steps = std::max(1.0, std::floor(total / spt));

        steps = std::min(steps, 5000.0);
        double used = steps * spt;
        m_extraDelta = std::max(0.0, total - used);
        return (XeniaDeltaT)used;
    }
};
