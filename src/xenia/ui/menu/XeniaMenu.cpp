#include "xenia/ui/Platform.hpp"
#include "xenia/core/Lang.hpp"
#include "xenia/ui/menu/XeniaMenu.hpp"
#include "xenia/ui/widgets/XeniaScrollbar.hpp"
#include "xenia/ui/widgets/XeniaSlider.hpp"
#include "xenia/ui/popups/XeniaSpeedPopup.hpp"
#include "xenia/ui/popups/XeniaDialog.hpp"
#include "xenia/ui/popups/XeniaMacroPicker.hpp"
#include "xenia/ui/popups/XeniaTpsPopup.hpp"
#include "xenia/ui/popups/XeniaUtilPopup.hpp"
#include "xenia/ui/popups/XeniaClickerPopup.hpp"
#include "xenia/ui/popups/XeniaStatsPopup.hpp"
#include "xenia/bot/Macro.hpp"
#include "xenia/bot/Bot.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/ui/overlay/XeniaOverlay.hpp"
#include "xenia/ui/table/XeniaTable.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static constexpr float BAR_W = 14.f;
static constexpr float PAD = 8.f;

XeniaMenu* XeniaMenu::s_instance = nullptr;
static int s_lastTab = 0;

XeniaMenu::~XeniaMenu() {
    m_scroll = nullptr;
    if (s_instance == this) s_instance = nullptr;
}

