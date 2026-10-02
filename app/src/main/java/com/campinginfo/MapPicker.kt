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
fun MapPicker(initialLat: Double?, initialLng: Double?, onPicked: (Double, Double) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Pair<Double, Double>?>(initialLat?.let { lat -> initialLng?.let { lat to it } }) }
    var mapReady by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Text("지도를 눌러 위치를 선택하세요", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (BuildConfig.KAKAO_MAP_KEY.isBlank()) {
            Text("카카오 지도 키가 설정되지 않았습니다. local.properties의 kakaoMapKey를 설정해 주세요.", color = MaterialTheme.colorScheme.error)
        } else {
            AndroidView(
                factory = {
                    MapView(context).also { mapView ->
                        mapView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        mapView.start(object : MapLifeCycleCallback() {
                            override fun onMapDestroy() = Unit
                            override fun onMapError(error: Exception?) = Unit
                        }, object : KakaoMapReadyCallback() {
                            override fun getPosition() = LatLng.from(initialLat ?: 36.5, initialLng ?: 127.8)
                            override fun getZoomLevel() = 8
                            override fun onMapReady(kakaoMap: KakaoMap) {
                                mapReady = true
                                kakaoMap.setOnMapClickListener { _, latLng, _, _ ->
                                    selected = latLng.latitude to latLng.longitude
                                }
                            }
                        })
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
        selected?.let { (lat, lng) ->
            Text("선택 좌표: ${"%.6f".format(lat)}, ${"%.6f".format(lng)}", modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = { onPicked(lat, lng) }, modifier = Modifier.fillMaxWidth(), enabled = mapReady) { Text("이 위치 저장") }
        }
    }
}
