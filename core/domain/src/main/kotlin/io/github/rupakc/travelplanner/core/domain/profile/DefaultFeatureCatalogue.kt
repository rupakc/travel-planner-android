package io.github.rupakc.travelplanner.core.domain.profile

import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureDescriptor
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.ACCESSIBLE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.ADVENTURE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.BUDGET_BACKPACKER
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.BUSINESS
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.CULTURE_FOODIE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.FAMILY
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.LUXURY
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.RELAXATION
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile

/**
 * The feature catalogue (spec §6.3): every [FeatureId], in display order, with its string key
 * (`feature_<id>_title`; description `feature_<id>_description`), icon name and the traveller
 * types it is on by default for. Mirrored in docs/Onboarding-and-Traveller-Types.md.
 */
object DefaultFeatureCatalogue : FeatureCatalogue {
    private val ALL_TYPES = TravellerType.entries.toSet()

    override val all: List<FeatureDescriptor> =
        listOf(
            entry(FeatureId.CHAT, "chat", ALL_TYPES),
            entry(FeatureId.VISA_CHECKER, "verified", ALL_TYPES),
            entry(FeatureId.DEAL_ALERTS, "notifications", setOf(BUDGET_BACKPACKER, FAMILY, BUSINESS)),
            entry(FeatureId.BEST_VALUE, "thumb_up", setOf(BUDGET_BACKPACKER, FAMILY)),
            entry(FeatureId.BUDGET_PLANNER, "wallet", setOf(BUDGET_BACKPACKER, FAMILY)),
            entry(FeatureId.COMPARE, "compare", setOf(BUDGET_BACKPACKER, CULTURE_FOODIE, RELAXATION, LUXURY)),
            entry(FeatureId.SURPRISE_ME, "surprise", setOf(BUDGET_BACKPACKER, ADVENTURE, CULTURE_FOODIE, RELAXATION)),
            entry(FeatureId.IMPORT_LINK, "link", setOf(ADVENTURE, CULTURE_FOODIE, LUXURY)),
            entry(FeatureId.NEAR_ME, "near_me", setOf(BUDGET_BACKPACKER, ADVENTURE, CULTURE_FOODIE, BUSINESS)),
            entry(FeatureId.ITINERARY_EDITOR, "edit_calendar", setOf(FAMILY, LUXURY, CULTURE_FOODIE, BUSINESS, RELAXATION)),
            entry(FeatureId.WALKING_TOURS, "walk", setOf(BUDGET_BACKPACKER, CULTURE_FOODIE)),
            entry(FeatureId.EVENTS, "event", setOf(BUDGET_BACKPACKER, LUXURY, CULTURE_FOODIE)),
            entry(FeatureId.PACKING_LIST, "luggage", setOf(FAMILY, ADVENTURE, RELAXATION, ACCESSIBLE)),
            entry(FeatureId.EMERGENCY_CARD, "emergency", setOf(FAMILY, ADVENTURE, ACCESSIBLE)),
            entry(FeatureId.TRIP_HEALTH_CHECK, "health", setOf(FAMILY, LUXURY, BUSINESS, ACCESSIBLE)),
            entry(FeatureId.FOREX, "currency", setOf(BUDGET_BACKPACKER, LUXURY, BUSINESS)),
            entry(FeatureId.SIM, "sim", setOf(BUDGET_BACKPACKER, ADVENTURE, BUSINESS)),
            entry(FeatureId.ACCESSIBILITY_INFO, "accessibility", setOf(ACCESSIBLE)),
        )

    override fun defaultsFor(types: Collection<TravellerType>): Set<FeatureId> =
        all.filter { d -> types.any(d.defaultOnFor::contains) }.mapTo(linkedSetOf()) { it.id }

    /** Descriptor for [id] (every id has one). */
    fun descriptor(id: FeatureId): FeatureDescriptor = all.first { it.id == id }

    private fun entry(id: FeatureId, iconName: String, defaultOnFor: Set<TravellerType>) =
        FeatureDescriptor(id, titleKey = "feature_${id.id}_title", iconName = iconName, defaultOnFor = defaultOnFor)
}

/** String-resource name of the one-line description (`feature_<id>_description`). */
val FeatureDescriptor.descriptionKey: String get() = "feature_${id.id}_description"

/**
 * Surfaces that are never hidden and are not in the catalogue (spec §6.3).
 * Override ids naming them are ignored because they are not [FeatureId]s.
 */
enum class CoreSurface { SEARCH, RESULTS, MY_PLAN, SETTINGS }

/**
 * Visible optional features for a stored [profile] (spec §6.3): defaults for its types ∪ enabled − disabled.
 * Before onboarding (no type yet) nothing is personalised, so every feature starts visible
 * (still minus the user's explicit disables).
 */
fun FeatureCatalogue.visibleFeatures(profile: TravellerProfile, overrides: FeatureOverrides): Set<FeatureId> {
    val types = profile.types()
    if (types.isNotEmpty()) return visible(types, overrides)
    val disabled = overrides.disabled.mapNotNull(FeatureId::fromId).toSet()
    return all.mapTo(linkedSetOf()) { it.id } - disabled
}

/** Overrides that turn the visible set for [types] into exactly [wanted] (used by the onboarding feature picker). */
fun FeatureCatalogue.overridesFor(types: Collection<TravellerType>, wanted: Set<FeatureId>): FeatureOverrides {
    val defaults = defaultsFor(types)
    return FeatureOverrides(
        enabled = (wanted - defaults).mapTo(sortedSetOf()) { it.id },
        disabled = (defaults - wanted).mapTo(sortedSetOf()) { it.id },
    )
}
