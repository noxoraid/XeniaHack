#pragma once
#include <Geode/Geode.hpp>
#include "xenia/ui/mobile/MobileProfile.hpp"
#include "xenia/ui/windows/WindowsProfile.hpp"

namespace xenia::ui::platform {
#ifdef GEODE_IS_DESKTOP
    namespace cur = xenia::ui::windows;
#else
    namespace cur = xenia::ui::mobile;
#endif
    constexpr float kDragSlop = cur::kDragSlop;
    constexpr float kWheelStep = cur::kWheelStep;
    constexpr int kDefaultUiMode = cur::kDefaultUiMode;
    constexpr bool kHasKeyboard = cur::kHasKeyboard;
}
