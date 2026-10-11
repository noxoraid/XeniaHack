#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"
using namespace geode::prelude;

class $modify(XeniaNoclip, PlayLayer) {
    void destroyPlayer(PlayerObject* p, GameObject* obj) override {
        if (xenia::State::get().noclipOn() && obj != m_anticheatSpike) return;
        PlayLayer::destroyPlayer(p, obj);
    }
};
