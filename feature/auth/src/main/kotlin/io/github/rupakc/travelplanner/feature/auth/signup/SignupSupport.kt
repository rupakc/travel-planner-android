package io.github.rupakc.travelplanner.feature.auth.signup

import androidx.annotation.StringRes
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.serverProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Seconds before "Resend email" can be tapped again. */
internal const val RESEND_COOLDOWN_SECONDS = 30

/** The backend's machine code (`error_code`) of a failure, whatever the HTTP status. */
internal fun AppError.errorCode(): String? =
    when (this) {
        is AppError.Client -> code
        is AppError.Server -> code
        is AppError.Forbidden -> code
        is AppError.RateLimited -> code
        is AppError.Unauthorized -> code
        is AppError.NotFound -> code
        else -> null
    }

/** Local copy of every account error code of the sign-up, verify and reset flows (web `errors` namespace). */
@StringRes
internal fun accountErrorCopy(code: String?): Int? =
    when (code) {
        "username_taken" -> R.string.auth_err_username_taken
        "username_invalid" -> R.string.auth_err_username_invalid
        "username_reserved" -> R.string.auth_err_username_reserved
        "email_invalid" -> R.string.auth_err_email_invalid
        "password_too_short" -> R.string.auth_err_password_too_short
        "password_too_long" -> R.string.auth_err_password_too_long
        "password_common" -> R.string.auth_err_password_common
        "password_matches_identity" -> R.string.auth_err_password_matches_identity
        "display_name_invalid" -> R.string.auth_err_display_name_invalid
        "invite_required" -> R.string.auth_err_invite_required
        "invite_invalid" -> R.string.auth_err_invite_invalid
        "email_unavailable" -> R.string.auth_err_email_unavailable
        "token_invalid" -> R.string.auth_err_token_invalid
        "token_expired" -> R.string.auth_err_token_expired
        "token_used" -> R.string.auth_err_token_used
        "email_changed" -> R.string.auth_err_email_changed
        "rate_limited" -> R.string.auth_err_rate_limited
        "email_unverified" -> R.string.auth_err_email_unverified
        "account_pending" -> R.string.auth_err_account_pending
        else -> null
    }

/**
 * Failure of an account call: our localized copy for a known `error_code`, else a no-server message, else the
 * backend's `detail` as-is (a 429 without a code keeps the server's own text), else [fallback].
 */
internal fun AppError.toAccountMessage(
    @StringRes fallback: Int = R.string.auth_error_generic,
): AuthMessage =
    accountErrorCopy(errorCode())?.let { AuthMessage.Local(it) }
        ?: serverProblem()
        ?: userFacingDetail()?.let(AuthMessage::Server)
        ?: AuthMessage.Local(fallback)

/** `email_verify.result` of a failed verification. */
internal fun AppError.verifyResult(): String =
    when (errorCode()) {
        "token_expired" -> "expired"
        "token_used" -> "used"
        "email_changed" -> "email_changed"
        else -> "invalid"
    }

/** `password_reset.result` of a failed confirmation. */
internal fun AppError.resetResult(): String =
    when (errorCode()) {
        "token_expired" -> "expired"
        "token_used" -> "used"
        "token_invalid" -> "invalid"
        else -> "error"
    }

/** The token of a pasted verify/reset link (`https://h/verify?token=abc&x=1`) or a bare code; null when neither. */
internal object LinkTokens {
    private val bare = Regex("^[A-Za-z0-9_\\-.~]{1,512}$")

    fun extract(input: String?): String? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null
        val query = text.substringAfter('?', "").substringBefore('#')
        if (text.contains('?') || text.contains("://")) {
            val raw =
                query.split('&').firstNotNullOfOrNull { pair ->
                    pair.substringBefore('=').takeIf { it == "token" }?.let { pair.substringAfter('=', "") }
                } ?: return null
            return raw.takeIf { it.isNotEmpty() && bare.matches(it) }
        }
        return text.takeIf { bare.matches(it) }
    }
}

/** Live password hints; the server stays the judge (`password_common` and the like arrive as error codes). */
data class PasswordCheck(val longEnough: Boolean, val distinctFromIdentity: Boolean) {
    val ok: Boolean get() = longEnough && distinctFromIdentity
}

object PasswordRules {
    private const val MIN_IDENTITY_PART = 3

