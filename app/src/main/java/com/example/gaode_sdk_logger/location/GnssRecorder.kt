package com.example.gaode_sdk_logger.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssClock
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

// 同一次記錄的三份檔案：原始 GNSS 觀測量、衛星狀態、Android 原生 GPS 定位
class GnssRecorder(private val context: Context, sessionName: String) {

    val file: File
    val statusFile: File
    val gpsFile: File
    private val executor = Executors.newSingleThreadExecutor()
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false

    init {
        // 放在子資料夾，歷史紀錄只會列出 tracks 下的軌跡檔，不會混進來
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "tracks/gnss").apply { mkdirs() }
        file = File(dir, "gnss_$sessionName.csv")
        statusFile = File(dir, "gnss_status_$sessionName.csv")
        gpsFile = File(dir, "gps_fix_$sessionName.csv")
        append(file, MEASUREMENT_HEADER)
        append(statusFile, STATUS_HEADER)
        append(gpsFile, GPS_HEADER)
    }

    // 原始觀測量：每顆衛星一列，時鐘欄位每列重複
    private val callback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            val wallTimeMs = System.currentTimeMillis()
            val clock = event.clock
            val clockFields = listOf(
                wallTimeMs.toString(),
                clock.timeNanos.toString(),
                if (clock.hasFullBiasNanos()) clock.fullBiasNanos.toString() else "",
                if (clock.hasBiasNanos()) clock.biasNanos.toString() else "",
                if (clock.hasBiasUncertaintyNanos()) clock.biasUncertaintyNanos.toString() else "",
                if (clock.hasDriftNanosPerSecond()) clock.driftNanosPerSecond.toString() else "",
                clock.hardwareClockDiscontinuityCount.toString(),
                if (clock.hasLeapSecond()) clock.leapSecond.toString() else "",
                if (clock.hasDriftUncertaintyNanosPerSecond()) clock.driftUncertaintyNanosPerSecond.toString() else "",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && clock.hasElapsedRealtimeNanos()) {
                    clock.elapsedRealtimeNanos.toString()
                } else {
                    ""
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && clock.hasElapsedRealtimeUncertaintyNanos()) {
                    clock.elapsedRealtimeUncertaintyNanos.toString()
                } else {
                    ""
                }
            ).joinToString(",")

            val rows = event.measurements.joinToString("") { m ->
                clockFields + "," + measurementFields(clock, m) + "\n"
            }
            if (rows.isNotEmpty()) append(file, rows)
        }
    }

    // 衛星狀態（仰角、方位角、C/N0、是否用於定位）：寫檔，同時給即時頁的天空圖使用
    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val now = System.currentTimeMillis()
            val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            val rows = (0 until status.satelliteCount).joinToString("") { i ->
                listOf(
                    now.toString(),
                    status.getSvid(i).toString(),
                    constellationName(status.getConstellationType(i)),
                    fmt("%.2f", status.getCn0DbHz(i)),
                    fmt("%.2f", status.getElevationDegrees(i)),
                    fmt("%.2f", status.getAzimuthDegrees(i)),
                    status.usedInFix(i).toString(),
                    status.hasAlmanacData(i).toString(),
                    status.hasEphemerisData(i).toString(),
                    if (modern && status.hasCarrierFrequencyHz(i)) fmt("%.0f", status.getCarrierFrequencyHz(i)) else "",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && status.hasBasebandCn0DbHz(i)) {
                        fmt("%.2f", status.getBasebandCn0DbHz(i))
                    } else {
                        ""
                    }
                ).joinToString(",") + "\n"
            }
            if (rows.isNotEmpty()) append(statusFile, rows)

            val satellites = (0 until status.satelliteCount).map { i ->
                SatelliteSignal(
                    constellation = constellationName(status.getConstellationType(i)),
                    svid = status.getSvid(i),
                    cn0DbHz = status.getCn0DbHz(i),
                    elevationDeg = status.getElevationDegrees(i),
                    azimuthDeg = status.getAzimuthDegrees(i),
                    usedInFix = status.usedInFix(i)
                )
            }
            TrackingRepository.setSatellites(satellites)
        }
    }

    // Android 原生 GPS 定位：同時更新對比頁，並逐筆寫檔
    private val nativeListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            TrackingRepository.setNativeFix(location)
            val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            val row = listOf(
                System.currentTimeMillis().toString(),
                location.time.toString(),
                location.elapsedRealtimeNanos.toString(),
                if (modern && location.hasElapsedRealtimeUncertaintyNanos()) {
                    location.elapsedRealtimeUncertaintyNanos.toString()
                } else {
                    ""
                },
                fmt("%.7f", location.latitude),
                fmt("%.7f", location.longitude),
                if (location.hasAltitude()) fmt("%.2f", location.altitude) else "",
                if (location.hasAccuracy()) fmt("%.2f", location.accuracy) else "",
                if (modern && location.hasVerticalAccuracy()) fmt("%.2f", location.verticalAccuracyMeters) else "",
                if (location.hasSpeed()) fmt("%.3f", location.speed) else "",
                if (modern && location.hasSpeedAccuracy()) fmt("%.3f", location.speedAccuracyMetersPerSecond) else "",
                if (location.hasBearing()) fmt("%.2f", location.bearing) else "",
                if (modern && location.hasBearingAccuracy()) fmt("%.2f", location.bearingAccuracyDegrees) else "",
                location.provider ?: "",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock.toString() else "",
                // GPS 提供者會在 extras 放衛星數與精度因子（hdop 等），另外保留完整鍵值
                location.extras?.let { if (it.containsKey("satellites")) it.getInt("satellites").toString() else "" } ?: "",
                bundleText(location.extras)
            ).joinToString(",", postfix = "\n")
            append(gpsFile, row)
        }
    }

    // 需要 ACCESS_FINE_LOCATION 且系統定位已開啟
    @SuppressLint("MissingPermission")
    fun start() {
        if (registered) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        registered = locationManager.registerGnssMeasurementsCallback(callback, handler)
        locationManager.registerGnssStatusCallback(statusCallback, handler)
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, nativeListener, Looper.getMainLooper()
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (registered) locationManager.unregisterGnssMeasurementsCallback(callback)
        locationManager.unregisterGnssStatusCallback(statusCallback)
        locationManager.removeUpdates(nativeListener)
        registered = false
        TrackingRepository.setSatellites(emptyList())
        TrackingRepository.setNativeFix(null)
        executor.shutdown()
    }

    // 停止後 callback 可能仍會送達，寫入被拒絕時直接忽略，避免崩潰
    private fun append(target: File, text: String) {
        try {
            executor.execute { target.appendText(text) }
        } catch (_: RejectedExecutionException) {
            // 已經停止記錄
        }
    }

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "北斗"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> "其他"
    }

    private fun measurementFields(clock: GnssClock, m: GnssMeasurement): String {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        // 接收時間減去衛星發射時間，單位 ns。偽距 = (此值 + 整數毫秒模糊值) × 光速
        // 這裡只寫出不足 1 ms 的部分（pseudorange_frac_m），整數毫秒需要另外解模糊
        val rxMinusTx: Double? = if (clock.hasFullBiasNanos()) {
            val bias = clock.fullBiasNanos.toDouble() + (if (clock.hasBiasNanos()) clock.biasNanos else 0.0)
            clock.timeNanos.toDouble() + m.timeOffsetNanos - bias - m.receivedSvTimeNanos.toDouble()
        } else {
            null
        }
        val fracMeters = rxMinusTx?.let { (it % 1_000_000.0) * 1e-9 * SPEED_OF_LIGHT }

        return listOf(
            m.svid.toString(),
            if (modern) m.constellationType.toString() else "",
            m.timeOffsetNanos.toString(),
            m.state.toString(),
            m.receivedSvTimeNanos.toString(),
            m.receivedSvTimeUncertaintyNanos.toString(),
            m.cn0DbHz.toString(),
            m.pseudorangeRateMetersPerSecond.toString(),
            m.pseudorangeRateUncertaintyMetersPerSecond.toString(),
            m.accumulatedDeltaRangeState.toString(),
            m.accumulatedDeltaRangeMeters.toString(),
            m.accumulatedDeltaRangeUncertaintyMeters.toString(),
            if (modern && m.hasCarrierFrequencyHz()) m.carrierFrequencyHz.toString() else "",
            if (modern && m.hasCarrierCycles()) m.carrierCycles.toString() else "",
            if (modern && m.hasCarrierPhase()) m.carrierPhase.toString() else "",
            if (modern && m.hasCarrierPhaseUncertainty()) m.carrierPhaseUncertainty.toString() else "",
            if (modern) m.multipathIndicator.toString() else "",
            if (modern && m.hasSnrInDb()) m.snrInDb.toString() else "",
            rxMinusTx?.let { fmt("%.3f", it) } ?: "",
            fracMeters?.let { fmt("%.4f", it) } ?: "",
            if (modern && m.hasAutomaticGainControlLevelDb()) m.automaticGainControlLevelDb.toString() else "",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && m.hasCodeType()) m.codeType ?: "" else "",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && m.hasBasebandCn0DbHz()) m.basebandCn0DbHz.toString() else "",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && m.hasSatelliteInterSignalBiasNanos()) {
                m.satelliteInterSignalBiasNanos.toString()
            } else {
                ""
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && m.hasSatelliteInterSignalBiasUncertaintyNanos()) {
                m.satelliteInterSignalBiasUncertaintyNanos.toString()
            } else {
                ""
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && m.hasFullInterSignalBiasNanos()) {
                m.fullInterSignalBiasNanos.toString()
            } else {
                ""
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && m.hasFullInterSignalBiasUncertaintyNanos()) {
                m.fullInterSignalBiasUncertaintyNanos.toString()
            } else {
                ""
            }
        ).joinToString(",")
    }

    private fun fmt(pattern: String, value: Number): String = String.format(Locale.US, pattern, value)

    // 把 Bundle 轉成 "鍵=值|鍵=值"，逗號與換行會被替換，避免破壞 CSV 結構
    private fun bundleText(bundle: Bundle?): String =
        bundle?.keySet()?.joinToString("|") { "$it=${bundle.get(it)}" }
            ?.replace(",", ";")
            ?.replace("\n", " ")
            ?: ""

    companion object {
        private const val SPEED_OF_LIGHT = 299792458.0

        private const val MEASUREMENT_HEADER = "wall_time_ms,clock_time_ns,clock_full_bias_ns,clock_bias_ns," +
            "clock_bias_uncertainty_ns,clock_drift_ns_per_s,clock_hw_discontinuity_count,clock_leap_second," +
            "clock_drift_uncertainty_ns_per_s,clock_elapsed_realtime_ns,clock_elapsed_realtime_uncertainty_ns," +
            "svid,constellation,time_offset_ns,state,received_sv_time_ns,received_sv_time_uncertainty_ns," +
            "cn0_dbhz,pseudorange_rate_mps,pseudorange_rate_uncertainty_mps,adr_state,adr_m," +
            "adr_uncertainty_m,carrier_freq_hz,carrier_cycles,carrier_phase,carrier_phase_uncertainty," +
            "multipath_indicator,snr_db,rx_minus_tx_ns,pseudorange_frac_m,agc_db,code_type," +
            "baseband_cn0_dbhz,sat_inter_signal_bias_ns,sat_inter_signal_bias_uncertainty_ns," +
            "full_inter_signal_bias_ns,full_inter_signal_bias_uncertainty_ns\n"

        private const val STATUS_HEADER = "wall_time_ms,svid,constellation,cn0_dbhz,elevation_deg," +
            "azimuth_deg,used_in_fix,has_almanac,has_ephemeris,carrier_freq_hz,baseband_cn0_dbhz\n"

        private const val GPS_HEADER = "wall_time_ms,fix_time_ms,elapsed_realtime_ns,elapsed_realtime_uncertainty_ns," +
            "latitude,longitude," +
            "altitude_m,accuracy_m,vertical_accuracy_m,speed_mps,speed_accuracy_mps,bearing_deg," +
            "bearing_accuracy_deg,provider,is_mock,satellites,extras\n"
    }
}
