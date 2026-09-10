package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val description: String = "Notes & messages",
    val iconName: String = "bookmark", // "bookmark", "folder", "work", "idea", "star", "code", "heart", "checklist"
    val imageUri: String? = null,
    val colorHex: Long = 0xFF2AABEE,   // Default Telegram Blue
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
