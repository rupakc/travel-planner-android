package io.github.rupakc.travelplanner.core.notifications

import io.github.rupakc.travelplanner.core.domain.alerts.NotificationTarget
import io.github.rupakc.travelplanner.core.domain.alerts.resolveNotificationTarget
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationLinksH2hTest {
    @Test
    fun `a head-to-head target opens the match, tagged with its category`() {
        assertEquals("travelplanner://play/h2h/abc-1", NotificationLinks.of(NotificationTarget.H2h("abc-1")))
        assertEquals(
            "travelplanner://play/h2h/abc-1?notif_cat=quiz",
            NotificationLinks.of(NotificationTarget.H2h("abc-1"), NotificationCategory.QUIZ),
        )
    }

    @Test
    fun `a quiz push with a match path resolves to the match and an odd path to the list`() {
        val ok = resolveNotificationTarget("quiz_h2h_invite", "/play/h2h/m_7", null, null, null, "quiz")
        assertEquals(NotificationTarget.H2h("m_7"), ok)
        val bad = resolveNotificationTarget("quiz_h2h_result", "https://evil.example/x", null, null, null, "quiz")
        assertEquals(NotificationTarget.Inbox, bad)
    }
}
