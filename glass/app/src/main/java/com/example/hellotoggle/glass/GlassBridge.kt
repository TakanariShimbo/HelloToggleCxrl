package com.example.hellotoggle.glass

import android.util.Log
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "GlassBridge"
private const val CHANNEL_FROM_PHONE = "rk_custom_client"
private const val CHANNEL_TO_PHONE = "rk_custom_key"

enum class BridgeStatus { DISCONNECTED, CONNECTING, CONNECTED }

object GlassBridge {
    private var bridge: CXRServiceBridge? = null

    private val _status = MutableStateFlow(BridgeStatus.DISCONNECTED)
    val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    private val _sessionOpen = MutableStateFlow(false)
    val sessionOpen: StateFlow<Boolean> = _sessionOpen.asStateFlow()

    fun init() {
        if (bridge != null) return
        bridge = CXRServiceBridge().apply {
            setStatusListener(object : CXRServiceBridge.StatusListener {
                override fun onConnected(p0: String?, p1: String?, p2: Int) {
                    Log.d(TAG, "onConnected pkg=$p0 device=$p1 type=$p2")
                    _status.value = BridgeStatus.CONNECTED
                }

                override fun onDisconnected() {
                    Log.d(TAG, "onDisconnected")
                    _status.value = BridgeStatus.DISCONNECTED
                    _sessionOpen.value = false
                }

                override fun onConnecting(p0: String?, p1: String?, p2: Int) {
                    Log.d(TAG, "onConnecting pkg=$p0 device=$p1 type=$p2")
                    _status.value = BridgeStatus.CONNECTING
                }

                override fun onARTCStatus(p0: Float, p1: Boolean) {}
                override fun onRokidAccountChanged(p0: String?) {}
            })
            subscribe(CHANNEL_FROM_PHONE, object : CXRServiceBridge.MsgCallback {
                override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
                    val event = readEvent(args)
                    Log.d(TAG, "received on $name: event=$event")
                    when (event) {
                        "session_open" -> _sessionOpen.value = true
                        "session_close" -> _sessionOpen.value = false
                    }
                }
            })
        }
    }

    fun sendCaps(caps: Caps) {
        val b = bridge
        if (b == null) {
            Log.w(TAG, "sendCaps: bridge not initialized")
            return
        }
        b.sendMessage(CHANNEL_TO_PHONE, caps)
    }

    private fun readEvent(caps: Caps?): String? {
        if (caps == null) return null
        for (i in 0 until caps.size() - 1) {
            val key = caps.at(i)
            if (key.type() == Caps.Value.TYPE_STRING && key.string == "event") {
                val v = caps.at(i + 1)
                if (v.type() == Caps.Value.TYPE_STRING) return v.string
            }
        }
        return null
    }
}
