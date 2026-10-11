#pragma once
#include <Geode/Geode.hpp>

class XeniaSpeedPopup : public geode::Popup {
protected:
    static XeniaSpeedPopup* s_instance;
    bool m_bot = false;
    std::function<void()> m_onApplied;
    geode::TextInput* m_input = nullptr;
    cocos2d::CCLabelBMFont* m_status = nullptr;

    ~XeniaSpeedPopup() override;
    bool initPopup();
    void setStatus(std::string const& text, bool ok);
    void onApply(cocos2d::CCObject*);
    void onReset(cocos2d::CCObject*);
    void onAudio(cocos2d::CCObject*);
    void onCloseBtn(cocos2d::CCObject*);

public:
    static XeniaSpeedPopup* create(bool bot, std::function<void()> onApplied);
    static void open(bool bot = false, std::function<void()> onApplied = {});
};
