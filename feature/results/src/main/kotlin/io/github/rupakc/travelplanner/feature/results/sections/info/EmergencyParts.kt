package io.github.rupakc.travelplanner.feature.results.sections.info

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.Embassy
import io.github.rupakc.travelplanner.core.model.section.EmergencyCardData
import io.github.rupakc.travelplanner.core.model.section.EmergencyCity
import io.github.rupakc.travelplanner.core.model.section.EmergencyNumbers
import io.github.rupakc.travelplanner.core.model.section.Hospital
import io.github.rupakc.travelplanner.core.model.section.LocalLaw
import io.github.rupakc.travelplanner.core.model.section.LocalPhrase
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.R

/** One city's card (flat single-city data uses the same shape). */
internal data class EmergencyCardModel(
    val city: String?,
    val numbers: EmergencyNumbers?,
    val embassy: Embassy?,
    val hospitals: List<Hospital>,
    val phrases: List<LocalPhrase>,
    val laws: List<LocalLaw>,
    val homeCountryNote: String?,
)

/** Multi-city trips carry `cities`; otherwise the flat card (web EmergencyCardSection). */
internal fun emergencyCards(data: EmergencyCardData): List<EmergencyCardModel> =
    data.cities.takeIf { it.isNotEmpty() }?.map { it.toModel() }
        ?: listOf(EmergencyCardModel(null, data.emergencyNumbers, data.embassy, data.hospitals, data.localPhrases, data.localLaws, data.homeCountryNote))

private fun EmergencyCity.toModel() = EmergencyCardModel(city, emergencyNumbers, embassy, hospitals, localPhrases, localLaws, homeCountryNote)

private val DIALABLE = Regex("[^0-9+*#]")

/** `tel:` digits of a listed number ("112 / 911" → first number), or null when there is nothing to dial. */
internal fun dialableNumber(value: String?): String? {
    val first = value.orEmpty().split('/', ',', ';', '|').firstOrNull().orEmpty()
    return DIALABLE.replace(first, "").takeIf { it.any(Char::isDigit) }
}

/** Opens the dialer pre-filled (never calls directly: no CALL_PHONE permission). */
internal fun dial(context: Context, number: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

/** Plain-text card for sharing / saving offline (Android stand-in for the web's print window). */
internal data class EmergencyLabels(
    val title: String,
    val police: String,
    val ambulance: String,
    val fire: String,
    val touristPolice: String,
    val hospitals: String,
    val phrases: String,
    val laws: String,
    val disclaimer: String,
    val footer: String,
)

internal fun emergencyShareText(cards: List<EmergencyCardModel>, labels: EmergencyLabels): String =
    buildString {
        appendLine("🚨 ${labels.title}")
        appendLine(labels.disclaimer)
        cards.forEach { card ->
            appendLine()
            card.city?.let { appendLine("📍 $it") }
            val n = card.numbers
            listOf(labels.police to n?.police, labels.ambulance to n?.ambulance, labels.fire to n?.fire, labels.touristPolice to n?.touristPolice)
                .forEach { (label, value) -> appendLine("$label: ${value?.takeIf { it.isNotBlank() } ?: "—"}") }
            card.embassy?.let { e ->
                appendLine()
                listOfNotNull(e.name, e.address, e.phone?.let { "📞 $it" }, e.emergencyAfterHours?.let { "🌙 $it" }, e.website).forEach { appendLine(it) }
            }
            if (card.embassy == null) card.homeCountryNote?.let { appendLine(it) }
            if (card.hospitals.isNotEmpty()) {
                appendLine()
                appendLine(labels.hospitals)
                card.hospitals.forEach { h -> appendLine(listOfNotNull(h.name, h.phone?.let { "📞 $it" }, h.address).joinToString(" — ")) }
            }
            if (card.phrases.isNotEmpty()) {
                appendLine()
                appendLine(labels.phrases)
                card.phrases.forEach { p -> appendLine(listOfNotNull(p.english, p.local, p.phonetic?.let { "($it)" }).joinToString(" — ")) }
            }
            if (card.laws.isNotEmpty()) {
                appendLine()
                appendLine(labels.laws)
                card.laws.forEach { l -> appendLine("• ${l.law.orEmpty()}: ${l.detail.orEmpty()}") }
            }
        }
        appendLine()
        append(labels.footer)
    }

@Composable
private fun emergencyLabels() =
    EmergencyLabels(
        title = stringResource(R.string.emergency_share_title),
        police = stringResource(R.string.emergency_police),
        ambulance = stringResource(R.string.emergency_ambulance),
        fire = stringResource(R.string.emergency_fire),
        touristPolice = stringResource(R.string.emergency_tourist_police),
        hospitals = stringResource(R.string.emergency_hospitals),
        phrases = stringResource(R.string.emergency_phrases),
        laws = stringResource(R.string.emergency_laws),
        disclaimer = stringResource(R.string.emergency_disclaimer),
        footer = stringResource(R.string.emergency_share_footer),
    )

@Composable
internal fun EmergencyBody(data: EmergencyCardData, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val cards = emergencyCards(data)
    val empty = cards.all { it.numbers == null && it.embassy == null && it.hospitals.isEmpty() && it.phrases.isEmpty() && it.laws.isEmpty() }
    if (empty) {
        EmptyState(stringResource(R.string.emergency_unavailable), modifier)
        return
    }
    val context = LocalContext.current
    val labels = emergencyLabels()
    val shareTitle = stringResource(R.string.emergency_share)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        ConflictBanner(data.verification, onOpenLink)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            MetaText(stringResource(R.string.emergency_offline))
            TpButton(
                shareTitle,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, emergencyShareText(cards, labels))
                    try {
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    } catch (_: ActivityNotFoundException) {
                        // no share target: nothing to do
                    }
                },
                variant = TpButtonVariant.Secondary,
                leadingIcon = Icons.Filled.Share,
            )
        }
        Callout(stringResource(R.string.emergency_disclaimer), tone = Tone.Warning)
        val multi = cards.size > 1 || cards.first().city != null
        cards.forEach { card ->
            if (multi) {
                Surface(shape = TravelShape.ItemCard, border = BorderStroke(InfoDimens.Border, TravelTheme.colors.outlineVariant)) {
                    Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                        SubHeading(stringResource(R.string.emergency_city, card.city.orEmpty()))
                        CityCard(card, onOpenLink)
                    }
                }
            } else {
                CityCard(card, onOpenLink)
            }
        }
    }
}

