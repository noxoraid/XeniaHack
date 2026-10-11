#pragma once
#include <Geode/Geode.hpp>
#include "xenia/core/State.hpp"

namespace xenia {
    enum LangId { LangEn = 0, LangIndo = 1 };
    constexpr int kLangCount = 2;

    inline char const* langName(int i) {
        return i == LangIndo ? "Bahasa Indonesia" : "English";
    }

    inline char const* tr(char const* en, char const* id) {
        return State::get().lang == LangIndo ? id : en;
    }

    template <class... Args>
    std::string trf(char const* en, char const* id, Args&&... args) {
        return fmt::format(fmt::runtime(tr(en, id)), std::forward<Args>(args)...);
    }
}
