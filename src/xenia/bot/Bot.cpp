#include "xenia/bot/Bot.hpp"
#include "xenia/bot/Macro.hpp"
#include "xenia/core/Lang.hpp"
#include "xenia/core/State.hpp"

#include <Geode/Geode.hpp>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>

#include <algorithm>
#include <climits>
#include <cmath>
#include <deque>
#include <vector>

using namespace geode::prelude;

namespace {
    struct BotEngine {
        xenia::bot::Stats stats;
        int frame = 0;
        int progressAtReset = 0;
        int stepsSinceReset = 0;
        bool progressValid = true;
        bool levelMismatch = false;
        size_t playbackIndex = 0;
        bool syncNeeded = false;
        bool dirty = false;
        float frameTimeAccumulator = 0.f;
        int updateTicks = 0;
        int stepTicks = 0;
        int clickWindow = 0;
        bool inHalfTick = false;
        bool injectingInput = false;

        std::vector<xenia::bot::Frame> currentAttemptFrames;
        std::vector<xenia::bot::Sample> currentAttemptSamples;
        int sampleStride = 1;
        double attemptTps = 240.0;
        int aliveFrame = 0;
        std::string bestName;
        int bestLength = 0;
        bool heldButtons[2][4] = {};
        bool freshRecord = false;
        bool recordArmed = false;

        std::deque<xenia::bot::Frame> pendingFrames[2][4];

        void clearPending() {
            for (auto& player : pendingFrames) {
                for (auto& queue : player) {
                    queue.clear();
                }
            }
        }

        void clearHeld() {
            for (auto& player : heldButtons) {
                for (auto& state : player) {
                    state = false;
                }
            }
        }

        void resetRuntime() {
            stats.clicks = 0;
            frame = 0;
            aliveFrame = 0;
            playbackIndex = 0;
            syncNeeded = false;
            recordArmed = false;
            levelMismatch = false;
            currentAttemptFrames.clear();
            currentAttemptSamples.clear();
            clearPending();
            clearHeld();
            stats.p1 = stats.p2 = false;
        }
    };

    BotEngine g_engine;

    constexpr char const* kCbfModId = "syzzi.click_between_frames";
    constexpr char const* kCbfSettingKey = "soft-toggle";
    bool g_cbfOverridden = false;
    bool g_cbfPreviousState = false;

    bool isPlayerDead(GJBaseGameLayer* layer) {
        return layer && layer->m_player1 && layer->m_player1->m_isDead;
    }

    int calculateSampleStride(double tps) {
        return std::max(1, static_cast<int>(std::lround(tps / 240.0)));
    }

    void recordPlayerSample(GJBaseGameLayer* layer) {
        if (g_engine.sampleStride > 1 && g_engine.frame % g_engine.sampleStride != 0) return;
        if (g_engine.currentAttemptSamples.size() >= xenia::bot::MacroManager::kMaxSamples) return;
        if (!g_engine.currentAttemptSamples.empty() && g_engine.currentAttemptSamples.back().frame >= g_engine.frame) return;
        if (!layer->m_player1) return;

        xenia::bot::Sample sample;
        sample.frame = g_engine.frame;
        auto const pos1 = layer->m_player1->getPosition();
        sample.x1 = pos1.x;
        sample.y1 = pos1.y;
        sample.v1 = layer->m_player1->m_yVelocity;

        if (layer->m_player2) {
            auto const pos2 = layer->m_player2->getPosition();
            sample.hasP2 = true;
            sample.x2 = pos2.x;
            sample.y2 = pos2.y;
            sample.v2 = layer->m_player2->m_yVelocity;
        }

        g_engine.currentAttemptSamples.push_back(sample);
    }

    void synchronizePlayerPosition(PlayerObject* player, float x, float y, double v) {
        if (!player) return;
        auto const currentPos = player->getPosition();
        if (currentPos.x != x || currentPos.y != y) {
            player->setPosition({x, y});
        }
        if (player->m_yVelocity != v) {
            player->m_yVelocity = v;
        }
    }

