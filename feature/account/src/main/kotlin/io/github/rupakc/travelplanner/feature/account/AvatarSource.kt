package io.github.rupakc.travelplanner.feature.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Decodes the picked image once at reduced size and re-encodes it as jpeg under the upload limit; null when unreadable. */
internal object AvatarSource {
    fun compress(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= DECODE_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return try {
            AvatarEncoder.encode { edge, quality ->
                val scale = edge.toFloat() / max(bitmap.width, bitmap.height)
                val scaled =
                    if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * scale).toInt().coerceAtLeast(1),
                            (bitmap.height * scale).toInt().coerceAtLeast(1),
                            true,
                        )
                    } else {
                        bitmap
                    }
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (scaled !== bitmap) scaled.recycle()
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private const val DECODE_EDGE = 1024
}
