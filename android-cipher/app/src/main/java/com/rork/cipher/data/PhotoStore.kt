package com.rork.cipher.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.SecretKey

/** How a "save to gallery" ended, so the viewer can say what actually happened. */
enum class PhotoSaveResult {
    SAVED,

    /** The sender sent it locked: it may be looked at, never kept. */
    LOCKED,

    /** The photo could not be fetched or decrypted on this device. */
    UNREADABLE,

    /** The phone's gallery refused the write. */
    WRITE_FAILED
}

/** A photo that has been downscaled, compressed and is ready to be sealed. */
data class PreparedPhoto(
    val bytes: ByteArray,
    val thumb: String,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Turns picked images into encrypted payloads and caches them on the way back.
 *
 * Nothing readable is ever written to disk: the cache stores exactly the
 * ciphertext the hub returned, and decryption happens in memory only.
 */
class PhotoStore(context: Context) {

    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val cacheDir = File(app.cacheDir, "photos").apply { mkdirs() }

    /** Decoded bytes, keyed by blob id. Bounded so large threads cannot bloat memory. */
    private val memory = object : LruCache<String, ByteArray>(MEMORY_BUDGET) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    /** Reads, rotates, downscales and compresses a picked image. */
    suspend fun prepare(uri: Uri, edit: PhotoEdit = PhotoEdit()): PreparedPhoto? =
        withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null
            val oriented = applyOrientation(uri, decoded)
            // The sender's edit is applied here, once, to the image that is
            // actually sealed — the preview only ever showed a rehearsal of it.
            val upright = applyEdit(oriented, edit)
            val scaled = downscale(upright, MAX_EDGE)

            val bytes = compressUnder(scaled, MAX_BYTES, 82)
            val thumbBitmap = downscale(scaled, THUMB_EDGE)
            val thumbBytes = compressUnder(thumbBitmap, THUMB_BYTES, 45)
            if (thumbBitmap !== scaled) thumbBitmap.recycle()

            val result = PreparedPhoto(
                bytes = bytes,
                thumb = Base64.encodeToString(thumbBytes, Base64.NO_WRAP),
                width = scaled.width,
                height = scaled.height
            )
            if (scaled !== upright) scaled.recycle()
            if (upright !== oriented) upright.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            result
        }.getOrElse {
            Log.w(TAG, "could not prepare photo: ${it.message}")
            null
        }
    }

    fun cached(blobId: String): ByteArray? = memory.get(blobId)

    /**
     * A screen-sized rehearsal of what will be sent, for the send preview.
     *
     * Decoded small on purpose: the preview redraws on every tap of a filter or
     * a frame, and a full-resolution round trip would make those taps stutter.
     */
    suspend fun preview(
        uri: Uri,
        edit: PhotoEdit = PhotoEdit(),
        maxEdge: Int = PREVIEW_EDGE
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = previewSample(bounds.outWidth, bounds.outHeight, maxEdge)
                }
                val decoded = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@withContext null
                val oriented = applyOrientation(uri, decoded)
                val edited = applyEdit(oriented, edit)
                val scaled = downscale(edited, maxEdge)
                if (scaled !== edited && edited !== oriented) edited.recycle()
                if (oriented !== decoded && oriented !== scaled) oriented.recycle()
                if (decoded !== scaled && decoded !== oriented) decoded.recycle()
                scaled
            }.getOrElse {
                Log.w(TAG, "could not preview photo: ${it.message}")
                null
            }
        }

    /**
     * Copies a decrypted photo into the phone's gallery.
     *
     * This is the one place a Cipher photo is written somewhere readable, and
     * it only happens because the person looking at it asked for it by name.
     */
    suspend fun saveToGallery(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        // Three ways in, because one phone's media store is not another's: the
        // album on the primary volume first, then whatever volume the device
        // calls external, and finally a plain file the scanner is told about.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            writeThrough(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), bytes)
        ) {
            return@withContext true
        }
        if (writeThrough(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, bytes)) {
            return@withContext true
        }
        writeAsFile(bytes)
    }

    /** Inserts a gallery row and fills it, cleaning up if the write fails. */
    private fun writeThrough(collection: Uri, bytes: ByteArray): Boolean = runCatching {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val seconds = System.currentTimeMillis() / 1000L
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, seconds)
            put(MediaStore.Images.Media.DATE_MODIFIED, seconds)
            if (modern) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Cipher"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val target = resolver.insert(collection, values) ?: return false
        val written = runCatching {
            resolver.openOutputStream(target)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } != null
        }.getOrElse {
            Log.w(TAG, "gallery write refused: ${it.message}")
            false
        }
        if (!written) {
            runCatching { resolver.delete(target, null, null) }
            return false
        }
        if (modern) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
        }
        true
    }.getOrElse {
        Log.w(TAG, "gallery insert failed: ${it.message}")
        false
    }

    /**
     * Last resort for phones whose media store will not take an insert: write
     * the file into the public Pictures album and tell the scanner about it.
     */
    private fun writeAsFile(bytes: ByteArray): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val album = File(pictures, "Cipher")
        if (!album.exists() && !album.mkdirs()) return false
        val file = File(album, fileName())
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(
            app,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null
        )
        true
    }.getOrElse {
        Log.w(TAG, "gallery file save failed: ${it.message}")
        false
    }

    private fun fileName(): String = "cipher-${System.currentTimeMillis()}.jpg"

    /**
     * Writes decrypted bytes to a short-lived cache file so a photo can be
     * re-sealed for somebody else. The hub hands a blob only to the account it
     * was uploaded for, so a forward has to travel as a fresh upload.
     */
    suspend fun stage(bytes: ByteArray): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(cacheDir, "forward-${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            Uri.fromFile(file)
        }.getOrElse {
            Log.w(TAG, "could not stage a forwarded photo")
            null
        }
    }

    /** Decrypts a cached attachment, or null when it has not been fetched yet. */
    suspend fun fromCache(blobId: String, key: SecretKey): ByteArray? {
        memory.get(blobId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(cacheDir, blobId)
            if (!file.exists()) return@withContext null
            val cipher = runCatching { file.readText() }.getOrNull() ?: return@withContext null
            CryptoBox.decryptBytes(cipher, key)?.also { memory.put(blobId, it) }
        }
    }

    /** Stores ciphertext on disk and the plaintext in memory only. */
    suspend fun store(blobId: String, cipher: String, plain: ByteArray) {
        memory.put(blobId, plain)
        withContext(Dispatchers.IO) {
            runCatching { File(cacheDir, blobId).writeText(cipher) }
                .onFailure { Log.w(TAG, "photo cache write failed") }
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

    private fun previewSample(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_EDGE * 2) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun applyOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /** Compresses to JPEG, stepping quality down until the payload fits. */
    private fun compressUnder(bitmap: Bitmap, limit: Int, startQuality: Int): ByteArray {
        var quality = startQuality
        var bytes = ByteArray(0)
        while (quality >= 30) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            if (bytes.size <= limit) return bytes
            quality -= 12
        }
        return bytes
    }

    private companion object {
        const val TAG = "PhotoStore"
        const val MAX_EDGE = 1440
        const val PREVIEW_EDGE = 1280
        const val MAX_BYTES = 700_000
        const val THUMB_EDGE = 96
        const val THUMB_BYTES = 4_000
        const val MEMORY_BUDGET = 12 * 1024 * 1024
    }
}
