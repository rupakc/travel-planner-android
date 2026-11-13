package io.github.rupakc.travelplanner.core.ui.journey

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.component.tpTapPosition
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.map.MapProjection
import io.github.rupakc.travelplanner.core.domain.map.ScratchMapRules
import io.github.rupakc.travelplanner.core.domain.map.WorldMapData
import io.github.rupakc.travelplanner.core.ui.R
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The bundled map parsed once per process and its country paths (in map units) built once; both off the main thread. */
private object WorldMapCache {
    @Volatile private var cached: Pair<WorldMapData, Map<String, Path>>? = null

    suspend fun get(context: android.content.Context): Pair<WorldMapData, Map<String, Path>> =
        cached ?: withContext(Dispatchers.Default) {
            context.resources.openRawResource(R.raw.world_map).use { stream ->
                val data = WorldMapData.parse(stream)
                val paths =
                    data.countries.mapValues { (_, rings) ->
                        Path().apply {
                            rings.forEach { ring ->
                                moveTo(ring.xy[0], ring.xy[1])
                                for (i in 1 until ring.size) lineTo(ring.xy[i * 2], ring.xy[i * 2 + 1])
                                close()
                            }
                        }
                    }
                (data to paths).also { cached = it }
            }
        }
}

/** A country's localized name from its ISO 3166 code, with no per-country strings. */
fun countryDisplayName(code: String, locale: Locale = Locale.getDefault()): String = Locale("", code).getDisplayCountry(locale).ifBlank { code }

/**
 * An offline world map with the [visited] countries (ISO 3166-1 alpha-2 codes) filled in the accent colour. Tapping a country says its
 * localized name and whether it is visited. The flag/name list stays beside it as the accessibility fallback; this canvas carries one
 * "N countries visited" description. The reveal fade is skipped under reduced motion.
 */
@Composable
fun ScratchMap(visited: Collection<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loaded by produceState<Pair<WorldMapData, Map<String, Path>>?>(null) { value = WorldMapCache.get(context.applicationContext) }
    val filled = remember(visited, loaded) { loaded?.let { ScratchMapRules.filled(it.first, visited) } ?: emptySet() }
    val reduceMotion = rememberReduceMotion()
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(loaded != null) { if (loaded != null && !reduceMotion) reveal.animateTo(1f, tween(600)) }
    var selected by remember { mutableStateOf<String?>(null) }
    var projection by remember { mutableStateOf(MapProjection(0f, 0f)) }
    val description = pluralStringResource(R.plurals.scratch_map_cd, filled.size, filled.size)
    val land = TravelTheme.colors.surfaceVariant
    val accent = TravelTheme.colors.primary
    val edge = TravelTheme.colors.outlineVariant
    val pick = TravelTheme.colors.tertiary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .onSizeChanged { projection = MapProjection(it.width.toFloat(), it.height.toFloat()) }
                .alpha(reveal.value)
                .semantics { contentDescription = description }
                .tpTapPosition("scratch_map") { at -> selected = loaded?.let { ScratchMapRules.tap(it.first, projection, at.x, at.y) } },
        ) {
            val p = MapProjection(size.width, size.height)
            val paths = loaded?.second ?: return@Canvas
            withTransform({
                translate(p.offsetX, p.offsetY)
                scale(p.scale, p.scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
            }) {
                val hair = Stroke(width = 0.6f / p.scale.coerceAtLeast(0.01f))
                loaded?.first?.drawOrder?.forEach { code ->
                    val path = paths[code] ?: return@forEach
                    drawPath(path, if (code in filled) accent else land)
                    drawPath(
                        path,
                        if (code ==
                            selected
                        ) {
                            pick
                        } else {
                            edge
                        },
                        style = if (code == selected) Stroke(width = 2f / p.scale.coerceAtLeast(0.01f)) else hair,
                    )
                }
            }
        }
        val code = selected
        androidx.compose.material3.Text(
            text =
                when {
                    code == null -> stringResource(R.string.scratch_map_hint)
                    code in filled -> stringResource(R.string.scratch_map_visited, countryDisplayName(code))
                    else -> stringResource(R.string.scratch_map_not_visited, countryDisplayName(code))
                },
            style = TravelTheme.typography.labelMedium,
            color = TravelTheme.colors.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
