package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.ShareCardStore
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [ShareCardStore] over the app's OkHttp client without its interceptors (no bearer token is ever sent: the card is a public
 * route, and its host comes from the server). Only http(s), only `image/png`, at most [MAX_BYTES]. [dir] is a cache folder
 * that holds nothing else.
 */
class ShareCardStoreImpl(
    private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
    private val dir: File,
) : ShareCardStore,
    UserStateCleaner {
    private val bare: OkHttpClient by lazy {
        client.newBuilder().apply {
            interceptors().clear()
            networkInterceptors().clear()
        }.build()
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun download(url: String): AppResult<File> =
        withContext(dispatchers.io) {
            val target = url.toHttpUrlOrNull()
            if (target == null || target.scheme !in SCHEMES) return@withContext AppResult.Failure(AppError.Unknown(null))
            try {
                bare.newCall(Request.Builder().url(target).get().build()).execute().use { response ->
                    val body = response.body
                    val type = body.contentType()
                    when {
                        !response.isSuccessful -> AppResult.Failure(AppError.Unknown(null))
                        type == null || type.type != "image" || type.subtype != "png" -> AppResult.Failure(AppError.Unknown(null))
                        else -> write(body.byteStream())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppResult.Failure(AppError.Network(null))
            } catch (e: Exception) {
                AppLogger.w(TAG, "Card download failed", e)
                AppResult.Failure(AppError.Unknown(null))
            }
        }

    private fun write(input: java.io.InputStream): AppResult<File> {
        clearFiles()
        dir.mkdirs()
        val out = File(dir, FILE_NAME)
        var total = 0L
        out.outputStream().use { sink ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) {
                    sink.close()
                    out.delete()
                    return AppResult.Failure(AppError.Unknown(null))
                }
                sink.write(buffer, 0, n)
            }
        }
        return if (total == 0L) {
            out.delete()
            AppResult.Failure(AppError.Unknown(null))
        } else {
            AppResult.Success(out)
        }
    }

    override suspend fun clear() = withContext(dispatchers.io) { clearFiles() }

    private fun clearFiles() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /** Name of the one cached card; the FileProvider path of `feature:share` exposes this folder only. */
        const val FILE_NAME = "share-card.png"
        const val DIR_NAME = "share-cards"
        const val MAX_BYTES = 8L * 1024 * 1024
        private const val BUFFER = 8 * 1024
        private const val TAG = "ShareCardStore"
        private val SCHEMES = setOf("http", "https")
    }
}
