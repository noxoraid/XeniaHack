#include <Geode/Geode.hpp>
#include <Geode/modify/MenuLayer.hpp>
#include "xenia/ui/overlay/XeniaOverlay.hpp"
using namespace geode::prelude;

class $modify(XeniaMenuLayer, MenuLayer) {
    bool init() {
        if (!MenuLayer::init()) return false;
        XeniaOverlay::ensure();
        return true;
    }
};
