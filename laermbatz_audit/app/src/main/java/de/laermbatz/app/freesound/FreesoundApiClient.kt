package de.laermbatz.app.freesound

import de.laermbatz.app.audio.WavDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/** A sound returned by the Freesound API v2 search endpoint. */
data class FreesoundSound(
    val id: Long,
    val name: String,
    val username: String,
    val license: String,
    val type: String,
    val duration: Double? = null,
    val filesize: Long? = null,
    val url: String? = null,
    val download: String? = null,
    val previews: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    /** True only when the caller explicitly opted into Attribution results during search. */
    val attributionAllowed: Boolean = false
)

data class FreesoundSearchPage(
    /** API total matching the query/filter; local safeguards may still remove unexpected items. */
    val count: Long,
    val next: String?,
    val previous: String?,
    val results: List<FreesoundSound>
)

data class FreesoundTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
    val tokenType: String,
    val scope: String?,
    val expiresAtEpochMillis: Long?
) {
    // Avoid accidentally printing credentials from a token response in logs.
    override fun toString(): String =
        "FreesoundTokenResponse(accessToken=[REDACTED], refreshToken=${if (refreshToken == null) "null" else "[REDACTED]"}, " +
            "expiresInSeconds=$expiresInSeconds, tokenType=$tokenType, scope=$scope, expiresAtEpochMillis=$expiresAtEpochMillis)"
}

/** Exact Freesound license names and WAV type checks used by both search and download. */
object FreesoundLicensePolicy {
    const val CC0 = "Creative Commons 0"
    const val ATTRIBUTION = "Attribution"
    const val ATTRIBUTION_NONCOMMERCIAL = "Attribution NonCommercial"

    @JvmStatic
    fun isAllowed(license: String, type: String, allowAttribution: Boolean = false): Boolean {
        if (!type.trim().equals("wav", ignoreCase = true)) return false
        return when (license.trim()) {
            CC0 -> true
            ATTRIBUTION -> allowAttribution
            else -> false
        }
    }

    @JvmStatic
    fun filterAllowed(sounds: List<FreesoundSound>, allowAttribution: Boolean = false): List<FreesoundSound> =
        sounds.filter { isAllowed(it.license, it.type, allowAttribution) }
}

/**
 * Small synchronous client for the Freesound API v2. Construction is offline; network access only
 * happens when one of the request methods is explicitly called.
 */
