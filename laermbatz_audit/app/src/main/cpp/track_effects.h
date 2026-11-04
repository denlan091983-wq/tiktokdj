#pragma once

#include "wsola_processor.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <new>
#include <vector>

namespace laermbatz {

/**
 * Per-track realtime effects. Delay storage is allocated in prepare() on the control thread;
 * process() uses fixed-size reverb combs, performs no allocation, and takes no locks.
 */
class TrackEffectProcessor final {
public:
    static constexpr int kCombCount = 4;
    static constexpr int kMaxCombFrames = 9000;

    bool prepare(int sampleRate) noexcept {
        if (sampleRate < 8000 || sampleRate > 192000) return false;
        try {
            delayLine_.assign(static_cast<size_t>(sampleRate) * 2 + 1, 0.0f);
        } catch (const std::bad_alloc &) {
            delayLine_.clear();
            return false;
        }
        sampleRate_ = sampleRate;
        constexpr std::array<float, kCombCount> delayMilliseconds{
            29.7f, 37.1f, 41.1f, 43.7f};
        for (int i = 0; i < kCombCount; ++i) {
            combLength_[i] = std::clamp(
                static_cast<int>(sampleRate * delayMilliseconds[i] / 1000.0f),
                1, kMaxCombFrames);
        }
        reset();
        return true;
    }

    void reset() noexcept {
        std::fill(delayLine_.begin(), delayLine_.end(), 0.0f);
        delayWriteIndex_ = 0;
        delayFramesCurrent_ = 1.0f;
        delayFramesTarget_ = 1;
        delayFramesRampRemaining_ = 0;
        lowPassLeft_ = 0.0f;
        lowPassRight_ = 0.0f;
        delayMixCurrent_ = 0.0f;
        feedbackCurrent_ = 0.0f;
        reverbSendCurrent_ = 0.0f;
        for (int i = 0; i < kCombCount; ++i) {
            combLeft_[i].fill(0.0f);
            combRight_[i].fill(0.0f);
            combIndex_[i] = 0;
            dampedLeft_[i] = 0.0f;
            dampedRight_[i] = 0.0f;
        }
    }

    void setParameters(float cutoffHz,
                       int delayDivision,
                       float delayMix,
                       float delayFeedback,
                       float reverbSend,
                       float bpm) noexcept {
        if (sampleRate_ <= 0) return;
        const float nyquistSafeCutoff = sampleRate_ * 0.45f;
        const float cutoff = std::clamp(cutoffHz, 200.0f, nyquistSafeCutoff);
        filterBypass_ = cutoff >= sampleRate_ * 0.40f;
        lowPassAlpha_ = 1.0f - std::exp(-2.0f * kPi * cutoff / sampleRate_);

        const int division = std::clamp(delayDivision, 0, 3);
        constexpr std::array<float, 4> beatsPerDelay{0.0f, 0.5f, 1.0f, 2.0f};
        int newDelayFrames = 1;
        if (division > 0 && !delayLine_.empty()) {
            const float safeBpm = std::clamp(bpm, 60.0f, 180.0f);
            const float frames = sampleRate_ * 60.0f / safeBpm * beatsPerDelay[division];
            newDelayFrames = std::clamp(static_cast<int>(std::lround(frames)),
                                        1, static_cast<int>(delayLine_.size()) - 1);
        }
        if (newDelayFrames != delayFramesTarget_) {
            delayFramesTarget_ = newDelayFrames;
            delayFramesRampRemaining_ = std::max(1, sampleRate_ / 20);
        }
        delayMixTarget_ = division == 0 ? 0.0f : std::clamp(delayMix, 0.0f, 0.8f);
        feedbackTarget_ = division == 0 ? 0.0f : std::clamp(delayFeedback, 0.0f, 0.8f);
        reverbSendTarget_ = std::clamp(reverbSend, 0.0f, 0.8f);
    }

