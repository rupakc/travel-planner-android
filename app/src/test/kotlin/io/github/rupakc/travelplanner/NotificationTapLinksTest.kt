package io.github.rupakc.travelplanner

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.rupakc.travelplanner.navigation.DeepLinkIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Notification taps are explicit intents to the launcher activity (the manifest declares no filter for `trip`,
 * `notifications`, `feed`, `play`, `u`); MainActivity still sanitises them and keeps the app link.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class NotificationTapLinksTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    private val links =
        listOf(
            "travelplanner://notifications?notif_cat=price_drops",
            "travelplanner://trip/12?tab=today&notif_cat=trip_reminders",
            "travelplanner://feed",
            "travelplanner://play/h2h/m1",
            "travelplanner://u/ada",
            "travelplanner://alerts?kind=fare&watch=3&notif_cat=price_drops",
        )

    @Test
    fun `the launcher component is MainActivity and each tap link survives sanitize`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        assertEquals(ComponentName(context.packageName, MainActivity::class.java.name), launcher)
        for (link in links) {
            val tap = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setComponent(launcher)
            assertNotNull(link, context.packageManager.resolveActivity(tap, 0))
            assertEquals(link, DeepLinkIntents.sanitize(tap).data.toString())
            assertEquals(link, DeepLinkIntents.appLink(tap))
        }
    }

    @Test
    fun `MainActivity launched by a tap keeps the link`() {
        for (link in links) {
            val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(link))
            val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
            // The shell may strip the `notif_cat` tag (PushTaps); the destination must stay.
            assertEquals(link, Uri.parse(link).host, activity.intent.data?.host)
        }
    }
}