@Composable
private fun CityCard(card: EmergencyCardModel, onOpenLink: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        val n = card.numbers
        val tiles =
            listOf(
                Triple(R.string.emergency_police, n?.police, Tone.Info),
                Triple(R.string.emergency_ambulance, n?.ambulance, Tone.Danger),
                Triple(R.string.emergency_fire, n?.fire, Tone.Warning),
                Triple(R.string.emergency_tourist_police, n?.touristPolice, Tone.Accent),
            )
        NumberGrid(tiles)
        card.embassy?.let { EmbassyBlock(it, onOpenLink) }
        if (card.embassy == null) {
            card.homeCountryNote?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.emergency_home_note, it),
                    style = TravelTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
        }
        if (card.hospitals.isNotEmpty()) {
            SubHeading(stringResource(R.string.emergency_hospitals))
            card.hospitals.forEach { HospitalRow(it) }
        }
        if (card.phrases.isNotEmpty()) PhraseTable(card.phrases)
        if (card.laws.isNotEmpty()) {
            SubHeading(stringResource(R.string.emergency_laws))
            card.laws.forEach { LawRow(it) }
        }
    }
}

@Composable
private fun NumberGrid(tiles: List<Triple<Int, String?, Tone>>) {
    // two per row on phones, four on wide screens (web grid-cols-2 sm:grid-cols-4)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= InfoDimens.TwoColumnMinWidth) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            tiles.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    row.forEach { (label, value, tone) -> NumberTile(stringResource(label), value, tone, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A big tap-to-dial tile; with no number it is plain text ("—"). */
@Composable
private fun NumberTile(label: String, value: String?, tone: Tone, modifier: Modifier) {
    val colors = toneColors(tone, SectionId.HOTELS)
    val context = LocalContext.current
    val number = dialableNumber(value)
    val a11y =
        if (number != null) stringResource(R.string.emergency_call_a11y, label, value.orEmpty()) else stringResource(R.string.emergency_no_number_a11y, label)
    Column(
        modifier
            .defaultMinSize(minHeight = InfoDimens.NumberTileMinHeight)
            .background(colors.container, TravelShape.Input)
            .then(if (number != null) Modifier.clickable(role = Role.Button) { dial(context, number) } else Modifier)
            .clearAndSetSemantics {
                contentDescription = a11y
                if (number != null) role = Role.Button
            }
            .padding(TravelTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = TravelTheme.typography.labelMedium, color = colors.content)
        Text(
            value?.takeIf {
                it.isNotBlank()
            } ?: stringResource(R.string.emergency_no_number),
            style = TravelTheme.typography.headlineSmall,
            color = colors.content,
        )
    }
}

/** A phone line that opens the dialer when the text holds a number. */
@Composable
private fun PhoneLine(phone: String?, suffix: String? = null) {
    if (phone.isNullOrBlank() && suffix.isNullOrBlank()) return
    val context = LocalContext.current
    val number = dialableNumber(phone)
    val text = listOfNotNull(phone?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.emergency_phone, it) }, suffix).joinToString(" · ")
    if (number == null) {
        MetaText(text)
    } else {
        TpButton(text, onClick = { dial(context, number) }, variant = TpButtonVariant.Text, leadingIcon = Icons.Filled.Call)
    }
}

