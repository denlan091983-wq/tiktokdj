#include "../../main/cpp/master_recording_ring.h"

#include <cassert>
#include <cstdint>
#include <iostream>

int main() {
    laermbatz::MasterRecordingRing<4> ring;
    const float first[] = {0.1f, -0.1f, 0.2f, -0.2f, 0.3f, -0.3f, 0.4f, -0.4f};
    assert(ring.push(first, 4) == 4);
    assert(ring.queuedFrames() == 4);
    const float overflow[] = {0.5f, -0.5f, 0.6f, -0.6f};
    assert(ring.push(overflow, 2) == 0);
    assert(ring.droppedFrames() == 2);

    float out[8]{};
    assert(ring.pop(out, 3) == 3);
    for (int i = 0; i < 6; ++i) assert(out[i] == first[i]);
    assert(ring.push(overflow, 2) == 2);  // Exercises the wrapped slots.
    assert(ring.pop(out, 4) == 3);
    assert(out[0] == first[6] && out[1] == first[7]);
    assert(out[2] == overflow[0] && out[3] == overflow[1]);
    assert(out[4] == overflow[2] && out[5] == overflow[3]);
    assert(ring.queuedFrames() == 0);

    ring.reset();
    assert(ring.droppedFrames() == 0);
    std::cout << "master recording SPSC ring tests passed\n";
    return 0;
}
