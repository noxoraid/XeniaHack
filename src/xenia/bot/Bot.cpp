#include "xenia/core/Lang.hpp"
#include <Geode/Geode.hpp>
#include <algorithm>
#include <cmath>
#include <climits>
#include <deque>
#include <Geode/modify/GJBaseGameLayer.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Bot.hpp"
#include "xenia/bot/Macro.hpp"
using namespace geode::prelude;

namespace {
    xenia::bot::Stats g_stats;
    int g_frame = 0;
    int g_progAtReset = 0;
    int g_stepsSince = 0;
    bool g_progOk = true;
    bool g_levelMismatch = false;
    size_t g_idx = 0;
    bool g_syncNeeded = false;
    bool g_dirty = false;
    float g_acc = 0.f;
    int g_updates = 0, g_steps = 0, g_clickWin = 0;
    bool g_inHalf = false;
    bool g_injecting = false;

    std::vector<xenia::bot::Frame> g_att;
    std::vector<xenia::bot::Sample> g_attSamples;
    int g_attStride = 1;
    double g_attTps = 240.0;
    int g_alive = 0;
    std::string g_bestName;
    int g_bestLen = 0;
    bool g_held[2][4] = {};
    bool g_freshRec = false;
    bool g_recArmed = false;

    std::deque<xenia::bot::Frame> g_pend[2][4];
    void clearPending() { for (auto& p : g_pend) for (auto& q : p) q.clear(); }
    void clearHeld() { for (auto& p : g_held) for (auto& h : p) h = false; }
    bool playerDead(GJBaseGameLayer* l) { return l->m_player1 && l->m_player1->m_isDead; }
    int strideFor(double tps) { return std::max(1, (int)std::lround(tps / 240.0)); }

    void recordSample(GJBaseGameLayer* l) {
        if (g_attStride > 1 && g_frame % g_attStride != 0) return;
        if (g_attSamples.size() >= xenia::bot::MacroManager::kMaxSamples) return;
        if (!g_attSamples.empty() && g_attSamples.back().frame >= g_frame) return;
        if (!l->m_player1) return;
        xenia::bot::Sample sp;
        sp.frame = g_frame;
        auto a = l->m_player1->getPosition();
        sp.x1 = a.x;
        sp.y1 = a.y;
        sp.v1 = l->m_player1->m_yVelocity;
        if (l->m_player2) {
            auto b = l->m_player2->getPosition();
            sp.hasP2 = true;
            sp.x2 = b.x;
            sp.y2 = b.y;
            sp.v2 = l->m_player2->m_yVelocity;
        }
        g_attSamples.push_back(sp);
    }

    void fixPlayer(PlayerObject* p, float x, float y, double v) {
        if (!p) return;
        auto pos = p->getPosition();
        if (pos.x != x || pos.y != y) p->setPosition({x, y});
        if (p->m_yVelocity != v) p->m_yVelocity = v;
    }

    void applySample(GJBaseGameLayer* l, std::vector<xenia::bot::Sample> const& sm) {
        if (sm.empty()) return;
        auto it = std::lower_bound(sm.begin(), sm.end(), g_frame,
            [](xenia::bot::Sample const& sp, int v) { return sp.frame < v; });
        if (it == sm.end() || it->frame != g_frame) return;
        fixPlayer(l->m_player1, it->x1, it->y1, it->v1);
        if (it->hasP2) fixPlayer(l->m_player2, it->x2, it->y2, it->v2);
    }
}

namespace {
    constexpr char const* kCbfId = "syzzi.click_between_frames";
    constexpr char const* kCbfSetting = "soft-toggle";
    bool g_cbfForced = false;
    bool g_cbfPrev = false;
}

namespace xenia::bot {
    Stats const& stats() { g_stats.frame = g_frame; return g_stats; }
    int currentFrame() { return g_frame; }

    double effectiveTps() {
        auto& s = State::get();
        if (s.bot == BotMode::Playback && s.botOn() && MacroManager::hasCurrent()) return MacroManager::current().tps;
        if (s.tpsOn()) return s.tps;
        return 240.0;
    }

