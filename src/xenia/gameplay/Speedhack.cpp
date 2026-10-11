#include <Geode/Geode.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"

using namespace geode::prelude;

namespace {
    float stepDeltaTime() {
        return static_cast<float>(1.0 / xenia::bot::effectiveTps()) * 1.0005f;
    }

    void setAudioPitch(float pitch) {
        if (auto* fmod = FMODAudioEngine::get()) {
            if (fmod->m_backgroundMusicChannel) {
                fmod->m_backgroundMusicChannel->setPitch(pitch);
            }
            if (fmod->m_globalChannel) {
                fmod->m_globalChannel->setPitch(pitch);
            }
        }
    }
}

class $modify(XeniaSpeed, GJBaseGameLayer) {
    struct Fields {
        bool m_audioApplied = false;
    };

    void update(float dt) override {
        auto& state = xenia::State::get();
        bool const inLevel = typeinfo_cast<PlayLayer*>(this) != nullptr;

        if (inLevel && state.paused && state.recording()) {
            setAudioPitch(0.f);
            m_fields->m_audioApplied = true;
            if (state.stepReq) {
                state.stepReq = false;
                GJBaseGameLayer::update(stepDeltaTime());
            }
            return;
        }

        double const factor = state.speedFactor();
        if (inLevel && factor != 1.0) {
            GJBaseGameLayer::update(dt * static_cast<float>(factor));
            if (state.speedAudio) {
                setAudioPitch(static_cast<float>(factor));
                m_fields->m_audioApplied = true;
            } else if (m_fields->m_audioApplied) {
                setAudioPitch(1.f);
                m_fields->m_audioApplied = false;
            }
            return;
        }

        if (inLevel && m_fields->m_audioApplied) {
            setAudioPitch(1.f);
            m_fields->m_audioApplied = false;
        }

        GJBaseGameLayer::update(dt);
    }
};

class $modify(XeniaSpeedQuit, PlayLayer) {
    void onQuit() {
        setAudioPitch(1.f);
        auto& state = xenia::State::get();
        state.paused = false;
        state.stepReq = false;
        PlayLayer::onQuit();
    }
};
