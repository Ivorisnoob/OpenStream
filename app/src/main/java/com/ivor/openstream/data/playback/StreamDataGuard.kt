package com.ivor.openstream.data.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the current network costs the user, and what streaming has already used.
 *
 * Two things drive the player's data behaviour: whether the active network is metered (so quality
 * can be capped and the user can be warned), and the bytes loaded since the app started (so the
 * settings can show a running total). Both are read from anywhere and never block; connectivity
 * callbacks arrive on a system thread and only touch a [StateFlow].
 */
@Singleton
class StreamDataGuard @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isMetered = MutableStateFlow(isActiveNetworkMetered())

    /** True while the phone is on mobile data or another paid network (tethered Wi-Fi counts). */
    val isMetered: StateFlow<Boolean> = _isMetered.asStateFlow()

    private val _dataUsedBytes = MutableStateFlow(0L)

    /** Bytes this app has streamed since it started. */
    val dataUsedBytes: StateFlow<Long> = _dataUsedBytes.asStateFlow()

    /** Set once the user has answered the metered-network prompt, so it asks only once per run. */
    private var askedThisRun = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isMetered.value = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }

        override fun onLost(network: Network) {
            _isMetered.value = isActiveNetworkMetered()
        }
    }

    init {
        // The callback keeps the state current after startup; failure just leaves the initial read.
        runCatching {
            connectivityManager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback
            )
        }
    }

    val meteredNow: Boolean get() = _isMetered.value

    /** True while the user is on a paid network and has not yet agreed to stream on it. */
    fun shouldWarn(): Boolean = _isMetered.value && !askedThisRun

    fun markAsked() {
        askedThisRun = true
    }

    fun addBytesLoaded(bytes: Long) {
        if (bytes > 0L) _dataUsedBytes.value += bytes
    }

    private fun isActiveNetworkMetered(): Boolean {
        val manager = connectivityManager ?: return false
        return runCatching {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            capabilities != null && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }.getOrDefault(false)
    }
}
