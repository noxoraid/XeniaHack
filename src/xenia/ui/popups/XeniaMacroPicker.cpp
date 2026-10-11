#include "xenia/core/Lang.hpp"
#include "xenia/ui/popups/XeniaMacroPicker.hpp"
#include "xenia/ui/widgets/XeniaScrollbar.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/bot/Macro.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static constexpr float BAR_W = 12.f;
static constexpr float ROW_H = 30.f;
static constexpr float GAP = 6.f;

void XeniaMacroPicker::open(std::string title, std::string action, std::string selected, bool allowEmpty, Cb cb) {
    auto p = new XeniaMacroPicker();
    p->m_title = std::move(title);
    p->m_action = std::move(action);
    p->m_sel = std::move(selected);
    p->m_allowEmpty = allowEmpty;
    p->m_cb = std::move(cb);
    if (p->initPicker()) {
        p->autorelease();
        p->show();
    } else {
        delete p;
    }
}

bool XeniaMacroPicker::initPicker() {
    auto win = CCDirector::get()->getWinSize();
    auto& st = xenia::State::get();
    auto& th = xenia::currentTheme(st.theme);

    m_W = std::min(320.f, win.width * 0.92f);
    m_H = std::min(250.f, win.height * 0.92f);
    if (!Popup::init(m_W, m_H)) return false;
    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    m_names = xenia::bot::MacroManager::list();
    if (std::find(m_names.begin(), m_names.end(), m_sel) == m_names.end()) m_sel.clear();

    auto bg = ui::makeSlice("panel.png", m_W, m_H, 16.f, 0.125f);
    ui::setSliceColor(bg, th.bg);
    ui::setSliceOpacity(bg, (GLubyte)std::round(std::max(st.uiOpacity, 0.85f) * 255.f));
    m_mainLayer->addChild(bg, -1);

    auto title = ui::makeLabel(m_title.c_str(), m_W - 80.f, 0.55f);
    title->setPosition({m_W / 2, m_H - 22.f});
    m_mainLayer->addChild(title);

    auto hint = ui::makeLabel(xenia::tr("Scroll up/down, tap to select, tap again to deselect", "Geser atas/bawah, ketuk untuk memilih, ketuk lagi untuk batal pilih"), m_W - 30.f, 0.32f);
    hint->setPosition({m_W / 2, m_H - 42.f});
    m_mainLayer->addChild(hint);

    float boxX = 14.f, boxY = 64.f, boxW = m_W - 28.f, boxH = m_H - 64.f - 54.f;
    auto box = ui::makeSlice("box.png", boxW, boxH, 12.f);
    box->setPosition({boxX, boxY});
    ui::setSliceOpacity(box, 200);
    m_mainLayer->addChild(box);

    float pad = 6.f;
    m_listW = boxW - pad * 3 - BAR_W;
    m_listH = boxH - pad * 2;
    m_scroll = ScrollLayer::create({m_listW, m_listH});
    m_scroll->setPosition({boxX + pad, boxY + pad});
    m_mainLayer->addChild(m_scroll);

    auto bar = XeniaScrollbar::create(m_scroll, BAR_W, m_listH);
    bar->setPosition({boxX + pad + m_listW + pad, boxY + pad});
    m_mainLayer->addChild(bar);

    m_status = ui::makeLabel("", m_W - 30.f, 0.34f);
    m_status->setPosition({m_W / 2, 52.f});
    m_status->setColor({255, 110, 110});
    m_mainLayer->addChild(m_status);

    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    m_mainLayer->addChild(menu, 5);
    auto no = CCMenuItemSpriteExtra::create(ui::makeButton(xenia::tr("Cancel", "Batal"), 110.f, false), this, menu_selector(XeniaMacroPicker::onCancel));
    no->setPosition({m_W / 2 - 62.f, 28.f});
    menu->addChild(no);
    auto yes = CCMenuItemSpriteExtra::create(ui::makeButton(m_action.c_str(), 110.f, true), this, menu_selector(XeniaMacroPicker::onAction));
    yes->setPosition({m_W / 2 + 62.f, 28.f});
    menu->addChild(yes);

    buildRows();
    m_scroll->moveToTop();
    return true;
}

void XeniaMacroPicker::buildRows() {
    auto content = m_scroll->m_contentLayer;
    content->removeAllChildren();

    int n = (int)m_names.size();
    float total = GAP + n * (ROW_H + GAP);
    float contentH = std::max(m_listH, total);
    content->setContentSize({m_listW, contentH});

    if (n == 0) {
        auto l = ui::makeLabel(xenia::tr("No macros yet. Create one first.", "Belum ada makro. Buat dulu lewat Create."), m_listW - 20.f, 0.4f);
        l->setPosition({m_listW / 2, contentH - 30.f});
        content->addChild(l);
        return;
    }

    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    menu->setContentSize({m_listW, contentH});
    content->addChild(menu);
    for (int i = 0; i < n; i++) {
        bool on = m_names[i] == m_sel;
        auto item = CCMenuItemSpriteExtra::create(
            ui::makeButton(m_names[i].c_str(), m_listW - 4.f, on, ROW_H), this, menu_selector(XeniaMacroPicker::onRow));
        item->setTag(i);
        item->setPosition({m_listW / 2, contentH - GAP - ROW_H / 2 - i * (ROW_H + GAP)});
        menu->addChild(item);
    }
}

void XeniaMacroPicker::doRebuild(float) {
    float y = m_scroll->m_contentLayer->getPositionY();
    buildRows();
    float vh = m_scroll->getContentHeight();
    float ch = m_scroll->m_contentLayer->getContentHeight();
    float minY = std::min(0.f, vh - ch);
    m_scroll->m_contentLayer->setPositionY(std::clamp(y, minY, 0.f));
}

void XeniaMacroPicker::onRow(CCObject* s) {
    int i = static_cast<CCNode*>(s)->getTag();
    if (i < 0 || i >= (int)m_names.size()) return;
    m_sel = (m_sel == m_names[i]) ? std::string() : m_names[i];
    m_status->setString("");

    this->unschedule(schedule_selector(XeniaMacroPicker::doRebuild));
    this->scheduleOnce(schedule_selector(XeniaMacroPicker::doRebuild), 0.01f);
}

void XeniaMacroPicker::onAction(CCObject*) {
    if (m_sel.empty() && !m_allowEmpty) {
        m_status->setString(xenia::tr("Pick a macro first", "Pilih makro dulu"));
        return;
    }
    auto cb = m_cb;
    auto sel = m_sel;
    this->onClose(nullptr);
    if (cb) cb(sel);
}

void XeniaMacroPicker::onCancel(CCObject*) {
    this->onClose(nullptr);
}
