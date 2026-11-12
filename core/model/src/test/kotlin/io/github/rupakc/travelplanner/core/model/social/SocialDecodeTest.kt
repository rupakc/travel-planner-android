package io.github.rupakc.travelplanner.core.model.social

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The examples of docs/Social-API.md parse; unknown keys and enum values are tolerated; optional profile fields stay null. */
class SocialDecodeTest {
    private fun <T> decode(s: kotlinx.serialization.KSerializer<T>, json: String): T = TravelJson.decodeFromString(s, json)

    @Test
    fun `own profile without a profile has the private defaults`() {
        val p =
            decode(
                OwnProfile.serializer(),
                """{"handle":null,"visibility":"private","bio":"","show_avatar":false,"leaderboard_opt_in":false,"home_country":null,"created_at":null}""",
            )
        assertFalse(p.hasProfile)
        assertEquals(ProfileVisibility.PRIVATE, p.visibilityMode)
        assertFalse(p.showCountries)
    }

    @Test
    fun `own profile with opt-ins and a new visibility value`() {
        val p =
            decode(
                OwnProfile.serializer(),
                """{"handle":"ana_k","visibility":"circle","bio":"hi","show_level":true,"leaderboard_opt_in":true,"home_country":"PT",
                "created_at":"2026-11-01T00:00:00Z","future":1}""",
            )
        assertTrue(p.hasProfile)
        assertEquals(ProfileVisibility.UNKNOWN, p.visibilityMode)
        assertTrue(p.showLevel && p.leaderboardOptIn)
        assertEquals("PT", p.homeCountry)
    }

    @Test
    fun `profile update omits unset fields`() {
        val json = TravelJson.encodeToString(ProfileUpdate.serializer(), ProfileUpdate(visibility = "public", showLevel = true))
        assertEquals("""{"visibility":"public","show_level":true}""", json)
    }

    @Test
    fun `public profile keeps absent opt-in fields null and counts may be strings`() {
        val p =
            decode(
                PublicProfile.serializer(),
                """{"handle":"ana_k","bio":"x","ideas_count":"3","followers_count":2.0,"following_count":1,
            "you":{"following":false,"pending":true,"follows_you":true,"blocked":false}}""",
            )
        assertNull(p.avatarUrl)
        assertNull(p.countries)
        assertNull(p.level)
        assertNull(p.badges)
        assertEquals(3, p.ideasCount)
        assertEquals(2, p.followersCount)
        assertTrue(p.you.pending && p.you.followsYou)
    }

    @Test
    fun `public profile with every optional field`() {
        val p =
            decode(
                PublicProfile.serializer(),
                """{"handle":"h","avatar_url":"https://x/a.png","home_country":"JP","countries":["JP","PT"],"level":4,
            "badges":[{"id":"inspirer","tier":"gold"},{"id":"muse","tier":2}],"ideas_count":0,"followers_count":0,"following_count":0}""",
            )
        assertEquals(listOf("JP", "PT"), p.countries)
        assertEquals(4, p.level)
        assertEquals("2", p.badges!![1].tier)
    }

    @Test
    fun `handle check, follow result and lists`() {
        assertEquals(HandleReason.RESERVED, decode(HandleCheck.serializer(), """{"available":false,"reason":"reserved"}""").reasonCode)
        assertEquals(HandleReason.UNKNOWN, decode(HandleCheck.serializer(), """{"available":false,"reason":"zzz"}""").reasonCode)
        assertNull(decode(HandleCheck.serializer(), """{"available":true}""").reasonCode)
        assertEquals(FollowState.PENDING, decode(FollowResult.serializer(), """{"state":"pending"}""").followState)
        assertEquals(FollowState.UNKNOWN, decode(FollowResult.serializer(), """{"state":"muted"}""").followState)
        assertEquals("a", decode(FollowRequests.serializer(), """{"requests":[{"handle":"a","created_at":"t"}]}""").requests.single().handle)
        val page = decode(HandlePage.serializer(), """{"users":[{"handle":"a"},{"handle":"b"}],"next":"c2"}""")
        assertEquals(2, page.users.size)
        assertEquals("c2", page.next)
        assertNull(decode(HandlePage.serializer(), """{"users":[],"next":null}""").next)
        assertEquals("z", decode(BlockList.serializer(), """{"users":[{"handle":"z"}]}""").users.single().handle)
    }