@Composable
private fun EmbassyBlock(embassy: Embassy, onOpenLink: (String) -> Unit) {
    Surface(shape = TravelShape.Input, border = BorderStroke(InfoDimens.Border, TravelTheme.colors.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
            Text(stringResource(R.string.emergency_embassy, embassy.name.orEmpty()), style = TravelTheme.typography.titleSmall)
            embassy.address?.let { MetaText(it) }
            PhoneLine(embassy.phone, stringResource(R.string.emergency_after_hours, embassy.emergencyAfterHours?.takeIf { it.isNotBlank() } ?: "—"))
            embassy.hours?.takeIf { it.isNotBlank() }?.let { MetaText(it) }
            LinkButton(embassy.website.orEmpty(), embassy.website, onOpenLink)
        }
    }
}

@Composable
private fun HospitalRow(h: Hospital) {
    Surface(shape = TravelShape.Input, border = BorderStroke(InfoDimens.Border, TravelTheme.colors.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TravelTheme.spacing.md)) {
            Text(h.name.orEmpty(), style = TravelTheme.typography.titleSmall)
            PhoneLine(h.phone)
            h.address?.let { MetaText(it) }
            h.notes?.takeIf {
                it.isNotBlank()
            }?.let { Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.colors.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PhraseTable(phrases: List<LocalPhrase>) {
    SubHeading(stringResource(R.string.emergency_phrases))
    Surface(shape = TravelShape.Input, border = BorderStroke(InfoDimens.Border, TravelTheme.colors.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column {
            PhraseRow(
                stringResource(R.string.emergency_phrase_english),
                stringResource(R.string.emergency_phrase_local),
                stringResource(R.string.emergency_phrase_phonetic),
                header = true,
            )
            phrases.forEach { PhraseRow(it.english.orEmpty(), it.local.orEmpty(), it.phonetic.orEmpty(), header = false) }
        }
    }
}

@Composable
private fun PhraseRow(english: String, local: String, phonetic: String, header: Boolean) {
    val style = if (header) TravelTheme.typography.labelLarge else TravelTheme.typography.bodySmall
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (header) TravelTheme.colors.surfaceContainerLow else TravelTheme.colors.surface)
            .padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Text(english, style = style, modifier = Modifier.weight(1f))
        Text(local, style = style, modifier = Modifier.weight(1f))
        Text(
            phonetic,
            style = style,
            fontStyle = if (header) FontStyle.Normal else FontStyle.Italic,
            color = TravelTheme.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LawRow(law: LocalLaw) {
    val tone =
        when (law.severity) {
            "critical" -> Tone.Danger
            "warning" -> Tone.Warning
            else -> Tone.Neutral
        }
    val colors = toneColors(tone)
    Column(Modifier.fillMaxWidth().background(colors.container, TravelShape.Input).padding(TravelTheme.spacing.md)) {
        Text((if (law.severity == "critical") "🚨 " else "") + law.law.orEmpty(), style = TravelTheme.typography.titleSmall, color = colors.content)
        law.detail?.let { Text(it, style = TravelTheme.typography.bodySmall) }
    }
}

private val previewEmergency =
    EmergencyCardData(
        emergencyNumbers = EmergencyNumbers(police = "112", ambulance = "112", fire = "112", touristPolice = "+351 213 421 623"),
        embassy = Embassy(name = "U.S. Embassy Lisbon", address = "Av. das Forças Armadas", phone = "+351 21 727 3300", website = "https://pt.usembassy.gov"),
        hospitals = listOf(Hospital("Hospital de Santa Maria", "+351 21 780 5000", "Av. Prof. Egas Moniz")),
        localPhrases = listOf(LocalPhrase("Help!", "Socorro!", "soo-KOH-roo")),
        localLaws = listOf(LocalLaw("Drinking in public", "Allowed, but not on beaches after midnight.", "warning")),
    )

@Preview(name = "Emergency card – light")
@Composable
private fun EmergencyLightPreview() = PreviewSurface(ThemeMode.LIGHT) { EmergencyBody(previewEmergency, {}) }

@Preview(name = "Emergency card – dark")
@Composable
private fun EmergencyDarkPreview() = PreviewSurface(ThemeMode.DARK) { EmergencyBody(previewEmergency, {}) }
