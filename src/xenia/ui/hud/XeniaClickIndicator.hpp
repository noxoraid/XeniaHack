#pragma once
#include <Geode/Geode.hpp>

namespace xenia::ui {
    // Indikator klik dari macro .nxr: note turun di sisi kiri layar, tiap kena muncul "Perfect".
    class ClickIndicator : public cocos2d::CCNode {
    public:
        static ClickIndicator* create();
        bool init() override;
        void update(float dt) override;

    private:
        void rebuildCache();
        void relocate(int cur);
        void firePerfect(int lane);

        cocos2d::CCDrawNode* m_draw = nullptr;
        int m_lastCur = -1;
        size_t m_nextHit = 0;
        float m_flash[2] = {0.f, 0.f};
    };
}
