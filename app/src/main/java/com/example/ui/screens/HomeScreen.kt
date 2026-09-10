package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.ChecklistItem
import com.example.data.MessageType
import com.example.data.MessageWithAttachments
import com.example.media.MediaUtils
import com.example.ui.SavedViewModel
import com.example.ui.components.AttachmentBottomSheet
import com.example.ui.components.ChatAvatar
import com.example.ui.components.ChatBubble
import com.example.ui.components.ComposerBar
import com.example.ui.components.CreateChecklistDialog
import com.example.ui.components.DateSeparator
import com.example.ui.components.PinnedBanner
import com.example.ui.components.RenameChatDialog
import com.example.ui.components.VoiceRecordingBar
import com.example.ui.theme.TelegramBlue
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SavedViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMediaGallery: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val activeChat by viewModel.activeChat.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var topMenuExpanded by remember { mutableStateOf(false) }

    val filteredMessages by viewModel.filteredMessages.collectAsState()
    val pinnedMessages by viewModel.pinnedMessages.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val onlyFavorites by viewModel.onlyFavorites.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedMessageIds by viewModel.selectedMessageIds.collectAsState()

    val composerText by viewModel.composerText.collectAsState()
    val pendingMediaUris by viewModel.pendingMediaUris.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()

    val isRecordingVoice by viewModel.audioRecorderManager.isRecording.collectAsState()
    val viewerAttachment by viewModel.viewerAttachment.collectAsState()
    val viewerContextList by viewModel.viewerContextList.collectAsState()

    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showChecklistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }

    val attachmentSheetState = rememberModalBottomSheetState()

    // Pick Multiple Media (Images / Videos) Launcher
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addPendingMedia(uris)
        }
    }

    // Pick Documents / Files Launcher
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addPendingMedia(uris)
        }
    }

    // Take Picture Camera Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val file = capturedPhotoFile
        if (success && file != null && file.exists() && file.length() > 0) {
            viewModel.saveCapturedPhoto(file)
            Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
        }
        capturedPhotoFile = null
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            launchCameraCapture(context) { file, uri ->
                capturedPhotoFile = file
                takePictureLauncher.launch(uri)
            }
        } else {
            Toast.makeText(context, "Camera permission needed to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Mic Audio Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val started = viewModel.startVoiceRecording()
            if (!started) {
                Toast.makeText(context, "Unable to start voice recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission needed for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Multi-select Top Bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedMessageIds.size} selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(imageVector = Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(
                            onClick = {
                                val selectedMsgs = viewModel.getSelectedMessages()
                                batchShareMessages(context, selectedMsgs) { viewModel.repository.getFileProviderUri(it) }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share selected")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            } else if (isSearching) {
                // Search Top Bar
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("Search notes, files, tags...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                viewModel.isSearching.value = false
                                viewModel.searchQuery.value = ""
                            }
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            } else {
                // Normal Saved Messages Top Bar
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("home_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to chats"
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showRenameDialog = true }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .testTag("home_chat_header")
                        ) {
                            val currentChat = activeChat
                            if (currentChat != null) {
                                ChatAvatar(chat = currentChat, size = 36.dp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(TelegramBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = activeChat?.name ?: "Saved",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename chat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = "Tap to rename",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelegramBlue
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.isSearching.value = true },
                            modifier = Modifier.testTag("home_search_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(
                            onClick = onNavigateToMediaGallery,
                            modifier = Modifier.testTag("home_gallery_btn")
                        ) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Media Gallery")
                        }
                        Box {
                            IconButton(
                                onClick = { topMenuExpanded = true },
                                modifier = Modifier.testTag("home_more_menu_btn")
                            ) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename chat") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        topMenuExpanded = false
                                        showRenameDialog = true
                                    },
                                    modifier = Modifier.testTag("menu_rename_active_chat")
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        topMenuExpanded = false
                                        onNavigateToSettings()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        bottomBar = {
            if (isRecordingVoice) {
                VoiceRecordingBar(
                    audioRecorderManager = viewModel.audioRecorderManager,
                    onCancel = { viewModel.cancelVoiceRecording() },
                    onSend = { viewModel.stopAndSendVoiceRecording() }
                )
            } else {
                ComposerBar(
                    text = composerText,
                    onTextChange = { viewModel.onComposerTextChange(it) },
                    pendingMediaUris = pendingMediaUris,
                    editingMessage = editingMessage,
                    onRemovePendingMedia = { viewModel.removePendingMedia(it) },
                    onCancelEdit = { viewModel.cancelEdit() },
                    onOpenAttachmentMenu = { showAttachmentSheet = true },
                    onLaunchCamera = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCameraCapture(context) { file, uri ->
                                capturedPhotoFile = file
                                takePictureLauncher.launch(uri)
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onStartVoiceRecording = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            val started = viewModel.startVoiceRecording()
                            if (!started) {
                                Toast.makeText(context, "Could not start audio recorder", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSend = { viewModel.sendMessage() }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.testTag("home_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Pinned Messages Banner
            PinnedBanner(
                pinnedMessages = pinnedMessages,
                onBannerClick = { pinnedMsg ->
                    val index = filteredMessages.indexOfFirst { it.message.id == pinnedMsg.message.id }
                    if (index >= 0) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    }
                },
                onUnpinClick = { viewModel.togglePin(it.message) }
            )

            // Lightweight Tag & Favorite filter row
            if (allTags.isNotEmpty() || onlyFavorites) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Favorites filter
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { viewModel.onlyFavorites.value = !onlyFavorites },
                        label = { Text("★ Favorites") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelegramBlue.copy(alpha = 0.15f),
                            selectedLabelColor = TelegramBlue
                        )
                    )

                    allTags.forEach { tag ->
                        val isSelected = selectedTag == tag
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.selectedTag.value = if (isSelected) null else tag
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            // Chat Timeline
            if (filteredMessages.isEmpty()) {
                // Empty state with Saved Messages explanation and quick starters
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Your Saved Messages",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Save quick thoughts, photos, audio memos, documents, and links privately on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            StarterChip(text = "📝 Text Note") {
                                viewModel.composerText.value = "Remember: "
                            }
                            StarterChip(text = "📷 Photo") {
                                pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
                            StarterChip(text = "🎙️ Voice Memo") {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.startVoiceRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            StarterChip(text = "✅ Checklist") {
                                showChecklistDialog = true
                            }
                        }
                    }
                }
            } else {
                // Messages list with Date Separators
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    var lastDateHeader = ""
                    filteredMessages.forEachIndexed { index, messageWithAttachments ->
                        val dateHeader = MediaUtils.formatDateHeader(messageWithAttachments.message.createdAt)
                        if (dateHeader != lastDateHeader) {
                            lastDateHeader = dateHeader
                            item(key = "header_${messageWithAttachments.message.id}_$dateHeader") {
                                DateSeparator(dateText = dateHeader)
                            }
                        }

                        item(key = "msg_${messageWithAttachments.message.id}") {
                            ChatBubble(
                                messageWithAttachments = messageWithAttachments,
                                audioPlayerManager = viewModel.audioPlayerManager,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedMessageIds.contains(messageWithAttachments.message.id),
                                onToggleSelect = { viewModel.toggleSelection(it) },
                                onStartSelection = { viewModel.startSelection(it) },
                                onOpenAttachment = { att, ctxList -> viewModel.openViewer(att, ctxList) },
                                onTogglePin = { viewModel.togglePin(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onEdit = { viewModel.startEdit(it) },
                                onDelete = { viewModel.deleteMessage(it) },
                                onToggleChecklistItem = { msgId, items, itemId ->
                                    viewModel.toggleChecklistItem(msgId, items, itemId)
                                },
                                getFileProviderUri = { viewModel.repository.getFileProviderUri(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Attachment Bottom Sheet
    if (showAttachmentSheet) {
        AttachmentBottomSheet(
            sheetState = attachmentSheetState,
            onDismiss = { showAttachmentSheet = false },
            onSelectGallery = {
                pickMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onLaunchCamera = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCameraCapture(context) { file, uri ->
                        capturedPhotoFile = file
                        takePictureLauncher.launch(uri)
                    }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onSelectDocuments = {
                pickDocumentLauncher.launch(arrayOf("*/*"))
            },
            onCreateChecklist = {
                showChecklistDialog = true
            }
        )
    }

    // Create Checklist Dialog
    if (showChecklistDialog) {
        CreateChecklistDialog(
            onDismiss = { showChecklistDialog = false },
            onSave = { title, items ->
                viewModel.saveChecklist(title, items)
            }
        )
    }

    // Multi-Select Batch Delete Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Messages") },
            text = { Text("Are you sure you want to delete ${selectedMessageIds.size} selected messages? Attached local files will also be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedMessages()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Fullscreen Media Viewer Dialog
    if (viewerAttachment != null) {
        ImageViewerDialog(
            initialAttachment = viewerAttachment!!,
            attachments = viewerContextList.ifEmpty { listOf(viewerAttachment!!) },
            onDismiss = { viewModel.closeViewer() },
            onDelete = { att ->
                // Delete attachment / associated message
                viewModel.deleteMessage(att.messageId)
            },
            getFileProviderUri = { viewModel.repository.getFileProviderUri(it) }
        )
    }

    // Rename Chat Dialog
    if (showRenameDialog && activeChat != null) {
        RenameChatDialog(
            chat = activeChat!!,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName, iconName, colorHex, imageUri ->
                showRenameDialog = false
                viewModel.updateChatAppearance(activeChat!!.id, newName, iconName, colorHex, imageUri)
            }
        )
    }
}

@Composable
private fun StarterChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private fun launchCameraCapture(context: Context, onReady: (File, Uri) -> Unit) {
    try {
        val mediaDir = File(context.filesDir, "saved_media").apply { mkdirs() }
        val photoFile = File(mediaDir, "photo_${System.currentTimeMillis()}.jpg")
        val authority = "${context.packageName}.fileprovider"
        val photoUri = FileProvider.getUriForFile(context, authority, photoFile)
        onReady(photoFile, photoUri)
    } catch (e: Exception) {
        Toast.makeText(context, "Error setting up camera: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun batchShareMessages(
    context: Context,
    messages: List<MessageWithAttachments>,
    getFileProviderUri: (File) -> Uri
) {
    if (messages.isEmpty()) return

    val fileUris = ArrayList<Uri>()
    val texts = StringBuilder()

    messages.forEach { m ->
        if (m.message.text.isNotBlank()) {
            texts.appendLine(m.message.text)
        }
        m.attachments.forEach { att ->
            val f = File(att.localPath)
            if (f.exists()) {
                fileUris.add(getFileProviderUri(f))
            }
        }
    }

    if (fileUris.size > 1) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
            if (texts.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, texts.toString())
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share messages"))
    } else if (fileUris.size == 1) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, fileUris.first())
            if (texts.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, texts.toString())
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share message"))
    } else if (texts.isNotBlank()) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texts.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Share message text"))
    }
}
