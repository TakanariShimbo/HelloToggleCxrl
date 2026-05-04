package com.example.hellotoggle.glass

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

private val MESSAGES = listOf("Hello World", "こんにちは", "Bonjour", "안녕")

class MainActivity : ComponentActivity() {

    private var helloVisible by mutableStateOf(true)
    private var index by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        GlassBridge.init()
        setContent {
            val bridgeStatus by GlassBridge.status.collectAsState()
            val sessionOpen by GlassBridge.sessionOpen.collectAsState()
            HelloWorldScreen(
                message = MESSAGES[index],
                visible = helloVisible,
                bridgeStatus = bridgeStatus,
                sessionOpen = sessionOpen,
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
        if (!handled) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            val gestureEvent: String? = when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    helloVisible = !helloVisible
                    "tap"
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    index = (index + 1).mod(MESSAGES.size)
                    "swipe_next"
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    index = (index - 1).mod(MESSAGES.size)
                    "swipe_prev"
                }
                else -> null
            }
            if (gestureEvent != null) {
                GlassBridge.sendGesture(
                    event = gestureEvent,
                    visible = helloVisible,
                    index = index,
                    message = MESSAGES[index],
                )
            }
        }
        return true
    }
}

@Composable
fun HelloWorldScreen(
    message: String,
    visible: Boolean,
    bridgeStatus: BridgeStatus,
    sessionOpen: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val fullyConnected = bridgeStatus == BridgeStatus.CONNECTED && sessionOpen
        when {
            fullyConnected -> {
                if (visible) {
                    Text(
                        text = message,
                        color = Color(0xFF00AF00),
                        fontSize = 56.sp,
                    )
                }
            }
            bridgeStatus == BridgeStatus.CONNECTING -> {
                Text(
                    text = "Connecting…",
                    color = Color(0xFFAAAAAA),
                    fontSize = 32.sp,
                )
            }
            else -> {
                Text(
                    text = "Phone not connected",
                    color = Color(0xFFC04040),
                    fontSize = 32.sp,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 480, heightDp = 270)
@Composable
private fun HelloWorldConnectedPreview() {
    HelloWorldScreen(
        message = "Hello World",
        visible = true,
        bridgeStatus = BridgeStatus.CONNECTED,
        sessionOpen = true,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 480, heightDp = 270)
@Composable
private fun HelloWorldDisconnectedPreview() {
    HelloWorldScreen(
        message = "Hello World",
        visible = true,
        bridgeStatus = BridgeStatus.DISCONNECTED,
        sessionOpen = false,
    )
}
