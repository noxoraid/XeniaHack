#pragma once
#include <Geode/Geode.hpp>

class XeniaScrollbar : public cocos2d::CCLayer {
protected:
    geode::ScrollLayer* m_scroll = nullptr;
    cocos2d::CCNode* m_track = nullptr;
    cocos2d::CCNode* m_thumb = nullptr;
    float m_thumbH = -1.f;
    float m_w = 0, m_h = 0;
    bool m_drag = false;

    bool init(geode::ScrollLayer* scroll, float w, float h);
    float thumbHeight() const;
    void applyTouchY(float y);

public:

    static XeniaScrollbar* create(geode::ScrollLayer* scroll, float w, float h);

    void update(float dt) override;
    void registerWithTouchDispatcher() override;
    bool ccTouchBegan(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchMoved(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchEnded(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchCancelled(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
};
