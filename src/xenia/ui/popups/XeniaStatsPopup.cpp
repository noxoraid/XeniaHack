#include "xenia/ui/popups/XeniaStatsPopup.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Lang.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static constexpr float W = 320.f, H = 232.f;

static char const* statName(int i) {
    switch (i) {
        case 0: return "FPS";
        case 1: return "TPS";
        case 2: return "Frame";
        case 3: return "Input P1/P2";
        case 4: return "CPS";
        case 5: return xenia::tr("Total clicks", "Total klik");
        case 6: return xenia::tr("Position X/Y", "Posisi X/Y");
        case 7: return xenia::tr("Y velocity", "Kecepatan Y");
        case 8: return xenia::tr("Vehicle", "Kendaraan");
        default: return xenia::tr("Bot status", "Status bot");
    }
}

XeniaStatsPopup* XeniaStatsPopup::s_instance = nullptr;

XeniaStatsPopup::~XeniaStatsPopup() {
    if (s_instance == this) s_instance = nullptr;
}

XeniaStatsPopup* XeniaStatsPopup::create() {
    auto ret = new XeniaStatsPopup();
    if (ret->initPopup()) {
        ret->autorelease();
        s_instance = ret;
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

void XeniaStatsPopup::open() {
    if (s_instance) return;
    if (auto p = XeniaStatsPopup::create()) p->show();
}

bool XeniaStatsPopup::initPopup() {
    if (!Popup::init(W, H)) return false;
    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    auto& st = xenia::State::get();
    auto& th = xenia::currentTheme(st.theme);
    auto bg = ui::makeSlice("panel.png", W, H, 16.f, 0.125f);
    ui::setSliceColor(bg, th.bg);
    ui::setSliceOpacity(bg, (GLubyte)std::round(st.uiOpacity * 255.f));
    m_mainLayer->addChild(bg, -1);

    auto title = ui::makeLabel("Stats HUD", 200.f, 0.55f);
    title->setPosition({W / 2, H - 24.f});
    m_mainLayer->addChild(title);

    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    m_mainLayer->addChild(menu, 5);

    for (int i = 0; i < xenia::kStatCount; i++) {
        int col = i % 2, row = i / 2;
        float x = 28.f + col * (W / 2 - 8.f);
        float y = H - 62.f - row * 32.f;
        auto chk = ui::makeCheckbox(this, menu_selector(XeniaStatsPopup::onToggle), (st.statsMask >> i) & 1, 22.f);
        chk->setTag(i);
        chk->setPosition({x, y});
        menu->addChild(chk);
        auto lbl = ui::makeLabel(statName(i), W / 2 - 52.f, 0.4f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({x + 18.f, y});
        m_mainLayer->addChild(lbl);
    }

    auto close = ui::makeSquareIconButton("close.png", 24.f, 11.f, this, menu_selector(XeniaStatsPopup::onCloseBtn));
    close->setPosition({W - 20.f, H - 20.f});
    menu->addChild(close);
    return true;
}

void XeniaStatsPopup::onToggle(CCObject* s) {
    int i = std::clamp(static_cast<CCNode*>(s)->getTag(), 0, xenia::kStatCount - 1);
    auto& st = xenia::State::get();
    st.statsMask ^= (1 << i);
    st.save();
}

void XeniaStatsPopup::onCloseBtn(CCObject*) {
    this->onClose(nullptr);
}
