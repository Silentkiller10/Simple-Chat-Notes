package com.example.data

import org.json.JSONArray
import org.json.JSONObject

object MessageType {
    const val TEXT = "TEXT"
    const val IMAGE = "IMAGE"
    const val VIDEO = "VIDEO"
    const val FILE = "FILE"
    const val AUDIO = "AUDIO"
    const val CHECKLIST = "CHECKLIST"
    const val LINK = "LINK"
}

data class ChecklistItem(
    val id: String,
    val text: String,
    val isDone: Boolean
) {
    companion object {
        fun toJsonArray(items: List<ChecklistItem>): String {
            val jsonArray = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("text", item.text)
                obj.put("isDone", item.isDone)
                jsonArray.put(obj)
            }
            return jsonArray.toString()
        }

        fun fromJsonArray(jsonString: String?): List<ChecklistItem> {
            if (jsonString.isNullOrBlank()) return emptyList()
            val list = mutableListOf<ChecklistItem>()
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ChecklistItem(
                            id = obj.optString("id", i.toString()),
                            text = obj.optString("text", ""),
                            isDone = obj.optBoolean("isDone", false)
                        )
                    )
                }
            } catch (_: Exception) {
            }
            return list
        }
    }
}

data class StorageStats(
    val imagesBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val filesBytes: Long = 0L,
    val totalMediaBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val messageCount: Int = 0,
    val attachmentCount: Int = 0
)
