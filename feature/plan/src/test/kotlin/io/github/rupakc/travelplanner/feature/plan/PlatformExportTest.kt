package io.github.rupakc.travelplanner.feature.plan

import android.app.Application
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.domain.export.PlanCalendarEvent
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.feature.plan.export.PlatformExport
import io.github.rupakc.travelplanner.feature.plan.export.ShareTextInput
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlatformExportTest {
    @Test
    fun `timed events use local time, all-day events UTC midnights`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val timed = PlanCalendarEvent("slot-1", "Market", "Tsukiji", "Go early", false, LocalDateTime.of(2027, 4, 1, 9, 0), LocalDateTime.of(2027, 4, 1, 11, 0))
        val intent = PlatformExport.calendarInsert(timed, zone)
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(ZonedDateTime.of(2027, 4, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals("Market", intent.getStringExtra(CalendarContract.Events.TITLE))
        val allDay = PlanCalendarEvent("hotel", "H", null, null, true, LocalDateTime.of(2027, 4, 1, 0, 0), LocalDateTime.of(2027, 4, 4, 0, 0))
        val i2 = PlatformExport.calendarInsert(allDay, zone)
        assertTrue(i2.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals(1_806_537_600_000L, i2.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
    }

    @Test
    fun `share text uses the module strings`() {
        val text =
            PlatformExport.buildText(
                ApplicationProvider.getApplicationContext(),
                ShareTextInput("Japan", "NYC", "Tokyo", LocalDate.of(2027, 4, 1), null, 2, PlanSelections(hotel = Hotel(name = "H")), emptyList(), 0.0),
            )
        assertTrue(text, text.startsWith("Japan\nTokyo from NYC\n"))
        assertTrue(text, text.contains("2 travelers"))
        assertTrue(text, text.contains("Hotel\n  • H"))
    }
}
