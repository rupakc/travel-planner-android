package io.github.rupakc.travelplanner.feature.search.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.feature.search.importlink.ContentResolverScreenshotReader
import io.github.rupakc.travelplanner.feature.search.importlink.ScreenshotReader
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@Module
@InstallIn(SingletonComponent::class)
internal interface SearchModule {
    /** The onboarding team binds the catalogue; until then (or in tests) every mode chip shows. */
    @BindsOptionalOf
    fun featureCatalogue(): FeatureCatalogue

    @Binds
    fun screenshotReader(impl: ContentResolverScreenshotReader): ScreenshotReader

    @Binds
    fun featureVisibility(impl: CatalogueFeatureVisibility): FeatureVisibility
}

/** The features visible for this user (spec §6.3), or null when no catalogue is bound (show everything). */
fun interface FeatureVisibility {
    fun visible(): Flow<Set<FeatureId>?>
}

/**
 * [FeatureVisibility] from the device-local traveller profile + overrides and the (optional) catalogue,
 * with the semantics of A1's `FeatureCatalogue.visibleFeatures(profile, overrides)`: personalised as soon as the
 * profile has a type; before that (no type) every feature is visible, minus the user's explicit disables.
 */
internal class CatalogueFeatureVisibility
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val catalogue: Optional<FeatureCatalogue>,
    ) : FeatureVisibility {
        override fun visible(): Flow<Set<FeatureId>?> {
            val impl = catalogue.orElse(null) ?: return flowOf(null)
            return combine(profileRepository.profile, profileRepository.featureOverrides) { profile, overrides ->
                visibleFeatures(impl, profile, overrides)
            }
        }
    }

/** Pure rule behind [CatalogueFeatureVisibility] (mirror of A1's `visibleFeatures`; switch to it once both are on main). */
internal fun visibleFeatures(catalogue: FeatureCatalogue, profile: TravellerProfile, overrides: FeatureOverrides): Set<FeatureId> {
    val types =
        (listOfNotNull(profile.primaryType) + profile.secondaryTypes)
            .mapNotNull { name -> TravellerType.entries.firstOrNull { it.name == name } }
    if (types.isNotEmpty()) return catalogue.visible(types, overrides)
    val disabled = overrides.disabled.mapNotNull(FeatureId::fromId).toSet()
    return catalogue.all.map { it.id }.toSet() - disabled
}
