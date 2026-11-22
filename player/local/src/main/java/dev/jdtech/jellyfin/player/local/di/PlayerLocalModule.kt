package dev.jdtech.jellyfin.player.local.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.player.local.domain.AndroidDeviceCapabilityInspector
import dev.jdtech.jellyfin.player.local.domain.DefaultNetworkBandwidthEstimator
import dev.jdtech.jellyfin.player.local.domain.DeviceCapabilityInspector
import dev.jdtech.jellyfin.player.local.domain.NetworkBandwidthEstimator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerLocalModule {
    @Binds
    @Singleton
    abstract fun bindDeviceCapabilityInspector(
        impl: AndroidDeviceCapabilityInspector
    ): DeviceCapabilityInspector

    @Binds
    @Singleton
    abstract fun bindNetworkBandwidthEstimator(
        impl: DefaultNetworkBandwidthEstimator
    ): NetworkBandwidthEstimator
}
