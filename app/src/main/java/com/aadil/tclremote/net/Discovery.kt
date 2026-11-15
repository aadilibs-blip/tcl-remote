package com.aadil.tclremote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

data class TvDevice(val name: String, val host: String)

private const val SERVICE_TYPE = "_androidtvremote2._tcp."

/** Finds Android TVs on the Wi-Fi network that accept remote connections. */
@Suppress("DEPRECATION")
fun discoverTvs(context: Context): Flow<List<TvDevice>> = callbackFlow {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val found = LinkedHashMap<String, TvDevice>()
    val resolveLock = Mutex() // NsdManager resolves one service at a time

    val listener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
            launch {
                resolveLock.withLock {
                    val resolved = resolve(nsd, info) ?: return@withLock
                    val host = resolved.host?.hostAddress ?: return@withLock
                    found[resolved.serviceName] = TvDevice(resolved.serviceName, host)
                    trySend(found.values.toList())
                    Unit
                }
            }
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            found.remove(info.serviceName)
            trySend(found.values.toList())
        }

        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
}

@Suppress("DEPRECATION")
private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
    suspendCancellableCoroutine { cont ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                if (cont.isActive) cont.resume(serviceInfo)
            }
        })
    }
