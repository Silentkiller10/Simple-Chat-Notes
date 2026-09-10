package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["messageId"]),
        Index(value = ["mimeType"])
    ]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val messageId: Long,
    val localPath: String, // Absolute path in app internal filesDir
    val filename: String,
    val mimeType: String,
    val fileSize: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailPath: String? = null
)
