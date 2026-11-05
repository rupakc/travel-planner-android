package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.PreviewDestination
import io.github.rupakc.travelplanner.core.model.importlink.PreviewPlace
import io.github.rupakc.travelplanner.core.model.search.ImportSource
import io.github.rupakc.travelplanner.core.model.search.MustIncludePlace
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Limits of "Plan from a link" (web utils/linkImport.js + backend `ImportRequest`). */
object ImportLimits {
    /** Places sent as `must_include` (backend max 25). */
    const val MAX_MUST_INCLUDE = 25

    /** Screenshots per extract call. */
    const val MAX_IMAGES = 4

    /** Upload limit per screenshot, applied to the base64 text as on the server. */
    const val MAX_UPLOAD_BASE64 = 5 * 1024 * 1024

    /** Files this big are refused before decoding. */
    const val MAX_SOURCE_IMAGE_BYTES = 40 * 1024 * 1024

    /** Long edge after downscaling (Claude scales larger images down anyway). */
    const val MAX_IMAGE_EDGE = 1568

    /** Pasted text limit (backend `text` ≤ 20000). */
    const val MAX_TEXT = 20_000

    /** Nights per stop in the review (web stepper 1..21). */
    const val MAX_STOP_NIGHTS = 21

    /** Image types the backend reads (HEIC is refused). */
    val IMAGE_TYPES: Set<String> = setOf("image/png", "image/jpeg", "image/webp", "image/gif")

    /** Default departure for an import: a month out (web `defaultImportDates`). */
    const val DEFAULT_DEPARTURE_DAYS = 30L
}

/** What the traveller typed (web `detectImportInput`). */
sealed interface ImportInput {
    data object Empty : ImportInput

    data object Text : ImportInput

    data class Url(val url: String) : ImportInput
}

private val URL_LIKE = Regex("""^(https?://)?([a-z0-9-]+\.)+[a-z]{2,}(:\d+)?([/?#]\S*)?$""", RegexOption.IGNORE_CASE)
private val HAS_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)
private val WHITESPACE = Regex("\\s")

/** Port of web `detectImportInput`: a single token that looks like a web address is a link (https added when missing). */
fun detectImportInput(raw: String?): ImportInput {
    val text = raw.orEmpty().trim()
    if (text.isEmpty()) return ImportInput.Empty
    if (!WHITESPACE.containsMatchIn(text) && URL_LIKE.matches(text)) {
        return ImportInput.Url(if (HAS_SCHEME.containsMatchIn(text)) text else "https://$text")
    }
    return ImportInput.Text
}

/** What the import error card offers next (web `ACTIONS`). */
enum class ImportAction { TEXT, IMAGES, RETRY }

/**
 * A failed extract call (web `importErrorView`). [message] is the backend's text, shown as-is;
 * null means "use the app's copy for [code]" (`network`, `unknown`, `too_large`).
 */
data class ImportErrorView(val code: String, val message: String?, val actions: List<ImportAction>)

private val IMPORT_ACTIONS: Map<String, List<ImportAction>> =
    mapOf(
        "blocked_source" to listOf(ImportAction.TEXT, ImportAction.IMAGES),
        "paywall" to listOf(ImportAction.TEXT, ImportAction.IMAGES),
        "blocked_by_site" to listOf(ImportAction.TEXT, ImportAction.IMAGES),
        "empty_page" to listOf(ImportAction.TEXT, ImportAction.IMAGES),
        "unsupported_content" to listOf(ImportAction.IMAGES),
        "unsupported_encoding" to listOf(ImportAction.TEXT, ImportAction.IMAGES),
        "not_found" to listOf(ImportAction.TEXT),
        "no_places" to listOf(ImportAction.TEXT),
        "timeout" to listOf(ImportAction.RETRY, ImportAction.TEXT),
        "unreachable" to listOf(ImportAction.RETRY, ImportAction.TEXT),
        "http_error" to listOf(ImportAction.RETRY, ImportAction.TEXT),
        "extract_failed" to listOf(ImportAction.RETRY),
        "network" to listOf(ImportAction.RETRY),
        "unknown" to listOf(ImportAction.RETRY),
    )

