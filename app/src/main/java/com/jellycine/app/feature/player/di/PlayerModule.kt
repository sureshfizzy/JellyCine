package com.jellycine.app.feature.player.di

import com.jellycine.app.feature.player.domain.usecase.InitializePlayerUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for player-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideInitializePlayerUseCase(): InitializePlayerUseCase {
        return InitializePlayerUseCase()
    }
}
