package de.laermbatz.app.freesound

import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Base64

/** Helpers for Freesound's documented OAuth2 authorization-code flow. */
object FreesoundOAuthFlow {
    private val secureRandom = SecureRandom()
    private const val AUTHORIZE_ENDPOINT = "https://freesound.org/apiv2/oauth2/authorize/"

    fun createState(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun authorizationUrl(clientId: String, state: String): String {
        require(clientId.isNotBlank()) { "Freesound Client-ID fehlt" }
        require(state.isNotBlank()) { "OAuth-State fehlt" }
        return "$AUTHORIZE_ENDPOINT?client_id=${encode(clientId)}&response_type=code&state=${encode(state)}"
    }

    /**
     * Parses the full redirect URL copied from Freesound and rejects missing or mismatched
     * state values before exchanging the short-lived authorization code.
     */
    fun authorizationCodeFromRedirect(
        redirectUrl: String,
        expectedState: String,
        expectedRedirectUri: String
    ): String {
        require(expectedState.isNotBlank()) { "Erwarteter OAuth-State fehlt" }
        require(expectedRedirectUri.isNotBlank()) { "Registrierte Freesound-Redirect-URL fehlt" }
        val uri = runCatching { URI(redirectUrl.trim()) }
            .getOrElse { throw IllegalArgumentException("OAuth-Weiterleitungs-URL ist ungültig") }
        val expectedUri = runCatching { URI(expectedRedirectUri.trim()) }
            .getOrElse { throw IllegalArgumentException("Registrierte OAuth-Redirect-URL ist ungültig") }
        require(
            uri.scheme.equals(expectedUri.scheme, ignoreCase = true) &&
                uri.rawAuthority.equals(expectedUri.rawAuthority, ignoreCase = true) &&
                uri.rawPath == expectedUri.rawPath
        ) { "OAuth-URL stimmt nicht mit der registrierten Redirect-URL überein" }
        val params = parseQuery(uri.rawQuery.orEmpty())
        val expectedFixedParams = parseQuery(expectedUri.rawQuery.orEmpty())
        require(expectedFixedParams.all { (key, value) -> params[key] == value }) {
            "OAuth-Redirect enthält nicht die registrierten URL-Parameter"
        }
        val error = params["error"]
        require(error.isNullOrBlank()) { "Freesound hat die Autorisierung abgelehnt: $error" }
        val actualState = params["state"]
        require(actualState == expectedState) { "OAuth-State stimmt nicht überein; Autorisierung bitte neu starten" }
        val code = params["code"].orEmpty()
        require(code.isNotBlank()) { "In der Weiterleitungs-URL fehlt der OAuth-Code" }
        return code
    }

    private fun parseQuery(query: String): Map<String, String> = query
        .split('&')
        .filter { it.isNotBlank() }
        .associate { part ->
            val split = part.indexOf('=')
            val key = if (split < 0) part else part.substring(0, split)
            val value = if (split < 0) "" else part.substring(split + 1)
            decode(key) to decode(value)
        }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
}
