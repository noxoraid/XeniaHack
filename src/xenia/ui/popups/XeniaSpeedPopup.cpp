#include "xenia/core/Lang.hpp"
#include "xenia/ui/popups/XeniaSpeedPopup.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static constexpr float W = 300.f, H = 206.f;

XeniaSpeedPopup* XeniaSpeedPopup::s_instance = nullptr;

XeniaSpeedPopup::~XeniaSpeedPopup() {
    if (s_instance == this) s_instance = nullptr;
}

XeniaSpeedPopup* XeniaSpeedPopup::create(bool bot, std::function<void()> onApplied) {
    auto ret = new XeniaSpeedPopup();
    ret->m_bot = bot;
    ret->m_onApplied = std::move(onApplied);
    if (ret->initPopup()) {
        ret->autorelease();
        s_instance = ret;
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

void XeniaSpeedPopup::open(bool bot, std::function<void()> onApplied) {
    if (s_instance) return;
    if (auto p = XeniaSpeedPopup::create(bot, std::move(onApplied))) p->show();
}

bool XeniaSpeedPopup::initPopup() {
    if (!Popup::init(W, H)) return false;
    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    auto& st = xenia::State::get();
    auto& th = xenia::currentTheme(st.theme);

    auto bg = ui::makeSlice("panel.png", W, H, 16.f, 0.125f);
    ui::setSliceColor(bg, th.bg);
    ui::setSliceOpacity(bg, (GLubyte)std::round(st.uiOpacity * 255.f));
    m_mainLayer->addChild(bg, -1);

    auto title = ui::makeLabel(m_bot ? "Bot Speed" : "Speedhack", 200.f, 0.55f);
    title->setPosition({W / 2, H - 24.f});
    m_mainLayer->addChild(title);

    auto hint = ui::makeLabel(xenia::tr("Enter a speed: 0.1 - 1000", "Ketik kecepatan: 0.1 - 1000"), W - 40.f, 0.36f);
    hint->setPosition({W / 2, H - 50.f});
    m_mainLayer->addChild(hint);

    auto inBg = ui::makeSlice("box.png", 170.f, 36.f, 10.f);
    inBg->setPosition({W / 2 - 85.f, H - 86.f - 18.f});
    m_mainLayer->addChild(inBg);

    m_input = TextInput::create(170.f, "1.0", ui::font());
    m_input->setFilter("0123456789.");
    m_input->setMaxCharCount(7);
    m_input->setString(fmt::format("{}", m_bot ? st.botSpeed : st.speed), false);
    if (auto b = m_input->getBGSprite()) b->setVisible(false);
    m_input->setPosition({W / 2, H - 86.f});
    m_mainLayer->addChild(m_input);

    m_status = ui::makeLabel("", W - 30.f, 0.34f);
    m_status->setPosition({W / 2, H - 114.f});
    m_mainLayer->addChild(m_status);
    if (m_bot) setStatus(xenia::trf("Active: {}x (clock shows while recording, Safe Mode OFF)", "Aktif: {}x (jam muncul saat Record, Safe Mode OFF)", st.botSpeed), true);
    else setStatus(xenia::trf("Active: {}x (enable Speedhack, Safe Mode OFF)", "Aktif: {}x (centang Speedhack + Safe Mode OFF)", st.speed), true);

    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    m_mainLayer->addChild(menu, 5);

    auto audio = ui::makeCheckbox(this, menu_selector(XeniaSpeedPopup::onAudio), st.speedAudio, 20.f);
    audio->setPosition({W / 2 - 70.f + 10.f, 68.f});
    menu->addChild(audio);
    auto audioLbl = ui::makeLabel(xenia::tr("Audio follows speed", "Audio ikut speed"), 130.f, 0.4f);
    audioLbl->setAnchorPoint({0.f, 0.5f});
    audioLbl->setPosition({W / 2 - 70.f + 28.f, 68.f});
    m_mainLayer->addChild(audioLbl);

    auto apply = CCMenuItemSpriteExtra::create(ui::makeButton("Apply", 100.f, true), this, menu_selector(XeniaSpeedPopup::onApply));
    apply->setPosition({W / 2 + 56.f, 26.f});
    menu->addChild(apply);
    auto reset = CCMenuItemSpriteExtra::create(ui::makeButton("Reset", 100.f, false), this, menu_selector(XeniaSpeedPopup::onReset));
    reset->setPosition({W / 2 - 56.f, 26.f});
    menu->addChild(reset);

    auto close = ui::makeSquareIconButton("close.png", 24.f, 11.f, this, menu_selector(XeniaSpeedPopup::onCloseBtn));
    close->setPosition({W - 20.f, H - 20.f});
    menu->addChild(close);
    return true;
}

void XeniaSpeedPopup::setStatus(std::string const& text, bool ok) {
    m_status->setString(text.c_str());
    m_status->setColor(ok ? ccColor3B{120, 230, 140} : ccColor3B{255, 110, 110});
}

void XeniaSpeedPopup::onApply(CCObject*) {
    auto text = m_input->getString();
    char* end = nullptr;
    double v = std::strtod(text.c_str(), &end);
    if (text.empty() || end == text.c_str() || !std::isfinite(v)) {
        return setStatus(xenia::tr("Invalid number", "Angka tidak valid"), false);
    }
    if (v < xenia::State::kMinSpeed || v > xenia::State::kMaxSpeed) {
        return setStatus(xenia::tr("Must be between 0.1 and 1000", "Harus antara 0.1 - 1000"), false);
    }
    auto& st = xenia::State::get();
    (m_bot ? st.botSpeed : st.speed) = v;
    st.save();
    setStatus(xenia::trf("Saved: {}x", "Tersimpan: {}x", v), true);
    if (m_onApplied) m_onApplied();
}

void XeniaSpeedPopup::onReset(CCObject*) {
    auto& st = xenia::State::get();
    (m_bot ? st.botSpeed : st.speed) = 1.0;
    st.save();
    m_input->setString("1", false);
    setStatus(xenia::tr("Reset to 1x", "Direset ke 1x"), true);
    if (m_onApplied) m_onApplied();
}

void XeniaSpeedPopup::onAudio(CCObject*) {
    auto& st = xenia::State::get();
    st.speedAudio = !st.speedAudio;
    st.save();
}

void XeniaSpeedPopup::onCloseBtn(CCObject*) {
    this->onClose(nullptr);
}
