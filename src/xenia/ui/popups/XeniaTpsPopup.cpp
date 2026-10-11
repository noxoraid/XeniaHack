#include "xenia/core/Lang.hpp"
#include "xenia/ui/popups/XeniaTpsPopup.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static constexpr float W = 300.f, H = 190.f;

XeniaTpsPopup* XeniaTpsPopup::s_instance = nullptr;

XeniaTpsPopup::~XeniaTpsPopup() {
    if (s_instance == this) s_instance = nullptr;
}

XeniaTpsPopup* XeniaTpsPopup::create() {
    auto ret = new XeniaTpsPopup();
    if (ret && ret->initPopup()) {
        ret->autorelease();
        s_instance = ret;
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

void XeniaTpsPopup::open() {
    if (s_instance) return;
    if (auto p = XeniaTpsPopup::create()) p->show();
}

bool XeniaTpsPopup::initPopup() {
    if (!Popup::init(W, H)) return false;
    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    auto& st = xenia::State::get();
    auto& th = xenia::currentTheme(st.theme);

    auto bg = ui::makeSlice("panel.png", W, H, 16.f, 0.125f);
    ui::setSliceColor(bg, th.bg);
    ui::setSliceOpacity(bg, (GLubyte)std::round(st.uiOpacity * 255.f));
    m_mainLayer->addChild(bg, -1);

    auto title = ui::makeLabel("TPS Bypass", 200.f, 0.55f);
    title->setPosition({W / 2, H - 24.f});
    m_mainLayer->addChild(title);

    auto hint = ui::makeLabel(xenia::tr("Enter physics TPS: 1 - 1000000000000 (default 240)", "Ketik TPS fisika: 1 - 1000000000000 (default 240)"), W - 30.f, 0.34f);
    hint->setPosition({W / 2, H - 50.f});
    m_mainLayer->addChild(hint);

    auto inBg = ui::makeSlice("box.png", 200.f, 36.f, 10.f);
    inBg->setPosition({W / 2 - 100.f, H - 86.f - 18.f});
    m_mainLayer->addChild(inBg);

    m_input = TextInput::create(200.f, "240", ui::font());
    m_input->setFilter("0123456789.");
    m_input->setMaxCharCount(14);
    m_input->setString(fmt::format("{}", st.tps), false);
    if (auto b = m_input->getBGSprite()) b->setVisible(false);
    m_input->setPosition({W / 2, H - 86.f});
    m_mainLayer->addChild(m_input);

    m_status = ui::makeLabel("", W - 30.f, 0.34f);
    m_status->setPosition({W / 2, H - 114.f});
    m_mainLayer->addChild(m_status);
    setStatus(xenia::trf("Active: {} TPS (enable TPS Bypass, Safe Mode OFF)", "Aktif: {} TPS (centang TPS Bypass + Safe Mode OFF)", st.tps), true);

    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    m_mainLayer->addChild(menu, 5);

    auto apply = CCMenuItemSpriteExtra::create(ui::makeButton("Apply", 100.f, true), this, menu_selector(XeniaTpsPopup::onApply));
    apply->setPosition({W / 2 + 56.f, 28.f});
    menu->addChild(apply);
    auto reset = CCMenuItemSpriteExtra::create(ui::makeButton("Reset", 100.f, false), this, menu_selector(XeniaTpsPopup::onReset));
    reset->setPosition({W / 2 - 56.f, 28.f});
    menu->addChild(reset);

    auto close = ui::makeSquareIconButton("close.png", 24.f, 11.f, this, menu_selector(XeniaTpsPopup::onCloseBtn));
    close->setPosition({W - 20.f, H - 20.f});
    menu->addChild(close);
    return true;
}

void XeniaTpsPopup::setStatus(std::string const& text, bool ok) {
    m_status->setString(text.c_str());
    m_status->setColor(ok ? ccColor3B{120, 230, 140} : ccColor3B{255, 110, 110});
}

void XeniaTpsPopup::onApply(CCObject*) {
    auto text = m_input->getString();
    char* end = nullptr;
    double v = std::strtod(text.c_str(), &end);
    if (text.empty() || end == text.c_str() || !std::isfinite(v)) return setStatus(xenia::tr("Invalid number", "Angka tidak valid"), false);
    if (v < xenia::State::kMinTps || v > xenia::State::kMaxTps) return setStatus(xenia::tr("Must be between 1 and 1000000000000", "Harus antara 1 - 1000000000000"), false);
    auto& st = xenia::State::get();
    st.tps = v;
    st.save();
    setStatus(xenia::trf("Saved: {} TPS", "Tersimpan: {} TPS", v), true);
}

void XeniaTpsPopup::onReset(CCObject*) {
    auto& st = xenia::State::get();
    st.tps = 240.0;
    st.save();
    m_input->setString("240", false);
    setStatus(xenia::tr("Reset to 240 TPS", "Direset ke 240 TPS"), true);
}

void XeniaTpsPopup::onCloseBtn(CCObject*) {
    this->onClose(nullptr);
}
