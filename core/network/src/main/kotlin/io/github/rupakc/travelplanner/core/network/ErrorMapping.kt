package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppException
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

/**
 * Maps backend errors to [AppError] (api-contract §0.3):
 * `detail` string | list (FastAPI 422) | `{code,message}` object, top-level
 * `code`/`message` (price-watch 422s), and `429` + `Retry-After`.
 */
object HttpErrorMapper {
    private const val UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
    private const val NOT_FOUND = 404
    private const val TOO_MANY = 429
    private const val SERVER_MIN = 500

    fun map(status: Int, retryAfterHeader: String?, body: String?): AppError {
        val parsed = parseBody(body)
        return when {
            status == UNAUTHORIZED -> AppError.Unauthorized(parsed.message)
            status == FORBIDDEN -> AppError.Forbidden(parsed.message)
            status == NOT_FOUND -> AppError.NotFound(parsed.message, parsed.code)
            status == TOO_MANY -> AppError.RateLimited(parsed.message, parseRetryAfter(retryAfterHeader))
            status >= SERVER_MIN -> AppError.Server(status, parsed.message, parsed.code)
            else -> AppError.Client(status, parsed.message, parsed.code, parsed.fieldErrors, parsed.extras)
        }
    }

    /** `Retry-After` in seconds (the backend always sends delta-seconds). */
    fun parseRetryAfter(value: String?): Long? = value?.trim()?.toLongOrNull()?.coerceAtLeast(0)

    internal data class ParsedBody(
        val message: String?,
        val code: String?,
        val fieldErrors: List<AppError.FieldError>,
        val extras: Map<String, String>,
    )

    internal fun parseBody(body: String?): ParsedBody {
        val obj =
            body?.takeIf(String::isNotBlank)?.let { runCatching { TravelJson.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                ?: return ParsedBody(null, null, emptyList(), emptyMap())
        var message: String? = null
        var code: String? = (obj["code"] as? JsonPrimitive)?.contentOrNull
        var fieldErrors = emptyList<AppError.FieldError>()
        when (val detail = obj["detail"]) {
            is JsonPrimitive -> {
                message = detail.takeIf { it !is JsonNull }?.contentOrNull?.takeIf(String::isNotBlank)
            }

            is JsonArray -> {
                fieldErrors = detail.mapNotNull { (it as? JsonObject)?.toFieldError() }
                message = fieldErrors.firstOrNull()?.message
            }

            is JsonObject -> {
                code = (detail["code"] as? JsonPrimitive)?.contentOrNull ?: code
                message = (detail["message"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            }

            else -> {
                Unit
            }
        }
        if (message == null) message = (obj["message"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        val extras = obj.filterKeys { it != "detail" && it != "code" }.mapValues { (_, v) -> v.asExtra() }
        return ParsedBody(message, code, fieldErrors, extras)
    }

    private fun JsonObject.toFieldError(): AppError.FieldError? {
        val msg = (this["msg"] as? JsonPrimitive)?.contentOrNull ?: return null
        val loc = (this["loc"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return AppError.FieldError(loc, msg, (this["type"] as? JsonPrimitive)?.contentOrNull)
    }

    private fun JsonElement.asExtra(): String = if (this is JsonPrimitive && isString) content else toString()
}

private const val TAG = "ErrorMapping"

/**
 * Any throwable from a call → [AppError]. Client-side failures (decoding, unknown) never carry
 * the exception text, which is English-only and leaks internals (an HTML 200 from a captive portal
 * would show "Unexpected JSON token…"); the throwable is logged instead and screens show their own
 * localized copy.
 */
fun Throwable.toAppError(): AppError =
    when (this) {
        is AppException -> error
        is HttpException -> HttpErrorMapper.map(code(), response()?.headers()?.get("Retry-After"), response()?.errorBody()?.string())
        is NoServerConfiguredException -> AppError.NotConfigured()
        is IOException -> AppError.Network(null)
        is SerializationException, is IllegalArgumentException -> {
            AppLogger.w(TAG, "Response could not be decoded", this)
            AppError.Decoding(null)
        }
        else -> {
            AppLogger.w(TAG, "Unexpected failure", this)
            AppError.Unknown(null)
        }
    }

/** Runs a Retrofit call and wraps the outcome. Cancellation is rethrown. */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

/** Thrown by the base-URL interceptor when no server is configured. */
class NoServerConfiguredException : IOException("No server URL is configured")
