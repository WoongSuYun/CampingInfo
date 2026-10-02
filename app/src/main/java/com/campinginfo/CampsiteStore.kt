package com.campinginfo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CampsiteStore(context: Context) {
    private val prefs = context.getSharedPreferences("camping_note", Context.MODE_PRIVATE)
    fun getAppTitle(): String = prefs.getString("app_title", "하웅캠핑") ?: "하웅캠핑"
    fun setAppTitle(title: String) { prefs.edit().putString("app_title", title).apply() }
    fun load(): List<Campsite> = runCatching {
        val array = JSONArray(prefs.getString("campsites", "[]"))
        List(array.length()) { i -> array.getJSONObject(i).toCampsite() }
    }.getOrDefault(emptyList())
    fun save(items: List<Campsite>) {
        val array = JSONArray(); items.forEach { array.put(it.toJson()) }
        prefs.edit().putString("campsites", array.toString()).apply()
    }
    fun exportJson(): String {
        val array = JSONArray(); load().forEach { array.put(it.toJson()) }
        return array.toString(2)
    }
    fun importJson(json: String): List<Campsite>? = runCatching {
        val array = JSONArray(json)
        List(array.length()) { i -> array.getJSONObject(i).toCampsite() }.also { save(it) }
    }.getOrNull()
    private fun Campsite.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("region", region); put("address", address)
        put("price", price); put("phone", phone); put("facilities", facilities); put("memo", memo)
        put("lat", latitude); put("lng", longitude); put("favorite", favorite)
        put("petFriendly", petFriendly); put("fenced", fenced); put("privateBathroom", privateBathroom)
        put("electricity", electricity); put("shower", shower); put("parking", parking); put("wifi", wifi); put("visitCount", visitCount)
        put("imageUri", imageUri)
        put("imageUris", JSONArray(imageUris))
        put("hasTarp", hasTarp); put("checkInTime", checkInTime); put("checkOutTime", checkOutTime)
    }
    private fun JSONObject.toCampsite() = Campsite(
        id = getLong("id"), name = getString("name"), region = when (getString("region")) { "강원" -> "강원도"; "충청" -> "충청도"; "전라" -> "전라도"; "경상" -> "경상도"; "제주" -> "제주도"; else -> getString("region") },
        address = optString("address"), price = optString("price"), phone = optString("phone"), facilities = optString("facilities"), memo = optString("memo"),
        latitude = if (isNull("lat")) null else optDouble("lat"), longitude = if (isNull("lng")) null else optDouble("lng"), favorite = optBoolean("favorite"),
        petFriendly = optBoolean("petFriendly"), fenced = optBoolean("fenced"), privateBathroom = optBoolean("privateBathroom"),
        electricity = optBoolean("electricity"), shower = optBoolean("shower"), parking = optBoolean("parking"), wifi = optBoolean("wifi"), visitCount = optInt("visitCount", 0), imageUri = optString("imageUri").takeIf { it.isNotBlank() }, imageUris = optJSONArray("imageUris")?.let { array -> List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() } } ?: optString("imageUri").takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(), hasTarp = optBoolean("hasTarp"), checkInTime = optString("checkInTime"), checkOutTime = optString("checkOutTime")
    )
}
