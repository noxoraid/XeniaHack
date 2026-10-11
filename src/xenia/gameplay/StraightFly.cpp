#include <Geode/Geode.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <algorithm>
#include <cmath>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"

using namespace geode::prelude;

namespace {
    enum VehicleKind { None = 0, Ship = 1, Wave = 2 };

    struct LockState {
        VehicleKind kind = None;
        float lockedY = 0.f;
        bool held = false;
    };

    VehicleKind kindOf(PlayerObject* player) {
        if (!player) return None;
        if (player->m_isShip) return Ship;
        if (player->m_isDart) return Wave;
        return None;
    }

    bool isEnabled(VehicleKind kind) {
        auto const& state = xenia::State::get();
        return (kind == Ship && state.straightShipOn()) || (kind == Wave && state.straightWaveOn());
    }

    bool hasClickEffect(VehicleKind kind) {
        auto const& state = xenia::State::get();
        return kind == Ship ? state.shipClick : state.waveClick;
    }

    double getClickRate(VehicleKind kind) {
        auto const& state = xenia::State::get();
        return kind == Ship ? state.shipCps : state.waveCps;
    }
}

class $modify(XeniaStraightLayer, GJBaseGameLayer) {
    struct Fields {
        LockState m_locks[2];
        int m_tick = 0;
    };

    void releaseHeld(int index) {
        auto& lock = m_fields->m_locks[index];
        if (!lock.held) return;
        lock.held = false;
        xenia::bot::injectButton(this, false, 1, index == 0);
    }

    void pulseInput(int index, PlayerObject* player) {
        auto& lock = m_fields->m_locks[index];
        if (!player || player->m_isDead || !isEnabled(lock.kind) || !hasClickEffect(lock.kind) || xenia::State::get().autoClickOn()) {
            releaseHeld(index);
            return;
        }

        double const tps = xenia::bot::effectiveTps();
        int const period = std::max(2, static_cast<int>(std::lround(tps / getClickRate(lock.kind))));
        bool const down = (m_fields->m_tick % period) < (period + 1) / 2;

        if (down != lock.held) {
            lock.held = down;
            xenia::bot::injectButton(this, down, 1, index == 0);
        }
    }

    void lockPlayerY(int index, PlayerObject* player) {
        auto& lock = m_fields->m_locks[index];
        if (!player || player->m_isDead) return;

        VehicleKind const k = kindOf(player);
        if (!isEnabled(k)) {
            releaseHeld(index);
            lock.kind = None;
            return;
        }

        if (k != lock.kind) {
            lock.kind = k;
            lock.lockedY = player->getPositionY();
        }

        if (player->getPositionY() != lock.lockedY) {
            player->setPosition({player->getPositionX(), lock.lockedY});
        }
        player->m_yVelocity = 0.0;
    }

    void processCommands(float dt, bool isHalfTick, bool isLastTick) {
        auto const& state = xenia::State::get();
        bool const isPlay = typeinfo_cast<PlayLayer*>(this) != nullptr && !isHalfTick;
        bool const active = isPlay && (state.straightShipOn() || state.straightWaveOn());
        bool const isDual = m_gameState.m_isDualMode;

        if (active) {
            pulseInput(0, m_player1);
            if (isDual) {
                pulseInput(1, m_player2);
            } else {
                releaseHeld(1);
            }
            m_fields->m_tick++;
        } else if (isPlay) {
            releaseHeld(0);
            releaseHeld(1);
            m_fields->m_locks[0].kind = None;
            m_fields->m_locks[1].kind = None;
        }

        GJBaseGameLayer::processCommands(dt, isHalfTick, isLastTick);

        if (active) {
            lockPlayerY(0, m_player1);
            if (isDual) {
                lockPlayerY(1, m_player2);
            }
        }
    }
};

class $modify(XeniaStraightPlay, PlayLayer) {
    void resetLevel() {
        PlayLayer::resetLevel();
    }
};
