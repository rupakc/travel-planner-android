package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.StressFinding
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.R
import kotlinx.serialization.Serializable

/** Overall verdict (web STRESS_OVERALL_STYLES); unknown values read as amber. */
internal enum class HealthOverall(val label: Int) {
    GREEN(R.string.health_green),
    AMBER(R.string.health_amber),
    RED(R.string.health_red),
    ;

    companion object {
        fun of(value: String?): HealthOverall =
            when (value) {
                "green" -> GREEN
                "red" -> RED
                else -> AMBER
            }
    }
}

/** Findings high → medium → low → other, stable within a severity (web sort). */
internal fun sortedFindings(findings: List<StressFinding>): List<StressFinding> {
    val rank = mapOf("high" to 0, "medium" to 1, "low" to 2)
    return findings.sortedBy { rank[it.severity] ?: OTHER_RANK }
}

private const val OTHER_RANK = 3
private const val MAX_SCORE = 100
private const val FULL_CIRCLE = 360f
private const val TOP = -90f

/** Web STRESS_CATEGORY_EMOJI (🔎 for others). */
internal fun healthCategoryEmoji(category: String?): String =
    when (category) {
        "pacing" -> "🏃"
        "timing" -> "⏰"
        "visa" -> "🛂"
        "weather" -> "🌧️"
        "budget" -> "💸"
        "logistics" -> "🧭"
        else -> "🔎"
    }

/** A re-check result and the edited itinerary it ran against (see `StressTestPayloads.recheckFingerprint`). */
@Serializable
internal data class RecheckResult(val data: StressTestData, val fingerprint: String)

/** A failed re-check: the server's message (null → generic copy) for the plan it ran against. */
@Serializable
internal data class RecheckFailure(val message: String?, val fingerprint: String)

/** Re-check state kept across scrolling/rotation (saved as JSON text). */
@Serializable
internal data class RecheckState(val result: RecheckResult? = null, val failure: RecheckFailure? = null, val running: Boolean = false) {
    fun encode(): String = TravelJson.encodeToString(serializer(), copy(running = false))

    companion object {
        fun decode(text: String): RecheckState = runCatching { TravelJson.decodeFromString(serializer(), text) }.getOrDefault(RecheckState())
    }
}

/** What the card shows for the current plan (web StressTestSection rules). */
internal enum class RecheckNote { NONE, ORIGINAL, EDITED, STALE }

internal data class HealthView(val data: StressTestData?, val note: RecheckNote, val showRecheck: Boolean, val error: RecheckFailure?)

/**
 * A re-check only applies while the layout differs from the original ([layoutMoved]); it is stale once the
 * edited plan's fingerprint changes, and an error belongs to the attempt that failed.
 */
internal fun healthView(original: StressTestData, layoutMoved: Boolean, state: RecheckState, currentFingerprint: String?): HealthView {
    val rechecked = state.result.takeIf { layoutMoved }
    val stale = rechecked != null && rechecked.fingerprint != currentFingerprint
    val note =
        when {
            !layoutMoved -> RecheckNote.NONE
            rechecked == null -> RecheckNote.ORIGINAL
            stale -> RecheckNote.STALE
            else -> RecheckNote.EDITED
        }
    val error = state.failure?.takeIf { layoutMoved && it.fingerprint == currentFingerprint }
    return HealthView(rechecked?.data ?: original, note, showRecheck = layoutMoved && (rechecked == null || stale), error = error)
}

@Composable
internal fun HealthBody(view: HealthView, rechecking: Boolean, onRecheck: () -> Unit, modifier: Modifier = Modifier) {
    val data = view.data
    if (data == null || data.isError) {
        EmptyState(stringResource(R.string.health_unavailable), modifier)
        return
    }
    val overall = HealthOverall.of(data.overall)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
        Surface(shape = TravelShape.ItemCard, color = TravelTheme.colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(TravelTheme.spacing.md), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
                ScoreRing((data.score ?: 0).coerceIn(0, MAX_SCORE), overall)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                    InfoPill(stringResource(overall.label), tone = overall.tone())
                    data.summary?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
                    MetaText(stringResource(R.string.health_caption))
                    RecheckLine(view, rechecking, onRecheck)
                }
            }
        }
        val findings = sortedFindings(data.findings)
        if (findings.isEmpty()) {
            Text(stringResource(R.string.health_no_issues), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) { findings.forEach { FindingCard(it) } }
        }
    }
}

