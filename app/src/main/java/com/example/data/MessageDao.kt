package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Transaction
    @Query("SELECT * FROM messages ORDER BY createdAt ASC")
    fun getAllMessagesAsc(): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun getMessagesForChatAsc(chatId: Long): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    fun getAllMessagesDesc(): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM messages WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedMessages(): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedMessagesForChat(chatId: Long): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM messages WHERE isFavorite = 1 ORDER BY createdAt ASC")
    fun getFavoriteMessages(): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isFavorite = 1 ORDER BY createdAt ASC")
    fun getFavoriteMessagesForChat(chatId: Long): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("""
        SELECT DISTINCT m.* FROM messages m 
        LEFT JOIN attachments a ON m.id = a.messageId 
        WHERE m.text LIKE '%' || :query || '%' 
           OR m.tags LIKE '%' || :query || '%' 
           OR m.linkUrl LIKE '%' || :query || '%' 
           OR m.linkDomain LIKE '%' || :query || '%' 
           OR a.filename LIKE '%' || :query || '%' 
           OR a.mimeType LIKE '%' || :query || '%' 
        ORDER BY m.createdAt DESC
    """)
    fun searchMessages(query: String): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("""
        SELECT DISTINCT m.* FROM messages m 
        LEFT JOIN attachments a ON m.id = a.messageId 
        WHERE m.chatId = :chatId AND (
           m.text LIKE '%' || :query || '%' 
           OR m.tags LIKE '%' || :query || '%' 
           OR m.linkUrl LIKE '%' || :query || '%' 
           OR m.linkDomain LIKE '%' || :query || '%' 
           OR a.filename LIKE '%' || :query || '%' 
           OR a.mimeType LIKE '%' || :query || '%' 
        )
        ORDER BY m.createdAt DESC
    """)
    fun searchMessagesInChat(chatId: Long, query: String): Flow<List<MessageWithAttachments>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    fun getLatestMessageForChat(chatId: Long): Flow<MessageEntity?>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    fun getMessageCountForChat(chatId: Long): Flow<Int>

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: Long)

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): MessageWithAttachments?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET isPinned = :isPinned, updatedAt = :timestamp WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE messages SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE messages SET text = :text, isEdited = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateText(id: Long, text: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE messages SET checklistJson = :checklistJson, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateChecklist(id: Long, checklistJson: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<Long>)

    // Attachments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>): List<Long>

    @Query("SELECT * FROM attachments ORDER BY id DESC")
    fun getAllAttachments(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE mimeType LIKE :mimePrefix || '%' ORDER BY id DESC")
    fun getAttachmentsByMimePrefix(mimePrefix: String): Flow<List<AttachmentEntity>>

    @Query("SELECT localPath FROM attachments WHERE messageId IN (:messageIds)")
    suspend fun getLocalPathsForMessages(messageIds: List<Long>): List<String>

    @Query("SELECT localPath FROM attachments")
    suspend fun getAllLocalPaths(): List<String>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getAttachmentById(id: Long): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: Long)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}
