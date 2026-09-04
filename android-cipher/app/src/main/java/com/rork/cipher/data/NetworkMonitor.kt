package com.rork.cipher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this device has a usable network, and a nudge the moment one appears.
 *
 * The socket loop waits on this instead of burning retries into airplane mode,
 * and reconnects immediately when Wi-Fi or mobile data comes back rather than
 * sitting out the rest of its backoff.
 */
class NetworkMonitor(context: Context, private val onAvailable: () -> Unit) {

    private val manager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var watching = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()

        override fun onLost(network: Network) = publish()

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) = publish()
    }

    init {
        watching = runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            manager?.registerNetworkCallback(request, callback) ?: return@runCatching false
            true
        }.getOrElse {
            // Without the callback we simply always assume a network is there;
            // the socket's own failures then drive reconnection.
            Log.w(TAG, "network callback unavailable: ${it.message}")
            false
        }
        if (watching) _online.value = hasNetwork()
    }

    private fun publish() {
        if (!watching) return
        val next = hasNetwork()
        val was = _online.value
        _online.value = next
        if (next && !was) onAvailable()
    }

    private fun hasNetwork(): Boolean = runCatching {
        val active = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}
