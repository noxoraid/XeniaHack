#include "xenia/core/Lang.hpp"
#include "xenia/ui/popups/XeniaDialog.hpp"
#include "xenia/ui/widgets/Widgets.hpp"
#include "xenia/core/State.hpp"
#include "xenia/core/Theme.hpp"
using namespace geode::prelude;
namespace ui = xenia::ui;

static std::vector<std::string> wrapText(std::string const& text, float maxW, float scale) {
    auto widthOf = [&](std::string const& s) {
        auto l = CCLabelBMFont::create(s.c_str(), ui::font());
        return l->getContentWidth() * scale;
    };
    std::vector<std::string> lines;
    size_t start = 0;
    while (start <= text.size()) {
        size_t nl = text.find('\n', start);
        std::string para = text.substr(start, nl == std::string::npos ? std::string::npos : nl - start);
        std::string cur;
        size_t i = 0;
        while (i < para.size()) {
            size_t sp = para.find(' ', i);
            std::string word = para.substr(i, sp == std::string::npos ? std::string::npos : sp - i);
            std::string test = cur.empty() ? word : cur + " " + word;
            if (!cur.empty() && widthOf(test) > maxW) {
                lines.push_back(cur);
                cur = word;
            } else {
                cur = test;
            }
            if (sp == std::string::npos) break;
            i = sp + 1;
        }
        lines.push_back(cur);
        if (nl == std::string::npos) break;
        start = nl + 1;
    }
    return lines;
}

XeniaDialog* XeniaDialog::make(Kind k, std::string title, std::string text) {
    auto ret = new XeniaDialog();
    ret->m_kind = k;
    ret->m_title = std::move(title);
    ret->m_text = std::move(text);
    return ret;
}

static void showDialog(XeniaDialog* d, bool ok) {
    if (ok) {
        d->autorelease();
        d->show();
    } else {
        delete d;
    }
}

void XeniaDialog::info(std::string title, std::string text) {
    auto d = make(Kind::Info, std::move(title), std::move(text));
    showDialog(d, d->initDialog());
}

void XeniaDialog::confirm(std::string title, std::string text, std::function<void()> onYes) {
    auto d = make(Kind::Confirm, std::move(title), std::move(text));
    d->m_onYes = std::move(onYes);
    showDialog(d, d->initDialog());
}

void XeniaDialog::input(std::string title, std::string hint, std::string initial, InputCb onOk) {
    auto d = make(Kind::Input, std::move(title), "");
    d->m_hint = std::move(hint);
    d->m_initial = std::move(initial);
    d->m_onInput = std::move(onOk);
    showDialog(d, d->initDialog());
}

bool XeniaDialog::initDialog() {
    auto win = CCDirector::get()->getWinSize();
    auto& st = xenia::State::get();
    auto& th = xenia::currentTheme(st.theme);

    float W = std::min(300.f, win.width * 0.92f);
    float tscale = 0.4f * ui::fontNorm() * st.textScale;
    std::vector<std::string> lines;
    float lineH = 14.f;
    if (m_kind != Kind::Input) {
        lines = wrapText(m_text, W - 40.f, tscale);
        auto probe = CCLabelBMFont::create("Ag", ui::font());
        lineH = std::max(12.f, probe->getContentHeight() * tscale * 1.25f);
    }
    float textH = lines.size() * lineH;
    float H = 44.f + (m_kind == Kind::Input ? 84.f : textH + 10.f) + 50.f;
    H = std::max(H, 120.f);

    if (!Popup::init(W, H)) return false;
    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    float fit = std::min(win.width * 0.98f / W, win.height * 0.98f / H);
    if (fit < 1.f) m_mainLayer->setScale(fit);

    auto bg = ui::makeSlice("panel.png", W, H, 16.f, 0.125f);
    ui::setSliceColor(bg, th.bg);
    ui::setSliceOpacity(bg, (GLubyte)std::round(std::max(st.uiOpacity, 0.85f) * 255.f));
    m_mainLayer->addChild(bg, -1);

    auto title = ui::makeLabel(m_title.c_str(), W - 80.f, 0.55f);
    title->setPosition({W / 2, H - 24.f});
    m_mainLayer->addChild(title);

    auto menu = CCMenu::create();
    menu->setPosition({0, 0});
    m_mainLayer->addChild(menu, 5);

    float y = H - 52.f;
    if (m_kind == Kind::Input) {
        auto hint = ui::makeLabel(m_hint.c_str(), W - 40.f, 0.36f);
        hint->setPosition({W / 2, y});
        m_mainLayer->addChild(hint);

        auto inBg = ui::makeSlice("box.png", 200.f, 34.f, 10.f);
        inBg->setPosition({W / 2 - 100.f, y - 12.f - 34.f});
        m_mainLayer->addChild(inBg);

        m_input = TextInput::create(200.f, xenia::tr("name", "nama"), ui::font());
        m_input->setFilter("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-");
        m_input->setMaxCharCount(32);
        m_input->setString(m_initial, false);
        if (auto b = m_input->getBGSprite()) b->setVisible(false);
        m_input->setScale(0.75f);
        m_input->setPosition({W / 2, y - 12.f - 17.f});
        m_mainLayer->addChild(m_input);

        m_status = ui::makeLabel("", W - 30.f, 0.34f);
        m_status->setPosition({W / 2, y - 12.f - 34.f - 14.f});
        m_status->setColor({255, 110, 110});
        m_mainLayer->addChild(m_status);
    } else {
        for (auto const& ln : lines) {
            auto l = CCLabelBMFont::create(ln.c_str(), ui::font());
            l->setScale(tscale);
            l->setAnchorPoint({0.5f, 1.f});
            l->setPosition({W / 2, y});
            m_mainLayer->addChild(l);
            y -= lineH;
        }
    }

    float by = 26.f;
    if (m_kind == Kind::Info) {
        auto ok = CCMenuItemSpriteExtra::create(ui::makeButton("OK", 110.f, true), this, menu_selector(XeniaDialog::onCancel));
        ok->setPosition({W / 2, by});
        menu->addChild(ok);
    } else {
        auto no = CCMenuItemSpriteExtra::create(ui::makeButton(xenia::tr("Cancel", "Batal"), 100.f, false), this, menu_selector(XeniaDialog::onCancel));
        no->setPosition({W / 2 - 56.f, by});
        menu->addChild(no);
        auto yes = CCMenuItemSpriteExtra::create(
            ui::makeButton(m_kind == Kind::Confirm ? xenia::tr("Yes", "Ya") : "OK", 100.f, true), this,
            m_kind == Kind::Confirm ? menu_selector(XeniaDialog::onYes) : menu_selector(XeniaDialog::onOk));
        yes->setPosition({W / 2 + 56.f, by});
        menu->addChild(yes);
    }
    return true;
}

void XeniaDialog::setStatus(std::string const& text) {
    if (m_status) m_status->setString(text.c_str());
}

void XeniaDialog::onOk(CCObject*) {
    if (!m_input || !m_onInput) return this->onClose(nullptr);
    auto err = m_onInput(m_input->getString());
    if (err.empty()) this->onClose(nullptr);
    else setStatus(err);
}

void XeniaDialog::onYes(CCObject*) {
    auto cb = m_onYes;
    this->onClose(nullptr);
    if (cb) cb();
}

void XeniaDialog::onCancel(CCObject*) {
    this->onClose(nullptr);
}
