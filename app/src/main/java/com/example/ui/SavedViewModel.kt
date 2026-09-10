package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SavedApplication
import com.example.data.AppRepository
import com.example.data.AttachmentEntity
import com.example.data.ChatEntity
import com.example.data.ChecklistItem
import com.example.data.MessageEntity
import com.example.data.MessageType
import com.example.data.MessageWithAttachments
import com.example.data.StorageStats
import com.example.data.UserPreferences
import com.example.media.AudioPlayerManager
import com.example.media.AudioRecorderManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private data class ChatFilterParams(
    val chatId: Long,
    val query: String,
    val tag: String?,
    val favOnly: Boolean
)

class SavedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SavedApplication
    val repository: AppRepository = app.repository
    val userPreferences: UserPreferences = app.userPreferences
    val audioPlayerManager: AudioPlayerManager = app.audioPlayerManager
    val audioRecorderManager = AudioRecorderManager(application)

    // Active Chat State
    val activeChatId = MutableStateFlow<Long>(1L)

    val allChats: StateFlow<List<ChatEntity>> = repository.getAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChat: StateFlow<ChatEntity?> = activeChatId.flatMapLatest { id ->
        repository.getChatByIdFlow(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Search and filter state
    val searchQuery = MutableStateFlow("")
    val isSearching = MutableStateFlow(false)
    val selectedTag = MutableStateFlow<String?>(null)
    val onlyFavorites = MutableStateFlow(false)

    // All messages from active chat
    @OptIn(ExperimentalCoroutinesApi::class)
    val allMessages: StateFlow<List<MessageWithAttachments>> = activeChatId.flatMapLatest { id ->
        repository.getMessagesForChatAsc(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pinnedMessages: StateFlow<List<MessageWithAttachments>> = activeChatId.flatMapLatest { id ->
        repository.getPinnedMessagesForChat(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic distinct tags from messages in current chat
    val allTags: StateFlow<List<String>> = allMessages.map { list ->
        list.flatMap { it.message.tags.split(",").filter { tag -> tag.isNotBlank() } }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered messages
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredMessages: StateFlow<List<MessageWithAttachments>> = combine(
        activeChatId,
        searchQuery,
        selectedTag,
        onlyFavorites
    ) { chatId, query, tag, favOnly ->
        ChatFilterParams(chatId, query.trim(), tag, favOnly)
    }.flatMapLatest { params ->
        if (params.query.isNotEmpty()) {
            repository.searchMessagesInChat(params.chatId, params.query).map { list ->
                list.filter { m ->
                    (params.tag == null || m.message.tags.contains(params.tag)) &&
                            (!params.favOnly || m.message.isFavorite)
                }.reversed() // search queries DESC -> timeline ASC
            }
        } else {
            repository.getMessagesForChatAsc(params.chatId).map { list ->
                list.filter { m ->
                    (params.tag == null || m.message.tags.contains(params.tag)) &&
                            (!params.favOnly || m.message.isFavorite)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Multi-selection state
    val isSelectionMode = MutableStateFlow(false)
    val selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())

    // Composer state
    val composerText = MutableStateFlow("")
    val pendingMediaUris = MutableStateFlow<List<Uri>>(emptyList())
    val editingMessage = MutableStateFlow<MessageEntity?>(null)

    // Media Gallery state
    val galleryTab = MutableStateFlow("ALL") // ALL, IMAGE, VIDEO, FILE, AUDIO
    @OptIn(ExperimentalCoroutinesApi::class)
    val galleryAttachments: StateFlow<List<AttachmentEntity>> = galleryTab.flatMapLatest { tab ->
        when (tab) {
            "IMAGE" -> repository.getAttachmentsByMimePrefix("image/")
            "VIDEO" -> repository.getAttachmentsByMimePrefix("video/")
            "AUDIO" -> repository.getAttachmentsByMimePrefix("audio/")
            "FILE" -> repository.getAllAttachments().map { list ->
                list.filter { !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("video/") && !it.mimeType.startsWith("audio/") }
            }
            else -> repository.getAllAttachments()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fullscreen media viewer
    val viewerAttachment = MutableStateFlow<AttachmentEntity?>(null)
    val viewerContextList = MutableStateFlow<List<AttachmentEntity>>(emptyList())

    // Storage info
    val storageStats = MutableStateFlow<StorageStats?>(null)

    // App Lock
    private val _isAppLocked = MutableStateFlow(userPreferences.isAppLockEnabled.value)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultChat()
        }
        loadStorageStats()
    }

    // Chat Management
    fun selectChat(chatId: Long) {
        activeChatId.value = chatId
        searchQuery.value = ""
        isSearching.value = false
        selectedTag.value = null
        onlyFavorites.value = false
        editingMessage.value = null
        composerText.value = ""
        pendingMediaUris.value = emptyList()
        clearSelection()
    }

    fun createChat(
        name: String,
        description: String = "Notes & messages",
        iconName: String = "bookmark",
        colorHex: Long = 0xFF2AABEE,
        imageUri: String? = null,
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val newId = repository.createChat(name, description, iconName, colorHex, imageUri)
            activeChatId.value = newId
            onCreated(newId)
        }
    }

    fun renameChat(chatId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameChat(chatId, newName)
        }
    }

    fun updateChatAppearance(chatId: Long, name: String, iconName: String, colorHex: Long, imageUri: String?) {
        viewModelScope.launch {
            repository.updateChatAppearance(chatId, name, iconName, colorHex, imageUri)
        }
    }

    fun deleteChat(chatId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
            val remaining = repository.getChatCount()
            if (remaining == 0) {
                repository.ensureDefaultChat()
                activeChatId.value = 1L
            } else {
                activeChatId.value = 1L
            }
            onDeleted()
        }
    }

    // Composer Actions
    fun onComposerTextChange(text: String) {
        composerText.value = text
    }

    fun addPendingMedia(uris: List<Uri>) {
        pendingMediaUris.value = (pendingMediaUris.value + uris).distinct()
    }

    fun removePendingMedia(uri: Uri) {
        pendingMediaUris.value = pendingMediaUris.value - uri
    }

    fun sendMessage() {
        val text = composerText.value.trim()
        val uris = pendingMediaUris.value
        val editing = editingMessage.value
        val chatId = activeChatId.value

        if (editing != null) {
            viewModelScope.launch {
                repository.updateMessageText(editing.id, text)
                editingMessage.value = null
                composerText.value = ""
            }
            return
        }

        if (text.isEmpty() && uris.isEmpty()) return

        viewModelScope.launch {
            if (uris.isNotEmpty()) {
                val typeHint = when {
                    uris.any { getApplication<Application>().contentResolver.getType(it)?.startsWith("video/") == true } -> MessageType.VIDEO
                    uris.any { getApplication<Application>().contentResolver.getType(it)?.startsWith("image/") == true } -> MessageType.IMAGE
                    uris.any { getApplication<Application>().contentResolver.getType(it)?.startsWith("audio/") == true } -> MessageType.AUDIO
                    else -> MessageType.FILE
                }
                repository.saveMediaAttachments(uris, caption = text, typeHint = typeHint, chatId = chatId)
            } else {
                repository.saveTextMessage(text, chatId = chatId)
            }
            composerText.value = ""
            pendingMediaUris.value = emptyList()
            loadStorageStats()
        }
    }

    fun startEdit(message: MessageEntity) {
        editingMessage.value = message
        composerText.value = message.text
    }

    fun cancelEdit() {
        editingMessage.value = null
        composerText.value = ""
    }

    fun saveCapturedPhoto(photoFile: File) {
        val chatId = activeChatId.value
        viewModelScope.launch {
            repository.saveCapturedPhoto(photoFile, chatId = chatId)
            loadStorageStats()
        }
    }

    // Voice recording
    fun startVoiceRecording(): Boolean {
        return audioRecorderManager.startRecording()
    }

    fun stopAndSendVoiceRecording() {
        val result = audioRecorderManager.stopRecording()
        val chatId = activeChatId.value
        if (result != null) {
            val (file, duration) = result
            viewModelScope.launch {
                repository.saveVoiceRecording(file, duration, chatId = chatId)
                loadStorageStats()
            }
        }
    }

    fun cancelVoiceRecording() {
        audioRecorderManager.cancelRecording()
    }

    // Checklist
    fun saveChecklist(title: String, items: List<ChecklistItem>) {
        if (title.isBlank() && items.isEmpty()) return
        val chatId = activeChatId.value
        viewModelScope.launch {
            repository.saveChecklistMessage(title.ifBlank { "Tasks" }, items, chatId = chatId)
        }
    }

    fun toggleChecklistItem(messageId: Long, currentItems: List<ChecklistItem>, itemId: String) {
        val updated = currentItems.map {
            if (it.id == itemId) it.copy(isDone = !it.isDone) else it
        }
        viewModelScope.launch {
            repository.updateChecklist(messageId, updated)
        }
    }

    // Pins & Favorites
    fun togglePin(message: MessageEntity) {
        viewModelScope.launch {
            repository.togglePin(message.id, message.isPinned)
        }
    }

    fun toggleFavorite(message: MessageEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(message.id, message.isFavorite)
        }
    }

    // Deletions
    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
            loadStorageStats()
        }
    }

    fun deleteSelectedMessages() {
        val ids = selectedMessageIds.value.toList()
        viewModelScope.launch {
            repository.deleteMessages(ids)
            clearSelection()
            loadStorageStats()
        }
    }

    // Multi-selection
    fun toggleSelectMessage(id: Long) {
        val current = selectedMessageIds.value
        if (current.contains(id)) {
            val next = current - id
            selectedMessageIds.value = next
            if (next.isEmpty()) {
                isSelectionMode.value = false
            }
        } else {
            selectedMessageIds.value = current + id
            isSelectionMode.value = true
        }
    }

    fun toggleSelection(id: Long) = toggleSelectMessage(id)
    fun startSelection(id: Long) = toggleSelectMessage(id)

    fun clearSelection() {
        isSelectionMode.value = false
        selectedMessageIds.value = emptySet()
    }

    fun selectAll() {
        val allIds = filteredMessages.value.map { it.message.id }.toSet()
        selectedMessageIds.value = allIds
        isSelectionMode.value = true
    }

    fun getSelectedMessages(): List<MessageWithAttachments> {
        val ids = selectedMessageIds.value
        return filteredMessages.value.filter { ids.contains(it.message.id) }
    }

    // Tag filter
    fun selectTag(tag: String?) {
        if (selectedTag.value == tag) {
            selectedTag.value = null
        } else {
            selectedTag.value = tag
        }
    }

    fun toggleFavoritesFilter() {
        onlyFavorites.value = !onlyFavorites.value
    }

    // Media Viewer
    fun openMediaViewer(attachment: AttachmentEntity, contextList: List<AttachmentEntity> = emptyList()) {
        viewerAttachment.value = attachment
        viewerContextList.value = contextList.ifEmpty { listOf(attachment) }
    }

    fun openViewer(attachment: AttachmentEntity, contextList: List<AttachmentEntity> = emptyList()) =
        openMediaViewer(attachment, contextList)

    fun closeMediaViewer() {
        viewerAttachment.value = null
        viewerContextList.value = emptyList()
    }

    fun closeViewer() = closeMediaViewer()

    // Storage
    fun loadStorageStats() {
        viewModelScope.launch {
            storageStats.value = repository.calculateStorageStats()
        }
    }

    fun clearCache(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.clearCache()
            loadStorageStats()
            onComplete(result)
        }
    }

    fun exportBackup(onDone: (File) -> Unit) {
        viewModelScope.launch {
            val file = repository.exportBackup()
            onDone(file)
        }
    }

    fun importBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importBackup(uri)
            loadStorageStats()
            onDone(success)
        }
    }

    // App Lock
    fun unlockWithPin(pin: String): Boolean {
        val verified = userPreferences.verifyPin(pin)
        if (verified) {
            _isAppLocked.value = false
        }
        return verified
    }

    fun setAppLockPin(pin: String) {
        userPreferences.setPin(pin)
        userPreferences.setAppLockEnabled(true)
    }

    fun disableAppLock() {
        userPreferences.setAppLockEnabled(false)
        _isAppLocked.value = false
    }

    fun lockAppNow() {
        if (userPreferences.isAppLockEnabled.value) {
            _isAppLocked.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.release()
    }
}
