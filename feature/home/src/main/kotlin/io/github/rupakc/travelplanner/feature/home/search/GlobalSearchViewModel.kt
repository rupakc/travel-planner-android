package io.github.rupakc.travelplanner.feature.home.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.data.profile.FeatureVisibility
import io.github.rupakc.travelplanner.core.data.repository.ChatRepository
import io.github.rupakc.travelplanner.core.data.repository.ChatThread
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.RecentSearchesRepository
import io.github.rupakc.travelplanner.core.data.repository.StoredChatMessage
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearch
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchResults
import io.github.rupakc.travelplanner.core.domain.globalsearch.PreparedItem
import io.github.rupakc.travelplanner.core.domain.globalsearch.SearchableItem
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsEntry
import io.github.rupakc.travelplanner.core.domain.place.placeName
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsEntryRoute
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.feature.home.HomePlaces
import io.github.rupakc.travelplanner.feature.home.destination
import io.github.rupakc.travelplanner.feature.home.routeLabel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class GlobalSearchUiState(
    val query: String = "",
    /** Selected kind chips; empty = all. */
    val filter: Set<GlobalSearchKind> = emptySet(),
    val results: GlobalSearchResults = GlobalSearchResults(),
    /** Features currently hidden: opening one turns it on. */
    val hiddenFeatures: Set<String> = emptySet(),
)

sealed interface GlobalSearchEvent {
    data class QueryChanged(val query: String) : GlobalSearchEvent

    data class ToggleFilter(val kind: GlobalSearchKind) : GlobalSearchEvent

    data object ClearFilter : GlobalSearchEvent

    data class Open(val item: SearchableItem) : GlobalSearchEvent
}

