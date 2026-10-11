#pragma once
#include <Geode/Geode.hpp>

class XeniaMacroPicker : public geode::Popup {
public:
    using Cb = std::function<void(std::string const& name)>;

protected:
    std::string m_title, m_action, m_sel;
    bool m_allowEmpty = false;
    Cb m_cb;
    std::vector<std::string> m_names;
    float m_W = 320.f, m_H = 250.f, m_listW = 0.f, m_listH = 0.f;
    geode::ScrollLayer* m_scroll = nullptr;
    cocos2d::CCLabelBMFont* m_status = nullptr;

    bool initPicker();
    void buildRows();
    void doRebuild(float);
    void onRow(cocos2d::CCObject*);
    void onAction(cocos2d::CCObject*);
    void onCancel(cocos2d::CCObject*);

public:

    static void open(std::string title, std::string action, std::string selected, bool allowEmpty, Cb cb);
};
