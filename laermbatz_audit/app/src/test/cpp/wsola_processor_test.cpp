#include "../../main/cpp/wsola_processor.h"
#include "../../main/cpp/track_effects.h"
#include "../../main/cpp/autopilot_arrangement.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <vector>

namespace {
constexpr int kSourceRate = 44100;
constexpr int kOutputRate = 48000;
constexpr double kFrequency = 440.0;
constexpr double kPi = 3.14159265358979323846;

bool preservesPitch(double tempoRatio, int channels) {
    const uint64_t sourceFrames = static_cast<uint64_t>(kSourceRate) * 2;
    std::vector<float> input(sourceFrames * static_cast<uint64_t>(channels));
    for (uint64_t frame = 0; frame < sourceFrames; ++frame) {
        for (int channel = 0; channel < channels; ++channel) {
            const double phase = channel == 0 ? 0.0 : 0.17;
            input[static_cast<size_t>(frame) * channels + channel] = static_cast<float>(
                0.7 * std::sin(2.0 * kPi * kFrequency * static_cast<double>(frame) /
                               kSourceRate + phase));
        }
    }

    laermbatz::WsolaProcessor processor;
    const double sourceFramesPerOutputFrame =
        static_cast<double>(kSourceRate) / kOutputRate * tempoRatio;
    constexpr int kWarmupFrames = 6000;
    constexpr int kMeasuredFrames = kOutputRate * 2;
    int risingCrossings = 0;
    float previous = 0.0f;
    double squareSum = 0.0;

    for (int frame = 0; frame < kWarmupFrames + kMeasuredFrames; ++frame) {
        const auto sample = processor.next(input.data(), sourceFrames, channels, kSourceRate,
                                           kOutputRate, sourceFramesPerOutputFrame);
        const float mono = (sample.left + sample.right) * 0.5f;
        if (frame >= kWarmupFrames) {
            if (previous <= 0.0f && mono > 0.0f) ++risingCrossings;
            squareSum += static_cast<double>(mono) * mono;
        }
        previous = mono;
    }

    const double measuredHz = static_cast<double>(risingCrossings) * kOutputRate /
                              kMeasuredFrames;
    const double rms = std::sqrt(squareSum / kMeasuredFrames);
    std::cout << "WSOLA " << channels << "ch tempo ratio " << tempoRatio << ": " << measuredHz
              << " Hz, RMS " << rms << '\n';
    return std::abs(measuredHz - kFrequency) < 5.0 && rms > 0.25;
}

bool preservesTransientSpacing(double tempoRatio) {
    constexpr uint64_t kSourceFrames = static_cast<uint64_t>(kOutputRate) * 4;
    std::vector<float> input(kSourceFrames, 0.0f);
    for (int beat = 0; beat < 8; ++beat) {
        for (int frame = 0; frame < 80; ++frame) {
            input[static_cast<size_t>(beat * (kOutputRate / 2) + frame)] =
                static_cast<float>(0.9 * std::exp(-frame / 12.0));
        }
    }

    laermbatz::WsolaProcessor processor;
    const int expectedBeatFrames = static_cast<int>(std::lround(
        static_cast<double>(kOutputRate / 2) / tempoRatio));
    constexpr int kOutputFrames = kOutputRate * 6;
    std::vector<float> output(kOutputFrames);
    for (int frame = 0; frame < kOutputFrames; ++frame) {
        output[frame] = processor.next(input.data(), kSourceFrames, 1, kOutputRate,
                                       kOutputRate, tempoRatio).left;
    }

    int maximumBeatError = 0;
    for (int beat = 1; beat <= 8; ++beat) {
        const int expected = beat * expectedBeatFrames;
        float peak = 0.0f;
        int peakFrame = expected;
        const int start = std::max(0, expected - 500);
        const int end = std::min(kOutputFrames, expected + 500);
        for (int frame = start; frame < end; ++frame) {
            if (std::abs(output[frame]) > peak) {
                peak = std::abs(output[frame]);
                peakFrame = frame;
            }
        }
        const int error = std::abs(peakFrame - expected);
        maximumBeatError = std::max(maximumBeatError, error);
        if (peak < 0.30f || error > 400) return false;
    }
    std::cout << "WSOLA percussive ratio " << tempoRatio << ": max beat error "
              << maximumBeatError << " frames\n";
    return true;
}

bool lowPassReducesHighFrequencyEnergy() {
    laermbatz::TrackEffectProcessor processor;
    if (!processor.prepare(kOutputRate)) return false;
    processor.setParameters(1000.0f, 0, 0.0f, 0.0f, 0.0f, 120.0f);
    double squareSum = 0.0;
    constexpr int kFrames = kOutputRate;
    for (int frame = 0; frame < kFrames; ++frame) {
        const float input = 0.7f * static_cast<float>(
            std::sin(2.0 * kPi * 8000.0 * frame / kOutputRate));
        const auto output = processor.process({input, input});
        if (frame > 2000) squareSum += static_cast<double>(output.left) * output.left;
    }
    const double rms = std::sqrt(squareSum / (kFrames - 2001));
    std::cout << "Low-pass 1 kHz on 8 kHz tone: RMS " << rms << '\n';
    return rms < 0.18;
}

bool beatDelayProducesRepeats() {
    laermbatz::TrackEffectProcessor processor;
    if (!processor.prepare(kOutputRate)) return false;
    processor.setParameters(20000.0f, 2, 0.5f, 0.5f, 0.0f, 120.0f);
    for (int frame = 0; frame < 3000; ++frame) processor.process({0.0f, 0.0f});

    float firstEcho = 0.0f;
    float secondEcho = 0.0f;
    constexpr int kFrames = kOutputRate * 2;
    for (int frame = 0; frame < kFrames; ++frame) {
        const float input = frame == 0 ? 1.0f : 0.0f;
        const auto output = processor.process({input, input});
        if (frame >= 23500 && frame <= 24500) firstEcho = std::max(firstEcho, std::abs(output.left));
        if (frame >= 47500 && frame <= 48500) secondEcho = std::max(secondEcho, std::abs(output.left));
    }
    std::cout << "Beat delay first/feedback echo: " << firstEcho << '/' << secondEcho << '\n';
    return firstEcho > 0.25f && secondEcho > 0.10f;
}

bool reverbProducesTail() {
    laermbatz::TrackEffectProcessor processor;
    if (!processor.prepare(kOutputRate)) return false;
    processor.setParameters(20000.0f, 0, 0.0f, 0.0f, 0.7f, 120.0f);
    for (int frame = 0; frame < 3000; ++frame) processor.process({0.0f, 0.0f});
    float maximumTail = 0.0f;
    for (int frame = 0; frame < 5000; ++frame) {
        const float input = frame == 0 ? 1.0f : 0.0f;
        const auto output = processor.process({input, input});
        if (frame > 100) maximumTail = std::max(maximumTail, std::abs(output.left));
    }
    std::cout << "Reverb tail peak: " << maximumTail << '\n';
    return maximumTail > 0.05f;
}

bool effectsRejectNonFiniteAndExtremeInputs() {
    laermbatz::TrackEffectProcessor processor;
    if (!processor.prepare(kOutputRate)) return false;
    processor.setParameters(1000.0f, 2, 0.5f, 0.6f, 0.4f, 120.0f);
    const std::array<laermbatz::StereoSample, 6> inputs{{
        {std::numeric_limits<float>::max(), -std::numeric_limits<float>::max()},
        {-std::numeric_limits<float>::max(), std::numeric_limits<float>::max()},
        {std::numeric_limits<float>::quiet_NaN(), 0.5f},
        {0.25f, std::numeric_limits<float>::infinity()},
        {0.5f, -0.5f},
        {0.0f, 0.0f},
    }};
    for (const auto &input : inputs) {
        const auto output = processor.process(input);
        if (!std::isfinite(output.left) || !std::isfinite(output.right)) return false;
    }
    std::cout << "Effect chain non-finite/extreme input guard: finite output\n";
    return true;
}

bool autopilotArrangementIsBoundedAndContinuous() {
    const auto intro = laermbatz::autopilotArrangementAt(0.0);
    const auto repeatedIntro = laermbatz::autopilotArrangementAt(64.0);
    const auto drop = laermbatz::autopilotArrangementAt(24.0);
    const auto breakScene = laermbatz::autopilotArrangementAt(32.0);
    if (intro.section != 0 || repeatedIntro.section != 0 ||
        std::abs(intro.tracks[0].gain - repeatedIntro.tracks[0].gain) > 1.0e-6f ||
        drop.section != 3 || drop.tracks[0].gain < 0.99f ||
        breakScene.section != 4 || breakScene.tracks[0].gain > 0.2f) {
        return false;
    }

    for (int boundary = 8; boundary <= 64; boundary += 8) {
        const auto before = laermbatz::autopilotArrangementAt(boundary - 0.0001);
        const auto after = laermbatz::autopilotArrangementAt(boundary);
        for (int track = 0; track < laermbatz::kAutopilotTracks; ++track) {
            if (std::abs(before.tracks[track].gain - after.tracks[track].gain) > 0.001f ||
                std::abs(before.tracks[track].cutoffHz - after.tracks[track].cutoffHz) > 10.0f) {
                return false;
            }
        }
    }

    for (double bar = 0.0; bar < 64.0; bar += 0.25) {
        const auto scene = laermbatz::autopilotArrangementAt(bar);
        if (scene.section < 0 || scene.section >= laermbatz::kAutopilotSections ||
            !std::isfinite(scene.sectionProgress) || scene.sectionProgress < 0.0f ||
            scene.sectionProgress > 1.0f) return false;
        for (const auto &track : scene.tracks) {
            if (!std::isfinite(track.gain) || track.gain < 0.0f || track.gain > 1.0f ||
                !std::isfinite(track.cutoffHz) || track.cutoffHz < 200.0f ||
                track.cutoffHz > 20000.0f || track.delayScale < 0.0f ||
                track.delayScale > 1.0f || track.reverbScale < 0.0f ||
                track.reverbScale > 1.0f || track.automaticDelayDivision < 0 ||
                track.automaticDelayDivision > 3) return false;
        }
    }
    std::cout << "Autopilot arrangement: 64-bar cycle, smooth sections, bounded levels/FX\n";
    return true;
}
}  // namespace

int main() {
    const bool fasterMono = preservesPitch(1.25, 1);
    const bool slowerMono = preservesPitch(0.80, 1);
    const bool fasterStereo = preservesPitch(1.25, 2);
    const bool slowerStereo = preservesPitch(0.80, 2);
    const bool fasterTransients = preservesTransientSpacing(1.25);
    const bool slowerTransients = preservesTransientSpacing(0.80);
    const bool lowPass = lowPassReducesHighFrequencyEnergy();
    const bool delay = beatDelayProducesRepeats();
    const bool reverb = reverbProducesTail();
    const bool finiteGuard = effectsRejectNonFiniteAndExtremeInputs();
    const bool arrangement = autopilotArrangementIsBoundedAndContinuous();
    if (!fasterMono || !slowerMono || !fasterStereo || !slowerStereo ||
        !fasterTransients || !slowerTransients || !lowPass || !delay || !reverb ||
        !finiteGuard || !arrangement) {
        std::cerr << "Native DSP/autopilot smoke test failed\n";
        return 1;
    }
    std::cout << "Native DSP/autopilot smoke tests passed\n";
    return 0;
}
