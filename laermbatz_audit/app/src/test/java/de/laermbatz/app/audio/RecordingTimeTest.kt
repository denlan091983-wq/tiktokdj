package de.laermbatz.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTimeTest {
    @Test fun formatsElapsedTimeAsMinutesAndSeconds() {
        assertEquals("00:00", formatRecordingTime(0))
        assertEquals("01:09", formatRecordingTime(69))
        assertEquals("59:59", formatRecordingTime(3599))
    }

    @Test fun clampsDisplayAtTheSixtyMinuteLimit() {
        assertEquals(3600, MAX_MASTER_RECORDING_SECONDS)
        assertEquals("60:00", formatRecordingTime(3600))
        assertEquals("60:00", formatRecordingTime(50_000))
        assertEquals("00:00", formatRecordingTime(-1))
    }
}