    void syncCbf() {
        auto mod = Loader::get()->getLoadedMod(kCbfId);
        if (!mod || !mod->hasSetting(kCbfSetting)) return;
        auto& s = State::get();
        bool want = s.cbfBypass && s.botOn();
        if (want && !g_cbfForced) {
            g_cbfPrev = mod->getSettingValue<bool>(kCbfSetting);
            g_cbfForced = true;
            (void)mod->setSettingValue<bool>(kCbfSetting, true);
        } else if (!want && g_cbfForced) {
            g_cbfForced = false;
            (void)mod->setSettingValue<bool>(kCbfSetting, g_cbfPrev);
        }
    }

    void restoreCbf() {
        if (!g_cbfForced) return;
        auto mod = Loader::get()->getLoadedMod(kCbfId);
        g_cbfForced = false;
        if (mod && mod->hasSetting(kCbfSetting)) (void)mod->setSettingValue<bool>(kCbfSetting, g_cbfPrev);
    }

    static void adoptAttempt(int len, bool force) {
        if (!MacroManager::hasCurrent()) return;
        auto& m = MacroManager::current();
        bool first = (g_bestLen == 0);
        if (!force && g_att.empty()) return;
        if (!force && !first && len < g_bestLen) return;
        m.frames = g_att;
        m.samples = g_attSamples;
        m.tps = g_attTps;
        g_bestLen = len;
        g_dirty = true;
    }

    static void rewindAttempt(int startFrame) {
        std::vector<xenia::bot::Frame> kept;
        bool held[2][4] = {};
        for (auto const& f : g_att) {
            if (f.frame >= startFrame) break;
            kept.push_back(f);
            if (f.button >= 1 && f.button <= 3) held[f.player2 ? 1 : 0][f.button] = f.down;
        }
        for (int p = 0; p < 2; p++)
            for (int b = 1; b <= 3; b++)
                if (held[p][b]) kept.push_back({startFrame, false, p == 1, b});
        g_att = std::move(kept);
        while (!g_attSamples.empty() && g_attSamples.back().frame >= startFrame) g_attSamples.pop_back();
    }

    void onReset(int progress, int levelId, std::string const& levelName) {
        int prevLen = g_alive;
        bool wasArmed = g_recArmed;
        int start = std::max(0, progress);
        g_frame = start;
        g_progAtReset = start;
        g_stepsSince = 0;
        g_alive = start;
        g_idx = 0;
        g_syncNeeded = true;
        g_recArmed = false;
        g_levelMismatch = false;
        clearPending();
        clearHeld();
        g_stats.p1 = g_stats.p2 = false;
        auto& s = State::get();
        if (s.bot == BotMode::Record && s.botOn() && MacroManager::hasCurrent()) {
            auto& m = MacroManager::current();
            if (g_freshRec) {
                g_freshRec = false;
                m.frames.clear();
                m.samples.clear();
                g_att.clear();
                g_attSamples.clear();
                g_bestLen = 0;
                g_bestName = m.name;
                g_dirty = true;
            } else {
                if (g_bestName != m.name) {
                    g_bestName = m.name;
                    g_bestLen = m.frames.empty() ? 0 : m.frames.back().frame + 1;
                }
                if (wasArmed) adoptAttempt(prevLen, false);
                if (start == 0) {
                    g_att.clear();
                    g_attSamples.clear();
                } else {
                    rewindAttempt(start);
                }
            }
            if (levelId != 0 && (m.levelId != levelId || m.levelName != levelName)) {
                m.levelId = levelId;
                m.levelName = levelName;
                g_dirty = true;
            }
            g_attTps = effectiveTps();
            g_attStride = strideFor(g_attTps);
            m.tps = g_bestLen == 0 ? g_attTps : m.tps;
            g_recArmed = true;
            if (g_dirty) {
                g_dirty = false;
                if (auto err = MacroManager::save(); !err.empty()) log::error("Failed to save macro: {}", err);
            }
        } else if (s.bot == BotMode::Playback && s.botOn() && MacroManager::hasCurrent()) {
            auto& m = MacroManager::current();
            g_levelMismatch = m.levelId != 0 && levelId != 0 && m.levelId != levelId;
            g_att.clear();
            g_attSamples.clear();
        } else {
            g_att.clear();
            g_attSamples.clear();
        }
    }

