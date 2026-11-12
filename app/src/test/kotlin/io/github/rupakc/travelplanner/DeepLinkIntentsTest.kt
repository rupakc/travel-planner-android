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
    fun `the oauth return survives only as voyager auth callback with code or error`() {
        val ok = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse("voyager://auth/callback?code=abc")))
        assertEquals("voyager://auth/callback?code=abc", ok.dataString)
        assertEquals(
            io.github.rupakc.travelplanner.core.domain.auth.OauthCallback.Code("abc"),
            DeepLinkIntents.oauthCallback(ok),
        )
        listOf("voyager://auth/callback", "voyager://auth/x?code=a", "voyager://evil/callback?code=a").forEach {
            val i = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
            assertNull(it, i.data)
            assertNull(it, DeepLinkIntents.oauthCallback(i))
        }
        assertNull(DeepLinkIntents.oauthCallback(Intent(Intent.ACTION_SEND, Uri.parse("voyager://auth/callback?code=a"))))
    }

    @Test
    fun `only app-scheme VIEW links survive`() {
        val app = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse("travelplanner://plan/7")))
        assertEquals("travelplanner://plan/7", app.dataString)
        val web = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse("https://evil.example/share/abc")))
        assertNull(web.data)
        // App-scheme data on any other action would route through handleDeepLink without being held by the gate.
        val send = DeepLinkIntents.sanitize(Intent(Intent.ACTION_SEND, Uri.parse("travelplanner://plan/7")))
        assertNull(send.data)
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
        assertNull(shell.take())
        shell.keep("travelplanner://plan/7")
        // Process death / recreation: a new ViewModel on the same saved state still has it.
        val restored = ShellViewModel(AuthEvents(), handle)
        assertEquals("travelplanner://plan/7", restored.take())
        assertNull(restored.take())
        restored.keep("travelplanner://chat")
        restored.clear()
        assertNull(restored.take())
    }
}