    void applyPlayerCorrection(GJBaseGameLayer* layer, std::vector<xenia::bot::Sample> const& samples) {
        if (samples.empty()) return;
        auto const it = std::ranges::lower_bound(samples, g_engine.frame, {}, &xenia::bot::Sample::frame);
        if (it == samples.end() || it->frame != g_engine.frame) return;

        synchronizePlayerPosition(layer->m_player1, it->x1, it->y1, it->v1);
        if (it->hasP2) {
            synchronizePlayerPosition(layer->m_player2, it->x2, it->y2, it->v2);
        }
    }

    void adoptAttemptData(int length, bool force) {
        if (!xenia::bot::MacroManager::hasCurrent()) return;
        auto& macro = xenia::bot::MacroManager::current();

        bool const isFirst = (g_engine.bestLength == 0);
        if (!force && g_engine.currentAttemptFrames.empty()) return;
        if (!force && !isFirst && length < g_engine.bestLength) return;

        macro.frames = g_engine.currentAttemptFrames;
        macro.samples = g_engine.currentAttemptSamples;
        macro.tps = g_engine.attemptTps;
        g_engine.bestLength = length;
        g_engine.dirty = true;
    }

    void rewindAttemptData(int targetFrame) {
        std::vector<xenia::bot::Frame> remaining;
        bool held[2][4] = {};

        for (auto const& f : g_engine.currentAttemptFrames) {
            if (f.frame >= targetFrame) break;
            remaining.push_back(f);
            if (f.button >= 1 && f.button <= 3) {
                held[f.player2 ? 1 : 0][f.button] = f.down;
            }
        }

        for (int p = 0; p < 2; ++p) {
            for (int b = 1; b <= 3; ++b) {
                if (held[p][b]) {
                    remaining.push_back({targetFrame, false, p == 1, b, false});
                }
            }
        }

        g_engine.currentAttemptFrames = std::move(remaining);
        while (!g_engine.currentAttemptSamples.empty() && g_engine.currentAttemptSamples.back().frame >= targetFrame) {
            g_engine.currentAttemptSamples.pop_back();
        }
    }
}

namespace xenia::bot {
    Stats const& stats() {
        g_engine.stats.frame = g_engine.frame;
        return g_engine.stats;
    }

    int currentFrame() {
        return g_engine.frame;
    }

    double effectiveTps() {
        auto const& s = State::get();
        if (s.bot == BotMode::Playback && s.botOn() && MacroManager::hasCurrent()) {
            return MacroManager::current().tps;
        }
        if (s.tpsOn()) {
            return s.tps;
        }
        return 240.0;
    }

    void syncCbf() {
        auto* mod = Loader::get()->getLoadedMod(kCbfModId);
        if (!mod || !mod->hasSetting(kCbfSettingKey)) return;

        auto const& s = State::get();
        bool const required = s.cbfBypass && s.botOn();

        if (required && !g_cbfOverridden) {
            g_cbfPreviousState = mod->getSettingValue<bool>(kCbfSettingKey);
            g_cbfOverridden = true;
            (void)mod->setSettingValue<bool>(kCbfSettingKey, true);
        } else if (!required && g_cbfOverridden) {
            g_cbfOverridden = false;
            (void)mod->setSettingValue<bool>(kCbfSettingKey, g_cbfPreviousState);
        }
    }

    void restoreCbf() {
        if (!g_cbfOverridden) return;
        auto* mod = Loader::get()->getLoadedMod(kCbfModId);
        g_cbfOverridden = false;
        if (mod && mod->hasSetting(kCbfSettingKey)) {
            (void)mod->setSettingValue<bool>(kCbfSettingKey, g_cbfPreviousState);
        }
    }

