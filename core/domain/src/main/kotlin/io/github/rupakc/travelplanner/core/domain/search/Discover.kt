package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.model.search.DiscoveryRequest
import java.util.Locale

/** Web Surprise-me button rule: enabled only with an origin and a nationality. */
fun canDiscover(form: SearchForm): Boolean = form.origin.isNotBlank() && form.nationality.isNotBlank()

/** Web `handleDiscover` body from the discover form. */
fun toDiscoveryRequest(form: SearchForm, locale: Locale): DiscoveryRequest =
    DiscoveryRequest(
        origin = form.origin.trim(),
        departureDate = form.departureDate,
        nationality = form.nationality.trim(),
        budgetUsd = parseBudget(form.budgetUsd, locale),
        returnDate = form.returnDate.ifBlank { null },
        interests = form.interests,
        // The backend requires adults ≥ 1 (DiscoveryRequest.adults ge=1); a seniors-only party still sends 1.
        adults = form.travellers.adults.coerceAtLeast(1),
        children = form.travellers.children,
        seniors = form.travellers.seniors,
        infants = form.travellers.infants,
    )

/**
 * Web DestinationCard "Plan this trip": the known form takes the pick ("City, Country") plus the
 * discover form's origin, dates, nationality, budget and interests; everything else stays.
 */
fun applyDiscoveredDestination(known: SearchForm, discover: SearchForm, pick: DiscoveredDestination): SearchForm =
    known.copy(
        destination = listOfNotNull(pick.city?.ifBlank { null }, pick.country?.ifBlank { null }).joinToString(", "),
        origin = discover.origin,
        departureDate = discover.departureDate,
        returnDate = discover.returnDate,
        nationality = discover.nationality,
        budgetUsd = discover.budgetUsd,
        interests = discover.interests,
    )

/** Web visa badge kinds on a DestinationCard. */
enum class VisaBadge { VISA_FREE, ON_ARRIVAL, E_VISA, REQUIRED, VERIFY }

fun visaBadge(visaType: String?): VisaBadge =
    when (visaType) {
        "visa-free" -> VisaBadge.VISA_FREE
        "visa-on-arrival" -> VisaBadge.ON_ARRIVAL
        "e-visa" -> VisaBadge.E_VISA
        "required" -> VisaBadge.REQUIRED
        else -> VisaBadge.VERIFY
    }
