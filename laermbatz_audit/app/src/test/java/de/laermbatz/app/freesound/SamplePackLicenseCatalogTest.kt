package de.laermbatz.app.freesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplePackLicenseCatalogTest {
    @Test
    fun mapsAllEightDriveArchiveNamesToTheExpectedVendors() {
        val cases = listOf(
            "Hardcore_Free_Samples_Loops_&_Sounds_-_www.lucidsamples.com.zip" to "LucidSamples",
            "FunctionLoops-PsytranceSyndicate.zip" to "FunctionLoops",
            "SINEE+-+Free+Hard+Techno+Samples.zip" to "SINEE",
            "FREE_HARD_TECHNO_SAMPLE_PACK_-_KNALWERK.zip" to "KNALWERK",
            "Myloops Free Hardstyle Sample Pack.zip" to "Myloops",
            "Elevated Trance - Psytrance Song Ideas.zip" to "Elevated Trance / Elevation Sounds",
            "Myloops Free Psy Trance Sample Pack.zip" to "Myloops",
            "Myloops Free Techno Sample Pack.zip" to "Myloops"
        )

        cases.forEach { (archiveName, vendor) ->
            val policy = SamplePackLicenseCatalog.forArchiveName(archiveName)
            assertNotNull("No policy for $archiveName", policy)
            assertEquals(archiveName, vendor, policy?.vendor)
        }
    }

    @Test
    fun lookupIsCaseInsensitiveAndUnknownArchivesAreNotSilentlyAllowed() {
        assertEquals(
            "KNALWERK",
            SamplePackLicenseCatalog.forArchiveName("free_hard_techno_sample_pack_-_knalwerk.ZIP")?.vendor
        )
        assertNull(SamplePackLicenseCatalog.forArchiveName("unverified_other_samples.zip"))
    }

    @Test
    fun recordsKeepAttributionAndPublicStreamCaveatsDistinctFromRawFileSharing() {
        val lucid = SamplePackLicenseCatalog.forArchiveName("Hardcore_Free_Samples_Loops_&_Sounds_-_www.lucidsamples.com.zip")
        val myloops = SamplePackLicenseCatalog.forArchiveName("Myloops Free Techno Sample Pack.zip")

        assertTrue(lucid?.attribution.orEmpty().contains("Lucid Samples"))
        assertTrue(lucid?.publicStreamCaveat.orEmpty().contains("nicht ausdrücklich"))
        assertTrue(lucid?.rawFileDistribution.orEmpty().contains("nicht"))
        assertTrue(myloops?.attribution.orEmpty().contains("willkommen"))
        assertFalse(SamplePackLicenseCatalog.isLocalPackLicense("Creative Commons 0"))
        assertTrue(SamplePackLicenseCatalog.isLocalPackLicense(lucid?.libraryLicenseValue().orEmpty()))
    }
}