private const val HTTP_PAYLOAD_TOO_LARGE = 413

/** Port of web `importErrorView` over [AppError] (cancellation never reaches here). */
fun importErrorView(error: AppError): ImportErrorView {
    val code =
        when (error) {
            is AppError.Client -> error.code
            is AppError.Server -> error.code
            is AppError.NotFound -> error.code
            else -> null
        }
    return when {
        error is AppError.Network -> {
            ImportErrorView("network", null, IMPORT_ACTIONS.getValue("network"))
        }

        code != null -> {
            ImportErrorView(code, error.message, IMPORT_ACTIONS[code].orEmpty())
        }

        error is AppError.RateLimited && !error.message.isNullOrBlank() -> {
            ImportErrorView("rate_limited", error.message, emptyList())
        }

        error is AppError.Client && error.status == HTTP_PAYLOAD_TOO_LARGE -> {
            ImportErrorView("too_large", error.message?.takeIf(String::isNotBlank), listOf(ImportAction.IMAGES))
        }

        else -> {
            ImportErrorView("unknown", null, IMPORT_ACTIONS.getValue("unknown"))
        }
    }
}

/** Why a picked file can't be used (web `checkImageFile`). */
enum class ImageProblem { HEIC, UNSUPPORTED_TYPE, TOO_BIG_TO_READ, TOO_BIG }

private val HEIC_NAME = Regex("""\.hei[cf]$""", RegexOption.IGNORE_CASE)

/** Port of web `checkImageFile`; with [beforeShrink] only the 40 MB pre-decode limit applies. */
fun checkImageFile(mimeType: String?, name: String?, sizeBytes: Long, beforeShrink: Boolean = false): ImageProblem? {
    val type = mimeType.orEmpty().lowercase()
    return when {
        type == "image/heic" || type == "image/heif" || HEIC_NAME.containsMatchIn(name.orEmpty()) -> ImageProblem.HEIC
        type !in ImportLimits.IMAGE_TYPES -> ImageProblem.UNSUPPORTED_TYPE
        beforeShrink && sizeBytes > ImportLimits.MAX_SOURCE_IMAGE_BYTES -> ImageProblem.TOO_BIG_TO_READ
        !beforeShrink && sizeBytes > ImportLimits.MAX_UPLOAD_BASE64 -> ImageProblem.TOO_BIG
        else -> null
    }
}

/** Web `fitWithin`: size scaled so the long edge is at most [max] (never upscaled). */
fun fitWithin(width: Int, height: Int, max: Int = ImportLimits.MAX_IMAGE_EDGE): Pair<Int, Int> {
    val scale = min(1.0, max.toDouble() / max(width, height))
    return (width * scale).roundToInt() to (height * scale).roundToInt()
}

/** Web `defaultImportDates`: departure 30 days out, return after `max(1, nights)` nights. */
fun defaultImportDates(today: LocalDate, nights: Int): Pair<String, String> {
    val departure = today.plusDays(ImportLimits.DEFAULT_DEPARTURE_DAYS)
    return departure.toString() to departure.plusDays(max(1, nights).toLong()).toString()
}

/** A place in the review list; [place.destination] is the stop it belongs to (editable). */
data class DraftPlace(val place: PreviewPlace, val selected: Boolean) {
    val id: String get() = place.id
}

/** A stop of the imported route; [nights] is editable (1..21), [included] can be toggled. */
data class DraftStop(val destination: PreviewDestination, val included: Boolean, val nights: Int = destination.nights) {
    val name: String get() = destination.name
}

/** Editable review state of an [ImportPreview] (web `previewToDraft`). */
data class ImportDraft(val places: List<DraftPlace>, val unresolved: List<DraftPlace>, val stops: List<DraftStop>) {
    val includedStops: List<DraftStop> get() = stops.filter { it.included }
}

