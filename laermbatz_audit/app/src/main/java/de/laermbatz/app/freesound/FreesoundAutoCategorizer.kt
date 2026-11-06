package de.laermbatz.app.freesound

import java.text.Normalizer
import java.util.Locale

/** Infers a useful loop category and broad genre from local archive/path/title metadata. */
object FreesoundAutoCategorizer {
    /** Returns the best matching category, or null when the title and filename contain no known cue. */
    fun inferCategory(title: String, filename: String = ""): FreesoundLoopCategory? {
        val text = normalize("$title $filename")
        val words = text.split(WHITESPACE).filter(String::isNotEmpty).toSet()
        val bassDrum = text.containsPhrase("bass drum")

        return when {
            !bassDrum && (words.any { it in BASS_CUES } || text.containsPhrase("low end")) -> FreesoundLoopCategory.BASSLINE
            words.any { it in HI_HAT_CUES } || text.containsPhrase("open hat") ||
                text.containsPhrase("high hat") || text.containsPhrase("hi hat") -> FreesoundLoopCategory.HI_HAT
            words.any { it in SNARE_CUES } -> FreesoundLoopCategory.SNARE
            words.any { it in KICK_CUES } || bassDrum -> FreesoundLoopCategory.KICK
            words.any { it in SYNTH_CUES } -> FreesoundLoopCategory.SYNTH
            words.any { it in PERCUSSION_CUES } -> FreesoundLoopCategory.PERCUSSION
            words.any { it in EFFECT_CUES } -> FreesoundLoopCategory.EFFECTS
            else -> null
        }
    }

    /** Guesses a broad genre from archive name, folder path, title and filename. */
    fun inferGenre(title: String, filename: String = ""): String {
        val text = normalize("$title $filename")
        return when {
            text.containsPhrase("hard techno") || text.containsPhrase("hardtechno") -> "Hard Techno"
            text.containsPhrase("industrial techno") || text.containsPhrase("industrial") -> "Industrial Techno"
            text.containsPhrase("hardstyle") -> "Hardstyle"
            text.containsPhrase("psytrance") || text.containsPhrase("psy trance") -> "Psytrance"
            text.containsPhrase("drum and bass") || text.containsPhrase("dnb") -> "Drum & Bass"
            text.containsPhrase("tech house") -> "Tech House"
            text.containsPhrase("deep house") || text.containsPhrase("house") -> "House"
            text.containsPhrase("trance") -> "Trance"
            text.containsPhrase("techno") -> "Techno"
            else -> "Unbekannt"
        }
    }

    /** Returns the fixed English search phrase used for the given loop category. */
    fun searchQuery(category: FreesoundLoopCategory): String = when (category) {
        FreesoundLoopCategory.KICK -> "techno kick loop"
        FreesoundLoopCategory.BASSLINE -> "techno bassline loop"
        FreesoundLoopCategory.HI_HAT -> "techno hi hat loop"
        FreesoundLoopCategory.SNARE -> "techno snare loop"
        FreesoundLoopCategory.PERCUSSION -> "techno percussion loop"
        FreesoundLoopCategory.SYNTH -> "techno synth loop"
        FreesoundLoopCategory.EFFECTS -> "techno sound effect loop"
        FreesoundLoopCategory.UNCATEGORIZED -> ""
    }

    /**
     * Queries are tried from specific to broad because Freesound requires all ordinary query terms
     * to match. The caller must retain the same strict license and WAV filters for every attempt.
     */
    fun searchQueries(category: FreesoundLoopCategory): List<String> {
        val fallbacks = when (category) {
            FreesoundLoopCategory.KICK -> listOf("techno kick", "kick drum", "bass drum", "kick")
            FreesoundLoopCategory.BASSLINE -> listOf("techno bassline", "bassline loop", "bassline")
            FreesoundLoopCategory.HI_HAT -> listOf("hi hat loop", "hi hat", "hihat")
            FreesoundLoopCategory.SNARE -> listOf("techno snare", "snare loop", "snare drum", "snare")
            FreesoundLoopCategory.PERCUSSION -> listOf("percussion loop", "percussion", "drum loop")
            FreesoundLoopCategory.SYNTH -> listOf("synth loop", "synth")
            FreesoundLoopCategory.EFFECTS -> listOf("sound effect loop", "riser loop", "impact")
            FreesoundLoopCategory.UNCATEGORIZED -> emptyList()
        }
        return (listOf(searchQuery(category)) + fallbacks)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(ACCENTS, "")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()

    private fun String.containsPhrase(phrase: String): Boolean = (" $this ").contains(" $phrase ")

    private val BASS_CUES = setOf("bass", "bassline", "subbass", "sub", "lowend")
    private val HI_HAT_CUES = setOf("hihat", "hat", "highhat", "hihatloop")
    private val SNARE_CUES = setOf("snare", "snear", "snaredrum", "snareloop")
    private val KICK_CUES = setOf("kick", "kickdrum", "bassdrum")
    private val SYNTH_CUES = setOf("synth", "synthesizer", "lead", "pad", "rave", "stab")
    private val PERCUSSION_CUES = setOf("percussion", "perc", "shaker", "clap", "tom", "schlagzeug", "trommel")
    private val EFFECT_CUES = setOf("fx", "effect", "effects", "impact", "riser", "noise", "effekt", "effekte")

    private val WHITESPACE = Regex("\\s+")
    private val ACCENTS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
}
