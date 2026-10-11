#include "xenia/ui/windows/XeniaWindowsKeys.hpp"

#ifdef GEODE_IS_DESKTOP
#include <Geode/ui/OverlayManager.hpp>
#include "xenia/ui/menu/XeniaMenu.hpp"
#include "xenia/ui/table/XeniaTable.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
using namespace geode::prelude;

XeniaWindowsKeys* XeniaWindowsKeys::s_instance = nullptr;

XeniaWindowsKeys::~XeniaWindowsKeys() {
    if (s_instance == this) s_instance = nullptr;
}

bool XeniaWindowsKeys::init() {
    if (!CCLayer::init()) return false;
    this->setKeyboardEnabled(true);
    return true;
}

void XeniaWindowsKeys::ensure() {
    if (s_instance) return;
    auto l = new XeniaWindowsKeys();
    if (l && l->init()) {
        l->autorelease();
        s_instance = l;
        OverlayManager::get()->addChild(l);
    } else {
        CC_SAFE_DELETE(l);
    }
}

bool XeniaWindowsKeys::shiftHeld() {
    return CCKeyboardDispatcher::get()->getShiftKeyPressed();
}

void XeniaWindowsKeys::onKey(enumKeyCodes key) {
    if (key != KEY_Tab) return;
    if (auto top = xenia::ui::topPopup(); top && !typeinfo_cast<XeniaMenu*>(top) && !typeinfo_cast<XeniaTable*>(top)) return;
    XeniaMenu::toggle();
}

void XeniaWindowsKeys::keyDown(enumKeyCodes key) { onKey(key); }
void XeniaWindowsKeys::keyDown(enumKeyCodes key, double) { onKey(key); }
#endif
