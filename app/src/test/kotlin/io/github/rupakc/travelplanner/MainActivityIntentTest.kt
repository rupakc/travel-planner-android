package io.github.rupakc.travelplanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.rupakc.travelplanner.navigation.DeepLinkIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** The exported MainActivity sanitises its launch intent before the NavHost reads it, and every new intent (S-M1). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class MainActivityIntentTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    private fun hostile(data: String): Intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(data))
            .putExtra(DeepLinkIntents.NAV_EXTRAS[0], intArrayOf(1, 2))
            .putExtra(DeepLinkIntents.NAV_EXTRAS[2], Bundle())

    private fun assertClean(intent: Intent) = DeepLinkIntents.NAV_EXTRAS.forEach { assertFalse(it, intent.hasExtra(it)) }

    @Test
    fun `launch and new intents are sanitised`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, hostile("https://evil.example/share/x")).setup()
        val launched = controller.get().intent
        assertClean(launched)
        assertNull(launched.data)

        val next = hostile("travelplanner://plan/7")
        controller.newIntent(next)
        assertClean(next)
        assertEquals("travelplanner://plan/7", next.dataString)
        controller.pause().stop().destroy()
    }

    @Test
    fun `cold start from a tagged push link does not crash and strips the tag`() {
        val intent = hostile("travelplanner://notifications?notif_cat=price_drops")
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        assertEquals("travelplanner://notifications", controller.get().intent.dataString)
        controller.pause().stop().destroy()
    }
}
