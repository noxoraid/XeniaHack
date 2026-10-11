#pragma once
#include <Geode/Geode.hpp>

class XeniaTpsPopup : public geode::Popup {
protected:
    static XeniaTpsPopup* s_instance;
    geode::TextInput* m_input = nullptr;
    cocos2d::CCLabelBMFont* m_status = nullptr;

    ~XeniaTpsPopup() override;
    bool initPopup();
    void setStatus(std::string const& text, bool ok);
    void onApply(cocos2d::CCObject*);
    void onReset(cocos2d::CCObject*);
    void onCloseBtn(cocos2d::CCObject*);

public:
    static XeniaTpsPopup* create();
    static void open();
};
