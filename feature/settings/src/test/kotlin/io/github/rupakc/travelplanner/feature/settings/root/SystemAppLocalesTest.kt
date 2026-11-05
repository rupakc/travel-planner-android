package io.github.rupakc.travelplanner.feature.settings.root

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Settings → Language works below API 33 through AppCompat (wiring item 9). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [30])
class SystemAppLocalesTest {
    private val locales = SystemAppLocales()

    @After
    fun tearDown() = locales.apply(null)

    @Test
    fun `per-app language is offered and applied on API 30`() {
        assertTrue(locales.perAppSupported)
        locales.apply("es")
        assertEquals("es", locales.current())
        locales.apply(null)
        assertNull(locales.current())
    }
}
