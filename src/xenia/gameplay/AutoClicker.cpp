#include <Geode/Geode.hpp>
#include <algorithm>
#include <cmath>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"
using namespace geode::prelude;

namespace {
    struct Clicker {
        bool started = false;
        bool down = false;
        double timer = 0.0;
    };

    Clicker g_ck[2];

    void resetClickers() {
        for (auto& c : g_ck) c = Clicker{};
    }

    void clickSend(GJBaseGameLayer* l, int i, bool down) {
        xenia::bot::injectButton(l, down, 1, i == 0, true);
    }

    void clickStop(GJBaseGameLayer* l, int i) {
        if (g_ck[i].down) clickSend(l, i, false);
        g_ck[i] = Clicker{};
    }

    void clickStep(GJBaseGameLayer* l, int i, PlayerObject* p) {
        auto& s = xenia::State::get();
        auto& c = g_ck[i];
        if (!p || p->m_isDead) return clickStop(l, i);

        double holdLen, relLen;
        if (s.acMode == 0) {
            holdLen = std::max(1, s.acHold);
            relLen = std::max(1, s.acRelease);
        } else {
            double tps = xenia::bot::effectiveTps();
            holdLen = tps / std::max(xenia::State::kMinRate, s.acHoldCps);
            relLen = tps / std::max(xenia::State::kMinRate, s.acReleaseCps);
        }

        if (!c.started) {
            c.started = true;
            c.down = true;
            c.timer = 0.0;
            clickSend(l, i, true);
        }
        c.timer += 1.0;
        int guard = 0;
        while (guard < 200) {
            double len = c.down ? holdLen : relLen;
            if (c.timer < len) break;
            c.timer -= len;
            c.down = !c.down;
            clickSend(l, i, c.down);
            guard++;
        }
        if (guard == 200) c.timer = 0.0;
    }
}

class $modify(XeniaClickerLayer, GJBaseGameLayer) {
    void processCommands(float dt, bool isHalfTick, bool isLastTick) {
        auto& s = xenia::State::get();
        bool full = typeinfo_cast<PlayLayer*>(this) != nullptr && !isHalfTick;
        if (full) {
            if (s.autoClickOn()) {
                clickStep(this, 0, m_player1);
                if (m_gameState.m_isDualMode) clickStep(this, 1, m_player2);
                else clickStop(this, 1);
            } else {
                clickStop(this, 0);
                clickStop(this, 1);
            }
        }
        GJBaseGameLayer::processCommands(dt, isHalfTick, isLastTick);
    }
};

class $modify(XeniaClickerPlay, PlayLayer) {
    void resetLevel() {
        PlayLayer::resetLevel();
        resetClickers();
    }

    void onQuit() {
        resetClickers();
        PlayLayer::onQuit();
    }
};
