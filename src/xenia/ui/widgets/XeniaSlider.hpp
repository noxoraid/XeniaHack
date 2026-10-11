#pragma once
#include <Geode/Geode.hpp>

class XeniaSlider : public cocos2d::CCLayer {
public:
    using Callback = std::function<void(float value, bool released)>;

protected:
    float m_w = 0, m_min = 0, m_max = 1, m_val = 0;
    int m_dec = 2;
    bool m_drag = false;
    cocos2d::CCNode* m_clip = nullptr;
    cocos2d::CCNode* m_track = nullptr;
    cocos2d::CCNode* m_fill = nullptr;
    cocos2d::CCNode* m_thumb = nullptr;
    Callback m_cb;

    bool init(float w, float mn, float mx, float v, int dec, cocos2d::CCNode* clip);
    void layout();
    float valueAt(float localX) const;

public:
    static XeniaSlider* create(float w, float mn, float mx, float v, int dec, cocos2d::CCNode* clip);
    void setCallback(Callback cb) { m_cb = std::move(cb); }
    void setValue(float v);
    float getValue() const { return m_val; }

    void registerWithTouchDispatcher() override;
    bool ccTouchBegan(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchMoved(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchEnded(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
    void ccTouchCancelled(cocos2d::CCTouch* t, cocos2d::CCEvent*) override;
};
