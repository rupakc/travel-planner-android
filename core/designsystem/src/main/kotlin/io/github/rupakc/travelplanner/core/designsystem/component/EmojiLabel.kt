package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * [label] without its leading decorative emoji, for TalkBack: "🍜 Food" → "Food", "✈️ 3h layover" → "3h layover".
 * Symbols that carry meaning stay ("★ 4.5", "✓ Visa-free"), and a label that is only emoji is kept as is.
 */
fun emojiFreeLabel(label: String): String {
    var i = 0
    while (i < label.length) {
        val cp = label.codePointAt(i)
        if (!isDecorative(cp)) break
        i += Character.charCount(cp)
    }
    if (i == 0) return label
    val rest = label.substring(i).trimStart()
    return rest.ifEmpty { label }
}

/** Has TalkBack read [text] without its leading decorative emoji (the visible text is unchanged). No-op when there is none. */
fun Modifier.readsWithoutEmoji(text: String): Modifier {
    val spoken = emojiFreeLabel(text)
    return if (spoken == text) this else semantics { contentDescription = spoken }
}

private fun isDecorative(cp: Int): Boolean =
    when {
        cp == STAR || cp == CHECK -> false
        cp >= PICTOGRAPHS_START -> true
        cp in MISC_SYMBOLS -> true
        cp == ZWJ || cp == VS16 || cp == KEYCAP -> true
        else -> false
    }

private const val STAR = 0x2605
private const val CHECK = 0x2713
private const val PICTOGRAPHS_START = 0x1F000
private val MISC_SYMBOLS = 0x2600..0x27BF
private const val ZWJ = 0x200D
private const val VS16 = 0xFE0F
private const val KEYCAP = 0x20E3
