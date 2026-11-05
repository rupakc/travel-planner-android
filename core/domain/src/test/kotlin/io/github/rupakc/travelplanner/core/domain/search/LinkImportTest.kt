package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreviewSource
import io.github.rupakc.travelplanner.core.model.importlink.PreviewDestination
import io.github.rupakc.travelplanner.core.model.importlink.PreviewPlace
import io.github.rupakc.travelplanner.core.model.search.ImportSource
import io.github.rupakc.travelplanner.core.model.search.MustIncludePlace
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Port of frontend/src/utils/linkImport.test.js (the parts that live in core:domain). */
class LinkImportTest {
    private val preview =
        ImportPreview(
            source = ImportPreviewSource(kind = "url", url = "https://wander.example/lisbon", title = "48 Hours in Lisbon | Wander", siteName = "Wander"),
            title = "48 hours in Lisbon",
            summary = "Viewpoints and pastries.",
            destinations =
                listOf(
                    PreviewDestination("Lisbon, Portugal", "Lisbon", "Portugal", listOf("LIS"), 38.7, -9.1, 3, 3),
                    PreviewDestination("Porto, Portugal", "Porto", "Portugal", listOf("OPO"), 41.1, -8.6, 1, 1),
                ),
            places =
                listOf(
                    PreviewPlace(
                        id = "p1",
                        name = "Time Out Market",
                        kind = "food",
                        city = "Lisbon",
                        destination = "Lisbon, Portugal",
                        lat = 38.707,
                        lng = -9.146,
                        approximate = false,
                        verified = true,
                        note = "Lunch",
                        quote = "start here",
                    ),
                    PreviewPlace("p2", "Belém Tower", "sight", "Lisbon", destination = "Lisbon, Portugal", lat = 38.7, lng = -9.1, approximate = true),
                    PreviewPlace("p3", "Pena Palace", "sight", "Sintra", destination = "Lisbon, Portugal", lat = 38.8, lng = -9.4, approximate = true),
                    PreviewPlace("p4", "Livraria Lello", "shopping", "Porto", destination = "Porto, Portugal", lat = 41.1, lng = -8.6, approximate = true),
                ),
            unresolved = listOf(PreviewPlace("p5", "Mystery Bar", "drink", "Xqz")),
            suggestedDays = 4,
            interests = listOf("food", "viewpoints"),
        )

    @Test
    fun `reads links`() {
        mapOf(
            "https://blog.example/trip" to "https://blog.example/trip",
            "  http://x.example/a?b=1  " to "http://x.example/a?b=1",
            "www.lonelyplanet.com/portugal/lisbon" to "https://www.lonelyplanet.com/portugal/lisbon",
            "youtu.be/abc123" to "https://youtu.be/abc123",
        ).forEach { (raw, url) -> assertEquals(raw, ImportInput.Url(url), detectImportInput(raw)) }
    }

    @Test
    fun `reads text`() {
        listOf("Belém Tower and LX Factory", "Lisbon", "Day 1: Colosseum.\nDay 2: Vatican", "ftp://x.example/file").forEach {
            assertEquals(it, ImportInput.Text, detectImportInput(it))
        }
    }

    @Test
    fun `treats blank input as empty`() {
        assertEquals(ImportInput.Empty, detectImportInput("   "))
    }

    @Test
    fun `offers text and screenshots for a blocked source`() {
        val v = importErrorView(AppError.Client(422, "Instagram doesn't let apps read posts.", code = "blocked_source"))
        assertEquals("blocked_source", v.code)
        assertEquals("Instagram doesn't let apps read posts.", v.message)
        assertEquals(listOf(ImportAction.TEXT, ImportAction.IMAGES), v.actions)
    }

