package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.media.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class AppRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val messageDao = database.messageDao()
    private val chatDao = database.chatDao()
    private val mediaDir: File = File(context.filesDir, "saved_media").apply { mkdirs() }

    // Chat operations
    fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()
    fun getChatByIdFlow(id: Long): Flow<ChatEntity?> = chatDao.getChatByIdFlow(id)
    suspend fun getChatById(id: Long): ChatEntity? = withContext(Dispatchers.IO) { chatDao.getChatById(id) }
    suspend fun getChatCount(): Int = withContext(Dispatchers.IO) { chatDao.getChatCount() }

    suspend fun ensureDefaultChat() = withContext(Dispatchers.IO) {
        if (chatDao.getChatCount() == 0) {
            chatDao.insertChat(
                ChatEntity(
                    id = 1L,
                    name = "Saved",
                    description = "Chat with yourself",
                    iconName = "bookmark",
                    colorHex = 0xFF2AABEE
                )
            )
        }
    }

    suspend fun createChat(
        name: String,
        description: String = "Notes & messages",
        iconName: String = "bookmark",
        colorHex: Long = 0xFF2AABEE,
        imageUri: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val copiedPath = imageUri?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                copyUriToInternalStorage(uri)?.file?.absolutePath
            } catch (e: Exception) {
                null
            }
        }

        chatDao.insertChat(
            ChatEntity(
                name = name.trim().ifEmpty { "New Chat" },
                description = description.trim(),
                iconName = iconName,
                imageUri = copiedPath,
                colorHex = colorHex,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun renameChat(id: Long, newName: String) = withContext(Dispatchers.IO) {
        chatDao.renameChat(id, newName.trim().ifEmpty { "Chat" })
    }

    suspend fun updateChatAppearance(id: Long, name: String, iconName: String, colorHex: Long, imageUri: String?) = withContext(Dispatchers.IO) {
        val copiedPath = imageUri?.let { uriString ->
            // If it's already a local path from our internal storage, don't copy it again
            if (uriString.startsWith(mediaDir.absolutePath)) {
                uriString
            } else {
                try {
                    val uri = Uri.parse(uriString)
                    copyUriToInternalStorage(uri)?.file?.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
        }
        chatDao.updateChatAppearance(id, name.trim().ifEmpty { "Chat" }, iconName, colorHex, copiedPath)
    }

    suspend fun deleteChat(id: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteChat(id)
        messageDao.deleteMessagesByChatId(id)
    }

    // Message operations
    fun getAllMessagesAsc(): Flow<List<MessageWithAttachments>> = messageDao.getAllMessagesAsc()
    fun getMessagesForChatAsc(chatId: Long): Flow<List<MessageWithAttachments>> = messageDao.getMessagesForChatAsc(chatId)
    fun getPinnedMessages(): Flow<List<MessageWithAttachments>> = messageDao.getPinnedMessages()
    fun getPinnedMessagesForChat(chatId: Long): Flow<List<MessageWithAttachments>> = messageDao.getPinnedMessagesForChat(chatId)
    fun getFavoriteMessages(): Flow<List<MessageWithAttachments>> = messageDao.getFavoriteMessages()
    fun getFavoriteMessagesForChat(chatId: Long): Flow<List<MessageWithAttachments>> = messageDao.getFavoriteMessagesForChat(chatId)
    fun searchMessages(query: String): Flow<List<MessageWithAttachments>> = messageDao.searchMessages(query)
    fun searchMessagesInChat(chatId: Long, query: String): Flow<List<MessageWithAttachments>> = messageDao.searchMessagesInChat(chatId, query)
    fun getLatestMessageForChat(chatId: Long): Flow<MessageEntity?> = messageDao.getLatestMessageForChat(chatId)
    fun getMessageCountForChat(chatId: Long): Flow<Int> = messageDao.getMessageCountForChat(chatId)

    fun getAllAttachments(): Flow<List<AttachmentEntity>> = messageDao.getAllAttachments()
    fun getAttachmentsByMimePrefix(prefix: String): Flow<List<AttachmentEntity>> =
        messageDao.getAttachmentsByMimePrefix(prefix)

    suspend fun saveTextMessage(text: String, tags: List<String> = emptyList(), chatId: Long = 1L): Long = withContext(Dispatchers.IO) {
        val detectedUrls = MediaUtils.extractUrls(text)
        val extractedTags = (tags + MediaUtils.extractTags(text)).distinct()
        val tagsString = extractedTags.joinToString(",")

        val (type, linkUrl, linkDomain) = if (detectedUrls.isNotEmpty() && text.trim() == detectedUrls.first()) {
            Triple(MessageType.LINK, detectedUrls.first(), MediaUtils.extractDomain(detectedUrls.first()))
        } else {
            Triple(MessageType.TEXT, detectedUrls.firstOrNull(), detectedUrls.firstOrNull()?.let { MediaUtils.extractDomain(it) })
        }

        val message = MessageEntity(
            chatId = chatId,
            type = type,
            text = text.trim(),
            tags = tagsString,
            linkUrl = linkUrl,
            linkDomain = linkDomain,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = messageDao.insertMessage(message)
        chatDao.touchChat(chatId)
        id
    }

    suspend fun saveChecklistMessage(title: String, items: List<ChecklistItem>, tags: List<String> = emptyList(), chatId: Long = 1L): Long = withContext(Dispatchers.IO) {
        val extractedTags = (tags + MediaUtils.extractTags(title)).distinct()
        val message = MessageEntity(
            chatId = chatId,
            type = MessageType.CHECKLIST,
            text = title.trim(),
            tags = extractedTags.joinToString(","),
            checklistJson = ChecklistItem.toJsonArray(items),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = messageDao.insertMessage(message)
        chatDao.touchChat(chatId)
        id
    }

    suspend fun updateChecklist(messageId: Long, items: List<ChecklistItem>) = withContext(Dispatchers.IO) {
        val json = ChecklistItem.toJsonArray(items)
        messageDao.updateChecklist(messageId, json)
    }

    suspend fun saveMediaAttachments(
        uris: List<Uri>,
        caption: String = "",
        typeHint: String = MessageType.IMAGE,
        chatId: Long = 1L
    ): Long = withContext(Dispatchers.IO) {
        val extractedTags = MediaUtils.extractTags(caption)
        val messageType = when {
            typeHint == MessageType.VIDEO -> MessageType.VIDEO
            typeHint == MessageType.FILE -> MessageType.FILE
            typeHint == MessageType.AUDIO -> MessageType.AUDIO
            else -> MessageType.IMAGE
        }

        val message = MessageEntity(
            chatId = chatId,
            type = messageType,
            text = caption.trim(),
            tags = extractedTags.joinToString(","),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val messageId = messageDao.insertMessage(message)
        chatDao.touchChat(chatId)

        val attachments = mutableListOf<AttachmentEntity>()
        for (uri in uris) {
            val copied = copyUriToInternalStorage(uri) ?: continue
            attachments.add(
                AttachmentEntity(
                    messageId = messageId,
                    localPath = copied.file.absolutePath,
                    filename = copied.filename,
                    mimeType = copied.mimeType,
                    fileSize = copied.file.length(),
                    durationMs = copied.durationMs,
                    width = copied.width,
                    height = copied.height,
                    thumbnailPath = null
                )
            )
        }
        if (attachments.isNotEmpty()) {
            messageDao.insertAttachments(attachments)
        }
        messageId
    }

    suspend fun saveVoiceRecording(
        recordedFile: File,
        durationMs: Long,
        caption: String = "",
        chatId: Long = 1L
    ): Long = withContext(Dispatchers.IO) {
        val message = MessageEntity(
            chatId = chatId,
            type = MessageType.AUDIO,
            text = caption.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val messageId = messageDao.insertMessage(message)
        chatDao.touchChat(chatId)

        val attachment = AttachmentEntity(
            messageId = messageId,
            localPath = recordedFile.absolutePath,
            filename = recordedFile.name,
            mimeType = "audio/mp4",
            fileSize = recordedFile.length(),
            durationMs = durationMs
        )
        messageDao.insertAttachment(attachment)
        messageId
    }

    suspend fun saveCapturedPhoto(photoFile: File, caption: String = "", chatId: Long = 1L): Long = withContext(Dispatchers.IO) {
        val message = MessageEntity(
            chatId = chatId,
            type = MessageType.IMAGE,
            text = caption.trim(),
            tags = MediaUtils.extractTags(caption).joinToString(","),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val messageId = messageDao.insertMessage(message)
        chatDao.touchChat(chatId)

        var width = 0
        var height = 0
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoFile.absolutePath, opts)
            width = opts.outWidth
            height = opts.outHeight
        } catch (_: Exception) {}

        val attachment = AttachmentEntity(
            messageId = messageId,
            localPath = photoFile.absolutePath,
            filename = photoFile.name,
            mimeType = "image/jpeg",
            fileSize = photoFile.length(),
            width = width,
            height = height
        )
        messageDao.insertAttachment(attachment)
        messageId
    }

    suspend fun togglePin(id: Long, currentPin: Boolean) = withContext(Dispatchers.IO) {
        messageDao.setPinned(id, !currentPin)
    }

    suspend fun toggleFavorite(id: Long, currentFav: Boolean) = withContext(Dispatchers.IO) {
        messageDao.setFavorite(id, !currentFav)
    }

    suspend fun updateMessageText(id: Long, newText: String) = withContext(Dispatchers.IO) {
        messageDao.updateText(id, newText)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        val paths = messageDao.getLocalPathsForMessages(listOf(id))
        for (path in paths) {
            try {
                File(path).delete()
            } catch (_: Exception) {}
        }
        messageDao.deleteMessageById(id)
    }

    suspend fun deleteMessages(ids: List<Long>) = withContext(Dispatchers.IO) {
        val paths = messageDao.getLocalPathsForMessages(ids)
        for (path in paths) {
            try {
                File(path).delete()
            } catch (_: Exception) {}
        }
        messageDao.deleteMessagesByIds(ids)
    }

    fun getFileProviderUri(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    suspend fun calculateStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        var images = 0L
        var videos = 0L
        var audio = 0L
        var files = 0L
        var total = 0L
        var attCount = 0

        val mediaFiles = mediaDir.listFiles() ?: emptyArray()
        for (file in mediaFiles) {
            if (file.isFile) {
                val len = file.length()
                total += len
                attCount++
                val name = file.name.lowercase()
                when {
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                            name.endsWith(".webp") || name.endsWith(".gif") -> images += len
                    name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") ||
                            name.endsWith(".webm") || name.endsWith(".3gp") -> videos += len
                    name.endsWith(".m4a") || name.endsWith(".mp3") || name.endsWith(".aac") ||
                            name.endsWith(".wav") || name.endsWith(".ogg") -> audio += len
                    else -> files += len
                }
            }
        }

        var cacheSize = 0L
        context.cacheDir.walkTopDown().forEach {
            if (it.isFile) cacheSize += it.length()
        }

        StorageStats(
            imagesBytes = images,
            videosBytes = videos,
            audioBytes = audio,
            filesBytes = files,
            totalMediaBytes = total,
            cacheBytes = cacheSize,
            attachmentCount = attCount
        )
    }

    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            true
        } catch (_: Exception) {
            false
        }
    }

    // Export complete backup into a zip file in cacheDir
    suspend fun exportBackup(): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val zipFile = File(backupDir, "saved_backup_${System.currentTimeMillis()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Export JSON metadata of all messages and attachments
            val allMessages = mutableListOf<MessageWithAttachments>()
            // Query DB synchronously for all
            val db = database.openHelper.readableDatabase
            val msgCursor = db.query("SELECT * FROM messages ORDER BY id ASC")
            val messagesArray = JSONArray()

            while (msgCursor.moveToNext()) {
                val obj = JSONObject()
                val id = msgCursor.getLong(msgCursor.getColumnIndexOrThrow("id"))
                obj.put("id", id)
                obj.put("type", msgCursor.getString(msgCursor.getColumnIndexOrThrow("type")))
                obj.put("text", msgCursor.getString(msgCursor.getColumnIndexOrThrow("text")))
                obj.put("createdAt", msgCursor.getLong(msgCursor.getColumnIndexOrThrow("createdAt")))
                obj.put("updatedAt", msgCursor.getLong(msgCursor.getColumnIndexOrThrow("updatedAt")))
                obj.put("isPinned", msgCursor.getInt(msgCursor.getColumnIndexOrThrow("isPinned")) == 1)
                obj.put("isFavorite", msgCursor.getInt(msgCursor.getColumnIndexOrThrow("isFavorite")) == 1)
                obj.put("isEdited", msgCursor.getInt(msgCursor.getColumnIndexOrThrow("isEdited")) == 1)
                obj.put("tags", msgCursor.getString(msgCursor.getColumnIndexOrThrow("tags")))
                obj.put("checklistJson", msgCursor.getString(msgCursor.getColumnIndexOrThrow("checklistJson")))
                obj.put("linkUrl", msgCursor.getString(msgCursor.getColumnIndexOrThrow("linkUrl")))
                obj.put("linkDomain", msgCursor.getString(msgCursor.getColumnIndexOrThrow("linkDomain")))
                messagesArray.put(obj)
            }
            msgCursor.close()

            val attCursor = db.query("SELECT * FROM attachments ORDER BY id ASC")
            val attachmentsArray = JSONArray()
            while (attCursor.moveToNext()) {
                val obj = JSONObject()
                val id = attCursor.getLong(attCursor.getColumnIndexOrThrow("id"))
                obj.put("id", id)
                obj.put("messageId", attCursor.getLong(attCursor.getColumnIndexOrThrow("messageId")))
                val fullPath = attCursor.getString(attCursor.getColumnIndexOrThrow("localPath"))
                val filename = attCursor.getString(attCursor.getColumnIndexOrThrow("filename"))
                obj.put("filename", filename)
                obj.put("storedName", File(fullPath).name)
                obj.put("mimeType", attCursor.getString(attCursor.getColumnIndexOrThrow("mimeType")))
                obj.put("fileSize", attCursor.getLong(attCursor.getColumnIndexOrThrow("fileSize")))
                obj.put("durationMs", attCursor.getLong(attCursor.getColumnIndexOrThrow("durationMs")))
                obj.put("width", attCursor.getInt(attCursor.getColumnIndexOrThrow("width")))
                obj.put("height", attCursor.getInt(attCursor.getColumnIndexOrThrow("height")))
                attachmentsArray.put(obj)
            }
            attCursor.close()

            val rootJson = JSONObject().apply {
                put("version", 1)
                put("exportTime", System.currentTimeMillis())
                put("messages", messagesArray)
                put("attachments", attachmentsArray)
            }

            // Write metadata entry
            zos.putNextEntry(ZipEntry("backup_metadata.json"))
            zos.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Write all media files
            val mediaFiles = mediaDir.listFiles() ?: emptyArray()
            val buffer = ByteArray(8192)
            for (file in mediaFiles) {
                if (file.isFile) {
                    zos.putNextEntry(ZipEntry("media/${file.name}"))
                    FileInputStream(file).use { fis ->
                        var count: Int
                        while (fis.read(buffer).also { count = it } > 0) {
                            zos.write(buffer, 0, count)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }

        zipFile
    }

    suspend fun importBackup(zipUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(zipUri) ?: return@withContext false
            var metadataString: String? = null

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(8192)

                while (entry != null) {
                    val name = entry.name
                    if (name == "backup_metadata.json") {
                        metadataString = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (name.startsWith("media/") && !entry.isDirectory) {
                        val fileName = name.substringAfter("media/")
                        if (fileName.isNotEmpty()) {
                            val targetFile = File(mediaDir, fileName)
                            FileOutputStream(targetFile).use { fos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            if (metadataString == null) return@withContext false

            val rootJson = JSONObject(metadataString!!)
            val messagesArray = rootJson.getJSONArray("messages")
            val attachmentsArray = rootJson.getJSONArray("attachments")

            // Clear existing and restore
            messageDao.clearAllMessages()

            for (i in 0 until messagesArray.length()) {
                val m = messagesArray.getJSONObject(i)
                val msg = MessageEntity(
                    id = m.optLong("id"),
                    type = m.optString("type", MessageType.TEXT),
                    text = m.optString("text", ""),
                    createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = m.optLong("updatedAt", System.currentTimeMillis()),
                    isPinned = m.optBoolean("isPinned", false),
                    isFavorite = m.optBoolean("isFavorite", false),
                    isEdited = m.optBoolean("isEdited", false),
                    tags = m.optString("tags", ""),
                    checklistJson = m.optString("checklistJson", ""),
                    linkUrl = if (m.has("linkUrl") && !m.isNull("linkUrl")) m.getString("linkUrl") else null,
                    linkDomain = if (m.has("linkDomain") && !m.isNull("linkDomain")) m.getString("linkDomain") else null
                )
                messageDao.insertMessage(msg)
            }

            for (j in 0 until attachmentsArray.length()) {
                val a = attachmentsArray.getJSONObject(j)
                val storedName = a.optString("storedName", "")
                val localFile = File(mediaDir, storedName)
                val att = AttachmentEntity(
                    id = a.optLong("id"),
                    messageId = a.optLong("messageId"),
                    localPath = localFile.absolutePath,
                    filename = a.optString("filename", "file"),
                    mimeType = a.optString("mimeType", "*/*"),
                    fileSize = a.optLong("fileSize", localFile.length()),
                    durationMs = a.optLong("durationMs", 0L),
                    width = a.optInt("width", 0),
                    height = a.optInt("height", 0)
                )
                messageDao.insertAttachment(att)
            }

            true
        } catch (e: Exception) {
            Log.e("AppRepository", "Error importing backup", e)
            false
        }
    }

    private data class CopiedMediaInfo(
        val file: File,
        val filename: String,
        val mimeType: String,
        val width: Int = 0,
        val height: Int = 0,
        val durationMs: Long = 0L
    )

    private fun copyUriToInternalStorage(uri: Uri): CopiedMediaInfo? {
        return try {
            val contentResolver = context.contentResolver
            var filename = "attachment_${System.currentTimeMillis()}"
            var mimeType = contentResolver.getType(uri) ?: "*/*"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) filename = name
                }
            }

            val ext = if (filename.contains('.')) {
                "." + filename.substringAfterLast('.')
            } else {
                when {
                    mimeType.startsWith("image/png") -> ".png"
                    mimeType.startsWith("image/") -> ".jpg"
                    mimeType.startsWith("video/") -> ".mp4"
                    mimeType.startsWith("audio/") -> ".m4a"
                    mimeType.startsWith("application/pdf") -> ".pdf"
                    else -> ".bin"
                }
            }

            val destFile = File(mediaDir, "saved_${System.currentTimeMillis()}_${(1000..9999).random()}$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            var width = 0
            var height = 0
            var durationMs = 0L

            if (mimeType.startsWith("image/")) {
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(destFile.absolutePath, opts)
                    width = opts.outWidth
                    height = opts.outHeight
                } catch (_: Exception) {}
            } else if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(destFile.absolutePath)
                    val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = dur?.toLongOrNull() ?: 0L
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    width = w?.toIntOrNull() ?: 0
                    height = h?.toIntOrNull() ?: 0
                    retriever.release()
                } catch (_: Exception) {}
            }

            CopiedMediaInfo(
                file = destFile,
                filename = filename,
                mimeType = mimeType,
                width = width,
                height = height,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            Log.e("AppRepository", "Error copying URI to local storage", e)
            null
        }
    }
}
