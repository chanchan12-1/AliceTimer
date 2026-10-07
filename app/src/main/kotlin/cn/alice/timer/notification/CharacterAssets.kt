package cn.alice.timer.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

object CharacterAssets {
    fun load(context: Context, id: String, uri: String?): Bitmap? = runCatching {
        fun open(): InputStream? = if (!uri.isNullOrEmpty()) context.contentResolver.openInputStream(Uri.parse(uri))
        else listOf("png", "webp", "jpg").firstNotNullOfOrNull { extension ->
            runCatching { context.assets.open("characters/$id/avatar.$extension") }.getOrNull()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val firstStream = open() ?: return null
        firstStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()
}
