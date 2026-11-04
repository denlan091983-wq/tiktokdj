package de.laermbatz.app.freesound

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials for one locally configured Freesound application/account. */
data class FreesoundCredentials(
    val clientId: String = "",
    /** Freesound labels this value "Client secret/Api key" in the credential page. */
    val clientSecretOrApiKey: String = "",
    val registeredRedirectUri: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val accessTokenExpiresAtMillis: Long = 0L,
    val apiUseConfirmed: Boolean = false,
    val allowAttributionLicense: Boolean = false
)

/**
 * Stores Freesound secrets encrypted at rest with a device-bound Android Keystore key.
 * This does not make a credential safe to ship in a public APK; users should configure
 * their own credential and never commit it to source control.
 */
class FreesoundCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): FreesoundCredentials = FreesoundCredentials(
        clientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
        clientSecretOrApiKey = decryptOrEmpty(KEY_CLIENT_SECRET),
        registeredRedirectUri = prefs.getString(KEY_REDIRECT_URI, "").orEmpty(),
        accessToken = decryptOrEmpty(KEY_ACCESS_TOKEN),
        refreshToken = decryptOrEmpty(KEY_REFRESH_TOKEN),
        accessTokenExpiresAtMillis = prefs.getLong(KEY_TOKEN_EXPIRY, 0L),
        apiUseConfirmed = prefs.getBoolean(KEY_API_USE_CONFIRMED, false),
        allowAttributionLicense = prefs.getBoolean(KEY_ALLOW_ATTRIBUTION, false)
    )

    @Synchronized
    fun save(value: FreesoundCredentials) {
        prefs.edit()
            .putString(KEY_CLIENT_ID, value.clientId.trim())
            .putString(KEY_CLIENT_SECRET, encrypt(value.clientSecretOrApiKey.trim()))
            .putString(KEY_REDIRECT_URI, value.registeredRedirectUri.trim())
            .putString(KEY_ACCESS_TOKEN, encrypt(value.accessToken.trim()))
            .putString(KEY_REFRESH_TOKEN, encrypt(value.refreshToken.trim()))
            .putLong(KEY_TOKEN_EXPIRY, value.accessTokenExpiresAtMillis)
            .putBoolean(KEY_API_USE_CONFIRMED, value.apiUseConfirmed)
            .putBoolean(KEY_ALLOW_ATTRIBUTION, value.allowAttributionLicense)
            .apply()
    }

    @Synchronized
    fun saveTokens(accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, encrypt(accessToken))
            .putString(KEY_REFRESH_TOKEN, encrypt(refreshToken))
            .putLong(KEY_TOKEN_EXPIRY, expiresAtMillis)
            .apply()
    }

    @Synchronized
    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .apply()
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + cipherText
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decryptOrEmpty(key: String): String {
        val encoded = prefs.getString(key, null) ?: return ""
        if (encoded.isBlank()) return ""
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > GCM_IV_BYTES) { "Encrypted credential is truncated" }
            val iv = packed.copyOfRange(0, GCM_IV_BYTES)
            val cipherText = packed.copyOfRange(GCM_IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "freesound_credentials"
        const val KEY_ALIAS = "laermbatz_freesound_aes_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_CLIENT_SECRET = "client_secret_api_key"
        const val KEY_REDIRECT_URI = "redirect_uri"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_TOKEN_EXPIRY = "access_token_expires_at"
        const val KEY_API_USE_CONFIRMED = "api_use_confirmed"
        const val KEY_ALLOW_ATTRIBUTION = "allow_attribution"
    }
}
