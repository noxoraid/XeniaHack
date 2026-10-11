#pragma once
#include <Geode/Geode.hpp>

class XeniaOverlay : public cocos2d::CCLayer {
protected:
    static XeniaOverlay* s_instance;
    cocos2d::CCSprite* m_logo = nullptr;
    cocos2d::CCPoint m_offset, m_start;
    bool m_moved = false;

    ~XeniaOverlay() override;
    bool init() override;
    void applySize();

public:
    static XeniaOverlay* create();
    static void ensure();
    static void refresh();

    void registerWithTouchDispatcher() override;
    bool ccTouchBegan(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchMoved(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchEnded(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchCancelled(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
};