/**
 * Unified search: indexes saved trips, chat threads (title + messages from Room), recent
 * searches, catalogue features and settings rows, all offline (repository caches), and ranks
 * them with the pure [GlobalSearch] as the user types.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class GlobalSearchViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        plansRepository: PlansRepository,
        private val chatRepository: ChatRepository,
        recentSearchesRepository: RecentSearchesRepository,
        private val profileRepository: ProfileRepository,
        featureVisibility: FeatureVisibility,
        private val labels: SearchLabels,
        dispatchers: AppDispatchers,
        private val placeNameLookup: PlaceNameLookup,
    ) : ViewModel() {
        private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
        private val filter =
            savedStateHandle.getStateFlow(KEY_FILTER, "").map { raw ->
                raw.split(',').mapNotNull { name -> GlobalSearchKind.entries.firstOrNull { it.name == name } }.toSet()
            }

        private val hidden: Flow<Set<String>> =
            featureVisibility.visible().map { visible -> FeatureId.entries.filterNot { it in visible }.map { it.id }.toSet() }

        /**
         * Chat threads with their messages. One live query (threads, debounced so streaming writes don't
         * rebuild the index per token); messages are read once per rebuild, not observed per thread.
         */
        private val chatItems: Flow<List<SearchableItem>> =
            chatRepository.threads
                .debounce(CHAT_DEBOUNCE_MILLIS)
                .mapLatest { threads -> threads.take(MAX_THREADS).map { t -> chatItem(t, chatRepository.messages(t.id).first()) } }

        /** City names for the trips' and recent searches' airport codes (shared lookup): titles name the city, both match. */
        private val placeNames: Flow<Map<String, String>> =
            combine(plansRepository.plans, recentSearchesRepository.recent) { plans, recent -> HomePlaces.of(plans, recent) }
                .distinctUntilChanged()
                .flatMapLatest { placeNameLookup.namesFor(it) }
                .onStart { emit(emptyMap()) }

        /** The index, normalised once per source change (not per keystroke). */
        private val index: Flow<List<PreparedItem>> =
            combine(plansRepository.plans, chatItems, recentSearchesRepository.recent, placeNames) { plans, chats, recent, names ->
                GlobalSearch.prepare(plans.map { tripItem(it, names) } + chats + recent.map { recentItem(it, names) } + staticItems())
            }.flowOn(dispatchers.default)

        val state: StateFlow<GlobalSearchUiState> =
            combine(query.debounce(DEBOUNCE_MILLIS), filter, index, hidden) { q, f, items, hiddenIds ->
                GlobalSearchUiState(query = q, filter = f, results = GlobalSearch.search(q, items, f), hiddenFeatures = hiddenIds)
            }.flowOn(dispatchers.default)
                .combine(query) { s, typed -> s.copy(query = typed) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GlobalSearchUiState(query = query.value))

        private val navigationChannel = Channel<AppRoute>(Channel.BUFFERED)

        /** One-shot navigation requests (collected by the route). */
        val navigation: Flow<AppRoute> = navigationChannel.receiveAsFlow()

        fun onEvent(event: GlobalSearchEvent) {
            when (event) {
                is GlobalSearchEvent.QueryChanged -> {
                    savedStateHandle[KEY_QUERY] = event.query
                }

                is GlobalSearchEvent.ToggleFilter -> {
                    val current = savedStateHandle.get<String>(KEY_FILTER).orEmpty().split(',').filter(String::isNotEmpty).toSet()
                    val next = if (event.kind.name in current) current - event.kind.name else current + event.kind.name
                    savedStateHandle[KEY_FILTER] = next.joinToString(",")
                }

                GlobalSearchEvent.ClearFilter -> {
                    savedStateHandle[KEY_FILTER] = ""
                }

                is GlobalSearchEvent.Open -> {
                    open(event.item)
                }
            }
        }

        private fun open(item: SearchableItem) {
            viewModelScope.launch {
                val route: AppRoute? =
                    when (item.kind) {
                        GlobalSearchKind.TRIP -> {
                            item.id.toLongOrNull()?.let(::PlanDetailRoute)
                        }

                        GlobalSearchKind.CHAT -> {
                            item.id.toLongOrNull()?.let { ChatRoute(it) }
                        }

                        GlobalSearchKind.RECENT_SEARCH -> {
                            SearchRoute(recentSearchId = item.id)
                        }

                        GlobalSearchKind.SETTING -> {
                            SettingsEntryRoute(item.id)
                        }

                        GlobalSearchKind.FEATURE -> {
                            FeatureId.fromId(item.id)?.let { feature ->
                                if (feature.id in hidden.first()) profileRepository.setFeature(feature.id, true)
                                feature.destination(upcomingPlanId = null)
                            }
                        }
                    }
                route?.let { navigationChannel.send(it) }
            }
        }

        private fun tripItem(plan: Plan, names: Map<String, String>): SearchableItem {
            val request = plan.searchRequest
            val codes = request?.destinations.orEmpty().ifEmpty { listOfNotNull(request?.destination) }
            val destinations = codes.map { placeName(it, names) }
            val subtitle = listOfNotNull(destinations.joinToString(" → ").takeIf(String::isNotEmpty), request?.departureDate).joinToString(", ")
            return SearchableItem(
                kind = GlobalSearchKind.TRIP,
                id = plan.id.toString(),
                title = plan.name.ifBlank { destinations.lastOrNull().orEmpty() },
                subtitle = subtitle.ifEmpty { null },
                keywords = (listOfNotNull(request?.origin, request?.origin?.let { placeName(it, names) }) + codes + destinations).distinct(),
            )
        }

        private fun chatItem(thread: ChatThread, messages: List<StoredChatMessage>): SearchableItem =
            SearchableItem(
                kind = GlobalSearchKind.CHAT,
                id = thread.id.toString(),
                title = thread.title,
                subtitle = messages.lastOrNull()?.content?.lineSequence()?.firstOrNull()?.take(PREVIEW_CHARS),
                keywords = messages.map { it.content.take(MAX_MESSAGE_CHARS) },
            )

        private fun recentItem(recent: RecentSearch, names: Map<String, String>): SearchableItem {
            val s = recent.search
            return SearchableItem(
                kind = GlobalSearchKind.RECENT_SEARCH,
                id = recent.id,
                title = s.routeLabel(names),
                subtitle = listOfNotNull(s.departureDate, s.returnDate).filter(String::isNotBlank).joinToString(" – ").ifEmpty { null },
                keywords = s.interests + s.routeLabel(),
            )
        }

        private fun staticItems(): List<SearchableItem> =
            FeatureId.entries.map { f ->
                SearchableItem(GlobalSearchKind.FEATURE, f.id, labels.featureTitle(f), labels.featureDescription(f))
            } +
                SettingsEntry.entries.map { e ->
                    SearchableItem(GlobalSearchKind.SETTING, e.key, labels.settingTitle(e), labels.settingSection(e), labels.settingKeywords(e))
                }

        private companion object {
            const val KEY_QUERY = "global_search_query"
            const val KEY_FILTER = "global_search_filter"
            const val DEBOUNCE_MILLIS = 150L
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val MAX_THREADS = 100
            const val CHAT_DEBOUNCE_MILLIS = 300L
            const val PREVIEW_CHARS = 120
            const val MAX_MESSAGE_CHARS = 2_000
        }
    }
