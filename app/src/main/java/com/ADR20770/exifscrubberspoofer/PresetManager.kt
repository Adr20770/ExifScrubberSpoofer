package com.ADR20770.exifscrubberspoofer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CustomPreset(
    val name: String,
    val make: String,
    val model: String,
    val date: String,
    val lat: String,
    val lon: String
)

class PresetManager(context: Context) {
    private val prefs = context.getSharedPreferences("custom_presets", Context.MODE_PRIVATE)

    fun getPresets(): List<CustomPreset> {
        val jsonStr = prefs.getString("presets_list", "[]") ?: "[]"
        val list = mutableListOf<CustomPreset>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CustomPreset(
                        name = obj.optString("name"),
                        make = obj.optString("make"),
                        model = obj.optString("model"),
                        date = obj.optString("date"),
                        lat = obj.optString("lat"),
                        lon = obj.optString("lon")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun savePreset(preset: CustomPreset) {
        val current = getPresets().toMutableList()
        current.removeAll { it.name.equals(preset.name, ignoreCase = true) }
        current.add(preset)

        val jsonArray = JSONArray()
        for (p in current) {
            val obj = JSONObject().apply {
                put("name", p.name)
                put("make", p.make)
                put("model", p.model)
                put("date", p.date)
                put("lat", p.lat)
                put("lon", p.lon)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("presets_list", jsonArray.toString()).apply()
    }
}
