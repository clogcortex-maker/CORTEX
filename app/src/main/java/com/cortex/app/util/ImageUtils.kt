package com.cortex.app.util

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
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    fun saveBitmapPng(bitmap: Bitmap, dir: File, name: String): File {
        dir.mkdirs()
        val f = File(dir, name)
        FileOutputStream(f).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return f
    }

    fun decodeScaled(path: String, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        var w = opts.outWidth
        var h = opts.outHeight
        while (w / 2 >= maxDim || h / 2 >= maxDim) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun decodeScaledTo512(path: String): Bitmap? {
        val src = decodeScaled(path, 1024) ?: return null
        return if (src.width == 512 && src.height == 512) src
        else Bitmap.createScaledBitmap(src, 512, 512, true)
    }

    fun saveToGallery(context: Context, file: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CORTEX")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: return false
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CORTEX")
                dir.mkdirs()
                val dest = File(dir, file.name)
                file.copyTo(dest, true)
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(dest))
                context.sendBroadcast(intent)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun shareImage(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share image"))
    }
}
