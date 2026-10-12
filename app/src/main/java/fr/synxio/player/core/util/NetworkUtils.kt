package fr.synxio.player.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null && wifiManager.isWifiEnabled) {
                val wifiInfo = wifiManager.connectionInfo
                val ipAddress = wifiInfo.ipAddress
                if (ipAddress != 0) {
                    @Suppress("DEPRECATION")
                    return Formatter.formatIpAddress(ipAddress)
                }
            }
            
            // Fallback pour les autres réseaux (Partage de connexion, Ethernet, etc.)
            return findLanIpv4Address()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    /**
     * Adresse IPv4 privée de l'appareil sur le réseau local (Wi-Fi, point d'accès, Ethernet),
     * sans Context : utilisable par un serveur pour se lier à cette seule interface plutôt
     * qu'à 0.0.0.0 (qui exposerait aussi le réseau mobile / VPN).
     *
     * Les interfaces Wi-Fi (`wlan*`), point d'accès (`ap*`, `swlan*`) et Ethernet (`eth*`)
     * sont préférées ; seules les adresses privées (RFC 1918) sont retenues.
     */
    fun findLanIpv4Address(): String? = runCatching {
        val candidates = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
            .flatMap { intf ->
                intf.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .map { intf.name.orEmpty() to it.hostAddress }
            }
        val preferred = listOf("wlan", "ap", "swlan", "eth")
        // Réseau mobile et tunnels VPN : jamais exposés, même en dernier recours.
        val excluded = listOf("rmnet", "ccmni", "tun", "ppp", "ipsec", "v4-", "dummy")
        preferred.firstNotNullOfOrNull { prefix ->
            candidates.firstOrNull { it.first.startsWith(prefix) }?.second
        } ?: candidates.firstOrNull { c -> excluded.none { c.first.startsWith(it) } }?.second
    }.getOrNull()
}
