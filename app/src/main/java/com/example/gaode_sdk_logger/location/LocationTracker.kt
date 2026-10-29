package com.example.gaode_sdk_logger.location

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationClientOption.AMapLocationMode
import com.amap.api.maps.MapsInitializer

class LocationTracker(context: Context, onLocation: (AMapLocation) -> Unit) {

    private val client = AMapLocationClient(context.applicationContext)

    init {
        val option = AMapLocationClientOption().apply {
            setLocationMode(AMapLocationMode.Hight_Accuracy)
            setOnceLocation(false)
            setInterval(1000L)
            setNeedAddress(true)
            setSensorEnable(true)
        }
        client.setLocationOption(option)
        client.setLocationListener { loc -> onLocation(loc) }
    }

    fun start() = client.startLocation()

    fun destroy() {
        client.stopLocation()
        client.onDestroy()
    }

    companion object {
        // 高德 SDK 要求在建立 AMapLocationClient 之前完成隱私合規設定
        fun setPrivacyConsent(context: Context) {
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)
            MapsInitializer.updatePrivacyShow(context, true, true)
            MapsInitializer.updatePrivacyAgree(context, true)
        }
    }
}