XeniaMenu* XeniaMenu::create() {
    auto ret = new XeniaMenu();
    if (ret && ret->initMenu()) {
        ret->autorelease();
        s_instance = ret;
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

void XeniaMenu::open() {
    if (s_instance || XeniaTable::isOpen()) return;
    if (xenia::State::get().uiMode == 0) {
        XeniaTable::open();
        return;
    }
    if (auto m = XeniaMenu::create()) m->show();
}

void XeniaMenu::toggle() {
    if (XeniaTable::isOpen()) {
        XeniaTable::closeNow();
        return;
    }
    if (s_instance) {
        auto m = s_instance;
        s_instance = nullptr;
        m->onClose(nullptr);
        return;
    }
    open();
}

bool XeniaMenu::initMenu() {

    auto win = CCDirector::get()->getWinSize();
    m_W = std::clamp(win.width * 0.74f, 380.f, 560.f);
    m_H = std::clamp(win.height * 0.90f, 230.f, 300.f);
    if (!Popup::init(m_W, m_H)) return false;
    m_tab = std::clamp(s_lastTab, 0, 3);

    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    m_root = CCNode::create();
    m_root->setContentSize({m_W, m_H});
    m_root->setAnchorPoint({0.5f, 0.5f});
    m_root->ignoreAnchorPointForPosition(false);
    m_root->setPosition(m_size / 2);
    m_mainLayer->addChild(m_root);

    build();
    applyScale();
    this->schedule(schedule_selector(XeniaMenu::gateTick), 0.f);
    return true;
}

void XeniaMenu::applyCover() {
    if (!m_root) return;
    ui::setItemsEnabled(m_root, !m_covered);
    if (m_scroll && m_scroll->getParent()) m_scroll->setTouchEnabled(!m_covered);
    m_coverApplied = true;
}

void XeniaMenu::gateTick(float) {
    bool cov = ui::coveredByPopup(this);
    if (cov != m_covered) {
        m_covered = cov;
        m_coverApplied = false;
    }
    if (!m_coverApplied) applyCover();
}

void XeniaMenu::applyScale() {
    auto win = CCDirector::get()->getWinSize();
    float fit = std::min(win.width * 0.98f / m_W, win.height * 0.98f / m_H);
    m_root->setScale(std::min(xenia::State::get().uiScale, fit));
}

void XeniaMenu::build() {
    m_scroll = nullptr;
    m_root->removeAllChildren();
    auto& st = xenia::State::get();
    m_panel = ui::makeSlice("panel.png", m_W, m_H, 16.f, 0.125f);
    ui::setSliceColor(m_panel, xenia::currentTheme(st.theme).bg);
    ui::setSliceOpacity(m_panel, (GLubyte)std::round(st.uiOpacity * 255.f));
    m_root->addChild(m_panel, -1);

    buildHeader();
    m_tabBar = CCNode::create();
    m_root->addChild(m_tabBar);
    m_body = CCNode::create();
    m_root->addChild(m_body);
    buildTabBar();
    buildBody();
    m_coverApplied = false;
}

void XeniaMenu::buildHeader() {
    auto logo = ui::makeIcon("logo.png", 28.f);
    logo->setPosition({26.f, m_H - 24.f});
    m_root->addChild(logo);
    auto title = ui::makeLabel("XENIA", 160.f, 0.55f);
    title->setAnchorPoint({0.f, 0.5f});
    title->setPosition({46.f, m_H - 24.f});
    m_root->addChild(title);

    auto cm = CCMenu::create();
    cm->setPosition({0, 0});
    auto close = ui::makeSquareIconButton("close.png", 26.f, 12.f, this, menu_selector(XeniaMenu::onCloseBtn));
    close->setPosition({m_W - 22.f, m_H - 24.f});
    cm->addChild(close);
    m_root->addChild(cm, 10);
}

void XeniaMenu::onCloseBtn(CCObject*) {
    this->onClose(nullptr);
}

void XeniaMenu::buildTabBar() {
    m_tabBar->removeAllChildren();
    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    m_tabBar->addChild(menu);

    float leftW = std::clamp(m_W * 0.25f, 100.f, 140.f);
    char const* names[] = {"Player", "Bot", "Utils", xenia::tr("Settings", "Pengaturan")};
    char const* icons[] = {"icon_player.png", "icon_bot.png", "icon_utils.png", "icon_sliders.png"};
    float bw = leftW - 16.f, bh = 32.f;
    for (int i = 0; i < 4; i++) {
        auto item = CCMenuItemSpriteExtra::create(
            ui::makeTabButton(icons[i], names[i], bw, i == m_tab, bh), this, menu_selector(XeniaMenu::onTab));
        item->setTag(i);
        item->setPosition({10.f + bw / 2, m_H - 62.f - i * (bh + 6.f)});
        menu->addChild(item);
    }
}

void XeniaMenu::buildBody() {
    m_scroll = nullptr;
    m_body->removeAllChildren();

    float leftW = std::clamp(m_W * 0.25f, 100.f, 140.f);
    float cX = leftW + 2.f, cY = 10.f;
    float cW = m_W - cX - 10.f, cH = m_H - 54.f;

    auto cbg = ui::makeSlice("box.png", cW, cH, 12.f);
    cbg->setPosition({cX, cY});
    ui::setSliceOpacity(cbg, 200);
    m_body->addChild(cbg);

    float sX = cX + PAD, sY = cY + PAD;
    float sW = cW - PAD * 3 - BAR_W, sH = cH - PAD * 2;

    m_scroll = ScrollLayer::create({sW, sH});
    m_scroll->setPosition({sX, sY});
    m_body->addChild(m_scroll);
    auto content = m_scroll->m_contentLayer;

    std::vector<CCNode*> items;
    int cols = 1;
    switch (m_tab) {
        case Player:   cols = 2; items = buildPlayer((sW - PAD) / 2, 36.f); break;
        case Bot:      cols = 1; items = buildBot(sW); break;
        case Utils:    cols = 2; items = buildUtils((sW - PAD) / 2, 36.f); break;
        case Settings: cols = 1; items = buildSettings(sW); break;
    }
    layoutItems(content, items, sW, sH, cols);
    m_scroll->moveToTop();

    auto bar = XeniaScrollbar::create(m_scroll, BAR_W, sH);
    bar->setPosition({sX + sW + PAD, sY});
    m_body->addChild(bar);
}

void XeniaMenu::layoutItems(CCNode* content, std::vector<CCNode*> const& items,
                             float w, float viewH, int cols) {
    float gap = PAD;
    float colW = (w - gap * (cols - 1)) / cols;
    std::vector<float> rowH;
    for (size_t i = 0; i < items.size(); i += cols) {
        float h = 0;
        for (int c = 0; c < cols && i + c < items.size(); c++) h = std::max(h, items[i + c]->getContentHeight());
        rowH.push_back(h);
    }
    float total = gap;
    for (auto h : rowH) total += h + gap;
    float contentH = std::max(viewH, total);
    content->setContentSize({w, contentH});
    float y = contentH - gap;
    for (size_t r = 0; r < rowH.size(); r++) {
        y -= rowH[r];
        for (int c = 0; c < cols && r * cols + c < items.size(); c++) {
            auto it = items[r * cols + c];
            it->setAnchorPoint({0, 0});
            it->setPosition({c * (colW + gap), y});
            content->addChild(it);
        }
        y -= gap;
    }
}

CCNode* XeniaMenu::makeModCard(char const* title, float w, float h, bool on, int infoTag,
                                SEL_MenuHandler onToggle, SEL_MenuHandler onGear) {
    auto card = ui::makeCard(w, h);
    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    card->addChild(menu);

    float cb = 22.f;
    auto tgl = ui::makeCheckbox(this, onToggle, on, cb);
    tgl->setPosition({10.f + cb / 2, h / 2});
    menu->addChild(tgl);

    float right = 10.f;
    auto info = ui::makeIconButton("info.png", 15.f, this, menu_selector(XeniaMenu::onInfo));
    info->setTag(infoTag);
    info->setPosition({w - right - 7.5f, h / 2});
    menu->addChild(info);
    right += 15.f + 8.f;
    if (onGear) {
        auto gear = ui::makeSquareIconButton("settings.png", 22.f, 14.f, this, onGear);
        gear->setPosition({w - right - 11.f, h / 2});
        menu->addChild(gear);
        right += 22.f + 8.f;
    }
    float lx = 10.f + cb + 8.f;
    auto lbl = ui::makeLabel(title, w - lx - right, 0.45f);
    lbl->setAnchorPoint({0.f, 0.5f});
    lbl->setPosition({lx, h / 2});
    card->addChild(lbl);
    return card;
}

CCNode* XeniaMenu::makeSliderRow(char const* title, float w, float mn, float mx, float v, int dec,
                                  std::function<void(float, int)> apply) {
    float h = 56.f;
    auto row = ui::makeCard(w, h);
    auto lbl = ui::makeLabel(title, w - 100.f, 0.45f);
    lbl->setAnchorPoint({0.f, 0.5f});
    lbl->setPosition({12.f, h - 17.f});
    row->addChild(lbl);

    float iw = 58.f, ih = 22.f;
    auto inBg = ui::makeSlice("box.png", iw, ih, 7.f);
    inBg->setPosition({w - 12.f - iw, h - 6.f - ih});
    row->addChild(inBg);

    auto fmtv = [dec](float x) { return fmt::format("{:.{}f}", x, dec); };
    auto input = TextInput::create(iw, "", ui::font());
    input->setFilter("0123456789.");
    input->setMaxCharCount(5);
    input->setString(fmtv(v), false);
    if (auto b = input->getBGSprite()) b->setVisible(false);
    input->setScale(0.8f);
    input->setPosition({w - 12.f - iw / 2, h - 6.f - ih / 2});
    row->addChild(input);

    auto slider = XeniaSlider::create(w - 24.f, mn, mx, v, dec, m_scroll);
    slider->setPosition({12.f, 6.f});
    row->addChild(slider);

    Ref<XeniaSlider> sRef = slider;
    Ref<TextInput> iRef = input;
    slider->setCallback([iRef, apply, fmtv](float val, bool released) {
        iRef->setString(fmtv(val), false);
        apply(val, released ? Release : Live);
    });
    input->setCallback([sRef, apply, mn, mx](std::string const& s) {
        if (s.empty()) return;
        char* end = nullptr;
        float x = std::strtof(s.c_str(), &end);
        if (end == s.c_str() || !std::isfinite(x) || x < mn || x > mx) return;
        sRef->setValue(x);
        apply(sRef->getValue(), Typed);
    });
    return row;
}

std::vector<CCNode*> XeniaMenu::buildPlayer(float colW, float cardH) {
    auto& st = xenia::State::get();
    return {
        makeModCard("Safe Mode", colW, cardH, st.safeMode, 2, menu_selector(XeniaMenu::onSafeMode), nullptr),
        makeModCard("Noclip",    colW, cardH, st.noclip,    0, menu_selector(XeniaMenu::onNoclip),    nullptr),
        makeModCard("Speedhack", colW, cardH, st.speedhack, 1, menu_selector(XeniaMenu::onSpeedhack), menu_selector(XeniaMenu::onSpeedSettings)),
        makeModCard("TPS Bypass", colW, cardH, st.tpsBypass, 4, menu_selector(XeniaMenu::onTps), menu_selector(XeniaMenu::onTpsSettings)),
        makeModCard("Stats HUD", colW, cardH, st.showStats, 3, menu_selector(XeniaMenu::onStats), menu_selector(XeniaMenu::onStatsSettings)),
    };
}

std::vector<CCNode*> XeniaMenu::buildBot(float w) {
    auto& st = xenia::State::get();
    std::vector<CCNode*> out;

    {
        float h = 70.f;
        auto card = ui::makeCard(w, h);
        auto lbl = ui::makeLabel("Bot Mode", w - 24.f, 0.45f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({12.f, h - 16.f});
        card->addChild(lbl);

        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        char const* names[] = {"Disable", "Record", "Playback"};
        float bw = (w - 24.f - 12.f) / 3;
        for (int i = 0; i < 3; i++) {
            bool on = (int)st.bot == i;
            auto item = CCMenuItemSpriteExtra::create(ui::makeButton(names[i], bw, on), this, menu_selector(XeniaMenu::onBotMode));
            item->setTag(i);
            item->setPosition({12.f + bw / 2 + i * (bw + 6.f), 20.f});
            menu->addChild(item);
        }
        out.push_back(card);
    }

    {
        float h = 44.f;
        auto card = ui::makeCard(w, h);
        auto lbl = ui::makeLabel("Replay", 80.f, 0.5f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({12.f, h / 2});
        card->addChild(lbl);

        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        float bw = std::min(200.f, w - 110.f);
        std::string nm = st.macroName.empty() ? std::string(xenia::tr("- pick a macro -", "- pilih makro -")) : st.macroName;
        auto btn = CCMenuItemSpriteExtra::create(
            ui::makeButton(nm.c_str(), bw, !st.macroName.empty(), 30.f), this, menu_selector(XeniaMenu::onMacroReplay));
        btn->setPosition({w - 12.f - bw / 2, h / 2});
        menu->addChild(btn);
        out.push_back(card);
    }

    for (auto n : buildMacroTable(w)) out.push_back(n);

    {
        float h = 84.f;
        auto card = ui::makeCard(w, h);
        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        struct Act { char const* name; SEL_MenuHandler cb; };
        Act acts[] = {
            {xenia::tr("Create", "Buat"), menu_selector(XeniaMenu::onMacroCreate)},
            {xenia::tr("Delete", "Hapus"), menu_selector(XeniaMenu::onMacroDelete)},
            {xenia::tr("Rename", "Ganti"), menu_selector(XeniaMenu::onMacroRename)},
            {xenia::tr("Load", "Muat"),   menu_selector(XeniaMenu::onMacroLoad)},
        };
        float gap = 6.f;
        float bw = (w - 24.f - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            auto item = CCMenuItemSpriteExtra::create(ui::makeButton(acts[i].name, bw, false), this, acts[i].cb);
            item->setPosition({12.f + bw / 2 + i * (bw + gap), h - 24.f});
            menu->addChild(item);
        }
        auto save = CCMenuItemSpriteExtra::create(ui::makeButton(xenia::tr("Save", "Simpan"), w - 24.f, true), this, menu_selector(XeniaMenu::onMacroSave));
        save->setPosition({w / 2, 24.f});
        menu->addChild(save);
        out.push_back(card);
    }

    {
        float h = 44.f;
        auto card = ui::makeCard(w, h);
        auto lbl = ui::makeLabel(fmt::format("Bot Speed  {}x", st.botSpeed).c_str(), w - 130.f, 0.45f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({12.f, h / 2});
        card->addChild(lbl);

        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        auto set = CCMenuItemSpriteExtra::create(ui::makeButton(xenia::tr("Set", "Atur"), 90.f, true, 26.f), this, menu_selector(XeniaMenu::onSpeedSet));
        set->setPosition({w - 12.f - 45.f, h / 2});
        menu->addChild(set);
        out.push_back(card);
    }

    out.push_back(makeModCard("CBF Bypass", w, 36.f, st.cbfBypass, 5, menu_selector(XeniaMenu::onCbf), nullptr));
    out.push_back(makeModCard("Particle Fixed", w, 36.f, st.particleFixed, 6, menu_selector(XeniaMenu::onParticle), nullptr));

    return out;
}

std::vector<CCNode*> XeniaMenu::buildUtils(float colW, float cardH) {
    auto& st = xenia::State::get();
    return {
        makeModCard("Straight Ship", colW, cardH, st.straightShip, 7, menu_selector(XeniaMenu::onStraightShip), menu_selector(XeniaMenu::onShipSettings)),
        makeModCard("Straight Wave", colW, cardH, st.straightWave, 8, menu_selector(XeniaMenu::onStraightWave), menu_selector(XeniaMenu::onWaveSettings)),
        makeModCard("Auto Clicker", colW, cardH, st.autoClick, 9, menu_selector(XeniaMenu::onAutoClick), menu_selector(XeniaMenu::onClickerSettings)),
        makeModCard("Wave Slide", colW, cardH, st.waveSlide, 10, menu_selector(XeniaMenu::onWaveSlide), menu_selector(XeniaMenu::onSlideSettings)),
    };
}

std::vector<CCNode*> XeniaMenu::buildMacroTable(float w) {

    std::vector<CCNode*> out;
    float h = 50.f;
    auto info = ui::makeCard(w, h);
    if (!xenia::bot::MacroManager::hasCurrent()) {
        auto l = ui::makeLabel(xenia::tr("Macro: none loaded", "Macro: belum ada makro dimuat"), w - 24.f, 0.42f);
        l->setAnchorPoint({0.f, 0.5f});
        l->setPosition({12.f, h / 2});
        info->addChild(l);
        out.push_back(info);
        return out;
    }
    auto& m = xenia::bot::MacroManager::current();
    int last = m.frames.empty() ? 0 : m.frames.back().frame;
    auto im = CCMenu::create();
    im->setPosition({0, 0});
    info->addChild(im, 5);
    auto ib = ui::makeSquareIconButton("info.png", 28.f, 16.f, this, menu_selector(XeniaMenu::onMacroInfo));
    ib->setPosition({w - 12.f - 14.f, h / 2});
    im->addChild(ib);
    std::string head = m.levelId != 0 ? fmt::format("Macro: {}  |  {} ({})", m.name, m.levelName, m.levelId) : fmt::format("Macro: {}", m.name);
    auto l1 = ui::makeLabel(head.c_str(), w - 64.f, 0.5f);
    l1->setAnchorPoint({0.f, 0.5f});
    l1->setPosition({12.f, h - 15.f});
    info->addChild(l1);
    auto l2 = ui::makeLabel(xenia::trf("{} inputs | TPS {} | last frame {} (~{:.1f} s)", "{} input | TPS {} | frame akhir {} (~{:.1f} dtk)",
        m.frames.size(), m.tps, last, m.tps > 0 ? last / m.tps : 0.0).c_str(), w - 64.f, 0.36f);
    l2->setAnchorPoint({0.f, 0.5f});
    l2->setPosition({12.f, 14.f});
    info->addChild(l2);
    out.push_back(info);
    return out;
}

std::vector<CCNode*> XeniaMenu::buildSettings(float w) {
    auto& st = xenia::State::get();
    std::vector<CCNode*> out;

    out.push_back(makeSliderRow(xenia::tr("UI Scale", "Skala UI"), w, 0.6f, 1.4f, st.uiScale, 2, [this](float v, int ph) {
        auto& s = xenia::State::get();
        s.uiScale = v;
        applyScale();
        if (ph != Live) s.save();
    }));
    out.push_back(makeSliderRow(xenia::tr("Opacity", "Opasitas"), w, 0.3f, 1.f, st.uiOpacity, 2, [this](float v, int ph) {
        auto& s = xenia::State::get();
        s.uiOpacity = v;
        ui::setSliceOpacity(m_panel, (GLubyte)std::round(v * 255.f));
        if (ph != Live) s.save();
    }));
    out.push_back(makeSliderRow(xenia::tr("Text Size", "Ukuran Teks"), w, 0.7f, 1.4f, st.textScale, 2, [this](float v, int ph) {
        auto& s = xenia::State::get();
        s.textScale = v;
        if (ph == Live) return;
        s.save();
        scheduleRebuild(ph == Typed ? 1.2f : 0.02f);
    }));

    out.push_back(makeSliderRow(xenia::tr("Logo Size", "Ukuran Logo"), w, 14.f, 60.f, st.logoSize, 0, [](float v, int ph) {
        auto& s = xenia::State::get();
        s.logoSize = v;
        XeniaOverlay::refresh();
        if (ph != Live) s.save();
    }));
    out.push_back(makeSliderRow(xenia::tr("Clock Size", "Ukuran Jam"), w, xenia::State::kMinHud, xenia::State::kMaxHud, st.clockSize, 0, [](float v, int ph) {
        auto& s = xenia::State::get();
        s.clockSize = v;
        if (ph != Live) s.save();
    }));
    out.push_back(makeSliderRow(xenia::tr("Stepper Size", "Ukuran Stepper"), w, xenia::State::kMinHud, xenia::State::kMaxHud, st.stepSize, 0, [](float v, int ph) {
        auto& s = xenia::State::get();
        s.stepSize = v;
        if (ph != Live) s.save();
    }));

    {
        float h = 44.f;
        auto card = ui::makeCard(w, h);
        auto lbl = ui::makeLabel("Font", 70.f, 0.45f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({12.f, h / 2});
        card->addChild(lbl);
        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        float cx = w - 12.f - 70.f;
        auto prev = ui::makeIconButton("arrow_left.png", 26.f, this, menu_selector(XeniaMenu::onFontPrev));
        prev->setPosition({cx - 52.f, h / 2});
        menu->addChild(prev);
        auto next = ui::makeIconButton("arrow_right.png", 26.f, this, menu_selector(XeniaMenu::onFontNext));
        next->setPosition({cx + 52.f + 18.f, h / 2});
        menu->addChild(next);
        auto nm = ui::makeLabel(ui::fontName(st.fontIdx), 74.f, 0.5f);
        nm->setPosition({cx + 9.f, h / 2});
        card->addChild(nm);
        out.push_back(card);
    }

    {
        float h = 70.f;
        auto card = ui::makeCard(w, h);
        auto lbl = ui::makeLabel(xenia::tr("Theme", "Tema"), w - 24.f, 0.45f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({12.f, h - 16.f});
        card->addChild(lbl);
        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        auto& all = xenia::themes();
        int n = (int)all.size();
        float bw = (w - 24.f - 6.f * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            auto item = CCMenuItemSpriteExtra::create(ui::makeButton(all[i].name, bw, st.theme == i), this, menu_selector(XeniaMenu::onTheme));
            item->setTag(i);
            item->setPosition({12.f + bw / 2 + i * (bw + 6.f), 20.f});
            menu->addChild(item);
        }
        out.push_back(card);
    }

    {
        float h = 70.f;
        auto card = ui::makeCard(w, h);
        auto lbl = ui::makeLabel(xenia::tr("UI Mode", "Mode UI"), w - 24.f, 0.45f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({12.f, h - 16.f});
        card->addChild(lbl);
        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        char const* names[] = {"Table", "Panel"};
        float bw = (w - 24.f - 6.f) / 2;
        for (int i = 0; i < 2; i++) {
            auto item = CCMenuItemSpriteExtra::create(ui::makeButton(names[i], bw, st.uiMode == i), this, menu_selector(XeniaMenu::onUiMode));
            item->setTag(i);
            item->setPosition({12.f + bw / 2 + i * (bw + 6.f), 20.f});
            menu->addChild(item);
        }
        out.push_back(card);
    }

    {
        float h = 70.f;
        auto card = ui::makeCard(w, h);
        auto lbl = ui::makeLabel(xenia::tr("Language", "Bahasa"), w - 24.f, 0.45f);
        lbl->setAnchorPoint({0.f, 0.5f});
        lbl->setPosition({12.f, h - 16.f});
        card->addChild(lbl);
        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        float bw = (w - 24.f - 6.f) / 2;
        for (int i = 0; i < xenia::kLangCount; i++) {
            auto item = CCMenuItemSpriteExtra::create(ui::makeButton(xenia::langName(i), bw, st.lang == i), this, menu_selector(XeniaMenu::onLang));
            item->setTag(i);
            item->setPosition({12.f + bw / 2 + i * (bw + 6.f), 20.f});
            menu->addChild(item);
        }
        out.push_back(card);
    }

    {
        float h = 40.f;
        auto card = ui::makeCard(w, h);
        auto menu = CCMenu::create();
        menu->setPosition({0, 0});
        card->addChild(menu);
        auto btn = CCMenuItemSpriteExtra::create(ui::makeButton("Reset UI", w - 24.f, false), this, menu_selector(XeniaMenu::onResetUI));
        btn->setPosition({w / 2, h / 2});
        menu->addChild(btn);
        out.push_back(card);
    }
    return out;
}

void XeniaMenu::scheduleRebuild(float delay) {
    this->unschedule(schedule_selector(XeniaMenu::doRebuild));
    this->scheduleOnce(schedule_selector(XeniaMenu::doRebuild), delay);
}

void XeniaMenu::doRebuild(float) {
    float y = m_scroll ? m_scroll->m_contentLayer->getPositionY() : 0.f;
    build();
    if (m_scroll) {
        float vh = m_scroll->getContentHeight();
        float ch = m_scroll->m_contentLayer->getContentHeight();
        float minY = std::min(0.f, vh - ch);
        m_scroll->m_contentLayer->setPositionY(std::clamp(y, minY, 0.f));
    }
}

void XeniaMenu::onTab(CCObject* s) {
    int t = std::clamp(static_cast<CCNode*>(s)->getTag(), 0, 3);
    if (t == m_tab && !m_tabPending) return;
    m_pendingTab = t;
    m_tabPending = true;
    this->unschedule(schedule_selector(XeniaMenu::doTab));
    this->scheduleOnce(schedule_selector(XeniaMenu::doTab), 0.f);
}

void XeniaMenu::doTab(float) {
    m_tabPending = false;
    if (m_pendingTab == m_tab) return;
    m_tab = m_pendingTab;
    s_lastTab = m_tab;
    buildTabBar();
    buildBody();
    m_coverApplied = false;
}

void XeniaMenu::onNoclip(CCObject*) {
    auto& st = xenia::State::get();
    st.noclip = !st.noclip;
    st.save();
}

void XeniaMenu::onSpeedhack(CCObject*) {
    auto& st = xenia::State::get();
    st.speedhack = !st.speedhack;
    st.save();
}

void XeniaMenu::onSafeMode(CCObject*) {
    auto& st = xenia::State::get();
    st.safeMode = !st.safeMode;
    st.save();
}

void XeniaMenu::onSpeedSettings(CCObject*) {
    XeniaSpeedPopup::open();
}

void XeniaMenu::onInfo(CCObject* s) {
    int tag = static_cast<CCNode*>(s)->getTag();
    if (tag == 2) {
        XeniaDialog::info("Safe Mode", xenia::tr("ON: noclip, speedhack, bot and utils are paused, so runs are clean and progress/leaderboards work normally.\nOFF: cheats are active, but a run that uses any cheat is NOT saved and does NOT reach the leaderboard.", "ON: noclip/speedhack/bot/utils dimatikan sementara, run bersih dan progress/leaderboard normal.\nOFF: hack aktif, tapi run yang memakai cheat TIDAK disimpan dan TIDAK masuk leaderboard."));
    } else if (tag == 0) {
        XeniaDialog::info("Noclip", xenia::tr("You do not die on collision. Only active while Safe Mode is OFF; the run is not saved.", "Tidak mati saat menabrak. Hanya aktif saat Safe Mode OFF; run-nya tidak disimpan."));
    } else if (tag == 4) {
        XeniaDialog::info("TPS Bypass", xenia::tr("Game physics runs at the TPS you set (default 240, from 1 up to unlimited), independent of the screen FPS. Configure it with the gear. During playback the TPS automatically follows the macro TPS. Only active while Safe Mode is OFF.", "Fisika game dihitung pada TPS yang kamu atur (default 240, 1 sampai tak terbatas), terpisah dari FPS layar. Atur lewat gear. Saat playback, TPS otomatis mengikuti TPS makro. Hanya aktif saat Safe Mode OFF."));
    } else if (tag == 5) {
        XeniaDialog::info("CBF Bypass", xenia::tr("While the bot is active, Click Between Frames is paused so bot inputs stay 100% accurate, then restored when you leave the level. No effect if CBF is not installed.", "Saat bot aktif, mod Click Between Frames dimatikan sementara supaya input bot tetap akurat 100%, lalu dikembalikan setelah keluar level. Tidak ada efek jika CBF tidak terpasang."));
    } else if (tag == 6) {
        XeniaDialog::info("Particle Fixed", xenia::tr("While the bot is active, leftover physics delta is dropped at the start of every attempt so frame timing is always identical and never drifts.", "Saat bot aktif, sisa delta fisika dibuang di setiap awal attempt sehingga timing frame selalu identik dan tidak melenceng."));
    } else if (tag == 7) {
        XeniaDialog::info("Straight Ship", xenia::tr("The ship is locked perfectly straight at the height it had when entering ship mode. Click effects still play even though the ship does not move, so it looks like it is clicking. Click rate and click effects are set with the gear. Only active while Safe Mode is OFF; the run is not saved.", "Ship terkunci lurus 100% di ketinggian saat masuk mode ship. Efek klik tetap jalan walau ship diam, jadi terlihat mengklik. Kecepatan klik dan efek klik diatur lewat gear. Hanya aktif saat Safe Mode OFF; run-nya tidak disimpan."));
    } else if (tag == 9) {
        XeniaDialog::info("Auto Clicker", xenia::tr("Automatic real clicks (recorded while in Record mode). Normal mode: Hold and Release are measured in ticks (0-1000), smaller is faster. Super mode: Hold and Release are measured in CPS (no upper limit), bigger is faster. Change the mode with the gear. Does not run during Playback; only active while Safe Mode is OFF.", "Klik asli otomatis (ikut terekam saat Record). Mode Normal: Hold dan Release dalam tick (0-1000), makin kecil makin cepat. Mode Super: Hold dan Release dalam CPS (tanpa batas atas), makin besar makin cepat. Ganti mode lewat gear. Tidak jalan saat Playback; hanya aktif saat Safe Mode OFF."));
    } else if (tag == 10) {
        XeniaDialog::info("Wave Slide", xenia::tr("The wave can slide along blocks without dying (no need to hold D manually): collisions with solid blocks are ignored and the position is held while it auto-clicks. Real clicks are recorded and counted in input/CPS, fake clicks are visual only. Configure it with the gear. Only active while Safe Mode is OFF.", "Wave bisa menyusuri block tanpa mati (tidak perlu menekan D manual): tabrakan dengan block solid diabaikan dan posisi ditahan, sambil mengklik otomatis. Klik asli ikut terekam dan masuk input/CPS, klik palsu hanya visual. Atur lewat gear. Hanya aktif saat Safe Mode OFF."));
    } else if (tag == 8) {
        XeniaDialog::info("Straight Wave", xenia::tr("The wave is locked perfectly straight (Y is frozen) at the height it had when entering wave mode. Click effects still play. Click rate and click effects are set with the gear. Only active while Safe Mode is OFF; the run is not saved.", "Wave terkunci lurus 100% (Y dikunci) seperti diam di ketinggian saat masuk mode wave. Efek klik tetap jalan. Kecepatan klik dan efek klik diatur lewat gear. Hanya aktif saat Safe Mode OFF; run-nya tidak disimpan."));
    } else if (tag == 3) {
        XeniaDialog::info("Stats HUD", xenia::tr("Shows info in the top-left corner while playing a level. Choose what is displayed (FPS, TPS, frame, input, CPS, position, etc.) with the gear.", "Menampilkan info di pojok kiri atas saat bermain level. Pilih yang ditampilkan (FPS, TPS, frame, input, CPS, posisi, dll) lewat gear."));
    } else {
        XeniaDialog::info("Speedhack", xenia::tr("Changes the game speed from 0.1x to 1000x (inside levels only, menus are not affected). A clock button appears to the left of the pause button in a level: tap it for slow-mo or speed-up, tap again for normal. Audio can follow the speed via the gear. Only active while Safe Mode is OFF.", "Ubah kecepatan game 0.1x - 1000x (hanya di dalam level, menu tidak ikut). Di level muncul tombol jam di kiri tombol pause: ketuk untuk slow-mo/speed, ketuk lagi untuk normal. Audio bisa ikut diatur lewat gear. Hanya aktif saat Safe Mode OFF."));
    }
}

void XeniaMenu::onBotMode(CCObject* s) {
    auto& st = xenia::State::get();
    auto mode = (xenia::BotMode)static_cast<CCNode*>(s)->getTag();
    if (mode != xenia::BotMode::Disable && !xenia::bot::MacroManager::hasCurrent()) {
        XeniaDialog::info("Bot", xenia::tr("No macro is loaded. Create one, or pick one and Load it first.", "Belum ada makro yang dimuat. Buat (Create) atau pilih lalu Load makro dulu."));
        return;
    }
    if (st.bot == xenia::BotMode::Record && mode != xenia::BotMode::Record) xenia::bot::flush();
    st.bot = mode;
    xenia::bot::syncCbf();
    xenia::bot::requestSync();
    xenia::bot::onModeChanged();
    if (mode != xenia::BotMode::Disable && st.safeMode) {
        XeniaDialog::info("Bot", xenia::tr("Safe Mode is still ON, so the bot is not running. Turn Safe Mode off in the Player tab.", "Safe Mode masih ON, jadi bot belum berjalan. Matikan Safe Mode di tab Player."));
    }
    scheduleRebuild(0.02f);
}

void XeniaMenu::onTps(CCObject*) {
    auto& st = xenia::State::get();
    st.tpsBypass = !st.tpsBypass;
    st.save();
}

void XeniaMenu::onTpsSettings(CCObject*) {
    XeniaTpsPopup::open();
}

void XeniaMenu::onCbf(CCObject*) {
    auto& st = xenia::State::get();
    st.cbfBypass = !st.cbfBypass;
    st.save();
    xenia::bot::syncCbf();
}

void XeniaMenu::onParticle(CCObject*) {
    auto& st = xenia::State::get();
    st.particleFixed = !st.particleFixed;
    st.save();
}

void XeniaMenu::onStats(CCObject*) {
    auto& st = xenia::State::get();
    st.showStats = !st.showStats;
    st.save();
}

void XeniaMenu::onMacroReplay(CCObject*) {
    Ref<XeniaMenu> self = this;
    XeniaMacroPicker::open("Replay", "OK", xenia::State::get().macroName, true, [self](std::string const& name) {
        auto& st = xenia::State::get();
        st.macroName = name;
        st.save();
        if (!name.empty()) {

            if (st.bot == xenia::BotMode::Record) xenia::bot::flush();
            if (auto res = xenia::bot::MacroManager::load(name); res.isErr()) {
                XeniaDialog::info("Replay", res.unwrapErr());
            }
            xenia::bot::requestSync();
        }
        self->scheduleRebuild(0.02f);
    });
}

void XeniaMenu::onMacroCreate(CCObject*) {
    Ref<XeniaMenu> self = this;
    XeniaDialog::input("Create Macro", xenia::tr("New macro name (NXR format)", "Nama makro baru (format NXR)"), "", [self](std::string const& name) -> std::string {
        if (auto res = xenia::bot::MacroManager::create(name); res.isErr()) {
            return res.unwrapErr();
        }
        auto& st = xenia::State::get();
        st.macroName = name;
        st.save();
        self->scheduleRebuild(0.02f);
        return "";
    });
}

void XeniaMenu::onMacroDelete(CCObject*) {
    Ref<XeniaMenu> self = this;
    XeniaMacroPicker::open("Delete Macro", xenia::tr("Delete", "Hapus"), xenia::State::get().macroName, false, [self](std::string const& name) {
        XeniaDialog::confirm("Delete Macro", xenia::trf("Delete macro \"{}\"? This cannot be undone.", "Hapus makro \"{}\"? Tidak bisa dibatalkan.", name), [self, name] {
            if (auto res = xenia::bot::MacroManager::remove(name); res.isErr()) {
                return XeniaDialog::info("Delete Macro", res.unwrapErr());
            }
            auto& st = xenia::State::get();
            if (st.macroName == name) st.macroName.clear();
            if (!xenia::bot::MacroManager::hasCurrent() && st.bot != xenia::BotMode::Disable) st.bot = xenia::BotMode::Disable;
            st.save();
            self->scheduleRebuild(0.02f);
        });
    });
}

void XeniaMenu::onMacroRename(CCObject*) {
    Ref<XeniaMenu> self = this;
    XeniaMacroPicker::open("Rename Macro", xenia::tr("Rename", "Ganti"), xenia::State::get().macroName, false, [self](std::string const& old) {
        XeniaDialog::input("Rename Macro", xenia::tr("New name", "Nama baru"), old, [self, old](std::string const& name) -> std::string {
            if (auto res = xenia::bot::MacroManager::rename(old, name); res.isErr()) {
                return res.unwrapErr();
            }
            auto& st = xenia::State::get();
            st.macroName = name;
            st.save();
            self->scheduleRebuild(0.02f);
            return "";
        });
    });
}

void XeniaMenu::onMacroLoad(CCObject*) {
    Ref<XeniaMenu> self = this;
    XeniaMacroPicker::open("Load Macro", xenia::tr("Load", "Muat"), xenia::State::get().macroName, false, [self](std::string const& name) {
        if (xenia::State::get().bot == xenia::BotMode::Record) xenia::bot::flush();
        if (auto res = xenia::bot::MacroManager::load(name); res.isErr()) {
            return XeniaDialog::info("Load Macro", res.unwrapErr());
        }
        auto& st = xenia::State::get();
        st.macroName = name;
        st.save();
        xenia::bot::requestSync();
        self->scheduleRebuild(0.02f);
    });
}

void XeniaMenu::onMacroInfo(CCObject*) {
    XeniaDialog::info("Info Macro", xenia::bot::MacroManager::info());
}

void XeniaMenu::onMacroSave(CCObject*) {
    if (!xenia::bot::MacroManager::hasCurrent()) {
        return XeniaDialog::info("Save Macro", xenia::tr("No macro loaded. Create or Load one first.", "Belum ada makro dimuat. Buat (Create) atau Load makro dulu."));
    }
    xenia::bot::commitIfIdle();
    auto res = xenia::bot::MacroManager::save();
    xenia::bot::markSaved();
    if (res.isErr()) {
        return XeniaDialog::info("Save Macro", res.unwrapErr());
    }
    auto& m = xenia::bot::MacroManager::current();
    XeniaDialog::info("Save Macro", xenia::trf("Saved: {}.nxr ({} inputs)", "Tersimpan: {}.nxr ({} input)", m.name, m.frames.size()));
    scheduleRebuild(0.02f);
}

void XeniaMenu::onSpeedSet(CCObject*) {
    Ref<XeniaMenu> self = this;
    XeniaSpeedPopup::open(true, [self] { self->scheduleRebuild(0.02f); });
}

void XeniaMenu::onStraightShip(CCObject*) {
    auto& st = xenia::State::get();
    st.straightShip = !st.straightShip;
    st.save();
}

void XeniaMenu::onStraightWave(CCObject*) {
    auto& st = xenia::State::get();
    st.straightWave = !st.straightWave;
    st.save();
}

void XeniaMenu::onAutoClick(CCObject*) {
    auto& st = xenia::State::get();
    st.autoClick = !st.autoClick;
    st.save();
}

void XeniaMenu::onClickerSettings(CCObject*) {
    XeniaClickerPopup::open();
}

void XeniaMenu::onWaveSlide(CCObject*) {
    auto& st = xenia::State::get();
    st.waveSlide = !st.waveSlide;
    st.save();
}

void XeniaMenu::onSlideSettings(CCObject*) {
    XeniaUtilPopup::open(2);
}

void XeniaMenu::onStatsSettings(CCObject*) {
    XeniaStatsPopup::open();
}

void XeniaMenu::onShipSettings(CCObject*) {
    XeniaUtilPopup::open(0);
}

void XeniaMenu::onWaveSettings(CCObject*) {
    XeniaUtilPopup::open(1);
}

void XeniaMenu::onTheme(CCObject* s) {
    auto& st = xenia::State::get();
    st.theme = static_cast<CCNode*>(s)->getTag();
    st.save();
    scheduleRebuild(0.02f);
}

void XeniaMenu::onUiMode(CCObject* s) {
    auto& st = xenia::State::get();
    int m = std::clamp(static_cast<CCNode*>(s)->getTag(), 0, 1);
    if (m == st.uiMode) return;
    st.uiMode = m;
    st.save();
    Loader::get()->queueInMainThread([] {
        XeniaMenu::toggle();
        XeniaMenu::open();
    });
}

void XeniaMenu::onLang(CCObject* s) {
    auto& st = xenia::State::get();
    int l = std::clamp(static_cast<CCNode*>(s)->getTag(), 0, xenia::kLangCount - 1);
    if (l == st.lang) return;
    st.lang = l;
    st.save();
    Loader::get()->queueInMainThread([] {
        XeniaMenu::toggle();
        XeniaMenu::open();
    });
}

void XeniaMenu::onFontPrev(CCObject*) {
    auto& st = xenia::State::get();
    st.fontIdx = (st.fontIdx + ui::fontCount() - 1) % ui::fontCount();
    st.save();
    scheduleRebuild(0.02f);
}

void XeniaMenu::onFontNext(CCObject*) {
    auto& st = xenia::State::get();
    st.fontIdx = (st.fontIdx + 1) % ui::fontCount();
    st.save();
    scheduleRebuild(0.02f);
}

void XeniaMenu::onResetUI(CCObject*) {
    auto& st = xenia::State::get();
    st.uiScale = 1.f; st.uiOpacity = 1.f; st.textScale = 1.f; st.logoSize = 24.f; st.fontIdx = 0; st.theme = xenia::ui::platform::kDefaultTheme;
    XeniaOverlay::refresh();
    st.save();
    applyScale();
    scheduleRebuild(0.02f);
}
