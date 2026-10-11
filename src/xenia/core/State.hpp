#pragma once
#include <Geode/Geode.hpp>

namespace xenia {
    enum class BotMode { Disable = 0, Record = 1, Playback = 2 };
    enum StatBit {
        StatFps = 1, StatTps = 2, StatFrame = 4, StatInput = 8, StatCps = 16,
        StatClicks = 32, StatPos = 64, StatYVel = 128, StatVehicle = 256, StatBot = 512
    };
    constexpr int kStatCount = 10;

    struct State {
        bool noclip = false;
        bool speedhack = false;
        double speed = 1.0;
        double botSpeed = 1.0;
        BotMode bot = BotMode::Disable;
        int theme = 0;
        int uiMode = 0;
        int lang = 0;
        std::string tableOrder;
        int tableCollapsed = 0;
        double overlayX = -1, overlayY = -1;

        float uiScale = 1.f;
        float uiOpacity = 1.f;
        float textScale = 1.f;
        float logoSize = 24.f;
        float clockSize = 30.f;
        float stepSize = 30.f;
        int fontIdx = 0;

        bool safeMode = true;
        bool tpsBypass = false;
        double tps = 240.0;
        bool cbfBypass = true;
        bool particleFixed = true;
        bool speedAudio = true;
        bool tainted = false;

        std::string macroName;
        bool showStats = true;

        bool clockOn = false;
        bool botClockOn = false;
        bool paused = false;
        bool stepReq = false;

        bool straightShip = false;
        bool straightWave = false;
        bool shipClick = true;
        bool waveClick = true;
        double shipCps = 12.0;
        double waveCps = 12.0;

        bool autoClick = false;
        int acMode = 0;
        int acHold = 2;
        int acRelease = 2;
        double acHoldCps = 30.0;
        double acReleaseCps = 30.0;

        bool waveSlide = false;
        bool slideReal = true;
        double slideCps = 12.0;

        // Click indicator (diambil dari .nxr)
        bool clickInd = false;
        int ciMode = 0;                       // 0 = Line (jalur), 1 = Follow Zone
        bool ciLadder = true;                 // tangga di kiri layar
        bool ciPerfectOn = true;
        int ciRung[3] = {240, 240, 240};      // anak tangga = klik
        int ciBar[3] = {230, 50, 50};         // bar hit di dasar tangga
        int ciArrow[3] = {60, 230, 110};      // chevron > < di sisi bar
        int ciPath[3] = {0, 255, 90};         // garis jalur (Line) + kotak klik
        int ciZone[3] = {255, 255, 255};      // zone (Follow Zone)
        int ciPerfect[3] = {80, 255, 120};    // tulisan Perfect
        double ciX = 60.0, ciY = 44.0;        // posisi tangga (kiri-bawah)
        double ciLen = 150.0, ciW = 40.0;     // panjang & lebar tangga
        double ciAhead = 0.8;                 // detik klik terlihat di tangga sebelum kena
        double ciReach = 1.2;                 // detik jalur/zone terlihat di depan player
        double ciThick = 3.0;                 // tebal garis jalur
        double ciOpacity = 0.9;
        double ciTextSize = 0.45;

        int statsMask = StatFps | StatTps | StatFrame | StatInput | StatBot;

        bool noclipOn() const { return noclip && !safeMode; }
        bool speedOn() const { return speedhack && !safeMode; }
        bool tpsOn() const { return tpsBypass && !safeMode; }
        bool botOn() const { return bot != BotMode::Disable && !safeMode; }

        bool recording() const { return bot == BotMode::Record && !safeMode; }
        bool stepperVisible() const { return recording(); }
        bool clockVisible() const { return recording() || (speedOn() && !botOn()); }

        bool clockActive() const { return recording() ? botClockOn : (speedOn() && !botOn() && clockOn); }

        double speedFactor() const {
            if (recording()) return botClockOn ? botSpeed : 1.0;
            if (botOn()) return 1.0;
            return (speedOn() && clockOn) ? speed : 1.0;
        }

        bool straightShipOn() const { return straightShip && !safeMode; }
        bool straightWaveOn() const { return straightWave && !safeMode; }
        bool autoClickOn() const { return autoClick && !safeMode && bot != BotMode::Playback; }
        bool waveSlideOn() const { return waveSlide && !safeMode; }

        static constexpr double kMinTps = 1.0;
        static constexpr double kMaxTps = 1e12;
        static constexpr double kMinSpeed = 0.1;
        static constexpr double kMaxSpeed = 1000.0;
        static constexpr double kMinCps = 1.0;
        static constexpr double kMaxCps = 120.0;
        static constexpr int kMaxTicks = 1000;
        static constexpr double kMinRate = 0.01;
        static constexpr float kMinHud = 20.f;
        static constexpr float kMaxHud = 64.f;

        bool cheating() const { return noclipOn() || speedOn() || botOn() || tpsOn() || straightShipOn() || straightWaveOn() || autoClickOn() || waveSlideOn(); }

        static State& get();
        void load();
        void save();
    };
}
