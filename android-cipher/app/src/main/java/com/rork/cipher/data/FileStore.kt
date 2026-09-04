package com.rork.cipher.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.SecretKey

/** What a picked document tells us about itself before it is sealed. */
data class PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String
)

/**
 * Reads picked documents and caches sealed ones on the way back.
 *
 * The cache holds ciphertext only, exactly like photos and voice notes. A file
 * differs from them in one honest way: the whole point of receiving one is to
 * hand it to another app, so opening or saving a file deliberately writes it
 * out in the clear — but only at the moment the user asks for it, never as a
 * side effect of the message arriving.
 */
class FileStore(context: Context) {

    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val cacheDir = File(app.cacheDir, "files").apply { mkdirs() }
    private val openDir = File(app.cacheDir, "open").apply { mkdirs() }

    /** Reads a picked document's name, size and type without copying it. */
    suspend fun inspect(uri: Uri): PickedFile? = withContext(Dispatchers.IO) {
        runCatching {
            var name = "file"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
                }
            }
            PickedFile(
                uri = uri,
                name = name.take(120),
                size = size,
                mime = resolver.getType(uri) ?: "application/octet-stream"
            )
        }.getOrElse {
            Log.w(TAG, "could not inspect a picked file: ${it.message}")
            null
        }
    }

    /** Reads the whole document into memory so it can be sealed in one piece. */
    suspend fun read(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrElse {
            Log.w(TAG, "could not read a picked file: ${it.message}")
            null
        }
    }

    fun isCached(blobId: String): Boolean = File(cacheDir, blobId).exists()

    /** Decrypts a cached file, or null when it has not been downloaded yet. */
    suspend fun fromCache(blobId: String, key: SecretKey): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, blobId)
        if (!file.exists()) return@withContext null
        val cipher = runCatching { file.readText() }.getOrNull() ?: return@withContext null
        CryptoBox.decryptBytes(cipher, key)
    }

    /** Stores the sealed bytes, never the readable ones. */
    suspend fun store(blobId: String, cipher: String) {
        withContext(Dispatchers.IO) {
            runCatching { File(cacheDir, blobId).writeText(cipher) }
                .onFailure { Log.w(TAG, "file cache write failed") }
        }
    }

    /**
     * Writes a decrypted file where another app can open it.
     *
     * This is the one place Cipher puts plaintext on disk on purpose. It lands
     * in a private cache directory shared only through a content URI, and
     * [clearOpened] wipes it when the vault locks.
     */
    suspend fun stageForOpen(name: String, bytes: ByteArray): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifEmpty { "file" }
            val target = File(openDir, safe)
            target.writeBytes(bytes)
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", target)
        }.getOrElse {
            Log.w(TAG, "could not stage a file for opening: ${it.message}")
            null
        }
    }

    /**
     * Copies a decrypted file into the phone's Downloads folder.
     *
     * @return the display name written, or null when the save failed.
     */
    suspend fun saveToDownloads(name: String, mime: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val safe = name.replace(Regex("[/\\\\]"), "_").take(120).ifEmpty { "file" }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safe)
                        put(MediaStore.Downloads.MIME_TYPE, mime.ifEmpty { "application/octet-stream" })
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val target = resolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    ) ?: return@withContext null
                    resolver.openOutputStream(target)?.use { it.write(bytes) }
                        ?: return@withContext null
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(target, values, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    val downloads =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloads.mkdirs()
                    File(downloads, safe).writeBytes(bytes)
                }
                safe
            }.getOrElse {
                Log.w(TAG, "could not save a file: ${it.message}")
                null
            }
        }

    suspend fun forget(blobId: String) {
        withContext(Dispatchers.IO) { runCatching { File(cacheDir, blobId).delete() } }
    }

    /** Wipes the plaintext staging area; called whenever the vault is locked. */
    suspend fun clearOpened() {
        withContext(Dispatchers.IO) { runCatching { openDir.listFiles()?.forEach { it.delete() } } }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
            runCatching { openDir.listFiles()?.forEach { it.delete() } }
        }
    }

    private companion object {
        const val TAG = "FileStore"
    }
}
