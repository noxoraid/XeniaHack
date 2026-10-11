#include <Geode/Geode.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <algorithm>
#include <cmath>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"

using namespace geode::prelude;

using DeltaType = decltype(std::declval<GJBaseGameLayer&>().getModifiedDelta(0.f));

class $modify(XeniaTps, GJBaseGameLayer) {
    DeltaType getModifiedDelta(float dt) override {
        double const tps = xenia::bot::effectiveTps();
        if (!typeinfo_cast<PlayLayer*>(this) || std::abs(tps - 240.0) < 1e-9) {
            return GJBaseGameLayer::getModifiedDelta(dt);
        }

        float modifier = std::min(1.f, m_gameState.m_timeWarp);
        if (modifier <= 0.f) modifier = 1.f;

        double const stepDuration = 1.0 / tps / modifier;
        double const totalDelta = static_cast<double>(dt) + static_cast<double>(m_extraDelta);
        double const steps = std::clamp(std::floor(totalDelta / stepDuration), 1.0, 5000.0);

        double const consumedDelta = steps * stepDuration;
        m_extraDelta = std::max(0.0, totalDelta - consumedDelta);
        return static_cast<DeltaType>(consumedDelta);
    }
};
