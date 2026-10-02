package com.campinginfo

data class Campsite(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val region: String,
    val address: String = "",
    val price: String = "",
    val phone: String = "",
    val facilities: String = "",
    val memo: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val favorite: Boolean = false
)

object Regions {
    val all = listOf("전체", "서울", "경기", "인천", "강원", "충청", "전라", "경상", "제주")
    val editable = all.drop(1)
}