    void onReset(int progress, int levelId, std::string const& levelName) {
        int const prevLength = g_engine.aliveFrame;
        bool const wasArmed = g_engine.recordArmed;
        int const startFrame = std::max(0, progress);

        g_engine.frame = startFrame;
        g_engine.progressAtReset = startFrame;
        g_engine.stepsSinceReset = 0;
        g_engine.aliveFrame = startFrame;
        g_engine.playbackIndex = 0;
        g_engine.syncNeeded = true;
        g_engine.recordArmed = false;
        g_engine.levelMismatch = false;

        g_engine.clearPending();
        g_engine.clearHeld();
        g_engine.stats.p1 = g_engine.stats.p2 = false;

        auto const& s = State::get();
        if (s.bot == BotMode::Record && s.botOn() && MacroManager::hasCurrent()) {
            auto& m = MacroManager::current();
            if (g_engine.freshRecord) {
                g_engine.freshRecord = false;
                m.frames.clear();
                m.samples.clear();
                g_engine.currentAttemptFrames.clear();
                g_engine.currentAttemptSamples.clear();
                g_engine.bestLength = 0;
                g_engine.bestName = m.name;
                g_engine.dirty = true;
            } else {
                if (g_engine.bestName != m.name) {
                    g_engine.bestName = m.name;
                    g_engine.bestLength = m.frames.empty() ? 0 : m.frames.back().frame + 1;
                }
                if (wasArmed) {
                    adoptAttemptData(prevLength, false);
                }
                if (startFrame == 0) {
                    g_engine.currentAttemptFrames.clear();
                    g_engine.currentAttemptSamples.clear();
                } else {
                    rewindAttemptData(startFrame);
                }
            }

            if (levelId != 0 && (m.levelId != levelId || m.levelName != levelName)) {
                m.levelId = levelId;
                m.levelName = levelName;
                g_engine.dirty = true;
            }

            g_engine.attemptTps = effectiveTps();
            g_engine.sampleStride = calculateSampleStride(g_engine.attemptTps);
            m.tps = g_engine.bestLength == 0 ? g_engine.attemptTps : m.tps;
            g_engine.recordArmed = true;

            if (g_engine.dirty) {
                g_engine.dirty = false;
                if (auto res = MacroManager::save(); res.isErr()) {
                    log::error("Macro auto-save failed: {}", res.unwrapErr());
                }
            }
        } else if (s.bot == BotMode::Playback && s.botOn() && MacroManager::hasCurrent()) {
            auto const& m = MacroManager::current();
            g_engine.levelMismatch = (m.levelId != 0 && levelId != 0 && m.levelId != levelId);
            g_engine.currentAttemptFrames.clear();
            g_engine.currentAttemptSamples.clear();
        } else {
            g_engine.currentAttemptFrames.clear();
            g_engine.currentAttemptSamples.clear();
        }
    }

    void onModeChanged() {
        g_engine.recordArmed = false;
        g_engine.clearPending();
        g_engine.clearHeld();

        auto const& s = State::get();
        g_engine.currentAttemptFrames.clear();
        g_engine.currentAttemptSamples.clear();
        g_engine.bestLength = 0;
        g_engine.bestName.clear();
        g_engine.freshRecord = (s.bot == BotMode::Record);

        if (s.bot != BotMode::Disable && s.botOn()) {
            Loader::get()->queueInMainThread([] {
                if (auto* pl = PlayLayer::get()) {
                    pl->resetLevel();
                }
            });
        }
    }

    void resetRuntime() {
        g_engine.resetRuntime();
    }

    void requestSync() {
        g_engine.syncNeeded = true;
    }

    void injectButton(GJBaseGameLayer* layer, bool down, int button, bool player1, bool record) {
        if (!layer) return;
        bool const prev = g_engine.injectingInput;
        g_engine.injectingInput = !record;
        layer->handleButton(down, button, player1);
        g_engine.injectingInput = prev;
        if (button == 1) {
            (player1 ? g_engine.stats.p1 : g_engine.stats.p2) = down;
        }
    }

