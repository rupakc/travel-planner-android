package io.github.rupakc.travelplanner.feature.trips.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.trips.PackAddressCard
import io.github.rupakc.travelplanner.core.model.trips.PackCurrency
import io.github.rupakc.travelplanner.core.model.trips.PackPhrase
import io.github.rupakc.travelplanner.core.model.trips.PackPhrases
import io.github.rupakc.travelplanner.core.ui.itinerary.ItineraryDayCard
import io.github.rupakc.travelplanner.feature.trips.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** Full-screen pack viewer with its own top bar; leaves with [onBack]. Register it with [offlinePackDestination]. */
@Composable
fun OfflinePackRoute(tripId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(stringResource(R.string.offline_title), onBack = onBack) }) { padding ->
        OfflinePackTabContent(tripId, Modifier.padding(padding))
    }
}

/**
 * The pack as a trip tab (no app bar): the trip screen plugs this into its "offline" tab. It owns its
 * state and reads only the saved copy, so it renders with the network off.
 */
@Composable
fun OfflinePackTabContent(tripId: Long, modifier: Modifier = Modifier, viewModel: OfflinePackViewModel = hiltViewModel(key = "offline-pack-$tripId")) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(tripId) { viewModel.start(tripId) }
    OfflinePackScreen(state, viewModel::onEvent, modifier)
}

/** Stateless pack viewer. */
@Composable
fun OfflinePackScreen(state: OfflinePackUiState, onEvent: (OfflinePackEvent) -> Unit, modifier: Modifier = Modifier) {
    val view = state.view
    LazyColumn(
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "status", contentType = "status") { StatusCard(state, onEvent) }
        when {
            state.loading -> item(key = "loading", contentType = "loading") { SkeletonList(message = stringResource(R.string.offline_loading)) }
            view == null -> Unit
            else -> packSections(state, view, onEvent)
        }
    }
    state.shownPhrase?.let { PhraseFullScreen(it) { onEvent(OfflinePackEvent.ClosePhrase) } }
}

private fun LazyListScope.packSections(state: OfflinePackUiState, view: PackView, onEvent: (OfflinePackEvent) -> Unit) {
    val pack = view.pack
    view.emergency?.let { e -> item(key = "emergency", contentType = "emergency") { EmergencyCard(e) } }
    pack.addressCard?.takeIf { !it.hotelName.isNullOrBlank() }?.let { card ->
        item(key = "address", contentType = "address") { AddressCard(card) }
    }
    pack.phrases?.takeIf { it.items.isNotEmpty() }?.let { p ->
        item(key = "phrases", contentType = "phrases") { PhrasesCard(p) { onEvent(OfflinePackEvent.ShowPhrase(it)) } }
    }
    pack.currency?.let { c -> item(key = "currency", contentType = "currency") { CurrencyCard(c, state, onEvent) } }
    item(key = "itinerary-title", contentType = "heading") { SectionTitle(stringResource(R.string.offline_itinerary_title)) }
    if (view.days.isEmpty()) {
        item(key = "itinerary-none", contentType = "note") { Muted(stringResource(R.string.offline_no_itinerary)) }
    } else {
        items(view.days, key = { "day-${it.day.dayNumber}-${it.day.date}" }, contentType = { "day" }) { day ->
            ItineraryDayCard(day = day, edits = view.edits, notes = view.notes)
        }
    }
    item(key = "checkins", contentType = "checkins") { CheckinsCard(pack.checkins) }
}

