#include <Geode/Geode.hpp>
#include <algorithm>
#include <cmath>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"
using namespace geode::prelude;

namespace {
    bool g_held[2] = {false, false};
    int g_tick = 0;

    void releaseHeld(GJBaseGameLayer* l, int i) {
        if (!g_held[i]) return;
        g_held[i] = false;
        xenia::bot::injectButton(l, false, 1, i == 0, xenia::State::get().slideReal);
    }

    void slidePulse(GJBaseGameLayer* l, int i, PlayerObject* p) {
        auto& s = xenia::State::get();
        if (!p || p->m_isDead || !p->m_isDart || s.autoClickOn()) return releaseHeld(l, i);
        double tps = xenia::bot::effectiveTps();
        int period = std::max(2, (int)std::lround(tps / s.slideCps));
        bool down = (g_tick % period) < (period + 1) / 2;
        if (down == g_held[i]) return;
        g_held[i] = down;
        xenia::bot::injectButton(l, down, 1, i == 0, s.slideReal);
    }
}

class $modify(XeniaSlideLayer, GJBaseGameLayer) {
    void processCommands(float dt, bool isHalfTick, bool isLastTick) {
        auto& s = xenia::State::get();
        bool full = typeinfo_cast<PlayLayer*>(this) != nullptr && !isHalfTick;
        if (full) {
            if (s.waveSlideOn()) {
                slidePulse(this, 0, m_player1);
                if (m_gameState.m_isDualMode) slidePulse(this, 1, m_player2);
                else releaseHeld(this, 1);
                g_tick++;
            } else {
                releaseHeld(this, 0);
                releaseHeld(this, 1);
            }
        }
        GJBaseGameLayer::processCommands(dt, isHalfTick, isLastTick);
    }
};

class $modify(XeniaSlidePlay, PlayLayer) {
    void destroyPlayer(PlayerObject* p, GameObject* obj) override {
        auto& s = xenia::State::get();
        if (s.waveSlideOn() && p && p->m_isDart && obj && obj != m_anticheatSpike
            && obj->m_objectType == GameObjectType::Solid) {
            p->setPosition({p->getPositionX(), p->m_lastPosition.y});
            p->m_yVelocity = 0.0;
            return;
        }
        PlayLayer::destroyPlayer(p, obj);
    }

    void resetLevel() {
        PlayLayer::resetLevel();
        g_held[0] = g_held[1] = false;
        g_tick = 0;
    }
};
