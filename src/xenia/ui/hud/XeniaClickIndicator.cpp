#include "xenia/ui/hud/XeniaClickIndicator.hpp"
#include "xenia/bot/Bot.hpp"
#include "xenia/bot/Macro.hpp"
#include "xenia/core/State.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include <algorithm>
#include <cmath>
using namespace geode::prelude;

namespace xenia::ui {
    namespace {
        struct Ev {
            int start = 0;
            int end = 0;
            int lane = 0;
        };

        std::vector<Ev> s_ev;
        struct Key {
            std::string name;
            size_t n = 0;
            int first = 0, last = 0, level = 0;
            double tps = 0.0;
            bool operator==(Key const&) const = default;
        };
        Key s_key;
        int s_maxHold = 0;
        bool s_hasP2 = false;

        Key keyOf(bot::Macro const& m) {
            return {m.name, m.frames.size(), m.frames.empty() ? 0 : m.frames.front().frame,
                    m.frames.empty() ? 0 : m.frames.back().frame, m.levelId, m.tps};
        }

        ccColor4F col(int const c[3], float a) {
            return {c[0] / 255.f, c[1] / 255.f, c[2] / 255.f, std::clamp(a, 0.f, 1.f)};
        }

        void rect(CCDrawNode* d, float x0, float y0, float x1, float y1, ccColor4F c) {
            if (y1 <= y0 || x1 <= x0) return;
            CCPoint v[4] = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
            d->drawPolygon(v, 4, c, 0.f, c);
        }
    }

    ClickIndicator* ClickIndicator::create() {
        auto r = new ClickIndicator();
        if (r && r->init()) {
            r->autorelease();
            return r;
        }
        CC_SAFE_DELETE(r);
        return nullptr;
    }

    bool ClickIndicator::init() {
        if (!CCNode::init()) return false;
        m_draw = CCDrawNode::create();
        this->addChild(m_draw, 1);
        this->scheduleUpdate();
        return true;
    }

    void ClickIndicator::rebuildCache() {
        s_ev.clear();
        s_maxHold = 0;
        s_hasP2 = false;
        auto const& m = bot::MacroManager::current();
        int open[2] = {-1, -1};
        for (auto const& f : m.frames) {
            if (f.button != 1) continue;
            int lane = f.player2 ? 1 : 0;
            if (f.down) {
                if (open[lane] >= 0) s_ev.push_back({open[lane], f.frame, lane});
                open[lane] = f.frame;
                if (lane == 1) s_hasP2 = true;
            } else if (open[lane] >= 0) {
                s_ev.push_back({open[lane], f.frame, lane});
                open[lane] = -1;
            }
        }
        for (int l = 0; l < 2; l++) {
            if (open[l] >= 0) s_ev.push_back({open[l], open[l], l});
        }
        std::stable_sort(s_ev.begin(), s_ev.end(), [](Ev const& a, Ev const& b) { return a.start < b.start; });
        for (auto const& e : s_ev) s_maxHold = std::max(s_maxHold, e.end - e.start);
        s_key = keyOf(m);
    }

    void ClickIndicator::relocate(int cur) {
        auto it = std::lower_bound(s_ev.begin(), s_ev.end(), cur, [](Ev const& e, int v) { return e.start < v; });
        m_nextHit = static_cast<size_t>(std::distance(s_ev.begin(), it));
    }

    void ClickIndicator::firePerfect(int lane) {
        auto& s = State::get();
        m_flash[lane] = 1.f;
        if (!s.ciPerfectOn) return;
        auto lbl = makeLabel("Perfect", 120.f, static_cast<float>(s.ciTextSize));
        float lx = static_cast<float>(s.ciX) + (s_hasP2 ? 2 : 1) * static_cast<float>(s.ciW) + (s_hasP2 ? 6.f : 0.f) + 8.f;
        float ly = static_cast<float>(s.ciY) + 10.f + lane * 14.f;
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setColor({(GLubyte)s.ciPerfect[0], (GLubyte)s.ciPerfect[1], (GLubyte)s.ciPerfect[2]});
        lbl->setPosition({lx, ly});
        this->addChild(lbl, 2);
        lbl->runAction(CCSequence::create(
            CCSpawn::create(CCMoveBy::create(0.55f, {0.f, 16.f}),
                            CCSequence::create(CCDelayTime::create(0.2f), CCFadeOut::create(0.35f), nullptr), nullptr),
            CCCallFunc::create(lbl, callfunc_selector(CCNode::removeMeAndCleanup)), nullptr));
    }

