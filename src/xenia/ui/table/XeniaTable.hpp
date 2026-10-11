#pragma once
#include <Geode/Geode.hpp>

class XeniaTableBoard;

class XeniaTable : public geode::Popup {
protected:
    static XeniaTable* s_instance;
    XeniaTableBoard* m_board = nullptr;

    ~XeniaTable() override;
    bool initTable();

public:
    static XeniaTable* create();
    static void open();
    static void closeNow();
    static bool isOpen();
};
