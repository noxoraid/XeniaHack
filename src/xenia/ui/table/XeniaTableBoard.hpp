#pragma once
#include <Geode/Geode.hpp>
#include <functional>
#include <set>
#include <string>
#include <vector>

class XeniaTableBoard : public cocos2d::CCLayer {
public:
    struct Cell {
        enum Kind { Toggle, Text, Input } kind = Toggle;
        std::string text;
        std::function<bool()> on;
        std::function<void()> tap;
        std::string hint;
        float weight = 1.f;
        bool center = false;
        bool dim = false;
        float indent = 0.f;

        std::string initial;
        std::string filter;
        int maxChars = 8;
        std::function<void(std::string const&)> change;
    };

    struct Row {
        std::vector<Cell> cells;
        int gear = -1;
        bool marker = false;
    };

    struct Win {
        int id = 0;
        std::string title;
        std::vector<Row> rows;
    };

protected:
    struct Box { float x, y, w, h; };
    struct HitCell { int win, row, cell; Box box; };
    struct WinLayout { int id; Box box; Box head; int rowCount; };
    enum class Target { None, Head, Cell, Gear, Close };
    enum class Mode { None, Pan, DragWin };

    cocos2d::CCSize m_view;
    cocos2d::CCNode* m_content = nullptr;
    cocos2d::CCNode* m_dragNode = nullptr;
    cocos2d::CCLayerColor* m_press = nullptr;
    cocos2d::CCLayerColor* m_hBar = nullptr;
    cocos2d::CCLayerColor* m_vBar = nullptr;
    cocos2d::CCLabelBMFont* m_hintLbl = nullptr;
    cocos2d::CCLayerColor* m_status = nullptr;

    std::vector<Win> m_wins;
    std::vector<WinLayout> m_layout;
    std::vector<HitCell> m_hits;
    std::vector<geode::TextInput*> m_inputs;
    std::vector<cocos2d::CCNode*> m_inputBoxes;
    std::vector<std::pair<cocos2d::CCLabelBMFont*, std::function<bool()>>> m_live;
    std::vector<std::pair<cocos2d::CCLayerColor*, std::function<bool()>>> m_liveBars;
    std::vector<int> m_order;

    float m_contentW = 0.f, m_contentH = 0.f;
    float m_scrollX = 0.f, m_scrollY = 0.f;
    float m_scale = 1.f;

    Target m_target = Target::None;
    int m_tWin = -1, m_tRow = -1, m_tCell = -1;
    Mode m_mode = Mode::None;
    cocos2d::CCPoint m_start, m_last, m_dragOff;
    bool m_down = false;
    int m_dragWinId = -1;

    std::string m_macroInput;
    bool m_delArm = false;
    std::string m_hintText;
    cocos2d::ccColor3B m_hintColor;

    bool init(cocos2d::CCSize view);
    void loadOrder();
    void saveOrder();
    void buildWindows();
    void rebuild();
    void doRebuild(float);
    void scheduleRebuild(float delay = 0.02f);
    void refreshLive();
    void disarmDelete(float);

    void layoutWindows();
    void drawWindows();
    void applyScroll();
    void clampScroll();
    void updateBars();
    void setHint(std::string const& text, bool ok = true);
    void defocusAll();

    cocos2d::CCPoint toContent(cocos2d::CCPoint const& screen) const;
    bool pick(cocos2d::CCPoint const& screen);
    bool onInputBox(cocos2d::CCPoint const& screen) const;
    void showPress(Box const& b);
    void hidePress();
    void doTap();
    void finishDrag(cocos2d::CCPoint const& screen);

    void setBotMode(int mode);
    void macroLoad(std::string const& name);
    void macroCreate();
    void macroRename();
    void macroSave();
    void macroDelete();
    void setUiMode(int mode);
    void setLang(int lang);
    void resetLayout();
    void resetUi();

    float colWidth() const;

public:
    static XeniaTableBoard* create(cocos2d::CCSize view);

    void registerWithTouchDispatcher() override;
    bool ccTouchBegan(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchMoved(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchEnded(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchCancelled(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void scrollWheel(float y, float x);
};
