package com.memoryassist.fanfanlokmapper.di

import com.memoryassist.fanfanlokmapper.data.repository.ImageRepository
import com.memoryassist.fanfanlokmapper.domain.repository.ImageRepositoryInterface
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindImageRepository(
        imageRepository: ImageRepository
    ): ImageRepositoryInterface
}