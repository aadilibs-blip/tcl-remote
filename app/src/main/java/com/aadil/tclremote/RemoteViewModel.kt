package com.aadil.tclremote

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aadil.tclremote.net.ClientIdentity
import com.aadil.tclremote.net.Link
import com.aadil.tclremote.net.PairingSession
import com.aadil.tclremote.net.RemoteClient
import com.aadil.tclremote.net.TvDevice
import com.aadil.tclremote.net.discoverTvs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { Pick, Connecting, NeedPin, Remote }

data class UiState(
    val phase: Phase = Phase.Pick,
    val devices: List<TvDevice> = emptyList(),
    val host: String? = null,
    val name: String? = null,
    val message: String? = null
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("remote", Context.MODE_PRIVATE)
    private val client = RemoteClient(app)
    private var pairing: PairingSession? = null
    private var discoveryJob: Job? = null
    private var started = false

    private val _ui = MutableStateFlow(UiState(host = prefs.getString(KEY_LAST_HOST, null)))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        ClientIdentity.init(app)
        viewModelScope.launch {
            client.link.collect { link ->
                if (link == Link.Closed && _ui.value.phase == Phase.Remote) {
                    _ui.update {
                        it.copy(phase = Phase.Pick, message = "Lost connection to the TV.")
                    }
                    startDiscovery()
                }
            }
        }
    }

    /** Called once when the screen appears: reconnect to the last TV, or look for one. */
    fun start() {
        if (started) return
        started = true
        val last = _ui.value.host
        if (last != null && isPaired(last)) connect(last) else startDiscovery()
    }

    fun isRemote(): Boolean = _ui.value.phase == Phase.Remote

    fun startDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            try {
                discoverTvs(getApplication()).collect { list ->
                    _ui.update { it.copy(devices = list) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(message = "Couldn't search the network. Enter the TV's IP address instead.") }
            }
        }
    }

    fun connect(host: String, name: String? = null) {
        val h = host.trim()
        if (h.isEmpty()) return
        discoveryJob?.cancel()
        _ui.update { it.copy(phase = Phase.Connecting, host = h, name = name ?: it.name, message = null) }
        viewModelScope.launch {
            try {
                client.connect(h)
                prefs.edit().putString(KEY_LAST_HOST, h).apply()
                _ui.update { it.copy(phase = Phase.Remote) }
            } catch (e: Exception) {
                if (isPaired(h)) {
                    backToPicker("Couldn't reach the TV. Check it's on and on the same Wi-Fi, or pair again.")
                } else {
                    beginPairing(h)
                }
            }
        }
    }

    /** Starts pairing again, e.g. after the TV was reset. */
    fun repair(host: String) {
        val h = host.trim()
        if (h.isEmpty()) return
        discoveryJob?.cancel()
        _ui.update { it.copy(phase = Phase.Connecting, host = h, message = null) }
        viewModelScope.launch { beginPairing(h) }
    }

    private suspend fun beginPairing(host: String) {
        try {
            pairing?.close()
            val session = PairingSession(host, Build.MODEL.ifBlank { "Android phone" })
            session.start()
            pairing = session
            _ui.update { it.copy(phase = Phase.NeedPin, host = host, message = null) }
        } catch (e: Exception) {
            backToPicker("Couldn't start pairing: ${e.message ?: "unknown error"}")
        }
    }

    fun submitCode(code: String) {
        val session = pairing ?: return
        val host = _ui.value.host ?: return
        viewModelScope.launch {
            try {
                if (session.finish(code)) {
                    session.close()
                    pairing = null
                    markPaired(host)
                    delay(800) // let the TV finish pairing before we reconnect
                    connect(host)
                } else {
                    _ui.update { it.copy(message = "That code doesn't match. Check the TV and try again.") }
                }
            } catch (e: Exception) {
                pairing?.close()
                pairing = null
                backToPicker("Pairing failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun cancelPairing() {
        pairing?.close()
        pairing = null
        backToPicker(null)
    }

    fun press(keyCode: Int) = client.key(keyCode)

    fun openLink(url: String) = client.openLink(url)

    fun disconnect() {
        client.close()
        backToPicker(null)
    }

    private fun backToPicker(message: String?) {
        _ui.update { it.copy(phase = Phase.Pick, message = message) }
        startDiscovery()
    }

    private fun isPaired(host: String): Boolean =
        prefs.getStringSet(KEY_PAIRED, emptySet())?.contains(host) == true

    private fun markPaired(host: String) {
        val set = (prefs.getStringSet(KEY_PAIRED, emptySet()) ?: emptySet()).toMutableSet()
        set.add(host)
        prefs.edit().putStringSet(KEY_PAIRED, set).apply()
    }

    override fun onCleared() {
        pairing?.close()
        client.close()
    }

    private companion object {
        const val KEY_LAST_HOST = "lastHost"
        const val KEY_PAIRED = "pairedHosts"
    }
}
