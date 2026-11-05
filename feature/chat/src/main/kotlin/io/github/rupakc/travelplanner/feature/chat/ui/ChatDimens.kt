package io.github.rupakc.travelplanner.feature.chat.ui

import androidx.compose.ui.unit.dp

/** Chat layout constants (the only dp values defined in this module; spacing comes from TravelTheme). */
internal object ChatDimens {
    /** Width from which threads and conversation sit side by side (Material expanded width class). */
    val ExpandedWidth = 840.dp
    val ThreadsPaneWidth = 320.dp
    val MaxConversationWidth = 760.dp
    val Avatar = 32.dp
    val AvatarIcon = 18.dp
    val SmallIcon = 16.dp
    val BubbleCorner = 20.dp
    val BubbleTail = 6.dp
    val StatusDot = 8.dp
    val RouteSpine = 2.dp
    val RouteNode = 12.dp
    val ProgressHeight = 4.dp
    val ComposerMaxHeight = 160.dp
    val StarterMaxWidth = 560.dp
    val EmptyLogo = 56.dp
    val EmptyLogoIcon = 28.dp

    /** Size of the invisible live-region node. */
    val A11yNode = 1.dp
    const val BUBBLE_MAX_FRACTION = 0.85f
    const val SECTION_LIMIT = 5
    const val LIST_SECTION_LIMIT = 8
    const val TIP_PREVIEW_CHARS = 120
    const val VISA_MAX_REQUIREMENTS = 5
    const val FOREX_MAX_LOCATIONS = 4
    const val INTERCITY_MAX_OPTIONS = 2
}
