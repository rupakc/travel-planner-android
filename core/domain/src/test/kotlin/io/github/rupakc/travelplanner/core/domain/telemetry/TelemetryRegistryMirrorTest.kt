package io.github.rupakc.travelplanner.core.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Phase 1/2 mirror of the server registry: names and enum values are wire tokens, nothing is duplicated. */
class TelemetryRegistryMirrorTest {
    private val events =
        listOf(
            TelemetryEvents.AUTH_START,
            TelemetryEvents.AUTH_RESULT,
            TelemetryEvents.SIGNUP_STEP,
            TelemetryEvents.EMAIL_VERIFY,
            TelemetryEvents.PASSWORD_RESET,
            TelemetryEvents.ACCOUNT_ACTION,
            TelemetryEvents.QUOTA_BLOCK,
            TelemetryEvents.GAME_AWARD,
            TelemetryEvents.BADGE_EARNED,
            TelemetryEvents.LEVEL_UP,
            TelemetryEvents.GAME_VIEW,
            TelemetryEvents.TRIP_VIEW,
            TelemetryEvents.TRIP_ACTION,
            TelemetryEvents.PREP_TOGGLE,
            TelemetryEvents.PREP_COMPLETE,
            TelemetryEvents.TODAY_ACTION,
            TelemetryEvents.OFFLINE_PACK,
            TelemetryEvents.OUTBOX_FLUSH,
            TelemetryEvents.EXPENSE_ACTION,
            TelemetryEvents.JOURNAL_ACTION,
            TelemetryEvents.PLACE_ACTION,
            TelemetryEvents.RECAP_ACTION,
            TelemetryEvents.COMPLETENESS_VIEW,
            TelemetryEvents.ONBOARDING_STEP,
            TelemetryEvents.ONBOARDING_RESULT,
            TelemetryEvents.ALERT_STORY_VIEW,
            TelemetryEvents.ALERT_ADVICE,
            TelemetryEvents.NOTIF_SETTINGS_VIEW,
            TelemetryEvents.NOTIF_CATEGORY_TOGGLE,
            TelemetryEvents.NOTIF_QUIET_HOURS_CHANGE,
            TelemetryEvents.NOTIF_CAP_CHANGE,
            TelemetryEvents.NOTIF_OPEN,
        )

    @Test
    fun `event names are the server names and unique tokens`() {
        assertEquals(events.size, events.toSet().size)
        assertTrue(events.all(TelemetryProps::isToken))
        assertEquals("trip_view", TelemetryEvents.TRIP_VIEW)
        assertEquals("outbox_flush", TelemetryEvents.OUTBOX_FLUSH)
        assertEquals("onboarding_result", TelemetryEvents.ONBOARDING_RESULT)
    }

    @Test
    fun `enum values are tokens and match the registry sizes`() {
        val sets =
            listOf(
                TelemetryEnums.AUTH_METHOD to 5,
                TelemetryEnums.NOTIF_CATEGORY to 7,
                TelemetryEnums.NOTIF_SOURCE to 2,
                TelemetryEnums.AUTH_MODE to 3,
                TelemetryEnums.AUTH_RESULT to 6,
                TelemetryEnums.SIGNUP_STEP to 4,
                TelemetryEnums.EMAIL_VERIFY_RESULT to 6,
                TelemetryEnums.PASSWORD_RESET_STEP to 2,
                TelemetryEnums.PASSWORD_RESET_RESULT to 5,
                TelemetryEnums.ACCOUNT_ACTION to 11,
                TelemetryEnums.QUOTA_BUCKET to 6,
                TelemetryEnums.QUOTA_REASON to 2,
                TelemetryEnums.GAME_VIEW_SURFACE to 3,
                TelemetryEnums.TRIP_VIEW_SURFACE to 8,
                TelemetryEnums.TRIP_PHASE to 6,
                TelemetryEnums.TRIP_ACTION to 9,
                TelemetryEnums.PREP_ITEM to 12,
                TelemetryEnums.TODAY_ACTION to 6,
                TelemetryEnums.OFFLINE_PACK_ACTION to 4,
                TelemetryEnums.OFFLINE_PACK_RESULT to 4,
                TelemetryEnums.EXPENSE_ACTION to 3,
                TelemetryEnums.EXPENSE_CATEGORY to 8,
                TelemetryEnums.JOURNAL_ACTION to 3,
                TelemetryEnums.PLACE_ACTION to 4,
                TelemetryEnums.PLACE_KIND to 8,
                TelemetryEnums.RECAP_ACTION to 4,
                TelemetryEnums.COMPLETENESS_SURFACE to 2,
                TelemetryEnums.ONBOARDING_STEP_ACTION to 4,
                TelemetryEnums.ONBOARDING_TYPE to 8,
                TelemetryEnums.ALERT_KIND to 4,
                TelemetryEnums.ALERT_VERDICT to 5,
                TelemetryEnums.ALERT_ADVICE_OUTCOME to 3,
            )
        for ((set, size) in sets) {
            assertEquals(size, set.size)
            assertTrue(set.all(TelemetryProps::isToken))
        }
    }
}
