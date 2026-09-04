package com.rork.cipher.data

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import kotlin.math.roundToInt

/** Frame presets offered while cropping. `ratio` is width ÷ height; null is freehand. */
enum class CropShape(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 0.8f),
    WIDE("16:9", 16f / 9f)
}

/**
 * The part of an image that is kept, as fractions of the rotated picture.
 *
 * Fractions rather than pixels: the crop is chosen on a screen-sized rehearsal
 * of the photo and applied to the full-size original, and the two have no
 * dimensions in common.
 */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val isFull: Boolean
        get() = left <= 0.002f && top <= 0.002f && right >= 0.998f && bottom >= 0.998f

    /** Follows the picture through a 90° clockwise turn. */
    fun turnedClockwise(): CropRect = CropRect(
        left = 1f - bottom,
        top = left,
        right = 1f - top,
        bottom = right
    )

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)

        /**
         * The largest centred rect of `ratio` (width ÷ height) that fits an
         * image of `width` × `height` pixels.
         */
        fun centred(ratio: Float, width: Int, height: Int): CropRect {
            if (width <= 0 || height <= 0) return FULL
            val current = width.toFloat() / height
            return if (current > ratio) {
                val fraction = ratio / current
                val inset = (1f - fraction) / 2f
                CropRect(inset, 0f, 1f - inset, 1f)
            } else {
                val fraction = current / ratio
                val inset = (1f - fraction) / 2f
                CropRect(0f, inset, 1f, 1f - inset)
            }
        }
    }
}

/**
 * What the sender did to a picked image before sending it.
 *
 * The edit is a description, not a bitmap: it is carried through the preview
 * untouched and only applied once, on the full-size image, at the moment the
 * photo is sealed. Nothing edited is ever written to the phone's storage.
 */
data class PhotoEdit(
    val rotation: Int = 0,
    val crop: CropRect? = null
) {
    val isPlain: Boolean
        get() = rotation % 360 == 0 && (crop == null || crop.isFull)

    /** Turns the picture, carrying any crop around with it. */
    fun turned(): PhotoEdit = copy(
        rotation = (rotation + 90) % 360,
        crop = crop?.turnedClockwise()
    )
}

/** One picked image on its way out: what it is, how it was edited, how it travels. */
data class PhotoDraft(
    val uri: Uri,
    val edit: PhotoEdit = PhotoEdit(),
    /** Locked photos can be looked at once opened, but never kept. */
    val locked: Boolean = false
)

/** Applies a rotation and then the crop the sender drew, in that order. */
fun applyEdit(source: Bitmap, edit: PhotoEdit): Bitmap {
    if (edit.isPlain) return source
    var working = source
    val turn = ((edit.rotation % 360) + 360) % 360
    if (turn != 0) {
        val matrix = Matrix().apply { postRotate(turn.toFloat()) }
        val rotated = Bitmap.createBitmap(working, 0, 0, working.width, working.height, matrix, true)
        if (rotated !== working && working !== source) working.recycle()
        working = rotated
    }
    val crop = edit.crop
    if (crop != null && !crop.isFull) {
        val cropped = cut(working, crop)
        if (cropped !== working && working !== source) working.recycle()
        working = cropped
    }
    return working
}

private fun cut(bitmap: Bitmap, rect: CropRect): Bitmap {
    val left = (rect.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
    val top = (rect.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
    val width = (rect.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width - left)
    val height = (rect.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height - top)
    if (left == 0 && top == 0 && width == bitmap.width && height == bitmap.height) return bitmap
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}
