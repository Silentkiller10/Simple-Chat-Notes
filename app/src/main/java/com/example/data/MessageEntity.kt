package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["createdAt"]),
        Index(value = ["isPinned"]),
        Index(value = ["isFavorite"]),
        Index(value = ["type"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "1")
    val chatId: Long = 1L,
    val type: String, // "TEXT", "IMAGE", "VIDEO", "FILE", "AUDIO", "CHECKLIST", "LINK"
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isEdited: Boolean = false,
    val tags: String = "", // e.g. "#work,#ideas"
    val checklistJson: String = "", // JSON array of items
    val linkUrl: String? = null,
    val linkDomain: String? = null
)
