package de.rwth_aachen.phyphox.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module(includes = [])
@InstallIn(SingletonComponent::class)
abstract class AppModule {}

