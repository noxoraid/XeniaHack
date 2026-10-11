#pragma once
#include <Geode/Geode.hpp>

class XeniaDialog : public geode::Popup {
public:

    using InputCb = std::function<std::string(std::string const&)>;

protected:
    enum class Kind { Info, Confirm, Input };
    Kind m_kind = Kind::Info;
    std::string m_title, m_text, m_hint, m_initial;
    std::function<void()> m_onYes;
    InputCb m_onInput;
    geode::TextInput* m_input = nullptr;
    cocos2d::CCLabelBMFont* m_status = nullptr;

    bool initDialog();
    void setStatus(std::string const& text);
    void onOk(cocos2d::CCObject*);
    void onYes(cocos2d::CCObject*);
    void onCancel(cocos2d::CCObject*);

    static XeniaDialog* make(Kind k, std::string title, std::string text);

public:
    static void info(std::string title, std::string text);
    static void confirm(std::string title, std::string text, std::function<void()> onYes);
    static void input(std::string title, std::string hint, std::string initial, InputCb onOk);
};
