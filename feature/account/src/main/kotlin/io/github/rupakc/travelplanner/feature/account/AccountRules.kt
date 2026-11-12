package io.github.rupakc.travelplanner.feature.account

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.auth.DeleteAccountRequest
import java.io.File
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Pure rules of the account area (no Android, no network). */
internal object AccountRules {
    /** The UI languages the backend accepts as `locale`/`language`. */
    val Languages = listOf("en", "es", "de", "fr", "hi", "zh", "bn")
    val Themes = listOf("system", "light", "dark")
    val Units = listOf("metric", "imperial")
    val Visibilities = listOf("public", "followers", "private")
    val Currencies = listOf("USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD", "CHF", "CNY", "MXN", "BRL", "SGD")

    const val MIN_GOAL = 1
    const val MAX_GOAL = 250
    const val MAX_NAME = 50

    /** Only the keys that differ (a JSON null clears `language`, `timezone`, `bucket_list_goal`); empty = nothing to send. */
    fun diff(old: AccountSettings, new: AccountSettings): JsonObject {
        val out = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        if (old.language != new.language) out["language"] = new.language?.let(::JsonPrimitive) ?: JsonNull
        if (old.theme != new.theme) out["theme"] = JsonPrimitive(new.theme)
        if (old.currency != new.currency) out["currency"] = JsonPrimitive(new.currency)
        if (old.units != new.units) out["units"] = JsonPrimitive(new.units)
        if (old.timezone != new.timezone) out["timezone"] = new.timezone?.let(::JsonPrimitive) ?: JsonNull
        if (old.hideGame != new.hideGame) out["hide_game"] = JsonPrimitive(new.hideGame)
        if (old.hideCommunity != new.hideCommunity) out["hide_community"] = JsonPrimitive(new.hideCommunity)
        if (old.profileVisibility != new.profileVisibility) out["profile_visibility"] = JsonPrimitive(new.profileVisibility)
        if (old.telemetryOptOut != new.telemetryOptOut) out["telemetry_opt_out"] = JsonPrimitive(new.telemetryOptOut)
        if (old.bucketListGoal != new.bucketListGoal) out["bucket_list_goal"] = new.bucketListGoal?.let(::JsonPrimitive) ?: JsonNull
        return JsonObject(out)
    }

    /** The confirmation field the account needs: the password when it has one, else its own username. */
    fun deleteRequest(hasPassword: Boolean, input: String, username: String): DeleteAccountRequest? =
        if (hasPassword) {
            input.takeIf { it.isNotEmpty() }?.let { DeleteAccountRequest(password = it) }
        } else {
            input.trim().takeIf { it.equals(username, ignoreCase = true) }?.let { DeleteAccountRequest(confirmUsername = username) }
        }

    /** The export file sits in a folder of the app's private cache, never in external storage. */
    fun exportTarget(cacheDir: File): File = File(File(cacheDir, EXPORT_DIR), EXPORT_NAME)

    const val EXPORT_DIR = "account-exports"
    const val EXPORT_NAME = "voyager-export.json"

    /** Up to two letters for the avatar fallback. */
    fun initials(displayName: String?, username: String): String {
        val words = (displayName?.takeIf { it.isNotBlank() } ?: username).trim().split(Regex("[\\s_]+")).filter { it.isNotEmpty() }
        val letters = if (words.size >= 2) "${words[0].first()}${words[1].first()}" else words.firstOrNull()?.take(2).orEmpty()
        return letters.uppercase()
    }

    /** The structured error code of a backend failure, if any. */
    fun codeOf(error: AppError): String? =
        when (error) {
            is AppError.Client -> error.code
            is AppError.Forbidden -> error.code
            is AppError.NotFound -> error.code
            is AppError.Unauthorized -> error.code
            is AppError.RateLimited -> error.code
            is AppError.Server -> error.code
            else -> null
        }
}

/** Downscales a picked photo until it fits the 2 MB upload limit. [render] returns jpeg bytes for (longest edge, quality) or null. */
internal object AvatarEncoder {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MIME = "image/jpeg"
    private val Edges = listOf(1024, 768, 512, 384, 256)
    private val Qualities = listOf(90, 80, 70, 55)

    fun encode(maxBytes: Int = MAX_BYTES, render: (edge: Int, quality: Int) -> ByteArray?): ByteArray? {
        for (edge in Edges) {
            for (quality in Qualities) {
                val bytes = render(edge, quality) ?: return null
                if (bytes.size <= maxBytes) return bytes
            }
        }
        return null
    }
}
