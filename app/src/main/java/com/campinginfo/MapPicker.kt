package com.campinginfo

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kakao.vectormap.*

@Composable
fun MapPicker(initialLat: Double?, initialLng: Double?) {
    val context = LocalContext.current
    var mapError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Text("검색된 위치", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (BuildConfig.KAKAO_MAP_KEY.isBlank()) {
            Text("카카오맵 키가 설정되지 않았습니다.", color = MaterialTheme.colorScheme.error)
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = {
                        MapView(context).also { mapView ->
                            mapView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            mapView.start(object : MapLifeCycleCallback() {
                                override fun onMapDestroy() = Unit
                                override fun onMapError(error: Exception?) { mapError = error?.message ?: "카카오맵을 불러오지 못했습니다. 앱 키 등록을 확인하세요." }
                            }, object : KakaoMapReadyCallback() {
                                override fun getPosition() = LatLng.from(initialLat ?: 36.5, initialLng ?: 127.8)
                                override fun getZoomLevel() = if (initialLat != null && initialLng != null) 17 else 8
                                override fun onMapReady(kakaoMap: KakaoMap) = Unit
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            mapError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        }
    }
}
