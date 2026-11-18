package com.ADR20770.exifscrubberspoofer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ScrubHistoryItem(
    val uriString: String,
    val timestamp: Long,
    val success: Boolean
)

class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("scrub_history", Context.MODE_PRIVATE)

    fun getHistory(): List<ScrubHistoryItem> {
        val jsonStr = prefs.getString("history_list", "[]") ?: "[]"
        val list = mutableListOf<ScrubHistoryItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ScrubHistoryItem(
                        uriString = obj.optString("uri"),
                        timestamp = obj.optLong("time"),
                        success = obj.optBoolean("success")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addHistoryItem(uriString: String, success: Boolean) {
        val current = getHistory().toMutableList()
        current.add(0, ScrubHistoryItem(uriString, System.currentTimeMillis(), success))
        if (current.size > 10) {
            current.removeAt(current.size - 1)
        }

        val jsonArray = JSONArray()
        for (item in current) {
            val obj = JSONObject().apply {
                put("uri", item.uriString)
                put("time", item.timestamp)
                put("success", item.success)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("history_list", jsonArray.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("history_list").apply()
    }
}
