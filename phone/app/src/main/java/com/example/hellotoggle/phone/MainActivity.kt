package com.example.hellotoggle.phone

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hellotoggle.phone.ui.theme.PhoneTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
private const val MAX_LOG = 200
private const val HI_ROKID_PKG = "com.rokid.sprite.global.aiapp"

private fun isPackageInstalled(context: Context, pkg: String): Boolean =
    try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

data class LogEntry(
    val ts: Long,
    val event: String,
    val visible: Boolean,
    val index: Int,
    val message: String,
)

object PhoneLog {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(e: LogEntry) {
        _entries.value = (listOf(e) + _entries.value).take(MAX_LOG)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hiRokidInstalled = remember { isPackageInstalled(context, HI_ROKID_PKG) }
    var authorized by remember { mutableStateOf(false) }
    var connection by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    val entries by PhoneLog.entries.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(
            hiRokidInstalled = hiRokidInstalled,
            authorized = authorized,
            connection = connection,
        )
        ActionButtons(
            authorized = authorized,
            connection = connection,
            onAuth = { authorized = true },
            onReauth = { authorized = false; connection = ConnectionState.DISCONNECTED },
            onConnect = { connection = ConnectionState.CONNECTED },
            onDisconnect = { connection = ConnectionState.DISCONNECTED },
            onAddDummyLog = { PhoneLog.add(makeDummyEntry()) },
        )
        LogTimeline(entries = entries, onClear = PhoneLog::clear)
    }
}

@Composable
private fun StatusCard(
    hiRokidInstalled: Boolean,
    authorized: Boolean,
    connection: ConnectionState,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("接続状態", style = MaterialTheme.typography.titleSmall)
            StatusRow("Hi Rokid", if (hiRokidInstalled) "installed" else "not installed")
            StatusRow("Authorization", if (authorized) "yes" else "no")
            StatusRow("Connection", connection.name.lowercase())
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun ActionButtons(
    authorized: Boolean,
    connection: ConnectionState,
    onAuth: () -> Unit,
    onReauth: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAddDummyLog: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAuth, enabled = !authorized) { Text("認証") }
            FilledTonalButton(onClick = onReauth, enabled = authorized) { Text("再認証") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onConnect,
                enabled = authorized && connection == ConnectionState.DISCONNECTED,
            ) { Text("接続開始") }
            FilledTonalButton(
                onClick = onDisconnect,
                enabled = connection != ConnectionState.DISCONNECTED,
            ) { Text("接続停止") }
        }
        OutlinedButton(onClick = onAddDummyLog) { Text("[debug] add dummy log") }
    }
}

@Composable
private fun LogTimeline(entries: List<LogEntry>, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ログ (${entries.size})", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text("Clear") }
        }
        if (entries.isEmpty()) {
            Text(
                text = "(まだイベントなし)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(entries) { e ->
                    val t = timeFmt.format(Date(e.ts))
                    val vis = if (e.visible) "true" else "false"
                    Text(
                        text = "$t  [${e.event}] visible=$vis index=${e.index} \"${e.message}\"",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private val DUMMY_EVENTS = listOf("tap", "swipe_next", "swipe_prev")
private val DUMMY_MESSAGES = listOf("Hello World", "こんにちは", "Bonjour", "안녕")

private fun makeDummyEntry(): LogEntry {
    val idx = DUMMY_MESSAGES.indices.random()
    return LogEntry(
        ts = System.currentTimeMillis(),
        event = DUMMY_EVENTS.random(),
        visible = (0..1).random() == 1,
        index = idx,
        message = DUMMY_MESSAGES[idx],
    )
}
