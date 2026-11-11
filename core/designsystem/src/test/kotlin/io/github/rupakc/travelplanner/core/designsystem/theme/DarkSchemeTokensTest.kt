package io.github.rupakc.travelplanner.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkSchemeTokensTest {
    @Test fun `dark surface is the web dark page surface`() = assertEquals(DarkPalette.White, DarkColors.surface)

    @Test fun `background is dark gray-50`() = assertEquals(DarkPalette.Gray50, DarkColors.background)

    @Test fun `onSurface is dark gray-900`() = assertEquals(DarkPalette.Gray900, DarkColors.onSurface)
}
