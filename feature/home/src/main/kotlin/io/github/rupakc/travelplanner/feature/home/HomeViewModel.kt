package io.github.rupakc.travelplanner.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.errorOrNull
import io.github.rupakc.travelplanner.core.common.getOrNull
import io.github.rupakc.travelplanner.core.data.profile.FeatureVisibility
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.RecentSearchesRepository
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.types
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Home screen state; every value comes from a repository (nothing invented). */
@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val userName: String? = null,
    /** Primary first, then secondaries; empty when the quiz was skipped. */
    val travellerTypes: List<TravellerType> = emptyList(),
    val upcomingTrip: UpcomingTrip? = null,
    val tiles: List<FeatureTile> = emptyList(),
    val becauseYouSearched: BecauseYouSearched? = null,
    val recentSearches: List<RecentSearch> = emptyList(),
    /** Top Taste Graph values (`GET /api/taste-profile`). */
    val tasteHighlights: List<String> = emptyList(),
    val refreshing: Boolean = false,
    /** Last refresh failed; cached data is still shown. */
    val refreshError: AppError? = null,
)

/** Everything the Home screen can ask for. Navigation events are handled by the route. */
sealed interface HomeEvent {
    data object Refresh : HomeEvent

    data object DismissError : HomeEvent

    data object OpenSearch : HomeEvent

    data object PlanTrip : HomeEvent

    data object OpenTravellerType : HomeEvent

    data object OpenTasteGraph : HomeEvent

    data class OpenTrip(val planId: Long) : HomeEvent

    data class OpenFeature(val feature: FeatureId) : HomeEvent

    data class OpenRecentSearch(val id: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        profileRepository: ProfileRepository,
        private val plansRepository: PlansRepository,
        private val recentSearchesRepository: RecentSearchesRepository,
        private val preferencesRepository: PreferencesRepository,
        private val catalogue: FeatureCatalogue,
        featureVisibility: FeatureVisibility,
        private val clock: AppClock,
    ) : ViewModel() {
        private val taste = MutableStateFlow<TasteProfile?>(null)
        private val refresh = MutableStateFlow(RefreshState())

        private val profileTypes =
            profileRepository.profile.map { it.types() }

        /** The shared visibility rule (core:data), same as Search, Settings and Results. */
        private val visible = featureVisibility.visible()

        private val personal =
            combine(visible, recentSearchesRepository.recent, taste) { features, recent, tasteProfile ->
                val icons = catalogue.iconNames()
                Personal(
                    tiles =
                        HomePersonalisation.orderTiles(features, HomePersonalisation.reasons(recent, tasteProfile)).map {
                            it.copy(iconName = icons[it.feature])
                        },
                    because =
                        HomePersonalisation.becauseYouSearched(recent, features)?.let { b ->
                            b.copy(tiles = b.tiles.map { it.copy(iconName = icons[it.feature]) })
                        },
                    recent = recent,
                    taste = HomePersonalisation.tasteHighlights(tasteProfile),
                )
            }

        val state: StateFlow<HomeUiState> =
            combine(authRepository.session, profileTypes, plansRepository.plans, personal, refresh) { session, types, plans, p, r ->
                HomeUiState(
                    loading = false,
                    userName = session?.user?.username,
                    travellerTypes = types,
                    upcomingTrip = UpcomingTrips.nearest(plans, clock.today()),
                    tiles = p.tiles,
                    becauseYouSearched = p.because,
                    recentSearches = p.recent,
                    tasteHighlights = p.taste,
                    refreshing = r.running,
                    refreshError = r.error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

        init {
            refresh()
        }

        fun onEvent(event: HomeEvent) {
            when (event) {
                HomeEvent.Refresh -> refresh()
                HomeEvent.DismissError -> refresh.update { it.copy(error = null) }
                else -> Unit // navigation: handled by HomeRoute
            }
        }

        private fun refresh() {
            if (refresh.value.running) return
            refresh.value = RefreshState(running = true)
            viewModelScope.launch {
                val plans = async { plansRepository.refresh() }
                val recent = async { recentSearchesRepository.refresh() }
                val tasteResult = async { preferencesRepository.tasteProfile() }
                // Taste is optional decoration: a failure just leaves it empty.
                (tasteResult.await() as? AppResult.Success)?.let { taste.value = it.value }
                val results = listOf(plans.await(), recent.await())
                val error = results.firstNotNullOfOrNull { it.errorOrNull() }.takeIf { results.all { r -> r.getOrNull() == null } }
                refresh.value = RefreshState(running = false, error = error)
            }
        }

        private data class RefreshState(val running: Boolean = false, val error: AppError? = null)

        private data class Personal(
            val tiles: List<FeatureTile>,
            val because: BecauseYouSearched?,
            val recent: List<RecentSearch>,
            val taste: List<String>,
        )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
