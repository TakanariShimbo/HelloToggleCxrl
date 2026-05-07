package com.example.hellotoggle.host

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.example.cxrglobal.auth.AuthResult
import com.example.cxrglobal.auth.AuthorizationHelper
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
import com.example.hellotoggle.host.ui.theme.HelloToggleCxrlHostTheme
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

private const val AUTH_REQUEST_CODE = 1001

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TokenStore.load(this)
        setContent {
            HelloToggleCxrlHostTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestAuth = {
                            AuthorizationHelper.requestAuthorization(this, AUTH_REQUEST_CODE)
                        },
                    )
                }
            }
        }
    }

    @Deprecated("required by AuthorizationHelper SDK callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != AUTH_REQUEST_CODE) return
        when (val result = AuthorizationHelper.parseAuthorizationResult(resultCode, data)) {
            is AuthResult.AuthSuccess -> {
                Log.d("HelloToggleCxrl", "auth success, token len=${result.token.length}")
                TokenStore.save(this, result.token)
            }
            is AuthResult.AuthFail -> Log.d("HelloToggleCxrl", "auth failed")
            is AuthResult.AuthCancel -> Log.d("HelloToggleCxrl", "auth cancelled")
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onRequestAuth: () -> Unit = {},
) {
    val context = LocalContext.current
    val hiRokidInstalled = remember { isPackageInstalled(context, HI_ROKID_PKG) }
    val token by TokenStore.token.collectAsState()
    val authorized = token != null
    val running by ConnectionService.running.collectAsState()
    val cxrState by ConnectionService.connState.collectAsState()
    val connection = when {
        !running -> ConnectionState.DISCONNECTED
        cxrState == CxrConnState.CONNECTED -> ConnectionState.CONNECTED
        else -> ConnectionState.CONNECTING
    }
    val entries by PhoneLog.entries.collectAsState()

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) ConnectionService.start(context)
    }

    val startService = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ConnectionService.start(context)
        }
    }

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
            onAuth = onRequestAuth,
            onReauth = {
                ConnectionService.stop(context)
                TokenStore.clear(context)
            },
            onConnect = startService,
            onDisconnect = { ConnectionService.stop(context) },
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

