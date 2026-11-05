package io.github.rupakc.travelplanner

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.network.AuthEvents
import io.github.rupakc.travelplanner.navigation.DeepLinkIntents
import io.github.rupakc.travelplanner.navigation.ShellViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DeepLinkIntentsTest {
    @Test
    fun `navigation's internal deep-link extras are stripped from incoming intents`() {
        val intent =
            Intent(Intent.ACTION_MAIN)
                .putExtra("android-support-nav:controller:deepLinkIds", intArrayOf(1, 2))
                .putExtra("android-support-nav:controller:deepLinkArgs", arrayListOf<android.os.Bundle>())
                .putExtra("android-support-nav:controller:deepLinkExtras", android.os.Bundle())
                .putExtra("android-support-nav:controller:deepLinkIntent", Intent())
        DeepLinkIntents.sanitize(intent)
        DeepLinkIntents.NAV_EXTRAS.forEach { assertFalse(it, intent.hasExtra(it)) }
    }

    @Test
    fun `only app-scheme VIEW links survive`() {
        val app = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse("travelplanner://plan/7")))
        assertEquals("travelplanner://plan/7", app.dataString)
        val web = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse("https://evil.example/share/abc")))
        assertNull(web.data)
        assertEquals(
            "travelplanner://alerts?kind=fare&watch=3",
            DeepLinkIntents.appLink(Intent(Intent.ACTION_VIEW, Uri.parse("travelplanner://alerts?kind=fare&watch=3"))),
        )
        assertNull(DeepLinkIntents.appLink(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `a deep link that hit the login gate is kept across recreation and replayed once`() {
        val handle = SavedStateHandle()
        val shell = ShellViewModel(AuthEvents(), handle)
        assertNull(shell.takePendingDeepLink())
        shell.keepPendingDeepLink("travelplanner://plan/7")
        // Process death / recreation: a new ViewModel on the same saved state still has it.
        val restored = ShellViewModel(AuthEvents(), handle)
        assertEquals("travelplanner://plan/7", restored.takePendingDeepLink())
        assertNull(restored.takePendingDeepLink())
    }
}
