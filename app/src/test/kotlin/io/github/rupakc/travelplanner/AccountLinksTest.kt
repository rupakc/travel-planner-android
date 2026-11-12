package io.github.rupakc.travelplanner

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.model.navigation.ResetPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.model.navigation.VerifyEmailRoute
import io.github.rupakc.travelplanner.navigation.AccountLinks
import io.github.rupakc.travelplanner.navigation.DeepLinkIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AccountLinksTest {
    private val host = "app.example.org"

    @Test
    fun `verify and reset links on the prod host open their screens with the token`() {
        assertEquals(VerifyEmailRoute("abc-DEF_123.~"), AccountLinks.route("https://$host/verify?token=abc-DEF_123.~", host))
        assertEquals(ResetPasswordRoute("t0k"), AccountLinks.route("https://$host/reset-password?token=t0k", host))
        assertEquals(VerifyEmailRoute("x"), AccountLinks.route("https://APP.example.org/verify?token=x", host))
    }

    @Test
    fun `a share link on the prod host opens the public share viewer`() {
        assertEquals(ShareRoute("Ab-_09xyz"), AccountLinks.route("https://$host/share/Ab-_09xyz", host))
        assertEquals(ShareRoute("tok"), AccountLinks.route("https://APP.example.org/share/tok", host))
    }

    @Test
    fun `share links from other hosts or with extra parts are rejected`() {
        val bad =
            listOf(
                "https://evil.example.org/share/tok",
                "https://$host.evil.org/share/tok",
                "https://evil.org@$host/share/tok",
                "https://$host:8443/share/tok",
                "http://$host/share/tok",
                "https://$host/share/",
                "https://$host/share",
                "https://$host/share/tok/page",
                "https://$host/share/tok/card.png",
                "https://$host/share/tok?next=/admin",
                "https://$host/share/tok?token=abc",
                "https://$host/share/tok#frag",
                "https://$host/share/to%20k",
                "https://$host/share/to.k",
                "https://$host//share/tok",
                "https://$host/api/share/tok",
                "https://$host/share/" + "a".repeat(129),
            )
        bad.forEach { assertNull(it, AccountLinks.route(it, host)) }
        assertNull(AccountLinks.route("https://$host/share/tok", ""))
    }

    @Test
    fun `other hosts, schemes, paths, params and long or odd tokens are rejected`() {
        val bad =
            listOf(
                "https://evil.example.org/verify?token=abc",
                "https://$host.evil.org/verify?token=abc",
                "https://evil.org@$host/verify?token=abc",
                "https://$host:8443/verify?token=abc",
                "http://$host/verify?token=abc",
                "travelplanner://$host/verify?token=abc",
                "https://$host/verify/extra?token=abc",
                "https://$host/verify/?token=abc",
                "https://$host/admin?token=abc",
                "https://$host/verify",
                "https://$host/verify?token=",
                "https://$host/verify?token=a&token=b",
                "https://$host/verify?token=abc&next=/admin",
                "https://$host/verify?code=abc",
                "https://$host/verify?token=abc#frag",
                "https://$host/verify?token=" + "a".repeat(AccountLinks.MAX_TOKEN + 1),
                "https://$host/verify?token=a%20b",
                "https://$host/verify?token=a/b",
                "",
            )
        bad.forEach { assertNull(it, AccountLinks.route(it, host)) }
        assertNull(AccountLinks.route(null, host))
        assertNull(AccountLinks.route("https://$host/verify?token=abc", ""))
        assertEquals(
            VerifyEmailRoute("a".repeat(AccountLinks.MAX_TOKEN)),
            AccountLinks.route("https://$host/verify?token=" + "a".repeat(AccountLinks.MAX_TOKEN), host),
        )
    }

    @Test
    fun `sanitize keeps an account link, drops look-alikes, and still allows the oauth return`() {
        val ok = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse("https://$host/reset-password?token=abc")), host)
        assertEquals("https://$host/reset-password?token=abc", ok.dataString)
        assertEquals("https://$host/reset-password?token=abc", DeepLinkIntents.accountLink(ok, host))
        assertEquals(ResetPasswordRoute("abc"), DeepLinkIntents.accountRoute(DeepLinkIntents.accountLink(ok, host)!!, host))
        listOf(
            "https://evil.org/verify?token=abc",
            "https://$host/verify/x?token=abc",
            "https://$host/verify?token=abc&x=1",
        ).forEach { link ->
            val i = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse(link)), host)
            assertNull(link, i.data)
            assertNull(link, DeepLinkIntents.accountLink(i, host))
        }
        val notView = DeepLinkIntents.sanitize(Intent(Intent.ACTION_SEND, Uri.parse("https://$host/verify?token=abc")), host)
        assertNull(notView.data)
        val oauth = DeepLinkIntents.sanitize(Intent(Intent.ACTION_VIEW, Uri.parse("voyager://auth/callback?code=abc")), host)
        assertEquals("voyager://auth/callback?code=abc", oauth.dataString)
        assertNull(DeepLinkIntents.heldLink(oauth))
    }
}
