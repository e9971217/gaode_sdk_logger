package com.example.gaode_sdk_logger.location

import android.content.Context
import com.amap.api.location.AMapLocation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

// 每次開始記錄建立一個 CSV 檔，寫入在背景執行緒進行，避免卡住畫面
class TrackWriter(context: Context) {

    val file: File
    private val executor = Executors.newSingleThreadExecutor()

    init {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "tracks").apply { mkdirs() }
        val name = "track_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"
        file = File(dir, name)
        append(HEADER)
    }

    fun write(loc: AMapLocation) {
        append(if (loc.errorCode == 0) validRow(loc) else errorRow(loc))
    }

    fun close() {
        executor.shutdown()
    }

    private fun append(text: String) {
        try {
            executor.execute { file.appendText(text) }
        } catch (_: RejectedExecutionException) {
            // 已經停止記錄
        }
    }

    // 高德欄位共 25 欄，順序需與 HEADER 一致
    private fun validRow(loc: AMapLocation): String = listOf(
        loc.time.toString(),
        fmt("%.7f", loc.latitude),
        fmt("%.7f", loc.longitude),
        fmt("%.1f", loc.altitude),
        fmt("%.2f", loc.speed),
        fmt("%.1f", loc.bearing),
        fmt("%.1f", loc.accuracy),
        loc.gpsAccuracyStatus.toString(),
        loc.satellites.toString(),
        loc.locationType.toString(),
        clean(loc.provider),
        loc.trustedLevel.toString(),
        loc.isMock.toString(),
        loc.isFixLastLocation.toString(),
        loc.isOffset.toString(),
        clean(loc.coordType),
        "0",
        "",
        clean(loc.country),
        clean(loc.province),
        clean(loc.city),
        clean(loc.district),
        clean(loc.street),
        clean(loc.adCode),
        clean(loc.address),
        clean(loc.poiName),
        clean(loc.road),
        clean(loc.streetNum),
        clean(loc.aoiName),
        clean(loc.buildingId),
        clean(loc.floor),
        clean(loc.cityCode),
        clean(loc.locationDetail),
        clean(loc.description),
        loc.conScenario.toString()
    ).joinToString(",", postfix = "\n")

    // 定位失敗時座標與位址留空，只保留時間、錯誤碼與錯誤說明
    private fun errorRow(loc: AMapLocation): String = (
        listOf(loc.time.toString()) + List(15) { "" } +
            listOf(loc.errorCode.toString(), clean(loc.errorInfo)) + List(17) { "" }
        ).joinToString(",", postfix = "\n")

    private fun clean(value: String?): String = (value ?: "").replace(",", ";").replace("\n", " ")

    private fun fmt(pattern: String, value: Number): String = String.format(Locale.US, pattern, value)

    companion object {
        private const val HEADER = "time_ms,latitude,longitude,altitude,speed_ms,bearing,accuracy_m," +
            "gps_accuracy_status,satellites,location_type,provider,trusted_level,is_mock," +
            "is_fix_last_location,is_offset,coord_type,error_code,error_info," +
            "country,province,city,district,street,adcode,address," +
            "poi_name,road,street_num,aoi_name,building_id,floor,city_code,location_detail,description,con_scenario\n"
    }
}
