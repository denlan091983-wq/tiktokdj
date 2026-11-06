package de.laermbatz.app.freesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreesoundAutoCategorizerTest {
    @Test
    fun infersCategoriesFromEnglishAndGermanTitles() {
        assertEquals(FreesoundLoopCategory.KICK, FreesoundAutoCategorizer.inferCategory("Kick Drum Loop"))
        assertEquals(FreesoundLoopCategory.BASSLINE, FreesoundAutoCategorizer.inferCategory("Dunkle Bassline"))
        assertEquals(FreesoundLoopCategory.HI_HAT, FreesoundAutoCategorizer.inferCategory("Hi-Hat Rhythm"))
        assertEquals(FreesoundLoopCategory.SNARE, FreesoundAutoCategorizer.inferCategory("Snare Drum Loop"))
        assertEquals(FreesoundLoopCategory.KICK, FreesoundAutoCategorizer.inferCategory("Bass Drum Loop"))
        assertEquals(FreesoundLoopCategory.PERCUSSION, FreesoundAutoCategorizer.inferCategory("Schlagzeug Loop"))
        assertEquals(FreesoundLoopCategory.SYNTH, FreesoundAutoCategorizer.inferCategory("Synthesizer Pad"))
        assertEquals(FreesoundLoopCategory.EFFECTS, FreesoundAutoCategorizer.inferCategory("Riser Effekt"))
    }

    @Test
    fun recognizesSpellingVariantsAndFilenameOnlyCues() {
        assertEquals(FreesoundLoopCategory.KICK, FreesoundAutoCategorizer.inferCategory("Groove", "909_kickdrum.wav"))
        assertEquals(FreesoundLoopCategory.BASSLINE, FreesoundAutoCategorizer.inferCategory("SUB-BASS"))
        assertEquals(FreesoundLoopCategory.BASSLINE, FreesoundAutoCategorizer.inferCategory("Low_end pulse"))
        assertEquals(FreesoundLoopCategory.HI_HAT, FreesoundAutoCategorizer.inferCategory("open_hat_loop"))
        assertEquals(FreesoundLoopCategory.HI_HAT, FreesoundAutoCategorizer.inferCategory("HIHAT"))
        assertEquals(FreesoundLoopCategory.HI_HAT, FreesoundAutoCategorizer.inferCategory("High Hat"))
        assertEquals(FreesoundLoopCategory.SNARE, FreesoundAutoCategorizer.inferCategory("snear_loop.wav"))
        assertEquals(FreesoundLoopCategory.PERCUSSION, FreesoundAutoCategorizer.inferCategory("Percussion"))
        assertEquals(FreesoundLoopCategory.PERCUSSION, FreesoundAutoCategorizer.inferCategory("Shaker-clap-tom"))
        assertEquals(FreesoundLoopCategory.SYNTH, FreesoundAutoCategorizer.inferCategory("RAVE_STAB"))
        assertEquals(FreesoundLoopCategory.EFFECTS, FreesoundAutoCategorizer.inferCategory("FX-impact_noise"))
    }

    @Test
    fun matchingIsCaseAndAccentInsensitiveAndUsesWholeWords() {
        assertEquals(FreesoundLoopCategory.BASSLINE, FreesoundAutoCategorizer.inferCategory("BÁSSLINE"))
        assertEquals(FreesoundLoopCategory.HI_HAT, FreesoundAutoCategorizer.inferCategory("hat"))
        assertNull(FreesoundAutoCategorizer.inferCategory("That melody is fantastic"))
    }

    @Test
    fun usesSpecificCategoryPriorityForMultipleMatches() {
        assertEquals(FreesoundLoopCategory.BASSLINE, FreesoundAutoCategorizer.inferCategory("kick bassline loop"))
        assertEquals(FreesoundLoopCategory.HI_HAT, FreesoundAutoCategorizer.inferCategory("clap hi-hat"))
        assertEquals(FreesoundLoopCategory.KICK, FreesoundAutoCategorizer.inferCategory("kick synth"))
        assertEquals(FreesoundLoopCategory.SYNTH, FreesoundAutoCategorizer.inferCategory("synth percussion"))
        assertEquals(FreesoundLoopCategory.PERCUSSION, FreesoundAutoCategorizer.inferCategory("tom noise"))
    }

    @Test
    fun returnsNullForUnknownOrBlankNames() {
        assertNull(FreesoundAutoCategorizer.inferCategory("Ambient texture"))
        assertNull(FreesoundAutoCategorizer.inferCategory("", "unknown-file-123.wav"))
        assertNull(FreesoundAutoCategorizer.inferCategory("   "))
    }

    @Test
    fun infersGenresFromArchiveNamesAndSubfolders() {
        assertEquals("Hard Techno", FreesoundAutoCategorizer.inferGenre("Pack.zip", "Loops/Hard-Techno/Kick.wav"))
        assertEquals("Hardstyle", FreesoundAutoCategorizer.inferGenre("MyLoops Free Hardstyle.zip"))
        assertEquals("Psytrance", FreesoundAutoCategorizer.inferGenre("psy trance bass.wav"))
        assertEquals("Unbekannt", FreesoundAutoCategorizer.inferGenre("random percussion.wav"))
    }

    @Test
    fun mapsEveryCategoryToItsSearchQuery() {
        assertEquals("techno kick loop", FreesoundAutoCategorizer.searchQuery(FreesoundLoopCategory.KICK))
        assertEquals("techno bassline loop", FreesoundAutoCategorizer.searchQuery(FreesoundLoopCategory.BASSLINE))
        assertEquals("techno hi hat loop", FreesoundAutoCategorizer.searchQuery(FreesoundLoopCategory.HI_HAT))
        assertEquals("techno snare loop", FreesoundAutoCategorizer.searchQuery(FreesoundLoopCategory.SNARE))
        assertEquals("techno percussion loop", FreesoundAutoCategorizer.searchQuery(FreesoundLoopCategory.PERCUSSION))
        assertEquals("techno synth loop", FreesoundAutoCategorizer.searchQuery(FreesoundLoopCategory.SYNTH))
        assertEquals("techno sound effect loop", FreesoundAutoCategorizer.searchQuery(FreesoundLoopCategory.EFFECTS))
        assertEquals("", FreesoundAutoCategorizer.searchQuery(FreesoundLoopCategory.UNCATEGORIZED))

        assertEquals(
            listOf("techno kick loop", "techno kick", "kick drum", "bass drum", "kick"),
            FreesoundAutoCategorizer.searchQueries(FreesoundLoopCategory.KICK)
        )
        assertEquals(
            listOf("techno bassline loop", "techno bassline", "bassline loop", "bassline"),
            FreesoundAutoCategorizer.searchQueries(FreesoundLoopCategory.BASSLINE)
        )
        assertEquals(
            listOf("techno hi hat loop", "hi hat loop", "hi hat", "hihat"),
            FreesoundAutoCategorizer.searchQueries(FreesoundLoopCategory.HI_HAT)
        )
        assertEquals(
            listOf("techno snare loop", "techno snare", "snare loop", "snare drum", "snare"),
            FreesoundAutoCategorizer.searchQueries(FreesoundLoopCategory.SNARE)
        )
        assertEquals(
            listOf("techno percussion loop", "percussion loop", "percussion", "drum loop"),
            FreesoundAutoCategorizer.searchQueries(FreesoundLoopCategory.PERCUSSION)
        )
        assertEquals(
            listOf("techno synth loop", "synth loop", "synth"),
            FreesoundAutoCategorizer.searchQueries(FreesoundLoopCategory.SYNTH)
        )
        assertEquals(
            listOf("techno sound effect loop", "sound effect loop", "riser loop", "impact"),
            FreesoundAutoCategorizer.searchQueries(FreesoundLoopCategory.EFFECTS)
        )
        assertEquals(emptyList<String>(), FreesoundAutoCategorizer.searchQueries(FreesoundLoopCategory.UNCATEGORIZED))
    }
}
