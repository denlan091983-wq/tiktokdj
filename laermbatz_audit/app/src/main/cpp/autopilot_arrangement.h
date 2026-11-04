#pragma once

#include <algorithm>
#include <array>
#include <cmath>

namespace laermbatz {

constexpr int kAutopilotTracks = 6;
constexpr int kAutopilotSections = 8;
constexpr double kAutopilotBars = 64.0;

struct AutopilotTrackTarget {
    float gain = 0.0f;
    float cutoffHz = 20000.0f;
    float delayScale = 0.0f;
    float reverbScale = 0.0f;
    int automaticDelayDivision = 0;
};

struct AutopilotArrangement {
    int section = 0;
    float sectionProgress = 0.0f;
    std::array<AutopilotTrackTarget, kAutopilotTracks> tracks{};
};

inline float lerpAutopilot(float from, float to, float amount) noexcept {
    return from + (to - from) * amount;
}

/**
 * Deterministic 64-bar performance map. Each row is the target at the start of an
 * eight-bar section; smoothstep interpolation reaches the next row exactly at its
 * boundary, avoiding stepped level/filter automation. FX send scales are ceilings
 * applied to the user's per-track sends; division 0 means "automatic" in Auto mode.
 */
inline AutopilotArrangement autopilotArrangementAt(double barPosition) noexcept {
    static constexpr std::array<std::array<float, kAutopilotTracks>, kAutopilotSections> gains{{
        {{0.78f, 0.05f, 0.28f, 0.00f, 0.00f, 0.00f}},  // Intro
        {{0.90f, 0.60f, 0.55f, 0.20f, 0.00f, 0.05f}},  // Groove A
        {{0.94f, 0.78f, 0.70f, 0.55f, 0.25f, 0.12f}},  // Build
        {{1.00f, 0.94f, 0.75f, 0.86f, 0.80f, 0.22f}},  // Drop A
        {{0.16f, 0.05f, 0.24f, 0.12f, 0.44f, 0.55f}},  // Break
        {{0.75f, 0.56f, 0.76f, 0.63f, 0.70f, 0.25f}},  // Build B
        {{1.00f, 0.98f, 0.82f, 0.92f, 0.98f, 0.30f}},  // Drop B
        {{0.82f, 0.68f, 0.58f, 0.48f, 0.35f, 0.12f}}   // Release / return
    }};
    static constexpr std::array<std::array<float, kAutopilotTracks>, kAutopilotSections> cutoffs{{
        {{7000.0f, 5000.0f, 8000.0f, 7000.0f, 5500.0f, 9000.0f}},
        {{13500.0f, 9500.0f, 12500.0f, 11500.0f, 11000.0f, 12000.0f}},
        {{16000.0f, 12500.0f, 15000.0f, 14000.0f, 13000.0f, 16000.0f}},
        {{20000.0f, 17500.0f, 18500.0f, 18000.0f, 20000.0f, 18000.0f}},
        {{4500.0f, 3000.0f, 5000.0f, 5000.0f, 11000.0f, 14000.0f}},
        {{14000.0f, 10000.0f, 16000.0f, 15000.0f, 14000.0f, 17000.0f}},
        {{20000.0f, 18000.0f, 19000.0f, 18500.0f, 20000.0f, 19000.0f}},
        {{16000.0f, 13000.0f, 14500.0f, 13500.0f, 13000.0f, 15000.0f}}
    }};
    static constexpr std::array<float, kAutopilotSections> delayScales{
        0.0f, 0.25f, 0.45f, 0.30f, 0.70f, 0.50f, 0.35f, 0.20f};
    static constexpr std::array<float, kAutopilotSections> reverbScales{
        0.08f, 0.15f, 0.28f, 0.20f, 0.80f, 0.42f, 0.22f, 0.12f};
    static constexpr std::array<int, kAutopilotSections> delayDivisions{
        0, 1, 1, 2, 3, 1, 2, 1};

    double cyclePosition = std::fmod(barPosition, kAutopilotBars);
    if (cyclePosition < 0.0) cyclePosition += kAutopilotBars;
    const double barsIntoSection = std::fmod(cyclePosition, 8.0);
    const int section = std::clamp(static_cast<int>(cyclePosition / 8.0), 0,
                                   kAutopilotSections - 1);
    const int nextSection = (section + 1) % kAutopilotSections;
    const float linearProgress = static_cast<float>(barsIntoSection / 8.0);
    const float smoothProgress = linearProgress * linearProgress * (3.0f - 2.0f * linearProgress);

    AutopilotArrangement result;
    result.section = section;
    result.sectionProgress = smoothProgress;
    for (int track = 0; track < kAutopilotTracks; ++track) {
        auto &target = result.tracks[track];
        target.gain = lerpAutopilot(gains[section][track], gains[nextSection][track], smoothProgress);
        target.cutoffHz = lerpAutopilot(cutoffs[section][track], cutoffs[nextSection][track], smoothProgress);
        target.delayScale = lerpAutopilot(delayScales[section], delayScales[nextSection], smoothProgress);
        target.reverbScale = lerpAutopilot(reverbScales[section], reverbScales[nextSection], smoothProgress);
        target.automaticDelayDivision = delayDivisions[section];
    }
    return result;
}

}  // namespace laermbatz
