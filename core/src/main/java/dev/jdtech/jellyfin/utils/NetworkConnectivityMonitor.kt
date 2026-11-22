package dev.jdtech.jellyfin.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NetworkConnectivityMonitor
@Inject
constructor(@ApplicationContext private val context: Context) {
    private val connectivityManager: ConnectivityManager? =
        try {
            context.getSystemService(ConnectivityManager::class.java)
        } catch (_: Exception) {
            null
        }

    private val _isOnlineFlow = MutableStateFlow(isOnline())
    val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnlineFlow.value = isOnline()
                    }

                    override fun onLost(network: Network) {
                        _isOnlineFlow.value = isOnline()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        _isOnlineFlow.value = isOnline()
                    }

                    override fun onUnavailable() {
                        _isOnlineFlow.value = isOnline()
                    }
                }
            )
        }
    }

    fun isOnline(): Boolean {
        val cm = connectivityManager ?: return false
        return try {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        } catch (_: SecurityException) {
            true
        } catch (_: Exception) {
            false
        }
    }
}
