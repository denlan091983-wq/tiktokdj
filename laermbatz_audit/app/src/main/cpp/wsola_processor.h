#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <limits>

namespace laermbatz {

struct StereoSample {
    float left = 0.0f;
    float right = 0.0f;
};

/**
 * Small real-time WSOLA time stretcher. Input samples remain immutable; the output hop buffer and
 * all search state are fixed-size members, so next() performs no heap allocation or locking.
 * `sourceFramesPerOutputFrame` includes both sample-rate conversion and the requested tempo ratio.
 */
class WsolaProcessor final {
public:
    static constexpr int kMaxOutputHopFrames = 2048;
    static constexpr int kMaxOverlapFrames = 2048;
    static constexpr uint64_t kMinimumWsolaLoopFrames = 512;

    void reset() noexcept {
        configured_ = false;
        fallback_ = false;
        outputIndex_ = 0;
        previousStart_ = 0;
        idealSourceStart_ = 0.0;
        fallbackPosition_ = 0.0;
        lastOutput_ = {};
    }

    StereoSample lastSample() const noexcept { return lastOutput_; }

    StereoSample next(const float *samples,
                      uint64_t frames,
                      int channels,
                      int sourceRate,
                      int outputRate,
                      double sourceFramesPerOutputFrame) noexcept {
        if (samples == nullptr || frames == 0 || (channels != 1 && channels != 2) ||
            sourceRate <= 0 || outputRate <= 0) {
            lastOutput_ = {};
            return lastOutput_;
        }
        if (!configured_ || samples != samples_ || frames != frames_ || channels != channels_ ||
            sourceRate != sourceRate_ || outputRate != outputRate_) {
            configure(samples, frames, channels, sourceRate, outputRate);
        }

        const double step = std::isfinite(sourceFramesPerOutputFrame) &&
                                    sourceFramesPerOutputFrame > 0.0
                                ? sourceFramesPerOutputFrame
                                : static_cast<double>(sourceRate_) / outputRate_;
        if (fallback_) {
            lastOutput_ = interpolate(fallbackPosition_);
            fallbackPosition_ = wrapPosition(fallbackPosition_ + step);
            return lastOutput_;
        }

        if (outputIndex_ >= outputHopFrames_) generateNextHop(step);
        const size_t offset = static_cast<size_t>(outputIndex_) * 2;
        lastOutput_ = {outputBuffer_[offset], outputBuffer_[offset + 1]};
        ++outputIndex_;
        return lastOutput_;
    }

private:
    void configure(const float *samples,
                   uint64_t frames,
                   int channels,
                   int sourceRate,
                   int outputRate) noexcept {
        samples_ = samples;
        frames_ = frames;
        channels_ = channels;
        sourceRate_ = sourceRate;
        outputRate_ = outputRate;
        outputHopFrames_ = std::clamp(outputRate / 100, 32, kMaxOutputHopFrames);
        overlapFrames_ = std::clamp(sourceRate / 100, 16, kMaxOverlapFrames);
        overlapFrames_ = static_cast<int>(std::min<uint64_t>(
            static_cast<uint64_t>(overlapFrames_), frames / 2));
        const uint64_t minimumWindowFrames = std::max<uint64_t>(
            kMinimumWsolaLoopFrames, static_cast<uint64_t>((sourceRate + 49) / 50));
        fallback_ = frames < minimumWindowFrames || overlapFrames_ < 64;
        fallbackPosition_ = 0.0;
        previousStart_ = 0;
        idealSourceStart_ = 0.0;
        outputIndex_ = 0;
        configured_ = true;

        if (!fallback_) {
            // Start with one natural-speed input hop; subsequent hops use overlap search.
            for (int frame = 0; frame < outputHopFrames_; ++frame) {
                const double sourceOffset = static_cast<double>(frame) * overlapFrames_ /
                                            outputHopFrames_;
                const StereoSample sample = interpolate(sourceOffset);
                const size_t offset = static_cast<size_t>(frame) * 2;
                outputBuffer_[offset] = sample.left;
                outputBuffer_[offset + 1] = sample.right;
            }
        }
    }

