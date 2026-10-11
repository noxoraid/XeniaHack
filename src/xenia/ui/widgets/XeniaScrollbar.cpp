#include "xenia/ui/widgets/XeniaScrollbar.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;

XeniaScrollbar* XeniaScrollbar::create(ScrollLayer* scroll, float w, float h) {
    auto ret = new XeniaScrollbar();
    if (ret && ret->init(scroll, w, h)) {
        ret->autorelease();
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

bool XeniaScrollbar::init(ScrollLayer* scroll, float w, float h) {
    if (!CCLayer::init()) return false;
    m_scroll = scroll;
    m_w = w;
    m_h = h;
    this->setContentSize({w, h});
    this->setTouchEnabled(true);

    m_track = xenia::ui::makeVSlice("scroll_track.png", w, h);
    this->addChild(m_track);
    m_track->setVisible(true);

    this->scheduleUpdate();
    this->update(0.f);
    return true;
}

float XeniaScrollbar::thumbHeight() const {
    float vh = m_scroll->getContentHeight();
    float ch = m_scroll->m_contentLayer->getContentHeight();
    if (ch <= 0) return m_h;
    return std::clamp(m_h * vh / ch, 36.f, m_h);
}

void XeniaScrollbar::update(float) {
    float vh = m_scroll->getContentHeight();
    float ch = m_scroll->m_contentLayer->getContentHeight();
    float minY = std::min(0.f, vh - ch);
    bool overflow = minY < -1.f;
    if (m_thumb) m_thumb->setVisible(overflow);
    xenia::ui::setSliceOpacity(m_track, overflow ? 255 : 110);
    if (!overflow) return;

    float f = (m_scroll->m_contentLayer->getPositionY() - minY) / (0.f - minY);
    f = std::clamp(f, 0.f, 1.f);
    float th = thumbHeight();
    if (!m_thumb || std::abs(th - m_thumbH) > 0.5f) {
        if (m_thumb) m_thumb->removeFromParent();
        m_thumb = xenia::ui::makeVSlice("scroll_thumb.png", m_w, th);
        xenia::ui::setSliceColor(m_thumb, xenia::currentTheme(xenia::State::get().theme).active);
        auto grip = xenia::ui::makeIcon("scroll_grip.png", m_w * 0.6f);
        grip->setPosition({m_w / 2, th / 2});
        m_thumb->addChild(grip, 1);
        this->addChild(m_thumb, 1);
        m_thumbH = th;
    }
    m_thumb->setPosition({0.f, m_h - th - f * (m_h - th)});
}

void XeniaScrollbar::applyTouchY(float y) {
    float vh = m_scroll->getContentHeight();
    float ch = m_scroll->m_contentLayer->getContentHeight();
    float minY = std::min(0.f, vh - ch);
    if (minY >= 0.f) return;
    float th = thumbHeight();
    float span = std::max(1.f, m_h - th);
    float f = std::clamp((m_h - th / 2 - y) / span, 0.f, 1.f);
    m_scroll->m_contentLayer->setPositionY(minY + f * (0.f - minY));
}

void XeniaScrollbar::registerWithTouchDispatcher() {
    CCTouchDispatcher::get()->addTargetedDelegate(this, -510, true);
}

bool XeniaScrollbar::ccTouchBegan(CCTouch* t, CCEvent*) {
    if (!m_thumb || !m_thumb->isVisible() || !this->isVisible()) return false;
    if (xenia::ui::coveredByPopup(this)) return false;
    auto p = this->convertToNodeSpace(t->getLocation());

    if (p.x < -14.f || p.x > m_w + 14.f || p.y < -6.f || p.y > m_h + 6.f) return false;
    m_drag = true;
    this->applyTouchY(p.y);
    return true;
}

void XeniaScrollbar::ccTouchMoved(CCTouch* t, CCEvent*) {
    if (!m_drag) return;
    this->applyTouchY(this->convertToNodeSpace(t->getLocation()).y);
}

void XeniaScrollbar::ccTouchEnded(CCTouch*, CCEvent*) { m_drag = false; }
void XeniaScrollbar::ccTouchCancelled(CCTouch*, CCEvent*) { m_drag = false; }
