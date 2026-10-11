#pragma once
#include <Geode/Geode.hpp>

class XeniaUtilPopup : public geode::Popup {
protected:
    static XeniaUtilPopup* s_instance;
    int m_kind = 0;
    geode::TextInput* m_input = nullptr;
    cocos2d::CCLabelBMFont* m_status = nullptr;

    ~XeniaUtilPopup() override;
    bool initPopup();
    void setStatus(std::string const& text, bool ok);
    void onApply(cocos2d::CCObject*);
    void onReset(cocos2d::CCObject*);
    void onClick(cocos2d::CCObject*);
    void onCloseBtn(cocos2d::CCObject*);

public:
    static XeniaUtilPopup* create(int kind);
    static void open(int kind);
};