    @Test
    fun `suggests text or screenshots for blocked sites, paywalls and no places`() {
        assertEquals(listOf(ImportAction.TEXT, ImportAction.IMAGES), importErrorView(AppError.Client(422, "m", code = "blocked_by_site")).actions)
        assertEquals(listOf(ImportAction.TEXT, ImportAction.IMAGES), importErrorView(AppError.Client(422, "m", code = "paywall")).actions)
        assertEquals(listOf(ImportAction.TEXT), importErrorView(AppError.Client(422, "m", code = "no_places")).actions)
        assertEquals(listOf(ImportAction.RETRY), importErrorView(AppError.Server(502, "m", code = "extract_failed")).actions)
    }

    @Test
    fun `shows the rate-limit detail as-is`() {
        val v = importErrorView(AppError.RateLimited("Too many requests — please wait 30 seconds and try again.", 30))
        assertEquals("rate_limited", v.code)
        assertEquals("Too many requests — please wait 30 seconds and try again.", v.message)
        assertEquals(emptyList<ImportAction>(), v.actions)
    }

    @Test
    fun `shows a too-big upload detail as-is, and explains one without detail`() {
        val v = importErrorView(AppError.Client(413, "That upload is too big — use up to 4 screenshots of 5 MB or less."))
        assertEquals("too_large", v.code)
        assertEquals("That upload is too big — use up to 4 screenshots of 5 MB or less.", v.message)
        assertEquals(listOf(ImportAction.IMAGES), v.actions)
        assertNull(importErrorView(AppError.Client(413, null)).message)
    }

    @Test
    fun `explains network errors and unknown failures`() {
        assertEquals("network", importErrorView(AppError.Network()).code)
        assertEquals("unknown", importErrorView(AppError.Server(500, null)).code)
        assertEquals(listOf(ImportAction.RETRY), importErrorView(AppError.Server(500, null)).actions)
    }

    @Test
    fun `accepts the supported image types`() {
        listOf("image/png", "image/jpeg", "image/webp", "image/gif").forEach { assertNull(checkImageFile(it, "a", 1000)) }
    }

    @Test
    fun `explains HEIC, other files and big images`() {
        assertEquals(ImageProblem.HEIC, checkImageFile("", "IMG_1.HEIC", 1000))
        assertEquals(ImageProblem.HEIC, checkImageFile("image/heif", "a.heif", 1000))
        assertEquals(ImageProblem.UNSUPPORTED_TYPE, checkImageFile("application/pdf", "a.pdf", 1000))
        assertEquals(ImageProblem.TOO_BIG, checkImageFile("image/png", "a.png", 6L * 1024 * 1024))
        assertNull(checkImageFile("image/png", "a.png", 6L * 1024 * 1024, beforeShrink = true))
        assertEquals(ImageProblem.TOO_BIG_TO_READ, checkImageFile("image/png", "a.png", 41L * 1024 * 1024, beforeShrink = true))
    }

    @Test
    fun `starts 30 days out and lasts the suggested nights`() {
        assertEquals("2026-12-04" to "2026-12-08", defaultImportDates(LocalDate.parse("2026-11-04"), 4))
    }

    @Test
    fun `leaves hotels and anything past the 25th place unticked`() {
        val many = List(28) { i -> PreviewPlace("x$i", "Spot $i", if (i == 0) "stay" else "sight", destination = "Lisbon, Portugal") }
        val d = previewToDraft(preview.copy(places = many))
        assertFalse(d.places[0].selected)
        assertEquals(ImportLimits.MAX_MUST_INCLUDE, d.places.count { it.selected })
        assertFalse(d.places.last().selected)
    }

    @Test
    fun `selects every placed spot and keeps unresolved ones aside`() {
        val d = previewToDraft(preview)
        assertEquals(listOf("p1" to true, "p2" to true, "p3" to true, "p4" to true), d.places.map { it.id to it.selected })
        assertEquals(listOf("p5"), d.unresolved.map { it.id })
        assertEquals(listOf(Triple("Lisbon", 3, true), Triple("Porto", 1, true)), d.stops.map { Triple(it.destination.city, it.nights, it.included) })
    }

