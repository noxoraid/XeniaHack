#include "vtscreen.h"

#include <algorithm>
#include <cstdio>
#include <utility>

namespace {

constexpr size_t kMaxHistory = 5000;

int clampInt(int v, int lo, int hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

int32_t rgb(int r, int g, int b) {
    uint32_t value = 0xFFu << 24;
    value |= static_cast<uint32_t>(clampInt(r, 0, 255)) << 16;
    value |= static_cast<uint32_t>(clampInt(g, 0, 255)) << 8;
    value |= static_cast<uint32_t>(clampInt(b, 0, 255));
    return static_cast<int32_t>(value);
}

const int32_t kPalette[16] = {
    rgb(0, 0, 0),       rgb(205, 49, 49),  rgb(13, 188, 121),  rgb(229, 229, 16),
    rgb(36, 114, 200),  rgb(188, 63, 188), rgb(17, 168, 205),  rgb(229, 229, 229),
    rgb(102, 102, 102), rgb(241, 76, 76),  rgb(35, 209, 139),  rgb(245, 245, 67),
    rgb(59, 142, 234),  rgb(214, 112, 214), rgb(41, 184, 219), rgb(255, 255, 255),
};

int32_t paletteColor(int n) {
    if (n < 16) return kPalette[std::max(n, 0)];
    if (n < 232) {
        int k = n - 16;
        auto level = [](int x) { return x == 0 ? 0 : 55 + x * 40; };
        return rgb(level(k / 36), level((k / 6) % 6), level(k % 6));
    }
    int gray = 8 + (std::min(n, 255) - 232) * 10;
    return rgb(gray, gray, gray);
}

Row makeRow(int cols, int bg) {
    Row row(static_cast<size_t>(cols));
    for (Cell& c : row) c.bg = bg;
    return row;
}

void clearRow(Row& row, int from, int to, int bg) {
    int end = std::min(to, static_cast<int>(row.size()));
    for (int i = std::max(from, 0); i < end; i++) row[i] = Cell{32, 0, bg, 0};
}

void resizeRow(Row& row, int n) {
    if (static_cast<int>(row.size()) == n) return;
    row.resize(static_cast<size_t>(n));
}

int parseNumber(const std::string& token) {
    long long value = 0;
    bool any = false;
    for (char ch : token) {
        if (ch < '0' || ch > '9') continue;
        any = true;
        value = value * 10 + (ch - '0');
        if (value > 2147483647LL) return 0;
    }
    return any ? static_cast<int>(value) : 0;
}

}

VtScreen::VtScreen(int cols, int rows) : cols_(cols), rows_(rows), bot_(rows - 1) {
    screen_.assign(static_cast<size_t>(rows), makeRow(cols, 0));
}

void VtScreen::reply(const char* text) {
    for (const char* p = text; *p; p++) replies_.push_back(static_cast<char16_t>(*p));
}

std::u16string VtScreen::feed(const char16_t* data, int count) {
    for (int i = 0; i < count; i++) put(data[i]);
    std::u16string out;
    out.swap(replies_);
    return out;
}

void VtScreen::put(char16_t c) {
    switch (state_) {
        case 0: normal(c); break;
        case 1: escape(c); break;
        case 2: csiChar(c); break;
        case 3:
            if (c == 0x07) state_ = 0;
            else if (c == 0x1b) state_ = 5;
            break;
        case 4: state_ = 0; break;
        case 5: state_ = 0; break;
        default: break;
    }
}

void VtScreen::normal(char16_t c) {
    if (c == 0x1b) {
        state_ = 1;
    } else if (c == '\r') {
        cx_ = 0;
        wrapPending_ = false;
    } else if (c == '\n' || c == 0x0b || c == 0x0c) {
        lineFeed();
        wrapPending_ = false;
    } else if (c == '\b') {
        if (cx_ > 0) cx_--;
        wrapPending_ = false;
    } else if (c == '\t') {
        cx_ = std::min(cols_ - 1, (cx_ / 8 + 1) * 8);
        wrapPending_ = false;
    } else if (c < ' ' || c == 0x7f) {
    } else if (c >= 0xDC00 && c <= 0xDFFF) {
    } else if (c >= 0xD800 && c <= 0xDBFF) {
        printCode('?');
    } else {
        printCode(static_cast<int32_t>(c));
    }
}

void VtScreen::printCode(int32_t code) {
    if (wrapPending_) {
        cx_ = 0;
        lineFeed();
        wrapPending_ = false;
    }
    Cell& cell = screen_[cy_][cx_];
    cell.ch = code;
    cell.fg = curFg_;
    cell.bg = curBg_;
    cell.fl = curFl_;
    if (cx_ == cols_ - 1) wrapPending_ = true;
    else cx_++;
}

void VtScreen::lineFeed() {
    if (cy_ == bot_) scrollUp(1);
    else if (cy_ < rows_ - 1) cy_++;
}

void VtScreen::scrollUp(int n) {
    for (int k = 0; k < n; k++) {
        Row removed = std::move(screen_[top_]);
        screen_.erase(screen_.begin() + top_);
        if (!alt_ && top_ == 0) {
            history_.push_back(std::move(removed));
            if (history_.size() > kMaxHistory) history_.pop_front();
            screen_.insert(screen_.begin() + bot_, makeRow(cols_, curBg_));
        } else {
            clearRow(removed, 0, cols_, curBg_);
            screen_.insert(screen_.begin() + bot_, std::move(removed));
        }
    }
}

void VtScreen::scrollDown(int n) {
    for (int k = 0; k < n; k++) {
        Row removed = std::move(screen_[bot_]);
        screen_.erase(screen_.begin() + bot_);
        clearRow(removed, 0, cols_, curBg_);
        screen_.insert(screen_.begin() + top_, std::move(removed));
    }
}

void VtScreen::escape(char16_t c) {
    state_ = 0;
    switch (c) {
        case '[':
            csi_.clear();
            state_ = 2;
            break;
        case ']': case 'P': case '^': case '_': case 'X':
            state_ = 3;
            break;
        case '(': case ')': case '*': case '+': case '#': case '%':
            state_ = 4;
            break;
        case '7': saveCursor(); break;
        case '8': restoreCursor(); break;
        case 'D': lineFeed(); break;
        case 'E':
            cx_ = 0;
            lineFeed();
            break;
        case 'M':
            if (cy_ == top_) scrollDown(1);
            else if (cy_ > 0) cy_--;
            break;
        case 'c': fullReset(); break;
        default: break;
    }
}

void VtScreen::csiChar(char16_t c) {
    if (c >= '@' && c <= '~') {
        state_ = 0;
        runCsi(c);
    } else if (c == 0x1b) {
        state_ = 1;
    } else if (csi_.size() < 80) {
        csi_.push_back(c < 128 ? static_cast<char>(c) : '?');
    }
}

void VtScreen::runCsi(char16_t final) {
    char pre = ' ';
    std::string body = csi_;
    if (!csi_.empty() && (csi_[0] == '?' || csi_[0] == '>' || csi_[0] == '=' || csi_[0] == '<')) {
        pre = csi_[0];
        body = csi_.substr(1);
    }
    std::vector<int> p;
    if (!body.empty()) {
        std::string token;
        for (char ch : body) {
            if (ch == ';' || ch == ':') {
                p.push_back(parseNumber(token));
                token.clear();
            } else {
                token.push_back(ch);
            }
        }
        p.push_back(parseNumber(token));
    }
    if (pre == '?') {
        if (final == 'h' || final == 'l') setModes(p, final == 'h');
        return;
    }
    if (pre != ' ') {
        if (final == 'c') reply("\x1b[>0;0;0c");
        return;
    }
    if (final != 'm') wrapPending_ = false;

    auto param = [&p](size_t i, int d) { return i < p.size() && p[i] > 0 ? p[i] : d; };
    int a0 = param(0, 1);
    int z = p.empty() ? 0 : p[0];

    switch (final) {
        case 'A': cy_ = std::max(0, cy_ - a0); break;
        case 'B': case 'e': cy_ = std::min(rows_ - 1, cy_ + a0); break;
        case 'C': case 'a': cx_ = std::min(cols_ - 1, cx_ + a0); break;
        case 'D': cx_ = std::max(0, cx_ - a0); break;
        case 'E':
            cx_ = 0;
            cy_ = std::min(rows_ - 1, cy_ + a0);
            break;
        case 'F':
            cx_ = 0;
            cy_ = std::max(0, cy_ - a0);
            break;
        case 'G': case '`': cx_ = clampInt(a0 - 1, 0, cols_ - 1); break;
        case 'H': case 'f':
            cy_ = clampInt(param(0, 1) - 1, 0, rows_ - 1);
            cx_ = clampInt(param(1, 1) - 1, 0, cols_ - 1);
            break;
        case 'd': cy_ = clampInt(a0 - 1, 0, rows_ - 1); break;
        case 'J': eraseDisplay(z); break;
        case 'K':
            if (z == 0) clearRow(screen_[cy_], cx_, cols_, curBg_);
            else if (z == 1) clearRow(screen_[cy_], 0, cx_ + 1, curBg_);
            else clearRow(screen_[cy_], 0, cols_, curBg_);
            break;
        case 'L':
            if (cy_ >= top_ && cy_ <= bot_) {
                int times = std::min(a0, bot_ - cy_ + 1);
                for (int k = 0; k < times; k++) {
                    Row removed = std::move(screen_[bot_]);
                    screen_.erase(screen_.begin() + bot_);
                    clearRow(removed, 0, cols_, curBg_);
                    screen_.insert(screen_.begin() + cy_, std::move(removed));
                }
            }
            break;
        case 'M':
            if (cy_ >= top_ && cy_ <= bot_) {
                int times = std::min(a0, bot_ - cy_ + 1);
                for (int k = 0; k < times; k++) {
                    Row removed = std::move(screen_[cy_]);
                    screen_.erase(screen_.begin() + cy_);
                    clearRow(removed, 0, cols_, curBg_);
                    screen_.insert(screen_.begin() + bot_, std::move(removed));
                }
            }
            break;
        case '@': {
            Row& row = screen_[cy_];
            int k = std::min(a0, cols_ - cx_);
            for (int i = cols_ - 1; i >= cx_ + k; i--) row[i] = row[i - k];
            clearRow(row, cx_, cx_ + k, curBg_);
            break;
        }
        case 'P': {
            Row& row = screen_[cy_];
            int k = std::min(a0, cols_ - cx_);
            for (int i = cx_; i < cols_ - k; i++) row[i] = row[i + k];
            clearRow(row, cols_ - k, cols_, curBg_);
            break;
        }
        case 'X': clearRow(screen_[cy_], cx_, std::min(cols_, cx_ + a0), curBg_); break;
        case 'S': scrollUp(a0); break;
        case 'T': scrollDown(a0); break;
        case 'm': sgr(p); break;
        case 'r': {
            int t = param(0, 1) - 1;
            int b = param(1, rows_) - 1;
            if (t < b && b < rows_) {
                top_ = t;
                bot_ = b;
            } else {
                top_ = 0;
                bot_ = rows_ - 1;
            }
            cx_ = 0;
            cy_ = 0;
            break;
        }
        case 's': saveCursor(); break;
        case 'u': restoreCursor(); break;
        case 'n':
            if (z == 6) {
                char buf[32];
                std::snprintf(buf, sizeof(buf), "\x1b[%d;%dR", cy_ + 1, cx_ + 1);
                reply(buf);
            } else if (z == 5) {
                reply("\x1b[0n");
            }
            break;
        case 'c': reply("\x1b[?1;2c"); break;
        default: break;
    }
}

void VtScreen::setModes(const std::vector<int>& params, bool on) {
    for (int n : params) {
        switch (n) {
            case 1: appCursor_ = on; break;
            case 25: cursorVisible_ = on; break;
            case 2004: bracketed_ = on; break;
            case 47: case 1047: case 1049: switchAlt(on); break;
            default: break;
        }
    }
}

void VtScreen::switchAlt(bool on) {
    if (on && !alt_) {
        mainScreen_ = std::move(screen_);
        hasMain_ = true;
        mainCx_ = cx_;
        mainCy_ = cy_;
        screen_.assign(static_cast<size_t>(rows_), makeRow(cols_, 0));
        alt_ = true;
        top_ = 0;
        bot_ = rows_ - 1;
    } else if (!on && alt_) {
        if (hasMain_) {
            screen_ = std::move(mainScreen_);
            mainScreen_.clear();
            hasMain_ = false;
        }
        alt_ = false;
        cx_ = clampInt(mainCx_, 0, cols_ - 1);
        cy_ = clampInt(mainCy_, 0, rows_ - 1);
        top_ = 0;
        bot_ = rows_ - 1;
    }
}

void VtScreen::eraseDisplay(int mode) {
    if (mode == 0) {
        clearRow(screen_[cy_], cx_, cols_, curBg_);
        for (int r = cy_ + 1; r < rows_; r++) clearRow(screen_[r], 0, cols_, curBg_);
    } else if (mode == 1) {
        for (int r = 0; r < cy_; r++) clearRow(screen_[r], 0, cols_, curBg_);
        clearRow(screen_[cy_], 0, cx_ + 1, curBg_);
    } else {
        for (int r = 0; r < rows_; r++) clearRow(screen_[r], 0, cols_, curBg_);
        if (mode == 3) history_.clear();
    }
}

void VtScreen::saveCursor() {
    savedX_ = cx_;
    savedY_ = cy_;
    savedFg_ = curFg_;
    savedBg_ = curBg_;
    savedFl_ = curFl_;
}

void VtScreen::restoreCursor() {
    cx_ = clampInt(savedX_, 0, cols_ - 1);
    cy_ = clampInt(savedY_, 0, rows_ - 1);
    curFg_ = savedFg_;
    curBg_ = savedBg_;
    curFl_ = savedFl_;
    wrapPending_ = false;
}

void VtScreen::fullReset() {
    for (Row& row : screen_) clearRow(row, 0, cols_, 0);
    cx_ = 0;
    cy_ = 0;
    top_ = 0;
    bot_ = rows_ - 1;
    curFg_ = 0;
    curBg_ = 0;
    curFl_ = 0;
    wrapPending_ = false;
}

void VtScreen::sgr(const std::vector<int>& p) {
    if (p.empty()) {
        curFg_ = 0;
        curBg_ = 0;
        curFl_ = 0;
        return;
    }
    auto at = [&p](size_t i) { return i < p.size() ? p[i] : 0; };
    size_t i = 0;
    while (i < p.size()) {
        int v = p[i];
        if (v == 0) {
            curFg_ = 0;
            curBg_ = 0;
            curFl_ = 0;
        } else if (v == 1) {
            curFl_ |= 1;
        } else if (v == 4) {
            curFl_ |= 2;
        } else if (v == 7) {
            curFl_ |= 4;
        } else if (v == 22) {
            curFl_ &= ~1;
        } else if (v == 24) {
            curFl_ &= ~2;
        } else if (v == 27) {
            curFl_ &= ~4;
        } else if (v >= 30 && v <= 37) {
            curFg_ = kPalette[v - 30];
        } else if (v == 38 || v == 48) {
            int32_t color = 0;
            bool hasMode = i + 1 < p.size();
            int mode = hasMode ? p[i + 1] : -1;
            if (mode == 5) {
                color = paletteColor(at(i + 2));
                i += 2;
            } else if (mode == 2) {
                color = rgb(at(i + 2), at(i + 3), at(i + 4));
                i += 4;
            }
            if (v == 38) curFg_ = color;
            else curBg_ = color;
        } else if (v == 39) {
            curFg_ = 0;
        } else if (v >= 40 && v <= 47) {
            curBg_ = kPalette[v - 40];
        } else if (v == 49) {
            curBg_ = 0;
        } else if (v >= 90 && v <= 97) {
            curFg_ = kPalette[v - 90 + 8];
        } else if (v >= 100 && v <= 107) {
            curBg_ = kPalette[v - 100 + 8];
        }
        i++;
    }
}

void VtScreen::resize(int newCols, int newRows) {
    for (Row& row : screen_) resizeRow(row, newCols);
    if (hasMain_) for (Row& row : mainScreen_) resizeRow(row, newCols);

    while (static_cast<int>(screen_.size()) > newRows) {
        if (!alt_ && cy_ >= newRows) {
            history_.push_back(std::move(screen_.front()));
            screen_.erase(screen_.begin());
            cy_--;
            if (history_.size() > kMaxHistory) history_.pop_front();
        } else {
            screen_.pop_back();
        }
    }
    while (static_cast<int>(screen_.size()) < newRows) screen_.push_back(makeRow(newCols, 0));

    if (hasMain_) {
        while (static_cast<int>(mainScreen_.size()) > newRows) mainScreen_.pop_back();
        while (static_cast<int>(mainScreen_.size()) < newRows) mainScreen_.push_back(makeRow(newCols, 0));
    }

    cols_ = newCols;
    rows_ = newRows;
    top_ = 0;
    bot_ = newRows - 1;
    cx_ = clampInt(cx_, 0, newCols - 1);
    cy_ = clampInt(cy_, 0, newRows - 1);
    wrapPending_ = false;
}

void VtScreen::copyRow(int index, int capacity, std::vector<int32_t>& ch, std::vector<int32_t>& fg,
                       std::vector<int32_t>& bg, std::vector<int32_t>& fl) const {
    const Row* row;
    int hist = static_cast<int>(history_.size());
    if (index >= 0 && index < hist) row = &history_[static_cast<size_t>(index)];
    else row = &screen_[static_cast<size_t>(clampInt(index - hist, 0, static_cast<int>(screen_.size()) - 1))];
    ch.assign(static_cast<size_t>(capacity), 32);
    fg.assign(static_cast<size_t>(capacity), 0);
    bg.assign(static_cast<size_t>(capacity), 0);
    fl.assign(static_cast<size_t>(capacity), 0);
    int n = std::min(capacity, static_cast<int>(row->size()));
    for (int j = 0; j < n; j++) {
        const Cell& c = (*row)[static_cast<size_t>(j)];
        ch[j] = c.ch;
        fg[j] = c.fg;
        bg[j] = c.bg;
        fl[j] = c.fl;
    }
}
