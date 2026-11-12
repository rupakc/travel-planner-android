package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.GameEventsImpl
import io.github.rupakc.travelplanner.core.data.impl.GameRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.GameRepository
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository

/** Game reads and the delta bus (kept apart from [DataModule]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class GameDataModule {
    @Binds abstract fun game(impl: GameRepositoryImpl): GameRepository

    @Binds abstract fun events(impl: GameEventsImpl): GameEvents

    /** Play reads `/trips/stats` only when a Trips implementation is bound (Task 8); until then the Trips section hides. */
    @BindsOptionalOf abstract fun trips(): TripsRepository
}