    std::string statusLine() {
        auto const& s = State::get();
        if (s.bot == BotMode::Disable) return "";
        if (s.safeMode) return xenia::tr("Bot: Safe Mode ON (bot disabled)", "Bot: Safe Mode ON (bot mati)");
        if (!MacroManager::hasCurrent()) return xenia::tr("Bot: no macro loaded", "Bot: belum ada makro dimuat");

        auto const& m = MacroManager::current();
        if (s.bot == BotMode::Record) {
            if (!g_engine.recordArmed) {
                return fmt::format("REC {}: waiting for attempt start", m.name);
            }
            return fmt::format("REC {}: {} inputs", m.name, std::max(g_engine.currentAttemptFrames.size(), m.frames.size()));
        }

        if (g_engine.levelMismatch) {
            return fmt::format("PLAY {}: {}/{} (mismatched level: {})", m.name, g_engine.playbackIndex, m.frames.size(), m.levelName);
        }
        return fmt::format("PLAY {}: {}/{}{}", m.name, g_engine.playbackIndex, m.frames.size(), m.samples.empty() ? "" : " [POS]");
    }

    void commitIfIdle() {
        auto const& s = State::get();
        if (s.bot == BotMode::Record && s.botOn() && g_engine.recordArmed) {
            adoptAttemptData(g_engine.aliveFrame, false);
        }
    }

    void commitLevelComplete() {
        auto const& s = State::get();
        if (s.bot == BotMode::Record && s.botOn() && g_engine.recordArmed) {
            adoptAttemptData(g_engine.aliveFrame, true);
            g_engine.bestLength = INT_MAX;
        }
    }

    void markSaved() {
        g_engine.dirty = false;
    }

    void flush() {
        commitIfIdle();
        g_engine.currentAttemptFrames.clear();
        g_engine.currentAttemptSamples.clear();
        g_engine.recordArmed = false;

        if (!g_engine.dirty) return;
        g_engine.dirty = false;
        if (auto res = MacroManager::save(); res.isErr()) {
            log::error("Macro flush save failed: {}", res.unwrapErr());
        }
    }
}

class $modify(XeniaBotLayer, GJBaseGameLayer) {
    void update(float dt) {
        if (typeinfo_cast<PlayLayer*>(this)) {
            g_engine.frameTimeAccumulator += dt;
            g_engine.updateTicks++;
            if (g_engine.frameTimeAccumulator >= 0.5f) {
                g_engine.stats.fps = static_cast<int>(std::lround(g_engine.updateTicks / g_engine.frameTimeAccumulator));
                g_engine.stats.tps = static_cast<int>(std::lround(g_engine.stepTicks / g_engine.frameTimeAccumulator));
                g_engine.stats.cps = static_cast<int>(std::lround(g_engine.clickWindow / g_engine.frameTimeAccumulator));
                g_engine.clickWindow = 0;
                g_engine.frameTimeAccumulator = 0.f;
                g_engine.updateTicks = g_engine.stepTicks = 0;
            }
        }
        GJBaseGameLayer::update(dt);
    }

