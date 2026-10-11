#include <Geode/Geode.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <algorithm>
#include <cmath>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"

using namespace geode::prelude;

class $modify(XeniaSlideLayer, GJBaseGameLayer) {
    struct Fields {
        bool m_held[2] = {false, false};
        int m_tick = 0;
    };

    void releaseHeld(int index) {
        if (!m_fields->m_held[index]) return;
        m_fields->m_held[index] = false;
        xenia::bot::injectButton(this, false, 1, index == 0, xenia::State::get().slideReal);
    }

    void slidePulse(int index, PlayerObject* player) {
        auto const& state = xenia::State::get();
        if (!player || player->m_isDead || !player->m_isDart || state.autoClickOn()) {
            releaseHeld(index);
            return;
        }

        double const tps = xenia::bot::effectiveTps();
        int const period = std::max(2, static_cast<int>(std::lround(tps / state.slideCps)));
        bool const down = (m_fields->m_tick % period) < (period + 1) / 2;

        if (down != m_fields->m_held[index]) {
            m_fields->m_held[index] = down;
            xenia::bot::injectButton(this, down, 1, index == 0, state.slideReal);
        }
    }

    void processCommands(float dt, bool isHalfTick, bool isLastTick) override {
        auto const& state = xenia::State::get();
        bool const isPlay = typeinfo_cast<PlayLayer*>(this) != nullptr && !isHalfTick;

        if (isPlay) {
            if (state.waveSlideOn()) {
                slidePulse(0, m_player1);
                if (m_gameState.m_isDualMode) {
                    slidePulse(1, m_player2);
                } else {
                    releaseHeld(1);
                }
                m_fields->m_tick++;
            } else {
                releaseHeld(0);
                releaseHeld(1);
            }
        }

        GJBaseGameLayer::processCommands(dt, isHalfTick, isLastTick);
    }
};

class $modify(XeniaSlidePlay, PlayLayer) {
    void destroyPlayer(PlayerObject* player, GameObject* object) override {
        auto const& state = xenia::State::get();
        if (state.waveSlideOn() && player && player->m_isDart && object && object != m_anticheatSpike
            && object->m_objectType == GameObjectType::Solid) {
            player->setPosition({player->getPositionX(), player->m_lastPosition.y});
            player->m_yVelocity = 0.0;
            return;
        }
        PlayLayer::destroyPlayer(player, object);
    }

    void resetLevel() override {
        PlayLayer::resetLevel();
    }
};
