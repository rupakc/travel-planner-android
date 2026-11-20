package io.github.rupakc.travelplanner.core.ui.facts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.facts.PlaceFactsView
import io.github.rupakc.travelplanner.core.model.plan.PlaceFactPick
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.PlaceFact
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsData
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.core.ui.R
import io.github.rupakc.travelplanner.core.ui.carbon.CarbonFormat
import io.github.rupakc.travelplanner.core.ui.carbon.currentCarbonLocale

/** One source line's fields (a crawler fact's `citation`, or the copy My Plan keeps). */
data class FactSource(
    val name: String?,
    val url: String?,
    val asOf: String?,
    val attribution: String?,
    val licenceUrl: String?,
    val conflict: Boolean = false,
)

/**
 * The text of a fact: in its own language ([lang]) when known, with a single quoted value (an official name, a motto)
 * marked in [valueLang] (web `FactText`). Compose marks languages with a span [LocaleList], which picks the right
 * glyphs (Han variants) and hyphenation.
 */
fun factText(text: String, lang: String?, valueLang: String?): AnnotatedString {
    val outer = PlaceFactsView.langTag(lang)
    val parts = PlaceFactsView.quotedValue(text, valueLang)
    return buildAnnotatedString {
        val body: AnnotatedString.Builder.() -> Unit = {
            if (parts == null) {
                append(text)
            } else {
                append(parts.before)
                withStyle(SpanStyle(localeList = LocaleList(parts.lang))) { append(parts.value) }
                append(parts.after)
            }
        }
        if (outer != null) withStyle(SpanStyle(localeList = LocaleList(outer))) { body() } else body()
    }
}

/**
 * A crawler fact's source line (web `SourceLine`): "Source: name" (a link when it has one), "as of" date, the licence
 * attribution (linked to the licence when it has a link) and a "sources disagree" warning. Renders nothing without a name.
 */
@Composable
fun FactSourceLine(source: FactSource?, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val name = source?.name?.takeIf { it.isNotBlank() } ?: return
    val url = PlaceFactsView.link(source.url)
    val asOf = PlaceFactsView.day(source.asOf)?.let { CarbonFormat.date(it, currentCarbonLocale()) }
    val label = stringResource(R.string.facts_source, name)
    Column(modifier.testTag("fact_source_line")) {
        if (url != null) {
            TpTextButton(onClick = { onOpenLink(url) }, tid = "results.official.source") { Text(label, style = TravelTheme.typography.labelSmall) }
        } else {
            Small(label)
        }
        asOf?.let { Small(stringResource(R.string.facts_as_of, it)) }
        source.attribution?.takeIf { it.isNotBlank() }?.let { attribution ->
            val licence = PlaceFactsView.link(source.licenceUrl)
            if (licence != null) {
                TpTextButton(onClick = { onOpenLink(licence) }, tid = "results.facts.licence") { Text(attribution, style = TravelTheme.typography.labelSmall) }
            } else {
                Small(attribution)
            }
        }
        if (source.conflict) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = TravelTheme.extendedColors.warning, modifier = Modifier.size(ICON))
                Text(stringResource(R.string.facts_conflict), style = TravelTheme.typography.bodySmall, color = TravelTheme.extendedColors.warning)
            }
        }
    }
}

/** The "AI-suggested, not verified" pill on every AI fact (text, never colour alone). */
@Composable
fun AiFactLabel(modifier: Modifier = Modifier) {
    val x = TravelTheme.extendedColors
    Surface(shape = TravelShape.Pill, color = x.warningContainer, contentColor = x.onWarningContainer, modifier = modifier.testTag("fact_ai_label")) {
        Row(
            Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(ICON))
            Text(stringResource(R.string.facts_ai_label), style = TravelTheme.typography.labelSmall)
        }
    }
}

/**
 * One fact kept in My Plan (web `SelectedFact`): "Place: text", then the crawler fact's source line, or the AI label.
 * A fact saved with the old `quote` flag shows as plain text with its attribution.
 */
