#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"

using namespace geode::prelude;

class $modify(XeniaNoclip, PlayLayer) {
    void destroyPlayer(PlayerObject* player, GameObject* object) override {
        if (xenia::State::get().noclipOn() && object != m_anticheatSpike) {
            return;
        }
        PlayLayer::destroyPlayer(player, object);
    }
};
