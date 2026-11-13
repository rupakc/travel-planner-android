package io.github.rupakc.travelplanner.core.notifications

import io.github.rupakc.travelplanner.core.domain.alerts.NotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationLinksTest {
    @Test
    fun `a social notification opens the ideas feed`() {
        assertEquals("travelplanner://feed", NotificationLinks.of(NotificationTarget.Feed))
    }

    @Test
    fun `the feed link carries the tap category like the others`() {
        val link = NotificationLinks.of(NotificationTarget.Feed, io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory.SOCIAL)
        assertEquals("travelplanner://feed?notif_cat=social", link)
    }

    @Test
    fun `match notifications still land on the inbox until the match screens exist`() {
        assertEquals("travelplanner://notifications", NotificationLinks.of(NotificationTarget.Inbox))
    }
}
