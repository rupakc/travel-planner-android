package io.github.rupakc.travelplanner.feature.search.importlink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.domain.search.ImageProblem
import io.github.rupakc.travelplanner.core.domain.search.ImportLimits
import io.github.rupakc.travelplanner.core.domain.search.checkImageFile
import io.github.rupakc.travelplanner.core.domain.search.fitWithin
import io.github.rupakc.travelplanner.core.model.importlink.ImportImage
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

/** A screenshot ready to upload, with a small thumbnail for the picker row. */
@Immutable
data class PreparedShot(val id: Long, val name: String, val image: ImportImage, val thumbnail: ImageBitmap?)

/** Why a picked screenshot was not added (web panel messages). */
sealed interface ShotProblem {
    val name: String

    data class Invalid(override val name: String, val problem: ImageProblem) : ShotProblem

    /** Still over 5 MB after shrinking. */
    data class StillTooBig(override val name: String) : ShotProblem

    /** Could not be decoded. */
    data class Unreadable(override val name: String) : ShotProblem
}

/** Reads a picked image off the main thread: checks it, downscales to ≤ 1568 px and re-encodes (web `prepareScreenshot`). */
fun interface ScreenshotReader {
    suspend fun read(uri: Uri, id: Long): Result<PreparedShot>
}

/** Thrown inside [ScreenshotReader] results to carry a [ShotProblem]. */
class ShotProblemException(val problem: ShotProblem) : Exception(problem.name)

/**
 * [ScreenshotReader] over the ContentResolver: WebP (lossy, API 30+) or JPEG at quality 85 with the
 * long edge ≤ [ImportLimits.MAX_IMAGE_EDGE]; the base64 must stay ≤ 5 MB (the server's limit).
 */
internal class ContentResolverScreenshotReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dispatchers: AppDispatchers,
    ) : ScreenshotReader {
        override suspend fun read(uri: Uri, id: Long): Result<PreparedShot> =
            withContext(dispatchers.io) {
                try {
                    Result.success(prepare(uri, id))
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Result.failure(e)
                } catch (e: OutOfMemoryError) {
                    Result.failure(e)
                }
            }

        private fun prepare(uri: Uri, id: Long): PreparedShot {
            val (name, size) = describe(uri)
            checkImageFile(context.contentResolver.getType(uri), name, size, beforeShrink = true)?.let { problem(ShotProblem.Invalid(name, it)) }
            val scaled = decodeScaled(uri) ?: problem(ShotProblem.Unreadable(name))
            val (format, mediaType) =
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R
                ) {
                    Bitmap.CompressFormat.WEBP_LOSSY to "image/webp"
                } else {
                    Bitmap.CompressFormat.JPEG to "image/jpeg"
                }
            var thumb: Bitmap? = null
            try {
                val bytes = ByteArrayOutputStream().use { out -> scaled.compress(format, QUALITY, out).let { out.toByteArray() } }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                if (base64.length > ImportLimits.MAX_UPLOAD_BASE64) problem(ShotProblem.StillTooBig(name))
                val (tw, th) = fitWithin(scaled.width, scaled.height, THUMBNAIL_EDGE)
                thumb = scaled.scale(tw, th)
                return PreparedShot(id, name, ImportImage(mediaType, base64), thumb.asImageBitmap())
            } finally {
                // The full-size bitmap is only needed to encode and thumbnail: free it now (four big photos add up).
                // createScaledBitmap returns the source itself when the size is unchanged: keep that one.
                if (thumb !== scaled) scaled.recycle()
            }
        }

        private fun problem(problem: ShotProblem): Nothing = throw ShotProblemException(problem)

        /** Display name and size (bytes, -1 when unknown) from the provider. */
        private fun describe(uri: Uri): Pair<String, Long> {
            var name = uri.lastPathSegment ?: DEFAULT_NAME
            var size = -1L
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            return name to size
        }

        /** Decodes with a power-of-two sample size, then scales so the long edge is ≤ 1568 px; null when undecodable. */
        private fun decodeScaled(uri: Uri): Bitmap? {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val (width, height) = fitWithin(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, width) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            if (decoded.width == width && decoded.height == height) return decoded
            return decoded.scale(width, height).also { decoded.recycle() }
        }

        /** Largest power of two that still decodes at least [target] px wide. */
        private fun sampleSize(source: Int, target: Int): Int {
            var sample = 1
            while (source / (sample * 2) >= target) sample *= 2
            return sample
        }

        private companion object {
            const val QUALITY = 85
            const val THUMBNAIL_EDGE = 192
            const val DEFAULT_NAME = "Screenshot"
        }
    }
