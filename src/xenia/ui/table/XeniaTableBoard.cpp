#include "xenia/core/Lang.hpp"
#include "xenia/ui/table/XeniaTableBoard.hpp"
#include "xenia/ui/table/XeniaTable.hpp"
#include "xenia/ui/menu/XeniaMenu.hpp"
#include "xenia/ui/overlay/XeniaOverlay.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/ui/Platform.hpp"
#include "xenia/ui/windows/XeniaWindowsKeys.hpp"
#include "xenia/bot/Macro.hpp"
#include "xenia/bot/Bot.hpp"
#include "xenia/core/State.hpp"
#include <algorithm>
#include <cmath>
#include <sstream>
#include <type_traits>
using namespace geode::prelude;
namespace ui = xenia::ui;

namespace {
    constexpr float COL_W = 124.f;
    constexpr float ROW_H = 17.f;
    constexpr float HEAD_H = 20.f;
    constexpr float GAP = 5.f;
    constexpr float MARGIN = 6.f;
    constexpr float STATUS_H = 18.f;
    constexpr float LABEL_SCALE = 0.36f;
    constexpr float INPUT_SCALE = 0.5f;
    constexpr float DRAG_SLOP = xenia::ui::platform::kDragSlop;
    constexpr int WIN_COUNT = 6;

    ccColor3B const ACCENT{232, 58, 107};
    ccColor3B const TXT{236, 236, 236};
    ccColor3B const DIM{140, 140, 140};
    ccColor3B const OKC{120, 230, 140};
    ccColor3B const BADC{255, 110, 110};
    ccColor3B const OFFBAR{78, 78, 78};
    ccColor4B const C_BG{43, 43, 43, 255};
    ccColor4B const C_INPUT{30, 30, 30, 255};
    ccColor4B const C_HEAD{232, 58, 107, 255};
    ccColor4B const C_BAR{26, 26, 26, 245};
    ccColor4B const C_SCROLL{232, 58, 107, 150};

    enum WinId { WPlayer = 0, WBot = 1, WMacro = 2, WUtils = 3, WStats = 4, WSettings = 5 };
    enum GearId { GSpeed = 10, GTps = 11, GShip = 12, GWave = 13, GClicker = 14, GSlide = 15, GIndicator = 16 };

    std::set<int> s_open;
    float s_scrollX = 0.f, s_scrollY = 0.f;

    using Cell = XeniaTableBoard::Cell;
    using Row = XeniaTableBoard::Row;

    bool parseNum(std::string const& s, double& out) {
        if (s.empty()) return false;
        char* end = nullptr;
        double v = std::strtod(s.c_str(), &end);
        if (end == s.c_str() || !std::isfinite(v)) return false;
        out = v;
        return true;
    }

    void fitLabel(CCLabelBMFont* l, float maxW, float scale) {
        l->limitLabelWidth(maxW, scale * ui::fontNorm() * xenia::State::get().textScale, 0.12f);
    }

    Cell mkToggle(std::string t, std::function<bool()> on, std::function<void()> tap, std::string hint = {}, bool center = false) {
        Cell c;
        c.kind = Cell::Toggle;
        c.text = std::move(t);
        c.on = std::move(on);
        c.tap = std::move(tap);
        c.hint = std::move(hint);
        c.center = center;
        return c;
    }

    Cell mkText(std::string t, bool dim = true, float w = 1.f, float indent = 0.f) {
        Cell c;
        c.kind = Cell::Text;
        c.text = std::move(t);
        c.dim = dim;
        c.weight = w;
        c.indent = indent;
        return c;
    }

    Cell mkInput(std::string init, std::string filter, int maxChars, std::function<void(std::string const&)> change, float w = 0.9f) {
        Cell c;
        c.kind = Cell::Input;
        c.initial = std::move(init);
        c.filter = std::move(filter);
        c.maxChars = maxChars;
        c.change = std::move(change);
        c.weight = w;
        return c;
    }

    Row mkRow(Cell c, int gear = -1, bool marker = true) {
        Row r;
        r.cells.push_back(std::move(c));
        r.gear = gear;
        r.marker = marker;
        return r;
    }

    Row mkMulti(std::vector<Cell> cells) {
        Row r;
        r.cells = std::move(cells);
        return r;
    }

    template <class T>
    Row numRow(std::string label, T& ref, double lo, double hi, int maxChars, std::function<void()> after = {}) {
        Row r;
        r.cells.push_back(mkText(std::move(label), true, 1.f, 8.f));
        r.cells.push_back(mkInput(fmt::format("{}", ref), "0123456789.", maxChars, [&ref, lo, hi, after](std::string const& s) {
            double v;
            if (!parseNum(s, v) || v < lo || v > hi) return;
            if constexpr (std::is_integral_v<T>) ref = (T)std::llround(v);
            else ref = (T)v;
            xenia::State::get().save();
            if (after) after();
        }));
        return r;
    }
}

