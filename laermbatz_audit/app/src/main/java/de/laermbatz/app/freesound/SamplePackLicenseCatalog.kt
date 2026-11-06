package de.laermbatz.app.freesound

import java.util.Locale

const val LOCAL_SAMPLE_PACK_LICENSE_PREFIX = "LOCAL_SAMPLE_PACK:"
const val LOCAL_USER_ZIP_LICENSE_PREFIX = "LOCAL_USER_ZIP:"

/**
 * Vendor-specific terms shown for user-supplied archives. These records never grant permission to
 * redistribute raw samples; they summarize the cited public terms and retain any unresolved points.
 */
data class SamplePackLicenseSummary(
    val packName: String,
    val vendor: String,
    val productUrl: String,
    val termsUrl: String,
    val musicUse: String,
    val attribution: String,
    val publicStreamCaveat: String =
        "Die geprüften Anbieterquellen bestätigen öffentliche TikTok-/Radio-Livestreams nicht ausdrücklich; vorher beim Anbieter klären.",
    val rawFileDistribution: String =
        "Rohe WAV-Dateien nicht in APK, GitHub, Website oder Downloadpaket mitliefern."
) {
    fun libraryLicenseValue(): String =
        "$LOCAL_SAMPLE_PACK_LICENSE_PREFIX$musicUse $attribution $publicStreamCaveat"
}

object SamplePackLicenseCatalog {
    private val policies = listOf(
        SamplePackLicenseSummary(
            packName = "Hardcore Free Samples Loops & Sounds",
            vendor = "LucidSamples",
            productUrl = "https://www.lucidsamples.com/free-sample-packs/78-hardcore-free-samples-loops-hard-sounds.html",
            termsUrl = "https://www.lucidsamples.com/content/3-terms-and-conditions-of-use",
            musicUse = "Eigene kommerzielle und nicht-kommerzielle Musik ist laut Anbieter gestattet.",
            attribution = "Lucid Samples muss als Eigentümer der verwendeten Samples genannt und anerkannt werden."
        ),
        SamplePackLicenseSummary(
            packName = "Psytrance Syndicate",
            vendor = "FunctionLoops",
            productUrl = "https://www.functionloops.com/psytrance-syndicate.html",
            termsUrl = "https://www.functionloops.com/license.html",
            musicUse = "Eigene Musikproduktion wird als royalty-free beworben; Lizenztext knüpft Rechte an 'once purchased', obwohl der Download gratis ist. Gratis-Lizenz vor Veröffentlichung beim Anbieter verifizieren.",
            attribution = "Auf den geprüften Seiten wird keine Pflicht zur Namensnennung angegeben."
        ),
        SamplePackLicenseSummary(
            packName = "Free Hard Techno & Industrial Samples (FREE-012)",
            vendor = "SINEE",
            productUrl = "https://sinee.de/en/free-hard-techno-samples",
            termsUrl = "https://sinee.de/en/informationen/terms-and-conditions/",
            musicUse = "Eigene private und geschäftliche Musikproduktion ist laut allgemeinen AGB grundsätzlich gestattet.",
            attribution = "Auf den geprüften Seiten wird keine Pflicht zur Namensnennung angegeben."
        ),
        SamplePackLicenseSummary(
            packName = "Free Hard Techno Sample Pack",
            vendor = "KNALWERK",
            productUrl = "https://knalwerktechno.com/products/free-hard-industrial-techno-sample-pack",
            termsUrl = "https://knalwerk.bandcamp.com/album/free-hard-industrial-techno-sample-pack",
            musicUse = "Kommerzielle Musikproduktionen werden auf der Produktseite als Nutzung genannt; Bandcamp führt 'all rights reserved'.",
            attribution = "Auf den geprüften Seiten wird keine Pflicht zur Namensnennung angegeben."
        ),
        SamplePackLicenseSummary(
            packName = "Free Hardstyle Sample Pack",
            vendor = "Myloops",
            productUrl = "https://www.myloops.net/download-free-hardstyle-samples",
            termsUrl = "https://www.myloops.net/terms-conditions",
            musicUse = "Eigene kommerzielle Musikproduktion wird beworben; allgemeine Bedingungen sprechen teilweise von Lizenzerwerb beim Kauf, obwohl das Pack gratis angeboten wird.",
            attribution = "Keine ausdrückliche Attributionspflicht in den geprüften Quellen gefunden."
        ),
        SamplePackLicenseSummary(
            packName = "Psytrance Song Ideas (Psytrance AI Voices Song Ideas, FREE PACK)",
            vendor = "Elevated Trance / Elevation Sounds",
            productUrl = "https://www.elevationsounds.com/p/psytrance-ai-voices-song-ideas-free-pack/",
            termsUrl = "https://www.elevationsounds.com/license/",
            musicUse = "Kommerzielle Nutzung der Song-Ideen in eigenen Track-Veröffentlichungen ist laut Produktseite gestattet.",
            attribution = "Öffentliche Namensnennung ist in den geprüften Texten nicht ausdrücklich festgelegt."
        ),
        SamplePackLicenseSummary(
            packName = "Free Psy Trance Sample Pack",
            vendor = "Myloops",
            productUrl = "https://www.myloops.net/download-free-psy-trance-samples",
            termsUrl = "https://www.myloops.net/download-free-sample-packs",
            musicUse = "Royalty-free Nutzung in eigenen Psy-Trance-Musikproduktionen wird von Packseite/FAQ beschrieben.",
            attribution = "Keine ausdrückliche Attributionspflicht in den geprüften Quellen gefunden."
        ),
        SamplePackLicenseSummary(
            packName = "Free Techno Sample Pack",
            vendor = "Myloops",
            productUrl = "https://www.myloops.net/download-free-techno-samples",
            termsUrl = "https://www.myloops.net/terms-conditions",
            musicUse = "Nutzung in eigenen Musikproduktionen und vollständig gemischten Musik-/audiovisuellen Produktionen wird beschrieben.",
            attribution = "Quellenangabe ist laut Bedingungen nicht erforderlich, aber willkommen."
        )
    )

