package dev.vpntester.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dev.vpntester.model.NetworkSnapshot
import dev.vpntester.model.NetworkTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkStateProvider(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
    }

    fun start() { runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }; refresh() }
    fun stop() { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }

    fun readSnapshot(): NetworkSnapshot {
        val active = connectivityManager.activeNetwork
        val capabilities = active?.let(connectivityManager::getNetworkCapabilities)
        val vpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        return NetworkSnapshot(
            transport = resolveTransport(capabilities, vpn),
            vpn = vpn,
            hasInternetCapability = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = connectivityManager.isActiveNetworkMetered
        )
    }

    private fun resolveTransport(activeCapabilities: NetworkCapabilities?, vpn: Boolean): NetworkTransport {
        if (!vpn) return activeCapabilities.toTransport()
        val underlying = connectivityManager.allNetworks.asSequence().mapNotNull(connectivityManager::getNetworkCapabilities).firstOrNull {
            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
        return underlying.toTransport().takeUnless { it == NetworkTransport.NONE } ?: NetworkTransport.OTHER
    }

    private fun NetworkCapabilities?.toTransport(): NetworkTransport = when {
        this == null -> NetworkTransport.NONE
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
        else -> NetworkTransport.OTHER
    }

    private fun refresh() { _snapshot.value = readSnapshot() }
}
