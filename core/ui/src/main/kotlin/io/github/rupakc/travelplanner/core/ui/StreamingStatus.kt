package io.github.rupakc.travelplanner.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus

/**
 * Invisible polite live region that announces sections as they become ready
 * ("Hotels ready") while a search streams (spec §9). Place once per results screen.
 */
@Composable
fun StreamingStatusAnnouncer(statuses: Map<SectionId, SectionStatus>, modifier: Modifier = Modifier) {
    var announced by remember { mutableStateOf(emptySet<SectionId>()) }
    var message by remember { mutableStateOf("") }
    val ready = statuses.filterValues { it == SectionStatus.DONE || it == SectionStatus.ENHANCING }.keys
    val newest = (ready - announced).firstOrNull()
    val title = newest?.let { stringResource(it.titleRes()) }
    val text = title?.let { stringResource(R.string.section_ready_announcement, it) }
    LaunchedEffect(newest) {
        if (newest != null && text != null) {
            message = text
            announced = announced + newest
        }
    }
    Box(
        modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            },
    )
}
