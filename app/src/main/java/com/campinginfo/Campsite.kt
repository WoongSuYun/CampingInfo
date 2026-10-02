package com.campinginfo

data class Campsite(
    val id: Long = System.currentTimeMillis(), val name: String, val region: String,
    val address: String = "", val price: String = "", val phone: String = "",
    val facilities: String = "", val memo: String = "", val latitude: Double? = null,
    val longitude: Double? = null, val favorite: Boolean = false,
    val petFriendly: Boolean = false, val fenced: Boolean = false,
    val privateBathroom: Boolean = false, val electricity: Boolean = false,
    val shower: Boolean = false, val parking: Boolean = false, val wifi: Boolean = false,
    val visitCount: Int = 0, val imageUri: String? = null, val imageUris: List<String> = emptyList(),
    val hasTarp: Boolean = false, val checkInTime: String = "", val checkOutTime: String = ""
)

object Regions {
    val all = listOf("전체", "서울", "경기", "인천", "강원도", "충청도", "전라도", "경상도", "제주도")
    val editable = all.drop(1)
}
