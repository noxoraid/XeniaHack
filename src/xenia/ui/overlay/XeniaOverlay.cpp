#include "xenia/ui/overlay/XeniaOverlay.hpp"
#include <Geode/ui/OverlayManager.hpp>
#include "xenia/ui/menu/XeniaMenu.hpp"
#include "xenia/ui/table/XeniaTable.hpp"
#include "xenia/ui/windows/XeniaWindowsKeys.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
using namespace geode::prelude;

XeniaOverlay* XeniaOverlay::s_instance = nullptr;

XeniaOverlay::~XeniaOverlay() {
    if (s_instance == this) s_instance = nullptr;
}

XeniaOverlay* XeniaOverlay::create() {
    auto ret = new XeniaOverlay();
    if (ret && ret->init()) {
        ret->autorelease();
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

void XeniaOverlay::ensure() {
    if (s_instance) return;
    if (auto o = XeniaOverlay::create()) {
        s_instance = o;
        OverlayManager::get()->addChild(o);
#ifdef GEODE_IS_DESKTOP
        XeniaWindowsKeys::ensure();
#endif
    }
}

void XeniaOverlay::applySize() {
    float side = std::max(m_logo->getContentWidth(), m_logo->getContentHeight());
    float size = std::clamp(xenia::State::get().logoSize, 14.f, 60.f);
    if (side > 0) m_logo->setScale(size / side);
}

void XeniaOverlay::refresh() {
    if (s_instance && s_instance->m_logo) s_instance->applySize();
}

bool XeniaOverlay::init() {
    if (!CCLayer::init()) return false;
    this->setTouchEnabled(true);

    m_logo = xenia::ui::loadSprite("logo.png");

    applySize();
    m_logo->setOpacity(210);

    auto win = CCDirector::get()->getWinSize();
    auto& st = xenia::State::get();
    float x = st.overlayX >= 0 ? (float)st.overlayX : 34.f;
    float y = st.overlayY >= 0 ? (float)st.overlayY : win.height - 70.f;
    m_logo->setPosition({std::clamp(x, 0.f, win.width), std::clamp(y, 0.f, win.height)});
    this->addChild(m_logo);
    return true;
}

void XeniaOverlay::registerWithTouchDispatcher() {

    CCTouchDispatcher::get()->addTargetedDelegate(this, -1000, true);
}

bool XeniaOverlay::ccTouchBegan(CCTouch* t, CCEvent*) {

    if (auto top = xenia::ui::topPopup(); top && !typeinfo_cast<XeniaMenu*>(top) && !typeinfo_cast<XeniaTable*>(top)) return false;
    auto p = t->getLocation();
    auto box = m_logo->boundingBox();
    box.origin.x -= 10.f; box.origin.y -= 10.f;
    box.size.width += 20.f; box.size.height += 20.f;
    if (!box.containsPoint(p)) return false;
    m_offset = m_logo->getPosition() - p;
    m_start = p;
    m_moved = false;
    m_logo->setOpacity(255);
    return true;
}

void XeniaOverlay::ccTouchMoved(CCTouch* t, CCEvent*) {
    auto p = t->getLocation();
    if (!m_moved && ccpDistance(p, m_start) > 8.f) m_moved = true;
    if (!m_moved) return;
    auto win = CCDirector::get()->getWinSize();
    auto np = p + m_offset;
    m_logo->setPosition({std::clamp(np.x, 0.f, win.width), std::clamp(np.y, 0.f, win.height)});
}

void XeniaOverlay::ccTouchEnded(CCTouch*, CCEvent*) {
    m_logo->setOpacity(210);
    if (m_moved) {
        auto& st = xenia::State::get();
        st.overlayX = m_logo->getPositionX();
        st.overlayY = m_logo->getPositionY();
        st.save();
    } else {
        XeniaMenu::toggle();
    }
}

void XeniaOverlay::ccTouchCancelled(CCTouch*, CCEvent*) {
    m_logo->setOpacity(210);
}

