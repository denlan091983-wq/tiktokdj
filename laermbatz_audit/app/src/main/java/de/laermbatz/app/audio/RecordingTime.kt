package de.laermbatz.app.audio

const val MAX_MASTER_RECORDING_SECONDS = 60 * 60

fun formatRecordingTime(seconds: Int): String {
    val bounded = seconds.coerceIn(0, MAX_MASTER_RECORDING_SECONDS)
    return "%02d:%02d".format(bounded / 60, bounded % 60)
}
