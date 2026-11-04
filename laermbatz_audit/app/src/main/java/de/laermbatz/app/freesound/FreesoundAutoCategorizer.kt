package de.laermbatz.app.freesound

import java.text.Normalizer
import java.util.Locale

/** Infers a useful loop category from the name supplied by Freesound. */
object FreesoundAutoCategorizer {
    /**
     * Returns the best matching category, or null when the title and filename contain no known cue.
     * Bassline and hi-hat cues take precedence over broader/percussive cues in compound names.
     */
    fun inferCategory(title: String, filename: String = ""): FreesoundLoopCategory? {
        val text = normalize("$title $filename")
        val words = text.split(WHITESPACE).filter(String::isNotEmpty).toSet()

        return when {
            words.any { it in BASS_CUES } || text.containsPhrase("low end") -> FreesoundLoopCategory.BASSLINE
            words.any { it in HI_HAT_CUES } || text.containsPhrase("open hat") -> FreesoundLoopCategory.HI_HAT
            words.any { it in KICK_CUES } -> FreesoundLoopCategory.KICK
            words.any { it in SYNTH_CUES } -> FreesoundLoopCategory.SYNTH
            words.any { it in PERCUSSION_CUES } -> FreesoundLoopCategory.PERCUSSION
            words.any { it in EFFECT_CUES } -> FreesoundLoopCategory.EFFECTS
            else -> null
        }
    }

    /** Returns the fixed English search phrase used for the given loop category. */
    fun searchQuery(category: FreesoundLoopCategory): String = when (category) {
        FreesoundLoopCategory.KICK -> "techno kick loop"
        FreesoundLoopCategory.BASSLINE -> "techno bassline loop"
        FreesoundLoopCategory.HI_HAT -> "techno hi hat loop"
        FreesoundLoopCategory.PERCUSSION -> "techno percussion loop"
        FreesoundLoopCategory.SYNTH -> "techno synth loop"
        FreesoundLoopCategory.EFFECTS -> "techno sound effect loop"
        FreesoundLoopCategory.UNCATEGORIZED -> ""
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(ACCENTS, "")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()

    private fun String.containsPhrase(phrase: String): Boolean =
        (" $this ").contains(" $phrase ")

    private val BASS_CUES = setOf("bass", "bassline", "subbass")
    private val HI_HAT_CUES = setOf("hihat", "hat")
    private val KICK_CUES = setOf("kick", "kickdrum", "bassdrum")
    private val SYNTH_CUES = setOf("synth", "synthesizer", "lead", "pad", "rave", "stab")
    private val PERCUSSION_CUES = setOf("percussion", "perc", "shaker", "clap", "tom", "schlagzeug", "trommel")
    private val EFFECT_CUES = setOf("fx", "effect", "effects", "impact", "riser", "noise", "effekt", "effekte")

    private val WHITESPACE = Regex("\\s+")
    private val ACCENTS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
}
