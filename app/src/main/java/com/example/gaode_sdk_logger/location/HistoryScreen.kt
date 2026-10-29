package com.example.gaode_sdk_logger.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private data class TrackSummary(
    val file: File,
    val pointCount: Int,
    val startMs: Long?,
    val endMs: Long?,
    val distanceM: Double,
    val sizeBytes: Long
)

private fun summarize(file: File): TrackSummary {
    val points = parseTrack(file)
    var distance = 0.0
    for (i in 1 until points.size) distance += distanceMeters(points[i - 1], points[i])
    return TrackSummary(
        file = file,
        pointCount = points.size,
        startMs = points.firstOrNull()?.timeMs,
        endMs = points.lastOrNull()?.timeMs,
        distanceM = distance,
        sizeBytes = file.length()
    )
}

// 球面距離（Haversine），單位公尺
private fun distanceMeters(a: ReplayPoint, b: ReplayPoint): Double {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(h))
}

private fun formatTime(ms: Long?): String =
    ms?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "--"

// 預覽前 200 行原始 CSV，避免大檔案撐爆畫面
private fun rawPreview(file: File): String =
    file.useLines { lines -> lines.take(200).joinToString("\n") }

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val tracksDir = remember { File(context.getExternalFilesDir(null) ?: context.filesDir, "tracks") }
    val summaries = remember {
        tracksDir.listFiles { f -> f.extension == "csv" }
            ?.sortedByDescending { it.name }
            .orEmpty()
            .map { summarize(it) }
    }
    var opened by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("歷史紀錄", style = MaterialTheme.typography.headlineSmall)

        if (summaries.isEmpty()) {
            Text("還沒有歷史紀錄，請先到即時畫面開始記錄。")
        }

        summaries.forEach { s ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { opened = if (opened == s.file) null else s.file }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(s.file.name, fontWeight = FontWeight.Bold)
                Text("開始：${formatTime(s.startMs)}")
                Text("結束：${formatTime(s.endMs)}")
                Text("點數：${s.pointCount}　距離：%.2f km".format(s.distanceM / 1000))
                Text("檔案大小：%.1f KB".format(s.sizeBytes / 1024.0))

                if (opened == s.file) {
                    Text(
                        text = rawPreview(s.file),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
