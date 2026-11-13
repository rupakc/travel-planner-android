package io.github.rupakc.travelplanner.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.domain.alerts.resolveNotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tapping a notification must reach MainActivity for every kind, including the hosts the manifest filters do not
 * declare (`trip`, `notifications`, `feed`, `play`, `u`): the intent is explicit, so it needs no filter.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AppOpenIntentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val main = ComponentName(context.packageName, "io.github.rupakc.travelplanner.MainActivity")

    @Before
    fun registerLauncher() {
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(main)
        pm.addIntentFilterForActivity(
            main,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private fun link(kind: String, link: String?, planId: String? = null): String {
        val target = resolveNotificationTarget(kind, link, planId, null, null)
        return NotificationLinks.of(target, category = null)
    }

    private val cases: Map<String, String> by lazy {
        mapOf(
            "trip_t7" to link("trip_t7", "/trips/12/today", planId = "12"),
            "trip_morning" to link("trip_morning", "/trips/12/today", planId = "12"),
            "social_new_follower" to link("social_new_follower", "/u/ada"),
            "quiz_h2h_invite" to link("quiz_h2h_invite", "/play/h2h/m1"),
            "price_drop" to link("price_drop", "/alerts?kind=fare&watch=3"),
            "inbox" to NotificationLinks.INBOX,
            "feed" to NotificationLinks.FEED,
        )
    }

    @Test
    fun `every notification kind resolves to MainActivity and keeps its link`() {
        for ((kind, deepLink) in cases) {
            val intent = appOpenIntent(context, "$deepLink${if ('?' in deepLink) "&" else "?"}notif_cat=price_drops")
            assertEquals(kind, Intent.ACTION_VIEW, intent.action)
            assertEquals(kind, main, intent.component)
            assertEquals(kind, "$deepLink${if ('?' in deepLink) "&" else "?"}notif_cat=price_drops", intent.data.toString())
            assertEquals(kind, main.className, context.packageManager.resolveActivity(intent, 0)?.activityInfo?.name)
        }
    }

    @Test
    fun `the old implicit intent resolves to nothing for hosts without a manifest filter`() {
        val implicit = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("travelplanner://notifications")).setPackage(context.packageName)
        assertNull(context.packageManager.resolveActivity(implicit, 0))
    }

    @Test
    fun `a posted notification carries the explicit intent`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifier = SystemNotifier(context)
        notifier.post(TravelChannel.PRICE_ALERTS, 5, "t", "b", "travelplanner://trip/12?tab=today")
        val pending = shadowOf(manager).allNotifications.single().contentIntent
        assertNotNull(pending)
        val sent = shadowOf(pending).savedIntent
        assertEquals(main, sent.component)
        assertEquals("travelplanner://trip/12?tab=today", sent.data.toString())
    }
}
