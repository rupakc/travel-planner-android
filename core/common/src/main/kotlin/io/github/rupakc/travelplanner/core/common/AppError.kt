package io.github.rupakc.travelplanner.core.common

/**
 * Every failure the UI can show. [message] is user-facing copy: the backend's
 * `detail` string when it sent one (web parity: shown as-is), else null and the
 * UI uses its own string resource for the case.
 */
sealed interface AppError {
    val message: String?

    /** No connection, DNS failure, timeout, connection reset. */
    data class Network(override val message: String? = null) : AppError

    /** 401: the session was cleared; the app routes to Login. */
    data class Unauthorized(override val message: String? = null) : AppError

    /** 403 (e.g. admin-only). */
    data class Forbidden(override val message: String? = null) : AppError

    /** 404 with the backend's detail ("Plan not found"). */
    data class NotFound(override val message: String? = null, val code: String? = null) : AppError

    /** 429. [retryAfterSeconds] from the `Retry-After` header when present. */
    data class RateLimited(override val message: String?, val retryAfterSeconds: Long?) : AppError

    /**
     * 400/409/413/422 and other 4xx with a readable detail.
     * [code] is set for structured details (`{code,message}` or top-level `code`, e.g.
     * `no_live_price`, `city_not_found`); [fieldErrors] for FastAPI validation lists;
     * [extras] keeps any other top-level JSON keys (raw JSON text values) for callers that need them.
     */
    data class Client(
        val status: Int,
        override val message: String?,
        val code: String? = null,
        val fieldErrors: List<FieldError> = emptyList(),
        val extras: Map<String, String> = emptyMap(),
    ) : AppError

    /** 5xx (502/503/504) — the backend detail when present ("Discovery timed out — please try again"); [code] for `{code,message}` details. */
    data class Server(val status: Int, override val message: String?, val code: String? = null) : AppError

    /** No backend URL is configured (prod flavour without PROD_BASE_URL and no Settings → Server value). */
    data class NotConfigured(override val message: String? = null) : AppError

    /** The response could not be decoded. */
    data class Decoding(override val message: String? = null) : AppError

    /** A stream ended before its terminal event. */
    data class Interrupted(override val message: String? = null) : AppError

    data class Unknown(override val message: String? = null) : AppError

    data class FieldError(val location: List<String>, val message: String, val type: String?)
}