    /**
     * Filename-only display hint. This does not verify ZIP identity or license provenance and must
     * never be used to mark a user-selected archive as rights-cleared.
     */
    fun forArchiveName(name: String): SamplePackLicenseSummary? {
        val normalized = normalize(name)
        return when {
            "lucidsamples" in normalized || "hardcore free samples loops sounds" in normalized -> policies[0]
            "functionloops" in normalized || "psytrance syndicate" in normalized -> policies[1]
            "sinee" in normalized && "hard techno" in normalized -> policies[2]
            "knalwerk" in normalized -> policies[3]
            "myloops" in normalized && "hardstyle" in normalized -> policies[4]
            "elevated" in normalized && "trance" in normalized -> policies[5]
            "myloops" in normalized && "psy" in normalized -> policies[6]
            "myloops" in normalized && "techno" in normalized -> policies[7]
            else -> null
        }
    }

    fun isLocalPackLicense(value: String): Boolean =
        value.startsWith(LOCAL_SAMPLE_PACK_LICENSE_PREFIX) || isUserSuppliedZipLicense(value)

    fun isUserSuppliedZipLicense(value: String): Boolean = value.startsWith(LOCAL_USER_ZIP_LICENSE_PREFIX)

    fun localLicenseDescription(value: String): String = when {
        isUserSuppliedZipLicense(value) -> value.removePrefix(LOCAL_USER_ZIP_LICENSE_PREFIX)
        value.startsWith(LOCAL_SAMPLE_PACK_LICENSE_PREFIX) -> value.removePrefix(LOCAL_SAMPLE_PACK_LICENSE_PREFIX)
        else -> value
    }

    fun legalImportNotice(): String =
        "Es werden nur WAV-Dateien lokal auf diesem Gerät importiert; ZIPs und rohe WAVs werden nicht in APK, GitHub oder Radio-Website eingebettet. Unbekannte ZIPs werden ausdrücklich als Nutzerimport mit nicht unabhängig geprüfter Lizenz markiert. Prüfe die Anbieterbedingungen und bestätige, dass du die Dateien lokal nutzen darfst. Öffentliche Live-Stream- oder Mix-Veröffentlichungsrechte sind damit nicht automatisch bestätigt; nötige Credits bleiben deine Verantwortung."

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
