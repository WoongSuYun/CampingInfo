package com.campinginfo

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class CampingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Empty key lets the rest of the app work; the map displays after local.properties is set.
        if (BuildConfig.KAKAO_MAP_KEY.isNotBlank()) KakaoMapSdk.init(this, BuildConfig.KAKAO_MAP_KEY)
    }
}
