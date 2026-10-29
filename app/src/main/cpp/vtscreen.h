#pragma once

#include <cstdint>
#include <deque>
#include <string>
#include <vector>

struct Cell {
    int32_t ch = 32;
    int32_t fg = 0;
    int32_t bg = 0;
    int32_t fl = 0;
};

using Row = std::vector<Cell>;

class VtScreen {
public:
    VtScreen(int cols, int rows);

    std::u16string feed(const char16_t* data, int count);
    void resize(int newCols, int newRows);
    void copyRow(int index, int capacity, std::vector<int32_t>& ch, std::vector<int32_t>& fg,
                 std::vector<int32_t>& bg, std::vector<int32_t>& fl) const;

    int cx() const { return cx_; }
    int cy() const { return cy_; }
    int cols() const { return cols_; }
    int rows() const { return rows_; }
    bool appCursor() const { return appCursor_; }
    bool bracketed() const { return bracketed_; }
    bool cursorVisible() const { return cursorVisible_; }
    int historySize() const { return static_cast<int>(history_.size()); }

private:
    void put(char16_t c);
    void normal(char16_t c);
    void escape(char16_t c);
    void csiChar(char16_t c);
    void runCsi(char16_t final);
    void setModes(const std::vector<int>& params, bool on);
    void switchAlt(bool on);
    void eraseDisplay(int mode);
    void sgr(const std::vector<int>& params);
    void printCode(int32_t code);
    void lineFeed();
    void scrollUp(int n);
    void scrollDown(int n);
    void saveCursor();
    void restoreCursor();
    void fullReset();
    void reply(const char* text);

    int cols_;
    int rows_;
    int cx_ = 0;
    int cy_ = 0;
    int top_ = 0;
    int bot_;
    int curFg_ = 0;
    int curBg_ = 0;
    int curFl_ = 0;
    int savedX_ = 0;
    int savedY_ = 0;
    int savedFg_ = 0;
    int savedBg_ = 0;
    int savedFl_ = 0;
    int mainCx_ = 0;
    int mainCy_ = 0;
    int state_ = 0;
    bool wrapPending_ = false;
    bool alt_ = false;
    bool hasMain_ = false;
    bool appCursor_ = false;
    bool bracketed_ = false;
    bool cursorVisible_ = true;
    std::string csi_;
    std::u16string replies_;
    std::vector<Row> screen_;
    std::vector<Row> mainScreen_;
    std::deque<Row> history_;
};