    void processCommands(float dt, bool isHalfTick, bool isLastTick) {
        bool const inLevel = typeinfo_cast<PlayLayer*>(this) != nullptr;
        auto const& s = xenia::State::get();
        bool const fullTick = inLevel && !isHalfTick;

        if (fullTick && g_engine.progressValid) {
            g_engine.frame = static_cast<int>(m_gameState.m_currentProgress);
        }

        if (fullTick && s.botOn() && xenia::bot::MacroManager::hasCurrent() && !isPlayerDead(this)) {
            if (s.bot == xenia::BotMode::Record && g_engine.recordArmed) {
                recordPlayerSample(this);
            } else if (s.bot == xenia::BotMode::Playback && !g_engine.levelMismatch) {
                applyPlayerCorrection(this, xenia::bot::MacroManager::current().samples);
            }
        }

        if (inLevel && s.bot == xenia::BotMode::Playback && s.botOn() && xenia::bot::MacroManager::hasCurrent() && !isPlayerDead(this)) {
            auto const& frames = xenia::bot::MacroManager::current().frames;
            if (g_engine.syncNeeded) {
                auto const it = std::ranges::lower_bound(frames, g_engine.frame, {}, &xenia::bot::Frame::frame);
                g_engine.playbackIndex = static_cast<size_t>(std::distance(frames.begin(), it));
                g_engine.syncNeeded = false;
                g_engine.clearPending();
            }

            while (g_engine.playbackIndex > 0 && frames[g_engine.playbackIndex - 1].frame > g_engine.frame) {
                g_engine.playbackIndex--;
                g_engine.clearPending();
            }

            auto executeFrame = [this](xenia::bot::Frame const& f) {
                if (f.button == 1) {
                    (f.player2 ? g_engine.stats.p2 : g_engine.stats.p1) = f.down;
                }
                g_engine.injectingInput = true;
                GJBaseGameLayer::handleButton(f.down, f.button, !f.player2);
                g_engine.injectingInput = false;
            };

            while (g_engine.playbackIndex < frames.size() && frames[g_engine.playbackIndex].frame <= g_engine.frame) {
                if (isHalfTick && !frames[g_engine.playbackIndex].half) break;
                executeFrame(frames[g_engine.playbackIndex++]);
            }
        }

        g_engine.inHalfTick = inLevel && isHalfTick;
        GJBaseGameLayer::processCommands(dt, isHalfTick, isLastTick);
        g_engine.inHalfTick = false;

        if (fullTick) {
            g_engine.frame++;
            g_engine.stepTicks++;
            g_engine.stepsSinceReset++;
            if (g_engine.progressValid && g_engine.stepsSinceReset == 12 && static_cast<int>(m_gameState.m_currentProgress) == g_engine.progressAtReset) {
                g_engine.progressValid = false;
                g_engine.frame = g_engine.progressAtReset + g_engine.stepsSinceReset;
            }
            if (!isPlayerDead(this)) {
                g_engine.aliveFrame = g_engine.frame;
            }
        }
    }

    void handleButton(bool down, int button, bool isPlayer1) {
        if (down && button == 1) {
            g_engine.clickWindow++;
            g_engine.stats.clicks++;
        }

        if (g_engine.injectingInput) {
            GJBaseGameLayer::handleButton(down, button, isPlayer1);
            return;
        }

        bool const inLevel = typeinfo_cast<PlayLayer*>(this) != nullptr;
        auto const& s = xenia::State::get();

        if (inLevel && s.botOn()) {
            if (s.bot == xenia::BotMode::Playback) {
                return;
            }
            if (s.bot == xenia::BotMode::Record && g_engine.recordArmed && xenia::bot::MacroManager::hasCurrent()
                && button >= 1 && button <= 3 && !isPlayerDead(this)) {
                bool& held = g_engine.heldButtons[isPlayer1 ? 0 : 1][button];
                if (down != held) {
                    held = down;
                    int const f = g_engine.currentAttemptFrames.empty()
                        ? g_engine.frame
                        : std::max(g_engine.frame, g_engine.currentAttemptFrames.back().frame);
                    g_engine.currentAttemptFrames.push_back({f, down, !isPlayer1, button, g_engine.inHalfTick});
                    g_engine.dirty = true;
                }
            }
        }

        if (inLevel && button == 1) {
            (isPlayer1 ? g_engine.stats.p1 : g_engine.stats.p2) = down;
        }

        GJBaseGameLayer::handleButton(down, button, isPlayer1);
    }
};

class $modify(XeniaBotPlay, PlayLayer) {
    void resetLevel() {
        PlayLayer::resetLevel();
        auto const& s = xenia::State::get();

        if (s.particleFixed && s.botOn()) {
            m_extraDelta = 0.f;
        }
        xenia::bot::syncCbf();

        int const levelId = m_level ? static_cast<int>(m_level->m_levelID.value()) : 0;
        std::string const levelName = m_level ? std::string(m_level->m_levelName) : std::string();

        xenia::bot::onReset(static_cast<int>(m_gameState.m_currentProgress), levelId, levelName);
    }

    void levelComplete() {
        xenia::bot::commitLevelComplete();
        xenia::bot::flush();
        PlayLayer::levelComplete();
    }

    void onQuit() {
        xenia::bot::flush();
        xenia::bot::restoreCbf();
        xenia::bot::resetRuntime();
        PlayLayer::onQuit();
    }
};