@Composable
private fun StatusCard(state: OfflinePackUiState, onEvent: (OfflinePackEvent) -> Unit) {
    val view = state.view
    TpCard(size = TpCardSize.Large, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.offline_title), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Muted(stringResource(R.string.offline_intro))
        if (view != null) {
            val whenSaved = formatDateTime(view.pack.generatedAt)
            Text(
                (if (whenSaved != null) stringResource(R.string.offline_saved, whenSaved) + " · " else "") + stringResource(R.string.offline_size, view.kb),
                style = TravelTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = TravelTheme.spacing.sm),
            )
        }
        if (state.offline) Notice(stringResource(R.string.offline_offline_note), error = false)
        state.failure?.let { f ->
            val text =
                when (f.kind) {
                    PackFailure.Kind.QUOTA -> stringResource(R.string.offline_error_quota)
                    PackFailure.Kind.UNSUPPORTED -> stringResource(R.string.offline_unsupported)
                    PackFailure.Kind.FAILED -> f.message ?: stringResource(R.string.offline_error_error)
                }
            Notice(text, error = true)
        }
        if (state.failure?.kind != PackFailure.Kind.UNSUPPORTED) {
            Row(Modifier.padding(top = TravelTheme.spacing.md), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                if (view != null) {
                    TpButton(stringResource(R.string.offline_refresh), {
                        onEvent(OfflinePackEvent.Save)
                    }, enabled = !state.busy, loading = state.busy, tid = "offlinePack.refresh")
                    TpButton(stringResource(R.string.offline_remove), {
                        onEvent(OfflinePackEvent.Remove)
                    }, variant = TpButtonVariant.Secondary, enabled = !state.busy, tid = "offlinePack.remove")
                } else if (!state.loading) {
                    TpButton(
                        if (state.busy) stringResource(R.string.offline_saving) else stringResource(R.string.offline_save),
                        { onEvent(OfflinePackEvent.Save) },
                        enabled = !state.busy,
                        loading = state.busy,
                        tid = "offlinePack.save",
                    )
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, error: Boolean) {
    val colors = TravelTheme.colors
    Text(
        text,
        style = TravelTheme.typography.bodySmall,
        color = if (error) colors.error else TravelTheme.extendedColors.onWarningContainer,
        modifier = Modifier.padding(top = TravelTheme.spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun EmergencyCard(e: EmergencyView) {
    val uri = LocalUriHandler.current
    TpCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.offline_emergency_title))
        val label =
            if (e.aiChecked) {
                stringResource(
                    R.string.offline_emergency_checked,
                    formatDate(e.asOf) ?: "",
                )
            } else {
                stringResource(R.string.offline_emergency_reference)
            }
        Muted(label, Modifier.padding(bottom = TravelTheme.spacing.sm))
        e.cities.forEach { city ->
            if (e.cities.size > 1 && city.city != null) Text(city.city.orEmpty(), style = TravelTheme.typography.titleSmall)
            city.numbers.forEach { n ->
                val kind = kindLabel(n.kind)
                val cd = stringResource(R.string.offline_call_cd, kind, n.number)
                Surface(
                    onClick = { runCatching { uri.openUri(n.telUri) } },
                    shape = TravelShape.Input,
                    color = TravelTheme.colors.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = TravelTheme.spacing.xs).semantics { contentDescription = cd },
                ) {
                    Row(
                        Modifier.padding(
                            horizontal = TravelTheme.spacing.lg,
                            vertical = TravelTheme.spacing.sm,
                        ).heightIn(min = TravelTheme.spacing.minTouchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(kind, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onErrorContainer, modifier = Modifier.weight(1f))
                        Text(n.number, style = TravelTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TravelTheme.colors.onErrorContainer)
                    }
                }
            }
            city.embassy?.let { ContactLine(stringResource(R.string.offline_emergency_embassy), it, uri::openUri) }
            city.hospitals.forEach { ContactLine(stringResource(R.string.offline_emergency_hospital), it, uri::openUri) }
        }
    }
}

@Composable
private fun ContactLine(label: String, contact: EmergencyContact, open: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${contact.name}", style = TravelTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        contact.phone?.let { phone ->
            TpButton(phone, { runCatching { open(telUri(phone)) } }, variant = TpButtonVariant.Text, tid = "offlinePack.call")
        }
    }
}

@Composable
private fun kindLabel(kind: String): String =
    when (kind) {
        "police" -> stringResource(R.string.offline_kind_police)
        "ambulance" -> stringResource(R.string.offline_kind_ambulance)
        "fire" -> stringResource(R.string.offline_kind_fire)
        "tourist_police" -> stringResource(R.string.offline_kind_tourist_police)
        "general" -> stringResource(R.string.offline_kind_general)
        else -> kind.replace('_', ' ')
    }

@Composable
private fun AddressCard(card: PackAddressCard) {
    TpCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.offline_address_title))
        val phrase = card.takeMeTo
        Text(
            phrase?.text?.takeIf { it.isNotBlank() } ?: stringResource(R.string.offline_address_fallback),
            style = TravelTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = TravelTheme.spacing.sm),
        )
        phrase?.romanized?.let { Muted(it) }
        Text(
            card.hotelName.orEmpty(),
            style = TravelTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = TravelTheme.spacing.md),
        )
        card.area?.let { Text(it, style = TravelTheme.typography.titleMedium) }
        card.city?.let { Text(it, style = TravelTheme.typography.titleMedium) }
    }
}

