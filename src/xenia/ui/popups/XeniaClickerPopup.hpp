#pragma once
#include <Geode/Geode.hpp>

class XeniaClickerPopup : public geode::Popup {
protected:
    static XeniaClickerPopup* s_instance;
    cocos2d::CCNode* m_content = nullptr;
    geode::TextInput* m_hold = nullptr;
    geode::TextInput* m_release = nullptr;
    cocos2d::CCLabelBMFont* m_status = nullptr;

    ~XeniaClickerPopup() override;
    bool initPopup();
    void buildContent();
    void setStatus(std::string const& text, bool ok);
    void onMode(cocos2d::CCObject*);
    void onApply(cocos2d::CCObject*);
    void onReset(cocos2d::CCObject*);
    void onCloseBtn(cocos2d::CCObject*);

public:
    static XeniaClickerPopup* create();
    static void open();
};
