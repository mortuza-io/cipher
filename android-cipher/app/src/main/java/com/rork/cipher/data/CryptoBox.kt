package com.rork.cipher.data

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * All cryptography for Cipher.
 *
 * The account key never leaves the device. Two independent values are derived
 * from it with domain separation: an auth digest the server uses as an opaque
 * account handle, and a vault key that encrypts local storage and seals the
 * account's private exchange key. Message bodies are encrypted under a
 * conversation key derived by ECDH between the two accounts, so the server
 * only ever stores opaque ciphertext.
 */
object CryptoBox {

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"
    private const val GROUPS = 16
    private const val GROUP_SIZE = 4
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val CURVE = "secp256r1"

    private const val PIN_ITERATIONS = 60_000

    private val random = SecureRandom()

    /** Generates a 64-character account key formatted as 16 dash-separated blocks. */
    fun generateAccountKey(): String = (0 until GROUPS).joinToString("-") {
        buildString {
            repeat(GROUP_SIZE) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }

    /** Splits a key into rows of four blocks for the key card layout. */
    fun keyRows(accountKey: String): List<String> =
        accountKey.split("-").chunked(4).map { it.joinToString("-") }

    fun normalizeKey(raw: String): String {
        val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }
        return cleaned.chunked(GROUP_SIZE).joinToString("-")
    }

    fun isKeyShaped(raw: String): Boolean =
        raw.uppercase().count { it.isLetterOrDigit() } == GROUPS * GROUP_SIZE

    fun sha256Hex(input: String): String = sha256(input.toByteArray())
        .joinToString("") { "%02X".format(it) }

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { digest.update(it) }
        return digest.digest()
    }

    /** Short human-comparable fingerprint, e.g. `4F2A·9CD1·77BE`. */
    fun fingerprint(input: String): String =
        sha256Hex(input).take(12).chunked(4).joinToString("·")

    /**
     * The code two people read to each other to prove nobody is in the middle.
     *
     * It is derived from both public keys in a fixed order, so the same number
     * appears on both phones — and a different number the moment either key is
     * swapped, which is exactly what an interception looks like.
     */
    fun safetyNumber(myPublicKey: String, peerPublicKey: String): List<String> {
        val pair = listOf(myPublicKey, peerPublicKey).sorted().joinToString("|")
        return sha256Hex("cipher.verify.v1|$pair").take(48).chunked(4)
    }

    /** The safety number as one line, which is what a QR code carries. */
    fun safetyCode(myPublicKey: String, peerPublicKey: String): String =
        safetyNumber(myPublicKey, peerPublicKey).joinToString("")

    /** Opaque account handle sent to the server. Never doubles as an encryption key. */
    fun authDigest(accountKey: String): String = sha256Hex("cipher.auth.v1|$accountKey")

    /** AES key protecting the on-device vault and the sealed private key. */
    fun vaultKey(accountKey: String): SecretKey =
        SecretKeySpec(sha256("cipher.vault.v1|$accountKey".toByteArray()), "AES")

    // -------------------------------------------------------------- exchange

    fun generateExchangeKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(CURVE))
        return generator.generateKeyPair()
    }

    fun encodePublicKey(key: PublicKey): String =
        Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    fun decodePublicKey(encoded: String): PublicKey? = runCatching {
        KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.decode(encoded, Base64.NO_WRAP)))
    }.getOrNull()

    /** Wraps the private exchange key so it can be stored server-side safely. */
    fun sealPrivateKey(key: PrivateKey, vault: SecretKey): String =
        encryptBytes(key.encoded, vault)

    fun unsealPrivateKey(sealed: String, vault: SecretKey): PrivateKey? = runCatching {
        val raw = decryptBytes(sealed, vault) ?: return null
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(raw))
    }.getOrNull()

    /**
     * Shared AES key for a conversation. Both sides compute the same value:
     * ECDH(secret) mixed with the two usernames in a stable order.
     */
    fun conversationKey(
        privateKey: PrivateKey,
        peerPublicKey: PublicKey,
        me: String,
        peer: String
    ): SecretKey? = runCatching {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        val pair = listOf(me, peer).sorted().joinToString("|")
        SecretKeySpec(
            sha256("cipher.convo.v1|$pair|".toByteArray(), agreement.generateSecret()),
            "AES"
        )
    }.getOrNull()

    // ----------------------------------------------------------------- rooms

    /** Short opaque id for a group room. */
    fun randomId(): String = randomToken(8)

    /** URL-safe random secret, used as a group invite token. */
    fun randomToken(bytes: Int = 16): String {
        val raw = ByteArray(bytes).also { random.nextBytes(it) }
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // ------------------------------------------------------------- app lock

    fun randomSalt(): String {
        val raw = ByteArray(16).also { random.nextBytes(it) }
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    /**
     * Stretches a screen-lock PIN into an AES key. The PIN alone is short, so
     * the work factor is what stands between a stolen phone and the vault.
     */
    fun pinKey(pin: String, salt: String): SecretKey? = runCatching {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            PIN_ITERATIONS,
            256
        )
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        SecretKeySpec(raw, "AES")
    }.getOrNull()

    // ---------------------------------------------------------------- media

    /** A throwaway AES key for a single attachment. */
    fun generateMediaKey(): SecretKey {
        val raw = ByteArray(32).also { random.nextBytes(it) }
        return SecretKeySpec(raw, "AES")
    }

    fun encodeSecret(key: SecretKey): String =
        Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    fun decodeSecret(encoded: String): SecretKey? = runCatching {
        SecretKeySpec(Base64.decode(encoded, Base64.NO_WRAP), "AES")
    }.getOrNull()

    // ------------------------------------------------------------ encryption

    fun encrypt(plain: String, key: SecretKey): String = encryptBytes(plain.toByteArray(), key)

    fun decrypt(payload: String, key: SecretKey): String? =
        decryptBytes(payload, key)?.let { String(it) }

    fun encryptBytes(plain: ByteArray, key: SecretKey): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return Base64.encodeToString(iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    fun decryptBytes(payload: String, key: SecretKey): ByteArray? = runCatching {
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, raw.copyOfRange(0, IV_BYTES))
        )
        cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size))
    }.getOrNull()
}