@Composable
private fun PhrasesCard(phrases: PackPhrases, onShow: (PackPhrase) -> Unit) {
    TpCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.offline_phrases_title, phrases.languageName ?: phrases.language.orEmpty()))
        phrases.items.forEach { p ->
            Row(Modifier.padding(vertical = TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Muted(phraseLabel(p.id))
                    Text(p.text, style = TravelTheme.typography.titleMedium)
                    p.romanized?.let { Muted(it) }
                }
                TpButton(stringResource(R.string.offline_show_local), { onShow(p) }, variant = TpButtonVariant.Secondary, tid = "offlinePack.phrase.show")
            }
        }
    }
}

@Composable
private fun phraseLabel(id: String?): String =
    when (id) {
        "help" -> stringResource(R.string.offline_phrase_help)
        "call_police" -> stringResource(R.string.offline_phrase_call_police)
        "need_doctor" -> stringResource(R.string.offline_phrase_need_doctor)
        "take_me_to" -> stringResource(R.string.offline_phrase_take_me_to)
        "thank_you" -> stringResource(R.string.offline_phrase_thank_you)
        "how_much" -> stringResource(R.string.offline_phrase_how_much)
        "where_toilet" -> stringResource(R.string.offline_phrase_where_toilet)
        "dont_understand" -> stringResource(R.string.offline_phrase_dont_understand)
        else -> id.orEmpty()
    }

/** "Show to a local": the phrase as large as the screen allows, to hold up to someone. */
@Composable
private fun PhraseFullScreen(phrase: PackPhrase, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = TravelTheme.colors.surface) {
            Column(
                Modifier.fillMaxSize().padding(TravelTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(phrase.text, style = TravelTheme.typography.displayMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                phrase.romanized?.let { Text(it, style = TravelTheme.typography.headlineSmall, textAlign = TextAlign.Center) }
                Muted(phraseLabel(phrase.id))
                TpButton(stringResource(R.string.offline_phrases_close), onClose, tid = "offlinePack.phrase.close")
            }
        }
    }
}

@Composable
private fun CurrencyCard(c: PackCurrency, state: OfflinePackUiState, onEvent: (OfflinePackEvent) -> Unit) {
    val local = c.local.orEmpty()
    val home = c.home.orEmpty()
    TpCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.offline_currency_title))
        val rate = c.rate
        if (rate == null) {
            Muted(stringResource(R.string.offline_currency_no_rate, local))
            return@TpCard
        }
        val from = if (state.toLocal) home else local
        val to = if (state.toLocal) local else home
        val locale = LocalLocale.current.platformLocale
        val result = PackConverter.convert(state.amount, rate, state.toLocal, to, locale)
        Row(
            Modifier.padding(top = TravelTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        ) {
            TpOutlinedTextField(
                value = state.amount,
                onValueChange = { onEvent(OfflinePackEvent.SetAmount(it)) },
                label = { Text(stringResource(R.string.offline_currency_amount, from)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                tid = "trips.offline_currency_amount",
            )
            TpButton("⇄", { onEvent(OfflinePackEvent.Swap) }, variant = TpButtonVariant.Secondary, tid = "offlinePack.swap")
        }
        Text(
            "${result ?: "—"} $to",
            style = TravelTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = TravelTheme.spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Muted(
            stringResource(R.string.offline_currency_rate_from, formatDate(c.asOf) ?: "", sourceLabel(c.source)),
            Modifier.padding(top = TravelTheme.spacing.xs),
        )
    }
}

@Composable
private fun sourceLabel(source: String?): String = if (source == "snapshot") stringResource(R.string.offline_currency_source_snapshot) else source.orEmpty()

@Composable
private fun CheckinsCard(days: List<String>) {
    TpCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.offline_checkins_title))
        if (days.isEmpty()) {
            Muted(stringResource(R.string.offline_checkins_none))
        } else {
            days.forEach { Text(formatDate(it) ?: it, style = TravelTheme.typography.bodyMedium, modifier = Modifier.padding(top = TravelTheme.spacing.xs)) }
        }
    }
}

/** A medium date from an ISO instant or day; null when [value] is blank or unreadable. */
internal fun formatDate(value: String?): String? = parseMoment(value)?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it) }

internal fun formatDateTime(value: String?): String? =
    parseMoment(value)?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(it) }

private fun parseMoment(value: String?): java.time.temporal.TemporalAccessor? =
    try {
        when {
            value.isNullOrBlank() -> null
            value.length <= DAY_LENGTH -> LocalDate.parse(value)
            else -> Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }
    } catch (_: DateTimeParseException) {
        null
    }

private const val DAY_LENGTH = 10
