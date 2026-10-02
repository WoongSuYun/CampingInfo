package com.campinginfo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CampsiteStore(context: Context) {
    private val prefs = context.getSharedPreferences("camping_note", Context.MODE_PRIVATE)

    fun load(): List<Campsite> = runCatching {
        val array = JSONArray(prefs.getString("campsites", "[]"))
        List(array.length()) { i -> array.getJSONObject(i).toCampsite() }
    }.getOrDefault(emptyList())

    fun save(items: List<Campsite>) {
        val array = JSONArray()
        items.forEach { c -> array.put(c.toJson()) }
        prefs.edit().putString("campsites", array.toString()).apply()
    }

    private fun Campsite.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("region", region); put("address", address)
        put("price", price); put("phone", phone); put("facilities", facilities); put("memo", memo)
        put("lat", latitude); put("lng", longitude); put("favorite", favorite)
    }
    private fun JSONObject.toCampsite() = Campsite(
        id = getLong("id"), name = getString("name"), region = getString("region"),
        address = optString("address"), price = optString("price"), phone = optString("phone"),
        facilities = optString("facilities"), memo = optString("memo"),
        latitude = if (isNull("lat")) null else optDouble("lat"),
        longitude = if (isNull("lng")) null else optDouble("lng"), favorite = optBoolean("favorite")
    )
}
