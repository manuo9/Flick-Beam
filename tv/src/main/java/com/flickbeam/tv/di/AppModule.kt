package com.flickbeam.tv.di

import com.flickbeam.tv.network.ReceiverController
import com.flickbeam.tv.network.ReceiverControllerImpl
import com.flickbeam.tv.permission.PermissionManager
import com.flickbeam.tv.permission.PermissionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Tells Hilt which class to use for each interface. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindPermissionManager(impl: PermissionManagerImpl): PermissionManager

    @Binds
    @Singleton
    abstract fun bindReceiverController(impl: ReceiverControllerImpl): ReceiverController
}
