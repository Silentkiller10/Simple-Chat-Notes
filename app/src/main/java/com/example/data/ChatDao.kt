package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id LIMIT 1")
    fun getChatByIdFlow(id: Long): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :id LIMIT 1")
    suspend fun getChatById(id: Long): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameChat(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET name = :name, iconName = :iconName, colorHex = :colorHex, imageUri = :imageUri, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateChatAppearance(
        id: Long,
        name: String,
        iconName: String,
        colorHex: Long,
        imageUri: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE chats SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    @Query("UPDATE chats SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchChat(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChat(id: Long)

    @Query("SELECT COUNT(*) FROM chats")
    suspend fun getChatCount(): Int
}
