#pragma once
#include <Geode/Geode.hpp>

#ifdef GEODE_IS_DESKTOP
class XeniaWindowsKeys : public cocos2d::CCLayer {
protected:
    static XeniaWindowsKeys* s_instance;
    ~XeniaWindowsKeys() override;
    bool init() override;
    void onKey(cocos2d::enumKeyCodes key);

public:
    static void ensure();
    static bool shiftHeld();

    void keyDown(cocos2d::enumKeyCodes key);
    void keyDown(cocos2d::enumKeyCodes key, double timestamp);
};
#endif
