package io.github.rupakc.travelplanner.core.data.impl

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
import io.github.rupakc.travelplanner.core.network.api.AlertsV2Api
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertsV2RepositoryImpl
    @Inject
    constructor(private val api: AlertsV2Api) : AlertsV2Repository {
        override suspend fun summary(): AppResult<Savings> = apiCall { api.summary() }

        override suspend fun story(kind: AlertKind, watchId: Long): AppResult<PriceStory> = apiCall { api.story(kind.wire, watchId) }

        override suspend fun advice(kind: AlertKind, watchId: Long): AppResult<AdviceTip> = apiCall { api.advice(kind.wire, watchId) }

        override suspend fun quickWatch(request: QuickWatchRequest): AppResult<QuickWatchResponse> = apiCall { api.quickWatch(request) }

        override suspend fun suggestions(planId: Long?): AppResult<SuggestionsResponse> = apiCall { api.suggestions(planId) }

        override suspend fun preferences(): AppResult<AlertPrefs> = apiCall { api.preferences() }

        override suspend fun updatePreferences(update: AlertPrefsUpdate): AppResult<AlertPrefs> = apiCall { api.updatePreferences(update) }
    }
