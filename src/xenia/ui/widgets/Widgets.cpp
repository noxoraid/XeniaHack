#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;

namespace xenia::ui {

    static constexpr char const* kFonts[] = {nullptr, "bigFont.fnt", "chatFont.fnt", "goldFont.fnt"};
    static constexpr char const* kNames[] = {"Xenia", "Big", "Chat", "Gold"};
    static constexpr float kNorm[] = {0.63f, 1.f, 1.5f, 1.f};
    static constexpr int kFontCount = 4;

    int fontCount() { return kFontCount; }
    char const* fontName(int i) { return kNames[std::clamp(i, 0, kFontCount - 1)]; }
    float fontNorm() { return kNorm[std::clamp(State::get().fontIdx, 0, kFontCount - 1)]; }

    char const* font() {
        static std::string const custom = [] {
            std::string name = "xeniaFont.fnt"_spr;
            auto full = CCFileUtils::sharedFileUtils()->fullPathForFilename(name.c_str(), false);
            if (full.empty() || full == name) return std::string("bigFont.fnt");
            return name;
        }();
        int i = std::clamp(State::get().fontIdx, 0, kFontCount - 1);
        return i == 0 ? custom.c_str() : kFonts[i];
    }

    CCSprite* loadSprite(char const* name) {
        auto s = CCSprite::create(std::string(Mod::get()->expandSpriteName(name)).c_str());
        if (!s) return CCSprite::create();
        if (auto t = s->getTexture()) t->setAntiAliasTexParameters();
        return s;
    }

    CCNode* makeSlice(char const* png, float w, float h, float corner, float insetFrac) {
        auto node = CCNode::create();
        node->setContentSize({w, h});
        auto probe = loadSprite(png);
        auto tex = probe->getTexture();
        if (!tex) return node;
        auto r = probe->getTextureRect();
        float sw = r.size.width, sh = r.size.height;
        float ci = std::min(sw, sh) * insetFrac;
        corner = std::max(0.f, std::min(corner, std::min(w, h) / 2));
        float sx[4] = {0, ci, sw - ci, sw}, sy[4] = {0, ci, sh - ci, sh};
        float dx[4] = {0, corner, w - corner, w}, dy[4] = {0, corner, h - corner, h};
        for (int iy = 0; iy < 3; iy++) {
            int row = 2 - iy;
            for (int ix = 0; ix < 3; ix++) {
                float srcW = sx[ix + 1] - sx[ix], srcH = sy[iy + 1] - sy[iy];
                float dstW = dx[ix + 1] - dx[ix], dstH = dy[row + 1] - dy[row];
                if (srcW <= 0.01f || srcH <= 0.01f || dstW <= 0.01f || dstH <= 0.01f) continue;
                auto s = CCSprite::createWithTexture(tex, CCRect(r.origin.x + sx[ix], r.origin.y + sy[iy], srcW, srcH));
                if (!s) continue;
                s->setAnchorPoint({0.f, 0.f});
                s->setPosition({dx[ix], dy[row]});
                s->setScaleX((dstW + (ix < 2 ? 0.3f : 0.f)) / srcW);
                s->setScaleY((dstH + (row < 2 ? 0.3f : 0.f)) / srcH);
                node->addChild(s);
            }
        }
        return node;
    }

    CCNode* makeVSlice(char const* png, float w, float h) {
        auto node = CCNode::create();
        node->setContentSize({w, h});
        auto probe = loadSprite(png);
        auto tex = probe->getTexture();
        if (!tex) return node;
        auto r = probe->getTextureRect();
        float sw = r.size.width, sh = r.size.height;
        float k = w / std::max(1.f, sw);
        float capSrc = sw * 0.5f;
        float capDst = capSrc * k;
        if (h < capDst * 2) {
            capDst = h / 2;
            capSrc = capDst / k;
        }
        float midSrc = sh - capSrc * 2;
        float midDst = h - capDst * 2;
        auto part = [&](float srcY, float srcH, float dstY, float dstH) {
            if (srcH <= 0.01f || dstH <= 0.01f) return;
            auto s = CCSprite::createWithTexture(tex, CCRect(r.origin.x, r.origin.y + srcY, sw, srcH));
            if (!s) return;
            s->setAnchorPoint({0.f, 0.f});
            s->setPosition({0.f, dstY});
            s->setScaleX(k);
            s->setScaleY(dstH / srcH);
            node->addChild(s);
        };
        part(0.f, capSrc, h - capDst, capDst);
        part(capSrc, midSrc, capDst - 0.3f, midDst + 0.6f);
        part(sh - capSrc, capSrc, 0.f, capDst);
        return node;
    }

    void setSliceColor(CCNode* slice, ccColor3B c) {
        if (!slice || !slice->getChildren()) return;
        for (auto ch : CCArrayExt<CCNode*>(slice->getChildren()))
            if (auto s = typeinfo_cast<CCSprite*>(ch)) s->setColor(c);
    }