    void ClickIndicator::update(float dt) {
        auto& s = State::get();
        m_draw->clear();

        bool show = s.clickInd && s.bot == BotMode::Playback && s.botOn() && bot::MacroManager::hasCurrent();
        if (!show) {
            m_lastCur = -1;
            return;
        }

        auto const& macro = bot::MacroManager::current();
        if (!(s_key == keyOf(macro))) {
            rebuildCache();
            m_lastCur = -1;
        }
        if (s_ev.empty()) return;

        double tps = macro.tps > 0.0 ? macro.tps : 240.0;
        int cur = bot::currentFrame();

        // Deteksi lompatan (restart / checkpoint) -> cari ulang tanpa memicu Perfect.
        if (m_lastCur < 0 || cur < m_lastCur || cur - m_lastCur > static_cast<int>(tps * 0.25)) {
            relocate(cur);
        } else {
            while (m_nextHit < s_ev.size() && s_ev[m_nextHit].start < cur) {
                firePerfect(s_ev[m_nextHit].lane);
                m_nextHit++;
            }
        }
        m_lastCur = cur;

        float const op = static_cast<float>(s.ciOpacity);
        float const len = static_cast<float>(s.ciLen);
        float const w = static_cast<float>(s.ciW);
        float const baseX = static_cast<float>(s.ciX);
        float const baseY = static_cast<float>(s.ciY);
        double const ahead = std::max(1.0, s.ciAhead * tps);
        int const lanes = s_hasP2 ? 2 : 1;
        auto laneX = [&](int l) { return baseX + l * (w + 6.f); };

        // Latar lane
        for (int l = 0; l < lanes; l++) {
            rect(m_draw, laneX(l), baseY, laneX(l) + w, baseY + len, {0.f, 0.f, 0.f, 0.35f * op});
        }

        // Cari event yang terlihat
        auto lo = std::lower_bound(s_ev.begin(), s_ev.end(), cur - s_maxHold, [](Ev const& e, int v) { return e.start < v; });
        auto hi = std::upper_bound(s_ev.begin(), s_ev.end(), cur + static_cast<int>(ahead), [](int v, Ev const& e) { return v < e.start; });

        bool held[2] = {false, false};
        bool haveTarget[2] = {false, false};
        float tY0[2] = {0, 0}, tY1[2] = {0, 0};

        for (auto it = lo; it != hi; ++it) {
            float ys = baseY + static_cast<float>((it->start - cur) / ahead) * len;
            float ye = baseY + static_cast<float>((it->end - cur) / ahead) * len;
            if (ye <= baseY && it->end > it->start) continue; // sudah selesai
            if (it->end <= cur) continue;
            bool isHeld = it->start < cur && it->end > cur;
            if (isHeld) held[it->lane] = true;

            float bottom = std::max(ys, baseY);
            float top = std::min(std::max(ye, ys + 4.f), baseY + len);
            if (top <= bottom) continue;

            float t = std::clamp((bottom - baseY) / len, 0.f, 1.f);
            float a = op * (1.f - 0.55f * t);
            float x = laneX(it->lane);
            rect(m_draw, x + 1.f, bottom, x + w - 1.f, top, col(s.ciNote, a));

            if (!haveTarget[it->lane]) {
                haveTarget[it->lane] = true;
                tY0[it->lane] = bottom;
                tY1[it->lane] = top;
            }
        }

        for (int l = 0; l < lanes; l++) {
            m_flash[l] = std::max(0.f, m_flash[l] - dt * 4.f);
            float x = laneX(l);
            float glow = std::max(m_flash[l], held[l] ? 0.6f : 0.f);

            if (s.ciMode == 0) {
                // Line: garis hit tetap di dasar lane
                rect(m_draw, x - 3.f, baseY - 1.f, x + w + 3.f, baseY + 1.f, col(s.ciLine, op));
                if (glow > 0.f) rect(m_draw, x - 4.f, baseY - 4.f, x + w + 4.f, baseY + 4.f, col(s.ciLine, 0.5f * glow * op));
            } else if (haveTarget[l]) {
                // Follow zone: kotak yang menempel & mengikuti note terdekat sampai kena
                float pad = 3.f;
                float z0 = std::max(baseY - pad, tY0[l] - pad);
                float z1 = std::min(baseY + len, tY1[l] + pad);
                float fa = (0.18f + 0.35f * glow) * op;
                rect(m_draw, x - 2.f, z0, x + w + 2.f, z1, col(s.ciLine, fa));
                // border tipis
                ccColor4F b = col(s.ciLine, op);
                rect(m_draw, x - 2.f, z0, x + w + 2.f, z0 + 1.f, b);
                rect(m_draw, x - 2.f, z1 - 1.f, x + w + 2.f, z1, b);
                rect(m_draw, x - 2.f, z0, x - 1.f, z1, b);
                rect(m_draw, x + w + 1.f, z0, x + w + 2.f, z1, b);
            } else if (glow > 0.f) {
                // zone sesaat setelah hit, di posisi hit
                rect(m_draw, x - 2.f, baseY - 4.f, x + w + 2.f, baseY + 4.f, col(s.ciLine, 0.5f * glow * op));
            }
        }
    }
}
