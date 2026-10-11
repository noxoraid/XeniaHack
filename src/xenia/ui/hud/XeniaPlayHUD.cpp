#include "xenia/core/Lang.hpp"
#include <Geode/Geode.hpp>
#include <Geode/modify/UILayer.hpp>
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
#include "xenia/bot/Bot.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

namespace {
    constexpr float kGap = 6.f;
    constexpr float kClockRight = 74.f;
    constexpr float kClockTopEdge = 11.f;
    constexpr float kStepLeft = 40.f;
    constexpr float kStepTopEdge = 15.f;
}

class $modify(XeniaPlayHUD, UILayer) {
    struct Fields {
        CCMenu* menu = nullptr;
        CCMenuItemSpriteExtra* clock = nullptr;
        CCMenuItemSpriteExtra* pauseBtn = nullptr;
        CCMenuItemSpriteExtra* playBtn = nullptr;
        CCMenuItemSpriteExtra* stepBtn = nullptr;
        CCLabelBMFont* stats = nullptr;
        float clockApplied = -1.f;
        float stepApplied = -1.f;
    };

    bool init(GJBaseGameLayer* layer) {
        if (!UILayer::init(layer)) return false;
        auto& s = xenia::State::get();
        s.paused = false;
        s.stepReq = false;

        m_fields->menu = CCMenu::create();
        m_fields->menu->setPosition({0, 0});
        this->addChild(m_fields->menu, 100);
        buildButtons();

        m_fields->stats = ui::makeLabel("", 320.f, 0.38f);
        m_fields->stats->setAnchorPoint({0.f, 1.f});
        m_fields->stats->setPosition({kStepLeft, CCDirector::get()->getWinSize().height - 60.f});
        m_fields->stats->setOpacity(170);
        this->addChild(m_fields->stats, 100);

        this->schedule(schedule_selector(XeniaPlayHUD::tick), 0.05f);
        tick(0.f);
        return true;
    }

    void buildButtons() {
        auto& s = xenia::State::get();
        m_fields->menu->removeAllChildren();
        auto add = [&](char const* icon, float size, SEL_MenuHandler cb) {
            auto b = ui::makeSquareIconButton(icon, size, size * 0.6f, this, cb);
            m_fields->menu->addChild(b);
            return b;
        };
        m_fields->clock = add("clock.png", s.clockSize, menu_selector(XeniaPlayHUD::onClock));
        m_fields->pauseBtn = add("pause.png", s.stepSize, menu_selector(XeniaPlayHUD::onTogglePause));
        m_fields->playBtn = add("play.png", s.stepSize, menu_selector(XeniaPlayHUD::onTogglePause));
        m_fields->stepBtn = add("step.png", s.stepSize, menu_selector(XeniaPlayHUD::onStep));
        m_fields->clockApplied = s.clockSize;
        m_fields->stepApplied = s.stepSize;
    }

    void placeToolbar() {
        auto& s = xenia::State::get();
        auto win = CCDirector::get()->getWinSize();
        float yc = win.height - kClockTopEdge - s.clockSize / 2;
        m_fields->clock->setPosition({win.width - kClockRight, yc});
        float ys = win.height - kStepTopEdge - s.stepSize / 2;
        float pitch = s.stepSize + kGap;
        float x0 = kStepLeft + s.stepSize / 2;
        m_fields->pauseBtn->setPosition({x0, ys});
        m_fields->playBtn->setPosition({x0, ys});
        m_fields->stepBtn->setPosition({x0 + pitch, ys});
        float sy = ys - s.stepSize / 2 - 6.f;
        m_fields->stats->setPosition({kStepLeft, sy});
    }

    void tick(float) {
        auto& s = xenia::State::get();
        if (m_fields->clockApplied != s.clockSize || m_fields->stepApplied != s.stepSize) buildButtons();
        if (!s.recording()) {
            s.paused = false;
            s.stepReq = false;
        }
        bool showClock = s.clockVisible();
        bool showStepper = s.stepperVisible();

        m_fields->clock->setVisible(showClock);
        m_fields->pauseBtn->setVisible(showStepper && !s.paused);
        m_fields->playBtn->setVisible(showStepper && s.paused);
        m_fields->stepBtn->setVisible(showStepper && s.paused);

        if (showClock) {
            auto& th = xenia::currentTheme(s.theme);

            if (auto img = typeinfo_cast<CCSprite*>(m_fields->clock->getNormalImage())) {
                img->setCascadeColorEnabled(true);
                img->setColor(s.clockActive() ? th.active : ccColor3B{255, 255, 255});
            }
        }

        auto* lbl = m_fields->stats;
        lbl->setVisible(s.showStats);
        if (s.showStats) {
            auto const& st = xenia::bot::stats();
            auto pl = PlayLayer::get();
            auto p1 = pl ? pl->m_player1 : nullptr;
            std::vector<std::string> a, b;
            int m = s.statsMask;
            if (m & xenia::StatFps) a.push_back(fmt::format("FPS {}", st.fps));
            if (m & xenia::StatTps) a.push_back(fmt::format("TPS {}", st.tps));
            if (m & xenia::StatFrame) a.push_back(fmt::format("Frame {}", st.frame));
            if (m & xenia::StatCps) a.push_back(fmt::format("CPS {}", st.cps));
            if (m & xenia::StatClicks) a.push_back(xenia::trf("Clicks {}", "Klik {}", st.clicks));
            if (m & xenia::StatInput) b.push_back(fmt::format("P1 {}  P2 {}", st.p1 ? "ON" : "-", st.p2 ? "ON" : "-"));
            if (p1 && (m & xenia::StatPos)) b.push_back(fmt::format("X {:.1f}  Y {:.1f}", p1->getPositionX(), p1->getPositionY()));
            if (p1 && (m & xenia::StatYVel)) b.push_back(fmt::format("YVel {:.2f}", p1->m_yVelocity));
            if (p1 && (m & xenia::StatVehicle)) {
                char const* v = p1->m_isShip ? "Ship" : p1->m_isDart ? "Wave" : p1->m_isBall ? "Ball" : p1->m_isBird ? "UFO" : p1->m_isRobot ? "Robot" : p1->m_isSpider ? "Spider" : p1->m_isSwing ? "Swing" : "Cube";
                b.push_back(v);
            }
            auto join = [](std::vector<std::string> const& v) {
                std::string o;
                for (size_t i = 0; i < v.size(); i++) o += (i ? "  " : "") + v[i];
                return o;
            };
            std::string line = join(a);
            auto l2 = join(b);
            if (!l2.empty()) line += (line.empty() ? "" : "\n") + l2;
            if (m & xenia::StatBot) {
                auto bot = xenia::bot::statusLine();
                if (!bot.empty()) line += (line.empty() ? "" : "\n") + bot;
            }
            lbl->setString(line.c_str());
        }
        placeToolbar();

        bool blocked = ui::topPopup() != nullptr;
        m_fields->clock->setEnabled(!blocked);
        m_fields->pauseBtn->setEnabled(!blocked);
        m_fields->playBtn->setEnabled(!blocked);
        m_fields->stepBtn->setEnabled(!blocked);
    }

    void onClock(CCObject*) {
        auto& s = xenia::State::get();

        if (s.recording()) s.botClockOn = !s.botClockOn;
        else if (!s.botOn()) s.clockOn = !s.clockOn;
    }

    void onTogglePause(CCObject*) {
        auto& s = xenia::State::get();
        s.paused = !s.paused;
        s.stepReq = false;
    }

    void onStep(CCObject*) {
        auto& s = xenia::State::get();
        if (s.paused) s.stepReq = true;
    }
};
