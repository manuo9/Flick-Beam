package com.flickbeam.remote.di

import com.flickbeam.remote.network.ConnectionController
import com.flickbeam.remote.network.ControlConnectionController
import com.flickbeam.remote.network.DiscoveryController
import com.flickbeam.remote.network.InstallController
import com.flickbeam.remote.network.NsdDiscoveryController
import com.flickbeam.remote.network.TvInstallController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Tells Hilt which class to use for each network interface. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PhoneModule {

    @Binds
    @Singleton
    abstract fun bindDiscoveryController(impl: NsdDiscoveryController): DiscoveryController

    @Binds
    @Singleton
    abstract fun bindConnectionController(impl: ControlConnectionController): ConnectionController

    @Binds
    @Singleton
    abstract fun bindInstallController(impl: TvInstallController): InstallController
}
