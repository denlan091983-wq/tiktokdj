package de.laermbatz.app.freesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class FreesoundApiClientTest {
    @Test
    fun cc0WavIsAllowedByDefault() {
        assertTrue(FreesoundLicensePolicy.isAllowed("Creative Commons 0", "wav"))
        assertTrue(FreesoundLicensePolicy.isAllowed("Creative Commons 0", "WAV"))
    }

    @Test
    fun attributionWavRequiresExplicitOptIn() {
        assertFalse(FreesoundLicensePolicy.isAllowed("Attribution", "wav"))
        assertTrue(FreesoundLicensePolicy.isAllowed("Attribution", "wav", allowAttribution = true))
    }

    @Test
    fun nonCommercialUnknownAndNonWavAreRejected() {
        assertFalse(FreesoundLicensePolicy.isAllowed("Attribution NonCommercial", "wav", allowAttribution = true))
        assertFalse(FreesoundLicensePolicy.isAllowed("Some unknown license", "wav", allowAttribution = true))
        assertFalse(FreesoundLicensePolicy.isAllowed("Creative Commons 0", "mp3"))
        assertFalse(FreesoundLicensePolicy.isAllowed("Attribution", "flac", allowAttribution = true))
    }

    @Test
    fun filteringOnlySurfacesTheSelectedAllowedPolicy() {
        val sounds = listOf(
            sound(id = 1, license = "Creative Commons 0"),
            sound(id = 2, license = "Attribution"),
            sound(id = 3, license = "Attribution NonCommercial"),
            sound(id = 4, license = "unknown"),
            sound(id = 5, license = "Creative Commons 0", type = "mp3")
        )

        assertEquals(listOf(1L), FreesoundLicensePolicy.filterAllowed(sounds).map { it.id })
        assertEquals(listOf(1L, 2L), FreesoundLicensePolicy.filterAllowed(sounds, allowAttribution = true).map { it.id })
    }

    @Test
    fun queryAndSearchUrlValuesAreEncodedAndRequestIsBounded() {
        assertEquals("ambient+%26+field", FreesoundApiClient.encodeQueryParameter("ambient & field"))
        assertEquals("drums+%2B+noise", FreesoundApiClient.encodeQueryParameter("drums + noise"))

        val url = FreesoundApiClient.buildSearchUrl("drums + noise", allowAttribution = false)
        assertTrue(url.startsWith("https://freesound.org/apiv2/search/?"))
        assertTrue(url.contains("query=drums+%2B+noise"))
        assertTrue(url.contains("page_size=50"))
        assertTrue(url.contains("license%3A%22Creative+Commons+0%22+type%3Awav"))
        assertTrue(url.contains("fields=id%2Cname%2Cusername%2Clicense%2Ctype%2Cduration%2Cfilesize%2Curl%2Cdownload%2Cpreviews%2Ctags"))

        val attributionUrl = FreesoundApiClient.buildSearchUrl("kick", allowAttribution = true)
        assertTrue(attributionUrl.contains("license%3A%28%22Creative+Commons+0%22+OR+%22Attribution%22%29+type%3Awav"))
        assertTrue(attributionUrl.contains("page_size=50"))
    }

    @Test
    fun existingDownloadIsRejectedWithoutReplacingTheFile() {
        val directory = Files.createTempDirectory("freesound-collision-test").toFile()
        try {
            val existing = File(directory, "freesound_42_kick.wav")
            val original = byteArrayOf(1, 2, 3, 4)
            existing.writeBytes(original)
            try {
                FreesoundApiClient().downloadWav(
                    FreesoundSound(42, "kick.wav", "tester", FreesoundLicensePolicy.CC0, "wav"),
                    "offline-token",
                    directory
                )
                fail("Expected an existing local download to be rejected")
            } catch (expected: IOException) {
                assertTrue(expected.message.orEmpty().contains("already in the local library"))
            }
            assertTrue(existing.readBytes().contentEquals(original))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun deeplyNestedJsonIsRejectedAtTheConfiguredDepthLimit() {
        val nested = "[".repeat(100) + "0" + "]".repeat(100)
        try {
            JsonParser(nested).parse()
            fail("Expected deeply nested JSON to be rejected")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("Malformed Freesound JSON"))
        }
    }

    private fun sound(id: Long, license: String, type: String = "wav") = FreesoundSound(
        id = id,
        name = "sound-$id.wav",
        username = "offline-test",
        license = license,
        type = type
    )
}
