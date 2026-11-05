package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.Customs
import io.github.rupakc.travelplanner.core.model.section.DutyFreeAllowances
import io.github.rupakc.travelplanner.core.model.section.Vaccinations
import io.github.rupakc.travelplanner.core.model.section.Verification
import io.github.rupakc.travelplanner.core.model.section.VerificationCheck
import io.github.rupakc.travelplanner.core.model.section.VerificationLevel
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.core.model.section.VisaRequirement
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.feature.results.R

/** Web typeColors: visa-free green, on arrival blue, e-visa yellow, required red, other gray. */
internal fun visaTone(type: String?): Tone =
    when (type) {
        "visa-free" -> Tone.Success
        "visa-on-arrival" -> Tone.Info
        "e-visa" -> Tone.Warning
        "visa-required" -> Tone.Danger
        else -> Tone.Neutral
    }

/** "VISA ON ARRIVAL" from "visa-on-arrival" (web label); null when absent. */
internal fun visaTypeLabel(type: String?): String? = type?.takeIf { it.isNotBlank() }?.replace("-", " ")?.uppercase()

/** Rose conflict banner with the failing check's official link (web VerificationConflictBanner). */
@Composable
internal fun ConflictBanner(verification: Verification?, onOpenLink: (String) -> Unit) {
    if (verification?.levelKind != VerificationLevel.CONFLICT) return
    val link = verification.checks.firstNotNullOfOrNull { c: VerificationCheck -> if (c.status == "fail") safeHttpUrl(c.url) else null }
    val colors = toneColors(Tone.Danger)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.container, TravelShape.Input)
            .padding(TravelTheme.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.content, modifier = Modifier.size(InfoDimens.SmallIcon))
            Text(verification.summary.orEmpty(), style = TravelTheme.typography.bodySmall, color = colors.content)
        }
        LinkButton(stringResource(R.string.visa_official_site), link, onOpenLink)
    }
}

@Composable
internal fun VisaBody(data: VisaData, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val req = data.requirement
    val vacc = data.vaccinations
    val customs = data.customs
    if (req == null && vacc == null && customs == null) {
        EmptyState(stringResource(R.string.visa_unavailable), modifier)
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
        ConflictBanner(data.verification, onOpenLink)
        req?.let { Requirements(it, onOpenLink) }
        vacc?.let {
            if (req != null) HorizontalDivider(color = TravelTheme.colors.outlineVariant)
            Health(it, onOpenLink)
        }
        customs?.let {
            if (req != null || vacc != null) HorizontalDivider(color = TravelTheme.colors.outlineVariant)
            CustomsBlock(it, onOpenLink)
        }
    }
}

@Composable
private fun Requirements(req: VisaRequirement, onOpenLink: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            val colors = toneColors(visaTone(req.visaType))
            Text(
                visaTypeLabel(req.visaType) ?: stringResource(R.string.visa_unknown_type),
                style = TravelTheme.typography.titleSmall,
                color = colors.content,
                modifier =
                    Modifier.background(
                        colors.container,
                        TravelShape.Pill,
                    ).padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.xs),
            )
            req.confidence?.takeIf { it.isNotBlank() }?.let { MetaText(stringResource(R.string.visa_confidence, it)) }
        }
        val tiles =
            listOfNotNull(
                req.maxStayDays?.takeIf { it > 0 }?.let { stringResource(R.string.visa_max_stay) to pluralStringResource(R.plurals.visa_days, it, it) },
                req.processingTime?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.visa_processing) to it },
                req.feeUsd?.let { stringResource(R.string.visa_fee) to (if (it == 0.0) stringResource(R.string.visa_fee_free) else localPrice(it)) },
            )
        if (tiles.isNotEmpty()) {
            AdaptiveGrid(tiles) { (label, value), cell -> Tile(label, value, cell) }
        }
        if (req.requirements.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                req.requirements.forEach { IconLine(Icons.Filled.CheckCircle, TravelTheme.extendedColors.success, it) }
            }
        }
        req.notes?.takeIf { it.isNotBlank() }?.let { Callout(it, tone = Tone.Info, icon = Icons.Filled.Info) }
        LinkButton(stringResource(R.string.visa_official_source), req.officialUrl, onOpenLink)
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(TravelTheme.colors.surfaceContainerLow, TravelShape.Input).padding(TravelTheme.spacing.md)) {
        Text(label, style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
        Text(value, style = TravelTheme.typography.titleSmall)
    }
}

