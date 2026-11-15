package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isConnectedNow(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val isOnline: Flow<Boolean> = callbackFlow {
        val validatedNetworks = mutableSetOf<Network>()

        fun emitCurrentStatus() {
            val connected = validatedNetworks.isNotEmpty() || isConnectedNow()
            trySend(connected)
        }

        // Seed initial active network if already online
        connectivityManager.activeNetwork?.let { active ->
            val caps = connectivityManager.getNetworkCapabilities(active)
            if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                validatedNetworks.add(active)
            }
        }
        trySend(validatedNetworks.isNotEmpty() || isConnectedNow())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps == null || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    validatedNetworks.add(network)
                }
                trySend(validatedNetworks.isNotEmpty() || isConnectedNow())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    validatedNetworks.add(network)
                } else {
                    validatedNetworks.remove(network)
                }
                trySend(validatedNetworks.isNotEmpty() || isConnectedNow())
            }

            override fun onLost(network: Network) {
                validatedNetworks.remove(network)
                emitCurrentStatus()
            }

            override fun onUnavailable() {
                validatedNetworks.clear()
                trySend(false)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
