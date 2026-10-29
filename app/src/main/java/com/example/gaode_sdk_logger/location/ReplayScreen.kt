package com.example.gaode_sdk_logger.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ReplayPoint(
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMs: Float,
    val bearing: Float
)

// 依標題列對應欄位，新舊格式都能讀；沒有經緯度的錯誤紀錄會略過
internal fun parseTrack(file: File): List<ReplayPoint> {
    val lines = file.readLines()
    if (lines.isEmpty()) return emptyList()
    val columns = lines.first().split(",").withIndex().associate { it.value to it.index }
    fun value(fields: List<String>, name: String): String? = columns[name]?.let { fields.getOrNull(it) }

    return lines.drop(1).mapNotNull { line ->
        val f = line.split(",")
        ReplayPoint(
            timeMs = value(f, "time_ms")?.toLongOrNull() ?: return@mapNotNull null,
            latitude = value(f, "latitude")?.toDoubleOrNull() ?: return@mapNotNull null,
            longitude = value(f, "longitude")?.toDoubleOrNull() ?: return@mapNotNull null,
            speedMs = value(f, "speed_ms")?.toFloatOrNull() ?: 0f,
            bearing = value(f, "bearing")?.toFloatOrNull() ?: 0f
        )
    }
}

@Composable
fun ReplayScreen() {
    val context = LocalContext.current
    val tracksDir = remember { File(context.getExternalFilesDir(null) ?: context.filesDir, "tracks") }
    val files = remember {
        tracksDir.listFiles { f -> f.extension == "csv" }?.sortedByDescending { it.name }.orEmpty()
    }

    var selected by remember { mutableStateOf<File?>(null) }
    val points = remember(selected) { selected?.let { parseTrack(it) }.orEmpty() }
    var index by remember(selected) { mutableIntStateOf(0) }
    var playing by remember(selected) { mutableStateOf(false) }

    // 依照原始時間間隔播放（加快 4 倍），每點間隔限制在 100 ~ 1000 ms
    LaunchedEffect(playing, points) {
        while (playing && index < points.lastIndex) {
            val gap = points[index + 1].timeMs - points[index].timeMs
            delay((gap / 4).coerceIn(100L, 1000L))
            index += 1
        }
        if (index >= points.lastIndex) playing = false
    }

    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val grey = MaterialTheme.colorScheme.outline.toArgb()
    val current = points.getOrNull(index)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("回放記錄", style = MaterialTheme.typography.headlineSmall)

        if (files.isEmpty()) {
            Text("還沒有記錄檔，請先到即時畫面開始記錄。")
        }
        files.forEach { file ->
            TextButton(onClick = { selected = file }) {
                Text(if (file == selected) "▶ ${file.name}" else file.name)
            }
        }

        if (selected != null && points.isEmpty()) {
            Text("此記錄檔沒有可用的資料。")
        }

        if (points.isNotEmpty()) {
            AmapView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp)),
                onUpdate = { aMap ->
                    aMap.clear()
                    // 整段軌跡（灰色）
                    aMap.addPolyline(
                        PolylineOptions()
                            .addAll(points.map { LatLng(it.latitude, it.longitude) })
                            .width(10f)
                            .color(grey)
                    )
                    // 已播放的部分（主色）
                    if (index > 0) {
                        aMap.addPolyline(
                            PolylineOptions()
                                .addAll(points.take(index + 1).map { LatLng(it.latitude, it.longitude) })
                                .width(12f)
                                .color(primary)
                        )
                    }
                    current?.let {
                        val pos = LatLng(it.latitude, it.longitude)
                        aMap.addMarker(MarkerOptions().position(pos))
                        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
                    }
                }
            )

            Slider(
                value = index.toFloat(),
                onValueChange = {
                    index = it.roundToInt()
                    playing = false
                },
                valueRange = 0f..points.lastIndex.coerceAtLeast(1).toFloat()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (index >= points.lastIndex) index = 0
                    playing = !playing
                }) {
                    Text(if (playing) "暫停" else "播放")
                }
                Text("  第 ${index + 1} / ${points.size} 點")
            }

            current?.let {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timeMs))
                Text("時間：$time")
                Text("速度：%.1f km/h".format(it.speedMs * 3.6f))
                Text("方向：%.1f°".format(it.bearing))
            }
        }
    }
}
