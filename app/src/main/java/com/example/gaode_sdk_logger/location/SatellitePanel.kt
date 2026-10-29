package com.example.gaode_sdk_logger.location

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SatellitePanel(satellites: List<SatelliteSignal>) {
    val scheme = MaterialTheme.colorScheme
    val usedCount = satellites.count { it.usedInFix }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "衛星（追蹤 ${satellites.size} 顆，定位使用 $usedCount 顆）",
            style = MaterialTheme.typography.titleMedium
        )
        if (satellites.isEmpty()) {
            Text("目前沒有衛星資料（室內或尚未開始記錄）", style = MaterialTheme.typography.bodySmall)
        } else {
            val byConstellation = satellites.groupingBy { it.constellation }.eachCount()
            Text(
                byConstellation.entries.joinToString("　") { "${it.key} ${it.value}" },
                style = MaterialTheme.typography.bodySmall
            )
            SkyPlot(satellites, scheme)
            satellites.sortedByDescending { it.cn0DbHz }.forEach { s ->
                SignalRow(s, constellationColor(s.constellation, scheme))
            }
        }
    }
}

// 天空圖：中心為天頂，外圈為地平線，北方朝上
@Composable
private fun SkyPlot(satellites: List<SatelliteSignal>, scheme: ColorScheme) {
    val outline = scheme.outline
    Canvas(modifier = Modifier.size(220.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        listOf(1f, 2f / 3f, 1f / 3f).forEach { ratio ->
            drawCircle(outline, radius = radius * ratio, center = center, style = Stroke(width = 1.dp.toPx()))
        }
        drawLine(outline, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 1.dp.toPx())
        drawLine(outline, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1.dp.toPx())

        satellites.forEach { s ->
            val az = Math.toRadians(s.azimuthDeg.toDouble())
            val r = radius * (90f - s.elevationDeg.coerceIn(0f, 90f)) / 90f
            val p = Offset(center.x + (r * sin(az)).toFloat(), center.y - (r * cos(az)).toFloat())
            val color = constellationColor(s.constellation, scheme)
            // 實心：用於定位；空心：只有追蹤
            if (s.usedInFix) {
                drawCircle(color, radius = 6.dp.toPx(), center = p)
            } else {
                drawCircle(color, radius = 6.dp.toPx(), center = p, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun SignalRow(s: SatelliteSignal, color: Color) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${s.constellation} ${s.svid}", modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(track)
        ) {
            // C/N0 以 50 dB-Hz 為滿格
            Box(
                modifier = Modifier
                    .fillMaxWidth((s.cn0DbHz / 50f).coerceIn(0f, 1f))
                    .height(10.dp)
                    .background(color)
            )
        }
        Text("%.0f dB-Hz".format(s.cn0DbHz), modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
        Text(if (s.usedInFix) "使用" else "未用", style = MaterialTheme.typography.bodySmall)
    }
}

private fun constellationColor(name: String, scheme: ColorScheme): Color = when (name) {
    "GPS" -> scheme.primary
    "北斗" -> scheme.tertiary
    "Galileo" -> scheme.secondary
    "GLONASS" -> scheme.error
    else -> scheme.outline
}
