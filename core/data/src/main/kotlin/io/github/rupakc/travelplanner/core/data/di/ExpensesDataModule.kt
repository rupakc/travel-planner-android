package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.ExpensesRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.ExpensesRepository

/** Expenses binding (kept apart from [DataModule], a shared hotspot). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExpensesDataModule {
    @Binds abstract fun expenses(impl: ExpensesRepositoryImpl): ExpensesRepository
}
