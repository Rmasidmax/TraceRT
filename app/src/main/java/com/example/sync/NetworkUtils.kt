package com.example.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkUtils {
    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        // 1. Modern approach (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            if (network != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities != null) {
                    val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                    if (hasTransport && hasInternet) {
                        return true
                    }
                }
            }
        }

        // 2. Deprecated Active Network Info fallback (very reliable across custom OEMs, ROMs and emulators)
        try {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                return true
            }
        } catch (e: Exception) {
            // Ignore potential security exception or hardware reporting faults
        }

        // 3. Concrete adapter state fallback: check individual transports
        try {
            @Suppress("DEPRECATION")
            val wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            @Suppress("DEPRECATION")
            val mobile = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
            if (wifi?.isConnected == true || mobile?.isConnected == true) {
                return true
            }
        } catch (e: Exception) {
            // Ignore
        }

        // 4. Try checking if there is any network capability reporting any internet access
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val allNetworks = connectivityManager.allNetworks
                for (net in allNetworks) {
                    val cap = connectivityManager.getNetworkCapabilities(net)
                    if (cap != null && (
                        cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    )) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return false
    }
}
