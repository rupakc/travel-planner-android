package io.github.rupakc.travelplanner.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiLabelTest {
    @Test fun stripsLeadingDecorativeEmoji() {
        assertEquals("Gastronomía", emojiFreeLabel("🍜 Gastronomía"))
        assertEquals("Hospitals", emojiFreeLabel("🏥 Hospitals"))
        assertEquals("Check weather", emojiFreeLabel("⚠️ Check weather"))
        assertEquals("3h layover in Doha", emojiFreeLabel("✈️ 3h layover in Doha"))
        assertEquals("Family", emojiFreeLabel("👨‍👩‍👧 Family"))
        assertEquals("+34 600", emojiFreeLabel("📞 +34 600"))
        assertEquals("Hearing impairment", emojiFreeLabel("🦻 Hearing impairment"))
    }

    @Test fun keepsMeaningfulSymbolsAndPlainText() {
        assertEquals("★ 4.5", emojiFreeLabel("★ 4.5"))
        assertEquals("✓ Visa-free", emojiFreeLabel("✓ Visa-free"))
        assertEquals("Food 🍜", emojiFreeLabel("Food 🍜"))
        assertEquals("🍜", emojiFreeLabel("🍜"))
        assertEquals("", emojiFreeLabel(""))
    }
}
