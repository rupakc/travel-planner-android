package io.github.rupakc.travelplanner.feature.settings.root

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LanguageOptionsTest {
    @Test
    fun `language dialog lists 8 options with Deutsch and বাংলা`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(8, SupportedLanguageTags.size)
        val labels = SupportedLanguageTags.map { context.getString(languageLabelRes(it)) }
        assertTrue(labels.toString(), labels.containsAll(listOf("Deutsch", "Français", "हिन्दी", "中文（简体）", "বাংলা")))
        assertEquals(8, labels.toSet().size)
    }
}
