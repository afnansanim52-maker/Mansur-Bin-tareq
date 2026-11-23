package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object BananaImageStorageHelper {

    /**
     * Safely decodes a Photo Picker [Uri] into a memory-bounded [Bitmap] (max 1536px on longest edge)
     * without requiring any storage permissions.
     */
    suspend fun decodePickedImage(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = 1536
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return@withContext null

        var inSampleSize = 1
        while ((rawWidth / inSampleSize) > maxDimensionPx * 2 || (rawHeight / inSampleSize) > maxDimensionPx * 2) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return@withContext null

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxDimensionPx) {
            decoded
        } else {
            val scale = maxDimensionPx.toFloat() / longest.toFloat()
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
    }

    /**
     * Saves a generated [Bitmap] to Pictures/VitRemote using MediaStore (zero runtime permissions on Android 10+).
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val filename = "VitRemote_Banana_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/VitRemote"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create MediaStore entry")

            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("Failed to encode PNG bitmap")
                }
            } ?: error("Unable to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }
    }

    /**
     * Writes [bitmap] to app cache and opens Android's share sheet via FileProvider.
     */
    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        chooserTitle: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cacheDir = File(context.cacheDir, "banana_ai").apply { mkdirs() }
            val imageFile = File(cacheDir, "banana_share_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(chooser)
            }
        }
    }
}