    StereoSample process(StereoSample input) noexcept {
        if (sampleRate_ <= 0) return input;
        input.left = std::isfinite(input.left) ? std::clamp(input.left, -1.0f, 1.0f) : 0.0f;
        input.right = std::isfinite(input.right) ? std::clamp(input.right, -1.0f, 1.0f) : 0.0f;
        lowPassLeft_ += lowPassAlpha_ * (input.left - lowPassLeft_);
        lowPassRight_ += lowPassAlpha_ * (input.right - lowPassRight_);
        float left = filterBypass_ ? input.left : lowPassLeft_;
        float right = filterBypass_ ? input.right : lowPassRight_;

        const float ramp = 1.0f / std::max(1.0f, sampleRate_ * 0.020f);
        approach(delayMixCurrent_, delayMixTarget_, ramp);
        approach(feedbackCurrent_, feedbackTarget_, ramp);
        approach(reverbSendCurrent_, reverbSendTarget_, ramp);
        if (delayFramesRampRemaining_ > 0) {
            delayFramesCurrent_ += (delayFramesTarget_ - delayFramesCurrent_) /
                                   static_cast<float>(delayFramesRampRemaining_);
            --delayFramesRampRemaining_;
        }

        float delayed = 0.0f;
        if (!delayLine_.empty()) {
            const int delayFrames = std::clamp(static_cast<int>(std::lround(delayFramesCurrent_)),
                                               1, static_cast<int>(delayLine_.size()) - 1);
            const size_t delaySamples = static_cast<size_t>(delayFrames);
            const size_t readIndex = delayWriteIndex_ >= delaySamples
                ? delayWriteIndex_ - delaySamples
                : delayWriteIndex_ + delayLine_.size() - delaySamples;
            delayed = delayLine_[readIndex];
            const float mono = (left + right) * 0.5f;
            delayLine_[delayWriteIndex_] = std::clamp(
                mono + delayed * feedbackCurrent_, -2.0f, 2.0f);
            if (++delayWriteIndex_ == delayLine_.size()) delayWriteIndex_ = 0;
            left += delayed * delayMixCurrent_;
            right += delayed * delayMixCurrent_;
        }

        const StereoSample reverb = processReverb(left, right);
        const float outputLeft = left + reverb.left;
        const float outputRight = right + reverb.right;
        return {
            std::isfinite(outputLeft) ? outputLeft : 0.0f,
            std::isfinite(outputRight) ? outputRight : 0.0f,
        };
    }

private:
    static constexpr float kPi = 3.14159265358979323846f;
    static constexpr float kCombFeedback = 0.74f;
    static constexpr float kDamping = 0.22f;

    static void approach(float &value, float target, float step) noexcept {
        if (value < target) value = std::min(value + step, target);
        else if (value > target) value = std::max(value - step, target);
    }

    StereoSample processReverb(float left, float right) noexcept {
        float wetLeft = 0.0f;
        float wetRight = 0.0f;
        for (int i = 0; i < kCombCount; ++i) {
            const int index = combIndex_[i];
            const float delayedLeft = combLeft_[i][index];
            const float delayedRight = combRight_[i][index];
            dampedLeft_[i] += kDamping * (delayedLeft - dampedLeft_[i]);
            dampedRight_[i] += kDamping * (delayedRight - dampedRight_[i]);
            combLeft_[i][index] = left * reverbSendCurrent_ + dampedLeft_[i] * kCombFeedback;
            combRight_[i][index] = right * reverbSendCurrent_ + dampedRight_[i] * kCombFeedback;
            wetLeft += delayedLeft;
            wetRight += delayedRight;
            if (++combIndex_[i] == combLength_[i]) combIndex_[i] = 0;
        }
        const float scale = 1.0f / kCombCount;
        return {wetLeft * scale, wetRight * scale};
    }

    int sampleRate_ = 0;
    std::vector<float> delayLine_;
    size_t delayWriteIndex_ = 0;
    int delayFramesTarget_ = 1;
    int delayFramesRampRemaining_ = 0;
    float delayFramesCurrent_ = 1.0f;
    float delayMixTarget_ = 0.0f;
    float delayMixCurrent_ = 0.0f;
    float feedbackTarget_ = 0.0f;
    float feedbackCurrent_ = 0.0f;
    float reverbSendTarget_ = 0.0f;
    float reverbSendCurrent_ = 0.0f;
    float lowPassAlpha_ = 1.0f;
    float lowPassLeft_ = 0.0f;
    float lowPassRight_ = 0.0f;
    bool filterBypass_ = true;
    std::array<int, kCombCount> combLength_{};
    std::array<int, kCombCount> combIndex_{};
    std::array<float, kCombCount> dampedLeft_{};
    std::array<float, kCombCount> dampedRight_{};
    std::array<std::array<float, kMaxCombFrames>, kCombCount> combLeft_{};
    std::array<std::array<float, kMaxCombFrames>, kCombCount> combRight_{};
};

}  // namespace laermbatz
