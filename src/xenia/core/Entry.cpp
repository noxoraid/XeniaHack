#include <Geode/Geode.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Macro.hpp"
using namespace geode::prelude;

$on_mod(Loaded) {
    auto& s = xenia::State::get();
    s.load();

    if (!s.macroName.empty()) {
        if (auto err = xenia::bot::MacroManager::load(s.macroName); !err.empty()) {
            log::warn("Macro '{}' was not loaded: {}", s.macroName, err);
        }
    }
}
