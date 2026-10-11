#pragma once
#include <string>

class GJBaseGameLayer;

namespace xenia::bot {
    struct Stats {
        int fps = 0;
        int tps = 0;
        int cps = 0;
        int clicks = 0;
        int frame = 0;
        bool p1 = false;
        bool p2 = false;
    };

    Stats const& stats();
    int currentFrame();
    void onReset(int progress, int levelId, std::string const& levelName);
    void onModeChanged();
    void markSaved();
    void flush();
    void commitIfIdle();
    void commitLevelComplete();
    void resetRuntime();
    void requestSync();
    std::string statusLine();
    double effectiveTps();
    void syncCbf();
    void restoreCbf();
    void injectButton(GJBaseGameLayer* layer, bool down, int button, bool player1, bool record = false);
}
