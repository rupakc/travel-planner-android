package io.github.rupakc.travelplanner.core.model.reference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/airports/search` element. */
@Serializable
data class Airport(
    @SerialName("iata_code") val iataCode: String? = null,
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)

/** `GET /api/nationalities/search` element. */
@Serializable
data class Nationality(
    val nationality: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)
