#pragma once

#include <array>
#include <atomic>
#include <algorithm>
#include <cstddef>
#include <cstdint>

namespace laermbatz {

// Fixed-capacity single-producer/single-consumer queue. push() is intended for
// the Oboe callback; it performs no allocation, lock, syscall, or JNI call.
template <size_t CapacityFrames>
class MasterRecordingRing {
    static_assert(CapacityFrames > 0 && (CapacityFrames & (CapacityFrames - 1)) == 0,
                  "CapacityFrames must be a power of two");
public:
    size_t push(const float *interleavedStereo, size_t frames) noexcept {
        const uint64_t head = head_.load(std::memory_order_relaxed);
        const uint64_t tail = tail_.load(std::memory_order_acquire);
        const size_t used = static_cast<size_t>(head - tail);
        const size_t accepted = std::min(frames, CapacityFrames - std::min(used, CapacityFrames));
        for (size_t frame = 0; frame < accepted; ++frame) {
            const size_t slot = static_cast<size_t>(head + frame) & (CapacityFrames - 1);
            samples_[slot * 2] = interleavedStereo[frame * 2];
            samples_[slot * 2 + 1] = interleavedStereo[frame * 2 + 1];
        }
        head_.store(head + accepted, std::memory_order_release);
        if (accepted != frames) droppedFrames_.fetch_add(frames - accepted, std::memory_order_relaxed);
        return accepted;
    }

    size_t pop(float *destination, size_t maxFrames) noexcept {
        const uint64_t tail = tail_.load(std::memory_order_relaxed);
        const uint64_t head = head_.load(std::memory_order_acquire);
        const size_t available = static_cast<size_t>(head - tail);
        const size_t count = std::min(maxFrames, available);
        for (size_t frame = 0; frame < count; ++frame) {
            const size_t slot = static_cast<size_t>(tail + frame) & (CapacityFrames - 1);
            destination[frame * 2] = samples_[slot * 2];
            destination[frame * 2 + 1] = samples_[slot * 2 + 1];
        }
        tail_.store(tail + count, std::memory_order_release);
        return count;
    }

    uint64_t droppedFrames() const noexcept {
        return droppedFrames_.load(std::memory_order_relaxed);
    }

    void reset() noexcept {
        // Called only while producer is disabled and consumer is stopped.
        head_.store(0, std::memory_order_relaxed);
        tail_.store(0, std::memory_order_relaxed);
        droppedFrames_.store(0, std::memory_order_relaxed);
    }

    size_t queuedFrames() const noexcept {
        return static_cast<size_t>(head_.load(std::memory_order_acquire) -
                                   tail_.load(std::memory_order_acquire));
    }

private:
    alignas(64) std::array<float, CapacityFrames * 2> samples_{};
    alignas(64) std::atomic<uint64_t> head_{0};
    alignas(64) std::atomic<uint64_t> tail_{0};
    std::atomic<uint64_t> droppedFrames_{0};
};

}  // namespace laermbatz
