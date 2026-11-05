package io.github.rupakc.travelplanner.core.common

/** Carries an [AppError] through a `Flow` (streams) where a result type cannot be returned. */
class AppException(val error: AppError, cause: Throwable? = null) : RuntimeException(error.message ?: error::class.simpleName, cause)
