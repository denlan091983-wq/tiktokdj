package de.laermbatz.app.freesound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreesoundOAuthFlowTest {
    @Test
    fun authorizationUrlContainsEncodedClientAndState() {
        val url = FreesoundOAuthFlow.authorizationUrl("client id", "state+value")
        assertTrue(url.startsWith("https://freesound.org/apiv2/oauth2/authorize/?"))
        assertTrue(url.contains("client_id=client+id"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=state%2Bvalue"))
    }

    @Test
    fun parsesCodeOnlyWhenStateMatches() {
        val code = FreesoundOAuthFlow.authorizationCodeFromRedirect(
            "https://callback.example/oauth?code=abc%2B123&state=expected",
            "expected",
            "https://callback.example/oauth"
        )
        assertEquals("abc+123", code)
    }

    @Test
    fun rejectsMismatchedState() {
        val error = runCatching {
            FreesoundOAuthFlow.authorizationCodeFromRedirect(
                "https://callback.example/?code=abc&state=other",
                "expected",
                "https://callback.example/"
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsDeniedOrMissingCodeCallback() {
        val denied = runCatching {
            FreesoundOAuthFlow.authorizationCodeFromRedirect(
                "https://callback.example/?error=access_denied&state=expected",
                "expected",
                "https://callback.example/"
            )
        }.exceptionOrNull()
        assertTrue(denied is IllegalArgumentException)

        val missingCode = runCatching {
            FreesoundOAuthFlow.authorizationCodeFromRedirect(
                "https://callback.example/?state=expected",
                "expected",
                "https://callback.example/"
            )
        }.exceptionOrNull()
        assertTrue(missingCode is IllegalArgumentException)
    }

    @Test
    fun rejectsDifferentRedirectHostEvenWhenStateMatches() {
        val error = runCatching {
            FreesoundOAuthFlow.authorizationCodeFromRedirect(
                "https://attacker.example/oauth?code=abc&state=expected",
                "expected",
                "https://callback.example/oauth"
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
