#include "xenia/ui/hud/XeniaClickIndicator.hpp"
#include "xenia/bot/Bot.hpp"
#include "xenia/bot/Macro.hpp"
#include "xenia/core/State.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include <algorithm>
#include <cctype>
#include <cmath>
using namespace geode::prelude;

namespace xenia::ui {
    std::string colorToHex(int const c[3]) {
        return fmt::format("{:02X}{:02X}{:02X}", c[0], c[1], c[2]);
    }

    bool hexToColor(std::string const& in, int c[3]) {
        std::string s = in;
        if (!s.empty() && s[0] == '#') s.erase(0, 1);
        if (s.size() != 6) return false;
        for (char ch : s) if (!std::isxdigit(static_cast<unsigned char>(ch))) return false;
        for (int i = 0; i < 3; i++) c[i] = std::stoi(s.substr(i * 2, 2), nullptr, 16);
        return true;
    }

    namespace {
        struct Ev {
            int start = 0;
            int end = 0;
            int lane = 0;
            bool pos = false;
            float x0 = 0.f, y0 = 0.f, x1 = 0.f;
        };

        struct Key {
            std::string name;
            size_t n = 0;
            int first = 0, last = 0, level = 0;
            size_t samples = 0;
            double tps = 0.0;
            bool operator==(Key const&) const = default;
        };

        std::vector<Ev> s_ev;
        Key s_key;
        bool s_keyValid = false;
        int s_maxHold = 0;
        bool s_hasP2 = false;

        Key keyOf(bot::Macro const& m) {
            return {m.name, m.frames.size(), m.frames.empty() ? 0 : m.frames.front().frame,
                    m.frames.empty() ? 0 : m.frames.back().frame, m.levelId, m.samples.size(), m.tps};
        }

        ccColor4F col(int const c[3], float a) {
            return {c[0] / 255.f, c[1] / 255.f, c[2] / 255.f, std::clamp(a, 0.f, 1.f)};
        }

        void rect(CCDrawNode* d, float x0, float y0, float x1, float y1, ccColor4F c) {
            if (y1 <= y0 || x1 <= x0) return;
            CCPoint v[4] = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
            d->drawPolygon(v, 4, c, 0.f, c);
        }

        void pickPos(bot::Sample const& s, int lane, float& x, float& y) {
            if (lane == 1 && s.hasP2) { x = s.x2; y = s.y2; }
            else { x = s.x1; y = s.y1; }
        }

        bool posAt(std::vector<bot::Sample> const& sm, int frame, int lane, float& x, float& y) {
            if (sm.empty()) return false;
            auto it = std::lower_bound(sm.begin(), sm.end(), frame, [](bot::Sample const& a, int f) { return a.frame < f; });
            if (it == sm.end()) { pickPos(sm.back(), lane, x, y); return true; }
            if (it == sm.begin() || it->frame == frame) { pickPos(*it, lane, x, y); return true; }
            auto pv = it - 1;
            float ax, ay, bx, by;
            pickPos(*pv, lane, ax, ay);
            pickPos(*it, lane, bx, by);
            float t = static_cast<float>(frame - pv->frame) / static_cast<float>(std::max(1, it->frame - pv->frame));
            x = ax + (bx - ax) * t;
            y = ay + (by - ay) * t;
            return true;
        }

        void rebuild() {
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
            for (auto& e : s_ev) {
                s_maxHold = std::max(s_maxHold, e.end - e.start);
                float y1 = 0.f;
                bool a = posAt(m.samples, e.start, e.lane, e.x0, e.y0);
                bool b = posAt(m.samples, e.end, e.lane, e.x1, y1);
                e.pos = a && b;
            }
            s_key = keyOf(m);
            s_keyValid = true;
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
        m_world = CCDrawNode::create();
        m_world->retain();
        m_hint = makeLabel("", 300.f, 0.3f);
        m_hint->setAnchorPoint({0.f, 1.f});
        m_hint->setOpacity(170);
        this->addChild(m_hint, 3);
        this->scheduleUpdate();
        return true;
    }

