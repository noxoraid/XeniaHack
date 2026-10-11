#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <unordered_map>
#include <unordered_set>
#include "xenia/core/State.hpp"
using namespace geode::prelude;

namespace {
    struct PState {
        double yVel = 0, yVelBeforeSlope = 0, yVelRel = 0, platX = 0, slopeRot = 0, lastJump = 0;
        float yVelRel3 = 0, xVelRel = 0, xVelRel2 = 0, slopeAngle = 0, slopeAngleRad = 0, rotSpeed = 0;
        bool onSlope = false, wasOnSlope = false, onGround = false, sliding = false, slidingRight = false;
        bool rotating = false, jumpBuffered = false, touchedRing = false, ringJumpRel = false;
        int stateOnGround = 0;
        unsigned char stateNoStickX = 0;
        std::unordered_set<int> rings;
        bool valid = false;
    };

    struct CpExtra {
        PState p1, p2;
        int progress = -1;
    };

    std::unordered_map<CheckpointObject*, CpExtra> g_cp;

    void capture(PlayerObject* p, PState& s) {
        if (!p) return;
        s.yVel = p->m_yVelocity;
        s.yVelBeforeSlope = p->m_yVelocityBeforeSlope;
        s.yVelRel = p->m_yVelocityRelated;
        s.yVelRel3 = p->m_yVelocityRelated3;
        s.xVelRel = p->m_xVelocityRelated;
        s.xVelRel2 = p->m_xVelocityRelated2;
        s.platX = p->m_platformerXVelocity;
        s.slopeAngle = p->m_slopeAngle;
        s.slopeAngleRad = p->m_slopeAngleRadians;
        s.slopeRot = p->m_slopeRotation;
        s.onSlope = p->m_isOnSlope;
        s.wasOnSlope = p->m_wasOnSlope;
        s.onGround = p->m_isOnGround;
        s.stateOnGround = p->m_stateOnGround;
        s.stateNoStickX = p->m_stateNoStickX;
        s.sliding = p->m_isSliding;
        s.slidingRight = p->m_isSlidingRight;
        s.rotating = p->m_isRotating;
        s.rotSpeed = p->m_rotationSpeed;
        s.lastJump = p->m_lastJumpTime;
        s.jumpBuffered = p->m_jumpBuffered;
        s.touchedRing = p->m_touchedRing;
        s.ringJumpRel = p->m_ringJumpRelated;
        s.rings.clear();
        for (auto v : p->m_touchedRings) s.rings.insert(v);
        s.valid = true;
    }

    void restore(PlayerObject* p, PState const& s) {
        if (!p || !s.valid) return;
        p->m_yVelocity = s.yVel;
        p->m_yVelocityBeforeSlope = s.yVelBeforeSlope;
        p->m_yVelocityRelated = s.yVelRel;
        p->m_yVelocityRelated3 = s.yVelRel3;
        p->m_xVelocityRelated = s.xVelRel;
        p->m_xVelocityRelated2 = s.xVelRel2;
        p->m_platformerXVelocity = s.platX;
        p->m_slopeAngle = s.slopeAngle;
        p->m_slopeAngleRadians = s.slopeAngleRad;
        p->m_slopeRotation = s.slopeRot;
        p->m_isOnSlope = s.onSlope;
        p->m_wasOnSlope = s.wasOnSlope;
        p->m_isOnGround = s.onGround;
        p->m_stateOnGround = s.stateOnGround;
        p->m_stateNoStickX = s.stateNoStickX;
        p->m_isSliding = s.sliding;
        p->m_isSlidingRight = s.slidingRight;
        p->m_isRotating = s.rotating;
        p->m_rotationSpeed = s.rotSpeed;
        p->m_lastJumpTime = s.lastJump;
        p->m_jumpBuffered = s.jumpBuffered;
        p->m_touchedRing = s.touchedRing;
        p->m_ringJumpRelated = s.ringJumpRel;
        p->m_touchedRings.clear();
        for (auto v : s.rings) p->m_touchedRings.insert(v);
    }
}

class $modify(XeniaPracticeFix, PlayLayer) {
    CheckpointObject* createCheckpoint() {
        auto cp = PlayLayer::createCheckpoint();
        if (cp && xenia::State::get().botOn()) {
            if (g_cp.size() > 4000) g_cp.clear();
            auto& e = g_cp[cp];
            e = CpExtra{};
            capture(m_player1, e.p1);
            capture(m_player2, e.p2);
            e.progress = (int)m_gameState.m_currentProgress;
        }
        return cp;
    }

    void loadFromCheckpoint(CheckpointObject* cp) {
        PlayLayer::loadFromCheckpoint(cp);
        if (!cp || !xenia::State::get().botOn()) return;
        auto it = g_cp.find(cp);
        if (it == g_cp.end()) return;
        restore(m_player1, it->second.p1);
        restore(m_player2, it->second.p2);
        if (it->second.progress >= 0 && (int)m_gameState.m_currentProgress != it->second.progress) {
            m_gameState.m_currentProgress = it->second.progress;
        }
    }

    void onQuit() {
        g_cp.clear();
        PlayLayer::onQuit();
    }
};
