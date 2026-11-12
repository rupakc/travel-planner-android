package io.github.rupakc.travelplanner.feature.trips.prep

import io.github.rupakc.travelplanner.feature.trips.R

/** String resources of the twelve prep items (a server id this build does not know is shown by its id). */
internal object PrepCopy {
    val titles: Map<String, Int> =
        mapOf(
            "visa" to R.string.prep_item_visa_title,
            "passport" to R.string.prep_item_passport_title,
            "bookings" to R.string.prep_item_bookings_title,
            "insurance" to R.string.prep_item_insurance_title,
            "documents" to R.string.prep_item_documents_title,
            "stress_test" to R.string.prep_item_stress_test_title,
            "sim" to R.string.prep_item_sim_title,
            "money" to R.string.prep_item_money_title,
            "packing" to R.string.prep_item_packing_title,
            "emergency_card" to R.string.prep_item_emergency_card_title,
            "checkin" to R.string.prep_item_checkin_title,
            "offline_pack" to R.string.prep_item_offline_pack_title,
        )

    val bodies: Map<String, Int> =
        mapOf(
            "visa" to R.string.prep_item_visa_body,
            "passport" to R.string.prep_item_passport_body,
            "bookings" to R.string.prep_item_bookings_body,
            "insurance" to R.string.prep_item_insurance_body,
            "documents" to R.string.prep_item_documents_body,
            "stress_test" to R.string.prep_item_stress_test_body,
            "sim" to R.string.prep_item_sim_body,
            "money" to R.string.prep_item_money_body,
            "packing" to R.string.prep_item_packing_body,
            "emergency_card" to R.string.prep_item_emergency_card_body,
            "checkin" to R.string.prep_item_checkin_body,
            "offline_pack" to R.string.prep_item_offline_pack_body,
        )
}