    ClickIndicator::~ClickIndicator() {
        if (m_world) {
            m_world->removeFromParent();
            m_world->release();
            m_world = nullptr;
        }
    }

    void ClickIndicator::setHint(std::string const& t) {
        if (t == m_hintText) return;
        m_hintText = t;
        m_hint->setString(t.c_str());
        m_hint->setVisible(!t.empty());
    }

    void ClickIndicator::relocate(int cur) {
        auto it = std::lower_bound(s_ev.begin(), s_ev.end(), cur, [](Ev const& e, int v) { return e.start < v; });
        m_nextHit = static_cast<size_t>(std::distance(s_ev.begin(), it));
    }

    void ClickIndicator::firePerfect(int lane) {
        auto& s = State::get();
        m_flash[lane] = 1.f;
        if (!s.ciPerfectOn) return;
        auto lbl = makeLabel("Perfect", 140.f, static_cast<float>(s.ciTextSize));
        int lanes = s_hasP2 ? 2 : 1;
        float lx = static_cast<float>(s.ciX) + (lanes - 1) * (static_cast<float>(s.ciW) + 56.f) + static_cast<float>(s.ciW) + 30.f;
        float ly = static_cast<float>(s.ciY) + 8.f + lane * 14.f;
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
        if (m_world) m_world->clear();

        auto pl = PlayLayer::get();
        if (!s.clickInd || !pl) {
            setHint("");
            m_lastCur = -1;
            return;
        }

        float const baseX = static_cast<float>(s.ciX);
        float const baseY = static_cast<float>(s.ciY);
        m_hint->setPosition({baseX, baseY - 6.f});

        bool const hasMacro = bot::MacroManager::hasCurrent();
        bool const playing = hasMacro && s.bot == BotMode::Playback && s.botOn();

        if (!hasMacro) {
            // tetap gambar tangga kosong supaya jelas indikatornya aktif
            setHint("Click Indicator ON - no macro loaded (load a .nxr in Bot)");
            m_lastCur = -1;
            s_ev.clear();
            s_keyValid = false;
            drawLadder(0, 240.0, dt);
            return;
        }

        auto const& macro = bot::MacroManager::current();
        if (!s_keyValid || !(s_key == keyOf(macro))) {
            rebuild();
            m_lastCur = -1;
        }

        double tps = macro.tps > 0.0 ? macro.tps : 240.0;
        int cur = bot::currentFrame();

        if (!playing) setHint("Click Indicator preview - set Bot to Playback and Safe Mode OFF to run");
        else if (macro.samples.empty()) setHint("Macro has no position data - record again for Line / Follow Zone");
        else setHint("");

        // Deteksi lompatan (restart / checkpoint) -> cari ulang tanpa memicu Perfect.
        if (m_lastCur < 0 || cur < m_lastCur || cur - m_lastCur > static_cast<int>(tps * 0.25)) {
            relocate(cur);
        } else {
            while (m_nextHit < s_ev.size() && s_ev[m_nextHit].start < cur) {
                if (playing) firePerfect(s_ev[m_nextHit].lane);
                m_nextHit++;
            }
        }
        m_lastCur = cur;

        if (s.ciLadder) drawLadder(cur, tps, dt);
        else for (int l = 0; l < 2; l++) m_flash[l] = std::max(0.f, m_flash[l] - dt * 4.f);

        drawWorld(cur, tps, pl);
    }

