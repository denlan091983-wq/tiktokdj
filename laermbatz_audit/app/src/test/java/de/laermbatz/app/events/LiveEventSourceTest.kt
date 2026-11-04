package de.laermbatz.app.events

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveEventSourceTest {
    @Test
    fun fakeSourceTransitionsBetweenDisconnectedAndDemo() {
        val source = FakeEventSource()
        assertEquals(EventSourceStatus.DISCONNECTED, source.status)
        assertEquals(EventSourceStatus.DEMO, source.connect())
        assertEquals(EventSourceStatus.DEMO, source.status)
        source.disconnect()
        assertEquals(EventSourceStatus.DISCONNECTED, source.status)
    }

    @Test
    fun tiktokSourceDoesNotPretendToConnect() {
        val source = TikTokLiveEventSource()
        assertEquals(EventSourceStatus.UNAVAILABLE, source.status)
        assertEquals(EventSourceStatus.UNAVAILABLE, source.connect())
        assertEquals(EventSourceStatus.UNAVAILABLE, source.status)
    }
}
