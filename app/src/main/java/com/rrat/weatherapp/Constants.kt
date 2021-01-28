package com.rrat.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {
    fun isNetworkAvailable(context: Context): Boolean{
        val connectivityManager: ConnectivityManager = context.
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork: NetworkCapabilities = connectivityManager.getNetworkCapabilities(network) as NetworkCapabilities

        return when{
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> return true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> return true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> return true
            else -> false
        }

    }
}