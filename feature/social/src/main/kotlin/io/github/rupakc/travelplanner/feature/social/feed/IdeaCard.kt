package io.github.rupakc.travelplanner.feature.social.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconToggleButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.feature.social.R

private const val CAPTION_LINES = 3

/** Destinations and days of the idea's snapshot as one line ("Lisbon, Porto · 5 days"); empty when it has neither. */
@Composable
internal fun ideaSummary(idea: Idea): String {
    val places = idea.snapshot.destinations.joinToString(", ")
    val days = idea.snapshot.days?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.social_idea_days, it, it) }
    return listOfNotNull(places.takeIf { it.isNotBlank() }, days).joinToString(" · ")
}

/** The three counts as one line; zero counts are left out. */
@Composable
internal fun ideaCounts(idea: Idea): String {
    val saves = idea.saves ?: 0
    val comments = idea.comments ?: 0
    return listOfNotNull(
        saves.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.social_idea_saves, it, it) },
        comments.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.social_idea_comments, it, it) },
    ).joinToString(" · ")
}

/** The like toggle with its count: one 48 dp target, the state is spoken, not only coloured. */
@Composable
internal fun LikeButton(liked: Boolean, count: Int, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (liked) R.string.social_idea_unlike else R.string.social_idea_like)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TpIconToggleButton(checked = liked, onCheckedChange = {
            onToggle()
        }, enabled = enabled, modifier = Modifier.semantics { stateDescription = label }, tid = "social.idea_card_icon_toggle_button") {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = label,
                tint = if (liked) TravelTheme.colors.primary else TravelTheme.colors.onSurfaceVariant,
            )
        }
        Text(pluralStringResource(R.plurals.social_idea_likes, count, count), style = TravelTheme.typography.bodySmall)
    }
}

/**
 * One idea in the feed. Everything the author wrote is plain `Text` (no links, no markdown). The card opens the idea; like and
 * save act in place.
 */
@Composable
internal fun IdeaCard(
    idea: Idea,
    busy: Boolean,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TpCard(modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter), onClick = onOpen) {
        Text(
            idea.title,
            Modifier.padding(horizontal = TravelTheme.spacing.md).padding(top = TravelTheme.spacing.md).semantics { heading() },
            style = TravelTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.social_idea_author, idea.author.handle),
            Modifier.padding(horizontal = TravelTheme.spacing.md),
            style = TravelTheme.typography.labelMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        val summary = ideaSummary(idea)
        if (summary.isNotEmpty()) {
            Text(summary, Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs), style = TravelTheme.typography.bodyMedium)
        }
        if (idea.caption.isNotBlank()) {
            Text(
                idea.caption,
                Modifier.padding(horizontal = TravelTheme.spacing.md),
                style = TravelTheme.typography.bodyMedium,
                maxLines = CAPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val counts = ideaCounts(idea)
        if (counts.isNotEmpty()) {
            Text(
                counts,
                Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = TravelTheme.spacing.minTouchTarget).padding(horizontal = TravelTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LikeButton(idea.liked, idea.likes ?: 0, enabled = !busy, onToggle = onLike)
            if (!idea.mine) {
                TpButton(
                    stringResource(if (idea.saved) R.string.social_idea_saved else R.string.social_idea_save),
                    onSave,
                    variant = if (idea.saved) TpButtonVariant.Text else TpButtonVariant.Tonal,
                    enabled = !busy,
                    tid = "social.idea.save",
                )
            }
        }
    }
}
