package com.example.gaode_sdk_logger

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gaode_sdk_logger.location.HistoryScreen
import com.example.gaode_sdk_logger.location.LocationScreen
import com.example.gaode_sdk_logger.location.ReplayScreen
import com.example.gaode_sdk_logger.location.LocationTracker
import com.example.gaode_sdk_logger.ui.theme.Gaode_sdk_loggerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gaode_sdk_loggerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private const val PREFS_NAME = "app_prefs"
private const val KEY_PRIVACY_AGREED = "privacy_agreed"

@Composable
private fun AppContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var agreed by remember { mutableStateOf(prefs.getBoolean(KEY_PRIVACY_AGREED, false)) }
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !agreed -> PrivacyDialog(
                onAgree = {
                    // 必須在建立 AMapLocationClient 之前呼叫
                    LocationTracker.setPrivacyConsent(context)
                    prefs.edit().putBoolean(KEY_PRIVACY_AGREED, true).apply()
                    agreed = true
                },
                onDecline = { (context as? Activity)?.finish() }
            )

            !granted -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                }) {
                    Text("授權定位權限")
                }
            }

            else -> MainTabs()
        }
    }
}

@Composable
private fun MainTabs() {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("即時", "回放", "歷史紀錄")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) }
                )
            }
        }
        when (tab) {
            0 -> LocationScreen()
            1 -> ReplayScreen()
            else -> HistoryScreen()
        }
    }
}

@Composable
private fun PrivacyDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("隱私政策") },
        text = {
            Text(
                "本 App 使用高德地圖定位 SDK 取得您的位置、速度與時間資訊，" +
                    "僅用於在畫面上顯示。同意後才會開始定位。"
            )
        },
        confirmButton = { TextButton(onClick = onAgree) { Text("同意") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("不同意") } }
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
