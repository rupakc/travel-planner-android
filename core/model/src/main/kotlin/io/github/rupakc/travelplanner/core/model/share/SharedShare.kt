package io.github.rupakc.travelplanner.core.model.share

import kotlinx.serialization.Serializable

/** One count chip of a share card (`stats` of `GET /share/{token}`): a [key] (`activities`, `drops_caught`, ...) and its [count]. */
@Serializable
data class ShareCardStat(val key: String = "", val count: Int = 0)

/**
 * The `card` of a recap or savings share (`GET /share/{token}`): a [headline], at most three count [stats] and, only when the
 * sharer allowed it, [destination] and [period] (`YYYY-MM`). Never holds exact dates.
 */
@Serializable
data class ShareCardSnapshot(
    val headline: String = "",
    val stats: List<ShareCardStat> = emptyList(),
    val destination: String? = null,
    val period: String? = null,
)
