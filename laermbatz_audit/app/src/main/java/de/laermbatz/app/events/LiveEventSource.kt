package de.laermbatz.app.events

/** Neutraler Vertrag, damit Audiologik nicht von einem Plattform-SDK abhängt. */
interface LiveEventSource {
    val status: EventSourceStatus
    fun connect(): EventSourceStatus
    fun disconnect()
}

enum class EventSourceStatus {
    DISCONNECTED,
    DEMO,
    UNAVAILABLE,
    CONNECTED
}

data class CommunityEvent(
    val id: String?,
    val type: Type,
    val displayName: String? = null,
    val receivedAtMillis: Long = System.currentTimeMillis()
) {
    enum class Type { FOLLOW, GIFT }
}

/** Nur lokale Testquelle; löst niemals echte TikTok-Daten aus. */
class FakeEventSource : LiveEventSource {
    override var status: EventSourceStatus = EventSourceStatus.DISCONNECTED
        private set

    override fun connect(): EventSourceStatus {
        status = EventSourceStatus.DEMO
        return status
    }

    override fun disconnect() {
        status = EventSourceStatus.DISCONNECTED
    }
}

/**
 * Bewusst nicht implementiert: keine unterstützte öffentliche TikTok-LIVE-API
 * für Geschenk-/Follow-Events wurde für dieses Projekt verifiziert.
 */
class TikTokLiveEventSource : LiveEventSource {
    override val status: EventSourceStatus = EventSourceStatus.UNAVAILABLE

    override fun connect(): EventSourceStatus = EventSourceStatus.UNAVAILABLE
    override fun disconnect() = Unit

    companion object {
        const val REASON: String =
            "TikTok veröffentlicht in der geprüften Developer-Doku keine LIVE-Geschenk-/Follow-Event-API."
    }
}