@Composable
private fun IconLine(icon: ImageVector, tint: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(InfoDimens.SmallIcon))
        Text(text, style = TravelTheme.typography.bodySmall)
    }
}

@Composable
private fun Health(vacc: Vaccinations, onOpenLink: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        SubHeading(stringResource(R.string.visa_health))
        if (vacc.required.isNotEmpty()) {
            Text(stringResource(R.string.visa_required), style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.error)
            vacc.required.forEach { IconLine(Icons.Filled.Warning, TravelTheme.colors.error, it) }
        }
        if (vacc.recommended.isNotEmpty()) {
            Text(stringResource(R.string.visa_recommended), style = TravelTheme.typography.labelLarge, color = TravelTheme.extendedColors.warning)
            vacc.recommended.forEach { IconLine(Icons.Filled.CheckCircle, TravelTheme.extendedColors.warning, it) }
        }
        vacc.covidStatus?.takeIf { it.isNotBlank() }?.let { Callout(it, tone = Tone.Success, icon = Icons.Filled.Info) }
        vacc.notes?.takeIf { it.isNotBlank() }?.let { MetaText(it) }
        LinkButton(stringResource(R.string.visa_health_source), vacc.sourceUrl, onOpenLink)
    }
}

@Composable
private fun allowances(a: DutyFreeAllowances): List<Pair<String, String>> =
    listOfNotNull(
        a.alcohol?.let { stringResource(R.string.visa_alcohol) to it },
        a.tobacco?.let { stringResource(R.string.visa_tobacco) to it },
        a.currency?.let { stringResource(R.string.visa_currency) to it },
        a.gifts?.let { stringResource(R.string.visa_gifts) to it },
    ).filter { it.second.isNotBlank() }

@Composable
private fun CustomsBlock(customs: Customs, onOpenLink: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        SubHeading(stringResource(R.string.visa_customs))
        customs.dutyFreeAllowances?.let { allowances(it) }?.takeIf { it.isNotEmpty() }?.let { items ->
            Text(stringResource(R.string.visa_duty_free), style = TravelTheme.typography.labelLarge)
            AdaptiveGrid(items) { (label, value), cell -> Tile(label, value, cell) }
        }
        if (customs.prohibitedItems.isNotEmpty()) {
            Text(stringResource(R.string.visa_prohibited), style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                customs.prohibitedItems.forEach { InfoPill(it, tone = Tone.Danger) }
            }
        }
        if (customs.declarationRequired.isNotEmpty()) {
            Text(stringResource(R.string.visa_must_declare), style = TravelTheme.typography.labelLarge, color = TravelTheme.extendedColors.warning)
            customs.declarationRequired.forEach { IconLine(Icons.Filled.Warning, TravelTheme.extendedColors.warning, it) }
        }
        customs.notes?.takeIf { it.isNotBlank() }?.let { Callout(it, tone = Tone.Warning, icon = Icons.Filled.Info) }
        LinkButton(stringResource(R.string.visa_customs_source), customs.sourceUrl, onOpenLink)
    }
}

private val previewVisa =
    VisaData(
        requirement =
            VisaRequirement(
                visaType = "visa-free",
                maxStayDays = 90,
                requirements = listOf("Passport valid 3 months beyond stay", "Proof of onward travel"),
                feeUsd = 0.0,
                officialUrl = "https://example.org/visa",
                confidence = "high",
            ),
        vaccinations = Vaccinations(recommended = listOf("Hepatitis A")),
        customs =
            Customs(
                dutyFreeAllowances = DutyFreeAllowances(alcohol = "1 L spirits", tobacco = "200 cigarettes"),
                prohibitedItems = listOf("Meat products"),
            ),
        verification = Verification(level = "conflict", summary = "Official site lists a different maximum stay."),
    )

@Preview(name = "Visa – light")
@Composable
private fun VisaLightPreview() = PreviewSurface(ThemeMode.LIGHT) { VisaBody(previewVisa, {}) }

@Preview(name = "Visa – dark")
@Composable
private fun VisaDarkPreview() = PreviewSurface(ThemeMode.DARK) { VisaBody(previewVisa, {}) }
