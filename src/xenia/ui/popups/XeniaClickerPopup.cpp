#include "xenia/core/Lang.hpp"
#include "xenia/ui/popups/XeniaClickerPopup.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static constexpr float W = 300.f, H = 220.f;

XeniaClickerPopup* XeniaClickerPopup::s_instance = nullptr;

XeniaClickerPopup::~XeniaClickerPopup() {
    if (s_instance == this) s_instance = nullptr;
}

XeniaClickerPopup* XeniaClickerPopup::create() {
    auto ret = new XeniaClickerPopup();
    if (ret->initPopup()) {
        ret->autorelease();
        s_instance = ret;
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

void XeniaClickerPopup::open() {
    if (s_instance) return;
    if (auto p = XeniaClickerPopup::create()) p->show();
}

bool XeniaClickerPopup::initPopup() {
    if (!Popup::init(W, H)) return false;
    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    auto& st = xenia::State::get();
    auto& th = xenia::currentTheme(st.theme);
    auto bg = ui::makeSlice("panel.png", W, H, 16.f, 0.125f);
    ui::setSliceColor(bg, th.bg);
    ui::setSliceOpacity(bg, (GLubyte)std::round(st.uiOpacity * 255.f));
    m_mainLayer->addChild(bg, -1);

    auto title = ui::makeLabel("Auto Clicker", 200.f, 0.55f);
    title->setPosition({W / 2, H - 24.f});
    m_mainLayer->addChild(title);

    buildContent();
    return true;
}

void XeniaClickerPopup::buildContent() {
    if (m_content) m_content->removeFromParent();
    m_content = CCNode::create();
    m_mainLayer->addChild(m_content, 2);

    auto& st = xenia::State::get();
    bool sup = st.acMode == 1;

    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    m_content->addChild(menu, 5);

    char const* names[] = {"Normal", "Super"};
    for (int i = 0; i < 2; i++) {
        auto b = CCMenuItemSpriteExtra::create(ui::makeButton(names[i], 110.f, st.acMode == i), this, menu_selector(XeniaClickerPopup::onMode));
        b->setTag(i);
        b->setPosition({W / 2 + (i == 0 ? -58.f : 58.f), H - 56.f});
        menu->addChild(b);
    }

    auto hl = ui::makeLabel(sup ? "Hold CPS (>0)" : "Hold tick (0-1000)", 124.f, 0.36f);
    hl->setPosition({W / 2 - 68.f, H - 86.f});
    m_content->addChild(hl);
    auto rl = ui::makeLabel(sup ? "Release CPS (>0)" : "Release tick (0-1000)", 124.f, 0.36f);
    rl->setPosition({W / 2 + 68.f, H - 86.f});
    m_content->addChild(rl);

    float cy = H - 112.f;
    for (int i = 0; i < 2; i++) {
        float cx = W / 2 + (i == 0 ? -68.f : 68.f);
        auto inBg = ui::makeSlice("box.png", 120.f, 30.f, 9.f);
        inBg->setPosition({cx - 60.f, cy - 15.f});
        m_content->addChild(inBg);
        auto in = TextInput::create(120.f, "0", ui::font());
        in->setFilter("0123456789.");
        in->setMaxCharCount(sup ? 14 : 4);
        std::string v = sup ? fmt::format("{}", i == 0 ? st.acHoldCps : st.acReleaseCps)
                            : fmt::format("{}", i == 0 ? st.acHold : st.acRelease);
        in->setString(v, false);
        if (auto b = in->getBGSprite()) b->setVisible(false);
        in->setScale(0.85f);
        in->setPosition({cx, cy});
        m_content->addChild(in);
        (i == 0 ? m_hold : m_release) = in;
    }

    m_status = ui::makeLabel(sup ? xenia::tr("Super: higher CPS is faster", "Super: makin besar CPS makin cepat") : xenia::tr("Normal: lower is faster (real clicks)", "Normal: makin kecil makin cepat (klik asli)"), W - 30.f, 0.34f);
    m_status->setColor({120, 230, 140});
    m_status->setPosition({W / 2, H - 140.f});
    m_content->addChild(m_status);

    auto apply = CCMenuItemSpriteExtra::create(ui::makeButton("Apply", 100.f, true), this, menu_selector(XeniaClickerPopup::onApply));
    apply->setPosition({W / 2 + 56.f, 26.f});
    menu->addChild(apply);
    auto reset = CCMenuItemSpriteExtra::create(ui::makeButton("Reset", 100.f, false), this, menu_selector(XeniaClickerPopup::onReset));
    reset->setPosition({W / 2 - 56.f, 26.f});
    menu->addChild(reset);
    auto close = ui::makeSquareIconButton("close.png", 24.f, 11.f, this, menu_selector(XeniaClickerPopup::onCloseBtn));
    close->setPosition({W - 20.f, H - 20.f});
    menu->addChild(close);
}

void XeniaClickerPopup::setStatus(std::string const& text, bool ok) {
    m_status->setString(text.c_str());
    m_status->setColor(ok ? ccColor3B{120, 230, 140} : ccColor3B{255, 110, 110});
}

void XeniaClickerPopup::onMode(CCObject* s) {
    auto& st = xenia::State::get();
    int m = std::clamp(static_cast<CCNode*>(s)->getTag(), 0, 1);
    if (m == st.acMode) return;
    st.acMode = m;
    st.save();
    Ref<XeniaClickerPopup> self = this;
    Loader::get()->queueInMainThread([self] { self->buildContent(); });
}

void XeniaClickerPopup::onApply(CCObject*) {
    auto& st = xenia::State::get();
    char* e1 = nullptr;
    char* e2 = nullptr;
    auto t1 = m_hold->getString();
    auto t2 = m_release->getString();
    double a = std::strtod(t1.c_str(), &e1);
    double b = std::strtod(t2.c_str(), &e2);
    if (t1.empty() || t2.empty() || e1 == t1.c_str() || e2 == t2.c_str() || !std::isfinite(a) || !std::isfinite(b)) {
        return setStatus(xenia::tr("Invalid number", "Angka tidak valid"), false);
    }
    if (st.acMode == 0) {
        if (a < 0 || b < 0 || a > xenia::State::kMaxTicks || b > xenia::State::kMaxTicks) {
            return setStatus(xenia::tr("Must be between 0 and 1000", "Harus antara 0 - 1000"), false);
        }
        st.acHold = (int)std::lround(a);
        st.acRelease = (int)std::lround(b);
    } else {
        if (a < xenia::State::kMinRate || b < xenia::State::kMinRate) {
            return setStatus(xenia::tr("CPS must be greater than 0", "CPS harus lebih dari 0"), false);
        }
        st.acHoldCps = a;
        st.acReleaseCps = b;
    }
    st.save();
    setStatus(xenia::tr("Saved", "Tersimpan"), true);
}

void XeniaClickerPopup::onReset(CCObject*) {
    auto& st = xenia::State::get();
    st.acHold = 2;
    st.acRelease = 2;
    st.acHoldCps = 30.0;
    st.acReleaseCps = 30.0;
    st.save();
    buildContent();
    setStatus(xenia::tr("Reset", "Direset"), true);
}

void XeniaClickerPopup::onCloseBtn(CCObject*) {
    this->onClose(nullptr);
}
