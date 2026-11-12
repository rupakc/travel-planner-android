package io.github.rupakc.travelplanner.feature.account

import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import java.io.File
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRulesTest {
    private val base = AccountSettings()

    @Test fun `no change sends nothing`() {
        assertTrue(AccountRules.diff(base, base.copy()).isEmpty())
    }

    @Test fun `only the changed keys are sent`() {
        val patch = AccountRules.diff(base, base.copy(theme = "dark", hideGame = true))
        assertEquals(setOf("theme", "hide_game"), patch.keys)
        assertEquals(JsonPrimitive("dark"), patch["theme"])
        assertEquals(JsonPrimitive(true), patch["hide_game"])
    }

    @Test fun `clearing a nullable key sends json null`() {
        val patch = AccountRules.diff(base.copy(language = "fr", bucketListGoal = 20), base)
        assertEquals(JsonNull, patch["language"])
        assertEquals(JsonNull, patch["bucket_list_goal"])
        assertEquals(2, patch.size)
    }

    @Test fun `delete needs the password when the account has one`() {
        assertEquals("pw", AccountRules.deleteRequest(true, "pw", "jane")?.password)
        assertNull(AccountRules.deleteRequest(true, "", "jane"))
        assertNull(AccountRules.deleteRequest(true, "pw", "jane")?.confirmUsername)
    }

    @Test fun `delete needs the username when the account has no password`() {
        val ok = AccountRules.deleteRequest(false, " Jane ", "jane")
        assertEquals("jane", ok?.confirmUsername)
        assertNull(ok?.password)
        assertNull(AccountRules.deleteRequest(false, "someone", "jane"))
        assertNull(AccountRules.deleteRequest(false, "", "jane"))
    }

    @Test fun `export target lives under the cache directory`() {
        val cache = File("/data/user/0/app/cache")
        assertEquals(cache.path, AccountRules.exportTarget(cache).parentFile!!.parentFile!!.path)
        assertTrue(AccountRules.exportTarget(cache).path.startsWith(cache.path))
    }

    @Test fun `initials`() {
        assertEquals("JD", AccountRules.initials("Jane Doe", "jd"))
        assertEquals("AB", AccountRules.initials(null, "ab"))
        assertEquals("JD", AccountRules.initials(null, "jane_doe"))
    }
}
