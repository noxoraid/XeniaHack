#include <Geode/Geode.hpp>
#include "xenia/core/State.hpp"
#include "xenia/bot/Macro.hpp"

using namespace geode::prelude;

$on_mod(Loaded) {
    auto& state = xenia::State::get();
    state.load();

    if (!state.macroName.empty()) {
        if (auto res = xenia::bot::MacroManager::load(state.macroName); !res.ok()) {
            log::warn("Default macro '{}' could not be loaded: {}", state.macroName, res.unwrapErr());
        }
    }
}
