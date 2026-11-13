package io.github.rupakc.travelplanner.core.model.share

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The examples of docs/Sharing-API.md parse; unknown kinds, dates and keys are tolerated. */
class SharesDecodeTest {
    @Test
    fun `create answer of the contract`() {
        val s =
            TravelJson.decodeFromString(
                CreatedShare.serializer(),
                """{"token":"abc","kind":"plan","url":"https://host/share/abc","plan_id":12,
                "card_url":{"link":"https://host/api/share/abc/card.png?size=link","story":"https://host/api/share/abc/card.png?size=story"},
                "options":{"dates":"month","show_origin":true,"show_name":false},"created_at":"2026-11-10T00:00:00Z","extra":1}""",
            )
        assertEquals("abc", s.token)
        assertEquals(ShareKind.PLAN, s.shareKind)
        assertEquals(12L, s.planId)
        assertEquals(ShareDates.MONTH, s.options.datesMode)
        assertTrue(s.options.showOrigin)
        assertFalse(s.options.showName)
        assertTrue(s.cardUrl.story!!.endsWith("size=story"))
    }

    @Test
    fun `options default to the private ones and a bad dates value is hidden`() {
        val s = TravelJson.decodeFromString(CreatedShare.serializer(), """{"token":"t","url":"u","options":{"dates":"weird"}}""")
        assertEquals(ShareDates.HIDDEN, s.options.datesMode)
        assertEquals(ShareOptions.PRIVATE, TravelJson.decodeFromString(CreatedShare.serializer(), """{"token":"t","url":"u"}""").options)
        assertNull(s.cardUrl.link)
    }

    @Test
    fun `unknown kind is a plain link`() {
        assertEquals(ShareKind.QUIZ, ShareKind.from("quiz"))
        assertEquals(ShareKind.STATUS, ShareKind.from("status"))
        assertEquals(ShareKind.UNKNOWN, ShareKind.from("postcard"))
        assertEquals(ShareKind.UNKNOWN, ShareKind.from(null))
        assertEquals(ShareKind.SAVINGS, ShareKind.from("savings"))
        assertTrue(ShareKind.RECAP.needsPlan)
        assertFalse(ShareKind.SAVINGS.needsPlan)
    }

    @Test
    fun `list with legacy shares and an older backend without them`() {
        val l =
            TravelJson.decodeFromString(
                SharesList.serializer(),
                """{"shares":[{"token":"a","kind":"savings","title":"Price alerts","url":"u","plan_id":null,"options":{}}],
                "legacy_plan_shares":[{"plan_id":3,"name":"Lisbon","url":"https://h/share/zz"}]}""",
            )
        assertEquals(ShareKind.SAVINGS, l.shares.single().shareKind)
        assertNull(l.shares.single().planId)
        assertEquals(3L, l.legacyPlanShares.single().planId)
        assertTrue(TravelJson.decodeFromString(SharesList.serializer(), """{"shares":[]}""").legacyPlanShares.isEmpty())
    }

    @Test
    fun `request body carries the private defaults explicitly and omits null plan`() {
        val body = TravelJson.encodeToString(CreateShareRequest.serializer(), CreateShareRequest("savings", null, ShareOptions.PRIVATE, "de"))
        assertEquals("""{"kind":"savings","options":{"dates":"hidden","show_origin":false,"show_name":false},"locale":"de"}""", body)
    }

    @Test
    fun `error codes`() {
        assertEquals(ShareErrorCode.PLAN_NOT_FOUND, AppError.NotFound("x", "plan_not_found").shareErrorCode())
        assertEquals(ShareErrorCode.SHARING_DISABLED, AppError.NotFound("x", "sharing_disabled").shareErrorCode())
        assertEquals(ShareErrorCode.INVALID_KIND, AppError.Client(422, "x", "invalid_kind").shareErrorCode())
        assertEquals(ShareErrorCode.UNKNOWN, AppError.RateLimited("slow", 5).shareErrorCode())
        assertEquals(ShareErrorCode.UNKNOWN, AppError.Network(null).shareErrorCode())
    }
}
