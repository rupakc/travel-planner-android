package io.github.rupakc.travelplanner.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spec §5: 24 dp large cards, 12 dp item cards, 8 dp inputs, pills fully rounded. */
internal val TravelShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Named shapes for components (use these, not raw dp). */
object TravelShape {
    val Input = RoundedCornerShape(8.dp)
    val ItemCard = RoundedCornerShape(12.dp)
    val LargeCard = RoundedCornerShape(24.dp)
    val Pill = CircleShape
}

/** Spacing scale (web px-4 gutters, gap-2/3/4, space-y-5/6). */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Page side gutter. */
    val gutter: Dp = 16.dp,
    /** Minimum touch target (a11y). */
    val minTouchTarget: Dp = 48.dp,
    /** Section accent bar width (web border-l-4). */
    val accentBar: Dp = 4.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
