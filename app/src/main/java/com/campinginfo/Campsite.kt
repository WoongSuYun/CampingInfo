package com.campinginfo

data class VisitRecord(val date: String = "", val review: String = "", @Deprecated("방문기록 평점은 더 이상 사용하지 않습니다") val rating: Int = 0)
data class SharedImage(val mimeType: String, val base64: String)

data class Campsite(
    val id: Long = System.currentTimeMillis(), val name: String, val region: String,
    val address: String = "", val price: String = "", val phone: String = "",
    val facilities: String = "", val memo: String = "", val latitude: Double? = null,
    val longitude: Double? = null, val favorite: Boolean = false,
    val petFriendly: Boolean = false, val dogPlayground: Boolean = false, val fenced: Boolean = false,
    val privateBathroom: Boolean = false, val electricity: Boolean = false,
    val shower: Boolean = false, val parking: Boolean = false, val wifi: Boolean = false, val wifiPassword: String = "",
    val visitCount: Int = 0, val imageUri: String? = null, val imageUris: List<String> = emptyList(),
    val hasTarp: Boolean = false, val checkInTime: String = "", val checkOutTime: String = "",
    val quietTime: String = "", val status: String = "가고 싶음", val visitDate: String = "",
    val rating: Int = 0, val review: String = "", val visits: List<VisitRecord> = emptyList(), val sharedImages: List<SharedImage> = emptyList()
)

val Campsite.allVisits: List<VisitRecord>
    get() = visits.ifEmpty { if (visitDate.isNotBlank() || review.isNotBlank()) listOf(VisitRecord(visitDate, review)) else emptyList() }

object Regions {
    val all = listOf("전체", "서울", "경기", "인천", "강원도", "충청도", "전라도", "경상도", "제주도")
    val editable = all.drop(1)
}
