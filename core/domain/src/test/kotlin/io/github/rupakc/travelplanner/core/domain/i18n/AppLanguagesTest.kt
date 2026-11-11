package io.github.rupakc.travelplanner.core.domain.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguagesTest {
    @Test
    fun `app de wins over system fr`() = assertEquals("de", AppLanguages.resolve(listOf("de"), listOf("fr")))

    @Test
    fun `system ja then fr resolves to fr`() = assertEquals("fr", AppLanguages.resolve(emptyList(), listOf("ja-JP", "fr-CA")))

    @Test
    fun `zh-TW resolves to zh`() {
        assertEquals("zh", AppLanguages.resolve(emptyList(), listOf("zh-TW")))
        assertEquals("zh", AppLanguages.resolve(listOf("zh-Hant-TW"), emptyList()))
    }

    @Test
    fun `empty resolves to en`() {
        assertEquals("en", AppLanguages.resolve(emptyList(), emptyList()))
        assertEquals("en", AppLanguages.resolve(listOf(""), listOf("ja")))
    }

    @Test
    fun `intlTag zh is zh-CN and de is de`() {
        assertEquals("zh-CN", AppLanguages.intlTag("zh"))
        assertEquals("hi-IN", AppLanguages.intlTag("hi"))
        assertEquals("bn-IN", AppLanguages.intlTag("bn"))
        assertEquals("de", AppLanguages.intlTag("de"))
    }
}
