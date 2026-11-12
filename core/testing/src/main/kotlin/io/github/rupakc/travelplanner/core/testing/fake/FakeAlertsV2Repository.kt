package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AlertsV2Repository
import io.github.rupakc.travelplanner.core.model.alerts.AdviceTip
import io.github.rupakc.travelplanner.core.model.alerts.AlertKind
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefsUpdate
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.model.alerts.SuggestionsResponse

/** Scriptable [AlertsV2Repository]: set the `*Result` of the call under test; unset ones answer [NotStubbed]. Calls are recorded. */
class FakeAlertsV2Repository : AlertsV2Repository {
    var summaryResult: AppResult<Savings> = AppResult.Success(Savings())
    var storyResult: AppResult<PriceStory> = NotStubbed
    var adviceResult: AppResult<AdviceTip> = NotStubbed
    var quickWatchResult: AppResult<QuickWatchResponse> = NotStubbed
    var suggestionsResult: AppResult<SuggestionsResponse> = AppResult.Success(SuggestionsResponse())
    var preferencesResult: AppResult<AlertPrefs> = AppResult.Success(AlertPrefs())
    var updatePreferencesResult: AppResult<AlertPrefs>? = null

    val storyCalls = mutableListOf<Pair<AlertKind, Long>>()
    val adviceCalls = mutableListOf<Pair<AlertKind, Long>>()
    val quickWatchCalls = mutableListOf<QuickWatchRequest>()
    val suggestionCalls = mutableListOf<Long?>()
    val preferenceUpdates = mutableListOf<AlertPrefsUpdate>()

    override suspend fun summary(): AppResult<Savings> = summaryResult

    override suspend fun story(kind: AlertKind, watchId: Long): AppResult<PriceStory> = storyResult.also { storyCalls += kind to watchId }

    override suspend fun advice(kind: AlertKind, watchId: Long): AppResult<AdviceTip> = adviceResult.also { adviceCalls += kind to watchId }

    override suspend fun quickWatch(request: QuickWatchRequest): AppResult<QuickWatchResponse> = quickWatchResult.also { quickWatchCalls += request }

    override suspend fun suggestions(planId: Long?): AppResult<SuggestionsResponse> = suggestionsResult.also { suggestionCalls += planId }

    override suspend fun preferences(): AppResult<AlertPrefs> = preferencesResult

    override suspend fun updatePreferences(update: AlertPrefsUpdate): AppResult<AlertPrefs> {
        preferenceUpdates += update
        return updatePreferencesResult ?: AppResult.Success(preferencesResult.let { (it as? AppResult.Success)?.value ?: AlertPrefs() })
    }
}