    void setSliceOpacity(CCNode* slice, GLubyte o) {
        if (!slice || !slice->getChildren()) return;
        for (auto ch : CCArrayExt<CCNode*>(slice->getChildren()))
            if (auto s = typeinfo_cast<CCSprite*>(ch)) s->setOpacity(o);
    }

    static void scanPopups(CCNode* n, int depth, FLAlertLayer*& top) {
        if (!n || !n->getChildren()) return;
        for (auto ch : CCArrayExt<CCNode*>(n->getChildren())) {
            if (!ch) continue;
            if (auto a = typeinfo_cast<FLAlertLayer*>(ch)) {
                if (a->isVisible() && a->isRunning()) top = a;
            }
            if (depth < 2) scanPopups(ch, depth + 1, top);
        }
    }

    FLAlertLayer* topPopup() {
        auto scene = CCDirector::get()->getRunningScene();
        FLAlertLayer* top = nullptr;
        scanPopups(scene, 0, top);
        return top;
    }

    bool coveredByPopup(CCNode* n) {
        auto top = topPopup();
        if (!top) return false;
        for (auto p = n; p; p = p->getParent()) {
            if (p == top) return false;
        }
        return true;
    }

    void setItemsEnabled(CCNode* root, bool enabled) {
        if (!root || !root->getChildren()) return;
        for (auto ch : CCArrayExt<CCNode*>(root->getChildren())) {
            if (!ch) continue;
            if (auto it = typeinfo_cast<CCMenuItem*>(ch)) it->setEnabled(enabled);
            setItemsEnabled(ch, enabled);
        }
    }

    CCSprite* makeHolder(float w, float h) {
        auto holder = CCSprite::create();
        holder->setContentSize({w, h});
        return holder;
    }

    CCSprite* makeIcon(char const* png, float size) {
        auto s = loadSprite(png);
        float m = std::max(s->getContentWidth(), s->getContentHeight());
        if (m > 0) s->setScale(size / m);
        return s;
    }

    CCLabelBMFont* makeLabel(char const* text, float maxW, float scale) {
        auto l = CCLabelBMFont::create(text, font());
        l->limitLabelWidth(maxW, scale * fontNorm() * State::get().textScale, 0.15f);
        return l;
    }

    CCSprite* makeButton(char const* text, float w, bool active, float h) {
        auto holder = makeHolder(w, h);
        auto bg = makeSlice(active ? "cardSel.png" : "card.png", w, h, std::min(8.f, h / 2.2f));
        holder->addChild(bg);
        auto lbl = makeLabel(text, w - 12.f, 0.45f);
        lbl->setPosition({w / 2, h / 2});
        holder->addChild(lbl);
        return holder;
    }

    CCSprite* makeTabButton(char const* icon, char const* text, float w, bool active, float h) {
        auto holder = makeHolder(w, h);
        holder->addChild(makeSlice(active ? "cardSel.png" : "card.png", w, h, std::min(8.f, h / 2.2f)));
        auto ic = makeIcon(icon, 16.f);
        ic->setPosition({17.f, h / 2});
        holder->addChild(ic);
        auto lbl = makeLabel(text, w - 42.f, 0.42f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({32.f, h / 2});
        holder->addChild(lbl);
        return holder;
    }

    CCNode* makeCard(float w, float h) {
        auto card = makeSlice("card.png", w, h, std::min(8.f, h / 2.2f));
        setSliceOpacity(card, 150);
        return card;
    }

    CCMenuItemToggler* makeCheckbox(CCObject* target, SEL_MenuHandler cb, bool on, float size) {
        auto& t = currentTheme(State::get().theme);
        auto off = makeHolder(size, size);
        off->addChild(makeSlice("box.png", size, size, size * 0.28f));
        auto onS = makeHolder(size, size);
        onS->addChild(makeSlice("box.png", size, size, size * 0.28f));
        auto chk = makeIcon("check.png", size * 0.72f);
        chk->setColor(t.active);
        chk->setPosition({size / 2, size / 2});
        onS->addChild(chk);
        auto tg = CCMenuItemToggler::create(off, onS, target, cb);
        tg->toggle(on);
        return tg;
    }

    CCMenuItemSpriteExtra* makeIconButton(char const* png, float size, CCObject* target, SEL_MenuHandler cb) {
        return CCMenuItemSpriteExtra::create(makeIcon(png, size), target, cb);
    }

    CCMenuItemSpriteExtra* makeSquareIconButton(char const* png, float box, float icon, CCObject* target, SEL_MenuHandler cb) {
        auto holder = makeHolder(box, box);
        holder->addChild(makeSlice("card.png", box, box, box * 0.3f));
        auto ic = makeIcon(png, icon);
        ic->setPosition({box / 2, box / 2});
        holder->addChild(ic);
        return CCMenuItemSpriteExtra::create(holder, target, cb);
    }
}