/** Port of web `previewToDraft`: places start ticked except stays and anything past the 25th; unresolved ones have no stop. */
fun previewToDraft(preview: ImportPreview): ImportDraft {
    var ticked = 0
    val places =
        preview.places.map { p ->
            val selected = p.kind != "stay" && ticked < ImportLimits.MAX_MUST_INCLUDE
            if (selected) ticked++
            DraftPlace(p, selected)
        }
    return ImportDraft(
        places = places,
        unresolved = preview.unresolved.map { DraftPlace(it.copy(destination = null), selected = true) },
        stops = preview.destinations.map { DraftStop(it, included = true) },
    )
}

/** Total nights of the included stops (each at least 1). */
fun importNights(draft: ImportDraft): Int = draft.includedStops.sumOf { max(1, it.nights) }

/** [buildImportSearch] result: the Search form and on-the-way stops for the normal search. */
data class ImportSearch(val form: SearchForm, val stops: List<ExtraStop>)

/**
 * Port of web `buildImportSearch`: places of excluded stops are dropped; at most 25 go in,
 * in order; approximate pins never send coordinates; the last included stop is the destination;
 * the return date follows the nights; interests merge the form's with the preview's.
 */
fun buildImportSearch(form: SearchForm, preview: ImportPreview, draft: ImportDraft): ImportSearch {
    val stops = draft.includedStops
    val byName = stops.associateBy { it.name }
    val chosen =
        (draft.places + draft.unresolved)
            .filter { it.selected && it.place.destination != null && it.place.destination in byName }
            .take(ImportLimits.MAX_MUST_INCLUDE)
    val mustInclude =
        chosen.map { d ->
            val p = d.place
            val stop = byName.getValue(p.destination.orEmpty())
            val exact = !p.approximate && p.lat != null && p.lng != null
            MustIncludePlace(
                name = p.name.trim(),
                kind = p.kind.ifBlank { "sight" },
                city = stop.destination.city,
                town = p.city?.takeIf { it.isNotEmpty() && it != stop.destination.city },
                lat = if (exact) p.lat else null,
                lng = if (exact) p.lng else null,
                note = p.note?.ifBlank { null },
                quote = p.quote?.ifBlank { null },
                verified = p.verified,
            )
        }
    val nights = importNights(draft)
    val returnDate = parseIsoDate(form.departureDate)?.plusDays(max(1, nights).toLong())?.toString().orEmpty()
    val source = preview.source
    return ImportSearch(
        form =
            form.copy(
                destination = stops.lastOrNull()?.name.orEmpty(),
                returnDate = returnDate,
                interests = (form.interests + preview.interests).distinct(),
                mustInclude = mustInclude,
                importSource =
                    ImportSource(
                        url = if (source.kind == "url") source.url?.ifBlank { null } else null,
                        title = preview.title.ifBlank { null } ?: source.title?.ifBlank { null },
                    ),
            ),
        stops = stops.dropLast(1).map { ExtraStop(it.name, it.nights.toString()) },
    )
}

/** Why "Plan this trip" is disabled in the import review (web `missing` / `canPlan`). */
enum class ImportPlanIssue { ORIGIN_REQUIRED, NATIONALITY_REQUIRED, NO_PLACES, NO_STOPS, DEPARTURE_IN_PAST }

fun importPlanIssue(form: SearchForm, search: ImportSearch, draft: ImportDraft, today: LocalDate): ImportPlanIssue? {
    val departure = parseIsoDate(form.departureDate)
    return when {
        form.origin.isBlank() -> ImportPlanIssue.ORIGIN_REQUIRED
        form.nationality.isBlank() -> ImportPlanIssue.NATIONALITY_REQUIRED
        draft.includedStops.isEmpty() -> ImportPlanIssue.NO_STOPS
        search.form.mustInclude.isNullOrEmpty() -> ImportPlanIssue.NO_PLACES
        departure == null || departure.isBefore(today) -> ImportPlanIssue.DEPARTURE_IN_PAST
        else -> null
    }
}