    @Test
    fun `idea of the feed with its snapshot`() {
        val page =
            decode(
                IdeaPage.serializer(),
                """{"ideas":[{"id":7,"kind":"plan","title":"Lisbon food","caption":"c","author":{"handle":"ana_k","avatar_url":"https://x/a.png"},
            "snapshot":{"v":1,"kind":"plan","destinations":["Lisbon"],"days":4,"interests":["food"],"stats":{"flights":1},
            "items":{"activities":[{"name":"Time Out Market"}]}},"likes":3,"saves":"1","comments":0,"liked":true,"saved":false,"mine":false,
            "status":"held","created_at":"2026-11-10T10:00:00Z","new_field":true}],"next":"o20"}""",
            )
        val i = page.ideas.single()
        assertEquals(7L, i.id)
        assertEquals(IdeaKind.PLAN, i.ideaKind)
        assertEquals(ContentStatus.HELD, i.contentStatus)
        assertEquals("Time Out Market", i.snapshot.items.getValue("activities").single().name)
        assertEquals(listOf("Lisbon"), i.snapshot.destinations)
        assertEquals(1, i.saves)
        assertEquals("o20", page.next)
    }

    @Test
    fun `status defaults to published and unknown kinds are tolerated`() {
        val i = decode(Idea.serializer(), """{"id":1,"kind":"video","title":"t","author":{"handle":"a"},"snapshot":{}}""")
        assertEquals(ContentStatus.PUBLISHED, i.contentStatus)
        assertEquals(IdeaKind.UNKNOWN, i.ideaKind)
        assertEquals(ContentStatus.UNKNOWN, ContentStatus.from("quarantined"))
    }

    @Test
    fun `comments and save result`() {
        val c =
            decode(
                CommentPage.serializer(),
                """{"comments":[{"id":4,"author":{"handle":"b"},"text":"nice","helpful":true,"mine":false,
                "status":"published","created_at":"t"}],"next":null}""",
            )
        assertTrue(c.comments.single().helpful)
        assertEquals(4L, c.comments.single().id)
        assertEquals(9L, decode(SaveIdeaResult.serializer(), """{"plan_id":9}""").planId)
    }

    @Test
    fun `request bodies match the contract`() {
        assertEquals(
            """{"plan_id":3,"kind":"plan","title":"T","caption":"C","locale":"es"}""",
            TravelJson.encodeToString(PublishIdeaRequest.serializer(), PublishIdeaRequest(3, "plan", "T", "C", "es")),
        )
        assertEquals(
            """{"target":"idea","id":"7","reason":"spam"}""",
            TravelJson.encodeToString(ReportRequest.serializer(), ReportRequest("idea", "7", "spam")),
        )
    }

    @Test
    fun `error codes map and unknown falls back`() {
        assertEquals(SocialErrorCode.HANDLE_TAKEN, AppError.Client(409, "x", "handle_taken").socialErrorCode())
        assertEquals(SocialErrorCode.PROFILE_REQUIRED, AppError.Forbidden("x", "profile_required").socialErrorCode())
        assertEquals(SocialErrorCode.PROFILE_NOT_FOUND, AppError.NotFound("x", "profile_not_found").socialErrorCode())
        assertEquals(SocialErrorCode.SOCIAL_DISABLED, AppError.NotFound("x", "social_disabled").socialErrorCode())
        assertEquals(SocialErrorCode.GAME_HIDDEN, AppError.Forbidden("x", "game_hidden").socialErrorCode())
        assertEquals(SocialErrorCode.ALREADY_DONE, AppError.Client(409, "x", "already_done").socialErrorCode())
        assertEquals(SocialErrorCode.LIMIT_REACHED, AppError.Client(422, "x", "limit_reached").socialErrorCode())
        assertEquals(SocialErrorCode.UNKNOWN, AppError.Client(422, "x", "brand_new").socialErrorCode())
        assertEquals(SocialErrorCode.UNKNOWN, AppError.Network(null).socialErrorCode())
        assertEquals(SocialErrorCode.ALREADY_DONE, SocialErrorCode.from("already_done"))
    }

    @Test
    fun `retry after of a handle rate limit`() {
        assertEquals(86400L, AppError.Client(422, "x", "handle_rate_limited", extras = mapOf("retry_after" to "86400")).retryAfterSeconds())
        assertNull(AppError.Client(422, "x", "handle_rate_limited").retryAfterSeconds())
        assertEquals(30L, AppError.RateLimited("x", 30L).retryAfterSeconds())
    }
}