    void onModeChanged() {
        g_recArmed = false;
        clearPending();
        clearHeld();
        auto& s = State::get();
        g_att.clear();
        g_attSamples.clear();
        g_bestLen = 0;
        g_bestName.clear();
        g_freshRec = (s.bot == BotMode::Record);
        if (s.bot != BotMode::Disable && s.botOn()) {
            Loader::get()->queueInMainThread([] {
                if (auto pl = PlayLayer::get()) pl->resetLevel();
            });
        }
    }

    void resetRuntime() {
        g_stats.clicks = 0;
        g_frame = 0;
        g_alive = 0;
        g_idx = 0;
        g_syncNeeded = false;
        g_recArmed = false;
        g_levelMismatch = false;
        g_att.clear();
        g_attSamples.clear();
        clearPending();
        clearHeld();
        g_stats.p1 = g_stats.p2 = false;
    }

    void requestSync() { g_syncNeeded = true; }

    void injectButton(GJBaseGameLayer* layer, bool down, int button, bool player1, bool record) {
        if (!layer) return;
        bool prev = g_injecting;
        g_injecting = !record;
        layer->handleButton(down, button, player1);
        g_injecting = prev;
        if (button == 1) (player1 ? g_stats.p1 : g_stats.p2) = down;
    }

    std::string statusLine() {
        auto& s = State::get();
        if (s.bot == BotMode::Disable) return "";
        if (s.safeMode) return xenia::tr("Bot: Safe Mode ON (bot disabled)", "Bot: Safe Mode ON (bot mati)");
        if (!MacroManager::hasCurrent()) return xenia::tr("Bot: no macro", "Bot: belum ada makro");
        auto& m = MacroManager::current();
        if (s.bot == BotMode::Record) {
            if (!g_recArmed) return xenia::trf("REC {}: waiting for attempt restart", "REC {}: menunggu restart attempt", m.name);
            return xenia::trf("REC {}: {} inputs", "REC {}: {} input", m.name, std::max(g_att.size(), m.frames.size()));
        }
        if (g_levelMismatch) return xenia::trf("PLAY {}: {}/{} (macro from another level: {})", "PLAY {}: {}/{} (makro dari level lain: {})", m.name, g_idx, m.frames.size(), m.levelName);
        return fmt::format("PLAY {}: {}/{}{}", m.name, g_idx, m.frames.size(), m.samples.empty() ? "" : " FIX");
    }

    void commitIfIdle() {
        auto& s = State::get();
        if (s.bot == BotMode::Record && s.botOn() && g_recArmed) adoptAttempt(g_alive, false);
    }

    void commitLevelComplete() {
        auto& s = State::get();
        if (s.bot == BotMode::Record && s.botOn() && g_recArmed) {
            adoptAttempt(g_alive, true);
            g_bestLen = INT_MAX;
        }
    }

    void markSaved() { g_dirty = false; }

    void flush() {
        commitIfIdle();
        g_att.clear();
        g_attSamples.clear();
        g_recArmed = false;
        if (!g_dirty) return;
        g_dirty = false;
        if (auto err = MacroManager::save(); !err.empty()) {
            log::error("Failed to save macro: {}", err);
        }
    }
}

class $modify(XeniaBotLayer, GJBaseGameLayer) {
    void update(float dt) {
        if (typeinfo_cast<PlayLayer*>(this)) {
            g_acc += dt;
            g_updates++;
            if (g_acc >= 0.5f) {
                g_stats.fps = (int)std::lround(g_updates / g_acc);
                g_stats.tps = (int)std::lround(g_steps / g_acc);
                g_stats.cps = (int)std::lround(g_clickWin / g_acc);
                g_clickWin = 0;
                g_acc = 0.f;
                g_updates = g_steps = 0;
            }
        }
        GJBaseGameLayer::update(dt);
    }

