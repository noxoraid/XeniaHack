#include "xenia/ui/table/XeniaTable.hpp"
#include "xenia/ui/table/XeniaTableBoard.hpp"
using namespace geode::prelude;

XeniaTable* XeniaTable::s_instance = nullptr;

XeniaTable::~XeniaTable() {
    m_board = nullptr;
    if (s_instance == this) s_instance = nullptr;
}

XeniaTable* XeniaTable::create() {
    auto ret = new XeniaTable();
    if (ret && ret->initTable()) {
        ret->autorelease();
        s_instance = ret;
        return ret;
    }
    CC_SAFE_DELETE(ret);
    return nullptr;
}

bool XeniaTable::initTable() {
    auto win = CCDirector::get()->getWinSize();
    if (!Popup::init(win.width, win.height)) return false;
    m_bgSprite->setVisible(false);
    m_closeBtn->setVisible(false);

    m_board = XeniaTableBoard::create(win);
    if (!m_board) return false;
    this->addChild(m_board, 50);
    return true;
}

void XeniaTable::open() {
    if (s_instance) return;
    if (auto t = XeniaTable::create()) t->show();
}

void XeniaTable::closeNow() {
    if (!s_instance) return;
    auto t = s_instance;
    s_instance = nullptr;
    t->onClose(nullptr);
}

bool XeniaTable::isOpen() {
    return s_instance != nullptr;
}
