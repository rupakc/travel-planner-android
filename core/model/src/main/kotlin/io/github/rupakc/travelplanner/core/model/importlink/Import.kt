package io.github.rupakc.travelplanner.core.model.importlink

import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /api/import/extract`: exactly one of [url], [text], [images] (1..4, ≤ 5 MB each, no HEIC). */
@Serializable
data class ImportRequest(
    val url: String? = null,
    val text: String? = null,
    val images: List<ImportImage>? = null,
)

@Serializable
data class ImportImage(
    @SerialName("media_type") val mediaType: String,
    @SerialName("data_base64") val dataBase64: String,
)

/** `ImportPreview` (`schemas/link_import.py`). */
@Serializable
data class ImportPreview(
    val source: ImportPreviewSource = ImportPreviewSource(),
    val title: String = "",
    val summary: String? = null,
    val destinations: List<PreviewDestination> = emptyList(),
    val places: List<PreviewPlace> = emptyList(),
    val unresolved: List<PreviewPlace> = emptyList(),
    @SerialName("also_mentioned") val alsoMentioned: List<AlsoMentioned> = emptyList(),
    @SerialName("suggested_days") val suggestedDays: Int = 0,
    @SerialName("season_hint") val seasonHint: String? = null,
    val interests: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class ImportPreviewSource(
    val kind: String? = null,
    val url: String? = null,
    val title: String? = null,
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("favicon_url") val faviconUrl: String? = null,
    val author: String? = null,
)

@Serializable
data class PreviewDestination(
    val name: String = "",
    val city: String = "",
    val country: String = "",
    val airports: List<String> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("place_count") val placeCount: Int = 0,
    val nights: Int = 0,
)

@Serializable
data class PreviewPlace(
    val id: String = "",
    val name: String = "",
    val kind: String = "sight",
    val city: String? = null,
    val country: String? = null,
    val destination: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val approximate: Boolean = true,
    val verified: Boolean = false,
    val address: String? = null,
    val rating: LenientDouble = null,
    val note: String? = null,
    val quote: String? = null,
    @SerialName("day_hint") val dayHint: LenientInt = null,
    val confidence: Double = 0.7,
)

@Serializable
data class AlsoMentioned(
    val name: String = "",
    @SerialName("place_count") val placeCount: Int = 0,
)

/** `POST /api/import/resolve-place` body. */
@Serializable
data class ResolvePlaceRequest(
    val name: String,
    val city: String,
    val id: String? = null,
    val kind: String = "sight",
    val country: String? = null,
    val note: String? = null,
    val quote: String? = null,
)

/** `ResolvedPlace` = `{place, destination}`; 422 `city_not_found`. */
@Serializable
data class ResolvedPlace(val place: PreviewPlace, val destination: PreviewDestination)
