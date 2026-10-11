#include <Geode/Geode.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <algorithm>
#include <cmath>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"

using namespace geode::prelude;

namespace {
    struct ClickerState {
        bool started = false;
        bool down = false;
        double timer = 0.0;
    };
}

class $modify(XeniaClickerLayer, GJBaseGameLayer) {
    struct Fields {
        ClickerState m_clickers[2];
    };

    void sendInput(int index, bool down) {
        xenia::bot::injectButton(this, down, 1, index == 0, true);
    }

    void stopInput(int index) {
        auto& clicker = m_fields->m_clickers[index];
        if (clicker.down) {
            sendInput(index, false);
        }
        clicker = ClickerState{};
    }

    void stepClicker(int index, PlayerObject* player) {
        auto const& state = xenia::State::get();
        auto& clicker = m_fields->m_clickers[index];
        if (!player || player->m_isDead) {
            stopInput(index);
            return;
        }

        double holdLen = 0.0;
        double relLen = 0.0;
        if (state.acMode == 0) {
            holdLen = std::max(1, state.acHold);
            relLen = std::max(1, state.acRelease);
        } else {
            double const tps = xenia::bot::effectiveTps();
            holdLen = tps / std::max(xenia::State::kMinRate, state.acHoldCps);
            relLen = tps / std::max(xenia::State::kMinRate, state.acReleaseCps);
        }

        if (!clicker.started) {
            clicker.started = true;
            clicker.down = true;
            clicker.timer = 0.0;
            sendInput(index, true);
        }

        clicker.timer += 1.0;
        int guard = 0;
        while (guard < 200) {
            double const limit = clicker.down ? holdLen : relLen;
            if (clicker.timer < limit) break;
            clicker.timer -= limit;
            clicker.down = !clicker.down;
            sendInput(index, clicker.down);
            guard++;
        }
        if (guard >= 200) {
            clicker.timer = 0.0;
        }
    }

    void processCommands(float dt, bool isHalfTick, bool isLastTick) {
        auto const& state = xenia::State::get();
        bool const isPlay = typeinfo_cast<PlayLayer*>(this) != nullptr && !isHalfTick;

        if (isPlay) {
            if (state.autoClickOn()) {
                stepClicker(0, m_player1);
                if (m_gameState.m_isDualMode) {
                    stepClicker(1, m_player2);
                } else {
                    stopInput(1);
                }
            } else {
                stopInput(0);
                stopInput(1);
            }
        }

        GJBaseGameLayer::processCommands(dt, isHalfTick, isLastTick);
    }
};

class $modify(XeniaClickerPlay, PlayLayer) {
    void resetLevel() {
        PlayLayer::resetLevel();
    }
};
