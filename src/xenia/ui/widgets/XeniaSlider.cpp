#include "xenia/ui/widgets/XeniaSlider.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static constexpr float TH = 16.f;

XeniaSlider* XeniaSlider::create(float w, float mn, float mx, float v, int dec, CCNode* clip) {
    auto ret = new XeniaSlider();
    if (ret && ret->init(w, mn, mx, v, dec, clip)) {
        ret->autorelease();
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

bool XeniaSlider::init(float w, float mn, float mx, float v, int dec, CCNode* clip) {
    if (!CCLayer::init()) return false;
    m_w = w; m_min = mn; m_max = mx; m_dec = dec; m_clip = clip;
    m_val = std::clamp(v, mn, mx);
    this->setContentSize({w, 20.f});
    this->setTouchEnabled(true);
    m_track = ui::makeSlice("toggle_off.png", w, 10.f, 5.f, 0.5f);
    m_track->setPosition({0.f, 5.f});
    this->addChild(m_track);
    this->layout();
    return true;
}

void XeniaSlider::layout() {
    auto& th = xenia::currentTheme(xenia::State::get().theme);
    float f = (m_max > m_min) ? (m_val - m_min) / (m_max - m_min) : 0.f;
    float tx = f * (m_w - TH);
    if (m_fill) m_fill->removeFromParent();
    if (m_thumb) m_thumb->removeFromParent();
    m_fill = ui::makeSlice("toggle_on.png", std::max(8.f, tx + TH / 2), 6.f, 3.f, 0.5f);
    ui::setSliceColor(m_fill, th.active);
    m_fill->setPosition({0.f, 7.f});
    this->addChild(m_fill, 1);
    m_thumb = ui::makeSlice("toggle_on.png", TH, TH, TH / 2, 0.5f);
    m_thumb->setPosition({tx, 2.f});
    this->addChild(m_thumb, 2);
}

void XeniaSlider::setValue(float v) {
    float p = std::pow(10.f, (float)m_dec);
    m_val = std::clamp(std::round(v * p) / p, m_min, m_max);
    this->layout();
}

float XeniaSlider::valueAt(float x) const {
    float f = std::clamp((x - TH / 2) / std::max(1.f, m_w - TH), 0.f, 1.f);
    return m_min + f * (m_max - m_min);
}

void XeniaSlider::registerWithTouchDispatcher() {
    CCTouchDispatcher::get()->addTargetedDelegate(this, -511, true);
}

bool XeniaSlider::ccTouchBegan(CCTouch* t, CCEvent*) {
    if (!this->isVisible()) return false;
    if (ui::coveredByPopup(this)) return false;
    if (m_clip) {
        auto c = m_clip->convertToNodeSpace(t->getLocation());
        if (c.x < 0 || c.y < 0 || c.x > m_clip->getContentWidth() || c.y > m_clip->getContentHeight()) return false;
    }
    auto p = this->convertToNodeSpace(t->getLocation());
    if (p.x < -8.f || p.x > m_w + 8.f || p.y < -8.f || p.y > 28.f) return false;
    m_drag = true;
    this->setValue(valueAt(p.x));
    if (m_cb) m_cb(m_val, false);
    return true;
}

void XeniaSlider::ccTouchMoved(CCTouch* t, CCEvent*) {
    if (!m_drag) return;
    this->setValue(valueAt(this->convertToNodeSpace(t->getLocation()).x));
    if (m_cb) m_cb(m_val, false);
}

void XeniaSlider::ccTouchEnded(CCTouch*, CCEvent*) {
    if (!m_drag) return;
    m_drag = false;
    if (m_cb) m_cb(m_val, true);
}

void XeniaSlider::ccTouchCancelled(CCTouch*, CCEvent*) {
    if (!m_drag) return;
    m_drag = false;
    if (m_cb) m_cb(m_val, true);
}