XeniaTableBoard* XeniaTableBoard::create(CCSize view) {
    auto ret = new XeniaTableBoard();
    if (ret && ret->init(view)) {
        ret->autorelease();
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

bool XeniaTableBoard::init(CCSize view) {
    if (!CCLayer::init()) return false;
    m_view = view;
    this->setContentSize(view);
    this->setTouchEnabled(true);
#ifdef GEODE_IS_DESKTOP
    this->setMouseEnabled(true);
#endif
    m_hintColor = DIM;
    m_hintText = xenia::tr("Tap: select  |  Drag: scroll  |  Drag title: move window", "Ketuk: pilih  |  Geser: gerak  |  Seret judul: pindah window");

    m_content = CCNode::create();
    m_content->setAnchorPoint({0.f, 0.f});
    m_content->ignoreAnchorPointForPosition(false);
    this->addChild(m_content, 1);

    m_status = CCLayerColor::create(C_BAR, view.width, STATUS_H);
    this->addChild(m_status, 20);
    auto line = CCLayerColor::create(C_HEAD, view.width, 1.f);
    line->setPosition({0.f, STATUS_H - 1.f});
    m_status->addChild(line);
    m_hintLbl = ui::makeLabel(m_hintText.c_str(), view.width - 90.f, 0.34f);
    m_hintLbl->setAnchorPoint({0.f, 0.5f});
    m_hintLbl->setPosition({8.f, STATUS_H / 2 - 0.5f});
    m_hintLbl->setColor(m_hintColor);
    m_status->addChild(m_hintLbl);
    auto close = ui::makeLabel(xenia::tr("Close", "Tutup"), 50.f, 0.36f);
    close->setColor(ACCENT);
    close->setPosition({view.width - 28.f, STATUS_H / 2 - 0.5f});
    m_status->addChild(close);

    m_hBar = CCLayerColor::create(C_SCROLL, 30.f, 2.f);
    m_vBar = CCLayerColor::create(C_SCROLL, 2.f, 30.f);
    m_hBar->setVisible(false);
    m_vBar->setVisible(false);
    this->addChild(m_hBar, 15);
    this->addChild(m_vBar, 15);

    loadOrder();
    m_scrollX = s_scrollX;
    m_scrollY = s_scrollY;
    buildWindows();
    layoutWindows();
    drawWindows();
    clampScroll();
    applyScroll();
    return true;
}

void XeniaTableBoard::registerWithTouchDispatcher() {
    CCTouchDispatcher::get()->addTargetedDelegate(this, -999, true);
}

void XeniaTableBoard::loadOrder() {
    m_order.clear();
    auto& st = xenia::State::get();
    std::stringstream ss(st.tableOrder);
    std::string tok;
    while (std::getline(ss, tok, ',')) {
        double v;
        if (!parseNum(tok, v)) continue;
        int id = (int)v;
        if (id < 0 || id >= WIN_COUNT) continue;
        if (std::find(m_order.begin(), m_order.end(), id) != m_order.end()) continue;
        m_order.push_back(id);
    }
    for (int i = 0; i < WIN_COUNT; i++) {
        if (std::find(m_order.begin(), m_order.end(), i) == m_order.end()) m_order.push_back(i);
    }
}

void XeniaTableBoard::saveOrder() {
    std::string s;
    for (size_t i = 0; i < m_order.size(); i++) {
        if (i) s += ",";
        s += std::to_string(m_order[i]);
    }
    auto& st = xenia::State::get();
    st.tableOrder = s;
    st.save();
}

float XeniaTableBoard::colWidth() const { return COL_W; }

void XeniaTableBoard::setHint(std::string const& text, bool ok) {
    m_hintText = text;
    m_hintColor = ok ? (text.empty() ? DIM : OKC) : BADC;
    if (!m_hintLbl) return;
    m_hintLbl->setString(text.c_str());
    fitLabel(m_hintLbl, m_view.width - 90.f, 0.34f);
    m_hintLbl->setColor(m_hintColor);
}

void XeniaTableBoard::defocusAll() {
    for (auto in : m_inputs) {
        if (in) in->defocus();
    }
}

void XeniaTableBoard::refreshLive() {
    for (auto& [lbl, fn] : m_live) {
        if (lbl && fn) lbl->setColor(fn() ? ACCENT : TXT);
    }
    for (auto& [bar, fn] : m_liveBars) {
        if (bar && fn) bar->setColor(fn() ? ACCENT : OFFBAR);
    }
}

void XeniaTableBoard::scheduleRebuild(float delay) {
    this->unschedule(schedule_selector(XeniaTableBoard::doRebuild));
    this->scheduleOnce(schedule_selector(XeniaTableBoard::doRebuild), delay);
}

void XeniaTableBoard::doRebuild(float) {
    rebuild();
}

void XeniaTableBoard::rebuild() {
    buildWindows();
    layoutWindows();
    drawWindows();
    clampScroll();
    applyScroll();
}

void XeniaTableBoard::disarmDelete(float) {
    if (!m_delArm) return;
    m_delArm = false;
    scheduleRebuild(0.01f);
}

void XeniaTableBoard::setBotMode(int mode) {
    auto& st = xenia::State::get();
    auto m = (xenia::BotMode)std::clamp(mode, 0, 2);
    if (m != xenia::BotMode::Disable && !xenia::bot::MacroManager::hasCurrent()) {
        setHint(xenia::tr("No macro loaded. Create or pick one in the Macro window first.", "Belum ada makro dimuat. Buat atau pilih makro di window Macro dulu."), false);
        return;
    }
    if (st.bot == xenia::BotMode::Record && m != xenia::BotMode::Record) xenia::bot::flush();
    st.bot = m;
    xenia::bot::syncCbf();
    xenia::bot::requestSync();
    xenia::bot::onModeChanged();
    if (m != xenia::BotMode::Disable && st.safeMode) setHint(xenia::tr("Safe Mode is still ON, so the bot is not running. Turn it off in the Player window.", "Safe Mode masih ON, bot belum berjalan. Matikan Safe Mode di window Player."), false);
    else setHint("");
    scheduleRebuild();
}

void XeniaTableBoard::macroLoad(std::string const& name) {
    auto& st = xenia::State::get();
    if (st.bot == xenia::BotMode::Record) xenia::bot::flush();
    if (auto res = xenia::bot::MacroManager::load(name); res.isErr()) {
        return setHint(res.unwrapErr(), false);
    }
    st.macroName = name;
    st.save();
    m_macroInput = name;
    xenia::bot::requestSync();
    setHint(xenia::tr("Loaded: ", "Dimuat: ") + name);
    scheduleRebuild();
}

void XeniaTableBoard::macroCreate() {
    if (auto res = xenia::bot::MacroManager::create(m_macroInput); res.isErr()) {
        return setHint(res.unwrapErr(), false);
    }
    auto& st = xenia::State::get();
    st.macroName = m_macroInput;
    st.save();
    setHint(xenia::tr("Created: ", "Dibuat: ") + m_macroInput);
    scheduleRebuild();
}

void XeniaTableBoard::macroRename() {
    auto& st = xenia::State::get();
    if (st.macroName.empty()) return setHint(xenia::tr("Pick a macro from the list first.", "Pilih makro dulu dari daftar."), false);
    auto old = st.macroName;
    if (auto res = xenia::bot::MacroManager::rename(old, m_macroInput); res.isErr()) {
        return setHint(res.unwrapErr(), false);
    }
    st.macroName = m_macroInput;
    st.save();
    setHint(xenia::trf("Renamed: {} -> {}", "Ganti nama: {} -> {}", old, m_macroInput));
    scheduleRebuild();
}

void XeniaTableBoard::macroSave() {
    if (!xenia::bot::MacroManager::hasCurrent()) return setHint(xenia::tr("No macro loaded. Create or pick one first.", "Belum ada makro dimuat. Buat atau pilih dulu."), false);
    xenia::bot::commitIfIdle();
    auto res = xenia::bot::MacroManager::save();
    xenia::bot::markSaved();
    if (res.isErr()) return setHint(res.unwrapErr(), false);
    auto& m = xenia::bot::MacroManager::current();
    setHint(xenia::trf("Saved: {}.nxr ({} inputs)", "Tersimpan: {}.nxr ({} input)", m.name, m.frames.size()));
    scheduleRebuild();
}

void XeniaTableBoard::macroDelete() {
    auto& st = xenia::State::get();
    if (st.macroName.empty()) return setHint(xenia::tr("Pick a macro from the list first.", "Pilih makro dulu dari daftar."), false);
    if (!m_delArm) {
        m_delArm = true;
        setHint(xenia::trf("Tap Sure? again to delete \"{}\".", "Ketuk Yakin? sekali lagi untuk menghapus \"{}\".", st.macroName), false);
        this->unschedule(schedule_selector(XeniaTableBoard::disarmDelete));
        this->scheduleOnce(schedule_selector(XeniaTableBoard::disarmDelete), 3.f);
        scheduleRebuild();
        return;
    }
    m_delArm = false;
    auto name = st.macroName;
    if (auto res = xenia::bot::MacroManager::remove(name); res.isErr()) {
        return setHint(res.unwrapErr(), false);
    }
    st.macroName.clear();
    if (!xenia::bot::MacroManager::hasCurrent() && st.bot != xenia::BotMode::Disable) st.bot = xenia::BotMode::Disable;
    st.save();
    m_macroInput.clear();
    setHint(xenia::tr("Deleted: ", "Dihapus: ") + name);
    scheduleRebuild();
}

void XeniaTableBoard::setUiMode(int mode) {
    auto& st = xenia::State::get();
    mode = std::clamp(mode, 0, 1);
    if (mode == st.uiMode) return;
    st.uiMode = mode;
    st.save();
    Loader::get()->queueInMainThread([] {
        XeniaTable::closeNow();
        XeniaMenu::open();
    });
}

void XeniaTableBoard::setLang(int lang) {
    auto& st = xenia::State::get();
    lang = std::clamp(lang, 0, xenia::kLangCount - 1);
    if (lang == st.lang) return;
    st.lang = lang;
    st.save();
    Loader::get()->queueInMainThread([] {
        XeniaTable::closeNow();
        XeniaMenu::open();
    });
}

void XeniaTableBoard::resetLayout() {
    auto& st = xenia::State::get();
    st.tableOrder.clear();
    st.tableCollapsed = 0;
    st.save();
    s_open.clear();
    s_scrollX = s_scrollY = 0.f;
    m_scrollX = m_scrollY = 0.f;
    loadOrder();
    setHint(xenia::tr("Layout reset", "Layout direset"));
    scheduleRebuild();
}

void XeniaTableBoard::resetUi() {
    auto& st = xenia::State::get();
    st.uiScale = 1.f;
    st.uiOpacity = 1.f;
    st.textScale = 1.f;
    st.logoSize = 24.f;
    st.fontIdx = 0;
    st.save();
    XeniaOverlay::refresh();
    setHint(xenia::tr("UI reset", "UI direset"));
    scheduleRebuild();
}

void XeniaTableBoard::buildWindows() {
    auto& st = xenia::State::get();
    m_scale = std::clamp(st.uiScale, 0.6f, 1.4f);
    m_wins.clear();
    m_wins.resize(WIN_COUNT);
    for (int i = 0; i < WIN_COUNT; i++) m_wins[i].id = i;

    auto boolRow = [this](std::string title, bool& ref, std::string hint, int gear = -1, std::function<void()> after = {}) {
        return mkRow(mkToggle(std::move(title), [&ref] { return ref; }, [this, &ref, after] {
            ref = !ref;
            xenia::State::get().save();
            if (after) after();
            refreshLive();
        }, std::move(hint)), gear);
    };
    auto subBool = [this](std::string title, bool& ref, std::string hint, std::function<void()> after = {}) {
        auto c = mkToggle(std::move(title), [&ref] { return ref; }, [this, &ref, after] {
            ref = !ref;
            xenia::State::get().save();
            if (after) after();
            refreshLive();
        }, std::move(hint));
        c.indent = 8.f;
        return mkRow(std::move(c), -1, true);
    };

    {
        auto& w = m_wins[WPlayer];
        w.title = "Player";
        w.rows.push_back(boolRow("Safe Mode", st.safeMode, xenia::tr("ON: cheats are paused and runs count normally. OFF: cheats are active, but the run is NOT saved and does NOT reach the leaderboard.", "ON: hack dimatikan sementara, run normal. OFF: hack aktif, tapi run TIDAK disimpan dan TIDAK masuk leaderboard.")));
        w.rows.push_back(boolRow("Noclip", st.noclip, xenia::tr("You do not die on collision. Only active while Safe Mode is OFF.", "Tidak mati saat menabrak. Aktif hanya saat Safe Mode OFF.")));
        w.rows.push_back(boolRow("Speedhack", st.speedhack, xenia::tr("Game speed from 0.1x to 1000x. A clock button shows up in levels. Needs Safe Mode OFF.", "Kecepatan game 0.1x-1000x. Tombol jam muncul di level. Butuh Safe Mode OFF."), GSpeed));
        if (s_open.count(GSpeed)) {
            w.rows.push_back(numRow("Speed", st.speed, xenia::State::kMinSpeed, xenia::State::kMaxSpeed, 8));
            w.rows.push_back(subBool("Speed Audio", st.speedAudio, xenia::tr("Audio pitch follows the speed.", "Pitch audio ikut kecepatan.")));
        }
        w.rows.push_back(boolRow("TPS Bypass", st.tpsBypass, xenia::tr("Physics runs at the configured TPS (default 240), independent of FPS. Playback automatically uses the macro TPS.", "Fisika dihitung pada TPS yang diatur (default 240), terpisah dari FPS. Saat playback otomatis ikut TPS makro."), GTps));
        if (s_open.count(GTps)) {
            w.rows.push_back(numRow("TPS", st.tps, xenia::State::kMinTps, xenia::State::kMaxTps, 13));
        }
    }

    {
        auto& w = m_wins[WBot];
        w.title = "Bot";
        std::vector<Cell> modes;
        char const* names[] = {"Disable", "Record", "Playback"};
        for (int i = 0; i < 3; i++) {
            auto c = mkToggle(names[i], [i] { return (int)xenia::State::get().bot == i; }, [this, i] { setBotMode(i); },
                xenia::tr("Bot mode. Record and Playback need a loaded macro and Safe Mode OFF.", "Mode bot. Record/Playback butuh makro dimuat dan Safe Mode OFF."), true);
            modes.push_back(std::move(c));
        }
        w.rows.push_back(mkMulti(std::move(modes)));
        w.rows.push_back(mkMulti({mkText("Macro", true, 0.8f, 5.f), mkText(st.macroName.empty() ? xenia::tr("- none -", "- kosong -") : st.macroName, false, 1.4f)}));
        w.rows.push_back(numRow("Bot Speed", st.botSpeed, xenia::State::kMinSpeed, xenia::State::kMaxSpeed, 8));
        w.rows.push_back(boolRow("CBF Bypass", st.cbfBypass, xenia::tr("While the bot is active, Click Between Frames is paused so bot inputs stay accurate.", "Saat bot aktif, mod Click Between Frames dimatikan sementara agar input bot akurat."), -1, [] { xenia::bot::syncCbf(); }));
        w.rows.push_back(boolRow("Particle Fixed", st.particleFixed, xenia::tr("While the bot is active, leftover physics delta is dropped on every attempt so frame timing stays identical.", "Saat bot aktif, sisa delta fisika dibuang tiap attempt agar timing frame identik.")));
        w.rows.push_back(boolRow("Click Indicator", st.clickInd, xenia::tr("Shows the clicks from the loaded .nxr on the left while the bot is playing. Tap the arrow for options.", "Tampilkan klik dari .nxr di sisi kiri saat bot Playback. Ketuk panah untuk pengaturan."), GIndicator));
        if (s_open.count(GIndicator)) {
            w.rows.push_back(mkMulti({
                mkToggle("Line", [] { return xenia::State::get().ciMode == 0; }, [this] {
                    xenia::State::get().ciMode = 0; xenia::State::get().save(); refreshLive();
                }, xenia::tr("Fixed hit line at the bottom; clicks fall onto it.", "Garis hit tetap di bawah; klik turun ke garis."), true),
                mkToggle("Follow Zone", [] { return xenia::State::get().ciMode == 1; }, [this] {
                    xenia::State::get().ciMode = 1; xenia::State::get().save(); refreshLive();
                }, xenia::tr("A zone box that follows the next click down to the hit.", "Kotak zone yang mengikuti klik berikutnya sampai kena."), true),
            }));
            w.rows.push_back(subBool(xenia::tr("Perfect Text", "Teks Perfect"), st.ciPerfectOn, xenia::tr("Show a green Perfect on every hit.", "Tampilkan Perfect hijau tiap kena.")));
            w.rows.push_back(numRow("Note R", st.ciNote[0], 0, 255, 3));
            w.rows.push_back(numRow("Note G", st.ciNote[1], 0, 255, 3));
            w.rows.push_back(numRow("Note B", st.ciNote[2], 0, 255, 3));
            w.rows.push_back(numRow("Line R", st.ciLine[0], 0, 255, 3));
            w.rows.push_back(numRow("Line G", st.ciLine[1], 0, 255, 3));
            w.rows.push_back(numRow("Line B", st.ciLine[2], 0, 255, 3));
            w.rows.push_back(numRow("Perfect R", st.ciPerfect[0], 0, 255, 3));
            w.rows.push_back(numRow("Perfect G", st.ciPerfect[1], 0, 255, 3));
            w.rows.push_back(numRow("Perfect B", st.ciPerfect[2], 0, 255, 3));
            w.rows.push_back(numRow("Pos X", st.ciX, 0.0, 1000.0, 5));
            w.rows.push_back(numRow("Pos Y", st.ciY, 0.0, 1000.0, 5));
            w.rows.push_back(numRow(xenia::tr("Length", "Panjang"), st.ciLen, 40.0, 400.0, 5));
            w.rows.push_back(numRow(xenia::tr("Width", "Lebar"), st.ciW, 4.0, 80.0, 5));
            w.rows.push_back(numRow(xenia::tr("Ahead (s)", "Awal (dtk)"), st.ciAhead, 0.2, 5.0, 4));
            w.rows.push_back(numRow(xenia::tr("Opacity", "Opasitas"), st.ciOpacity, 0.1, 1.0, 4));
            w.rows.push_back(numRow(xenia::tr("Text size", "Ukuran teks"), st.ciTextSize, 0.2, 1.2, 4));
        }
    }

    {
        auto& w = m_wins[WMacro];
        w.title = "Macro";
        if (xenia::bot::MacroManager::hasCurrent()) {
            auto& m = xenia::bot::MacroManager::current();
            int last = m.frames.empty() ? 0 : m.frames.back().frame;
            w.rows.push_back(mkRow(mkText(m.levelId != 0 ? fmt::format("{} | {}", m.name, m.levelName) : m.name, false, 1.f, 5.f), -1, false));
            w.rows.push_back(mkRow(mkText(xenia::trf("{} inputs | TPS {}", "{} input | TPS {}", m.frames.size(), m.tps), true, 1.f, 5.f), -1, false));
            w.rows.push_back(mkRow(mkText(xenia::trf("frame {} (~{:.1f} s)", "frame {} (~{:.1f} dtk)", last, m.tps > 0 ? last / m.tps : 0.0), true, 1.f, 5.f), -1, false));
        } else {
            w.rows.push_back(mkRow(mkText(xenia::tr("no macro loaded", "belum ada makro dimuat"), true, 1.f, 5.f), -1, false));
        }
        auto names = xenia::bot::MacroManager::list();
        for (auto const& n : names) {
            auto c = mkToggle(n, [n] { return xenia::State::get().macroName == n; }, [this, n] { macroLoad(n); }, xenia::tr("Tap to load this macro.", "Ketuk untuk memuat makro ini."));
            w.rows.push_back(mkRow(std::move(c)));
        }
        if (names.empty()) w.rows.push_back(mkRow(mkText(xenia::tr("(macro folder is empty)", "(folder makro kosong)"), true, 1.f, 5.f), -1, false));
        w.rows.push_back(mkMulti({mkText(xenia::tr("Name", "Nama"), true, 0.7f, 5.f), mkInput(m_macroInput, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-", 32,
            [this](std::string const& s) { m_macroInput = s; }, 1.3f)}));
        w.rows.push_back(mkMulti({
            mkToggle(xenia::tr("Create", "Buat"), nullptr, [this] { macroCreate(); }, xenia::tr("Create a new macro using the name in the Name field.", "Buat makro baru dengan nama di kolom Name."), true),
            mkToggle(xenia::tr("Rename", "Ganti"), nullptr, [this] { macroRename(); }, xenia::tr("Rename the selected macro to the name in the Name field.", "Ganti nama makro terpilih dengan nama di kolom Name."), true),
        }));
        w.rows.push_back(mkMulti({
            mkToggle(xenia::tr("Save", "Simpan"), nullptr, [this] { macroSave(); }, xenia::tr("Save the current macro to its .nxr file.", "Simpan makro saat ini ke file .nxr."), true),
            mkToggle(m_delArm ? xenia::tr("Sure?", "Yakin?") : xenia::tr("Delete", "Hapus"), [this] { return m_delArm; }, [this] { macroDelete(); }, xenia::tr("Delete the selected macro (tap twice).", "Hapus makro terpilih (ketuk dua kali)."), true),
        }));
    }

    {
        auto& w = m_wins[WUtils];
        w.title = "Utils";
        w.rows.push_back(boolRow("Straight Ship", st.straightShip, xenia::tr("Locks the ship perfectly straight in ship mode. Click effects still play. Needs Safe Mode OFF.", "Ship terkunci lurus 100% saat mode ship. Efek klik tetap jalan. Butuh Safe Mode OFF."), GShip));
        if (s_open.count(GShip)) {
            w.rows.push_back(subBool(xenia::tr("Click Effect", "Efek Klik"), st.shipClick, xenia::tr("Show click effects.", "Tampilkan efek klik.")));
            w.rows.push_back(numRow("CPS", st.shipCps, xenia::State::kMinCps, xenia::State::kMaxCps, 6));
        }
        w.rows.push_back(boolRow("Straight Wave", st.straightWave, xenia::tr("Locks the wave perfectly straight (Y is frozen). Click effects still play. Needs Safe Mode OFF.", "Wave terkunci lurus 100% (Y dikunci). Efek klik tetap jalan. Butuh Safe Mode OFF."), GWave));
        if (s_open.count(GWave)) {
            w.rows.push_back(subBool(xenia::tr("Click Effect", "Efek Klik"), st.waveClick, xenia::tr("Show click effects.", "Tampilkan efek klik.")));
            w.rows.push_back(numRow("CPS", st.waveCps, xenia::State::kMinCps, xenia::State::kMaxCps, 6));
        }
        w.rows.push_back(boolRow("Auto Clicker", st.autoClick, xenia::tr("Automatic real clicks (recorded too). Normal: ticks, smaller is faster. Super: CPS, bigger is faster. Does not run during Playback.", "Klik asli otomatis (ikut terekam). Normal: tick, kecil = cepat. Super: CPS, besar = cepat. Tidak jalan saat Playback."), GClicker));
        if (s_open.count(GClicker)) {
            w.rows.push_back(mkMulti({
                mkToggle("Normal", [] { return xenia::State::get().acMode == 0; }, [this] {
                    xenia::State::get().acMode = 0; xenia::State::get().save(); scheduleRebuild();
                }, xenia::tr("Tick mode (0-1000).", "Mode tick (0-1000)."), true),
                mkToggle("Super", [] { return xenia::State::get().acMode == 1; }, [this] {
                    xenia::State::get().acMode = 1; xenia::State::get().save(); scheduleRebuild();
                }, xenia::tr("CPS mode with no upper limit.", "Mode CPS tanpa batas atas."), true),
            }));
            if (st.acMode == 0) {
                w.rows.push_back(numRow("Hold tick", st.acHold, 0, xenia::State::kMaxTicks, 4));
                w.rows.push_back(numRow("Release tick", st.acRelease, 0, xenia::State::kMaxTicks, 4));
            } else {
                w.rows.push_back(numRow("Hold CPS", st.acHoldCps, xenia::State::kMinRate, 1e12, 14));
                w.rows.push_back(numRow("Release CPS", st.acReleaseCps, xenia::State::kMinRate, 1e12, 14));
            }
        }
        w.rows.push_back(boolRow("Wave Slide", st.waveSlide, xenia::tr("The wave slides along blocks without dying while auto-clicking. Needs Safe Mode OFF.", "Wave menyusuri block tanpa mati sambil klik otomatis. Butuh Safe Mode OFF."), GSlide));
        if (s_open.count(GSlide)) {
            w.rows.push_back(subBool(xenia::tr("Real Click", "Klik Asli"), st.slideReal, xenia::tr("ON: real clicks (recorded). OFF: fake clicks (visual only).", "ON: klik asli (terekam). OFF: klik palsu (visual).")));
            w.rows.push_back(numRow("CPS", st.slideCps, xenia::State::kMinCps, xenia::State::kMaxCps, 6));
        }
    }

    {
        auto& w = m_wins[WStats];
        w.title = "Stats";
        w.rows.push_back(boolRow("Stats HUD", st.showStats, xenia::tr("Show info in the top-left corner while playing a level.", "Tampilkan info di pojok kiri atas saat bermain level.")));
        char const* kNames[xenia::kStatCount] = {
            "FPS", "TPS", "Frame", "Input P1/P2", "CPS",
            xenia::tr("Total clicks", "Total klik"), xenia::tr("Position X/Y", "Posisi X/Y"),
            xenia::tr("Y velocity", "Kecepatan Y"), xenia::tr("Vehicle", "Kendaraan"),
            xenia::tr("Bot status", "Status bot")
        };
        for (int i = 0; i < xenia::kStatCount; i++) {
            auto c = mkToggle(kNames[i], [i] { return ((xenia::State::get().statsMask >> i) & 1) != 0; }, [this, i] {
                auto& s = xenia::State::get();
                s.statsMask ^= (1 << i);
                s.save();
                refreshLive();
            }, xenia::tr("Choose what the Stats HUD shows.", "Pilih info yang tampil di Stats HUD."));
            c.indent = 8.f;
            w.rows.push_back(mkRow(std::move(c)));
        }
    }

    {
        auto& w = m_wins[WSettings];
        w.title = xenia::tr("Settings", "Pengaturan");
        w.rows.push_back(mkMulti({
            mkToggle("Table", [] { return xenia::State::get().uiMode == 0; }, [this] { setUiMode(0); }, xenia::tr("Table layout (default on Android and Windows).", "Mode tampilan table (default di Android dan Windows)."), true),
            mkToggle("Panel", [] { return xenia::State::get().uiMode == 1; }, [this] { setUiMode(1); }, xenia::tr("Panel layout with tabs.", "Mode tampilan panel dengan tab."), true),
        }));
        w.rows.push_back(mkMulti({
            mkToggle("English", [] { return xenia::State::get().lang == xenia::LangEn; }, [this] { setLang(xenia::LangEn); },
                     xenia::tr("Interface language: English.", "Bahasa antarmuka: English."), true),
            mkToggle("Indonesia", [] { return xenia::State::get().lang == xenia::LangIndo; }, [this] { setLang(xenia::LangIndo); },
                     xenia::tr("Interface language: Indonesian.", "Bahasa antarmuka: Bahasa Indonesia."), true),
        }));
        w.rows.push_back(numRow(xenia::tr("Scale", "Skala"), st.uiScale, 0.6, 1.4, 5, [this] { scheduleRebuild(1.2f); }));
        w.rows.push_back(numRow(xenia::tr("Opacity", "Opasitas"), st.uiOpacity, 0.3, 1.0, 5, [this] { scheduleRebuild(1.2f); }));
        w.rows.push_back(numRow(xenia::tr("Text", "Teks"), st.textScale, 0.7, 1.4, 5, [this] { scheduleRebuild(1.2f); }));
        w.rows.push_back(numRow("Logo", st.logoSize, 14.0, 60.0, 4, [] { XeniaOverlay::refresh(); }));
        w.rows.push_back(numRow(xenia::tr("Clock", "Jam"), st.clockSize, xenia::State::kMinHud, xenia::State::kMaxHud, 4));
        w.rows.push_back(numRow("Stepper", st.stepSize, xenia::State::kMinHud, xenia::State::kMaxHud, 4));
        w.rows.push_back(mkRow(mkToggle(fmt::format("Font: {}", ui::fontName(st.fontIdx)), nullptr, [this] {
            auto& s = xenia::State::get();
            s.fontIdx = (s.fontIdx + 1) % ui::fontCount();
            s.save();
            scheduleRebuild();
        }, xenia::tr("Change the font.", "Ganti font."), true), -1, false));
        w.rows.push_back(mkMulti({
            mkToggle("Reset UI", nullptr, [this] { resetUi(); }, xenia::tr("Restore scale, opacity, text, logo and font.", "Kembalikan scale, opacity, text, logo, font."), true),
            mkToggle("Reset Layout", nullptr, [this] { resetLayout(); }, xenia::tr("Restore window order and expand everything.", "Kembalikan urutan window dan buka semua."), true),
        }));
    }
}

void XeniaTableBoard::layoutWindows() {
    m_layout.clear();
    auto& st = xenia::State::get();
    float avail = (m_view.height - STATUS_H) / m_scale - MARGIN * 2.f;

    struct Col { float h = 0.f; };
    std::vector<Col> cols;
    struct Place { int id; int col; float top; float h; int rows; };
    std::vector<Place> places;

    for (int id : m_order) {
        auto& w = m_wins[id];
        bool collapsed = (st.tableCollapsed >> id) & 1;
        int rows = collapsed ? 0 : (int)w.rows.size();
        float h = HEAD_H + rows * ROW_H;
        int pick = -1;
        for (size_t c = 0; c < cols.size(); c++) {
            float need = cols[c].h + (cols[c].h > 0 ? GAP : 0.f) + h;
            if (need <= avail) { pick = (int)c; break; }
        }
        if (pick < 0) {
            cols.push_back({});
            pick = (int)cols.size() - 1;
        }
        float top = cols[pick].h + (cols[pick].h > 0 ? GAP : 0.f);
        cols[pick].h = top + h;
        places.push_back({id, pick, top, h, rows});
    }

    float maxH = 0.f;
    for (auto& c : cols) maxH = std::max(maxH, c.h);
    m_contentH = maxH + MARGIN * 2.f;
    m_contentW = cols.size() * (COL_W + GAP) - GAP + MARGIN * 2.f;
    m_contentH = std::max(m_contentH, 10.f);

    for (auto& p : places) {
        float x = MARGIN + p.col * (COL_W + GAP);
        float y = m_contentH - MARGIN - p.top - p.h;
        WinLayout wl;
        wl.id = p.id;
        wl.box = {x, y, COL_W, p.h};
        wl.head = {x, y + p.h - HEAD_H, COL_W, HEAD_H};
        wl.rowCount = p.rows;
        m_layout.push_back(wl);
    }
}

void XeniaTableBoard::drawWindows() {
    m_content->removeAllChildren();
    m_hits.clear();
    m_inputs.clear();
    m_inputBoxes.clear();
    m_live.clear();
    m_liveBars.clear();
    m_press = nullptr;

    auto& st = xenia::State::get();
    GLubyte bgA = (GLubyte)std::round(std::clamp(st.uiOpacity, 0.3f, 1.f) * 255.f);
    m_content->setContentSize({m_contentW, m_contentH});
    m_content->setScale(m_scale);

    for (auto& wl : m_layout) {
        auto& win = m_wins[wl.id];
        float w = wl.box.w, h = wl.box.h;
        bool collapsed = (st.tableCollapsed >> wl.id) & 1;

        auto node = CCNode::create();
        node->setTag(100 + wl.id);
        node->setContentSize({w, h});
        node->setPosition({wl.box.x, wl.box.y});
        m_content->addChild(node, 1);

        if (!collapsed && wl.rowCount > 0) {
            auto body = CCLayerColor::create({C_BG.r, C_BG.g, C_BG.b, bgA}, w, h - HEAD_H);
            node->addChild(body, 0);
        }
        auto head = CCLayerColor::create(C_HEAD, w, HEAD_H);
        head->setPosition({0.f, h - HEAD_H});
        node->addChild(head, 2);

        auto mBar = CCLayerColor::create({255, 255, 255, 255}, 8.f, 1.6f);
        mBar->setPosition({6.f, h - HEAD_H / 2 - 0.8f});
        node->addChild(mBar, 3);
        if (collapsed) {
            auto vBar = CCLayerColor::create({255, 255, 255, 255}, 1.6f, 8.f);
            vBar->setPosition({6.f + 3.2f, h - HEAD_H / 2 - 4.f});
            node->addChild(vBar, 3);
        }
        auto title = ui::makeLabel(win.title.c_str(), w - 44.f, 0.42f);
        title->setPosition({w / 2, h - HEAD_H / 2 - 0.5f});
        node->addChild(title, 3);

        if (collapsed) continue;

        for (size_t r = 0; r < win.rows.size(); r++) {
            auto& row = win.rows[r];
            float ry = h - HEAD_H - (r + 1) * ROW_H;
            float usable = w - (row.gear >= 0 ? 16.f : 0.f);
            float totalW = 0.f;
            for (auto& c : row.cells) totalW += c.weight;
            float cx = 0.f;

            for (size_t ci = 0; ci < row.cells.size(); ci++) {
                auto& c = row.cells[ci];
                float cw = usable * c.weight / std::max(0.01f, totalW);
                float maxW = std::max(10.f, cw - 10.f - c.indent);

                if (ci > 0) {
                    auto sep = CCLayerColor::create(C_BAR, 1.f, ROW_H - 4.f);
                    sep->setPosition({cx, ry + 2.f});
                    node->addChild(sep, 2);
                }

                if (c.kind == Cell::Input) {
                    auto box = CCLayerColor::create(C_INPUT, cw - 6.f, ROW_H - 2.f);
                    box->setPosition({cx + 3.f, ry + 1.f});
                    node->addChild(box, 2);
                    m_inputBoxes.push_back(box);

                    auto in = TextInput::create((cw - 8.f) / INPUT_SCALE, "", ui::font());
                    if (!c.filter.empty()) in->setFilter(c.filter);
                    in->setMaxCharCount(c.maxChars);
                    in->setString(c.initial, false);
                    if (auto bg = in->getBGSprite()) bg->setVisible(false);
                    in->setScale(INPUT_SCALE);
                    in->setPosition({cx + cw / 2, ry + ROW_H / 2});
                    if (c.change) in->setCallback(c.change);
                    node->addChild(in, 4);
                    m_inputs.push_back(in);
                } else {
                    auto lbl = ui::makeLabel(c.text.c_str(), maxW, LABEL_SCALE);
                    bool center = c.center || row.cells.size() > 1;
                    if (c.kind == Cell::Text && !c.center && row.cells.size() > 1) center = false;
                    if (center) {
                        lbl->setPosition({cx + cw / 2, ry + ROW_H / 2 - 0.5f});
                    } else {
                        lbl->setAnchorPoint({0.f, 0.5f});
                        lbl->setPosition({cx + 6.f + c.indent, ry + ROW_H / 2 - 0.5f});
                    }
                    if (c.kind == Cell::Text) {
                        lbl->setColor(c.dim ? DIM : TXT);
                    } else {
                        bool on = c.on ? c.on() : false;
                        lbl->setColor(on ? ACCENT : TXT);
                        if (c.on) m_live.push_back({lbl, c.on});
                    }
                    node->addChild(lbl, 4);

                    if (c.kind == Cell::Toggle) {
                        m_hits.push_back({wl.id, (int)r, (int)ci, {wl.box.x + cx, wl.box.y + ry, cw, ROW_H}});
                    }
                }
                cx += cw;
            }

            bool single = row.cells.size() == 1 && row.cells[0].kind == Cell::Toggle && row.cells[0].on && row.marker;
            if (single) {
                auto bar = CCLayerColor::create({OFFBAR.r, OFFBAR.g, OFFBAR.b, 255}, 2.f, ROW_H - 4.f);
                bar->setPosition({w - 3.f, ry + 2.f});
                node->addChild(bar, 2);
                bool on = row.cells[0].on();
                bar->setColor(on ? ACCENT : OFFBAR);
                m_liveBars.push_back({bar, row.cells[0].on});
            }

            if (row.gear >= 0) {
                bool open = s_open.count(row.gear) > 0;
                auto tri = CCDrawNode::create();
                CCPoint pts[3] = {{w - 15.f, ry + 3.f}, {w - 6.f, ry + 3.f}, {w - 6.f, ry + 12.f}};
                ccColor4F col = open ? ccColor4F{ACCENT.r / 255.f, ACCENT.g / 255.f, ACCENT.b / 255.f, 1.f} : ccColor4F{0.47f, 0.47f, 0.47f, 1.f};
                tri->drawPolygon(pts, 3, col, 0.f, col);
                node->addChild(tri, 4);
                m_hits.push_back({wl.id, (int)r, -1, {wl.box.x + w - 16.f, wl.box.y + ry, 16.f, ROW_H}});
            }
        }
    }
    refreshLive();
}

void XeniaTableBoard::clampScroll() {
    float maxX = std::max(0.f, m_contentW * m_scale - m_view.width);
    float maxY = std::max(0.f, m_contentH * m_scale - (m_view.height - STATUS_H));
    m_scrollX = std::clamp(m_scrollX, 0.f, maxX);
    m_scrollY = std::clamp(m_scrollY, 0.f, maxY);
}

void XeniaTableBoard::applyScroll() {
    s_scrollX = m_scrollX;
    s_scrollY = m_scrollY;
    m_content->setPosition({-m_scrollX, m_view.height - m_contentH * m_scale + m_scrollY});
    updateBars();
}

void XeniaTableBoard::updateBars() {
    float maxX = std::max(0.f, m_contentW * m_scale - m_view.width);
    float maxY = std::max(0.f, m_contentH * m_scale - (m_view.height - STATUS_H));
    if (maxX > 1.f) {
        float vw = m_view.width;
        float tw = std::clamp(vw * vw / (m_contentW * m_scale), 24.f, vw);
        float f = m_scrollX / maxX;
        m_hBar->setContentSize({tw, 2.f});
        m_hBar->setPosition({f * (vw - tw), STATUS_H + 1.f});
        m_hBar->setVisible(true);
    } else {
        m_hBar->setVisible(false);
    }
    if (maxY > 1.f) {
        float vh = m_view.height - STATUS_H;
        float th = std::clamp(vh * vh / (m_contentH * m_scale), 24.f, vh);
        float f = m_scrollY / maxY;
        m_vBar->setContentSize({2.f, th});
        m_vBar->setPosition({m_view.width - 3.f, m_view.height - th - f * (vh - th)});
        m_vBar->setVisible(true);
    } else {
        m_vBar->setVisible(false);
    }
}

CCPoint XeniaTableBoard::toContent(CCPoint const& screen) const {
    auto pos = m_content->getPosition();
    return {(screen.x - pos.x) / m_scale, (screen.y - pos.y) / m_scale};
}

bool XeniaTableBoard::onInputBox(CCPoint const& screen) const {
    for (auto b : m_inputBoxes) {
        if (!b || !b->getParent()) continue;
        auto local = b->convertToNodeSpace(screen);
        auto sz = b->getContentSize();
        if (local.x >= -2.f && local.y >= -2.f && local.x <= sz.width + 2.f && local.y <= sz.height + 2.f) return true;
    }
    return false;
}

bool XeniaTableBoard::pick(CCPoint const& screen) {
    m_target = Target::None;
    m_tWin = m_tRow = m_tCell = -1;
    auto p = toContent(screen);
    auto inside = [&](Box const& b) {
        return p.x >= b.x && p.x <= b.x + b.w && p.y >= b.y && p.y <= b.y + b.h;
    };
    for (auto& wl : m_layout) {
        if (inside(wl.head)) {
            m_target = Target::Head;
            m_tWin = wl.id;
            return true;
        }
    }
    for (auto& h : m_hits) {
        if (inside(h.box)) {
            m_tWin = h.win;
            m_tRow = h.row;
            m_tCell = h.cell;
            m_target = h.cell < 0 ? Target::Gear : Target::Cell;
            return true;
        }
    }
    return false;
}

void XeniaTableBoard::showPress(Box const& b) {
    hidePress();
    m_press = CCLayerColor::create({255, 255, 255, 38}, b.w, b.h);
    m_press->setPosition({b.x, b.y});
    m_content->addChild(m_press, 50);
}

void XeniaTableBoard::hidePress() {
    if (m_press) {
        m_press->removeFromParent();
        m_press = nullptr;
    }
}

bool XeniaTableBoard::ccTouchBegan(CCTouch* t, CCEvent*) {
    if (!this->isVisible()) return false;
    if (ui::coveredByPopup(this)) return false;
    auto p = this->convertToNodeSpace(t->getLocation());

    m_down = true;
    m_mode = Mode::None;
    m_start = m_last = p;
    m_target = Target::None;

    if (p.y <= STATUS_H) {
        if (p.x >= m_view.width - 56.f) m_target = Target::Close;
        return true;
    }
    if (onInputBox(p)) {
        m_down = false;
        return false;
    }
    defocusAll();

    if (pick(p)) {
        if (m_target == Target::Head) {
            for (auto& wl : m_layout) if (wl.id == m_tWin) { showPress(wl.head); break; }
        } else {
            for (auto& h : m_hits) {
                if (h.win == m_tWin && h.row == m_tRow && h.cell == m_tCell) { showPress(h.box); break; }
            }
        }
    }
    return true;
}

void XeniaTableBoard::ccTouchMoved(CCTouch* t, CCEvent*) {
    if (!m_down) return;
    auto p = this->convertToNodeSpace(t->getLocation());
    if (m_mode == Mode::None && ccpDistance(p, m_start) > DRAG_SLOP) {
        hidePress();
        if (m_target == Target::Close) {
            m_mode = Mode::Pan;
        } else if (m_target == Target::Head) {
            m_mode = Mode::DragWin;
            m_dragWinId = m_tWin;
            m_dragNode = m_content->getChildByTag(100 + m_dragWinId);
            if (m_dragNode) {
                m_dragOff = toContent(p) - m_dragNode->getPosition();
                m_dragNode->setZOrder(100);
            } else {
                m_mode = Mode::Pan;
            }
        } else {
            m_mode = Mode::Pan;
        }
    }
    if (m_mode == Mode::Pan) {
        m_scrollX -= p.x - m_last.x;
        m_scrollY += p.y - m_last.y;
        clampScroll();
        applyScroll();
    } else if (m_mode == Mode::DragWin && m_dragNode) {
        m_dragNode->setPosition(toContent(p) - m_dragOff);
    }
    m_last = p;
}

void XeniaTableBoard::ccTouchEnded(CCTouch* t, CCEvent*) {
    if (!m_down) return;
    m_down = false;
    hidePress();
    auto p = this->convertToNodeSpace(t->getLocation());
    if (m_mode == Mode::DragWin) {
        finishDrag(p);
    } else if (m_mode == Mode::None) {
        if (m_target == Target::Close) {
            Loader::get()->queueInMainThread([] { XeniaTable::closeNow(); });
        } else if (m_target != Target::None) {
            doTap();
        }
    }
    m_mode = Mode::None;
    m_dragNode = nullptr;
}

void XeniaTableBoard::ccTouchCancelled(CCTouch*, CCEvent*) {
    m_down = false;
    hidePress();
    if (m_mode == Mode::DragWin) scheduleRebuild(0.01f);
    m_mode = Mode::None;
    m_dragNode = nullptr;
}

void XeniaTableBoard::doTap() {
    auto& st = xenia::State::get();
    if (m_target == Target::Head) {
        st.tableCollapsed ^= (1 << m_tWin);
        st.save();
        rebuild();
        return;
    }
    if (m_target == Target::Gear) {
        int gear = -1;
        if (m_tWin >= 0 && m_tWin < (int)m_wins.size()) {
            auto& rows = m_wins[m_tWin].rows;
            if (m_tRow >= 0 && m_tRow < (int)rows.size()) gear = rows[m_tRow].gear;
        }
        if (gear < 0) return;
        if (s_open.count(gear)) s_open.erase(gear);
        else s_open.insert(gear);
        scheduleRebuild();
        return;
    }
    if (m_target == Target::Cell) {
        if (m_tWin < 0 || m_tWin >= (int)m_wins.size()) return;
        auto& rows = m_wins[m_tWin].rows;
        if (m_tRow < 0 || m_tRow >= (int)rows.size()) return;
        auto& cells = rows[m_tRow].cells;
        if (m_tCell < 0 || m_tCell >= (int)cells.size()) return;
        auto cell = cells[m_tCell];
        if (!cell.hint.empty()) setHint(cell.hint);
        if (cell.tap) cell.tap();
    }
}

void XeniaTableBoard::finishDrag(CCPoint const& screen) {
    auto drop = toContent(screen);
    int dragged = m_dragWinId;
    m_dragWinId = -1;

    int bestId = -1;
    float bestD = 1e18f;
    float rightMost = 0.f;
    Box bestBox{0, 0, 0, 0};
    for (auto& wl : m_layout) {
        rightMost = std::max(rightMost, wl.box.x + wl.box.w);
        if (wl.id == dragged) continue;
        float cx = wl.box.x + wl.box.w / 2, cy = wl.box.y + wl.box.h / 2;
        float d = (drop.x - cx) * (drop.x - cx) + (drop.y - cy) * (drop.y - cy);
        if (d < bestD) { bestD = d; bestId = wl.id; bestBox = wl.box; }
    }

    std::vector<int> neo;
    for (int id : m_order) if (id != dragged) neo.push_back(id);
    if (bestId < 0) {
        neo.push_back(dragged);
    } else if (drop.x > rightMost) {
        neo.push_back(dragged);
    } else {
        auto it = std::find(neo.begin(), neo.end(), bestId);
        bool sameCol = std::abs(drop.x - (bestBox.x + bestBox.w / 2)) < bestBox.w / 2;
        bool below = drop.y < bestBox.y + bestBox.h / 2;
        if (sameCol && below && it != neo.end()) ++it;
        neo.insert(it, dragged);
    }
    m_order = neo;
    saveOrder();

    layoutWindows();
    for (auto& wl : m_layout) {
        if (wl.id != dragged) continue;
        if (auto node = m_content->getChildByTag(100 + dragged)) {
            node->runAction(CCEaseOut::create(CCMoveTo::create(0.12f, {wl.box.x, wl.box.y}), 2.f));
        }
    }
    scheduleRebuild(0.15f);
}

void XeniaTableBoard::scrollWheel(float y, float x) {
    constexpr float step = xenia::ui::platform::kWheelStep;
    if (step <= 0.f) return;
    bool shift = false;
#ifdef GEODE_IS_DESKTOP
    shift = XeniaWindowsKeys::shiftHeld();
#endif
    if (shift) m_scrollX += (y + x) * step;
    else {
        m_scrollY += y * step;
        m_scrollX += x * step;
    }
    clampScroll();
    applyScroll();
}
