#pragma once
#include <Geode/Geode.hpp>
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"

class XeniaSlider;

class XeniaMenu : public geode::Popup {
protected:
    enum Tab { Player = 0, Bot = 1, Utils = 2, Settings = 3 };
    enum Phase { Live = 0, Release = 1, Typed = 2 };
    static XeniaMenu* s_instance;

    int m_tab = Player;
    int m_pendingTab = Player;
    bool m_tabPending = false;
    float m_W = 470.f, m_H = 270.f;
    cocos2d::CCNode* m_root = nullptr;
    cocos2d::CCNode* m_panel = nullptr;
    cocos2d::CCNode* m_tabBar = nullptr;
    cocos2d::CCNode* m_body = nullptr;
    geode::ScrollLayer* m_scroll = nullptr;
    bool m_covered = false;
    bool m_coverApplied = false;

    ~XeniaMenu() override;
    void gateTick(float);
    void applyCover();
    bool initMenu();
    void build();
    void buildHeader();
    void buildTabBar();
    void buildBody();
    void applyScale();
    void doRebuild(float);
    void scheduleRebuild(float delay);

    void layoutItems(cocos2d::CCNode* content, std::vector<cocos2d::CCNode*> const& items,
                     float w, float viewH, int cols);
    cocos2d::CCNode* makeModCard(char const* title, float w, float h, bool on, int infoTag,
                                 cocos2d::SEL_MenuHandler onToggle, cocos2d::SEL_MenuHandler onGear);
    cocos2d::CCNode* makeSliderRow(char const* title, float w, float mn, float mx, float v, int dec,
                                   std::function<void(float, int)> apply);

    std::vector<cocos2d::CCNode*> buildPlayer(float colW, float cardH);
    std::vector<cocos2d::CCNode*> buildBot(float w);
    std::vector<cocos2d::CCNode*> buildIndicator(float w);
    std::vector<cocos2d::CCNode*> buildUtils(float colW, float cardH);
    std::vector<cocos2d::CCNode*> buildSettings(float w);

    void onTab(cocos2d::CCObject*);
    void doTab(float);
    void onNoclip(cocos2d::CCObject*);
    void onSpeedhack(cocos2d::CCObject*);
    void onSpeedSettings(cocos2d::CCObject*);
    void onSafeMode(cocos2d::CCObject*);
    void onInfo(cocos2d::CCObject*);
    void onBotMode(cocos2d::CCObject*);
    void onStats(cocos2d::CCObject*);
    void onClickInd(cocos2d::CCObject*);
    void onCiMode(cocos2d::CCObject*);
    void onCiLadder(cocos2d::CCObject*);
    void onCiPerfect(cocos2d::CCObject*);
    void onTps(cocos2d::CCObject*);
    void onTpsSettings(cocos2d::CCObject*);
    void onCbf(cocos2d::CCObject*);
    void onParticle(cocos2d::CCObject*);
    void onMacroReplay(cocos2d::CCObject*);
    void onMacroSave(cocos2d::CCObject*);
    void onMacroInfo(cocos2d::CCObject*);
    void onSpeedSet(cocos2d::CCObject*);
    void onStraightShip(cocos2d::CCObject*);
    void onStraightWave(cocos2d::CCObject*);
    void onShipSettings(cocos2d::CCObject*);
    void onAutoClick(cocos2d::CCObject*);
    void onClickerSettings(cocos2d::CCObject*);
    void onWaveSlide(cocos2d::CCObject*);
    void onSlideSettings(cocos2d::CCObject*);
    void onStatsSettings(cocos2d::CCObject*);
    void onWaveSettings(cocos2d::CCObject*);
    void onMacroCreate(cocos2d::CCObject*);
    void onMacroDelete(cocos2d::CCObject*);
    void onMacroRename(cocos2d::CCObject*);
    void onMacroLoad(cocos2d::CCObject*);
    std::vector<cocos2d::CCNode*> buildMacroTable(float w);
    void onTheme(cocos2d::CCObject*);
    void onUiMode(cocos2d::CCObject*);
    void onLang(cocos2d::CCObject*);
    void onFontPrev(cocos2d::CCObject*);
    void onFontNext(cocos2d::CCObject*);
    void onResetUI(cocos2d::CCObject*);
    void onCloseBtn(cocos2d::CCObject*);

public:
    static XeniaMenu* create();
    static void open();
    static void toggle();
};
