package io.github.rupakc.travelplanner.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.domain.format.formatWholeUsd
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale

/**
 * [formatPrice] in the app language: "$1,480" in English, "1480 US$" / "1.480 US$" in Spanish.
 * Use this on screen; exports (share text, .ics) keep the en-US default on purpose.
 */
@Composable
@ReadOnlyComposable
fun localPrice(amount: Double?, currency: String? = "USD", cents: Boolean = false): String = formatPrice(amount, currency, cents, currentLocale())

/** Whole US dollars in the app language (web `formatUsd`): "$1,234" / "1234 US$". */
@Composable
@ReadOnlyComposable
fun localUsd(amount: Double): String = formatWholeUsd(amount, currentLocale())
