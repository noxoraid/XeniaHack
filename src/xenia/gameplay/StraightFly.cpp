#include <Geode/Geode.hpp>
#include <algorithm>
#include <cmath>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"
using namespace geode::prelude;

namespace {
    enum Kind { None = 0, Ship = 1, Wave = 2 };

    struct Lock {
        int kind = None;
        float y = 0.f;
        bool held = false;
    };

    Lock g_lock[2];
    int g_tick = 0;

    void resetLocks() {
        for (auto& l : g_lock) l = Lock{};
        g_tick = 0;
    }

    int kindOf(PlayerObject* p) {
        if (!p) return None;
        if (p->m_isShip) return Ship;
        if (p->m_isDart) return Wave;
        return None;
    }

    bool enabled(int kind) {
        auto& s = xenia::State::get();
        return (kind == Ship && s.straightShipOn()) || (kind == Wave && s.straightWaveOn());
    }

    bool clickEffect(int kind) {
        auto& s = xenia::State::get();
        return kind == Ship ? s.shipClick : s.waveClick;
    }

    double clickRate(int kind) {
        auto& s = xenia::State::get();
        return kind == Ship ? s.shipCps : s.waveCps;
    }

    void releaseHeld(GJBaseGameLayer* l, int i) {
        if (!g_lock[i].held) return;
        g_lock[i].held = false;
        xenia::bot::injectButton(l, false, 1, i == 0);
    }

    void pulse(GJBaseGameLayer* l, int i, PlayerObject* p) {
        auto& lk = g_lock[i];
        if (!p || p->m_isDead || !enabled(lk.kind) || !clickEffect(lk.kind) || xenia::State::get().autoClickOn()) return releaseHeld(l, i);
        double tps = xenia::bot::effectiveTps();
        int period = std::max(2, (int)std::lround(tps / clickRate(lk.kind)));
        bool down = (g_tick % period) < (period + 1) / 2;
        if (down == lk.held) return;
        lk.held = down;
        xenia::bot::injectButton(l, down, 1, i == 0);
    }

    void holdY(GJBaseGameLayer* l, int i, PlayerObject* p) {
        auto& lk = g_lock[i];
        if (!p || p->m_isDead) return;
        int k = kindOf(p);
        if (!enabled(k)) {
            releaseHeld(l, i);
            lk.kind = None;
            return;
        }
        if (k != lk.kind) {
            lk.kind = k;
            lk.y = p->getPositionY();
        }
        if (p->getPositionY() != lk.y) p->setPosition({p->getPositionX(), lk.y});
        p->m_yVelocity = 0.0;
    }
}

class $modify(XeniaStraightLayer, GJBaseGameLayer) {
    void processCommands(float dt, bool isHalfTick, bool isLastTick) {
        auto& s = xenia::State::get();
        bool full = typeinfo_cast<PlayLayer*>(this) != nullptr && !isHalfTick;
        bool active = full && (s.straightShipOn() || s.straightWaveOn());
        bool dual = m_gameState.m_isDualMode;

        if (active) {
            pulse(this, 0, m_player1);
            if (dual) pulse(this, 1, m_player2);
            else releaseHeld(this, 1);
            g_tick++;
        } else if (full) {
            releaseHeld(this, 0);
            releaseHeld(this, 1);
            g_lock[0].kind = None;
            g_lock[1].kind = None;
        }

        GJBaseGameLayer::processCommands(dt, isHalfTick, isLastTick);

        if (active) {
            holdY(this, 0, m_player1);
            if (dual) holdY(this, 1, m_player2);
        }
    }
};

class $modify(XeniaStraightPlay, PlayLayer) {
    void resetLevel() {
        PlayLayer::resetLevel();
        resetLocks();
    }

    void onQuit() {
        resetLocks();
        PlayLayer::onQuit();
    }
};
