package io.github.rupakc.travelplanner.feature.social.feed

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.social.Idea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaLogicTest {
    @Test
    fun `like and save adjust the counts and never go below zero`() {
        val idea = Idea(id = 1, likes = 0, saves = 1)
        assertEquals(1, idea.withLiked(true).likes)
        assertTrue(idea.withLiked(true).liked)
        assertEquals(0, idea.withLiked(false).likes)
        assertEquals(0, idea.copy(saved = true).withSaved(false).saves)
        assertEquals(2, idea.withSaved(true).saves)
        assertEquals(idea, idea.withLiked(false))
    }

    @Test
    fun `merging pages never shows an idea twice`() {
        val merged = mergeIdeas(listOf(Idea(id = 1), Idea(id = 2)), listOf(Idea(id = 2), Idea(id = 3)))
        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
    }

    @Test
    fun `status notice is for anything but published`() {
        assertFalse(Idea(id = 1, status = "published").needsReviewNotice())
        assertFalse(Idea(id = 1, status = null).needsReviewNotice())
        assertTrue(Idea(id = 1, status = "held").needsReviewNotice())
    }

    @Test
    fun `publish refusals map to guiding problems`() {
        fun problem(code: String) = AppError.Forbidden("x", code).toPublishProblem()
        assertEquals(PublishProblem.ProfileRequired, problem("profile_required"))
        assertEquals(PublishProblem.EmailUnverified, problem("email_unverified"))
        assertEquals(PublishProblem.LimitReached, AppError.Client(422, "x", "limit_reached").toPublishProblem())
        assertEquals(PublishProblem.ContentRejected, AppError.Client(422, "x", "content_rejected").toPublishProblem())
        assertNull(AppError.Network(null).toPublishProblem())
    }

    @Test
    fun `unavailable ideas look the same whatever the reason`() {
        assertTrue(AppError.NotFound("x", "idea_not_found").isGone())
        assertTrue(AppError.NotFound("x", "idea_unavailable").isGone())
        assertFalse(AppError.Network(null).isGone())
    }
}
