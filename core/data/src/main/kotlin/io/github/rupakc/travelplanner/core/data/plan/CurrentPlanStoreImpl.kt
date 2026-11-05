package io.github.rupakc.travelplanner.core.data.plan

import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.database.PlanDraftDao
import io.github.rupakc.travelplanner.core.database.PlanDraftEntity
import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.SelectionKeys
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.search.searchKey
import io.github.rupakc.travelplanner.core.model.section.SectionId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Hilt singleton [CurrentPlanStore]. State lives in memory and is persisted to Room so it survives
 * process death (review I7):
 * - the draft *without* its search data is written on every change (latest wins, collected from the state,
 *   so a stale value can never overwrite a newer one);
 * - the large search-data snapshot lives in its own row, written only when it changes, debounced, and is
 *   dropped (with a logged warning) above [MAX_SEARCH_DATA_BYTES] instead of risking an oversized row;
 * - every Room call is guarded: a failed write is logged and the next change retries; a failed read
 *   deletes the bad rows and starts empty.
 * `save`/`load`/`reset` are serialised with a mutex (review I5). Every whole-draft replacement (`newPlan`,
 * `replace`, `load`, `reset`; not the undo `restore`) bumps a generation counter under [lock]; `save` applies the returned
 * plan id only when the generation is unchanged, so a slow save can never stamp its id on a different trip
 * (architecture review I2). Replacements don't wait for the save mutex, so opening a trip never blocks
 * behind a slow network save.
 */
