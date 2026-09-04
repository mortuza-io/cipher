package com.rork.cipher.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds a copy of the account key behind the phone's own biometric hardware.
 *
 * The wrapping key lives in the Android keystore and is minted with
 * `setUserAuthenticationRequired`, so the OS — not this app — decides whether a
 * fingerprint or face was really presented. Nothing here is usable without a
 * fresh authentication, and the key is destroyed when biometrics change.
 */
object BiometricVault {

    private const val ALIAS = "cipher.biometric.v1"
    private const val STORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    /** Seconds an authentication stays good for, so seal/open can follow a prompt. */
    private const val AUTH_WINDOW_SECONDS = 20

    private fun keystore(): KeyStore? = runCatching {
        KeyStore.getInstance(STORE).apply { load(null) }
    }.getOrNull()

    /** True once a secret has been sealed and the wrapping key still exists. */
    fun isReady(): Boolean = runCatching {
        keystore()?.containsAlias(ALIAS) == true
    }.getOrDefault(false)

    private fun mintKey(): SecretKey? = runCatching {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        AUTH_WINDOW_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS)
                }
            }
            .build()
        generator.init(spec)
        generator.generateKey()
    }.onFailure { Log.w(TAG, "keystore key could not be minted") }.getOrNull()

    private fun existingKey(): SecretKey? = runCatching {
        keystore()?.getKey(ALIAS, null) as? SecretKey
    }.getOrNull()

    /**
     * Wraps a secret for later biometric retrieval. Must be called moments after
     * a successful biometric prompt, while the authentication window is open.
     */
    fun seal(secret: String): String? = runCatching {
        clear()
        val key = mintKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val payload = cipher.iv + cipher.doFinal(secret.toByteArray())
        Base64.encodeToString(payload, Base64.NO_WRAP)
    }.onFailure { Log.w(TAG, "biometric seal failed") }.getOrNull()

    /** Unwraps the secret. Returns null unless a biometric prompt just succeeded. */
    fun open(sealed: String): String? = runCatching {
        val key = existingKey() ?: return null
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, raw.copyOfRange(0, IV_BYTES))
        )
        String(cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size)))
    }.onFailure { Log.w(TAG, "biometric open failed") }.getOrNull()

    fun clear() {
        runCatching { keystore()?.deleteEntry(ALIAS) }
    }

    private const val TAG = "BiometricVault"
}
