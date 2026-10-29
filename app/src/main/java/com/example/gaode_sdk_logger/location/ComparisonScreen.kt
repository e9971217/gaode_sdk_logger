package com.example.gaode_sdk_logger.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// 高德融合定位與 Android 原生 GPS 的對比表，嵌入即時頁面
@Composable
fun ComparisonSection() {
    val amapFix by TrackingRepository.location.collectAsState()
    val nativeFix by TrackingRepository.nativeFix.collectAsState()
    val satellites by TrackingRepository.satellites.collectAsState()

    val amap = amapFix?.takeIf { it.errorCode == 0 }
    val native = nativeFix
    val usedCount = satellites.count { it.usedInFix }
    val distance = if (amap != null && native != null) {
        haversineMeters(amap.latitude, amap.longitude, native.latitude, native.longitude)
    } else {
        null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("高德 vs 原生 GPS", style = MaterialTheme.typography.titleMedium)
        Text(
            distance?.let { "兩者距離：%.1f m".format(it) } ?: "等待兩邊的定位結果…",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        CompareRow("項目", "高德", "GPS（原生）")
        HorizontalDivider()
        CompareRow(
            "緯度",
            amap?.let { "%.6f".format(it.latitude) },
            native?.let { "%.6f".format(it.latitude) }
        )
        CompareRow(
            "經度",
            amap?.let { "%.6f".format(it.longitude) },
            native?.let { "%.6f".format(it.longitude) }
        )
        CompareRow(
            "精度",
            amap?.let { "%.1f m".format(it.accuracy) },
            native?.takeIf { it.hasAccuracy() }?.let { "%.1f m".format(it.accuracy) }
        )
        CompareRow("來源", amap?.provider, native?.provider)
        CompareRow(
            "時間",
            amap?.takeIf { it.time > 0 }?.let { formatClock(it.time) },
            native?.let { formatClock(it.time) }
        )
        CompareRow(
            "速度",
            amap?.let { "%.2f m/s".format(it.speed) },
            native?.takeIf { it.hasSpeed() }?.let { "%.2f m/s".format(it.speed) }
        )
        CompareRow(
            "海拔",
            amap?.let { "%.1f m".format(it.altitude) },
            native?.takeIf { it.hasAltitude() }?.let { "%.1f m".format(it.altitude) }
        )
        CompareRow(
            "方向",
            amap?.let { "%.1f°".format(it.bearing) },
            native?.takeIf { it.hasBearing() }?.let { "%.1f°".format(it.bearing) }
        )
        CompareRow(
            "定位類型",
            amap?.let { locationTypeName(it.locationType) },
            null
        )
        CompareRow(
            "衛星",
            amap?.let { "${it.satellites} 顆" },
            "使用 $usedCount / 追蹤 ${satellites.size}"
        )

        Text(
            "高德為融合定位（GPS／Wi‑Fi／基站）；GPS 為 Android 原生 GPS 提供者。" +
                "衛星欄位：高德為融合結果的衛星數，GPS 欄位為 GNSS 狀態回報。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CompareRow(label: String, amapValue: String?, nativeValue: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            amapValue ?: "--",
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            nativeValue ?: "--",
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatClock(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

// 球面距離（Haversine），單位公尺
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(h))
}
