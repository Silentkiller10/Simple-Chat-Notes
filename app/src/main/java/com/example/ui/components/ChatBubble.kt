@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AttachmentEntity
import com.example.data.ChecklistItem
import com.example.data.MessageEntity
import com.example.data.MessageType
import com.example.data.MessageWithAttachments
import com.example.media.AudioPlayerManager
import com.example.media.MediaUtils
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    messageWithAttachments: MessageWithAttachments,
    audioPlayerManager: AudioPlayerManager,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: (Long) -> Unit,
    onStartSelection: (Long) -> Unit,
    onOpenAttachment: (AttachmentEntity, List<AttachmentEntity>) -> Unit,
    onTogglePin: (MessageEntity) -> Unit,
    onToggleFavorite: (MessageEntity) -> Unit,
    onEdit: (MessageEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleChecklistItem: (Long, List<ChecklistItem>, String) -> Unit,
    getFileProviderUri: (File) -> Uri,
    modifier: Modifier = Modifier
) {
    val message = messageWithAttachments.message
    val attachments = messageWithAttachments.attachments
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 4.dp
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        // Selection checkbox if in multi-select mode
        if (isSelectionMode) {
            IconButton(
                onClick = { onToggleSelect(message.id) },
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(28.dp)
                    .testTag("select_message_${message.id}")
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = "Select message",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .widthIn(min = 120.dp, max = 320.dp)
                .clip(bubbleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primaryContainer
                )
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelect(message.id)
                        }
                    },
                    onLongClick = {
                        if (isSelectionMode) {
                            onToggleSelect(message.id)
                        } else {
                            showMenu = true
                        }
                    }
                )
                .padding(8.dp)
                .testTag("chat_bubble_${message.id}")
        ) {
            Column {
                // Attachments rendering
                if (attachments.isNotEmpty()) {
                    when (message.type) {
                        MessageType.IMAGE -> {
                            ImageGridContent(
                                attachments = attachments,
                                onOpenAttachment = { att -> onOpenAttachment(att, attachments) }
                            )
                        }
                        MessageType.VIDEO -> {
                            VideoAttachmentContent(
                                attachment = attachments.first(),
                                onOpen = { onOpenAttachment(it, attachments) }
                            )
                        }
                        MessageType.AUDIO -> {
                            AudioAttachmentContent(
                                attachment = attachments.first(),
                                audioPlayerManager = audioPlayerManager
                            )
                        }
                        else -> {
                            // File or multi-type
                            attachments.forEach { att ->
                                FileAttachmentContent(
                                    attachment = att,
                                    context = context,
                                    getFileProviderUri = getFileProviderUri
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Checklist rendering
                if (message.type == MessageType.CHECKLIST) {
                    val items = remember(message.checklistJson) {
                        ChecklistItem.fromJsonArray(message.checklistJson)
                    }
                    ChecklistContent(
                        title = message.text,
                        items = items,
                        onToggle = { itemId ->
                            onToggleChecklistItem(message.id, items, itemId)
                        }
                    )
                }

                // Text rendering (notes, links, captions)
                if (message.text.isNotBlank() && message.type != MessageType.CHECKLIST) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("message_text_${message.id}")
                    )
                }

                // Link Preview Card
                if (!message.linkUrl.isNullOrBlank()) {
                    LinkPreviewCard(
                        url = message.linkUrl,
                        domain = message.linkDomain ?: MediaUtils.extractDomain(message.linkUrl),
                        context = context
                    )
                }

                // Tags chips
                if (message.tags.isNotBlank()) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        message.tags.split(",").filter { it.isNotBlank() }.forEach { tag ->
                            Text(
                                text = if (tag.startsWith("#")) tag else "#$tag",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Footer: Timestamp, edited status, pin, favorite, checkmarks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            text = "edited",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    if (message.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(end = 2.dp)
                        )
                    }

                    if (message.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(end = 2.dp)
                        )
                    }

                    Text(
                        text = MediaUtils.formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Context dropdown menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy Text") },
                    onClick = {
                        showMenu = false
                        clipboardManager.setText(AnnotatedString(message.text))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )

                if (message.type == MessageType.TEXT || message.type == MessageType.LINK) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit(message)
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text(if (message.isPinned) "Unpin" else "Pin") },
                    onClick = {
                        showMenu = false
                        onTogglePin(message)
                    }
                )

                DropdownMenuItem(
                    text = { Text(if (message.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                    onClick = {
                        showMenu = false
                        onToggleFavorite(message)
                    }
                )

                DropdownMenuItem(
                    text = { Text("Select") },
                    onClick = {
                        showMenu = false
                        onStartSelection(message.id)
                    }
                )

                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        showMenu = false
                        shareMessageContent(context, messageWithAttachments, getFileProviderUri)
                    }
                )

                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete(message.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ImageGridContent(
    attachments: List<AttachmentEntity>,
    onOpenAttachment: (AttachmentEntity) -> Unit
) {
    val count = attachments.size
    if (count == 1) {
        val att = attachments.first()
        AsyncImage(
            model = File(att.localPath),
            contentDescription = att.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = { onOpenAttachment(att) })
        )
    } else {
        // Multi-image grid
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val chunked = attachments.chunked(2)
            chunked.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowItems.forEach { att ->
                        AsyncImage(
                            model = File(att.localPath),
                            contentDescription = att.filename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(onClick = { onOpenAttachment(att) })
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun VideoAttachmentContent(
    attachment: AttachmentEntity,
    onOpen: (AttachmentEntity) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .combinedClickable(onClick = { onOpen(attachment) }),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = File(attachment.localPath),
            contentDescription = "Video preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        // Dark translucent overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
        // Play button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Video",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        // Duration pill
        if (attachment.durationMs > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = MediaUtils.formatDuration(attachment.durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun AudioAttachmentContent(
    attachment: AttachmentEntity,
    audioPlayerManager: AudioPlayerManager
) {
    val currentPlayingPath by audioPlayerManager.currentPlayingPath.collectAsState()
    val isPlaying by audioPlayerManager.isPlaying.collectAsState()
    val progress by audioPlayerManager.progress.collectAsState()
    val currentPosMs by audioPlayerManager.currentPositionMs.collectAsState()

    val isThisPlaying = currentPlayingPath == attachment.localPath && isPlaying
    val isThisActive = currentPlayingPath == attachment.localPath

    val displayDuration = if (isThisActive && currentPosMs > 0) {
        MediaUtils.formatDuration(currentPosMs)
    } else {
        MediaUtils.formatDuration(attachment.durationMs)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(onClick = { audioPlayerManager.playOrPause(attachment.localPath) }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isThisPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = if (isThisActive) progress else 0f,
                    onValueChange = { frac ->
                        if (isThisActive) {
                            audioPlayerManager.seekTo(frac)
                        }
                    },
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Voice message",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = displayDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun FileAttachmentContent(
    attachment: AttachmentEntity,
    context: Context,
    getFileProviderUri: (File) -> Uri
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {
                openFileWithIntent(context, File(attachment.localPath), attachment.mimeType, getFileProviderUri)
            })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "File",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.filename,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${MediaUtils.formatBytes(attachment.fileSize)} • ${MediaUtils.getFileExtension(attachment.filename)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open file",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ChecklistContent(
    title: String,
    items: List<ChecklistItem>,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onToggle(item.id) })
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isDone,
                    onCheckedChange = { onToggle(item.id) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun LinkPreviewCard(url: String, domain: String, context: Context) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                }
            })
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open Link",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun openFileWithIntent(
    context: Context,
    file: File,
    mimeType: String,
    getFileProviderUri: (File) -> Uri
) {
    if (!file.exists()) {
        Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = getFileProviderUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareMessageContent(
    context: Context,
    messageWithAttachments: MessageWithAttachments,
    getFileProviderUri: (File) -> Uri
) {
    val message = messageWithAttachments.message
    val attachments = messageWithAttachments.attachments

    if (attachments.isNotEmpty()) {
        val file = File(attachments.first().localPath)
        if (file.exists()) {
            val uri = getFileProviderUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = attachments.first().mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                if (message.text.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, message.text)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share via"))
            return
        }
    }

    if (message.text.isNotBlank()) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message.text)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }
}
