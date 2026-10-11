#include <Geode/Geode.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"
using namespace geode::prelude;

static bool s_audioApplied = false;
static float stepDt() { return (float)(1.0 / xenia::bot::effectiveTps()) * 1.0005f; }

static void setAudioPitch(float p) {
    auto fmod = FMODAudioEngine::get();
    if (!fmod) return;
    if (fmod->m_backgroundMusicChannel) fmod->m_backgroundMusicChannel->setPitch(p);
    if (fmod->m_globalChannel) fmod->m_globalChannel->setPitch(p);
}

static void restoreAudio() {
    if (!s_audioApplied) return;
    setAudioPitch(1.f);
    s_audioApplied = false;
}

class $modify(XeniaSpeed, GJBaseGameLayer) {
    void update(float dt) {
        auto& s = xenia::State::get();
        bool inLevel = typeinfo_cast<PlayLayer*>(this) != nullptr;

        if (inLevel && s.paused && s.recording()) {
            setAudioPitch(0.f);
            s_audioApplied = true;
            if (s.stepReq) {
                s.stepReq = false;
                GJBaseGameLayer::update(stepDt());
            }
            return;
        }

        double factor = s.speedFactor();
        if (inLevel && factor != 1.0) {
            GJBaseGameLayer::update(dt * static_cast<float>(factor));
            if (s.speedAudio) {
                setAudioPitch(static_cast<float>(factor));
                s_audioApplied = true;
            } else {
                restoreAudio();
            }
            return;
        }

        if (inLevel) restoreAudio();
        GJBaseGameLayer::update(dt);
    }
};

class $modify(XeniaSpeedQuit, PlayLayer) {
    void onQuit() {
        restoreAudio();
        xenia::State::get().paused = false;
        xenia::State::get().stepReq = false;
        PlayLayer::onQuit();
    }
};