    void generateNextHop(double sourceFramesPerOutputFrame) noexcept {
        const double requestedAdvance = outputHopFrames_ * sourceFramesPerOutputFrame;
        const double expectedStart = wrapPosition(idealSourceStart_ + requestedAdvance);
        const uint64_t base = static_cast<uint64_t>(std::llround(expectedStart)) % frames_;

        const int tempoOffset = static_cast<int>(std::min<double>(
            std::abs(requestedAdvance - overlapFrames_),
            static_cast<double>(std::numeric_limits<int>::max() / 4)));
        const int nominalSearch = std::max(1, sourceRate_ / 200);  // approximately 5 ms
        const int searchRadius = std::min(overlapFrames_ / 2,
                                          nominalSearch + tempoOffset / 2);
        const int coarseStep = std::max(1, searchRadius / 24);
        const int correlationStride = std::max(1, overlapFrames_ / 128);

        uint64_t bestStart = base;
        double bestScore = -std::numeric_limits<double>::infinity();
        for (int delta = -searchRadius; delta <= searchRadius; delta += coarseStep) {
            const uint64_t candidate = wrapFrame(static_cast<int64_t>(base) + delta);
            const double score = correlation(candidate, correlationStride);
            if (score > bestScore) {
                bestScore = score;
                bestStart = candidate;
            }
        }
        // Refine around the coarse winner to sample-level precision without a large search cost.
        const uint64_t refineCenter = bestStart;
        for (int delta = -coarseStep; delta <= coarseStep; ++delta) {
            const uint64_t candidate = wrapFrame(static_cast<int64_t>(refineCenter) + delta);
            const double score = correlation(candidate, correlationStride);
            if (score > bestScore) {
                bestScore = score;
                bestStart = candidate;
            }
        }

        for (int frame = 0; frame < outputHopFrames_; ++frame) {
            const double sourceOffset = static_cast<double>(frame) * overlapFrames_ /
                                        outputHopFrames_;
            const StereoSample oldTail = interpolate(
                static_cast<double>(previousStart_) + overlapFrames_ + sourceOffset);
            const StereoSample newHead = interpolate(static_cast<double>(bestStart) + sourceOffset);
            const float blend = outputHopFrames_ > 1
                ? static_cast<float>(frame) / static_cast<float>(outputHopFrames_ - 1)
                : 1.0f;
            const size_t offset = static_cast<size_t>(frame) * 2;
            outputBuffer_[offset] = oldTail.left + (newHead.left - oldTail.left) * blend;
            outputBuffer_[offset + 1] = oldTail.right + (newHead.right - oldTail.right) * blend;
        }

        previousStart_ = bestStart;
        idealSourceStart_ = expectedStart;
        outputIndex_ = 0;
    }

    double correlation(uint64_t candidateStart, int stride) const noexcept {
        double cross = 0.0;
        double leftEnergy = 0.0;
        double rightEnergy = 0.0;
        uint64_t aFrame = wrapFrame(static_cast<int64_t>(previousStart_) + overlapFrames_);
        uint64_t bFrame = candidateStart;
        for (int offset = 0; offset < overlapFrames_; offset += stride) {
            for (int channel = 0; channel < channels_; ++channel) {
                const float a = samples_[static_cast<size_t>(aFrame) * channels_ + channel];
                const float b = samples_[static_cast<size_t>(bFrame) * channels_ + channel];
                cross += static_cast<double>(a) * b;
                leftEnergy += static_cast<double>(a) * a;
                rightEnergy += static_cast<double>(b) * b;
            }
            aFrame += static_cast<uint64_t>(stride);
            bFrame += static_cast<uint64_t>(stride);
            if (aFrame >= frames_) aFrame -= frames_;
            if (bFrame >= frames_) bFrame -= frames_;
        }
        const double denominator = std::sqrt(leftEnergy * rightEnergy);
        return denominator > 1e-12 ? cross / denominator : 0.0;
    }

    StereoSample interpolate(double position) const noexcept {
        const double wrapped = wrapPosition(position);
        const uint64_t first = static_cast<uint64_t>(wrapped);
        const uint64_t second = first + 1 < frames_ ? first + 1 : 0;
        const float fraction = static_cast<float>(wrapped - first);
        const size_t firstOffset = static_cast<size_t>(first) * channels_;
        const size_t secondOffset = static_cast<size_t>(second) * channels_;
        if (channels_ == 1) {
            const float a = samples_[firstOffset];
            const float b = samples_[secondOffset];
            const float value = a + (b - a) * fraction;
            return {value, value};
        }
        const float leftA = samples_[firstOffset];
        const float rightA = samples_[firstOffset + 1];
        const float leftB = samples_[secondOffset];
        const float rightB = samples_[secondOffset + 1];
        return {leftA + (leftB - leftA) * fraction,
                rightA + (rightB - rightA) * fraction};
    }

    double wrapPosition(double position) const noexcept {
        const double length = static_cast<double>(frames_);
        double wrapped = position;
        if (wrapped >= length) wrapped -= length;
        if (wrapped >= length || wrapped < 0.0) {
            wrapped = std::fmod(wrapped, length);
            if (wrapped < 0.0) wrapped += length;
        }
        return wrapped;
    }

    uint64_t wrapFrame(int64_t frame) const noexcept {
        const int64_t length = static_cast<int64_t>(frames_);
        int64_t wrapped = frame % length;
        if (wrapped < 0) wrapped += length;
        return static_cast<uint64_t>(wrapped);
    }

    const float *samples_ = nullptr;
    uint64_t frames_ = 0;
    int channels_ = 0;
    int sourceRate_ = 0;
    int outputRate_ = 0;
    int outputHopFrames_ = 0;
    int overlapFrames_ = 0;
    int outputIndex_ = 0;
    uint64_t previousStart_ = 0;
    double idealSourceStart_ = 0.0;
    double fallbackPosition_ = 0.0;
    bool configured_ = false;
    bool fallback_ = false;
    StereoSample lastOutput_{};
    std::array<float, static_cast<size_t>(kMaxOutputHopFrames) * 2> outputBuffer_{};
};

}  // namespace laermbatz