    fun check(password: String, minLength: Int, username: String = "", email: String = ""): PasswordCheck {
        val lower = password.lowercase()
        val parts = listOf(username.trim(), email.trim().substringBefore('@')).map { it.lowercase() }.filter { it.length >= MIN_IDENTITY_PART }
        val identity = password.isNotEmpty() && (parts.any { lower.contains(it) } || lower == email.trim().lowercase())
        return PasswordCheck(longEnough = password.length >= minLength, distinctFromIdentity = !identity)
    }
}

/** A seconds countdown for "Resend": [start] begins [RESEND_COOLDOWN_SECONDS], [onTick] gets the remaining seconds down to 0. */
internal class Cooldown(private val scope: CoroutineScope, private val onTick: (Int) -> Unit) {
    private var job: Job? = null

    fun start(seconds: Int = RESEND_COOLDOWN_SECONDS) {
        job?.cancel()
        onTick(seconds)
        job =
            scope.launch {
                var left = seconds
                while (left > 0) {
                    delay(MILLIS)
                    left--
                    onTick(left)
                }
            }
    }

    private companion object {
        const val MILLIS = 1000L
    }
}

/** One improvement hint of the strength meter (web `signup:strength.hint.*`). */
enum class StrengthHint { TooShort, Longer, Variety, Repeats, Sequence, Identity }

/** [score] 0..4 (web `strength.level.<score>`); [hints] in the web's order. */
data class PasswordStrengthResult(val score: Int, val hints: List<StrengthHint>)

/**
 * Port of the web's `utils/passwordStrength.js`: a small dependency-free heuristic. Advice only; the server's
 * rules (length, common list, identity match) stay the authority.
 */
object PasswordStrength {
    /** The server's minimum for sign-up, reset and change-password (web `MIN_PASSWORD_LENGTH`). */
    const val MIN_LENGTH = 10

    private const val MIN_IDENTITY_PART = 3
    private const val LEN_FAIR = 12
    private const val LEN_GOOD = 16
    private const val LEN_LONG = 20
    private const val CLASSES_OK = 3
    private const val MAX_SCORE = 4
    private const val SCORE_LONG = 3
    private const val SEQUENCE = 4
    private const val IDENTITY_PENALTY = 2
    private const val DISTINCT_RATIO = 3
    private val repeatedRun = Regex("(.)\\1{2,}")

    private fun hasSequence(password: String): Boolean {
        val lower = password.lowercase()
        for (i in 0..lower.length - SEQUENCE) {
            val steps = (1 until SEQUENCE).map { lower[i + it] - lower[i + it - 1] }
            if (steps.all { it == 1 } || steps.all { it == -1 }) return true
        }
        return false
    }

    private fun identityParts(username: String, email: String): List<String> =
        listOf(username, email.substringBefore('@', ""))
            .map { it.lowercase() }
            .filter { it.length >= MIN_IDENTITY_PART }

    fun of(password: String, username: String = "", email: String = ""): PasswordStrengthResult {
        if (password.isEmpty()) return PasswordStrengthResult(0, emptyList())
        val hints = mutableListOf<StrengthHint>()
        val len = password.codePointCount(0, password.length)
        var score =
            when {
                len < MIN_LENGTH -> 0
                len < LEN_FAIR -> 1
                len < LEN_GOOD -> 2
                else -> SCORE_LONG
            }
        if (len < MIN_LENGTH) {
            hints += StrengthHint.TooShort
        } else if (len < LEN_GOOD) {
            hints += StrengthHint.Longer
        }

        val classes =
            listOf(Regex("[a-z]"), Regex("[A-Z]"), Regex("\\d"), Regex("[^a-zA-Z0-9]")).count { it.containsMatchIn(password) }
        if (len >= MIN_LENGTH && (classes >= CLASSES_OK || len >= LEN_LONG)) score += 1
        if (classes < CLASSES_OK && len < LEN_LONG) hints += StrengthHint.Variety

        val distinct = password.codePoints().distinct().count().toInt()
        val lowVariety = distinct * DISTINCT_RATIO < len
        if (repeatedRun.containsMatchIn(password) || lowVariety) {
            score -= 1
            hints += StrengthHint.Repeats
        }
        if (lowVariety) score = minOf(score, 0)
        if (hasSequence(password)) {
            score -= 1
            hints += StrengthHint.Sequence
        }
        val lower = password.lowercase()
        if (identityParts(username, email).any { lower.contains(it) }) {
            score -= IDENTITY_PENALTY
            hints += StrengthHint.Identity
        }
        return PasswordStrengthResult(score.coerceIn(0, MAX_SCORE), hints)
    }
}
