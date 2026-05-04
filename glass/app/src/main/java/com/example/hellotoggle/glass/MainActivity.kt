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
        setContent {
            HelloWorldScreen(
                message = MESSAGES[index],
                visible = helloVisible,
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
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> helloVisible = !helloVisible
                KeyEvent.KEYCODE_DPAD_RIGHT -> index = (index + 1).mod(MESSAGES.size)
                KeyEvent.KEYCODE_DPAD_LEFT -> index = (index - 1).mod(MESSAGES.size)
            }
        }
        return true
    }
}

@Composable
fun HelloWorldScreen(message: String, visible: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Text(
                text = message,
                color = Color(0xFF00AF00),
                fontSize = 56.sp,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 480, heightDp = 270)
@Composable
private fun HelloWorldPreview() {
    HelloWorldScreen(message = "Hello World", visible = true)
}