private fun HealthOverall.tone(): Tone =
    when (this) {
        HealthOverall.GREEN -> Tone.Success
        HealthOverall.AMBER -> Tone.Warning
        HealthOverall.RED -> Tone.Danger
    }

@Composable
private fun RecheckLine(view: HealthView, rechecking: Boolean, onRecheck: () -> Unit) {
    when (view.note) {
        RecheckNote.NONE -> {
            Unit
        }

        RecheckNote.ORIGINAL -> {
            Text(
                stringResource(R.string.health_original),
                style = TravelTheme.typography.labelSmall,
                color = TravelTheme.extendedColors.warning,
            )
        }

        RecheckNote.STALE -> {
            Text(stringResource(R.string.health_stale), style = TravelTheme.typography.labelSmall, color = TravelTheme.extendedColors.warning)
        }

        RecheckNote.EDITED -> {
            Text(stringResource(R.string.health_edited), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.primary)
        }
    }
    if (view.showRecheck) {
        TpButton(
            stringResource(if (rechecking) R.string.health_rechecking else R.string.health_recheck),
            onClick = onRecheck,
            loading = rechecking,
            variant = TpButtonVariant.Secondary,
            leadingIcon = Icons.Filled.Refresh,
        )
    }
    view.error?.let {
        Text(
            it.message?.takeIf { m -> m.isNotBlank() } ?: stringResource(R.string.health_recheck_error),
            style = TravelTheme.typography.labelMedium,
            color = TravelTheme.colors.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

/** Score ring (web r=26, stroke 6 on a 64 box) with the number in the middle; read as one sentence. */
@Composable
private fun ScoreRing(score: Int, overall: HealthOverall) {
    val color: Color =
        when (overall) {
            HealthOverall.GREEN -> TravelTheme.extendedColors.success
            HealthOverall.AMBER -> TravelTheme.extendedColors.warning
            HealthOverall.RED -> TravelTheme.colors.error
        }
    val track = TravelTheme.colors.outlineVariant
    val a11y = stringResource(R.string.health_score_a11y, score, stringResource(overall.label))
    Box(Modifier.size(InfoDimens.ScoreRing).clearAndSetSemantics { contentDescription = a11y }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(InfoDimens.ScoreRing)) {
            val stroke = InfoDimens.ScoreStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, FULL_CIRCLE, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                color,
                TOP,
                FULL_CIRCLE * score / MAX_SCORE,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text("$score", style = TravelTheme.typography.titleMedium)
    }
}

@Composable
private fun FindingCard(f: StressFinding) {
    val (label, tone) =
        when (f.severity) {
            "high" -> R.string.health_high to Tone.Danger
            "medium" -> R.string.health_medium to Tone.Warning
            else -> R.string.health_low to Tone.Info
        }
    Surface(shape = TravelShape.ItemCard, color = TravelTheme.colors.surface, border = BorderStroke(InfoDimens.Border, TravelTheme.colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
                InfoPill(stringResource(label), tone = tone)
                f.category?.takeIf { it.isNotBlank() }?.let { MetaText("${healthCategoryEmoji(it)} $it") }
                f.dayNumber?.let { InfoPill(stringResource(R.string.health_day, it)) }
            }
            f.issue?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
            f.suggestion?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.health_suggestion, it), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.primary)
            }
        }
    }
}

private val previewHealth =
    StressTestData(
        overall = "amber",
        summary = "Day 2 is packed and the evening tour ends late.",
        score = 68,
        findings =
            listOf(
                StressFinding("low", "budget", null, "Fado dinner is above the daily average.", null),
                StressFinding("high", "pacing", 2, "Three museums back to back.", "Move one to Day 3."),
            ),
    )

@Preview(name = "Trip Health Check – light")
@Composable
private fun HealthLightPreview() = PreviewSurface(ThemeMode.LIGHT) { HealthBody(healthView(previewHealth, false, RecheckState(), null), false, {}) }

@Preview(name = "Trip Health Check – dark, moved")
@Composable
private fun HealthDarkPreview() =
    PreviewSurface(ThemeMode.DARK) {
        HealthBody(healthView(previewHealth, true, RecheckState(failure = RecheckFailure(null, "fp")), "fp"), rechecking = false, onRecheck = {})
    }