class FreesoundApiClient(
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
    }

    /** Search once (at most 25 results) and locally exclude disallowed types/licenses. */
    @JvmOverloads
    @Throws(IOException::class)
    fun search(query: String, apiKey: String, allowAttribution: Boolean = false): FreesoundSearchPage {
        require(query.isNotBlank()) { "query must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        val connection = openHttps(buildSearchUrl(query, allowAttribution), "GET")
        connection.setRequestProperty("Authorization", "Token $apiKey")
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw httpError(status)
            val body = connection.inputStream.use { readBounded(it, MAX_JSON_BYTES, "search response") }
            val root = JsonParser(String(body, StandardCharsets.UTF_8)).parse() as? Map<*, *>
                ?: throw IOException("Invalid Freesound search response")
            val rawResults = (root["results"] as? List<*>) ?: emptyList<Any?>()
            val sounds = rawResults.mapNotNull { parseSound(it)?.copy(attributionAllowed = allowAttribution) }
            return FreesoundSearchPage(
                count = root.longValue("count") ?: sounds.size.toLong(),
                next = root.stringValue("next"),
                previous = root.stringValue("previous"),
                results = FreesoundLicensePolicy.filterAllowed(sounds, allowAttribution)
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Exchange an OAuth authorization code using the application's registered client credentials. */
    @Throws(IOException::class)
    fun exchangeAuthorizationCode(
        clientId: String,
        clientSecret: String,
        code: String
    ): FreesoundTokenResponse {
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        require(clientSecret.isNotBlank()) { "clientSecret must not be blank" }
        require(code.isNotBlank()) { "code must not be blank" }
        return requestToken(
            linkedMapOf(
                "grant_type" to "authorization_code",
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "code" to code
            )
        )
    }

    /** Obtain a new access token from an OAuth refresh token. */
    @Throws(IOException::class)
    fun refreshAccessToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String
    ): FreesoundTokenResponse {
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        require(clientSecret.isNotBlank()) { "clientSecret must not be blank" }
        require(refreshToken.isNotBlank()) { "refreshToken must not be blank" }
        val response = requestToken(
            linkedMapOf(
                "grant_type" to "refresh_token",
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "refresh_token" to refreshToken
            )
        )
        return if (response.refreshToken == null) response.copy(refreshToken = refreshToken) else response
    }

    /** Download a policy-approved WAV to a temporary file, validate it, then atomically rename it. */
    @Throws(IOException::class)
    fun downloadWav(
        sound: FreesoundSound,
        accessToken: String,
        destinationDir: File,
        beforeCommit: ((File) -> Unit)? = null
    ): File {
        require(sound.id > 0L) { "sound id must be positive" }
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        require(FreesoundLicensePolicy.isAllowed(sound.license, sound.type, allowAttribution = sound.attributionAllowed)) {
            "Sound license or type is not allowed for automated WAV download"
        }
        if (sound.filesize != null) {
            require(sound.filesize >= 0L) { "sound filesize must not be negative" }
            require(sound.filesize <= MAX_DOWNLOAD_BYTES) { "Sound exceeds the 32 MiB download limit" }
        }
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw IOException("Could not create destination directory")
        }
        if (!destinationDir.isDirectory) throw IOException("Destination is not a directory")

        val finalFile = File(destinationDir, safeWavFilename(sound))
        if (finalFile.exists()) throw IOException("This Freesound sound is already in the local library")
        val temporaryFile = Files.createTempFile(destinationDir.toPath(), ".freesound-${sound.id}-", ".part").toFile()
        try {
            val connection = openDownloadConnection(sound.id, accessToken)
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw httpError(status)
                val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
                if (declaredLength > MAX_DOWNLOAD_BYTES) throw IOException("Sound exceeds the 32 MiB download limit")
                val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                if (contentType == "text/html" || contentType == "application/json") {
                    throw IOException("Freesound returned a non-audio response")
                }
                val written = connection.inputStream.use { input -> copyBounded(input, temporaryFile, MAX_DOWNLOAD_BYTES) }
                if (sound.filesize != null && sound.filesize > 0L && written != sound.filesize) {
                    throw IOException("Downloaded WAV size does not match Freesound metadata")
                }
            } finally {
                connection.disconnect()
            }

            try {
                Files.newInputStream(temporaryFile.toPath()).use { WavDecoder.decode(it) }
            } catch (exception: Exception) {
                throw IOException("Downloaded file is not a valid supported WAV", exception)
            }
            beforeCommit?.invoke(finalFile)
            try {
                Files.move(
                    temporaryFile.toPath(),
                    finalFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (exception: AtomicMoveNotSupportedException) {
                throw IOException("Filesystem does not support atomic WAV finalization", exception)
            }
            return finalFile
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun requestToken(parameters: LinkedHashMap<String, String>): FreesoundTokenResponse {
        val form = parameters.entries.joinToString("&") { (key, value) ->
            "${encodeQueryParameter(key)}=${encodeQueryParameter(value)}"
        }.toByteArray(StandardCharsets.UTF_8)
        val connection = openHttps(OAUTH_TOKEN_ENDPOINT, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setFixedLengthStreamingMode(form.size)
        try {
            connection.outputStream.use { it.write(form) }
            val status = connection.responseCode
            if (status !in 200..299) throw httpError(status)
            val bytes = connection.inputStream.use { readBounded(it, MAX_JSON_BYTES, "token response") }
            val root = JsonParser(String(bytes, StandardCharsets.UTF_8)).parse() as? Map<*, *>
                ?: throw IOException("Invalid Freesound token response")
            val accessToken = root.stringValue("access_token")?.takeIf { it.isNotBlank() }
                ?: throw IOException("Freesound token response is missing access_token")
            val expiresIn = root.longValue("expires_in")?.takeIf { it >= 0L }
            val expiresAt = expiresIn?.let { seconds ->
                val now = System.currentTimeMillis()
                if (seconds > (Long.MAX_VALUE - now) / 1_000L) Long.MAX_VALUE else now + seconds * 1_000L
            }
            return FreesoundTokenResponse(
                accessToken = accessToken,
                refreshToken = root.stringValue("refresh_token"),
                expiresInSeconds = expiresIn,
                tokenType = root.stringValue("token_type") ?: "Bearer",
                scope = root.stringValue("scope"),
                expiresAtEpochMillis = expiresAt
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openDownloadConnection(soundId: Long, accessToken: String): HttpsURLConnection {
        var current = validatedHttpsUrl("$SOUNDS_ENDPOINT/$soundId/download/")
        for (redirectCount in 0..MAX_DOWNLOAD_REDIRECTS) {
            val connection = openHttps(current.toExternalForm(), "GET")
            // Never forward the bearer credential to a CDN or other redirect host.
            if (current.host.equals(API_HOST, ignoreCase = true)) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            connection.setRequestProperty("Accept", "audio/wav, application/octet-stream, */*")
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || redirectCount == MAX_DOWNLOAD_REDIRECTS) {
                    throw IOException("Freesound download redirect could not be followed")
                }
                current = validatedHttpsUrl(URL(current, location).toExternalForm())
                continue
            }
            return connection
        }
        throw IOException("Freesound download redirect limit exceeded")
    }

    private fun openHttps(rawUrl: String, method: String): HttpsURLConnection {
        val url = validatedHttpsUrl(rawUrl)
        val connection = url.openConnection() as? HttpsURLConnection
            ?: throw IOException("Only HTTPS connections are allowed")
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        return connection
    }

    private fun validatedHttpsUrl(rawUrl: String): URL {
        val url = try {
            URL(rawUrl)
        } catch (exception: Exception) {
            throw IOException("Invalid Freesound URL", exception)
        }
        if (!url.protocol.equals("https", ignoreCase = true) || url.host.isBlank() || url.userInfo != null) {
            throw IOException("Only HTTPS URLs without embedded credentials are allowed")
        }
        return url
    }

    private fun httpError(status: Int): IOException = IOException("Freesound request failed (HTTP $status)")

    companion object {
        private const val API_HOST = "freesound.org"
        private const val SEARCH_ENDPOINT = "https://freesound.org/apiv2/search/"
        private const val OAUTH_TOKEN_ENDPOINT = "https://freesound.org/apiv2/oauth2/access_token/"
        private const val SOUNDS_ENDPOINT = "https://freesound.org/apiv2/sounds"
        private const val SEARCH_FIELDS = "id,name,username,license,type,duration,filesize,url,download,previews,tags"
        private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 15_000
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000
        private const val MAX_JSON_BYTES = 4 * 1024 * 1024
        private const val MAX_DOWNLOAD_BYTES = 32L * 1024L * 1024L
        private const val MAX_DOWNLOAD_REDIRECTS = 5

        /** Visible to local JVM tests; URLEncoder's '+' spaces are valid form-style URL encoding. */
        internal fun encodeQueryParameter(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        /** Deterministic URL builder keeps every user/API-supplied value properly encoded. */
        internal fun buildSearchUrl(query: String, allowAttribution: Boolean): String {
            val filter = if (allowAttribution) {
                "license:(\"${FreesoundLicensePolicy.CC0}\" OR \"${FreesoundLicensePolicy.ATTRIBUTION}\") type:wav"
            } else {
                "license:\"${FreesoundLicensePolicy.CC0}\" type:wav"
            }
            val parameters = listOf(
                "query" to query,
                "filter" to filter,
                "fields" to SEARCH_FIELDS,
                "page_size" to "50"
            )
            return SEARCH_ENDPOINT + "?" + parameters.joinToString("&") { (key, value) ->
                "${encodeQueryParameter(key)}=${encodeQueryParameter(value)}"
            }
        }

        private fun safeWavFilename(sound: FreesoundSound): String {
            var stem = sound.name.trim()
            if (stem.endsWith(".wav", ignoreCase = true)) stem = stem.dropLast(4)
            stem = stem.replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .trim('.', '_', '-', ' ')
                .take(80)
                .ifBlank { "sound" }
            return "freesound_${sound.id}_$stem.wav"
        }

        private fun parseSound(value: Any?): FreesoundSound? {
            val map = value as? Map<*, *> ?: return null
            val id = map.longValue("id")?.takeIf { it > 0L } ?: return null
            val previews = (map["previews"] as? Map<*, *>)
                ?.mapNotNull { (key, previewUrl) ->
                    if (key is String && previewUrl is String) key to previewUrl else null
                }?.toMap() ?: emptyMap()
            val tags = (map["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            return FreesoundSound(
                id = id,
                name = map.stringValue("name") ?: "",
                username = map.stringValue("username") ?: "",
                license = map.stringValue("license") ?: "",
                type = map.stringValue("type") ?: "",
                duration = map.doubleValue("duration"),
                filesize = map.longValue("filesize"),
                url = map.stringValue("url"),
                download = map.stringValue("download"),
                previews = previews,
                tags = tags
            )
        }

        private fun readBounded(input: InputStream, limit: Int, description: String): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val one = input.read()
                    if (one < 0) break
                    if (output.size() >= limit) throw IOException("Freesound $description is too large")
                    output.write(one)
                } else {
                    if (count > limit - output.size()) throw IOException("Freesound $description is too large")
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        }

        private fun copyBounded(input: InputStream, destination: File, limit: Long): Long {
            var total = 0L
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val one = input.read()
                        if (one < 0) break
                        if (total >= limit) throw IOException("Sound exceeds the 32 MiB download limit")
                        output.write(one)
                        total++
                    } else {
                        if (count.toLong() > limit - total) throw IOException("Sound exceeds the 32 MiB download limit")
                        output.write(buffer, 0, count)
                        total += count
                    }
                }
                output.fd.sync()
            }
            return total
        }
    }
}

private fun Map<*, *>.stringValue(key: String): String? = this[key] as? String

private fun Map<*, *>.longValue(key: String): Long? = when (val value = this[key]) {
    is Long -> value
    is Int -> value.toLong()
    is Double -> if (value.isFinite() && value % 1.0 == 0.0) value.toLong() else null
    is String -> value.toLongOrNull()
    else -> null
}

private fun Map<*, *>.doubleValue(key: String): Double? = when (val value = this[key]) {
    is Number -> value.toDouble().takeIf { it.isFinite() }
    is String -> value.toDoubleOrNull()?.takeIf { it.isFinite() }
    else -> null
}

/** Minimal strict JSON reader for API objects; avoids Android-only org.json and extra libraries. */
internal class JsonParser(private val source: String) {
    private var position = 0
    private val maxNestingDepth = 64

    fun parse(): Any? {
        skipWhitespace()
        val value = readValue(0)
        skipWhitespace()
        if (position != source.length) fail()
        return value
    }

    private fun readValue(depth: Int): Any? {
        if (depth > maxNestingDepth) fail()
        skipWhitespace()
        if (position >= source.length) fail()
        return when (source[position]) {
            '{' -> readObject(depth)
            '[' -> readArray(depth)
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", null)
            '-', in '0'..'9' -> readNumber()
            else -> fail()
        }
    }

    private fun readObject(depth: Int): Map<String, Any?> {
        position++
        skipWhitespace()
        val result = linkedMapOf<String, Any?>()
        if (consume('}')) return result
        while (true) {
            skipWhitespace()
            if (position >= source.length || source[position] != '"') fail()
            val key = readString()
            skipWhitespace()
            expect(':')
            result[key] = readValue(depth + 1)
            skipWhitespace()
            if (consume('}')) return result
            expect(',')
        }
    }

    private fun readArray(depth: Int): List<Any?> {
        position++
        skipWhitespace()
        val result = mutableListOf<Any?>()
        if (consume(']')) return result
        while (true) {
            result += readValue(depth + 1)
            skipWhitespace()
            if (consume(']')) return result
            expect(',')
        }
    }

    private fun readString(): String {
        expect('"')
        val result = StringBuilder()
        while (position < source.length) {
            val character = source[position++]
            when {
                character == '"' -> return result.toString()
                character == '\\' -> {
                    if (position >= source.length) fail()
                    when (val escaped = source[position++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (position + 4 > source.length) fail()
                            val code = source.substring(position, position + 4).toIntOrNull(16) ?: fail()
                            result.append(code.toChar())
                            position += 4
                        }
                        else -> fail()
                    }
                }
                character < ' ' -> fail()
                else -> result.append(character)
            }
        }
        fail()
    }

    private fun readNumber(): Number {
        val start = position
        consume('-')
        if (consume('0')) {
            if (position < source.length && source[position].isDigit()) fail()
        } else {
            readDigits(required = true)
        }
        if (consume('.')) readDigits(required = true)
        if (consume('e') || consume('E')) {
            if (!consume('+')) consume('-')
            readDigits(required = true)
        }
        val token = source.substring(start, position)
        return if ('.' !in token && 'e' !in token.lowercase(Locale.ROOT)) {
            token.toLongOrNull() ?: token.toDoubleOrNull() ?: fail()
        } else {
            token.toDoubleOrNull() ?: fail()
        }
    }

    private fun readDigits(required: Boolean) {
        val start = position
        while (position < source.length && source[position].isDigit()) position++
        if (required && start == position) fail()
    }

    private fun <T> readLiteral(token: String, value: T): T {
        if (!source.startsWith(token, position)) fail()
        position += token.length
        return value
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position] in charArrayOf(' ', '\t', '\r', '\n')) position++
    }

    private fun consume(expected: Char): Boolean {
        if (position < source.length && source[position] == expected) {
            position++
            return true
        }
        return false
    }

    private fun expect(expected: Char) {
        if (!consume(expected)) fail()
    }

    private fun fail(): Nothing = throw IOException("Malformed Freesound JSON")
}
