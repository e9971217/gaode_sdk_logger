package com.example.gaode_sdk_logger.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import kotlin.math.roundToInt

private const val MAP_ZOOM = 17f

// 即時頁：記錄控制、目前位址、軌跡地圖、高德與原生 GPS 對比、衛星資訊
@Composable
fun LocationScreen() {
    val context = LocalContext.current
    val recording by TrackingRepository.recording.collectAsState()
    val loc by TrackingRepository.location.collectAsState()
    val nativeFix by TrackingRepository.nativeFix.collectAsState()
    val trail by TrackingRepository.trail.collectAsState()
    val pointCount by TrackingRepository.pointCount.collectAsState()
    val trackFile by TrackingRepository.trackFile.collectAsState()
    val gnssFile by TrackingRepository.gnssFile.collectAsState()
    val satellites by TrackingRepository.satellites.collectAsState()

    val fix = loc?.takeIf { it.errorCode == 0 }

    var bgGranted by remember { mutableStateOf(hasBackgroundLocation(context)) }
    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { bgGranted = it }

    // 進入畫面時自動開始記錄，之後可用按鈕停止
    LaunchedEffect(Unit) {
        if (!TrackingRepository.recording.value) TrackingService.start(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("即時定位", style = MaterialTheme.typography.headlineSmall)

        loc?.takeIf { it.errorCode != 0 }?.let {
            Text(
                text = "定位錯誤 ${it.errorCode}：${it.errorInfo}",
                color = MaterialTheme.colorScheme.error
            )
        } ?: run {
            if (loc == null) Text("等待定位中…")
        }

        // 記錄控制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recording) {
                Button(onClick = { TrackingService.stop(context) }) { Text("停止記錄") }
            } else {
                Button(onClick = { TrackingService.start(context) }) { Text("開始記錄") }
            }
            Text(
                if (recording) "● 記錄中，已記錄 $pointCount 點" else "未記錄",
                style = MaterialTheme.typography.bodyMedium,
                color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!bgGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Button(onClick = { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                Text("允許背景定位")
            }
        }

        // 目前位址
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("目前位址", style = MaterialTheme.typography.labelLarge)
            Text(
                fix?.address?.takeIf { it.isNotBlank() } ?: "等待位址資料…（需要網路）",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        trackFile?.let {
            Text("記錄檔：$it", style = MaterialTheme.typography.bodySmall)
        }
        gnssFile?.let {
            Text("GNSS 記錄檔：$it", style = MaterialTheme.typography.bodySmall)
        }

        // 軌跡地圖：高德軌跡與兩個定位點
        TrailMap(trail = trail, amap = fix, native = nativeFix)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Compass(bearing = loc?.bearing ?: 0f)
            SpeedText(speedMs = loc?.speed ?: 0f)
        }

        ComparisonSection()

        SatellitePanel(satellites)

    }
}

// 對照 AMapLocation 的 LOCATION_TYPE_* 常數
internal fun locationTypeName(type: Int): String = when (type) {
    AMapLocation.LOCATION_TYPE_GPS -> "GPS"
    AMapLocation.LOCATION_TYPE_SAME_REQ -> "同請求結果"
    AMapLocation.LOCATION_TYPE_FAST -> "快速定位"
    AMapLocation.LOCATION_TYPE_FIX_CACHE -> "固定快取"
    AMapLocation.LOCATION_TYPE_WIFI -> "Wi‑Fi"
    AMapLocation.LOCATION_TYPE_CELL -> "基站"
    AMapLocation.LOCATION_TYPE_AMAP -> "高德網路定位"
    AMapLocation.LOCATION_TYPE_OFFLINE -> "離線定位"
    AMapLocation.LOCATION_TYPE_LAST_LOCATION_CACHE -> "上次定位快取"
    AMapLocation.LOCATION_COMPENSATION -> "補償定位"
    AMapLocation.LOCATION_TYPE_COARSE_LOCATION -> "粗略定位"
    else -> "未知（$type）"
}

private fun hasBackgroundLocation(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun TrailMap(trail: List<TrackPoint>, amap: AMapLocation?, native: Location?) {
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()

    AmapView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp)),
        onUpdate = { aMap ->
            aMap.clear()
            if (trail.size > 1) {
                aMap.addPolyline(
                    PolylineOptions()
                        .addAll(trail.map { LatLng(it.latitude, it.longitude) })
                        .width(12f)
                        .color(lineColor)
                )
            }
            amap?.let {
                aMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(it.latitude, it.longitude))
                        .title("高德")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            }
            native?.let {
                aMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(it.latitude, it.longitude))
                        .title("GPS")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }
            val focus = amap?.let { LatLng(it.latitude, it.longitude) }
                ?: native?.let { LatLng(it.latitude, it.longitude) }
            focus?.let { aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, MAP_ZOOM)) }
        }
    )
}

@Composable
private fun Compass(bearing: Float) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(80.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2 * 0.8f

        drawCircle(color, radius = size.minDimension / 2, style = Stroke(width = 2.dp.toPx()))

        // 箭頭預設朝上（正北），依方位角順時針旋轉
        rotate(degrees = bearing, pivot = Offset(cx, cy)) {
            val arrow = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.5f, cy + r * 0.6f)
                lineTo(cx, cy + r * 0.25f)
                lineTo(cx - r * 0.5f, cy + r * 0.6f)
                close()
            }
            drawPath(arrow, color)
        }
    }
}

@Composable
private fun SpeedText(speedMs: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = (speedMs * 3.6f).roundToInt().toString(),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text("km/h", style = MaterialTheme.typography.bodyMedium)
    }
}
