#include "xenia/core/Lang.hpp"
#include "xenia/ui/popups/XeniaUtilPopup.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static constexpr float W = 300.f, H = 206.f;

XeniaUtilPopup* XeniaUtilPopup::s_instance = nullptr;

static bool& clickFlag(int k) {
    auto& s = xenia::State::get();
    return k == 0 ? s.shipClick : (k == 1 ? s.waveClick : s.slideReal);
}

static double& clickRate(int k) {
    auto& s = xenia::State::get();
    return k == 0 ? s.shipCps : (k == 1 ? s.waveCps : s.slideCps);
}

static char const* titleOf(int k) {
    return k == 0 ? "Straight Ship" : (k == 1 ? "Straight Wave" : "Wave Slide");
}

XeniaUtilPopup::~XeniaUtilPopup() {
    if (s_instance == this) s_instance = nullptr;
}

XeniaUtilPopup* XeniaUtilPopup::create(int kind) {
    auto ret = new XeniaUtilPopup();
    ret->m_kind = kind;
    if (ret->initPopup()) {
        ret->autorelease();
        s_instance = ret;
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

void XeniaUtilPopup::open(int kind) {
    if (s_instance) return;
    if (auto p = XeniaUtilPopup::create(kind)) p->show();
}

bool XeniaUtilPopup::initPopup() {
    if (!Popup::init(W, H)) return false;
    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    auto& st = xenia::State::get();
    auto& th = xenia::currentTheme(st.theme);

    auto bg = ui::makeSlice("panel.png", W, H, 16.f, 0.125f);
    ui::setSliceColor(bg, th.bg);
    ui::setSliceOpacity(bg, (GLubyte)std::round(st.uiOpacity * 255.f));
    m_mainLayer->addChild(bg, -1);

    auto title = ui::makeLabel(titleOf(m_kind), 200.f, 0.55f);
    title->setPosition({W / 2, H - 24.f});
    m_mainLayer->addChild(title);

    auto hint = ui::makeLabel(xenia::tr("Click rate (CPS): 1 - 120", "Kecepatan klik (CPS): 1 - 120"), W - 40.f, 0.36f);
    hint->setPosition({W / 2, H - 50.f});
    m_mainLayer->addChild(hint);

    auto inBg = ui::makeSlice("box.png", 170.f, 36.f, 10.f);
    inBg->setPosition({W / 2 - 85.f, H - 86.f - 18.f});
    m_mainLayer->addChild(inBg);

    m_input = TextInput::create(170.f, "12", ui::font());
    m_input->setFilter("0123456789.");
    m_input->setMaxCharCount(6);
    m_input->setString(fmt::format("{}", clickRate(m_kind)), false);
    if (auto b = m_input->getBGSprite()) b->setVisible(false);
    m_input->setPosition({W / 2, H - 86.f});
    m_mainLayer->addChild(m_input);

    m_status = ui::makeLabel("", W - 30.f, 0.34f);
    m_status->setPosition({W / 2, H - 114.f});
    m_mainLayer->addChild(m_status);
    setStatus(xenia::trf("Active: {} CPS (needs Safe Mode OFF)", "Aktif: {} CPS (butuh Safe Mode OFF)", clickRate(m_kind)), true);

    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    m_mainLayer->addChild(menu, 5);

    auto chk = ui::makeCheckbox(this, menu_selector(XeniaUtilPopup::onClick), clickFlag(m_kind), 20.f);
    chk->setPosition({W / 2 - 70.f + 10.f, 68.f});
    menu->addChild(chk);
    auto chkLbl = ui::makeLabel(m_kind == 2 ? xenia::tr("Real clicks (recorded)", "Klik asli (terekam)") : xenia::tr("Click effect", "Efek klik"), 150.f, 0.4f);
    chkLbl->setAnchorPoint({0.f, 0.5f});
    chkLbl->setPosition({W / 2 - 70.f + 28.f, 68.f});
    m_mainLayer->addChild(chkLbl);

    auto apply = CCMenuItemSpriteExtra::create(ui::makeButton("Apply", 100.f, true), this, menu_selector(XeniaUtilPopup::onApply));
    apply->setPosition({W / 2 + 56.f, 26.f});
    menu->addChild(apply);
    auto reset = CCMenuItemSpriteExtra::create(ui::makeButton("Reset", 100.f, false), this, menu_selector(XeniaUtilPopup::onReset));
    reset->setPosition({W / 2 - 56.f, 26.f});
    menu->addChild(reset);

    auto close = ui::makeSquareIconButton("close.png", 24.f, 11.f, this, menu_selector(XeniaUtilPopup::onCloseBtn));
    close->setPosition({W - 20.f, H - 20.f});
    menu->addChild(close);
    return true;
}

void XeniaUtilPopup::setStatus(std::string const& text, bool ok) {
    m_status->setString(text.c_str());
    m_status->setColor(ok ? ccColor3B{120, 230, 140} : ccColor3B{255, 110, 110});
}

void XeniaUtilPopup::onApply(CCObject*) {
    auto text = m_input->getString();
    char* end = nullptr;
    double v = std::strtod(text.c_str(), &end);
    if (text.empty() || end == text.c_str() || !std::isfinite(v)) {
        return setStatus(xenia::tr("Invalid number", "Angka tidak valid"), false);
    }
    if (v < xenia::State::kMinCps || v > xenia::State::kMaxCps) {
        return setStatus(xenia::tr("Must be between 1 and 120", "Harus antara 1 - 120"), false);
    }
    auto& st = xenia::State::get();
    clickRate(m_kind) = v;
    st.save();
    setStatus(xenia::trf("Saved: {} CPS", "Tersimpan: {} CPS", v), true);
}

void XeniaUtilPopup::onReset(CCObject*) {
    auto& st = xenia::State::get();
    clickRate(m_kind) = 12.0;
    st.save();
    m_input->setString("12", false);
    setStatus(xenia::tr("Reset to 12 CPS", "Direset ke 12 CPS"), true);
}

void XeniaUtilPopup::onClick(CCObject*) {
    auto& st = xenia::State::get();
    bool& v = clickFlag(m_kind);
    v = !v;
    st.save();
}

void XeniaUtilPopup::onCloseBtn(CCObject*) {
    this->onClose(nullptr);
}
