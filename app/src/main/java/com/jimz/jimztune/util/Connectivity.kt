package com.jimz.jimztune.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Status internet. "Online" berarti jaringan aktif DAN benar-benar tersambung ke internet (bukan Wi-Fi tanpa internet). */
object Connectivity {
    fun isOnline(context: Context): Boolean = try {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        true // tidak bisa memeriksa: anggap online supaya beranda biasa tetap tampil
    }
}

/** Status internet yang ikut berubah otomatis saat sinyal hilang atau kembali. */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current.applicationContext
    var online by remember { mutableStateOf(Connectivity.isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = Connectivity.isOnline(context) }
            override fun onLost(network: Network) { online = Connectivity.isOnline(context) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = Connectivity.isOnline(context)
            }
        }
        try {
            cm?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
        }
        online = Connectivity.isOnline(context)
        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }
    return online
}
