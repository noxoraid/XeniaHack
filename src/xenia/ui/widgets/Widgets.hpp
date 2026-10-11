#pragma once
#include <Geode/Geode.hpp>

namespace xenia::ui {
    char const* font();
    float fontNorm();
    int fontCount();
    char const* fontName(int i);

    cocos2d::CCSprite* loadSprite(char const* name);

    cocos2d::CCNode* makeSlice(char const* png, float w, float h, float corner, float insetFrac = 0.2f);
    cocos2d::CCNode* makeVSlice(char const* png, float w, float h);
    void setSliceColor(cocos2d::CCNode* slice, cocos2d::ccColor3B c);
    void setSliceOpacity(cocos2d::CCNode* slice, GLubyte o);

    FLAlertLayer* topPopup();
    bool coveredByPopup(cocos2d::CCNode* n);
    void setItemsEnabled(cocos2d::CCNode* root, bool enabled);

    cocos2d::CCSprite* makeHolder(float w, float h);
    cocos2d::CCSprite* makeIcon(char const* png, float size);
    cocos2d::CCLabelBMFont* makeLabel(char const* text, float maxW, float scale = 0.5f);

    cocos2d::CCSprite* makeButton(char const* text, float w, bool active, float h = 28.f);
    cocos2d::CCSprite* makeTabButton(char const* icon, char const* text, float w, bool active, float h = 32.f);
    cocos2d::CCNode* makeCard(float w, float h);
    CCMenuItemToggler* makeCheckbox(cocos2d::CCObject* target, cocos2d::SEL_MenuHandler cb, bool on, float size = 22.f);
    CCMenuItemSpriteExtra* makeIconButton(char const* png, float size, cocos2d::CCObject* target, cocos2d::SEL_MenuHandler cb);

    CCMenuItemSpriteExtra* makeSquareIconButton(char const* png, float box, float icon, cocos2d::CCObject* target, cocos2d::SEL_MenuHandler cb);
}
