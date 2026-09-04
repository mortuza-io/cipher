package com.rork.cipher.data

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.SecretKey

/**
 * Cache for encrypted voice notes.
 *
 * Exactly the same rule as photos: what lands on disk is the ciphertext the
 * hub returned, and the decoded audio only ever exists in memory. Nothing here
 * can be recovered by pulling the cache directory off the device.
 */
class VoiceStore(context: Context) {

    private val cacheDir = File(context.applicationContext.cacheDir, "voice").apply { mkdirs() }

    private val memory = object : LruCache<String, ByteArray>(MEMORY_BUDGET) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun cached(blobId: String): ByteArray? = memory.get(blobId)

    /** Decrypts a cached note, or null when it has not been fetched yet. */
    suspend fun fromCache(blobId: String, key: SecretKey): ByteArray? {
        memory.get(blobId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(cacheDir, blobId)
            if (!file.exists()) return@withContext null
            val cipher = runCatching { file.readText() }.getOrNull() ?: return@withContext null
            CryptoBox.decryptBytes(cipher, key)?.also { memory.put(blobId, it) }
        }
    }

    /** Stores ciphertext on disk and the audio in memory only. */
    suspend fun store(blobId: String, cipher: String, plain: ByteArray) {
        memory.put(blobId, plain)
        withContext(Dispatchers.IO) {
            runCatching { File(cacheDir, blobId).writeText(cipher) }
                .onFailure { Log.w(TAG, "voice cache write failed") }
        }
    }

    suspend fun forget(blobId: String) {
        memory.remove(blobId)
        withContext(Dispatchers.IO) { runCatching { File(cacheDir, blobId).delete() } }
    }

    suspend fun clear() {
        memory.evictAll()
        withContext(Dispatchers.IO) {
            runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
        }
    }

    private companion object {
        const val TAG = "VoiceStore"
        const val MEMORY_BUDGET = 8 * 1024 * 1024
    }
}
