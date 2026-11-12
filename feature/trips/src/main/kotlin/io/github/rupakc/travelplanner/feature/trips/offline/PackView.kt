package io.github.rupakc.travelplanner.feature.trips.offline

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.domain.format.parseLocalizedAmount
import io.github.rupakc.travelplanner.core.domain.itinerary.PlanItinerary
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.domain.journey.Money
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import io.github.rupakc.travelplanner.core.model.trips.PackEmergency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One dialable emergency number; [kind] is `police`, `ambulance`, … (an unknown kind is shown with underscores as spaces). */
@Immutable
data class EmergencyNumber(val kind: String, val number: String) {
    /** `tel:` URI with digits, `+`, `*` and `#` only. */
    val telUri: String get() = telUri(number)
}

@Immutable
data class EmergencyContact(val name: String, val phone: String?)

@Immutable
data class EmergencyCityView(
    val city: String?,
    val numbers: List<EmergencyNumber>,
    val embassy: EmergencyContact?,
    val hospitals: List<EmergencyContact>,
)

/** [aiChecked] is true when the numbers came from the AI and carry an `as_of` date; otherwise they are the reference list. */
@Immutable
data class EmergencyView(val aiChecked: Boolean, val asOf: String?, val cities: List<EmergencyCityView>)

/** The renderable parts of a saved [OfflinePack], computed with no network. */
@Immutable
data class PackView(
    val pack: OfflinePack,
    val kb: Int,
    val emergency: EmergencyView?,
    val days: List<ScheduledDay>,
    val edits: Map<String, SlotEdit>,
    val notes: Map<String, String>,
) {
    companion object {
        fun of(pack: OfflinePack): PackView {
            val selections =
                try {
                    TravelJson.decodeFromJsonElement(PlanSelections.serializer(), pack.selections)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            val kb = (TravelJson.encodeToString(OfflinePack.serializer(), pack).length / BYTES_PER_KB).coerceAtLeast(1)
            return PackView(
                pack = pack,
                kb = kb,
                emergency = pack.emergency?.let(::emergencyView),
                days = selections?.let { PlanItinerary.days(it) }.orEmpty(),
                edits = selections?.let { SlotEdit.fromSelections(it.itineraryEdits) }.orEmpty(),
                notes = selections?.itineraryNotes?.filterValues(String::isNotBlank).orEmpty(),
            )
        }

        private const val BYTES_PER_KB = 1024

        private fun emergencyView(e: PackEmergency): EmergencyView? {
            if (e.cities.isEmpty()) return null
            return EmergencyView(
                aiChecked = e.source == "ai" && e.asOf != null,
                asOf = e.asOf,
                cities =
                    e.cities.map { c ->
                        EmergencyCityView(
                            city = c.city,
                            numbers = c.emergencyNumbers.mapNotNull { (k, v) -> dialable(v)?.let { EmergencyNumber(k, it) } },
                            embassy = contact(c.embassy),
                            hospitals = c.hospitals.mapNotNull { (it as? JsonObject)?.let(::contact) },
                        )
                    },
            )
        }

        private fun dialable(value: JsonElement): String? =
            (value as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.any(Char::isDigit) }

        private fun contact(o: JsonObject?): EmergencyContact? {
            val name = (o?.get("name") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
            val phone = (o["phone"] as? JsonPrimitive)?.contentOrNull?.takeIf { p -> p.any(Char::isDigit) }
            return EmergencyContact(name, phone)
        }
    }
}

/** `tel:` URI keeping only digits, `+`, `*` and `#`. */
fun telUri(number: String): String = "tel:" + number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

/** Offline currency maths. `rate` is local units per 1 home unit (the OfflinePack contract). */
object PackConverter {
    /**
     * [text] converted: home to local multiplies by [rate], local to home divides. Rounded to [target]'s minor
     * units. Null when the amount is not a number, is negative, or the rate is unusable.
     */
    fun convert(text: String, rate: Double?, toLocal: Boolean, target: String, locale: Locale): String? {
        if (rate == null || !rate.isFinite() || rate <= 0.0) return null
        val amount = parseLocalizedAmount(text, locale)?.takeIf { it >= 0.0 } ?: return null
        val value =
            if (toLocal) {
                BigDecimal(
                    amount,
                ).multiply(BigDecimal(rate))
            } else {
                BigDecimal(amount).divide(BigDecimal(rate), MATH_SCALE, RoundingMode.HALF_UP)
            }
        val exp = Money.exponent(target)
        val rounded = value.setScale(exp, RoundingMode.HALF_UP)
        return NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = exp
        }.format(rounded)
    }

    private const val MATH_SCALE = 10
}
