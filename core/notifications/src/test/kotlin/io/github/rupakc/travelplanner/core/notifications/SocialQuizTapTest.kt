package io.github.rupakc.travelplanner.core.notifications

import io.github.rupakc.travelplanner.core.domain.alerts.NotificationTarget
import io.github.rupakc.travelplanner.core.domain.alerts.QUIZ_PUSH_KINDS
import io.github.rupakc.travelplanner.core.domain.alerts.SOCIAL_PUSH_KINDS
import io.github.rupakc.travelplanner.core.domain.alerts.resolveNotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every social and quiz notification kind of the Social API lands on a screen that exists, never on nothing. */
class SocialQuizTapTest {
    private val links =
        mapOf(
            "social_idea_saved" to "/feed/12",
            "social_new_follower" to "/u/ada",
            "quiz_h2h_invite" to "/play/h2h/m1",
            "quiz_h2h_result" to "/play/h2h/m2",
            "social_idea_liked" to "/feed",
            "social_idea_comment" to "/feed",
            "quiz_daily" to "/play",
        )

    @Test
    fun `the contract kinds are exactly the ones the app routes`() {
        assertEquals(links.keys, SOCIAL_PUSH_KINDS + QUIZ_PUSH_KINDS)
    }

    @Test
    fun `each kind resolves to a target whose deep link is a real in-app destination`() {
        for ((kind, link) in links) {
            val target = resolveNotificationTarget(kind, link, null, null, null)
            val deepLink = NotificationLinks.of(target)
            assertTrue("$kind -> $deepLink", deepLink.startsWith("travelplanner://"))
            assertNotEquals("$kind must not fall into the price-alert screen", NotificationTarget.Alert::class, target::class)
        }
    }

    @Test
    fun `each kind lands on its own screen`() {
        fun link(kind: String) = NotificationLinks.of(resolveNotificationTarget(kind, links.getValue(kind), null, null, null))
        assertEquals("travelplanner://idea/12", link("social_idea_saved"))
        assertEquals("travelplanner://u/ada", link("social_new_follower"))
        assertEquals("travelplanner://play/h2h/m1", link("quiz_h2h_invite"))
        assertEquals("travelplanner://play/h2h/m2", link("quiz_h2h_result"))
        assertEquals("travelplanner://feed", link("social_idea_liked"))
        assertEquals("travelplanner://feed", link("social_idea_comment"))
        assertEquals("travelplanner://play", link("quiz_daily"))
    }

    @Test
    fun `the Play target is the plain play deep link and carries the tap category`() {
        assertEquals("travelplanner://play", io.github.rupakc.travelplanner.core.model.navigation.DeepLinks.PLAY_PATTERN)
        assertEquals("travelplanner://play", NotificationLinks.of(NotificationTarget.Play))
        assertEquals(
            "travelplanner://play?notif_cat=quiz",
            NotificationLinks.of(NotificationTarget.Play, io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory.QUIZ),
        )
        assertEquals("travelplanner://idea/9", NotificationLinks.of(resolveNotificationTarget("social_idea_saved", "/feed/9", null, null, null)))
    }

    @Test
    fun `a kind with a bad or missing link still opens the list`() {
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("quiz_h2h_invite", null, null, null, null))
        assertEquals(NotificationTarget.Inbox, resolveNotificationTarget("quiz_h2h_result", null, null, null, null))
        assertEquals(NotificationTarget.Feed, resolveNotificationTarget("social_new_follower", null, null, null, null))
    }
}
