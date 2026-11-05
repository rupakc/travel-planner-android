package io.github.rupakc.travelplanner.feature.settings

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureDescriptor
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.feature.settings.root.AppLocales

internal class FakeCatalogue(private val defaults: Set<FeatureId>) : FeatureCatalogue {
    override val all = emptyList<FeatureDescriptor>()

    override fun defaultsFor(types: Collection<TravellerType>) = if (types.isEmpty()) emptySet() else defaults
}

internal class FakeAppLocales(override val perAppSupported: Boolean = true) : AppLocales {
    val applied = mutableListOf<String?>()

    override fun apply(tag: String?) {
        applied += tag
    }

    override fun current(): String? = applied.lastOrNull()
}

internal val TestBuildInfo =
    AppBuildInfo(defaultBaseUrl = "http://10.0.2.2:8001", flavor = "local", versionName = "1.2.3", debug = true, cleartextAllowed = true)

/** Scriptable preferences repository (results per call). */
internal class ScriptedPreferencesRepository(
    var getResult: AppResult<Preferences> = AppResult.Success(Preferences(username = "alice", nationality = "American")),
    var saveResult: ((Preferences) -> AppResult<Preferences>)? = null,
    var tasteResult: AppResult<TasteProfile> = AppResult.Success(TasteProfile()),
    var resetResult: AppResult<Int> = AppResult.Success(0),
) : PreferencesRepository {
    val saved = mutableListOf<Preferences>()
    var resetCalls = 0

    override suspend fun get() = getResult

    override suspend fun save(preferences: Preferences): AppResult<Preferences> {
        saved += preferences
        return saveResult?.invoke(preferences) ?: AppResult.Success(preferences)
    }

    override suspend fun update(transform: (Preferences) -> Preferences): AppResult<Preferences> = AppResult.Failure(AppError.Unknown())

    override suspend fun tasteProfile() = tasteResult

    override suspend fun resetTasteProfile(): AppResult<Int> {
        resetCalls++
        return resetResult
    }
}
