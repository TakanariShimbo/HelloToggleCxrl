package com.example.hellotoggle.glass

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "GlassBridge"
private const val CHANNEL_FROM_PHONE = "rk_custom_client"
private const val CHANNEL_TO_PHONE = "rk_custom_key"
private const val PING_TIMEOUT_MS = 12_000L

enum class BridgeStatus { DISCONNECTED, CONNECTING, CONNECTED }

object GlassBridge {
    private var bridge: CXRServiceBridge? = null

    private val _status = MutableStateFlow(BridgeStatus.DISCONNECTED)
    val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    private val _sessionOpen = MutableStateFlow(false)
    val sessionOpen: StateFlow<Boolean> = _sessionOpen.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pingTimeoutRunnable = Runnable {
        Log.d(TAG, "ping timeout, marking session closed")
        _sessionOpen.value = false
    }

    private fun armPingWatchdog() {
        mainHandler.removeCallbacks(pingTimeoutRunnable)
        mainHandler.postDelayed(pingTimeoutRunnable, PING_TIMEOUT_MS)
    }

    private fun stopPingWatchdog() {
        mainHandler.removeCallbacks(pingTimeoutRunnable)
    }

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
                    stopPingWatchdog()
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
                    if (event != "ping") Log.d(TAG, "received on $name: event=$event")
                    when (event) {
                        "session_open" -> {
                            _sessionOpen.value = true
                            armPingWatchdog()
                        }
                        "session_close" -> {
                            _sessionOpen.value = false
                            stopPingWatchdog()
                        }
                        "ping" -> armPingWatchdog()
                    }
                }
            })
        }
    }

    fun sendCaps(caps: Caps): Int {
        val b = bridge
        if (b == null) {
            Log.w(TAG, "sendCaps: bridge not initialized")
            return -1
        }
        val rc = b.sendMessage(CHANNEL_TO_PHONE, caps)
        Log.d(TAG, "sendMessage($CHANNEL_TO_PHONE) -> $rc")
        return rc
    }

    fun sendGesture(event: String, visible: Boolean, index: Int, message: String) {
        if (_status.value != BridgeStatus.CONNECTED || !_sessionOpen.value) {
            Log.d(TAG, "sendGesture skipped (status=${_status.value} sessionOpen=${_sessionOpen.value})")
            return
        }
        val caps = Caps().apply {
            write("event")
            write(event)
            write("visible")
            writeInt32(if (visible) 1 else 0)
            write("index")
            writeInt32(index)
            write("message")
            write(message)
            write("ts")
            writeInt64(System.currentTimeMillis())
        }
        sendCaps(caps)
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