@Composable
fun SelectedPlaceFact(fact: PlaceFactPick, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val text = fact.text.orEmpty()
    val shown = if (fact.quote == true) AnnotatedString(text) else factText(text, fact.lang, fact.valueLang)
    val prefix = fact.place?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.facts_place_prefix, it) }
    Column(modifier.testTag("selected_fact"), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
        Text(
            buildAnnotatedString {
                if (prefix != null) append(prefix)
                append(shown)
            },
            style = TravelTheme.typography.bodyMedium,
        )
        val c = fact.citation
        if (fact.isCrawler && c != null) {
            FactSourceLine(FactSource(c.name, c.url, c.asOf, c.attribution, c.licenceUrl), onOpenLink)
        } else if (!fact.isCrawler) {
            AiFactLabel()
        }
    }
}

/**
 * The Place facts card body (web `PlaceFactsSection`): place chips (several places), group chips with counts, the
 * facts of the chosen group (crawler facts with their source line, AI facts with their label, each with Add to plan)
 * and the AI note. Renders nothing when [data] has no fact.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceFactsBody(
    data: PlaceFactsData,
    isSelected: (PlanItem) -> Boolean,
    onToggle: (PlanItem) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val places = remember(data) { PlaceFactsView.places(data) }
    if (places.isEmpty()) return
    var placeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val place = places.firstOrNull { it.key == placeKey } ?: places.first()
    val available = PlaceFactsView.availableGroups(place)
    val active = group?.takeIf { it in available } ?: available.first()
    val facts = place.groups[active].orEmpty()
    Column(modifier.fillMaxWidth().testTag("place_facts"), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        if (places.size > 1) {
            val label = stringResource(R.string.facts_places_label)
            FlowRow(
                Modifier.semantics { contentDescription = label },
                horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
            ) {
                places.forEach { p ->
                    val name = p.name.orEmpty()
                    val scope = scopeLabel(p.scope)
                    TpChip(
                        label = if (scope != null) stringResource(R.string.facts_place_chip, name, scope) else name,
                        selected = p.key == place.key,
                        onClick = { placeKey = p.key },
                        tid = "results.facts.place",
                    )
                }
            }
        }
        val groupsLabel = stringResource(R.string.facts_groups_label)
        FlowRow(Modifier.semantics { contentDescription = groupsLabel }, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            available.forEach { t ->
                TpChip(
                    label = stringResource(R.string.facts_group_count, groupLabel(t), place.groups[t].orEmpty().size),
                    selected = t == active,
                    onClick = { group = t },
                    tid = "results.facts.group",
                )
            }
        }
        facts.forEach { fact ->
            val item = PlanItem.PlaceFactItem(PlaceFactsView.selection(fact, place))
            FactCard(fact, isSelected(item), onToggle = { onToggle(item) }, onOpenLink = onOpenLink)
        }
        if (facts.any { !it.isCrawler }) {
            Small(stringResource(R.string.facts_ai_note))
        }
    }
}

@Composable
private fun FactCard(fact: PlaceFact, selected: Boolean, onToggle: () -> Unit, onOpenLink: (String) -> Unit) {
    val text = fact.text.orEmpty()
    TpCard(Modifier.fillMaxWidth().testTag("place_fact"), selected = selected) {
        Text(factText(text, fact.lang, fact.valueLang), style = TravelTheme.typography.bodyMedium)
        Column(Modifier.padding(top = TravelTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            if (fact.isCrawler) {
                val c = fact.citation
                FactSourceLine(c?.let { FactSource(it.name ?: it.sourceId, it.url, it.asOf, it.attribution, it.licenceUrl, it.conflict == true) }, onOpenLink)
                if (fact.machineTranslated == true) Small(stringResource(R.string.facts_machine_translated))
            } else {
                AiFactLabel()
            }
            AddToPlanButton(selected, text, onToggle, tid = "results.fact.add")
        }
    }
}

@Composable
private fun groupLabel(type: String): String =
    when (type) {
        "historical" -> stringResource(R.string.facts_group_historical)
        "fun" -> stringResource(R.string.facts_group_fun)
        "popular" -> stringResource(R.string.facts_group_popular)
        "unbelievable" -> stringResource(R.string.facts_group_unbelievable)
        "little_known" -> stringResource(R.string.facts_group_little_known)
        else -> type
    }

@Composable
private fun scopeLabel(scope: String?): String? =
    when (scope) {
        "city" -> stringResource(R.string.facts_scope_city)
        "place" -> stringResource(R.string.facts_scope_place)
        "region" -> stringResource(R.string.facts_scope_region)
        "country" -> stringResource(R.string.facts_scope_country)
        else -> null
    }

@Composable
private fun Small(text: String) {
    Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
}

private val ICON = 12.dp
