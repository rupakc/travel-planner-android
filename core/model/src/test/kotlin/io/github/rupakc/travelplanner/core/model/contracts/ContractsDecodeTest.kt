package io.github.rupakc.travelplanner.core.model.contracts

import io.github.rupakc.travelplanner.core.model.auth.AccountDto
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import io.github.rupakc.travelplanner.core.model.auth.OauthResultKind
import io.github.rupakc.travelplanner.core.model.auth.QuotaDto
import io.github.rupakc.travelplanner.core.model.auth.RefreshResponse
import io.github.rupakc.travelplanner.core.model.auth.SessionDto
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailResponse
import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.game.GameMe
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.trips.CheckinResponse
import io.github.rupakc.travelplanner.core.model.trips.ExpensesResponse
import io.github.rupakc.travelplanner.core.model.trips.JournalResponse
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.PlacesResponse
import io.github.rupakc.travelplanner.core.model.trips.PrepResponse
import io.github.rupakc.travelplanner.core.model.trips.PrepToggleResponse
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceResponse
import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.core.model.trips.RecapCompleteResponse
import io.github.rupakc.travelplanner.core.model.trips.Stats
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.model.trips.TripsResponse
import kotlinx.serialization.DeserializationStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden JSON of every Accounts-API / Trips-API object (examples copied from the contract pages) decodes. */
class ContractsDecodeTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/contracts/$name.json")) { "missing fixture $name" }.readText()

    private fun <T> decode(name: String, s: DeserializationStrategy<T>): T = TravelJson.decodeFromString(s, fixture(name))

    @Test
    fun `user and session`() {
        val user = decode("user", User.serializer())
        assertEquals("jane_doe", user.username)
        assertEquals("Jane", user.displayName)
        assertTrue(user.emailVerified && user.accountVerified && user.hasPassword)
        assertEquals("password", user.signupMethod)
        assertEquals("https://example.com/api/avatars/6f1c.webp?v=3", user.avatarUrl)

        val s = decode("session", SessionDto.serializer())
        assertEquals("rt_abc", s.refreshToken)
        assertEquals(3600L, s.expiresIn)
        assertNull(s.user.avatarUrl)
    }

    @Test
    fun `legacy user without Phase 1 keys keeps old defaults`() {
        val user = TravelJson.decodeFromString(User.serializer(), """{"username":"alice","is_admin":true,"email":null}""")
        assertTrue(user.isAdmin)
        assertNull(user.id)
        assertFalse(user.emailVerified)
        assertTrue(user.accountVerified)
        assertTrue(user.hasPassword)
    }

    @Test
    fun `game delta`() {
        val d = decode("game_delta", GameDelta.serializer())
        assertEquals(150, d.xp)
        assertEquals("email_verified", d.awards.single().rule)
        assertEquals("verified", d.badges.single().id)
        assertEquals(2, d.levelUp?.to)
        assertFalse(d.isEmpty)
        assertTrue(GameDelta(xp = 5).isEmpty)
    }

    @Test
    fun `account settings and onboarding`() {
        val a = decode("account", AccountDto.serializer())
        assertEquals("oauth", a.user.signupMethod)
        assertFalse(a.user.hasPassword)
        assertEquals("github", a.identities.single().provider)
        assertEquals("es", a.settings.language)
        assertEquals(30, a.settings.bucketListGoal)
        assertTrue(a.settings.hideGame)
        assertEquals(3, a.onboarding.step)
        val settings = decode("settings", io.github.rupakc.travelplanner.core.model.auth.AccountSettings.serializer())
        assertNull(settings.language)
        assertNull(settings.bucketListGoal)
        assertEquals("system", settings.theme)
    }

    @Test
    fun `quota with an unlimited bucket`() {
        val q = decode("quota", QuotaDto.serializer())
        assertEquals(20, q.buckets.getValue("search").limit)
        assertNull(q.buckets.getValue("chat").limit)
    }

    @Test
    fun `verify email with and without a session`() {
        val v = decode("verify_email", VerifyEmailResponse.serializer())
        assertEquals("rt_1", v.session?.refreshToken)
        assertEquals(50, v.game?.xp)
        val only = decode("verify_email_confirmation_only", VerifyEmailResponse.serializer())
        assertNull(only.session)
        assertNull(only.game)
    }

    @Test
    fun `oauth results by status`() {
        val signedIn = decode("oauth_signed_in", OauthResult.serializer())
        assertEquals(OauthResultKind.SIGNED_IN, signedIn.kind)
        assertEquals("jane", signedIn.session?.user?.username)
        val needs = decode("oauth_needs_username", OauthResult.serializer())
        assertEquals(OauthResultKind.NEEDS_USERNAME, needs.kind)
        assertEquals("st_1", needs.signupToken)
        assertNull(needs.email)
        assertEquals(OauthResultKind.LINKED, decode("oauth_linked", OauthResult.serializer()).kind)
        assertEquals(OauthResultKind.UNKNOWN, TravelJson.decodeFromString(OauthResult.serializer(), "{}").kind)
    }

    @Test
    fun `refresh response`() {
        val r = decode("refresh", RefreshResponse.serializer())
        assertEquals("rt_new", r.refreshToken)
    }

    @Test
    fun `game me and catalog`() {
        val me = decode("game_me", GameMe.serializer())
        assertEquals(2, me.level)
        assertEquals(250, me.nextLevelXp)
        assertNull(me.badges[1].earnedAt)
        assertEquals(5, me.streaks.getValue("daily").longest)
        assertEquals("plan_saved", me.recent.single().rule)
        val cat = decode("game_catalog", GameCatalog.serializer())
        assertTrue(cat.rules.first { it.id == "traveller_type" }.once)
        assertEquals("bronze", cat.badges.single().tier)
        assertEquals(100, cat.levels[1].minXp)
    }

    @Test
    fun `trip and list with an unknown phase`() {
        val t = decode("trip", Trip.serializer())
        assertEquals(12L, t.id)
        assertEquals(Phase.PLANNING, t.phase)
        assertEquals(140, t.daysToStart)
        assertEquals(33, t.prep.percent)
        assertEquals(3000.0, t.budgetUsd!!, 0.0)
        assertEquals(listOf("Lisbon", "Porto"), t.destinations)
        val list = decode("trips_list", TripsResponse.serializer())
        assertEquals(1L, list.pinnedId)
        assertEquals(Phase.ON_THE_ROAD, list.trips[0].phase)
        assertNull(list.trips[1].phase)
        assertEquals(Phase.ON_THE_ROAD, Phase.fromWire("on_the_road"))
        assertNull(Phase.fromWire("x"))
    }

    @Test
    fun `stats`() {
        val s = decode("stats", Stats.serializer())
        assertEquals("PT", s.countries.single().code)
        assertEquals(30, s.bucketGoal)
        assertEquals(2, s.tripsByPhase["planning"])
    }

    @Test
    fun `prep and toggle`() {
        val p = decode("prep", PrepResponse.serializer())
        assertEquals("na", p.items[0].status)
        assertTrue(p.items[1].auto && p.items[1].overdue)
        assertEquals("visa-free", (p.items[0].facts["visa_type"] as kotlinx.serialization.json.JsonPrimitive).content)
        val t = decode("prep_toggle", PrepToggleResponse.serializer())
        assertTrue(t.complete)
        assertEquals("passport", t.item?.id)
        assertEquals("trip_ready", t.game?.awards?.single()?.rule)
    }

    @Test
    fun `offline pack full and minimal`() {
        val full = decode("offline_pack", OfflinePack.serializer())
        assertEquals("PT", full.destinations.single().countryCode)
        assertEquals("ai", full.emergency?.source)
        assertEquals("Português", full.phrases?.languageName)
        assertEquals("help", full.phrases?.items?.single()?.id)
        assertEquals("Leve-me ao Hotel X", full.addressCard?.takeMeTo?.text)
        assertEquals(0.92, full.currency?.rate!!, 0.0)
        assertTrue(full.selections.containsKey("itinerary"))
        assertEquals(listOf("2027-04-01"), full.checkins)
        val min = decode("offline_pack_minimal", OfflinePack.serializer())
        assertNull(min.phrases)
        assertNull(min.addressCard)
        assertNull(min.currency?.rate)
        assertTrue(min.selections.isEmpty())
    }

    @Test
    fun `expenses keep amounts as strings`() {
        val e = decode("expenses", ExpensesResponse.serializer())
        assertEquals("12.50", e.expenses.single().amount)
        assertEquals("123.40", e.totals.byCurrency["EUR"])
        assertEquals("140.20", e.totals.usdEstimate)
        assertEquals(listOf("GBP"), e.totals.unconverted)
        assertEquals(0.92, e.rate?.usdToLocal!!, 0.0)
        assertNull(decode("expenses_no_rate", ExpensesResponse.serializer()).rate)
    }

    @Test
    fun `journal and places`() {
        val j = decode("journal", JournalResponse.serializer())
        assertEquals("great", j.entries[0].mood)
        assertNull(j.entries[1].day)
        val put = decode("place_put", PutPlaceResponse.serializer())
        assertEquals("activity:pasteis-de-belem", put.place.placeKey)
        assertEquals(5, put.place.stars)
        assertEquals(38.69, put.place.lat!!, 0.0)
        assertEquals("place_visited", put.game?.awards?.single()?.rule)
        val places = decode("places", PlacesResponse.serializer())
        assertNull(places.places.single().stars)
        assertFalse(places.places.single().visited)
    }

    @Test
    fun `checkin recap and complete`() {
        val c = decode("checkin", CheckinResponse.serializer())
        assertTrue(c.newCountry)
        assertEquals(1, c.dayIndex)
        assertEquals(1, c.streak?.current)
        val r = decode("recap", Recap.serializer())
        assertEquals(2, r.route.size)
        assertEquals(38.70, r.route[0][0], 0.0)
        assertEquals(41.2, r.distanceKm!!, 0.0)
        assertTrue(r.countries.single().new)
        assertEquals("2140.50", r.spend.spentUsd)
        assertNull(r.spend.planCostUsd)
        assertTrue(r.canComplete)
        val done = decode("recap_complete", RecapCompleteResponse.serializer())
        assertEquals(listOf("PT"), done.newCountries)
        assertNull(done.game)
    }

    @Test
    fun `config with and without the new keys, unknown keys ignored`() {
        val full = decode("config_full", ClientConfigDto.serializer())
        val f = full.features!!
        assertTrue(f.accounts && f.game && f.trips && f.journey && f.offlinePack)
        assertEquals("invite", full.auth?.signupMode)
        assertTrue(full.auth?.signupAvailable == true)
        assertEquals(10, full.auth?.minPasswordLength)
        val legacy = decode("config_legacy", ClientConfigDto.serializer())
        assertNull(legacy.features)
        assertNull(legacy.auth)
        assertEquals(false, legacy.telemetry?.enabled)
    }
}
