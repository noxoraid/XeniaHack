#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <unordered_map>
#include <unordered_set>
#include "xenia/core/State.hpp"

using namespace geode::prelude;

namespace {
    struct PlayerSnapshot {
        double yVelocity = 0.0;
        double yVelocityBeforeSlope = 0.0;
        double yVelocityRelated = 0.0;
        float yVelocityRelated3 = 0.f;
        float xVelocityRelated = 0.f;
        float xVelocityRelated2 = 0.f;
        double platformerXVelocity = 0.0;
        float slopeAngle = 0.f;
        float slopeAngleRadians = 0.f;
        double slopeRotation = 0.0;
        bool isOnSlope = false;
        bool wasOnSlope = false;
        bool isOnGround = false;
        int stateOnGround = 0;
        uint8_t stateNoStickX = 0;
        bool isSliding = false;
        bool isSlidingRight = false;
        bool isRotating = false;
        float rotationSpeed = 0.f;
        double lastJumpTime = 0.0;
        bool jumpBuffered = false;
        bool touchedRing = false;
        bool ringJumpRelated = false;
        std::unordered_set<int> touchedRings;
        bool valid = false;

        void capture(PlayerObject* player) {
            if (!player) return;
            yVelocity = player->m_yVelocity;
            yVelocityBeforeSlope = player->m_yVelocityBeforeSlope;
            yVelocityRelated = player->m_yVelocityRelated;
            yVelocityRelated3 = player->m_yVelocityRelated3;
            xVelocityRelated = player->m_xVelocityRelated;
            xVelocityRelated2 = player->m_xVelocityRelated2;
            platformerXVelocity = player->m_platformerXVelocity;
            slopeAngle = player->m_slopeAngle;
            slopeAngleRadians = player->m_slopeAngleRadians;
            slopeRotation = player->m_slopeRotation;
            isOnSlope = player->m_isOnSlope;
            wasOnSlope = player->m_wasOnSlope;
            isOnGround = player->m_isOnGround;
            stateOnGround = player->m_stateOnGround;
            stateNoStickX = player->m_stateNoStickX;
            isSliding = player->m_isSliding;
            isSlidingRight = player->m_isSlidingRight;
            isRotating = player->m_isRotating;
            rotationSpeed = player->m_rotationSpeed;
            lastJumpTime = player->m_lastJumpTime;
            jumpBuffered = player->m_jumpBuffered;
            touchedRing = player->m_touchedRing;
            ringJumpRelated = player->m_ringJumpRelated;
            touchedRings.clear();
            for (auto const r : player->m_touchedRings) {
                touchedRings.insert(r);
            }
            valid = true;
        }

        void restore(PlayerObject* player) const {
            if (!player || !valid) return;
            player->m_yVelocity = yVelocity;
            player->m_yVelocityBeforeSlope = yVelocityBeforeSlope;
            player->m_yVelocityRelated = yVelocityRelated;
            player->m_yVelocityRelated3 = yVelocityRelated3;
            player->m_xVelocityRelated = xVelocityRelated;
            player->m_xVelocityRelated2 = xVelocityRelated2;
            player->m_platformerXVelocity = platformerXVelocity;
            player->m_slopeAngle = slopeAngle;
            player->m_slopeAngleRadians = slopeAngleRadians;
            player->m_slopeRotation = slopeRotation;
            player->m_isOnSlope = isOnSlope;
            player->m_wasOnSlope = wasOnSlope;
            player->m_isOnGround = isOnGround;
            player->m_stateOnGround = stateOnGround;
            player->m_stateNoStickX = stateNoStickX;
            player->m_isSliding = isSliding;
            player->m_isSlidingRight = isSlidingRight;
            player->m_isRotating = isRotating;
            player->m_rotationSpeed = rotationSpeed;
            player->m_lastJumpTime = lastJumpTime;
            player->m_jumpBuffered = jumpBuffered;
            player->m_touchedRing = touchedRing;
            player->m_ringJumpRelated = ringJumpRelated;
            player->m_touchedRings.clear();
            for (auto const r : touchedRings) {
                player->m_touchedRings.insert(r);
            }
        }
    };

    struct CheckpointData {
        PlayerSnapshot p1;
        PlayerSnapshot p2;
        int progress = -1;
    };
}

class $modify(XeniaPracticeFix, PlayLayer) {
    struct Fields {
        std::unordered_map<CheckpointObject*, CheckpointData> m_checkpoints;
    };

    CheckpointObject* createCheckpoint() {
        auto* cp = PlayLayer::createCheckpoint();
        if (cp && xenia::State::get().botOn()) {
            auto& data = m_fields->m_checkpoints[cp];
            data.p1.capture(m_player1);
            data.p2.capture(m_player2);
            data.progress = static_cast<int>(m_gameState.m_currentProgress);
        }
        return cp;
    }

    void loadFromCheckpoint(CheckpointObject* cp) {
        PlayLayer::loadFromCheckpoint(cp);
        if (!cp || !xenia::State::get().botOn()) return;

        auto const it = m_fields->m_checkpoints.find(cp);
        if (it == m_fields->m_checkpoints.end()) return;

        it->second.p1.restore(m_player1);
        it->second.p2.restore(m_player2);
        if (it->second.progress >= 0) {
            m_gameState.m_currentProgress = it->second.progress;
        }
    }
};
