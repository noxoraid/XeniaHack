#pragma once
#include <Geode/Geode.hpp>
#include <string>

namespace xenia::ui {
    std::string colorToHex(int const c[3]);
    bool hexToColor(std::string const& s, int c[3]);

    // Click indicator dari macro .nxr:
    //  - Tangga di kiri layar (klik turun ke bar hit, tiap kena muncul "Perfect")
    //  - Mode Line: jalur player di depan, kotak di titik klik
    //  - Mode Follow Zone: pita lebar = hold, ikut player saat kena lalu hilang
    class ClickIndicator : public cocos2d::CCNode {
    public:
        static ClickIndicator* create();
        bool init() override;
        void update(float dt) override;
        ~ClickIndicator() override;

    private:
        void setHint(std::string const& t);
        void relocate(int cur);
        void firePerfect(int lane);
        void drawLadder(int cur, double tps, float dt);
        void drawWorld(int cur, double tps, PlayLayer* pl);

        cocos2d::CCDrawNode* m_draw = nullptr;   // layar (tangga)
        cocos2d::CCDrawNode* m_world = nullptr;  // dunia level (jalur / zone)
        cocos2d::CCLabelBMFont* m_hint = nullptr;
        std::string m_hintText;
        int m_lastCur = -1;
        size_t m_nextHit = 0;
        float m_flash[2] = {0.f, 0.f};
    };
}
