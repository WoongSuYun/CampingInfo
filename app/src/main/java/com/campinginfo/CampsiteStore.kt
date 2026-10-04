package com.campinginfo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CampsiteStore(context: Context) {
    private val prefs = context.getSharedPreferences("camping_note", Context.MODE_PRIVATE)
    fun getAppTitle(): String = prefs.getString("app_title", "하웅캠핑") ?: "하웅캠핑"
    fun setAppTitle(title: String) { prefs.edit().putString("app_title", title).apply() }
    fun getFilterPresets(): String {
        val current = prefs.getString("filter_presets", "[]") ?: "[]"
        if (current != "[]") return current
        val legacy = prefs.getString("filter_preset", "") ?: ""
        return if (legacy.isBlank()) "[]" else JSONArray().put(JSONObject().put("name", "기존 저장 필터").put("value", legacy)).toString()
    }
    fun setFilterPresets(value: String) { prefs.edit().putString("filter_presets", value).apply() }
    fun load(): List<Campsite> = runCatching {
        val array = JSONArray(prefs.getString("campsites", "[]"))
        List(array.length()) { i -> array.getJSONObject(i).toCampsite() }
    }.getOrDefault(emptyList())
    fun save(items: List<Campsite>) {
        val array = JSONArray(); items.forEach { array.put(it.toJson()) }
        prefs.edit().putString("campsites", array.toString()).apply()
    }
    fun exportJson(): String {
        return exportJson(load())
    }
    fun exportJson(items: List<Campsite>): String {
        val array = JSONArray(); items.forEach { array.put(it.toJson()) }
        return array.toString(2)
    }
    fun importJson(json: String): List<Campsite>? = runCatching {
        val array = JSONArray(json)
        List(array.length()) { i -> array.getJSONObject(i).toCampsite() }.also { save(it) }
    }.getOrNull()
    /** 항목별 가져오기: 기존 목록은 유지하고 가져온 캠핑장만 추가한다. */
    fun mergeJson(json: String): List<Campsite>? = runCatching {
        val imported = parseJson(json)
        val current = load().toMutableList()
        val ids = current.map { it.id }.toMutableSet()
        imported.forEach { item ->
            val uniqueId = if (item.id in ids) generateUniqueId(ids) else item.id
            current += item.copy(id = uniqueId)
            ids += uniqueId
        }
        save(current)
        current
    }.getOrNull()
    private fun parseJson(json: String): List<Campsite> {
        val array = JSONArray(json)
        return List(array.length()) { i -> array.getJSONObject(i).toCampsite() }
    }
    private fun generateUniqueId(ids: Set<Long>): Long {
        var id = System.currentTimeMillis()
        while (id in ids) id++
        return id
    }
    private fun Campsite.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("region", region); put("address", address)
        put("price", price); put("phone", phone); put("facilities", facilities); put("memo", memo)
        put("lat", latitude); put("lng", longitude); put("favorite", favorite)
        put("petFriendly", petFriendly); put("fenced", fenced); put("privateBathroom", privateBathroom)
        put("electricity", electricity); put("shower", shower); put("parking", parking); put("wifi", wifi); put("visitCount", visitCount)
        put("imageUri", imageUri)
        put("imageUris", JSONArray(imageUris))
        put("hasTarp", hasTarp); put("checkInTime", checkInTime); put("checkOutTime", checkOutTime); put("quietTime", quietTime); put("status", status); put("visitDate", visitDate); put("rating", rating); put("review", review); put("visits", JSONArray(visits.map { visit -> JSONObject().apply { put("date", visit.date); put("rating", visit.rating); put("review", visit.review) } }))
    }
    private fun JSONObject.toCampsite() = Campsite(
        id = getLong("id"), name = getString("name"), region = when (getString("region")) { "강원" -> "강원도"; "충청" -> "충청도"; "전라" -> "전라도"; "경상" -> "경상도"; "제주" -> "제주도"; else -> getString("region") },
        address = optString("address"), price = optString("price"), phone = optString("phone"), facilities = optString("facilities"), memo = optString("memo"),
        latitude = if (isNull("lat")) null else optDouble("lat"), longitude = if (isNull("lng")) null else optDouble("lng"), favorite = optBoolean("favorite"),
        petFriendly = optBoolean("petFriendly"), fenced = optBoolean("fenced"), privateBathroom = optBoolean("privateBathroom"),
        electricity = optBoolean("electricity"), shower = optBoolean("shower"), parking = optBoolean("parking"), wifi = optBoolean("wifi"), visitCount = optInt("visitCount", 0), imageUri = optString("imageUri").takeIf { it.isNotBlank() }, imageUris = optJSONArray("imageUris")?.let { array -> List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() } } ?: optString("imageUri").takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(), hasTarp = optBoolean("hasTarp"), checkInTime = optString("checkInTime"), checkOutTime = optString("checkOutTime"), quietTime = optString("quietTime"), status = optString("status", "가고 싶음"), visitDate = optString("visitDate"), rating = optInt("rating", 0), review = optString("review"), visits = optJSONArray("visits")?.let { array -> List(array.length()) { index -> array.getJSONObject(index).let { VisitRecord(it.optString("date"), it.optInt("rating", 0), it.optString("review")) } } } ?: emptyList()
    )
}