    void ClickIndicator::drawLadder(int cur, double tps, float dt) {
        auto& s = State::get();
        float const op = static_cast<float>(s.ciOpacity);
        float const len = static_cast<float>(s.ciLen);
        float const w = static_cast<float>(s.ciW);
        float const x0 = static_cast<float>(s.ciX);
        float const y0 = static_cast<float>(s.ciY);
        float const barH = 7.f, rail = 3.f;
        float const usable = std::max(10.f, len - barH);
        double const ahead = std::max(1.0, s.ciAhead * tps);
        int const lanes = s_hasP2 ? 2 : 1;
        auto laneX = [&](int l) { return x0 + l * (w + 56.f); };

        bool held[2] = {false, false};

        // Rail + isi tangga
        for (int l = 0; l < lanes; l++) {
            float x = laneX(l);
            rect(m_draw, x, y0, x + w, y0 + len, {0.f, 0.f, 0.f, 0.45f * op});
            ccColor4F rc{0.82f, 0.82f, 0.82f, 0.55f * op};
            rect(m_draw, x, y0, x + rail, y0 + len, rc);
            rect(m_draw, x + w - rail, y0, x + w, y0 + len, rc);
            rect(m_draw, x, y0 + len - rail, x + w, y0 + len, rc);
        }

        // Anak tangga (klik)
        if (!s_ev.empty()) {
            auto lo = std::lower_bound(s_ev.begin(), s_ev.end(), cur - s_maxHold, [](Ev const& e, int v) { return e.start < v; });
            auto hi = std::upper_bound(s_ev.begin(), s_ev.end(), cur + static_cast<int>(ahead), [](int v, Ev const& e) { return v < e.start; });
            float const base = y0 + barH;
            for (auto it = lo; it != hi; ++it) {
                if (it->end <= cur) continue;
                float ys = base + static_cast<float>((it->start - cur) / ahead) * usable;
                float ye = base + static_cast<float>((it->end - cur) / ahead) * usable;
                if (it->start < cur) held[it->lane] = true;
                float bottom = std::max(ys, base);
                float top = std::min(std::max(ye, ys + 5.f), y0 + len - rail);
                if (top <= bottom) continue;
                float t = std::clamp((bottom - base) / usable, 0.f, 1.f);
                float x = laneX(it->lane);
                rect(m_draw, x + rail + 1.f, bottom, x + w - rail - 1.f, top, col(s.ciRung, op * (1.f - 0.5f * t)));
            }
        }

        // Bar hit + chevron
        for (int l = 0; l < lanes; l++) {
            m_flash[l] = std::max(0.f, m_flash[l] - dt * 4.f);
            float x = laneX(l);
            float glow = std::max(m_flash[l], held[l] ? 0.7f : 0.f);
            rect(m_draw, x + rail, y0, x + w - rail, y0 + barH, col(s.ciBar, op));
            if (glow > 0.f) rect(m_draw, x + rail, y0, x + w - rail, y0 + barH, {1.f, 1.f, 1.f, 0.55f * glow * op});

            float cy = y0 + barH / 2.f;
            float sz = 9.f, r = 2.2f, off = 20.f;
            ccColor4F ac = col(s.ciArrow, op);
            CCPoint la{x - off, cy + sz}, lb{x - off + sz, cy}, lc{x - off, cy - sz};
            m_draw->drawSegment(la, lb, r, ac);
            m_draw->drawSegment(lb, lc, r, ac);
            CCPoint ra{x + w + off, cy + sz}, rb{x + w + off - sz, cy}, rc2{x + w + off, cy - sz};
            m_draw->drawSegment(ra, rb, r, ac);
            m_draw->drawSegment(rb, rc2, r, ac);
        }
    }