@OptIn(FlowPreview::class)
@Singleton
class CurrentPlanStoreImpl
    @Inject
    constructor(
        private val plansRepository: PlansRepository,
        private val draftDao: PlanDraftDao,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : CurrentPlanStore {
        private val state = MutableStateFlow(PlanDraft())
        override val draft: StateFlow<PlanDraft> = state.asStateFlow()
        private val mutex = Mutex()
        private val lock = Any()
        private var generation = 0L
        private val restoreJob: Job = scope.launch { restore() }

        init {
            scope.launch {
                restoreJob.join()
                launch {
                    state.map { it.copy(searchData = null) }.distinctUntilChanged().collectLatest { persistDraft(it) }
                }
                launch {
                    state.map { it.searchData }.distinctUntilChanged {
                        a,
                        b,
                        ->
                        a === b
                    }.debounce(SEARCH_DATA_DEBOUNCE_MS).collectLatest { persistSearchData(it) }
                }
            }
        }

        override fun toggle(item: PlanItem) = state.update { it.copy(selections = PlanSelectionRules.toggle(it.selections, item), dirty = true) }

        override fun isSelected(item: PlanItem): Boolean = PlanSelectionRules.isSelected(state.value.selections, item)

        override fun setSelections(selections: PlanSelections) = state.update { it.copy(selections = selections, dirty = true) }

        override fun rename(name: String) = state.update { it.copy(name = name, dirty = true) }

        override fun attachSearch(request: TravelSearchRequest, data: JsonObject?) =
            state.update { it.copy(searchRequest = request, searchData = data, searchKey = request.searchKey()) }

        override fun newPlan() = replace(PlanDraft()) // searchKey = null

        override fun replace(draft: PlanDraft) {
            synchronized(lock) {
                generation++
                state.value = draft
            }
        }

        override fun load(plan: Plan): PlanDraft = draftOf(plan).also(::replace)

        override suspend fun reset() {
            restoreJob.join()
            mutex.withLock {
                replace(PlanDraft())
                guarded("clear the draft") { draftDao.clear() }
            }
        }

        private fun draftOf(plan: Plan): PlanDraft {
            val request = plan.searchRequest
            return PlanDraft(
                planId = plan.id,
                name = plan.name,
                searchRequest = request,
                searchData = sectionsOf(plan),
                selections = plan.selections,
                dirty = false,
                searchKey = request?.searchKey(),
                loadedPlan = true,
            )
        }

        override suspend fun load(planId: Long): AppResult<PlanDraft> {
            restoreJob.join()
            return mutex.withLock {
                plansRepository.get(planId).map(::draftOf).onSuccess(::replace)
            }
        }

        override suspend fun save(name: String?): AppResult<Plan> {
            restoreJob.join()
            return mutex.withLock {
                val (sent, sentGeneration) = synchronized(lock) { state.value to generation }
                val planName = name?.takeIf(String::isNotBlank) ?: sent.name?.takeIf(String::isNotBlank) ?: PlanDraft.DEFAULT_NAME
                val planId = sent.planId
                val result =
                    if (planId == null) {
                        val searchData =
                            sent.searchRequest
                                ?.let { TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), it) as JsonObject }
                                ?: EmptyJsonObject
                        plansRepository.create(planName, searchData, withItinerarySnapshot(sent))
                    } else {
                        plansRepository.update(planId, planName, withItinerarySnapshot(sent))
                    }
                result.onSuccess { plan ->
                    // Apply only identity fields: edits made while the request was in flight are kept and stay dirty.
                    // A different draft (another trip opened, a new search) never gets this plan's id.
                    synchronized(lock) {
                        if (generation != sentGeneration) return@onSuccess
                        state.update { now ->
                            val nameUnchanged = now.name == sent.name
                            now.copy(
                                planId = plan.id,
                                name = if (nameUnchanged) plan.name else now.name,
                                dirty = now.selections != sent.selections || !nameUnchanged,
                            )
                        }
                    }
                }
            }
        }

        override fun snapshot(): PlanDraft = state.value

        /** Undo within the same draft: keeps the generation, so an in-flight save of this trip still applies. */
        override fun restore(snapshot: PlanDraft) {
            state.value = snapshot
        }

        /**
         * Sections recoverable from a saved plan, in the stream-snapshot shape (`{"itinerary":{…},…}`).
         * A saved plan's `search_data` is the request (web parity), so normally only the itinerary snapshot
         * (`selections.itinerary`, written on save) is available; any section objects found in `search_data`
         * (keys that are section wire names) are taken too. Null when nothing is recoverable.
         */
        private fun sectionsOf(plan: Plan): JsonObject? {
            val sections =
                buildJsonObject {
                    plan.searchData.forEach { (key, value) -> if (SectionId.fromWire(key) != null && value is JsonObject) put(key, value) }
                    val itinerary = plan.selections.itinerarySnapshot
                    if (itinerary != null && SelectionKeys.ITINERARY !in plan.searchData) put(SelectionKeys.ITINERARY, itinerary)
                }
            return sections.takeIf { it.isNotEmpty() }
        }

        /** Web parity: on save the live itinerary is snapshotted into `selections.itinerary` so a saved plan is self-contained. */
        private fun withItinerarySnapshot(draft: PlanDraft): PlanSelections {
            val live = draft.searchData?.get(SelectionKeys.ITINERARY) as? JsonObject ?: return draft.selections
            val snapshot =
                buildJsonObject {
                    live["days"]?.let { put("days", it) }
                    live["total_estimated_cost_usd"]?.let { put("total_estimated_cost_usd", it) }
                }
            return if (snapshot.isEmpty()) draft.selections else draft.selections.copy(itinerarySnapshot = snapshot)
        }

        private suspend fun restore() {
            val saved =
                guarded("restore the draft") {
                    val draftRow = draftDao.getRow(PlanDraftEntity.SINGLE_ROW) ?: return@guarded null
                    val restored = TravelJson.decodeFromString(PlanDraft.serializer(), draftRow.json)
                    val search =
                        draftDao.getRow(PlanDraftEntity.SEARCH_ROW)?.json
                            ?.let { TravelJson.parseToJsonElement(it) as? JsonObject }
                    restored.copy(searchData = search ?: restored.searchData)
                }
            if (saved == null) {
                // Nothing stored, or the rows were unreadable: start empty and drop whatever is there.
                guarded("drop unreadable draft rows") { if (draftDao.getRow(PlanDraftEntity.SINGLE_ROW) != null) draftDao.clear() }
            } else {
                state.compareAndSet(PlanDraft(), saved)
            }
        }

        private suspend fun persistDraft(draft: PlanDraft) {
            guarded("persist the draft") {
                draftDao.save(PlanDraftEntity(PlanDraftEntity.SINGLE_ROW, TravelJson.encodeToString(PlanDraft.serializer(), draft)))
            }
        }

        private suspend fun persistSearchData(data: JsonObject?) {
            guarded("persist the search snapshot") {
                if (data == null) {
                    draftDao.deleteRow(PlanDraftEntity.SEARCH_ROW)
                    return@guarded
                }
                val json = data.toString()
                if (json.length > MAX_SEARCH_DATA_BYTES) {
                    AppLogger.w(TAG, "Search snapshot is ${json.length} chars; not persisting it (limit $MAX_SEARCH_DATA_BYTES)")
                    draftDao.deleteRow(PlanDraftEntity.SEARCH_ROW)
                } else {
                    draftDao.save(PlanDraftEntity(PlanDraftEntity.SEARCH_ROW, json))
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T> guarded(what: String, block: suspend () -> T): T? =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Could not $what", e)
                null
            }

        companion object {
            private const val TAG = "CurrentPlanStore"
            const val SEARCH_DATA_DEBOUNCE_MS = 500L

            /** Above this the snapshot is not persisted (SQLite CursorWindow is ~2 MB). Chars ≈ bytes for this ASCII-escaped JSON. */
            const val MAX_SEARCH_DATA_BYTES = 1_500_000
        }
    }
