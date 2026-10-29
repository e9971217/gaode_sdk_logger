package com.example.gaode_sdk_logger.location

import android.location.Location
import com.amap.api.location.AMapLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackPoint(val latitude: Double, val longitude: Double)

data class SatelliteSignal(
    val constellation: String,
    val svid: Int,
    val cn0DbHz: Float,
    val elevationDeg: Float,
    val azimuthDeg: Float,
    val usedInFix: Boolean
)

// 服務與畫面共用的狀態，兩者在同一個 process 內
object TrackingRepository {
    private const val MAX_TRAIL_POINTS = 300

    private val _location = MutableStateFlow<AMapLocation?>(null)
    val location: StateFlow<AMapLocation?> = _location.asStateFlow()

    private val _trail = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trail: StateFlow<List<TrackPoint>> = _trail.asStateFlow()

    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _trackFile = MutableStateFlow<String?>(null)
    val trackFile: StateFlow<String?> = _trackFile.asStateFlow()

    private val _gnssFile = MutableStateFlow<String?>(null)
    val gnssFile: StateFlow<String?> = _gnssFile.asStateFlow()

    private val _satellites = MutableStateFlow<List<SatelliteSignal>>(emptyList())
    val satellites: StateFlow<List<SatelliteSignal>> = _satellites.asStateFlow()

    fun setSatellites(list: List<SatelliteSignal>) {
        _satellites.value = list
    }

    private val _nativeFix = MutableStateFlow<Location?>(null)
    val nativeFix: StateFlow<Location?> = _nativeFix.asStateFlow()

    fun setNativeFix(location: Location?) {
        _nativeFix.value = location
    }

    fun onFix(loc: AMapLocation) {
        _location.value = loc
        if (loc.errorCode == 0) {
            _trail.value = (_trail.value + TrackPoint(loc.latitude, loc.longitude)).takeLast(MAX_TRAIL_POINTS)
            _pointCount.value += 1
        }
    }

    fun setRecording(on: Boolean) {
        _recording.value = on
    }

    fun setTrackFile(path: String?) {
        _trackFile.value = path
    }

    fun setGnssFile(path: String?) {
        _gnssFile.value = path
    }
}
