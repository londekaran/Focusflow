package com.example.focusflow.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** The profile picture: a small square copy kept in the app's private storage. It never leaves the phone. */
object ProfilePhoto {
    private const val SIZE = 512

    private fun file(ctx: Context) = File(ctx.filesDir, "profile_photo.jpg")

    /** Copies the chosen picture into the app (cropped to a square, scaled down, rotated upright). */
    suspend fun save(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext false
            val rotation = cr.openInputStream(uri)?.use { exifRotation(it) } ?: 0

            val side = minOf(raw.width, raw.height)
            val scale = if (side > SIZE) SIZE.toFloat() / side else 1f
            val m = Matrix().apply {
                postRotate(rotation.toFloat())
                postScale(scale, scale)
            }
            val out = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side, m, true)
            file(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** A picture's upright, down-sampled pixels, for the cropper to show. Null if it can't be read. */
    suspend fun loadUpright(ctx: Context, uri: Uri, maxSide: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@withContext null
            val rotation = cr.openInputStream(uri)?.use { exifRotation(it) } ?: 0
            if (rotation == 0) raw
            else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves the square region chosen in the cropper. [cx], [cy] and [side] are in pixels of [src].
     * Written to a temporary file first, so a failure never damages the existing picture.
     */
    suspend fun saveCropped(ctx: Context, src: Bitmap, cx: Float, cy: Float, side: Float): Boolean = withContext(Dispatchers.IO) {
        try {
            val sd = side.coerceIn(1f, minOf(src.width, src.height).toFloat())
            val left = (cx - sd / 2).coerceIn(0f, src.width - sd).toInt()
            val top = (cy - sd / 2).coerceIn(0f, src.height - sd).toInt()
            val px = sd.toInt().coerceAtLeast(1).coerceAtMost(minOf(src.width - left, src.height - top))
            val cropped = Bitmap.createBitmap(src, left, top, px, px)
            val out = if (px > SIZE) Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true) else cropped
            val tmp = File(ctx.filesDir, "profile_photo.tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val dest = file(ctx)
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun load(ctx: Context): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(ctx)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    fun remove(ctx: Context) { file(ctx).delete() }

    private fun exifRotation(input: InputStream): Int = try {
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}