    void processCommands(float dt, bool isHalfTick, bool isLastTick) {
        bool inLevel = typeinfo_cast<PlayLayer*>(this) != nullptr;
        auto& s = xenia::State::get();

        bool fullTick = inLevel && !isHalfTick;
        if (fullTick && g_progOk) g_frame = (int)m_gameState.m_currentProgress;
        if (fullTick && s.botOn() && xenia::bot::MacroManager::hasCurrent() && !playerDead(this)) {
            if (s.bot == xenia::BotMode::Record && g_recArmed) {
                recordSample(this);
            } else if (s.bot == xenia::BotMode::Playback && !g_levelMismatch) {
                applySample(this, xenia::bot::MacroManager::current().samples);
            }
        }

        if (inLevel && s.bot == xenia::BotMode::Playback && s.botOn() && xenia::bot::MacroManager::hasCurrent()
            && !playerDead(this)) {
            auto& fr = xenia::bot::MacroManager::current().frames;
            if (g_syncNeeded) {

                g_idx = (size_t)(std::lower_bound(fr.begin(), fr.end(), g_frame,
                    [](xenia::bot::Frame const& f, int v) { return f.frame < v; }) - fr.begin());
                g_syncNeeded = false;
                clearPending();
            }
            while (g_idx > 0 && fr[g_idx - 1].frame > g_frame) {
                g_idx--;
                clearPending();
            }
            auto exec = [&](xenia::bot::Frame const& f) {
                if (f.button == 1) (f.player2 ? g_stats.p2 : g_stats.p1) = f.down;
                g_injecting = true;
                GJBaseGameLayer::handleButton(f.down, f.button, !f.player2);
                g_injecting = false;
            };

            while (g_idx < fr.size() && fr[g_idx].frame <= g_frame) {
                if (isHalfTick && !fr[g_idx].half) break;
                exec(fr[g_idx++]);
            }
        }
        g_inHalf = inLevel && isHalfTick;
        GJBaseGameLayer::processCommands(dt, isHalfTick, isLastTick);
        g_inHalf = false;
        if (fullTick) {
            g_frame++;
            g_steps++;
            g_stepsSince++;
            if (g_progOk && g_stepsSince == 12 && (int)m_gameState.m_currentProgress == g_progAtReset) {
                g_progOk = false;
                g_frame = g_progAtReset + g_stepsSince;
            }
            if (!playerDead(this)) g_alive = g_frame;
        }
    }

    void handleButton(bool down, int button, bool isPlayer1) {

        if (down && button == 1) {
            g_clickWin++;
            g_stats.clicks++;
        }
        if (g_injecting) {
            GJBaseGameLayer::handleButton(down, button, isPlayer1);
            return;
        }
        bool inLevel = typeinfo_cast<PlayLayer*>(this) != nullptr;
        auto& s = xenia::State::get();
        if (inLevel && s.botOn()) {

            if (s.bot == xenia::BotMode::Playback) return;
            if (s.bot == xenia::BotMode::Record && g_recArmed && xenia::bot::MacroManager::hasCurrent()
                && button >= 1 && button <= 3 && !playerDead(this)) {

                bool& held = g_held[isPlayer1 ? 0 : 1][button];
                if (down != held) {
                    held = down;
                    int fr = g_att.empty() ? g_frame : std::max(g_frame, g_att.back().frame);
                    g_att.push_back({fr, down, !isPlayer1, button, g_inHalf});
                    g_dirty = true;
                }
            }
        }
        if (inLevel && button == 1) (isPlayer1 ? g_stats.p1 : g_stats.p2) = down;
        GJBaseGameLayer::handleButton(down, button, isPlayer1);
    }
};

class $modify(XeniaBotPlay, PlayLayer) {
    void resetLevel() {
        PlayLayer::resetLevel();
        auto& s = xenia::State::get();

        if (s.particleFixed && s.botOn()) m_extraDelta = 0.f;
        xenia::bot::syncCbf();
        int lid = m_level ? (int)m_level->m_levelID.value() : 0;
        std::string lname = m_level ? std::string(m_level->m_levelName) : std::string();
        xenia::bot::onReset((int)m_gameState.m_currentProgress, lid, lname);
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
