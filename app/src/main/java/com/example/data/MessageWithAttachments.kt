package com.example.data

import androidx.room.Embedded
import androidx.room.Relation

data class MessageWithAttachments(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId"
    )
    val attachments: List<AttachmentEntity> = emptyList()
)