    void ClickIndicator::drawWorld(int cur, double tps, PlayLayer* pl) {
        auto& s = State::get();
        auto& macro = bot::MacroManager::current();
        if (!m_world || !pl->m_objectLayer || !pl->m_player1 || macro.samples.empty()) return;
        if (m_world->getParent() != pl->m_objectLayer) {
            m_world->removeFromParent();
            pl->m_objectLayer->addChild(m_world, 10000);
        }

        float const op = static_cast<float>(s.ciOpacity);
        float const thick = static_cast<float>(s.ciThick);
        int const reachF = std::max(1, static_cast<int>(s.ciReach * tps));
        int const f1 = cur + reachF;
        auto const& sm = macro.samples;

        // Jalur (Line)
        if (s.ciMode == 0) {
            int lanes = s_hasP2 ? 2 : 1;
            auto it0 = std::lower_bound(sm.begin(), sm.end(), cur, [](bot::Sample const& a, int f) { return a.frame < f; });
            auto it1 = std::upper_bound(sm.begin(), sm.end(), f1, [](int f, bot::Sample const& a) { return f < a.frame; });
            int count = static_cast<int>(std::distance(it0, it1));
            if (count >= 2) {
                int step = std::max(1, count / 140);
                for (int l = 0; l < lanes; l++) {
                    bool have = false;
                    CCPoint prev{};
                    for (int i = 0; i < count; i += step) {
                        float x, y;
                        pickPos(*(it0 + i), l, x, y);
                        if (have) {
                            float a = op * (1.f - 0.7f * static_cast<float>(i) / static_cast<float>(count)) * (l == 1 ? 0.7f : 1.f);
                            m_world->drawSegment(prev, {x, y}, thick * 0.5f, col(s.ciPath, a));
                        }
                        prev = {x, y};
                        have = true;
                    }
                }
            }
        }

        // Kotak di titik klik + zone
        float yLo = 0.f, yHi = 0.f;
        if (s.ciMode == 1) {
            auto win = CCDirector::get()->getWinSize();
            auto a = pl->m_objectLayer->convertToNodeSpace({0.f, 0.f});
            auto b = pl->m_objectLayer->convertToNodeSpace({win.width, win.height});
            yLo = std::min(a.y, b.y) - 30.f;
            yHi = std::max(a.y, b.y) + 30.f;
        }

        if (s_ev.empty()) return;
        int const fade = std::max(2, static_cast<int>(0.15 * tps));
        auto lo = std::lower_bound(s_ev.begin(), s_ev.end(), cur - s_maxHold - fade, [](Ev const& e, int v) { return e.start < v; });
        auto hi = std::upper_bound(s_ev.begin(), s_ev.end(), f1, [](int v, Ev const& e) { return v < e.start; });
        float const minW = 14.f;
        float const edge = std::max(1.f, thick * 0.7f);

        for (auto it = lo; it != hi; ++it) {
            if (!it->pos) continue;
            bool hit = cur >= it->start;

            if (s.ciMode == 1) {
                PlayerObject* pp = (it->lane == 1 && pl->m_player2) ? pl->m_player2 : pl->m_player1;
                float px = pp->getPositionX();
                float xr = std::max(it->x1, it->x0 + minW);
                bool tap = (it->x1 - it->x0) < minW;
                float L, R, al = 1.f;
                if (!hit) {
                    L = it->x0; R = xr;
                } else if (tap) {
                    int el = cur - it->start;
                    if (el > fade) continue;
                    al = 1.f - static_cast<float>(el) / static_cast<float>(fade);
                    L = px; R = px + minW * al;
                } else {
                    if (cur >= it->end || px >= it->x1) continue; // hold selesai -> hilang
                    L = px; R = it->x1;
                }
                if (R - L <= 0.5f) continue;
                rect(m_world, L, yLo, R, yHi, col(s.ciZone, (hit ? 0.42f : 0.28f) * op * al));
                rect(m_world, L, yLo, L + edge, yHi, col(s.ciZone, 0.9f * op * al));
                rect(m_world, R - edge, yLo, R, yHi, col(s.ciZone, 0.9f * op * al));
            }

            if (!hit) {
                float h = thick * 1.4f;
                rect(m_world, it->x0 - h, it->y0 - h, it->x0 + h, it->y0 + h, col(s.ciPath, op));
            }
        }
    }
}
