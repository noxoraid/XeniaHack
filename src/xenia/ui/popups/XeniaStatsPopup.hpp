#pragma once
#include <Geode/Geode.hpp>

class XeniaStatsPopup : public geode::Popup {
protected:
    static XeniaStatsPopup* s_instance;

    ~XeniaStatsPopup() override;
    bool initPopup();
    void onToggle(cocos2d::CCObject*);
    void onCloseBtn(cocos2d::CCObject*);

public:
    static XeniaStatsPopup* create();
    static void open();
};
