// Oboe-Mixer fuer sechs importierte WAV-Loops. Der Echtzeit-Callback
// verwendet keine JNI-Aufrufe, Locks, Logs oder Heap-Allokationen.
#include <jni.h>
#include <oboe/Oboe.h>
#include "autopilot_arrangement.h"
#include "track_effects.h"
#include "wsola_processor.h"
#include "master_recording_ring.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <memory>
#include <mutex>
#include <new>
#include <thread>
#include <vector>

namespace {
using laermbatz::StereoSample;
using laermbatz::TrackEffectProcessor;
using laermbatz::WsolaProcessor;

constexpr int kTrackCount = 6;
constexpr int kMaxBarsPerLoop = 64;
constexpr size_t kMaxInterleavedSamples = 4'000'000;
constexpr float kDefaultBpm = 128.0f;
constexpr float kMasterGain = 0.76f;
constexpr float kRampSeconds = 0.020f;

struct TrackData {
    std::vector<float> samples;
    TrackEffectProcessor effects;
    int sampleRate = 0;
    int channels = 0;
    int bars = 1;
    uint64_t frames = 0;
    uint64_t startBar = 0;
    uint64_t generation = 0;
};

std::array<std::atomic<TrackData *>, kTrackCount> gTracks{
    nullptr, nullptr, nullptr, nullptr, nullptr, nullptr};
std::array<std::atomic<float>, kTrackCount> gTrackGains{
    0.55f, 0.55f, 0.55f, 0.55f, 0.55f, 0.55f};
std::array<std::atomic<float>, kTrackCount> gTrackLowPassHz{
    20000.0f, 20000.0f, 20000.0f, 20000.0f, 20000.0f, 20000.0f};
std::array<std::atomic<int>, kTrackCount> gTrackDelayDivision{0, 0, 0, 0, 0, 0};
std::array<std::atomic<float>, kTrackCount> gTrackDelayMix{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
std::array<std::atomic<float>, kTrackCount> gTrackDelayFeedback{
    0.35f, 0.35f, 0.35f, 0.35f, 0.35f, 0.35f};
std::array<std::atomic<float>, kTrackCount> gTrackReverbSend{
    0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
std::array<std::atomic<bool>, kTrackCount> gManualEnabled{
    true, true, true, true, true, true};
std::atomic<int> gLoadedTrackCount{0};
std::atomic<int> gActiveCallbacks{0};
std::atomic<uint64_t> gNextTrackGeneration{1};
std::atomic<uint64_t> gCurrentBar{0};
std::atomic<float> gBpm{kDefaultBpm};
std::atomic<bool> gAutopilot{true};
std::atomic<float> gAutopilotIntensity{1.0f};
std::atomic<float> gAutopilotBarPosition{0.0f};
std::atomic<float> gAutopilotPhaseIntensity{0.0f};
constexpr size_t kRecordingRingFrames = 262144;  // 5.46 s at 48 kHz, 2 MiB.
laermbatz::MasterRecordingRing<kRecordingRingFrames> gRecordingRing;
std::atomic<bool> gRecordingEnabled{false};
std::atomic<int> gRecordingSampleRate{0};
std::atomic<float> gLastOutputPeak{0.0f};
std::atomic<int32_t> gRequestedOutputDeviceId{oboe::kUnspecified};
std::mutex gLifecycleMutex;  // JNI/control path only; never taken in onAudioReady.

bool isValidTrackIndex(int index) {
    return index >= 0 && index < kTrackCount;
}

float softLimit(float sample) {
    if (!std::isfinite(sample)) return 0.0f;
    const float magnitude = std::fabs(sample);
    if (magnitude <= 0.78f) return sample;
    const float limited = 0.78f + 0.22f * std::tanh((magnitude - 0.78f) / 0.22f);
    return std::copysign(limited, sample);
}

class MixerCallback final : public oboe::AudioStreamDataCallback {
public:
    std::atomic<bool> playing{false};

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream,
                                           void *audioData,
                                           int32_t numFrames) override {
        gActiveCallbacks.fetch_add(1, std::memory_order_seq_cst);
        auto *out = static_cast<float *>(audioData);
        const int outputChannels = stream->getChannelCount();
        const float outputRate = static_cast<float>(stream->getSampleRate());
        const float bpm = std::clamp(gBpm.load(std::memory_order_relaxed), 60.0f, 180.0f);
        const double beatsPerFrame = static_cast<double>(bpm) / (60.0 * outputRate);
        const float rampPerFrame = 1.0f / std::max(1.0f, kRampSeconds * outputRate);
        const bool autopilot = gAutopilot.load(std::memory_order_relaxed);
        const float autopilotIntensity = std::clamp(
            gAutopilotIntensity.load(std::memory_order_relaxed), 0.5f, 1.25f);
        const auto arrangement = laermbatz::autopilotArrangementAt(beatCounter_ / 4.0);
        const bool isPlaying = playing.load(std::memory_order_relaxed);
        const float masterTarget = isPlaying ? 1.0f : 0.0f;
        std::array<TrackData *, kTrackCount> callbackTracks{};
        std::array<double, kTrackCount> sourceFramesPerOutputFrame{};
        for (int trackIndex = 0; trackIndex < kTrackCount; ++trackIndex) {
            TrackData *track = gTracks[trackIndex].load(std::memory_order_seq_cst);
            callbackTracks[trackIndex] = track;
            if (track != nullptr && track->frames > 0 && track->bars > 0) {
                sourceFramesPerOutputFrame[trackIndex] =
                    static_cast<double>(track->frames) * bpm /
                    (240.0 * track->bars * outputRate);
                const auto &automation = arrangement.tracks[trackIndex];
                const float baseCutoff = gTrackLowPassHz[trackIndex].load(std::memory_order_relaxed);
                const int baseDivision = gTrackDelayDivision[trackIndex].load(std::memory_order_relaxed);
                const float baseDelayMix = gTrackDelayMix[trackIndex].load(std::memory_order_relaxed);
                const float baseFeedback = gTrackDelayFeedback[trackIndex].load(std::memory_order_relaxed);
                const float baseReverb = gTrackReverbSend[trackIndex].load(std::memory_order_relaxed);
                const float effectiveCutoff = autopilot
                    ? std::min(baseCutoff, automation.cutoffHz) : baseCutoff;
                const int effectiveDivision = autopilot && baseDivision == 0
                    ? automation.automaticDelayDivision : baseDivision;
                const float effectiveDelayMix = autopilot
                    ? baseDelayMix * automation.delayScale * autopilotIntensity : baseDelayMix;
                const float effectiveFeedback = autopilot
                    ? baseFeedback * (0.55f + 0.45f * automation.delayScale) : baseFeedback;
                const float effectiveReverb = autopilot
                    ? baseReverb * automation.reverbScale * autopilotIntensity : baseReverb;
                track->effects.setParameters(
                    effectiveCutoff, effectiveDivision, effectiveDelayMix,
                    effectiveFeedback, effectiveReverb, bpm);
            }
        }

        float blockPeak = 0.0f;
        for (int32_t frame = 0; frame < numFrames; ++frame) {
            if (masterGain_ < masterTarget) masterGain_ = std::min(masterGain_ + rampPerFrame, masterTarget);
            else if (masterGain_ > masterTarget) masterGain_ = std::max(masterGain_ - rampPerFrame, masterTarget);

            const uint64_t currentBar = static_cast<uint64_t>(beatCounter_ / 4.0);
            if (currentBar != publishedBar_) {
                publishedBar_ = currentBar;
                gCurrentBar.store(currentBar, std::memory_order_relaxed);
            }
            float left = 0.0f;
            float right = 0.0f;

            for (int trackIndex = 0; trackIndex < kTrackCount; ++trackIndex) {
                TrackData *track = callbackTracks[trackIndex];
                const uint64_t generation = track == nullptr ? 0 : track->generation;
                if (track != lastTrackPointers_[trackIndex] ||
                    generation != lastTrackGenerations_[trackIndex]) {
                    wsola_[trackIndex].reset();
                    trackGainState_[trackIndex] = 0.0f;
                    lastTrackPointers_[trackIndex] = track;
                    lastTrackGenerations_[trackIndex] = generation;
                }
                const float configuredGain = gTrackGains[trackIndex].load(std::memory_order_relaxed);
                const bool launchReady = track != nullptr && currentBar >= track->startBar;
                const float automatedGain = autopilot
                    ? arrangement.tracks[trackIndex].gain * autopilotIntensity
                    : (gManualEnabled[trackIndex].load(std::memory_order_relaxed) ? 1.0f : 0.0f);
                const float targetGain = (isPlaying && launchReady)
                    ? configuredGain * automatedGain : 0.0f;
                float &rampedGain = trackGainState_[trackIndex];
                if (rampedGain < targetGain) rampedGain = std::min(rampedGain + rampPerFrame, targetGain);
                else if (rampedGain > targetGain) rampedGain = std::max(rampedGain - rampPerFrame, targetGain);

                if (track == nullptr || track->frames == 0) continue;

                // Run the stretcher even while muted so its input phase follows the global beat.
                // While paused/stopped, hold the last sample for the short gain ramp and freeze WSOLA.
                StereoSample sample{};
                if (isPlaying && launchReady) {
                    sample = wsola_[trackIndex].next(
                        track->samples.data(), track->frames, track->channels, track->sampleRate,
                        static_cast<int>(outputRate), sourceFramesPerOutputFrame[trackIndex]);
                    sample = track->effects.process(sample);
                } else if (rampedGain > 0.0f) {
                    sample = wsola_[trackIndex].lastSample();
                }
                if (rampedGain > 0.0f) {
                    left += sample.left * rampedGain;
                    right += sample.right * rampedGain;
                }
            }

            const float envelope = masterGain_ * kMasterGain;
            left = softLimit(left * envelope);
            right = softLimit(right * envelope);
            if (outputChannels == 1) {
                out[frame] = (left + right) * 0.5f;
            } else {
                out[frame * outputChannels] = left;
                out[frame * outputChannels + 1] = right;
                for (int channel = 2; channel < outputChannels; ++channel) {
                    out[frame * outputChannels + channel] = 0.0f;
                }
            }
            blockPeak = std::max(blockPeak, std::max(std::fabs(left), std::fabs(right)));
            if (isPlaying) beatCounter_ += beatsPerFrame;
        }
        gLastOutputPeak.store(blockPeak, std::memory_order_relaxed);

        // Capture the final, master-gained and limited stereo output only.
        if (gRecordingEnabled.load(std::memory_order_relaxed) && outputChannels == 2) {
            gRecordingRing.push(out, static_cast<size_t>(numFrames));
        }

        double cycleBar = std::fmod(beatCounter_ / 4.0, laermbatz::kAutopilotBars);
        if (cycleBar < 0.0) cycleBar += laermbatz::kAutopilotBars;
        float phaseGain = 0.0f;
        for (const auto &target : arrangement.tracks) phaseGain += target.gain;
        gAutopilotBarPosition.store(static_cast<float>(cycleBar), std::memory_order_relaxed);
        gAutopilotPhaseIntensity.store(
            std::clamp((phaseGain / static_cast<float>(kTrackCount)) * autopilotIntensity, 0.0f, 1.0f),
            std::memory_order_relaxed);

        gActiveCallbacks.fetch_sub(1, std::memory_order_seq_cst);
        return oboe::DataCallbackResult::Continue;
    }

private:
    double beatCounter_ = 0.0;
    uint64_t publishedBar_ = 0;
    float masterGain_ = 0.0f;
    std::array<TrackData *, kTrackCount> lastTrackPointers_{};
    std::array<WsolaProcessor, kTrackCount> wsola_{};
    std::array<uint64_t, kTrackCount> lastTrackGenerations_{};
    std::array<float, kTrackCount> trackGainState_{};
};

MixerCallback gCallback;
std::shared_ptr<oboe::AudioStream> gStream;

void waitForCallbacksToDrain() {
    while (gActiveCallbacks.load(std::memory_order_seq_cst) != 0) {
        std::this_thread::yield();
    }
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeStart(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    if (gLoadedTrackCount.load(std::memory_order_relaxed) == 0) return JNI_FALSE;
    if (gStream && gStream->getState() == oboe::StreamState::Started) {
        gCallback.playing.store(true, std::memory_order_relaxed);
        return JNI_TRUE;
    }

    gCallback.playing.store(false, std::memory_order_relaxed);
    if (gStream) {
        gStream->close();
        gStream.reset();
    }
    const auto openForDevice = [](int32_t deviceId) {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music)
            ->setDeviceId(deviceId)
            ->setDataCallback(&gCallback);
        return builder.openStream(gStream);
    };

    const int32_t requestedDeviceId = gRequestedOutputDeviceId.load(std::memory_order_relaxed);
    oboe::Result openResult = openForDevice(requestedDeviceId);
    if (openResult != oboe::Result::OK && requestedDeviceId != oboe::kUnspecified) {
        gStream.reset();
        openResult = openForDevice(oboe::kUnspecified);
    }
    if (openResult != oboe::Result::OK || !gStream) {
        gStream.reset();
        return JNI_FALSE;
    }
    for (auto &trackSlot : gTracks) {
        TrackData *track = trackSlot.load(std::memory_order_seq_cst);
        if (track != nullptr && !track->effects.prepare(gStream->getSampleRate())) {
            gStream->close();
            gStream.reset();
            return JNI_FALSE;
        }
    }
    if (gStream->requestStart() != oboe::Result::OK) {
        gStream->close();
        gStream.reset();
        return JNI_FALSE;
    }
    gCallback.playing.store(true, std::memory_order_relaxed);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeStop(JNIEnv *, jobject) {
    gCallback.playing.store(false, std::memory_order_relaxed);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeIsStreamRunning(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    return gStream && gStream->getState() == oboe::StreamState::Started &&
                   gCallback.playing.load(std::memory_order_relaxed)
               ? JNI_TRUE
               : JNI_FALSE;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeGetLastOutputPeak(JNIEnv *, jobject) {
    return gLastOutputPeak.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeSetOutputDeviceId(JNIEnv *, jobject, jint deviceId) {
    gRequestedOutputDeviceId.store(deviceId > 0 ? deviceId : oboe::kUnspecified,
                                   std::memory_order_relaxed);
}

extern "C" JNIEXPORT jint JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeGetOutputDeviceId(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    return gStream ? gStream->getDeviceId() : oboe::kUnspecified;
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeRelease(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    gRecordingEnabled.store(false, std::memory_order_release);
    gCallback.playing.store(false, std::memory_order_relaxed);
    if (gStream) {
        gStream->requestStop();
        gStream->close();
        gStream.reset();
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeStartRecording(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    if (!gStream || gStream->getState() != oboe::StreamState::Started ||
        gStream->getChannelCount() != 2 || !gCallback.playing.load(std::memory_order_relaxed)) return 0;
    gRecordingEnabled.store(false, std::memory_order_release);
    waitForCallbacksToDrain();
    gRecordingRing.reset();
    const int rate = gStream->getSampleRate();
    gRecordingSampleRate.store(rate, std::memory_order_relaxed);
    gRecordingEnabled.store(true, std::memory_order_release);
    return rate;
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeStopRecording(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    gRecordingEnabled.store(false, std::memory_order_release);
    waitForCallbacksToDrain();
}

extern "C" JNIEXPORT jint JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeReadRecording(JNIEnv *env, jobject,
                                                             jfloatArray destination) {
    if (destination == nullptr) return 0;
    const jsize capacity = env->GetArrayLength(destination) / 2;
    if (capacity <= 0) return 0;
    jfloat *samples = env->GetFloatArrayElements(destination, nullptr);
    if (samples == nullptr) return 0;
    const size_t count = gRecordingRing.pop(samples, static_cast<size_t>(capacity));
    env->ReleaseFloatArrayElements(destination, samples, 0);
    return static_cast<jint>(count);
}

extern "C" JNIEXPORT jlong JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeRecordingDroppedFrames(JNIEnv *, jobject) {
    return static_cast<jlong>(gRecordingRing.droppedFrames());
}

extern "C" JNIEXPORT jint JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeRecordingSampleRate(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    if (gStream && gStream->getState() == oboe::StreamState::Started &&
        gCallback.playing.load(std::memory_order_relaxed)) {
        return gStream->getSampleRate();
    }
    return gRecordingSampleRate.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeLoadTrack(JNIEnv *env,
                                                        jobject,
                                                        jint trackIndex,
                                                        jfloatArray pcm,
                                                        jint sampleRate,
                                                        jint channels,
                                                        jint bars) {
    if (!isValidTrackIndex(trackIndex) || pcm == nullptr || sampleRate < 8000 ||
        sampleRate > 192000 || (channels != 1 && channels != 2) ||
        bars < 1 || bars > kMaxBarsPerLoop) return JNI_FALSE;
    const jsize length = env->GetArrayLength(pcm);
    if (length <= 0 || static_cast<size_t>(length) > kMaxInterleavedSamples || length % channels != 0) {
        return JNI_FALSE;
    }
    jfloat *elements = env->GetFloatArrayElements(pcm, nullptr);
    if (elements == nullptr) return JNI_FALSE;

    std::unique_ptr<TrackData> replacement;
    try {
        replacement = std::make_unique<TrackData>();
        replacement->samples.assign(elements, elements + length);
        replacement->sampleRate = sampleRate;
        replacement->channels = channels;
        replacement->bars = bars;
        replacement->frames = static_cast<uint64_t>(length / channels);
    } catch (const std::bad_alloc &) {
        env->ReleaseFloatArrayElements(pcm, elements, JNI_ABORT);
        return JNI_FALSE;
    }
    env->ReleaseFloatArrayElements(pcm, elements, JNI_ABORT);
    for (float &sample : replacement->samples) {
        if (!std::isfinite(sample)) return JNI_FALSE;
        // Decoded PCM and float WAVs enter the mixer in a normalized [-1, 1] range.
        // Clamp finite but hostile/out-of-range float samples before any DSP arithmetic.
        sample = std::clamp(sample, -1.0f, 1.0f);
    }

    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    const bool streamRunning = gStream && gStream->getState() == oboe::StreamState::Started;
    replacement->generation = gNextTrackGeneration.fetch_add(1, std::memory_order_relaxed);
    if (streamRunning && !replacement->effects.prepare(gStream->getSampleRate())) {
        return JNI_FALSE;
    }
    const uint64_t currentBar = gCurrentBar.load(std::memory_order_relaxed);
    replacement->startBar = streamRunning ? currentBar + 1 : currentBar;
    TrackData *newPointer = replacement.release();
    TrackData *oldPointer = gTracks[trackIndex].exchange(newPointer, std::memory_order_seq_cst);
    if (oldPointer == nullptr) gLoadedTrackCount.fetch_add(1, std::memory_order_relaxed);
    waitForCallbacksToDrain();
    delete oldPointer;
    gTrackGains[trackIndex].store(0.55f, std::memory_order_relaxed);
    gManualEnabled[trackIndex].store(true, std::memory_order_relaxed);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeClearTrack(JNIEnv *, jobject, jint trackIndex) {
    if (!isValidTrackIndex(trackIndex)) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(gLifecycleMutex);
    TrackData *oldPointer = gTracks[trackIndex].exchange(nullptr, std::memory_order_seq_cst);
    if (oldPointer == nullptr) return JNI_TRUE;
    gLoadedTrackCount.fetch_sub(1, std::memory_order_relaxed);
    waitForCallbacksToDrain();
    delete oldPointer;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeSetTrackEnabled(JNIEnv *, jobject,
                                                               jint trackIndex,
                                                               jboolean enabled) {
    if (isValidTrackIndex(trackIndex)) {
        gManualEnabled[trackIndex].store(enabled == JNI_TRUE, std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeSetTrackGain(JNIEnv *, jobject,
                                                            jint trackIndex,
                                                            jfloat gain) {
    if (isValidTrackIndex(trackIndex) && std::isfinite(gain)) {
        gTrackGains[trackIndex].store(std::clamp(gain, 0.0f, 1.25f), std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeSetTrackEffects(JNIEnv *, jobject,
                                                              jint trackIndex,
                                                              jfloat lowPassHz,
                                                              jint delayDivision,
                                                              jfloat delayMix,
                                                              jfloat delayFeedback,
                                                              jfloat reverbSend) {
    if (!isValidTrackIndex(trackIndex) || !std::isfinite(lowPassHz) ||
        !std::isfinite(delayMix) || !std::isfinite(delayFeedback) ||
        !std::isfinite(reverbSend)) return;
    gTrackLowPassHz[trackIndex].store(std::clamp(lowPassHz, 200.0f, 20000.0f),
                                      std::memory_order_relaxed);
    gTrackDelayDivision[trackIndex].store(std::clamp(delayDivision, 0, 3),
                                          std::memory_order_relaxed);
    gTrackDelayMix[trackIndex].store(std::clamp(delayMix, 0.0f, 0.8f),
                                     std::memory_order_relaxed);
    gTrackDelayFeedback[trackIndex].store(std::clamp(delayFeedback, 0.0f, 0.8f),
                                         std::memory_order_relaxed);
    gTrackReverbSend[trackIndex].store(std::clamp(reverbSend, 0.0f, 0.8f),
                                       std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeSetBpm(JNIEnv *, jobject, jfloat bpm) {
    if (std::isfinite(bpm)) gBpm.store(std::clamp(bpm, 60.0f, 180.0f), std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeSetAutopilot(JNIEnv *, jobject, jboolean enabled) {
    gAutopilot.store(enabled == JNI_TRUE, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeSetAutopilotIntensity(JNIEnv *, jobject,
                                                                    jfloat intensity) {
    if (std::isfinite(intensity)) {
        gAutopilotIntensity.store(std::clamp(intensity, 0.5f, 1.25f),
                                  std::memory_order_relaxed);
    }
}

extern "C" JNIEXPORT jfloat JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeGetAutopilotBarPosition(JNIEnv *, jobject) {
    return gAutopilotBarPosition.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeGetAutopilotPhaseIntensity(JNIEnv *, jobject) {
    return gAutopilotPhaseIntensity.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT jint JNICALL
Java_de_laermbatz_app_audio_AudioEngine_nativeGetLoadedTrackCount(JNIEnv *, jobject) {
    return gLoadedTrackCount.load(std::memory_order_relaxed);
}