    private val form =
        SearchForm(
            origin = "London",
            nationality = "British",
            interests = listOf("history"),
            travellers = TravellerCounts(adults = 2),
            budgetUsd = "2000",
            departureDate = "2026-12-04",
        )

    @Test
    fun `builds a multi-city search that must include the chosen places`() {
        var draft = previewToDraft(preview)
        draft =
            draft.copy(
                places = draft.places.map { if (it.id == "p2") it.copy(selected = false) else it },
                unresolved = draft.unresolved.map { it.copy(place = it.place.copy(destination = "Porto, Portugal")) },
            )
        val built = buildImportSearch(form, preview, draft)
        assertEquals("Porto, Portugal", built.form.destination)
        assertEquals(listOf(ExtraStop("Lisbon, Portugal", "3")), built.stops)
        assertEquals("2026-12-04", built.form.departureDate)
        assertEquals("2026-12-08", built.form.returnDate)
        assertEquals(listOf("history", "food", "viewpoints"), built.form.interests)
        val must = built.form.mustInclude.orEmpty()
        assertEquals(listOf("Time Out Market", "Pena Palace", "Livraria Lello", "Mystery Bar"), must.map { it.name })
        assertEquals(MustIncludePlace("Time Out Market", "food", "Lisbon", null, 38.707, -9.146, "Lunch", "start here", true), must[0])
        assertEquals("Sintra", must[1].town)
        assertNull(must[1].lat) // approximate pins never pretend to be exact
        assertEquals("Porto", must[3].city)
        assertEquals(ImportSource("https://wander.example/lisbon", "48 hours in Lisbon"), built.form.importSource)
    }

    @Test
    fun `drops excluded stops and their places, and caps the list`() {
        val manyPlaces = List(30) { i -> PreviewPlace("x$i", "Spot $i", "sight", "Lisbon", destination = "Lisbon, Portugal", approximate = true) }
        var draft = previewToDraft(preview.copy(places = manyPlaces))
        draft = draft.copy(stops = draft.stops.mapIndexed { i, s -> if (i == 1) s.copy(included = false) else s })
        val built = buildImportSearch(form, preview, draft)
        assertEquals("Lisbon, Portugal", built.form.destination)
        assertEquals(emptyList<ExtraStop>(), built.stops)
        assertEquals(ImportLimits.MAX_MUST_INCLUDE, built.form.mustInclude?.size)
        assertEquals("2026-12-07", built.form.returnDate)
    }

    @Test
    fun `has no source URL for pasted text`() {
        val text = preview.copy(source = ImportPreviewSource(kind = "text"))
        assertEquals(ImportSource(null, "48 hours in Lisbon"), buildImportSearch(form, text, previewToDraft(text)).form.importSource)
    }

    @Test
    fun `fits the long edge within 1568 px, keeping the aspect ratio`() {
        assertEquals(1568, ImportLimits.MAX_IMAGE_EDGE)
        assertEquals(725 to 1568, fitWithin(1170, 2532))
        assertEquals(1568 to 523, fitWithin(3000, 1000))
        assertEquals(800 to 600, fitWithin(800, 600))
    }

    @Test
    fun `plan issues follow the web order`() {
        val draft = previewToDraft(preview)
        val today = LocalDate.parse("2026-11-04")
        val built = buildImportSearch(form, preview, draft)
        assertNull(importPlanIssue(form, built, draft, today))
        assertEquals(ImportPlanIssue.ORIGIN_REQUIRED, importPlanIssue(form.copy(origin = " "), built, draft, today))
        assertEquals(ImportPlanIssue.NATIONALITY_REQUIRED, importPlanIssue(form.copy(nationality = ""), built, draft, today))
        val none = draft.copy(places = draft.places.map { it.copy(selected = false) })
        assertEquals(ImportPlanIssue.NO_PLACES, importPlanIssue(form, buildImportSearch(form, preview, none), none, today))
        assertEquals(ImportPlanIssue.DEPARTURE_IN_PAST, importPlanIssue(form, built, draft, LocalDate.parse("2026-12-05")))
    }
}
